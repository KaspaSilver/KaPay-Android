package com.kachat.app.services

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import protowire.Rpc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live UTXO subscription to the wallet's own addresses (iOS wallet 301ebfb `WalletService`'s
 * `setupUtxoSubscription`): the main address, every revealed spending address and every
 * cold-storage address, streamed from a node (`NotifyUtxosChanged`) so a receive is announced and
 * the balance moves within seconds instead of on the next poll.
 *
 * - Runs while the app is open ([start] / [stop] from the process lifecycle), as the rest of the
 *   app's network work does; while it's away the push service carries "Received", and the
 *   catch-up pass finds the rest on return.
 * - Each batch goes to [AddressActivityNotifier.handleLiveUtxoChanges] ("Received KAS" with the
 *   self-send check) and a balance refresh. Batches are queued and processed one at a time; one
 *   that shares a transaction with a batch still waiting is merged into it, one entry per
 *   outpoint, so the same change delivered twice never counts an output twice (iOS IOS-071).
 * - A failed subscribe (no node yet, a node without a UTXO index) retries with capped backoff
 *   (1 s doubling to 30 s), and the dropped stream of a failover re-subscribes on the next
 *   connection. A change of default network re-subscribes at once, out of any backoff
 *   ([NodePoolManager.networkPathChanges]). Whenever the subscription comes (back) up, the catch-up pass runs for what
 *   arrived meanwhile and the balance refreshes. While it is down, the notifier's fallback poll
 *   runs ([AddressActivityNotifier.liveSubscriptionActive]).
 * - A new spending address or cold-storage account, or a switch of account, re-subscribes with
 *   the new set.
 */
