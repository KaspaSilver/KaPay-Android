package com.kachat.app.services

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kachat.app.util.KaspaAddress
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WalletService — handles high-level wallet operations like balance tracking
 * and transaction orchestration (Build -> Sign -> Broadcast).
 *
 * This matches the WalletService/ChatService logic in the iOS app.
 */
@Singleton
class WalletService @Inject constructor(
    private val networkService: NetworkService,
    private val walletManager: WalletManager,
    private val walletEngine: KaspaWalletEngine,
    private val knsService: KnsService,
    /** .k and .kaspa names for the chatting-address scan (iOS 7a5b157). */
    private val nameServices: NameServicesClient,
    private val knsInscriptionEngine: KnsInscriptionEngine,
    /** For the KNS transfer refusals' localized text (iOS 2a8ca5a). */
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) {
    private val gson = Gson()

    private val _balance = MutableStateFlow(0L)
    val balance: StateFlow<Long> = _balance.asStateFlow()

    // [balance]'s 0L initial value is indistinguishable from a genuinely empty wallet, so
    // consumers that must react only to a *confirmed* zero (ChatThreadScreen's funding gate)
    // check this alongside it — it flips true the first time a balance fetch for the chatting
    // (identity) address actually succeeds, and only ever flips back on an account switch
    // (see resetBalancesForAccountSwitch: the new account's balance is unknown again).
    private val _balanceKnown = MutableStateFlow(false)
    val balanceKnown: StateFlow<Boolean> = _balanceKnown.asStateFlow()

    private val _spendingBalance = MutableStateFlow(0L)
    val spendingBalance: StateFlow<Long> = _spendingBalance.asStateFlow()

    /**
     * The REST API client is built asynchronously off [AppSettingsRepository]'s persisted URL
     * (see [NetworkService.observeSettings]), so right after a cold app launch it can still be
     * null for the first tens-of-milliseconds while that first DataStore read resolves. Every
     * caller here used to just no-op on null with no retry, so a refresh that raced this window
     * (as [WalletViewModel]'s own init block always does) silently never happened, leaving
     * balance/UTXOs stale until some later, unrelated action happened to call this again — this
     * waits briefly instead, bounded so a genuinely broken custom endpoint doesn't hang forever.
     */
    private suspend fun readyApi(): KaspaRestApi? =
        networkService.kaspaRestApi.value ?: withTimeoutOrNull(10_000) { networkService.kaspaRestApi.filterNotNull().first() }

    /**
     * Account switch: zero the balances and drop [balanceKnown] back to false so the new account
     * never renders the previous account's numbers while its own first fetch is in flight — a
     * brand-new account would otherwise flash the old account's balance. Called by
     * WalletViewModel's activeAddressFlow collector, the same reset point the KNS profile state
     * uses.
     */
    fun resetBalancesForAccountSwitch() {
        _balance.value = 0L
        _spendingBalance.value = 0L
        _balanceKnown.value = false
    }

    suspend fun refreshBalance() {
        val address = try { walletManager.getAddress() } catch (e: Exception) { return }
        val api = readyApi() ?: return

        try {
            // Node first (the sum of the address's UTXOs, as iOS computes it), the REST
            // gateway second - see KaspaWalletEngine.nodeBalance.
            val balance = walletEngine.nodeBalance(address) ?: api.getBalance(address).balance
            // A fetch that raced an account switch must not stamp the OLD account's balance
            // under the new one.
            if ((try { walletManager.getAddress() } catch (e: Exception) { null }) != address) return
            _balance.value = balance
            _balanceKnown.value = true
        } catch (e: Exception) {
            Log.e("WalletService", "Error refreshing balance", e)
        }
    }

    suspend fun refreshSpendingBalance() {
        val address = try { walletManager.currentSpendingAddress() } catch (e: Exception) { return }
        val api = readyApi() ?: return

        try {
            val balance = walletEngine.nodeBalance(address) ?: api.getBalance(address).balance
            // Same account-switch race guard as refreshBalance (the spending address is
            // per-account too).
            if ((try { walletManager.currentSpendingAddress() } catch (e: Exception) { null }) != address) return
            _spendingBalance.value = balance
        } catch (e: Exception) {
            Log.e("WalletService", "Error refreshing spending balance", e)
        }
    }

    /**
     * Orchestrates a Kaspa payment: Fetch UTXOs -> Build -> Sign -> Broadcast, from the
     * identity (chatting) address.
     * @return The transaction ID if successful.
     */
    suspend fun sendKaspa(
        toAddress: String,
        amountSompi: Long,
        payloadBytes: ByteArray? = null,
        feeRateOverride: Long? = null,
        allowReducedAmount: Boolean = false,
        /** Coin control and the extra fee - the Send KAS sheet with Chats Payment Privacy off
         *  (iOS 62c2773). Defaults keep every other caller unchanged. */
        manualUtxos: List<UtxoEntry>? = null,
        extraFeeSompi: Long = 0L
    ): String {
        val result = walletEngine.sendKaspa(
            toAddress, amountSompi, payloadBytes,
            feeRateOverride = feeRateOverride,
            manualUtxos = manualUtxos,
            allowReducedAmount = allowReducedAmount,
            extraFeeSompi = extraFeeSompi
        )

        if (result.isSuccess) {
            refreshBalance()
            return result.getOrThrow()
        } else {
            throw result.exceptionOrNull() ?: Exception("Unknown error during Kaspa send")
        }
    }

    /** Addresses a history lookup found unused this session (iOS `sessionUnusedAddresses`): "unused"
     *  can change any time, so it is never persisted, only remembered until the app restarts. */
    private val sessionUnusedAddresses = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** What is already known about [address]'s use without a lookup (iOS
     *  `cachedSpendingAddressUsedState`): true once it was ever seen used, false when a lookup this
     *  session found it unused, null when unknown. */
    fun cachedSpendingAddressUsedState(address: String): Boolean? = when {
        walletManager.isAddressKnownUsed(address) -> true
        address in sessionUnusedAddresses -> false
        else -> null
    }

    /** A send just spent from [address] or put its change there: this session's "never used" answer
     *  is out of date (iOS 2da5f4b `markSpendingAddressUsed`). "Used" is monotonic, so it's kept. */
    fun markSpendingAddressUsed(address: String) {
        sessionUnusedAddresses.remove(address)
        walletManager.markAddressUsed(address)
    }

    /**
     * Whether [address] was ever used (iOS `spendingAddressUsedState`, 338d9f5): the cached answer,
     * else a one-row history lookup. True = used (remembered for good: "used" is monotonic), false
     * = confirmed unused, null = the lookup failed, so used-ness is unknown right now - never
     * guessed as unused.
     */
    suspend fun spendingAddressUsedState(address: String): Boolean? {
        cachedSpendingAddressUsedState(address)?.let { return it }
        val api = readyApi() ?: return null
        return try {
            val used = api.getTransactions(address, limit = 1).isNotEmpty()
            if (used) {
                sessionUnusedAddresses.remove(address)
                walletManager.markAddressUsed(address)
            } else {
                sessionUnusedAddresses.add(address)
            }
            used
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    data class SpendingAddressEntry(
        val index: Int,
        val address: String,
        val balanceSompi: Long,
        val everUsed: Boolean,
        val isCurrent: Boolean,
        val hidden: Boolean = false,
        val label: String? = null,
        // Whether this load actually confirmed the row's balance AND used-ness live. A throttled
        // or failed lookup used to be swallowed as balance=0/everUsed=false — indistinguishable
        // from a genuinely fresh address, which is how Generate kept re-offering the same low
        // index. Old persisted snapshots lack this field (Gson defaults it to false), which is
        // safe: only Generate consults it, and Generate only ever reads the live list.
        val liveChecked: Boolean = true
    ) {
        /** A quantum-safe (KIP-22) slot: public index [WalletManager.QUANTUM_INDEX_BASE] + n.
         *  Computed, so snapshots from before the upgrade decode unchanged. */
        val isQuantum: Boolean get() = WalletManager.isQuantumIndex(index)

        /** The row's number on its own chain, "Address #n" on both (quantum restarts at 0). */
        val displayTitle: String get() = WalletManager.spendingAddressTitle(index)

        /** Its nickname, else [displayTitle] (iOS `SpendingAddressEntry.displayLabel`). */
        val displayLabel: String get() = label?.trim()?.takeIf { it.isNotEmpty() } ?: displayTitle
    }

    /**
     * Whether a quantum-safe (KIP-22) address has ever SPENT - the one event that puts its key on
     * chain (receiving reveals nothing; iOS `ChatService.quantumAddressSpentState`). true is
     * marked locally and permanent; false means no spend in its whole history; null when the
     * history can't be read right now, or is deeper than the walk goes.
     *
     * The whole history, a page at a time ([quantumSpentFromPages]): a spend buried under later
     * activity still revealed the key (iOS da2c8e2, IOS-105 / AND-051). A found spend is marked
     * for good, so the walk runs once per address; a page that can't be read leaves the answer
     * unknown, never "not spent".
     */
    suspend fun quantumAddressSpentState(address: String): Boolean? {
        if (walletManager.isQuantumAddressSpentLocally(address)) return true
        // the account whose address this is; the pages below can span an account switch, and a
        // found spend is marked for it - nowhere once it was removed (iOS ca09dd0, IOS-109 /
        // AND-056)
        val owner = walletManager.getActiveAccount()?.address
        val api = readyApi() ?: return null
        val spent = quantumSpentFromPages(address) { offset, limit ->
            try {
                api.getTransactions(address, limit = limit, offset = offset)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        if (spent == true) walletManager.markQuantumAddressSpent(address, owner = owner)
        return spent
    }

    /**
     * Every spending-chain address derived/shown so far for the active account — index 0 through
     * the higher of the current active index and the highest one the Manage Addresses screen has
     * generated — each with its live balance and whether it's ever had any on-chain history
     * (so the UI can steer the user away from reusing an already-used address).
     *
     * Returns an EMPTY list only when nothing could be loaded live (no account, API unreachable,
     * or every balance lookup failed) — a real wallet always lists at least index 0, so callers
     * can treat empty as "the live check failed", and [WalletViewModel.generateNewSpendingAddress]
     * does exactly that. A failed load never overwrites the persisted snapshot.
     */
    suspend fun getSpendingAddressList(): List<SpendingAddressEntry> {
        val account = walletManager.getActiveAccount() ?: return emptyList()
        val maxIndex = WalletManager.legacyMaxSpendingIndex(account)
        val api = readyApi() ?: return emptyList()
        val hiddenIndices = walletManager.getHiddenSpendingIndices(account.address)
        val labels = walletManager.getSpendingAddressLabels(account.address)
        val addressByIndex = (0..maxIndex).associateWith { walletManager.deriveSpendingAddress(it) }.toMutableMap()
        // Both chains (iOS getSpendingAddressList): the quantum-safe slots after the legacy ones,
        // derived with one seed computation for the whole range.
        val quantumMax = WalletManager.maxQuantumIndex(account)
        if (quantumMax >= 0) {
            val base = WalletManager.QUANTUM_INDEX_BASE
            val quantum = walletManager.deriveSpendingAddresses(base until base + quantumMax + 1)
            if (quantum.size != quantumMax + 1) return emptyList()
            addressByIndex.putAll(quantum)
        }
        // Balances in ONE batched round trip (with fetchBalancesBatched's own per-address
        // fallback) instead of the old per-index burst — 2x(maxIndex+1) simultaneous requests
        // against the shared REST host routinely got throttled, and every swallowed failure
        // painted a funded/used row as fresh. An entirely-empty result means the host answered
        // nothing: bail out as a failed load rather than fabricate an all-zero list.
        val balances = fetchBalancesBatched(addressByIndex.values.toList())
        if (balances.isEmpty()) return emptyList()
        // A failed read isn't an empty address: the last figures known, which also keeps a funded
        // address in the From lists (iOS 2da5f4b). Such a row stays not live-checked.
        val lastKnownBalances: Map<String, Long> = if (addressByIndex.values.all { it in balances }) emptyMap()
        else cachedSpendingAddressList().associate { it.address to it.balanceSompi }
        // Re-read the primary index AFTER the slow balance round trip — a send can rotate it
        // mid-load, and stamping rows with the index captured before the fetch is how a stale
        // isCurrent used to land in the committed list and the persisted snapshot. Display
        // derives isCurrent live anyway (WalletViewModel.manageAddresses); this keeps the
        // built rows and the self-heal below as fresh as possible too.
        val primaryNow = walletManager.getActiveAccount()?.spendingAddressIndex ?: account.spendingAddressIndex
        // "Used" is monotonic — a persisted positive answer skips the history probe forever;
        // only never-used addresses re-probe (they can become used anytime). Probes run in small
        // chunks, not one big burst, for the same rate-limit reason as above.
        val entries = coroutineScope {
            addressByIndex.keys.sorted().chunked(4).flatMap { chunk ->
                chunk.map { index ->
                    async {
                        val address = addressByIndex.getValue(index)
                        val balance = balances[address]
                        var confirmed = balance != null
                        val everUsed = when {
                            walletManager.isAddressKnownUsed(address) -> true
                            (balance ?: 0L) > 0L -> { walletManager.markAddressUsed(address); true }
                            else -> {
                                val used = try {
                                    api.getTransactions(address, limit = 1).isNotEmpty()
                                } catch (e: Exception) {
                                    confirmed = false
                                    false
                                }
                                if (used) walletManager.markAddressUsed(address)
                                used
                            }
                        }
                        SpendingAddressEntry(
                            index, address, balance ?: lastKnownBalances[address] ?: 0L, everUsed,
                            isCurrent = index == primaryNow,
                            hidden = index in hiddenIndices,
                            label = labels[index],
                            liveChecked = confirmed
                        )
                    }
                }.awaitAll()
            }
        }
        // Any address may be hidden - funded ones and the current one too (iOS 245a5d2: Address
        // Visibility > Hide All starts the list over), so nothing here forces a row visible.
        // Persist the snapshot so the next open paints instantly (see cachedSpendingAddressList).
        try { walletManager.setManageAddressesSnapshot(account.address, gson.toJson(entries)) } catch (e: Exception) { /* best-effort */ }
        return entries
    }

    /** Last fully-loaded Manage Addresses list for the active account — instant-paint cache;
     *  balances may be a refresh stale, the live load replaces them. `isCurrent` is recomputed
     *  against the LIVE primary index (the snapshot's copy goes stale the moment a send rotates
     *  it), and the hidden flags are the persisted set's, as the live load paints them. */
    fun cachedSpendingAddressList(): List<SpendingAddressEntry> {
        val account = walletManager.getActiveAccount() ?: return emptyList()
        val json = walletManager.getManageAddressesSnapshot(account.address) ?: return emptyList()
        val type = object : TypeToken<List<SpendingAddressEntry>>() {}.type
        val raw: List<SpendingAddressEntry> = try { gson.fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() }
        val hiddenIndices = walletManager.getHiddenSpendingIndices(account.address)
        return raw.map { entry ->
            val isCurrent = entry.index == account.spendingAddressIndex
            // liveChecked means "confirmed by the fetch that produced THIS object" — a snapshot
            // row was confirmed in some PREVIOUS session at best, so it comes back untrusted.
            // Only a fresh getSpendingAddressList load can mark rows live-confirmed.
            entry.copy(
                isCurrent = isCurrent,
                hidden = entry.index in hiddenIndices,
                liveChecked = false
            )
        }
    }

    /**
     * One-time visibility seeding right after an import's spending-index recovery
     * ([WalletViewModel.commitImport] -> [SpendingAddressDiscovery.discoverIndex]): the recovery
     * raises the index bounds to cover every previously used slot, and with a brand-new install's
     * EMPTY hidden set the default "visible unless hidden" painted all of 0..maxIndex — burned
     * change indices, gap addresses, old rotated primaries — as rows the user never revealed
     * here. The expected initial set is the primary plus anything holding a balance; every other
     * recovered index starts hidden (it stays reachable via Address Visibility and Generate's
     * recycling, which un-hides the lowest hidden unused index).
     *
     * Explicit user state always wins: if this wallet already has hidden entries or a Manage
     * Addresses snapshot on this device (e.g. restored from an OS backup), the user curated their
     * visibility before and nothing is rewritten. When the balance fetch fails entirely, seeding
     * still hides the non-primary rows — hiding a funded row is self-healing (the list loader
     * force-unhides primary/funded rows on every successful load), while painting dozens of
     * random rows is not.
     *
     * For [restored], the account the restore started for, whichever account is active by now
     * (iOS e571f45, IOS-107): its own record, chain and hidden set; nothing once it was removed.
     */
    suspend fun seedImportedSpendingVisibility(restored: WalletManager.SpendingSweepAccount) {
        val account = walletManager.sweepAccountRecord(restored) ?: return
        val primary = account.spendingAddressIndex
        val maxIndex = WalletManager.legacyMaxSpendingIndex(account)
        if (maxIndex <= 0) return
        if (walletManager.getHiddenSpendingIndices(account.address).isNotEmpty()) return
        if (walletManager.getManageAddressesSnapshot(account.address) != null) return
        val addressByIndex = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            walletManager.deriveSpendingAddresses(0..maxIndex, restored)
        }
        val balances = fetchBalancesBatched(addressByIndex.values.toList())
        if (!walletManager.sweepAccountStillSaved(restored)) return
        for ((index, address) in addressByIndex) {
            if (index == primary) continue
            if ((balances[address] ?: 0L) > 0L) continue
            walletManager.setSpendingAddressHidden(account.address, index, true)
        }
    }

    /** Whether [address] has any on-chain history — the same single-tx probe the list loader
     *  uses, exposed for Address Visibility rows derived beyond the loaded list. Positive
     *  answers persist (monotonic) so re-opens skip the round-trip. */
    suspend fun hasSpendingAddressBeenUsed(address: String): Boolean {
        if (walletManager.isAddressKnownUsed(address)) return true
        val api = readyApi() ?: return false
        val used = try { api.getTransactions(address, limit = 1).isNotEmpty() } catch (e: Exception) { false }
        if (used) walletManager.markAddressUsed(address)
        return used
    }

    /**
     * One scanned identity-chain slot for the import wizard's "Change Chatting Address" picker —
     * mirrors iOS's `ChattingAddressCandidate`. The picker lists only the interesting ones
     * (balance or domains), always including index 0 and the current index.
     */
    data class ChattingAddressCandidate(
        val index: Int,
        val address: String,
        val balanceSompi: Long,
        val domains: List<KnsAsset>,
        val primaryDomain: String?,
        /** Names on the other services (.k, .kaspa). */
        val otherNames: List<OwnedServiceName> = emptyList(),
        /** .kachat names (active or in grace) where the registry is live - listed first (iOS 6ac48a7). */
        val kachatNames: List<OwnedServiceName> = emptyList(),
    ) {
        val nameCount: Int get() = kachatNames.size + domains.size + otherNames.size
        /** The one name to show on the row when there is exactly one. */
        val onlyName: String? get() = if (nameCount != 1) null
            else kachatNames.firstOrNull()?.display ?: domains.firstOrNull()?.asset ?: otherNames.firstOrNull()?.display
        val isInteresting: Boolean get() = balanceSompi > 0L || nameCount > 0
    }

    /**
     * Derives [indices] on the active account's identity chain — WITHIN its own source family,
     * see [WalletManager.deriveChattingAddresses] — and checks the whole batch for KAS balance
     * (one batched `POST addresses/balances` call, with a bounded-concurrency per-address sweep as
     * fallback) and KNS domains (the existing [KnsService.getOwnedDomainsCached] batch, four
     * concurrent requests at a time, same as the address lists' "Contains domain" tags). Never
     * fires 50 raw balance requests.
     *
     * Returns ALL derived candidates in index order; the caller filters for interesting ones. Null
     * when derivation itself fails (no active account / unusable seed), which the UI surfaces as
     * an error rather than an empty scan.
     */
    suspend fun scanChattingAddressCandidates(indices: IntRange): List<ChattingAddressCandidate>? = coroutineScope {
        val derived = walletManager.deriveChattingAddresses(indices)
        if (derived.isEmpty()) return@coroutineScope null
        val addresses = derived.map { it.second }

        val balanceByAddress = fetchBalancesBatched(addresses)

        // Domains + primary domain, both off the cached-per-address KNS lookups.
        val domainsByAddress = mutableMapOf<String, List<KnsAsset>>()
        for (chunk in addresses.chunked(4)) {
            val results = chunk.map { address ->
                async { address to (try { knsService.getOwnedDomainsCached(address) } catch (e: Exception) { emptyList<KnsAsset>() }) }
            }.awaitAll()
            results.forEach { (address, domains) -> domainsByAddress[address] = domains }
        }
        // .k and .kaspa too: an identity can live at an address whose only trace is a name on
        // one of them.
        val otherNamesByAddress = nameServices.ownedNames(of = addresses)
        val kachatNamesByAddress = runCatching { nameServices.kachatNames(addresses) }.getOrDefault(emptyMap())

        derived.map { (index, address) ->
            val domains = domainsByAddress[address].orEmpty()
            ChattingAddressCandidate(
                index = index,
                address = address,
                balanceSompi = balanceByAddress[address] ?: 0L,
                domains = domains,
                // Only worth a reverse-lookup round trip for addresses that actually own domains.
                primaryDomain = if (domains.isEmpty()) null else {
                    try { knsService.reverseResolve(address) } catch (e: Exception) { null }
                },
                otherNames = otherNamesByAddress[address].orEmpty(),
                kachatNames = kachatNamesByAddress[address].orEmpty(),
            )
        }
    }

    /** One batched balances call, degrading to a chunked-concurrent sweep when the configured REST
     *  host doesn't implement the batch endpoint. Missing/failed addresses simply come back absent
     *  (treated as zero by the caller). */
    private suspend fun fetchBalancesBatched(addresses: List<String>): Map<String, Long> = coroutineScope {
        val api = readyApi() ?: return@coroutineScope emptyMap()
        try {
            api.getBalances(BalancesRequest(addresses)).associate { it.address to it.balance }
        } catch (e: Exception) {
            Log.w("WalletService", "Batched balances unavailable, falling back to per-address sweep", e)
            val out = mutableMapOf<String, Long>()
            for (chunk in addresses.chunked(8)) {
                chunk.map { address ->
                    async { address to (try { api.getBalance(address).balance } catch (e: Exception) { null }) }
                }.awaitAll().forEach { (address, balance) -> if (balance != null) out[address] = balance }
            }
            out
        }
    }

    /**
     * Makes the identity-chain address at [index] this account's chatting address, returning the
     * new address. Delegates straight to [WalletManager.switchChattingAddress] (which rewrites the
     * account record and routes the change through the normal account-switch machinery); the
     * caller is responsible for refreshing its own derived UI state afterwards.
     */
    fun switchChattingAddress(index: Int): String = walletManager.switchChattingAddress(index)

    /**
     * Hides (or shows) one spending-chain address in Manage Addresses (iOS 245a5d2
     * `setSpendingAddressHidden`). Any address can be hidden - funded ones and the current one
     * too: the person can start the list over from nothing (Address Visibility > Hide All).
     * Nothing is lost: Profile's Manage Addresses total still counts hidden addresses, and
     * Address Visibility brings any back.
     * @return whether the flag was persisted (false only with no active account).
     */
    suspend fun setSpendingAddressHidden(index: Int, hidden: Boolean): Boolean {
        val account = walletManager.getActiveAccount() ?: return false
        walletManager.setSpendingAddressHidden(account.address, index, hidden)
        return true
    }

    /** Hides every revealed spending address at once (iOS 245a5d2 `hideAllSpendingAddresses`,
     *  Address Visibility > Hide All): Manage Addresses then starts empty under the chatting
     *  address. */
    fun hideAllSpendingAddresses() {
        val account = walletManager.getActiveAccount() ?: return
        val maxIndex = maxOf(0, WalletManager.legacyMaxSpendingIndex(account))
        // both chains: every revealed quantum slot too
        val quantumMax = WalletManager.maxQuantumIndex(account)
        val base = WalletManager.QUANTUM_INDEX_BASE
        walletManager.setSpendingAddressesHidden(account.address, (0..maxIndex).toSet() + (base until base + quantumMax + 1))
    }

    /** Sets or clears (blank/null) a user nickname for one spending-chain address — see [WalletManager.setSpendingAddressLabel]. */
    fun setSpendingAddressLabel(index: Int, label: String?) {
        val account = walletManager.getActiveAccount() ?: return
        walletManager.setSpendingAddressLabel(account.address, index, label)
    }

    /** Derives one more spending-chain address for the Manage Addresses screen, without changing which one "Pay in Kaspa" currently sources from. */
    fun generateNextSpendingAddress(): Int {
        val address = walletManager.getAddress()
        return walletManager.generateNextSpendingAddress(address)
    }

    /** Reveals one more quantum-safe (KIP-22) slot, un-hidden, and returns its public index -
     *  what Generate New Address makes from now on (iOS `generateNextQuantumAddress`). Checked on
     *  chain first, like every other fresh quantum slot (iOS 2343e84 `verifiedFreshQuantumIndex`,
     *  IOS-101 / AND-049): a slot already used elsewhere is skipped, never handed out as new.
     *  The reveal itself stays under the allocation lock with the in-flight guard
     *  ([WalletManager.generateNextQuantumAddress]). */
    suspend fun generateNextQuantumAddress(owner: String? = null): Int {
        // One account for the probe and the reveal, taken now (iOS 6891935, IOS-109 / AND-056):
        // the slot is probed on that account's chain and revealed there, whichever account is
        // active by then. Removed meanwhile: nothing is revealed.
        val account = walletManager.spendingSweepAccount(owner) ?: throw IllegalStateException("No active account")
        val index = walletManager.verifyFreshQuantumIndex(account) { spendingAddressUsedState(it) }
        if (!walletManager.sweepAccountStillSaved(account)) return index
        return walletManager.generateNextQuantumAddress(account.owner)
    }

    enum class KnsInscribeStep { CHECKING_AVAILABILITY, FETCHING_FEE, SUBMITTING_COMMIT, SUBMITTING_REVEAL, VERIFYING }

    data class ProfileUpdateResult(val fieldKey: String, val commitTxId: String, val revealTxId: String, val verified: Boolean)

    private data class KnsAddProfilePayload(val op: String, val id: String, val key: String, val value: String)

    /** Flat, self-funded commit/reveal amounts for profile field updates — not tiered like domain registration, and the reveal goes back to the wallet itself (matches iOS's `submitAddProfile` defaults). */
    private val PROFILE_COMMIT_SOMPI = 200_000_000L
    private val PROFILE_REVEAL_SOMPI = 100_000_000L

    /**
     * Sets one on-chain KNS profile field (bio, social links, etc.) on the given domain asset —
     * same commit/reveal engine as a domain transfer, but a flat, much smaller, self-funded cost.
     * Verified against iOS's `KNSProfileWriteService.submitAddProfile` (`KNSService.swift:1150-1277`).
     */
    suspend fun updateKnsProfileField(assetId: String, fieldKey: String, value: String, onStep: (KnsInscribeStep) -> Unit = {}): ProfileUpdateResult {
        val trimmedAssetId = assetId.trim()
        require(trimmedAssetId.isNotEmpty()) { "Missing KNS asset id" }
        val trimmedValue = value.trim()

        val payloadJson = Gson().toJson(KnsAddProfilePayload(op = "addProfile", id = trimmedAssetId, key = fieldKey, value = trimmedValue)).toByteArray()

        // KNS activity is funded and settled entirely on the identity/chatting address chain -
        // no spending-address split, so nothing ends up scattered across two addresses.
        val myAddress = walletManager.getAddress()
        val identityPrivateKey = walletManager.getPrivateKeyBytes()

        onStep(KnsInscribeStep.SUBMITTING_COMMIT)
        val commit = knsInscriptionEngine.buildAndSubmitCommit(
            payloadJson = payloadJson,
            commitAmountSompi = PROFILE_COMMIT_SOMPI,
            revealAmountSompi = PROFILE_REVEAL_SOMPI,
            revealTargetAddress = myAddress,
            fundingAddress = myAddress,
            fundingPrivateKey = identityPrivateKey,
            ownerPrivateKey = identityPrivateKey
        )
        onStep(KnsInscribeStep.SUBMITTING_REVEAL)
        val revealTxId = knsInscriptionEngine.buildAndSubmitReveal(commit, myAddress, myAddress, identityPrivateKey)

        onStep(KnsInscribeStep.VERIFYING)
        val verified = verifyProfileField(trimmedAssetId, fieldKey, trimmedValue)
        refreshBalance()
        // We just changed this profile, so the cached copy of it is wrong by our own doing -
        // drop it rather than let the owner see their old avatar or bio read back at them.
        knsService.invalidateCache(myAddress)

        return ProfileUpdateResult(fieldKey = fieldKey, commitTxId = commit.commitTxId, revealTxId = revealTxId, verified = verified)
    }

    data class TransferDomainResult(val domain: String, val toAddress: String, val commitTxId: String, val revealTxId: String, val verified: Boolean)

    private data class KnsTransferDomainPayload(val op: String, val p: String, val id: String, val to: String)

    /** Same flat commit cost as a profile edit, but reveal is 0 — ownership moves via the inscription payload's "to" field, not by paying the recipient (matches iOS's `KNSDomainTransferService.transferDomain`, `KNSService.swift:1517-1630`, which reuses the exact same `addProfile` builder functions). */
    private val TRANSFER_COMMIT_SOMPI = 200_000_000L
    private val TRANSFER_REVEAL_SOMPI = 0L

    /**
     * Transfers ownership of an owned domain to another address — irreversible once the reveal
     * confirms. [toAddress] must already be a resolved, validated Kaspa address (any ".kas"
     * resolution and format validation happens earlier, in the caller, so the UI can show the
     * user exactly what address they're sending to before they confirm). Re-validates here too
     * as a backend safety net: rejects sending to your own address, a different network's
     * address, or a domain you no longer actually own.
     *
     * [fromSpendingAddressIndex] non-null makes THAT spending-chain address the transfer source
     * (mirrors iOS `KNSDomainTransferService.transferDomain(fromSpendingAddressIndex:)`): it is
     * the domain's owner — it funds the commit, its pubkey goes into the redeem script, its key
     * signs both transactions, and it receives all change. Null keeps the historical
     * identity/chatting-address behavior.
     */
    suspend fun transferDomain(fullDomain: String, assetId: String, toAddress: String, priorityFeeSompi: Long = KnsInscriptionEngine.REVEAL_PRIORITY_FEE_SOMPI, fromSpendingAddressIndex: Int? = null, onStep: (KnsInscribeStep) -> Unit = {}): TransferDomainResult {
        val trimmedAssetId = assetId.trim()
        require(trimmedAssetId.isNotEmpty()) { "Missing KNS asset id" }
        // One key does everything — no split between funder and owner.
        // Said plainly, before anything is built or spent (iOS 2a8ca5a): KNS cannot move a domain
        // out of a quantum-safe address, nor ever out of a script address it is sent to.
        knsTransferRefusalRes(fromSpendingAddressIndex, toAddress)?.let {
            throw IllegalStateException(appContext.getString(it))
        }
        val sourceAddress: String
        val sourcePrivateKey: ByteArray
        // The account the source is derived from: a fresh change slot is probed and picked there
        // too, even if another account is active by then (iOS fb2323e, IOS-109 / AND-056
        // follow-up).
        val sourceOwner = walletManager.getActiveAccount()?.address
        if (fromSpendingAddressIndex != null) {
            sourceAddress = walletManager.deriveSpendingAddress(fromSpendingAddressIndex)
            sourcePrivateKey = walletManager.getSpendingPrivateKeyBytes(fromSpendingAddressIndex)
        } else {
            sourceAddress = walletManager.getAddress()
            sourcePrivateKey = walletManager.getPrivateKeyBytes()
        }

        KaspaAddress.requireActiveNetwork(toAddress)
        require(KaspaAddress.isValid(toAddress)) { "Invalid recipient address" }
        require(toAddress != sourceAddress) { "Recipient address must be different from your wallet" }
        require(toAddress.substringBefore(":") == sourceAddress.substringBefore(":")) { "Recipient address is on the wrong network" }

        val currentOwner = knsService.resolve(fullDomain)
        if (currentOwner != sourceAddress) {
            throw IllegalStateException(
                if (fromSpendingAddressIndex != null) "Domain is not owned by this spending address"
                else "Domain is not owned by current wallet"
            )
        }

        val payloadJson = Gson().toJson(KnsTransferDomainPayload(op = "transfer", p = "domain", id = trimmedAssetId, to = toAddress)).toByteArray()

        // A transfer funded by the PRIMARY spending address is a send out of it, so both
        // transactions' change lands on a fresh quantum-safe slot (KIP-22, iOS
        // `nextFreshQuantumIndex`: never revealed, funded or offered, key hidden until it spends)
        // and the primary rotates there once the commit is accepted - the
        // same rule as chat payments and withdrawals. The domain itself leaves anyway; the old
        // address keeps any other domains it holds and stays listed in Manage Addresses. If the
        // fresh address cannot be derived, change stays on the source and nothing rotates - a
        // pointer must never move to an address the funds did not reach. The slot is picked, not
        // revealed: held as in flight until the commit is decided, given back if it fails
        // ([WalletManager.pickFreshQuantumChange]).
        val activeSpendingIndex = walletManager.getActiveAccount()?.spendingAddressIndex
        val fresh = if (fromSpendingAddressIndex != null && fromSpendingAddressIndex == activeSpendingIndex) {
            // checked on chain first: a slot that already has history is skipped (iOS da2c8e2
            // `verifiedFreshQuantumIndex`, IOS-101 / AND-049); slot and address from the source's
            // account (iOS fb2323e `verifiedFreshQuantumSlot`)
            sourceOwner?.let { owner -> walletManager.verifiedFreshQuantumChange(owner = owner) { spendingAddressUsedState(it) } }
        } else null
        val changeAddress = fresh?.address ?: sourceAddress

        onStep(KnsInscribeStep.SUBMITTING_COMMIT)
        val commit = try {
            knsInscriptionEngine.buildAndSubmitCommit(
                payloadJson = payloadJson,
                commitAmountSompi = TRANSFER_COMMIT_SOMPI,
                revealAmountSompi = TRANSFER_REVEAL_SOMPI,
                revealTargetAddress = sourceAddress,
                fundingAddress = sourceAddress,
                fundingPrivateKey = sourcePrivateKey,
                ownerPrivateKey = sourcePrivateKey,
                changeAddress = changeAddress,
                ownerSpendingIndex = fromSpendingAddressIndex,
                revealPriorityFeeSompi = priorityFeeSompi
            )
        } catch (e: Throwable) {
            // A commit that failed gives the slot back; one interrupted with its outcome unknown
            // reveals it - revealing is always safe, handing a slot that may hold change out
            // again is not.
            fresh?.let {
                walletManager.finishQuantumChangePick(
                    it,
                    reveal = e is kotlinx.coroutines.CancellationException || e is SendOutcomeUnknownException
                )
                // It may yet land, with its change on the fresh address: that has to be on the
                // list (iOS 2da5f4b).
                if (e is SendOutcomeUnknownException) walletManager.setSpendingAddressIndex(it.walletAddress, it.index)
            }
            throw e
        }
        if (fresh != null) {
            // The commit is accepted and its change is already on the fresh address, so it is
            // revealed and the primary follows it now, whatever becomes of the reveal
            // ([KnsRevealRecovery] retries that), and the pointer no longer names an address that
            // just spent. Both on the account this transfer started on
            // (the pick's own, [sourceOwner]) even if another is active by now, and nothing once
            // it was removed (iOS ca09dd0, IOS-109 / AND-056).
            walletManager.finishQuantumChangePick(fresh, reveal = true)
            walletManager.setSpendingAddressIndex(fresh.walletAddress, fresh.index)
        }
        onStep(KnsInscribeStep.SUBMITTING_REVEAL)
        val revealTxId = knsInscriptionEngine.buildAndSubmitReveal(commit, sourceAddress, changeAddress, sourcePrivateKey, priorityFeeSompi)
        // No chat transfer hint: the wallet has no chats (iOS wallet 301ebfb).

        onStep(KnsInscribeStep.VERIFYING)
        val verified = verifyDomainOwnership(fullDomain, toAddress)
        refreshBalance()

        return TransferDomainResult(domain = fullDomain, toAddress = toAddress, commitTxId = commit.commitTxId, revealTxId = revealTxId, verified = verified)
    }

    /**
     * Uploads a profile avatar/banner image (already downscaled + PNG-encoded by the caller) and
     * writes the resulting URL on-chain — reuses [updateKnsProfileField] for the on-chain half, so
     * this costs exactly one more real commit/reveal transaction (~2/1 KAS) on top of the upload
     * itself, same as any other profile field.
     */
    suspend fun uploadKnsProfileImage(assetId: String, uploadType: String, imageBytes: ByteArray, onStep: (KnsInscribeStep) -> Unit = {}): ProfileUpdateResult {
        val url = knsService.uploadProfileImageWithFallback(assetId, uploadType, imageBytes, walletManager.getPrivateKeyBytes())
        val fieldKey = if (uploadType == "avatar") "avatarUrl" else "bannerUrl"
        return updateKnsProfileField(assetId, fieldKey, url, onStep)
    }

    /** Marks an owned domain as primary — off-chain, free, no transaction. */
    suspend fun setPrimaryDomain(assetId: String) {
        knsService.setPrimaryDomain(
            assetId.trim(),
            walletManager.getPrivateKeyBytes(),
            ownerAddress = try { walletManager.getAddress() } catch (e: Exception) { null },
        )
    }

    /** Polls the profile endpoint until the new field value is indexed, up to 90s — a UX confirmation step only. */
    private suspend fun verifyProfileField(assetId: String, fieldKey: String, expectedValue: String): Boolean {
        val deadlineMs = System.currentTimeMillis() + 90_000L
        while (System.currentTimeMillis() < deadlineMs) {
            if (fieldValue(knsService.getProfileForEditing(assetId), fieldKey) == expectedValue) return true
            delay(2_000L)
        }
        return false
    }

    /** Polls forward resolution until the new domain's owner matches, up to 90s — a UX confirmation step, not something the broadcast itself depends on. */
    private suspend fun verifyDomainOwnership(fullDomain: String, expectedOwnerAddress: String): Boolean {
        val deadlineMs = System.currentTimeMillis() + 90_000L
        while (System.currentTimeMillis() < deadlineMs) {
            if (knsService.resolve(fullDomain) == expectedOwnerAddress) return true
            delay(2_000L)
        }
        return false
    }

    private fun kasToSompi(kas: Double): Long {
        require(kas >= 0) { "Negative KAS amount is invalid" }
        return Math.round(kas * 100_000_000.0)
    }

    companion object {
        /** The shield probe's page size and how many pages it reads at most (iOS da2c8e2: 200 x
         *  50 = 10,000 transactions). */
        internal const val QUANTUM_SPENT_PAGE_SIZE = 200
        internal const val QUANTUM_SPENT_MAX_PAGES = 50

        /**
         * The shield probe's walk (iOS `quantumAddressSpentState`, da2c8e2 / IOS-105 / AND-051):
         * [page] (offset, limit) reads one page of [address]'s history, null when it can't. True
         * at the first page where an input spends from [address]; false once a short page ends
         * the history without one; null when a page can't be read, or the history is deeper than
         * [QUANTUM_SPENT_MAX_PAGES] pages - never "not spent" on a guess.
         */
        internal suspend fun quantumSpentFromPages(
            address: String,
            page: suspend (offset: Int, limit: Int) -> List<TransactionResponse>?,
        ): Boolean? {
            var offset = 0
            while (offset < QUANTUM_SPENT_MAX_PAGES * QUANTUM_SPENT_PAGE_SIZE) {
                val transactions = page(offset, QUANTUM_SPENT_PAGE_SIZE) ?: return null
                val spent = transactions.any { tx ->
                    tx.inputs.any { it.previousOutpointAddress?.equals(address, ignoreCase = true) == true }
                }
                if (spent) return true
                if (transactions.size < QUANTUM_SPENT_PAGE_SIZE) return false
                offset += QUANTUM_SPENT_PAGE_SIZE
            }
            // deeper than the walk goes: say so rather than claim the key is hidden
            return null
        }

        /**
         * Why a KNS domain transfer is refused up front, or null (iOS 2a8ca5a). KNS names a
         * domain's owner by the public key in the reveal script, and a quantum-safe (KIP-22)
         * address keeps its key hidden behind a hash - so KNS cannot move a domain out of one,
         * and a domain sent to any script (P2SH) address, quantum-safe ones included, would be
         * stuck there for good.
         */
        @androidx.annotation.StringRes
        internal fun knsTransferRefusalRes(fromSpendingAddressIndex: Int?, toAddress: String): Int? = when {
            fromSpendingAddressIndex != null && WalletManager.isQuantumIndex(fromSpendingAddressIndex) ->
                com.kachat.app.R.string.kns_transfer_quantum_source
            isScriptAddress(toAddress) -> com.kachat.app.R.string.kns_transfer_to_script_address
            else -> null
        }

        /** A script (P2SH) address - its version byte is 0x08 (was ChatRepository's, iOS wallet 301ebfb). */
        internal fun isScriptAddress(address: String?): Boolean {
            if (address.isNullOrBlank()) return false
            return runCatching { com.kachat.app.util.KaspaAddress.decode(address.trim().lowercase()).first == 0x08.toByte() }.getOrDefault(false)
        }


        /** Legacy per-conversation random alias — 6 random bytes as 12 lowercase hex chars, the
         *  format Kasia web and iOS KaChat validate. No longer used for new chats (XP-003: a
         *  handshake carries the deterministic alias); kept for its format test. */
        internal fun generateAlias(): String {
            val bytes = ByteArray(6)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /** Maps a KNS profile field key (e.g. "bio", "avatarUrl") to its current value — shared by verification polling and the Edit KNS Profile screen's change-diffing. */
        internal fun fieldValue(profile: KnsProfileFields?, fieldKey: String): String? = when (fieldKey) {
            "bio" -> profile?.bio
            "avatarUrl" -> profile?.avatarUrl
            "x" -> profile?.x
            "website" -> profile?.website
            "telegram" -> profile?.telegram
            "discord" -> profile?.discord
            "contactEmail" -> profile?.contactEmail
            "github" -> profile?.github
            "redirectUrl" -> profile?.redirectUrl
            "bannerUrl" -> profile?.bannerUrl
            else -> null
        }
    }
}
