package com.kachat.app.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.util.KaspaExtendedPublicKey
import com.kachat.app.util.KaspaUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Received" notifications: alerts the user when any of the wallet's own addresses - the main
 * address, spending-chain addresses (Manage Addresses) or watch-only cold-storage addresses -
 * receives Kaspa from someone else. Android port of iOS's `AddressActivityNotifier.swift`.
 *
 * As on iOS, two paths: the live one - [OwnAddressSubscription] streams the node's UTXO changes
 * for the wallet's own addresses into [handleLiveUtxoChanges] - and a catch-up diff for what
 * arrived while there was no stream: a per-wallet BASELINE + DIFF on app-foreground
 * ([onAppForeground]), when the subscription comes (back) up, from the periodic background worker
 * ([runPassNow]), and on a fallback poll ([POLL_INTERVAL_MS]) only while the subscription is down:
 *
 * - Per-wallet balance baselines persist across launches; the first run for a wallet (or a new
 *   address) seeds them silently and sets the address's floor - no dredging up history.
 * - A balance increase is attributed to the address's recent transactions newer than its floor,
 *   newest first, up to the increase. A tx with ANY input spending from one of our addresses is
 *   our own (change, consolidation, a send from another app on the same seed) and never
 *   notifies; an input the REST API hasn't resolved yet is looked up in the tx that created it
 *   ([spendsOwnFunds]). Unattributable increases notify a neutral "Balance increased".
 * - With remote push registered, the push service sends "Received" for the same addresses and the
 *   open app hides it - so the app announces a receive itself only while it is open, and one that
 *   landed while it was away is left to the push ([pushShowsReceive]).
 * - Handled txIds are persisted per wallet (cap 500) so passes never double-notify.
 * - [utxoActivityEvents] ALWAYS emits when watched balances change - a UI-refresh signal
 *   (Available pill, Manage Addresses, Cold Storage) NOT gated by the Address Activity setting.
 * - The notification itself is gated by Settings > Notifications > "Address Activity" (default
 *   ON) plus the global notifications toggle.
 */