@Singleton
class OwnAddressSubscription @Inject constructor(
    private val nodePoolManager: NodePoolManager,
    private val walletManager: WalletManager,
    private val notifier: AddressActivityNotifier,
    // Lazy: WalletService pulls in the whole send stack, which this must not wait on at startup.
    private val walletServiceLazy: dagger.Lazy<WalletService>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.kachat.app.util.StartupSafety.logOnlyHandler("OwnAddressSubscription"))
    private var runJob: Job? = null

    /** A batch waiting its turn, with every transaction it touches. */
    private data class Queued(
        val added: List<AddressActivityNotifier.LiveUtxo>,
        val removed: List<AddressActivityNotifier.LiveUtxo>,
        val txIds: Set<String>,
    )

    private val queueMutex = Mutex()
    private val queue = ArrayDeque<Queued>()
    private var processing = false

    /** App in front: subscribe (again) for whichever account is active, following switches. */
    fun start() {
        if (runJob?.isActive == true) return
        runJob = scope.launch {
            // Signed out counts as no account: nothing is subscribed then (iOS has no wallet).
            combine(walletManager.activeAddressFlow, walletManager.loggedOutFlow) { address, loggedOut ->
                address.takeUnless { loggedOut }
            }.distinctUntilChanged().collectLatest { address ->
                notifier.liveSubscriptionActive = false
                if (address != null) runSubscription(address)
            }
        }
    }

    /** App away: the subscription is dropped (the push service and the catch-up cover it). */
    fun stop() {
        runJob?.cancel()
        runJob = null
        notifier.liveSubscriptionActive = false
    }

    /** Subscribes [walletAddress]'s own addresses and keeps it subscribed until cancelled. */
    private suspend fun runSubscription(walletAddress: String) {
        var attempts = 0
        var subscribed: Set<String> = emptySet()
        var subscribedOn: com.kachat.app.services.grpc.KaspadConnection? = null
        try {
            while (true) {
                val addresses = notifier.watchedOwnAddresses()
                if (addresses.isEmpty()) {
                    delay(RESUBSCRIBE_CHECK_MS)
                    continue
                }
                // Read before the subscribe: a change landing during it re-subscribes again.
                val pathChange = nodePoolManager.networkPathChanges.value
                val (connection, generation, notifications) = try {
                    val conn = nodePoolManager.getUtxoSubscriptionConnection()
                    if (!conn.isConnected) conn.connect()
                    val gen = conn.connectionGeneration.value
                    Triple(conn, gen, conn.subscribeToUtxosChanged(addresses.toList()))
                } catch (e: Exception) {
                    // Only this job's own cancellation ends the loop. A subscribe that timed out is
                    // a CancellationException too, and rethrowing it ended the live balance
                    // for good, on a dead path above all (iOS 2da5f4b retries a failed subscribe).
                    currentCoroutineContext().ensureActive()
                    // Capped exponential backoff: a dead pool must not burn an attempt every second.
                    val delaySeconds = minOf(30.0, Math.pow(2.0, attempts.toDouble()))
                    attempts++
                    Log.w(TAG, "UTXO subscription failed (retry in ${delaySeconds.toInt()}s): ${e.message}")
                    notifier.liveSubscriptionActive = false
                    // A new network is worth trying at once, whatever the backoff had reached.
                    if (networkChangedWithin((delaySeconds * 1000).toLong(), pathChange)) attempts = 0
                    continue
                }
                attempts = 0
                subscribed = addresses
                subscribedOn = connection
                notifier.liveSubscriptionActive = true
                Log.i(TAG, "Real-time notifications active for ${addresses.size} addresses")
                // What arrived while there was no stream, and the balance as it is now.
                scope.launch {
                    notifier.runPassNow()
                    refreshBalances()
                }

                // Until the stream dies (a new generation) or the address set changes.
                val collector = scope.launch {
                    notifications.collect { message -> enqueue(message, walletAddress) }
                }
                try {
                    var ticks = 0
                    while (true) {
                        val pathChanged = networkChangedWithin(RESUBSCRIBE_CHECK_MS, pathChange)
                        if (pathChanged || connection.connectionGeneration.value != generation || !connection.isConnected) {
                            Log.i(TAG, "UTXO stream dropped - re-subscribing")
                            break
                        }
                        // The address set (cold-storage derivation included) every 30 s.
                        if (++ticks % ADDRESS_SET_CHECK_TICKS == 0 && notifier.watchedOwnAddresses() != subscribed) {
                            Log.i(TAG, "Own address set changed - re-subscribing")
                            break
                        }
                    }
                } finally {
                    collector.cancel()
                }
                notifier.liveSubscriptionActive = false
            }
        } finally {
            notifier.liveSubscriptionActive = false
            // Stop the node sending this account's changes once nobody is listening.
            val connection = subscribedOn
            if (connection != null && subscribed.isNotEmpty() && connection.isConnected) {
                scope.launch { runCatching { connection.unsubscribeFromUtxosChanged(subscribed.toList()) } }
            }
        }
    }

    /** Waits up to [ms]; true as soon as the default network has changed since [seen] (the old
     *  path's sockets died with it: iOS `UtxoSubscriptionManager.handleEpochChange`). */
    private suspend fun networkChangedWithin(ms: Long, seen: Long): Boolean =
        withTimeoutOrNull(ms) { nodePoolManager.networkPathChanges.first { it != seen } } != null

    private suspend fun enqueue(message: Rpc.UtxosChangedNotificationMessage, walletAddress: String) {
        // More than the Kaspa supply in one batch is a broken or hostile node's: dropped (iOS
        // 283cd28, IOS-020); the catch-up still finds anything real.
        val added = runCatching { liveEntries(message.addedList) }.getOrNull() ?: return
        val removed = runCatching { liveEntries(message.removedList) }.getOrNull() ?: return
        val txIds = (added + removed).map { it.transactionId }.toSet()
        if (txIds.isEmpty()) return
        queueMutex.withLock {
            val index = queue.indexOfFirst { it.txIds.any(txIds::contains) }
            if (index >= 0) {
                val existing = queue[index]
                queue[index] = Queued(
                    added = (existing.added + added).distinctBy { "${it.transactionId}:${it.index}" },
                    removed = (existing.removed + removed).distinctBy { "${it.transactionId}:${it.index}" },
                    txIds = existing.txIds + txIds,
                )
            } else {
                queue.addLast(Queued(added, removed, txIds))
            }
            if (processing) return
            processing = true
        }
        scope.launch { drain(walletAddress) }
    }

    /** Processes the queued batches one at a time, in order. */
    private suspend fun drain(walletAddress: String) {
        while (true) {
            val next = queueMutex.withLock {
                val item = queue.removeFirstOrNull()
                if (item == null) processing = false
                item
            } ?: return
            if (walletManager.activeAddressFlow.first() != walletAddress) continue
            try {
                notifier.handleLiveUtxoChanges(next.added, next.removed)
                refreshBalances()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Live UTXO batch failed", e)
            }
        }
    }

    private suspend fun refreshBalances() {
        runCatching {
            walletServiceLazy.get().refreshBalance()
            walletServiceLazy.get().refreshSpendingBalance()
        }
    }

    private fun liveEntries(entries: List<Rpc.RpcUtxosByAddressesEntry>): List<AddressActivityNotifier.LiveUtxo> =
        com.kachat.app.util.UtxoMath.checkedFromNetwork(entries) { it.utxoEntry.amount }.mapNotNull { entry ->
            val txId = entry.outpoint.transactionId
            if (entry.address.isEmpty() || txId.isEmpty()) return@mapNotNull null
            AddressActivityNotifier.LiveUtxo(
                address = entry.address,
                transactionId = txId,
                index = entry.outpoint.index,
                amount = entry.utxoEntry.amount,
            )
        }

    companion object {
        private const val TAG = "OwnAddressSubscription"
        /** How often the subscription checks its stream and the address set. */
        private const val RESUBSCRIBE_CHECK_MS = 5_000L
        private const val ADDRESS_SET_CHECK_TICKS = 6
    }
}
