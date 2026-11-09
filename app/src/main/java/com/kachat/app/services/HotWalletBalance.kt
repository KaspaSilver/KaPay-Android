package com.kachat.app.services

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the whole hot wallet holds: the chatting address plus every spending address, hidden ones
 * included. Cold storage isn't part of it. The top bar on every tab and the Wallet tab's balance
 * card all read this, so they can't disagree (iOS 953b255 `HotWalletBalance`).
 */
@Singleton
class HotWalletBalance @Inject constructor(
    private val walletManager: WalletManager,
    private val nodePoolManager: NodePoolManager,
    walletService: WalletService,
    addressActivityNotifier: AddressActivityNotifier,
) {
    /** The figure and the account it was read for. */
    private data class Read(val account: String, val sompi: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val read = MutableStateFlow<Read?>(null)

    // The state below is only touched on the main thread (see [refresh]), iOS's @MainActor.
    /** The account [read] and [lastReadAt] belong to. */
    private var account: String? = null
    private var lastReadAt: Long? = null
    /** Only the newest read counts: an older one finishing late can't put an old figure back. */
    private var readGeneration = 0

    /** The active account's spending addresses together, or null until they're known. */
    val spendingSompi: StateFlow<Long?> =
        combine(walletManager.activeAddressFlow, read) { address, current ->
            current?.takeIf { it.account == address }?.sompi
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The chatting address's balance, or null until a fetch has answered: never a made-up 0. */
    val chattingSompi: StateFlow<Long?> =
        combine(walletService.balance, walletService.balanceKnown) { balance, known ->
            balance.takeIf { known }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The whole hot wallet, or null until both parts are known: a total missing one of them
     *  would read as the whole balance. */
    val totalSompi: StateFlow<Long?> =
        combine(chattingSompi, spendingSompi) { chatting, spending ->
            if (chatting != null && spending != null) chatting + spending else null
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // One transaction can bring several UTXO changes: they make one read.
        scope.launch {
            addressActivityNotifier.utxoActivityEvents.collectLatest {
                delay(500L)
                refresh(force = true)
            }
        }
    }

    /** Reads the spending addresses, in one call. A failed read keeps what it last knew. */
    suspend fun refresh(force: Boolean = false): Unit = withContext(Dispatchers.Main.immediate) {
        val address = walletManager.activeAddressFlow.value
        if (address != account) {
            account = address
            read.value = null
            lastReadAt = null
        }
        if (address == null) return@withContext
        val last = lastReadAt
        if (!force && last != null && System.currentTimeMillis() - last < MIN_READ_INTERVAL_MS) return@withContext

        val all = withContext(Dispatchers.Default) { runCatching { walletManager.allSpendingAddresses() }.getOrDefault(emptyList()) }
        if (all.isEmpty()) return@withContext
        val generation = ++readGeneration
        val balances = withContext(Dispatchers.IO) { nodePoolManager.getBalancesByAddresses(all) }
        if (balances == null) {
            Log.w(TAG, "Reading the spending addresses failed; keeping the last known figure")
            return@withContext
        }
        if (generation != readGeneration || walletManager.activeAddressFlow.value != address) return@withContext
        read.value = Read(address, balances.values.sum())
        lastReadAt = System.currentTimeMillis()
    }

    /** For a top bar: refreshes, then keeps trying while the figure is unknown (the node may not
     *  be connected yet just after launch), so it doesn't stay a dash. */
    suspend fun refreshUntilKnown() {
        refresh()
        while (currentCoroutineContext().isActive && spendingSompi.value == null && hasSpendingAddresses()) {
            delay(UNKNOWN_RETRY_MS)
            refresh(force = true)
        }
    }

    private suspend fun hasSpendingAddresses(): Boolean = withContext(Dispatchers.Default) {
        runCatching { walletManager.allSpendingAddresses() }.getOrDefault(emptyList()).isNotEmpty()
    }

    private companion object {
        const val TAG = "HotWalletBalance"
        /** Each tab's top bar asks when it appears; this keeps tab switches from reading each
         *  time. Arriving funds refresh at once. */
        const val MIN_READ_INTERVAL_MS = 30_000L
        const val UNKNOWN_RETRY_MS = 5_000L
    }
}