@Singleton
class AddressActivityNotifier @Inject constructor(
    @ApplicationContext context: Context,
    private val walletManager: WalletManager,
    private val coldStorageManager: ColdStorageManager,
    private val networkService: NetworkService,
    private val settingsRepository: AppSettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val notificationCenter: GlobalNotificationCenterStore,
    // Lazy: WalletService is built from the chat stack, which this must not wait on at startup.
    private val walletServiceLazy: dagger.Lazy<WalletService>,
    /** Whether the push service is sending this device "Received" pushes (see [pushShowsReceive]). */
    private val pushState: PushState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.kachat.app.util.StartupSafety.logOnlyHandler("AddressActivityNotifier"))
    private val runMutex = Mutex()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private var pollJob: Job? = null

    /** Always-emitted UI refresh event: the set of watched own addresses whose balance changed
     *  in the last diff pass. NOT gated by the Address Activity setting - consumers (Available
     *  pill, Manage Addresses, Cold Storage screens) refresh regardless of notification prefs. */
    private val _utxoActivityEvents = MutableSharedFlow<Set<String>>(extraBufferCapacity = 8)
    val utxoActivityEvents: SharedFlow<Set<String>> = _utxoActivityEvents

    // --- Lifecycle --------------------------------------------------------------------

    /** Called from the process lifecycle observer on foreground: an immediate catch-up pass,
     *  then the foreground-only fallback poll - which does its pass only while the live UTXO
     *  subscription ([OwnAddressSubscription]) is down, as iOS's `startFallbackPolling`. */
    fun onAppForeground() {
        activeSinceMs = System.currentTimeMillis()
        scope.launch { runDiffPass() }
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (liveSubscriptionActive) continue
                try {
                    runDiffPass()
                } catch (e: Exception) {
                    Log.w(TAG, "Address activity poll failed", e)
                }
            }
        }
    }

    /** True while [OwnAddressSubscription] streams this wallet's UTXO changes: the poll then
     *  stands aside (iOS wallet 301ebfb `WalletService`: no fallback polling while subscribed). */
    @Volatile
    var liveSubscriptionActive: Boolean = false

    /**
     * Every address the live subscription watches (iOS `watchedOwnAddresses` plus the main
     * address): the main address, every revealed spending address and every cold-storage address
     * - muted ones too, so their changes still refresh the screens and count as ours.
     */
    fun watchedOwnAddresses(): Set<String> {
        val wallet = walletAddressOrNull() ?: return emptySet()
        val spending = runCatching { walletManager.allSpendingAddresses() }.getOrDefault(emptyList())
        return spending.toSet() + coldLabelByAddress().keys + wallet
    }

    /** Called on background - the loop freezes with the process, matching the app's
     *  foreground-only sync design. Next foreground runs the catch-up diff. */
    fun onAppBackground() {
        activeSinceMs = null
        pollJob?.cancel()
        pollJob = null
    }

    /** One pass, awaited - the periodic background worker's catch-up (KaChat Wallet's SyncWorker,
     *  iOS wallet 7f789f4 `runCatchUpIfNeeded`). */
    suspend fun runPassNow() {
        try {
            runDiffPass()
        } catch (e: Exception) {
            Log.w(TAG, "Address activity pass failed", e)
        }
    }

    /**
     * What the push service watches for this device's "Received" pushes (the registration's
     * `watch_only_addresses`): the main address, the spending addresses while their switch is on,
     * and every cold-storage address whose account notifies - the same choices the local
     * notifications follow. Empty while Address Activity is off. The service keeps 200 at most.
     * KaChat Wallet only (iOS wallet 1806a69): KaChat gets the main address's payments as chat pushes.
     */
    suspend fun pushWatchOnlyAddresses(): List<String> {
        if (!settingsRepository.addressActivityNotificationsEnabled.first()) return emptyList()
        val wallet = walletAddressOrNull() ?: return emptyList()
        val addresses = mutableSetOf(wallet)
        if (settingsRepository.spendingReceiveNotifications.first()) {
            addresses += runCatching { walletManager.allSpendingAddresses() }.getOrDefault(emptyList())
        }
        val cold = coldLabelByAddress()
        addresses += cold.keys - coldMutedAddresses
        return addresses.sorted().take(MAX_PUSH_WATCH_ONLY)
    }

    /** One immediate pass - used by the payment composer's post-send refresh path. */
    fun requestRefresh() {
        scope.launch {
            try {
                runDiffPass()
            } catch (e: Exception) {
                Log.w(TAG, "Address activity refresh failed", e)
            }
        }
    }

    /** Since when the app has been open this time (null while it isn't) - see [pushShowsReceive]. */
    @Volatile
    private var activeSinceMs: Long? = null

    // --- Watched sets -----------------------------------------------------------------

    /** The signed-in account's address; null while signed out, so nothing is watched, caught up
     *  or notified then (iOS guards on `currentWallet`). */
    private fun walletAddressOrNull(): String? {
        if (walletManager.isLoggedOut) return null
        return try { walletManager.getAddress() } catch (e: Exception) { null }
    }

    /** Cold-storage addresses whose account has receive notifications switched off. Still ours
     *  for the self-send check, and still watched for the UI refresh; never notified. */
    @Volatile
    private var coldMutedAddresses: Set<String> = emptySet()

    /** address -> cold account label, for every derived cold address of the active wallet. Also
     *  records which of them belong to an account that has been switched off. */
    private fun coldLabelByAddress(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val muted = mutableSetOf<String>()
        val accounts = try { coldStorageManager.getAccounts() } catch (e: Exception) { return result }
        for (account in accounts) {
            val parsed = KaspaExtendedPublicKey.parse(account.kpub).getOrNull() ?: continue
            val rootKey = try { KaspaExtendedPublicKey.toDeterministicKey(parsed) } catch (e: Exception) { continue }
            for (index in 0..account.maxDerivedIndex) {
                val address = try {
                    KaspaExtendedPublicKey.deriveChildAddress(rootKey, chain = 0, index = index)
                } catch (e: Exception) {
                    continue
                }
                result[address] = account.name
                if (!account.notifyOnReceive) muted.add(address)
            }
        }
        coldMutedAddresses = muted
        return result
    }

    // --- Live path (iOS wallet 42470af / 34f4b45 `handleLiveUtxoAdditions`) -----------------

    /** One UTXO in a live batch: who it belongs to and which output it is. */
    data class LiveUtxo(val address: String, val transactionId: String, val index: Int, val amount: Long)

    /** Txs the live path is checking right now - the catch-up leaves them to it. */
    private val inFlightTxIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Outpoints (`txid:index`) our addresses spent, from the live batches' removed entries - kept
     *  a few minutes so a receive whose input addresses the REST API hasn't filled in yet can
     *  still be recognised as our own spend. */
    private val recentOwnSpentOutpoints = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** When the live path last saw a receive to each address - the catch-up's neutral "Balance
     *  increased" stays quiet for those, since the live path announces that receive itself. */
    private val recentLiveHitAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * A live batch from [OwnAddressSubscription]: the UI refresh signal (always), then "Received"
     * for each transaction that put Kaspa on a notifiable address of ours - once its self-send
     * check (any input spending from our addresses, [spendsOwnFunds]) says it came from someone
     * else. The coins our addresses spent are remembered by outpoint for that check.
     */
    suspend fun handleLiveUtxoChanges(added: List<LiveUtxo>, removed: List<LiveUtxo>) {
        val walletAddress = walletAddressOrNull() ?: return
        val touched = (added + removed).map { it.address }.toSet()
        if (touched.isNotEmpty()) _utxoActivityEvents.tryEmit(touched)

        val spending = runCatching { walletManager.allSpendingAddresses() }.getOrDefault(emptyList()).toSet()
        val coldLabels = coldLabelByAddress()
        val ownAll = spending + coldLabels.keys + walletAddress
        val now = System.currentTimeMillis()
        for (entry in removed) {
            if (entry.address in ownAll) recentOwnSpentOutpoints[outpointKey(entry.transactionId, entry.index)] = now
        }
        pruneOwnSpends(now)
        val batchSpentOwn = removed.any { it.address in ownAll }

        val featureOn = settingsRepository.addressActivityNotificationsEnabled.first() &&
            settingsRepository.notificationsEnabled.first()
        if (!featureOn) return
        val spendingMuted = if (settingsRepository.spendingReceiveNotifications.first()) emptySet() else spending
        val notifiable = ownAll - coldMutedAddresses - spendingMuted
        // one entry per outpoint: the same batch delivered twice must not count an output twice
        val hitsByTx = added.filter { it.address in notifiable && it.amount > 0 }
            .distinctBy { outpointKey(it.transactionId, it.index) }
            .groupBy { it.transactionId }
        if (hitsByTx.isEmpty()) return

        recentLiveHitAt.entries.removeAll { now - it.value >= LIVE_HIT_TTL_MS }
        hitsByTx.values.flatten().forEach { recentLiveHitAt[it.address] = now }

        ensureHandledLoaded(walletAddress)
        for ((txId, hits) in hitsByTx) {
            if (isHandled(txId, walletAddress) || !inFlightTxIds.add(txId)) continue
            scope.launch {
                try {
                    resolveAndNotifyLive(txId, hits, ownAll, batchSpentOwn, walletAddress, coldLabels)
                } finally {
                    inFlightTxIds.remove(txId)
                }
            }
        }
    }

    private suspend fun resolveAndNotifyLive(
        txId: String,
        hits: List<LiveUtxo>,
        ownAddresses: Set<String>,
        batchSpentOwn: Boolean,
        walletAddress: String,
        coldLabels: Map<String, String>,
    ) {
        val api = networkService.kaspaRestApi.value
        // A seconds-old tx may not be on the REST API yet: a few tries, 2 s apart, as iOS.
        var tx: TransactionResponse? = null
        if (api != null) {
            for (attempt in 0 until LIVE_TX_FETCH_RETRIES) {
                tx = try { api.getTransactionWithInputAddresses(txId) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
                if (tx != null) break
                delay(LIVE_TX_FETCH_DELAY_MS)
            }
        }
        val spendsOwn = if (tx != null && api != null) spendsOwnFunds(tx, ownAddresses, api) else null
        // Inputs we couldn't place (or no tx at all): an own spend in the same batch makes it our
        // own change far more likely than a receive - e.g. a KaPosts post from this seed.
        val isSelfSend = spendsOwn ?: batchSpentOwn
        if (isHandled(txId, walletAddress)) return
        // the lookup above can take a while: an answer for an account no longer signed in is
        // dropped (iOS 2da5f4b)
        if (walletAddressOrNull() != walletAddress) return
        markHandled(txId, walletAddress)
        if (isSelfSend) {
            Log.i(TAG, "Suppressed self-send $txId (live)")
        } else if (pushShowsReceive(blockTimeMs = null)) {
            Log.i(TAG, "Receive ${txId.take(12)} left to the push (app not open)")
        } else {
            // An unreadable tx in a batch that spent nothing of ours still notifies: missing
            // real funds is worse than a rare stray ping.
            postReceiveHits(hits, coldLabels, txId)
        }
        refreshBaselines(hits.map { it.address }.toSet(), walletAddress)
    }

    /** The balances the live path just moved, written into the baselines so the next catch-up
     *  doesn't count them again (iOS `refreshBaselines`). */
    private suspend fun refreshBaselines(addresses: Set<String>, walletAddress: String) {
        val api = networkService.kaspaRestApi.value ?: return
        val fresh = addresses.mapNotNull { address ->
            runCatching { address to api.getBalance(address).balance }.getOrNull()
        }
        if (fresh.isEmpty()) return
        runMutex.withLock {
            if (walletAddressOrNull() != walletAddress) return@withLock
            val baselines = loadBaselines(walletAddress).toMutableMap()
            fresh.forEach { (address, balance) -> baselines[address] = balance }
            saveBaselines(baselines, walletAddress)
        }
    }

    private fun pruneOwnSpends(now: Long = System.currentTimeMillis()) {
        recentOwnSpentOutpoints.entries.removeAll { now - it.value >= OWN_SPENT_OUTPOINT_TTL_MS }
    }

    private fun outpointKey(txId: String, index: Int) = "${txId.lowercase()}:$index"

    // --- Core diff pass ---------------------------------------------------------------

    private suspend fun runDiffPass() = runMutex.withLock {
        val walletAddress = walletAddressOrNull() ?: return@withLock
        val api = networkService.kaspaRestApi.value ?: return@withLock

        val spending = walletManager.allSpendingAddresses().toSet()
        val coldLabels = coldLabelByAddress()
        // The chatting address is watched like the others (iOS 56b75c8), for plain transfers only.
        val watched = spending + coldLabels.keys + walletAddress

        // NOTIFIABLE = watched, the chatting address included (only its plain transfers count -
        // see attributeAndMaybeNotifyIncrease).
        // Per account: each cold-storage account has its own switch, and the spending chain has
        // one of its own. An account switched off is still ours - still watched, still counted
        // for the self-send test - just quiet (iOS ba352a2).
        val spendingMuted = if (settingsRepository.spendingReceiveNotifications.first()) emptySet() else spending
        val notifiable = watched - coldMutedAddresses - spendingMuted
        // "Ours" for the self-send input test includes the chatting address too.
        val ownAll = watched

        // Balances - one fetch per address (Android's REST client has no batched endpoint);
        // any individual failure keeps that address's previous baseline untouched.
        val currentBalances = mutableMapOf<String, Long>()
        for (address in watched) {
            val balance = try { api.getBalance(address).balance } catch (e: Exception) { return@withLock }
            currentBalances[address] = balance
        }

        val baselines = loadBaselines(walletAddress).toMutableMap()
        val isFirstRun = baselines.isEmpty()
        val featureOn = settingsRepository.addressActivityNotificationsEnabled.first() &&
            settingsRepository.notificationsEnabled.first()

        var changed = false
        val changedAddresses = mutableSetOf<String>()
        for ((address, balance) in currentBalances) {
            val previous = baselines[address]
            if (previous == null) {
                // First sighting of this address (feature install, newly revealed slot, fresh
                // cold import) - seed silently: its history predates our tracking, and its
                // receives count from now on.
                baselines[address] = balance
                setCatchUpFloor(System.currentTimeMillis(), address, walletAddress)
                changed = true
                continue
            }
            if (balance != previous) {
                changed = true
                // KaChat Wallet has no messages: its main address is a wallet address like the
                // rest, so its changes refresh the UI too (iOS wallet 301ebfb).
                changedAddresses.add(address)
            }
            if (balance > previous && !isFirstRun && address in notifiable) {
                attributeAndMaybeNotifyIncrease(
                    address = address,
                    delta = balance - previous,
                    walletAddress = walletAddress,
                    ownAll = ownAll,
                    coldLabels = coldLabels,
                    featureOn = featureOn,
                    api = api
                )
            }
            baselines[address] = balance
        }

        if (changed) {
            saveBaselines(baselines, walletAddress)
            if (changedAddresses.isNotEmpty()) {
                _utxoActivityEvents.tryEmit(changedAddresses)
                // KaChat Wallet: no message poll refreshes the balance, so a receive does (iOS
                // wallet 7f789f4, the fallback poll that refreshes the balance instead).
                runCatching {
                    walletServiceLazy.get().refreshBalance()
                    walletServiceLazy.get().refreshSpendingBalance()
                }
            }
        }
    }

    /**
     * Attributes a balance increase to its transaction(s) via recent history for the address
     * (iOS wallet 34f4b45 / 42470af `attributeAndNotifyIncrease`). Only receives newer than the
     * address's floor (the newest block time a pass already looked at, or when the address started
     * being tracked) count, newest first and no more than the increase - so older history is never
     * announced again. A tx that spends from any of our addresses is our own and never notifies
     * ([spendsOwnFunds]). If history can't attribute the increase at all, a neutral "Balance
     * increased" notification fires instead - unless the push service is the one to announce it.
     */
    private suspend fun attributeAndMaybeNotifyIncrease(
        address: String,
        delta: Long,
        walletAddress: String,
        ownAll: Set<String>,
        coldLabels: Map<String, String>,
        featureOn: Boolean,
        api: KaspaRestApi
    ) {
        ensureHandledLoaded(walletAddress)
        val transactions = try {
            api.getTransactions(address, limit = 10)
        } catch (e: Exception) {
            emptyList()
        }
        // An address tracked before floors existed has none yet: the increase alone bounds it.
        val floor = catchUpFloor(address, walletAddress)
        transactions.mapNotNull { it.blockTime }.maxOrNull()?.let { newest ->
            setCatchUpFloor(maxOf(newest, floor ?: 0L), address, walletAddress)
        }
        // The neutral fallback stays quiet when the receive is someone else's to announce: the
        // live path's (it just saw one here) or the push's (the app was away).
        val liveSawIt = recentLiveHitAt[address]?.let { System.currentTimeMillis() - it < LIVE_HIT_TTL_MS } == true
        val mayFallBack = featureOn && !liveSawIt && !pushShowsReceive(blockTimeMs = null)
        if (transactions.isEmpty()) {
            // Resolution failed entirely - still surface the funds, neutral wording.
            if (mayFallBack) postBalanceIncreased(address, delta, coldLabels)
            return
        }
        // Newest first, and only as many receives as the balance grew by: anything older was
        // already in the baseline. Own change doesn't count against the increase.
        val newestFirst = transactions.sortedByDescending { it.blockTime ?: Long.MAX_VALUE }
        var remaining = delta
        var attributedAny = false
        for (tx in newestFirst) {
            if (remaining <= 0) break
            val time = tx.blockTime
            if (floor != null && time != null && time <= floor) continue
            val toAddress = tx.outputs.filter { it.scriptPublicKeyAddress == address }.sumOf { it.amount }
            if (toAddress <= 0) continue
            attributedAny = true
            // Already announced, or the live path is on it right now.
            if (isHandled(tx.transactionId, walletAddress) || tx.transactionId in inFlightTxIds) {
                remaining -= minOf(remaining, toAddress)
                continue
            }
            val isSelfSend = spendsOwnFunds(tx, ownAll, api) ?: false
            if (isHandled(tx.transactionId, walletAddress) || tx.transactionId in inFlightTxIds) continue
            markHandled(tx.transactionId, walletAddress)
            if (isSelfSend) {
                Log.i(TAG, "Suppressed self-send ${tx.transactionId.take(12)}")
                continue
            }
            remaining -= minOf(remaining, toAddress)
            if (pushShowsReceive(blockTimeMs = time)) {
                Log.i(TAG, "Receive ${tx.transactionId.take(12)} left to the push (app not open)")
                continue
            }
            if (featureOn) {
                postReceive(totalSompi = toAddress, address = address, coldLabels = coldLabels, dedupeKey = tx.transactionId)
            }
        }
        if (!attributedAny && mayFallBack) {
            // Balance grew but none of the fetched recent txs pays this address (deep history
            // page or indexer lag) - neutral fallback rather than staying silent.
            postBalanceIncreased(address, delta, coldLabels)
        }
    }

    /**
     * Whether the push service already shows this receive, so the app must not (iOS wallet
     * 34f4b45): it pushes "Received" for the addresses this device registered, and the open app
     * hides that push. A receive from before the app opened this time - or any receive while it
     * isn't open - is the push's. [blockTimeMs] null means it is happening now.
     */
    private fun pushShowsReceive(blockTimeMs: Long?): Boolean {
        if (!pushState.isActive) return false
        val since = activeSinceMs
        if (!notificationHelper.isAppInForeground || since == null) return true
        if (blockTimeMs == null) return false
        return blockTimeMs < since
    }

    /**
     * Whether [tx] spends from any of our addresses (iOS wallet 42470af `spendsOwnFunds`): true
     * as soon as one input is ours, false when every input is placed and none is, null when some
     * input couldn't be placed. An input the REST API left unresolved (usual for a tx seconds old)
     * is looked up in the transaction that created it - so change from another app on the same
     * seed (a KaPosts post, a KaChat message) is never announced as a receive.
     */
    private suspend fun spendsOwnFunds(tx: TransactionResponse, ownAddresses: Set<String>, api: KaspaRestApi): Boolean? {
        val inputs = tx.inputs
        if (inputs.isEmpty()) return null
        pruneOwnSpends()
        val unresolved = mutableListOf<TransactionInput>()
        for (input in inputs) {
            val address = input.previousOutpointAddress
            if (!address.isNullOrEmpty()) {
                if (address in ownAddresses) return true
            } else {
                // a coin our addresses just spent, by outpoint (the live batches' removed entries)
                if (recentOwnSpentOutpoints.containsKey(outpointKey(input.previousOutpointHash, input.previousOutpointIndex))) return true
                unresolved += input
            }
        }
        var unknown = false
        val previousTxs = mutableMapOf<String, TransactionResponse?>()
        for (input in unresolved) {
            val hash = input.previousOutpointHash
            val previous = previousTxs.getOrPut(hash) {
                try { api.getTransaction(hash) } catch (e: Exception) { null }
            }
            val outputs = previous?.outputs.orEmpty()
            val spent = outputs.firstOrNull { it.index == input.previousOutpointIndex }
                ?: outputs.getOrNull(input.previousOutpointIndex)
            val address = spent?.scriptPublicKeyAddress
            if (address.isNullOrEmpty()) {
                unknown = true
                continue
            }
            if (address in ownAddresses) return true
        }
        return if (unknown) null else false
    }

    // --- Notifications ----------------------------------------------------------------

    /** One notification for a tx's outputs to our addresses, summed (iOS `postReceiveNotification`):
     *  the body names the address, or how many; the tap opens the one that got the most. */
    private suspend fun postReceiveHits(hits: List<LiveUtxo>, coldLabels: Map<String, String>, dedupeKey: String) {
        val byAddress = hits.groupBy { it.address }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val total = byAddress.values.sum()
        val opened = byAddress.maxByOrNull { it.value }?.key ?: return
        if (byAddress.size == 1) {
            postReceive(totalSompi = total, address = opened, coldLabels = coldLabels, dedupeKey = dedupeKey)
            return
        }
        if (total <= 0 || !notificationHelper.claimForBanner(dedupeKey)) return
        val title = "Received ${formatKas(total)} ${KaspaUnit.symbol}"
        val text = "${byAddress.size} of your addresses"
        notificationHelper.showAddressActivity(title = title, text = text, dedupeKey = dedupeKey, kind = kindOf(opened, coldLabels), address = opened)
        notificationCenter.record("wallet-$dedupeKey", "wallet", title, text, System.currentTimeMillis(), opened)
        Log.i(TAG, "Notified external receive ${dedupeKey.take(12)} (${hits.size} outputs)")
    }

    private suspend fun postReceive(totalSompi: Long, address: String, coldLabels: Map<String, String>, dedupeKey: String) {
        if (totalSompi <= 0) return
        // One banner per transaction: the FCM handler claims the same txid for a Received push.
        if (!notificationHelper.claimForBanner(dedupeKey)) return
        notificationHelper.showAddressActivity(
            title = "Received ${formatKas(totalSompi)} ${KaspaUnit.symbol}",
            text = describe(address, coldLabels),
            dedupeKey = dedupeKey,
            kind = kindOf(address, coldLabels),
            // One address per notification here (each receipt is posted per address), so the
            // tap always opens that address's History (iOS 13046ad).
            address = address
        )
        // Also list it in the Profile notifications bell; the row opens the same address's
        // History (iOS 95e2cba).
        notificationCenter.record("wallet-$dedupeKey", "wallet", "Received ${formatKas(totalSompi)} ${KaspaUnit.symbol}", describe(address, coldLabels), System.currentTimeMillis(), address)
        // a txid's start, or "bal-" and 8 characters of an address, never more of it
        Log.i(TAG, "Notified external receive ${dedupeKey.take(12)}")
    }

    private suspend fun postBalanceIncreased(address: String, delta: Long, coldLabels: Map<String, String>) {
        if (delta <= 0) return
        val dedupeKey = "bal-${address.takeLast(12)}-${System.currentTimeMillis()}"
        notificationHelper.showAddressActivity(
            title = "Balance increased by ${formatKas(delta)} ${KaspaUnit.symbol}",
            text = describe(address, coldLabels),
            dedupeKey = dedupeKey,
            kind = kindOf(address, coldLabels),
            // One address per notification here (each receipt is posted per address), so the
            // tap always opens that address's History (iOS 13046ad).
            address = address
        )
        // the bell's row opens the same address's History (iOS 95e2cba)
        notificationCenter.record("wallet-$dedupeKey", "wallet", "Balance increased by ${formatKas(delta)} ${KaspaUnit.symbol}", describe(address, coldLabels), System.currentTimeMillis(), address)
    }

    /** Which wallet screen the notification's tap should open - mirrors iOS's `kindKey(for:)`,
     *  which puts the same string in the notification's `userInfo["kind"]`. */
    private fun kindOf(address: String, coldLabels: Map<String, String>): String = when {
        address == walletAddressOrNull() -> NotificationHelper.KIND_CHATTING
        coldLabels[address] != null -> NotificationHelper.KIND_COLD
        else -> NotificationHelper.KIND_SPENDING
    }

    private fun describe(address: String, coldLabels: Map<String, String>): String {
        if (address == walletAddressOrNull()) return "Chatting address ${shortAddress(address)}"
        val label = coldLabels[address]
        return if (label != null) {
            "Cold storage ($label) ${shortAddress(address)}"
        } else {
            "Spending address ${shortAddress(address)}"
        }
    }

    // --- Persistence (per wallet) -----------------------------------------------------

    private fun baselinesKey(walletAddress: String) = "baselines_$walletAddress"

    // Catch-up floors (iOS wallet 34f4b45): per address, the newest block time (ms) a pass has
    // already looked at, or when the address started being tracked - older receives were before
    // it was watched, or already handled, and are never announced.
    private fun catchUpFloorsKey(walletAddress: String) = "addr_activity_floors_$walletAddress"

    private fun loadCatchUpFloors(walletAddress: String): Map<String, Long> {
        val json = prefs.getString(catchUpFloorsKey(walletAddress), null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, Long>>() {}.type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun catchUpFloor(address: String, walletAddress: String): Long? =
        loadCatchUpFloors(walletAddress)[address] ?: if (address == walletAddress) {
            // The main address's floor used to be kept on its own, set when it was first tracked.
            prefs.getLong("addr_activity_chat_floor_${walletAddress.lowercase()}", 0L).takeIf { it > 0 }
        } else {
            null
        }

    private fun setCatchUpFloor(floor: Long, address: String, walletAddress: String) {
        val floors = loadCatchUpFloors(walletAddress).toMutableMap()
        if (floors[address] == floor) return
        floors[address] = floor
        prefs.edit().putString(catchUpFloorsKey(walletAddress), gson.toJson(floors)).apply()
    }
    private fun handledKey(walletAddress: String) = "handled_txids_$walletAddress"

    private fun loadBaselines(walletAddress: String): Map<String, Long> {
        val json = prefs.getString(baselinesKey(walletAddress), null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveBaselines(baselines: Map<String, Long>, walletAddress: String) {
        prefs.edit().putString(baselinesKey(walletAddress), gson.toJson(baselines)).apply()
    }

    private var handledOrder: MutableList<String> = mutableListOf()
    private var handledSet: MutableSet<String> = mutableSetOf()
    private var handledLoadedFor: String? = null

    @Synchronized
    private fun ensureHandledLoaded(walletAddress: String) {
        if (handledLoadedFor == walletAddress) return
        val json = prefs.getString(handledKey(walletAddress), null)
        val list: List<String> = if (json == null) emptyList() else try {
            gson.fromJson(json, Array<String>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
        handledOrder = list.toMutableList()
        handledSet = list.toMutableSet()
        handledLoadedFor = walletAddress
    }

    @Synchronized
    private fun isHandled(txId: String, walletAddress: String): Boolean {
        ensureHandledLoaded(walletAddress)
        return txId in handledSet
    }

    @Synchronized
    private fun markHandled(txId: String, walletAddress: String) {
        ensureHandledLoaded(walletAddress)
        if (!handledSet.add(txId)) return
        handledOrder.add(txId)
        while (handledOrder.size > HANDLED_TX_ID_CAP) {
            handledSet.remove(handledOrder.removeAt(0))
        }
        prefs.edit().putString(handledKey(walletAddress), gson.toJson(handledOrder)).apply()
    }

    companion object {
        private const val TAG = "AddressActivity"
        private const val PREFS_NAME = "kachat_address_activity"
        private const val HANDLED_TX_ID_CAP = 500
        /** The fallback poll while there is no live subscription - iOS's `pollDelayAfterSync`. */
        private const val POLL_INTERVAL_MS = 60_000L
        private const val OWN_SPENT_OUTPOINT_TTL_MS = 600_000L
        private const val LIVE_HIT_TTL_MS = 180_000L
        private const val LIVE_TX_FETCH_RETRIES = 6
        private const val LIVE_TX_FETCH_DELAY_MS = 2_000L

        /** The push service keeps this many watch-only addresses at most. */
        private const val MAX_PUSH_WATCH_ONLY = 200

        internal fun shortAddress(a: String): String =
            if (a.length <= 20) a else a.take(14) + "..." + a.takeLast(6)

        internal fun formatKas(sompi: Long): String {
            val kas = sompi.toDouble() / 100_000_000.0
            return String.format(java.util.Locale.US, "%.8f", kas).trimEnd('0').trimEnd('.')
        }
    }
}
