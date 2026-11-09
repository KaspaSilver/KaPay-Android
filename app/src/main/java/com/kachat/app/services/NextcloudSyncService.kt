package com.kachat.app.services

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wallet's Nextcloud backup: Contacts and the Portfolio, nothing else (iOS `NextcloudService`,
 * 953b255; [NextcloudService] holds the account and the WebDAV calls). KaChat's NEXTCLOUD_SYNC.md,
 * "Contacts only", is the contract for the contacts.
 *
 *   * **Contacts**: `kachat-contacts.json`, encrypted ([BackupCrypto]), the same file with the same
 *     key as KaChat. [runContactsBackup] is one read, merge, write, written only when the file is
 *     missing something this device has (newest edit or deletion wins). A file this account can't
 *     read throws before any write, except its own damaged one, which is replaced. With Automatic
 *     Sync on: [SYNC_DEBOUNCE_MS] after a contact changes ([noteContactActivity]), at most every
 *     [AUTO_SYNC_MIN_INTERVAL_MS] ([AUTO_SYNC_MIN_INTERVAL_METERED_MS] on metered networks), on
 *     background, and a foreground ETag poll every [CHANGE_WATCH_INTERVAL_MS]
 *     ([CHANGE_WATCH_INTERVAL_METERED_MS]) brings in other devices' changes.
 *   * The earlier plain contacts files are merged in once per account and never written again.
 *   * **Portfolio**: one file per portfolio in the backup folder, named after it, in the contacts'
 *     encrypted v1 envelope (KaPay iPhone, 2026-11-06); a plain one from an earlier build is read
 *     until this account's first full backup.
 *     [runPortfolioBackup] merges this account's files in first, uploads what's out of date and
 *     removes the file of a portfolio renamed or deleted; other files are left alone. With Automatic
 *     Sync on they upload [BACKUP_DEBOUNCE_MS] after a change ([notePortfolioChanged]) and on
 *     background, and merge in what's on the server when the app opens, at most once a minute.
 *     The one Portfolio file the app used to write is merged in once and never written again.
 *   * A **fresh connection** ([connectAsync]) merges both in once, by itself. There's no Restore.
 *   * A 401 stops automatic work ([NextcloudService.needsReconnect]) and Settings says so.
 *
 * Every owed flag is persisted, so a change made just before the process died still goes up on the
 * next launch, foreground or [NextcloudAutoSyncWorker] run. Every upload snapshots the active wallet
 * and re-checks it before each write, so a wallet switch mid-way never writes one account's data
 * into another's files.
 */
@Singleton
class NextcloudSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val walletManager: WalletManager,
    private val nextcloudService: NextcloudService,
    // Foreground flag for the change watcher (KaChatApplication's lifecycle observer drives it).
    private val notificationHelper: NotificationHelper,
    // Metered networks: a slower watcher and a longer floor between automatic uploads.
    private val meteredNetwork: MeteredNetwork,
    // Lazy: each of these reaches this class back (lazily) to report its changes.
    private val addressBookManager: dagger.Lazy<AddressBookManager>,
    private val portfolioLedgerStore: dagger.Lazy<PortfolioLedgerStore>,
    private val portfolioRepository: dagger.Lazy<com.kachat.app.repository.PortfolioRepository>,
) {
    companion object {
        private const val TAG = "NextcloudBackup"

        /** Quiet time after a Portfolio change before the upload, so a burst of edits is one (iOS
         *  `backupDebounce`, 5 s). */
        const val BACKUP_DEBOUNCE_MS = 5_000L

        /** Going in and out of the app quickly checks the server once (iOS `serverCheckInterval`). */
        const val SERVER_CHECK_INTERVAL_MS = 60_000L

        /** An on-background contacts sync with no change owed runs at most this often. */
        const val AUTO_BACKUP_MIN_INTERVAL_MS = 3_600_000L
        /** If the last automatic sync is older than this (the app was force-closed for days), sync
         *  on coming to the foreground instead of waiting for the next background. */
        const val AUTO_BACKUP_CATCH_UP_INTERVAL_MS = 86_400_000L
        /** Quiet time after the last contact change before the automatic merge upload runs. */
        const val SYNC_DEBOUNCE_MS = 15_000L
        /** Floor between automatic contact uploads; a debounce firing earlier re-arms itself. Back
         *  Up Contacts Now isn't held to it. */
        const val AUTO_SYNC_MIN_INTERVAL_MS = 90_000L
        const val AUTO_SYNC_MIN_INTERVAL_METERED_MS = 300_000L
        /** How often the contacts file's ETag is polled while the app is in the foreground. */
        const val CHANGE_WATCH_INTERVAL_MS = 30_000L
        const val CHANGE_WATCH_INTERVAL_METERED_MS = 60_000L
        /** Failed polls back off (x3 per consecutive failure, interval capped here) and snap back on
         *  the next success. */
        private const val CHANGE_WATCH_BACKOFF_MAX_MS = 60_000L
        private const val CHANGE_WATCH_BACKOFF_FACTOR_MAX = 12L

        /** Periodic WorkManager fallback cadence for an owed upload the in-process paths missed. */
        const val PERIODIC_INTERVAL_HOURS = 6L

        const val WORK_NAME = "kachat_nextcloud_auto_sync"

        // KaChat's chat-sync DataStore keys (per wallet), never written any more: removed on
        // account deletion so nothing of an older build's state is left behind.
        private val LEGACY_KEY_BASES = listOf(
            "nextcloud_auto_sync_last_ms", "nextcloud_auto_sync_pending", "nextcloud_auto_restore_done",
            "nextcloud_auto_sync_etag", "nextcloud_auto_sync_merged_etag",
        )

        /**
         * Opens the file already at the contacts path. Returns the decrypted content, or null when it
         * is THIS wallet's own file that fails to decrypt: a sync replaces that one in place, since
         * Nextcloud's version history keeps the old content. Anything else that can't be opened
         * THROWS, so a sync writes nothing: a plain file or a newer envelope version, another
         * wallet's file, and an envelope with no walletHint that won't decrypt (iOS 953b255
         * `BackupEnvelope.decryptIfEnveloped` + `isOwnUnreadableEnvelope`).
         */
        internal fun openContactsFile(remote: String, walletAddress: String, decrypt: (String) -> String): String? {
            if (!BackupCrypto.isEnvelope(remote)) throw NextcloudException("Unexpected response from the Nextcloud server.")
            val hint = BackupCrypto.envelopeWalletHint(remote)
            if (hint != null && hint != BackupCrypto.walletHint(walletAddress)) {
                throw NextcloudException(BackupCrypto.DECRYPT_FAILED_MESSAGE)
            }
            return try {
                decrypt(remote)
            } catch (e: Exception) {
                if (hint == null) throw e
                null
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.kachat.app.util.StartupSafety.logOnlyHandler("NextcloudSyncService"))

    /** Contacts backups run one at a time on this device: two uploads of the same file at once get
     *  HTTP 423. A caller arriving while one runs waits for it, then merges on top of what it wrote. */
    private val contactsMutex = Mutex()

    /** The same for the Portfolio files. */
    private val portfolioMutex = Mutex()

    /** Counts Portfolio changes, so a backup clears the pending flag only if none came in after it
     *  read what to upload (iOS `portfolioChanges`). */
    private val portfolioChanges = java.util.concurrent.atomic.AtomicInteger(0)

    /** Serializes automatic contact uploads and the watcher's merges; a skipped run leaves the dirty
     *  flag set. */
    private val syncInFlight = AtomicBoolean(false)

    /** The pending Portfolio upload after a change - cancelled on an account switch or disconnect so
     *  one account's data never uploads while another is active (iOS `backupTask`). */
    @Volatile
    private var debounceJob: Job? = null

    /** The in-flight on-background or catch-up contacts sync (iOS `autoBackupTask`). */
    @Volatile
    private var autoBackupJob: Job? = null

    /** The quiet-time timer armed by [noteContactActivity], so a run of edits becomes one upload. */
    @Volatile
    private var syncDebounceJob: Job? = null

    /** When the app last looked for another device's Portfolio changes (0 = not yet for this wallet). */
    @Volatile
    private var lastServerCheckMs = 0L

    /** When the active wallet's backup last uploaded (epoch ms), null = never. */
    val lastBackupMs: StateFlow<Long?> get() = nextcloudService.lastBackupMs

    init {
        // The Portfolio files are opened and sealed with the contacts' key (KaPay iPhone, 2026-11-06).
        nextcloudService.portfolioKeyFor = ::backupEncryptionKey
        // The periodic fallback job is enqueued exactly while it can do anything: an account
        // connected for the active wallet with Automatic Sync on.
        scope.launch {
            combine(nextcloudService.account, nextcloudService.autoBackupEnabled) { account, auto ->
                account != null && auto
            }.distinctUntilChanged().collect { active ->
                if (active) ensurePeriodicWork() else cancelScheduledWork()
            }
        }
        // A wallet switch, logout or disconnect drops the pending uploads; a wallet that owes one
        // (a change made just before the app was closed) gets it scheduled (iOS setCurrentWallet).
        scope.launch {
            var previous: Pair<String, String>? = null
            combine(nextcloudService.activeWallet, nextcloudService.account, nextcloudService.autoBackupEnabled) { address, account, auto ->
                // The wallet and server a backup would go to, or null while none would run.
                if (address != null && account != null && auto) address to account.server else null
            }.distinctUntilChanged().collect { active ->
                // Another wallet, another server, disconnected or switched off: what was pending
                // belongs to what was active before.
                if (previous != null && active != previous) cancelPendingWork()
                // Newly active: bring in what's on the server, or upload a change still owed. The
                // toggle's own immediate upload (setAutoBackupEnabled) may already be on its way;
                // it is not replaced. A moment of (old wallet, new server) during a switch is skipped.
                if (active != previous) lastServerCheckMs = 0L
                if (active != null && active != previous && active.first == nextcloudService.activeWalletAddress) {
                    if (debounceJob?.isActive != true) syncPortfoliosOnOpen()
                    // Not on the encrypted file yet: the first sync brings the plain file in and
                    // writes it.
                    if (!nextcloudService.plainContactsMerged) noteContactActivity()
                }
                previous = active
            }
        }
        // The change watcher runs exactly while the app is in the foreground with an account
        // connected, Automatic Sync on and no refused login. Any of those changing cancels the loop;
        // a wallet switch restarts it for the new wallet (iOS start/stopChangeWatcher).
        scope.launch {
            combine(
                notificationHelper.appForegroundFlow,
                nextcloudService.account,
                nextcloudService.autoBackupEnabled,
                nextcloudService.activeWallet,
                nextcloudService.needsReconnect,
            ) { foreground, account, auto, wallet, needsReconnect ->
                if (foreground && account != null && auto && !needsReconnect) wallet else null
            }.distinctUntilChanged().collectLatest { wallet ->
                if (wallet != null) runChangeWatcher(wallet)
            }
        }
    }

    /** Logged out, this device's wallet stays active on Android; iOS clears it, which stops every
     *  automatic path. */
    private val isSignedOut: Boolean get() = walletManager.isLoggedOut

    private fun cancelPendingWork() {
        debounceJob?.cancel()
        debounceJob = null
        autoBackupJob?.cancel()
        autoBackupJob = null
        syncDebounceJob?.cancel()
        syncDebounceJob = null
    }

    /** A 401 won't fix itself, and repeated failed logins can trip the server's brute-force
     *  protection: automatic work stops and Settings asks for a new connection (iOS 953b255). */
    private fun noteRejectedLogin(error: Exception) {
        if (error !is NextcloudRejectedLoginException) return
        nextcloudService.markNeedsReconnect()
        syncDebounceJob?.cancel()
        syncDebounceJob = null
        Log.w(TAG, "The server refused this login; automatic sync stops until it reconnects")
    }

    // -------------------------------------------------------------------------
    // Automatic Portfolio backup
    // -------------------------------------------------------------------------

    /**
     * The Portfolio changed ([PortfolioLedgerStore] calls this on every save): with Automatic Sync
     * on, it uploads a few seconds after the changes settle (iOS `notePortfolioChanged`).
     */
    fun notePortfolioChanged() {
        portfolioChanges.incrementAndGet()
        if (!nextcloudService.autoBackupEnabled.value || !nextcloudService.isConnected || nextcloudService.needsReconnect.value) return
        nextcloudService.setBackupPending(true)
        scheduleBackup(BACKUP_DEBOUNCE_MS)
    }

    /**
     * Settings' Automatic Sync toggle (iOS `setAutoBackupEnabled`): turning it on uploads right
     * away, turning it off drops what's pending.
     */
    fun setAutoBackupEnabled(enabled: Boolean) {
        nextcloudService.setAutoBackupEnabled(enabled)
        if (enabled) {
            nextcloudService.setBackupPending(true)
            scheduleBackup(0L)
            noteContactActivity()
        } else {
            debounceJob?.cancel()
            debounceJob = null
            syncDebounceJob?.cancel()
            syncDebounceJob = null
        }
    }

    private fun scheduleBackup(delayMs: Long) {
        if (!nextcloudService.autoBackupEnabled.value || !nextcloudService.isConnected || nextcloudService.needsReconnect.value) return
        val walletAtStart = nextcloudService.activeWalletAddress ?: return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (nextcloudService.activeWalletAddress != walletAtStart || isSignedOut) return@launch
            try {
                runPortfolioBackup()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Kept pending: the next change, background or launch retries.
                noteRejectedLogin(e)
                Log.w(TAG, "Automatic Portfolio backup failed: ${e.message}")
            }
        }
    }

    /**
     * The app opened or came back (KaChatApplication's onStart): syncs the contacts if the last
     * automatic sync is a day old or one is owed, and brings in another device's Portfolio changes,
     * at most once a minute (iOS didBecomeActive). The change watcher starts by itself.
     */
    fun syncWithServerOnOpen() {
        scheduleAutoBackup(AUTO_BACKUP_CATCH_UP_INTERVAL_MS)
        syncPortfoliosOnOpen()
    }

    /** A change still owed is uploaded instead of merged, since that merges the server's copy in
     *  first (iOS `syncWithServerOnOpen`). */
    private fun syncPortfoliosOnOpen() {
        if (!nextcloudService.autoBackupEnabled.value || !nextcloudService.isConnected || nextcloudService.needsReconnect.value || isSignedOut) return
        val walletAtStart = nextcloudService.activeWalletAddress ?: return
        val now = System.currentTimeMillis()
        if (lastServerCheckMs != 0L && now - lastServerCheckMs < SERVER_CHECK_INTERVAL_MS) return
        lastServerCheckMs = now
        if (nextcloudService.isBackupPending) {
            scheduleBackup(BACKUP_DEBOUNCE_MS)
            return
        }
        debounceJob?.cancel()
        debounceJob = scope.launch {
            // after the other services have switched to this account
            delay(1_000L)
            if (nextcloudService.activeWalletAddress != walletAtStart) return@launch
            try {
                portfolioMutex.withLock { mergePortfoliosFromServer(portfolioTarget(walletAtStart)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                noteRejectedLogin(e)
                Log.w(TAG, "Checking the server failed: ${e.message}")
            }
        }
    }

    /**
     * Going to the background (KaChatApplication's onStop): the flush takes over from any quiet-time
     * timer. The contacts sync at most every [minIntervalMs] unless one is owed, and a Portfolio
     * change still owed uploads (iOS didEnterBackground). Never throws.
     */
    suspend fun autoBackupIfDue(minIntervalMs: Long = AUTO_BACKUP_MIN_INTERVAL_MS) {
        syncDebounceJob?.cancel()
        syncDebounceJob = null
        val contacts = scheduleAutoBackup(minIntervalMs)
        val portfolio = scope.launch { backUpPendingPortfolio() }
        contacts.join()
        portfolio.join()
    }

    /** [NextcloudAutoSyncWorker]'s body: an owed upload the in-process paths missed (the process
     *  died first), or a wallet that has never synced. No network at all otherwise. Never throws. */
    suspend fun periodicUploadIfNeeded() {
        if (!nextcloudService.autoBackupEnabled.value || !nextcloudService.isConnected || nextcloudService.needsReconnect.value || isSignedOut) return
        val wallet = nextcloudService.activeWalletAddress ?: return
        if (nextcloudService.isContactsDirty(wallet) || nextcloudService.lastAutoSyncMs.value == null) {
            performAutomaticSync(wallet)
        }
        backUpPendingPortfolio()
    }

    /** iOS `backUpPendingInBackground`. */
    private suspend fun backUpPendingPortfolio() {
        if (!nextcloudService.autoBackupEnabled.value || !nextcloudService.isConnected || nextcloudService.needsReconnect.value || isSignedOut) return
        if (!nextcloudService.isBackupPending) return
        try {
            runPortfolioBackup()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Automatic Portfolio backup failed (a later trigger retries): ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Automatic contacts sync
    // -------------------------------------------------------------------------

    /**
     * A contact changed ([AddressBookManager] calls this on every user edit). Marks the active
     * wallet's contacts dirty (persisted, so a kill can't lose the fact that an upload is owed) and
     * restarts the quiet-time timer, so a run of edits becomes one merge upload.
     */
    fun noteContactActivity() {
        if (!nextcloudService.autoBackupEnabled.value || !nextcloudService.isConnected || nextcloudService.needsReconnect.value) return
        val wallet = nextcloudService.activeWalletAddress ?: return
        if (!nextcloudService.isContactsDirty(wallet)) nextcloudService.setContactsDirty(true, wallet)
        armSyncDebounce(wallet, SYNC_DEBOUNCE_MS)
    }

    /** Runs [autoBackupJob], so a wallet switch can cancel it mid-flight. */
    private fun scheduleAutoBackup(minIntervalMs: Long): Job {
        autoBackupJob?.cancel()
        return scope.launch { contactsSyncIfDue(minIntervalMs) }.also { autoBackupJob = it }
    }

    /** Syncs the contacts when at least [minIntervalMs] past the last time (hourly on background,
     *  daily on the foreground catch-up), or when the dirty flag says an upload is still owed. */
    private suspend fun contactsSyncIfDue(minIntervalMs: Long) {
        if (!nextcloudService.autoBackupEnabled.value || !nextcloudService.isConnected || nextcloudService.needsReconnect.value || isSignedOut) return
        val wallet = nextcloudService.activeWalletAddress ?: return
        val last = nextcloudService.lastAutoSyncMs.value ?: 0L
        if (System.currentTimeMillis() - last < minIntervalMs && !nextcloudService.isContactsDirty(wallet)) return
        performAutomaticSync(wallet)
    }

    private fun armSyncDebounce(wallet: String, delayMs: Long) {
        syncDebounceJob?.cancel()
        syncDebounceJob = scope.launch {
            delay(delayMs)
            if (nextcloudService.isContactsDirty(wallet)) performAutomaticSync(wallet)
        }
    }

    /**
     * The one automatic contacts upload path. The dirty flag is cleared BEFORE building - a contact
     * changed during the upload re-marks it, so nothing is lost - and re-marked on any failure so a
     * later trigger retries. One due inside the floor re-arms for the earliest allowed time.
     */
    private suspend fun performAutomaticSync(wallet: String) {
        if (nextcloudService.activeWalletAddress != wallet || isSignedOut) return
        if (!nextcloudService.autoBackupEnabled.value || !nextcloudService.isConnected) return
        nextcloudService.lastAutoSyncMs.value?.let { last ->
            val floor = if (meteredNetwork.isMetered) AUTO_SYNC_MIN_INTERVAL_METERED_MS else AUTO_SYNC_MIN_INTERVAL_MS
            val elapsed = System.currentTimeMillis() - last
            if (elapsed < floor) {
                armSyncDebounce(wallet, floor - elapsed)
                return
            }
        }
        if (!syncInFlight.compareAndSet(false, true)) {
            armSyncDebounce(wallet, 30_000L)
            return
        }
        try {
            nextcloudService.setContactsDirty(false, wallet)
            runContactsBackup()
            if (nextcloudService.activeWalletAddress != wallet) return
            nextcloudService.recordAutoSync(wallet)
            Log.i(TAG, "Automatic sync merged the contacts")
        } catch (e: CancellationException) {
            nextcloudService.setContactsDirty(true, wallet)
            throw e
        } catch (e: Exception) {
            nextcloudService.setContactsDirty(true, wallet)
            noteRejectedLogin(e)
            Log.w(TAG, "Automatic sync upload failed (a later trigger retries): ${e.message}")
        } finally {
            syncInFlight.set(false)
        }
    }

    // -------------------------------------------------------------------------
    // Change watcher (foreground pull of other devices' contact changes)
    // -------------------------------------------------------------------------

    /** The poll loop, cancelled by the init observer. A failed poll backs off and snaps back on the
     *  next success. */
    private suspend fun runChangeWatcher(walletAtStart: String) {
        var backoffFactor = 1L
        while (true) {
            val base = if (meteredNetwork.isMetered) CHANGE_WATCH_INTERVAL_METERED_MS else CHANGE_WATCH_INTERVAL_MS
            delay(minOf(base * backoffFactor, CHANGE_WATCH_BACKOFF_MAX_MS))
            if (isSignedOut) continue
            try {
                checkForRemoteChangeAndImport(walletAtStart)
                backoffFactor = 1L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                noteRejectedLogin(e)
                backoffFactor = minOf(backoffFactor * 3, CHANGE_WATCH_BACKOFF_FACTOR_MAX)
            }
        }
    }

    /**
     * One poll: fetch the ETag, and merge the file if it changed. Skips silently (leaving the
     * last-known ETag alone, so the next poll retries) while a sync is running. Throws on network
     * trouble so the watcher backs off. A new device's first poll sees an ETag it doesn't know, so
     * this is also how it gets the contacts.
     */
    private suspend fun checkForRemoteChangeAndImport(walletAtStart: String) {
        // ETag BEFORE the download: if the file is replaced between the two requests, the stored
        // ETag is the older one and the next poll simply merges again, which changes nothing.
        val etag = nextcloudService.fetchBackupEtag() ?: return // no backup yet
        if (etag == nextcloudService.lastKnownEtag(walletAtStart)) return
        if (nextcloudService.activeWalletAddress != walletAtStart) return
        if (!nextcloudService.autoBackupEnabled.value || !nextcloudService.isConnected) return
        if (!syncInFlight.compareAndSet(false, true)) return
        try {
            val raw = nextcloudService.downloadBackup() ?: throw NextcloudHttpException(404)
            if (nextcloudService.activeWalletAddress != walletAtStart) return
            val book = addressBookManager.get()
            val serverIsBehind = try {
                val key = backupEncryptionKey(walletAtStart) ?: throw NextcloudException(BackupCrypto.DECRYPT_FAILED_MESSAGE)
                val plaintext = openContactsFile(raw, walletAtStart) { BackupCrypto.decrypt(it, key) }
                    ?: throw NextcloudException(BackupCrypto.DECRYPT_FAILED_MESSAGE)
                if (nextcloudService.activeWalletAddress != walletAtStart || !book.holdsWallet(walletAtStart)) return
                book.mergeBackup(plaintext.toByteArray(Charsets.UTF_8)).serverIsBehind
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A file this wallet can't read: record the ETag so it isn't downloaded every poll;
                // a future replacement changes the ETag and gets a fresh look.
                nextcloudService.setLastKnownEtag(etag, walletAtStart)
                noteContactsSyncFailure(e, walletAtStart)
                Log.w(TAG, "Change watcher skipped an unreadable contacts file: ${e.message}")
                return
            }
            nextcloudService.setLastKnownEtag(etag, walletAtStart)
            nextcloudService.setContactsSyncError(null, walletAtStart)
            if (serverIsBehind) noteContactActivity()
            Log.i(TAG, "Change watcher merged another device's contacts")
        } finally {
            syncInFlight.set(false)
        }
    }

    // -------------------------------------------------------------------------
    // Contacts backup
    // -------------------------------------------------------------------------

    /** Back Up Contacts Now and the automatic sync both come here, one at a time. */
    suspend fun runContactsBackup() {
        contactsMutex.withLock {
            val wallet = nextcloudService.activeWalletAddress
            try {
                performContactsBackup()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (wallet != null) noteContactsSyncFailure(e, wallet)
                throw e
            }
            if (wallet != null && nextcloudService.activeWalletAddress == wallet) nextcloudService.setContactsSyncError(null, wallet)
        }
    }

    /**
     * A contacts sync that failed in a way that won't fix itself - another account's file in the
     * Backup Folder, above all - is kept for Settings to show until a sync succeeds or the folder
     * changes, rather than only logged. A dropped connection isn't (the next sync retries), nor a
     * refused login ([NextcloudService.needsReconnect] says that).
     */
    private fun noteContactsSyncFailure(error: Exception, wallet: String) {
        if (error is NextcloudRejectedLoginException || error is java.io.IOException) return
        if (nextcloudService.activeWalletAddress != wallet) return
        val message = if (error.message == BackupCrypto.DECRYPT_FAILED_MESSAGE) {
            "Contacts can't sync: the contacts file in the Backup Folder can't be opened by this account " +
                "and may belong to another one. Choose a different Backup Folder for this account."
        } else {
            error.message?.takeIf { it.isNotBlank() } ?: "Contacts couldn't sync."
        }
        nextcloudService.setContactsSyncError(message, wallet)
    }

    /** A mid-flight account switch aborts: one account's contacts never land in another's file. */
    private fun ensureStillActive(wallet: String) {
        if (nextcloudService.activeWalletAddress != wallet) throw CancellationException("The active account changed.")
    }

    /**
     * One read, merge, write of the contacts file. Newest edit or deletion wins per address, so no
     * device can undo another's newer change. Written only when the file is missing something this
     * device has. Any file that can't be read as this account's contacts throws before the PUT,
     * except this account's own file that fails to decrypt (cut off at rest, say): that's replaced
     * in place, since Nextcloud's version history keeps the old content.
     */
    private suspend fun performContactsBackup() {
        // Key up front: writers ALWAYS encrypt, so without the key the backup is skipped rather than
        // uploaded readable.
        val wallet = nextcloudService.activeWalletAddress ?: throw NextcloudException(BackupCrypto.KEY_UNAVAILABLE_MESSAGE)
        val key = backupEncryptionKey(wallet) ?: throw NextcloudException(BackupCrypto.KEY_UNAVAILABLE_MESSAGE)
        moveToEncryptedContactsIfNeeded(wallet)
        ensureStillActive(wallet)

        val raw = nextcloudService.downloadBackup() // null: nothing there yet, the one other case that writes without merging
        var serverCopy: String? = null
        var replaceUnreadable = false
        if (raw != null) {
            serverCopy = openContactsFile(raw, wallet) { BackupCrypto.decrypt(it, key) }
            if (serverCopy == null) {
                Log.w(TAG, "This account's contacts file can't be decrypted; replacing it in place")
                replaceUnreadable = true
            }
        }
        ensureStillActive(wallet)

        val book = addressBookManager.get()
        if (!book.holdsWallet(wallet)) throw CancellationException("The active account changed.")
        var serverIsBehind = replaceUnreadable || book.hasBackupData
        if (serverCopy != null) serverIsBehind = book.mergeBackup(serverCopy.toByteArray(Charsets.UTF_8)).serverIsBehind
        if (!serverIsBehind) {
            nextcloudService.setLastKnownEtag(fetchEtagOrNull(), wallet)
            nextcloudService.stampBackup()
            return
        }

        val body = BackupCrypto.encrypt(String(book.exportData(includingDeleted = true), Charsets.UTF_8), key, wallet)
            .toByteArray(Charsets.UTF_8)
        ensureStillActive(wallet)
        var newEtag = nextcloudService.uploadBackup(body)
        // A relay that cuts a large PUT can leave a short file, which the next sync would read as
        // damaged. Say so plainly instead of looping. Only our own write counts: another device may
        // have replaced the file in between.
        val storedSize = nextcloudService.contactsFileInfo()?.size
        if (storedSize != null && storedSize != body.size.toLong()) {
            val currentEtag = fetchEtagOrNull()
            if (newEtag == null || currentEtag == newEtag) {
                throw NextcloudException(
                    "The backup upload was cut off: the server stored $storedSize of ${body.size} bytes. " +
                        "Something between this device and Nextcloud is ending large uploads early."
                )
            }
        }
        // Some proxies strip the PUT response's ETag header.
        if (newEtag == null) newEtag = fetchEtagOrNull()
        // Remember our own write's ETag so the watcher never downloads it back.
        if (nextcloudService.activeWalletAddress != wallet) return
        nextcloudService.setLastKnownEtag(newEtag, wallet)
        nextcloudService.stampBackup()
        Log.i(TAG, "Contacts backed up")
    }

    private suspend fun fetchEtagOrNull(): String? = try {
        nextcloudService.fetchBackupEtag()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** The active wallet's envelope key, from the chatting address's raw private key, as in KaChat.
     *  Null unless the key is [wallet]'s: the key a mid-flight switch would hand back isn't. */
    private fun backupEncryptionKey(wallet: String): ByteArray? {
        if (walletManager.getActiveAccount()?.address != wallet) return null
        val key = runCatching { BackupCrypto.deriveKey(walletManager.getPrivateKeyBytes()) }.getOrNull() ?: return null
        return key.takeIf { walletManager.getActiveAccount()?.address == wallet }
    }

    /**
     * Once per wallet: finds the folder KaChat's contacts file is in, and merges the plain files the
     * contacts used to be kept in, so nothing in them is lost. The plain files stay where they are,
     * and aren't read again. A failure leaves this to run again with the next backup; it never stops
     * one.
     */
    private suspend fun moveToEncryptedContactsIfNeeded(walletAtStart: String) {
        if (nextcloudService.plainContactsMerged) return
        try {
            if (nextcloudService.account.value?.backupFolder == null) {
                nextcloudService.discoverExistingBackupFolder(walletAtStart)?.let { found ->
                    if (nextcloudService.activeWalletAddress != walletAtStart) return
                    nextcloudService.setBackupFolder(found, forWallet = walletAtStart)
                }
            }
            // Once this account's encrypted file is there, a plain file proves nothing: anyone who
            // can write to the folder could have put it there, and merging it would add their
            // address under a friend's name (iOS 2da5f4b).
            if (hasOwnEncryptedContactsFile(walletAtStart)) {
                if (nextcloudService.activeWalletAddress != walletAtStart) return
                nextcloudService.markPlainContactsMerged(walletAtStart)
                return
            }
            // Every name, not just the newest: a device on an older build may have kept writing an
            // older one. Newest edit or deletion wins, so an old file can't undo a newer change.
            val book = addressBookManager.get()
            for (name in NextcloudService.PLAIN_CONTACTS_FILE_NAMES) {
                val plain = nextcloudService.downloadBackupFile(name, emptyList()) ?: continue
                if (nextcloudService.activeWalletAddress != walletAtStart || !book.holdsWallet(walletAtStart)) return
                book.mergeBackup(plain)
            }
            if (nextcloudService.activeWalletAddress != walletAtStart) return
            nextcloudService.markPlainContactsMerged(walletAtStart)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Merging the earlier contacts file failed: ${e.message}")
        }
    }

    /** Whether the backup folder's contacts file is an envelope [wallet]'s key opens. A file that
     *  can't be read counts as no. */
    private suspend fun hasOwnEncryptedContactsFile(wallet: String): Boolean {
        val key = backupEncryptionKey(wallet) ?: return false
        val data = try {
            nextcloudService.downloadFile(
                "${nextcloudService.backupFolderPath}/${NextcloudService.BACKUP_FILE_NAME}",
                NextcloudService.BACKUP_MAX_BYTES,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        val text = String(data, Charsets.UTF_8)
        return BackupCrypto.isEnvelope(text) && runCatching { BackupCrypto.decrypt(text, key) }.isSuccess
    }

    // -------------------------------------------------------------------------
    // Portfolio backup
    // -------------------------------------------------------------------------

    /** [wallet] with its own login and folder, snapshotted for one Portfolio sync; throws while it
     *  isn't the active wallet any more, or isn't connected. */
    private fun portfolioTarget(wallet: String): NextcloudService.BackupTarget {
        val target = nextcloudService.backupTarget() ?: throw NextcloudException("Nextcloud isn't connected for this account.")
        if (target.wallet != wallet) throw CancellationException("The active account changed.")
        return target
    }

    /**
     * Once per account: merges the one file the Portfolio used to be kept in. It stays where it is
     * and isn't read again. A failure leaves this to run again with the next backup. Returns whether
     * it changed anything here.
     *
     * The file is one per Nextcloud login and only an earlier build's had an owner inside. With an
     * owner, only that account takes it. Without one, an account takes it only if no other account
     * on this device has taken this login's file before (iOS `singlePortfolioLoginsKey`) and none of
     * its portfolios or rows belong to another wallet here - otherwise it is that account's, and
     * merging it would hand that account's portfolios to this one.
     */
    private suspend fun mergeSinglePortfolioFileIfNeeded(target: NextcloudService.BackupTarget): Boolean {
        val walletAtStart = target.wallet
        if (nextcloudService.isSinglePortfolioMerged(walletAtStart)) return false
        return try {
            var changed = false
            val names = NextcloudService.SINGLE_PORTFOLIO_FILE_NAMES
            nextcloudService.downloadBackupFile(names.first(), names.drop(1), target.account)?.let { data ->
                if (nextcloudService.activeWalletAddress != walletAtStart) return false
                val file = PortfolioBackupFile.decode(data)
                val isOurs = when (val owner = file.walletAddress) {
                    null -> !nextcloudService.isSinglePortfolioLoginClaimed(target.account) &&
                        !portfolioLedgerStore.get().holdsOtherWalletsItems(walletAtStart, file.sync)
                    else -> owner.equals(walletAtStart, ignoreCase = true)
                }
                if (isOurs) {
                    changed = importPortfolio(walletAtStart, file.sync)
                    nextcloudService.claimSinglePortfolioLogin(target.account)
                } else {
                    Log.i(TAG, "The earlier Portfolio file is another account's; left alone")
                }
            }
            if (nextcloudService.activeWalletAddress != walletAtStart) return changed
            nextcloudService.markSinglePortfolioMerged(walletAtStart)
            changed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Merging the earlier Portfolio file failed: ${e.message}")
            false
        }
    }

    /** Merges every Portfolio file of this account into this device, without uploading. */
    private suspend fun mergePortfoliosFromServer(target: NextcloudService.BackupTarget): Pair<Boolean, NextcloudService.ServerPortfolios> {
        var changed = mergeSinglePortfolioFileIfNeeded(target)
        val server = nextcloudService.readServerPortfolios(target)
        ensureStillActive(target.wallet)
        if (server.own.isNotEmpty()) {
            val merged = importPortfolio(target.wallet, PortfolioSync.merge(server.own.map { it.backup.sync }))
            changed = changed || merged
        }
        return changed to server
    }

    /** Back Up Portfolio Now and the automatic backup both come here, one at a time. */
    suspend fun runPortfolioBackup() {
        portfolioMutex.withLock { performPortfolioBackup() }
    }

    /**
     * Uploads each portfolio to its own file, skipping one already up to date. What's on the server
     * is merged in first, so another device's edits and deletions survive. Afterwards, this account's
     * file for a portfolio that was deleted or renamed goes: the other files carry the deletion.
     *
     * The wallet, its login and its folder are snapshotted once, here: every read, PUT and DELETE
     * uses them, and the run stops before any write once another wallet is active, so a backup
     * never writes into the next account's Nextcloud.
     */
    private suspend fun performPortfolioBackup() {
        val walletAtStart = nextcloudService.activeWalletAddress
            ?: throw NextcloudException("Sign in to an account before connecting Nextcloud.")
        val target = portfolioTarget(walletAtStart)
        // Writers always encrypt: without the key nothing is read or written.
        val key = backupEncryptionKey(walletAtStart) ?: throw NextcloudException(BackupCrypto.KEY_UNAVAILABLE_MESSAGE)
        val server = mergePortfoliosFromServer(target).second

        val changesAtRead = portfolioChanges.get()
        val ledgerState = portfolioLedgerStore.get().syncState(walletAtStart)
        var state = PortfolioSync.merge(listOf(ledgerState.forArchive()))
        // Every file carries every deletion, but the untouched seed has no file. With only the
        // untouched "Portfolio 1" left after a deletion, its file carries the deletion, or no file
        // would and the other devices would put the deleted portfolio back.
        if (state.portfolios.isEmpty() && state.tombstones.isNotEmpty()) {
            state = PortfolioSync.merge(listOf(ledgerState))
        }
        val seedIds = ledgerState.portfolios
            .filter { PortfolioSync.isPristineSeed(it, ledgerState.transactions, ledgerState.fees) }
            .map { it.id }.toSet()
        val names = NextcloudService.portfolioFileNames(state.portfolios, server.taken)
        for (portfolio in state.portfolios) {
            val name = names[portfolio.id] ?: continue
            val one = PortfolioSync(
                portfolios = listOf(portfolio),
                transactions = state.transactions.filter { it.portfolioId == portfolio.id },
                fees = state.fees.filter { it.portfolioId == portfolio.id },
                tombstones = state.tombstones,
            )
            // A plain file of this account's goes up again, encrypted, even when nothing in it changed.
            if (server.own.any { found ->
                    if (!found.file.name.equals(name, ignoreCase = true) || !found.encrypted) return@any false
                    if (found.backup.sync == one) return@any true
                    // another device's untouched seed carrying the same deletions does the same job:
                    // writing ours over it would only have the two devices take turns
                    val backup = found.backup.sync
                    portfolio.id in seedIds &&
                        backup.portfolios.all { PortfolioSync.isPristineSeed(it, backup.transactions, backup.fees) } &&
                        PortfolioSync.mergeTombstones(listOf(backup.tombstones)) == one.tombstones
                }
            ) continue
            val sealed = BackupCrypto.encrypt(
                String(PortfolioBackupFile.encode(one, walletAtStart), Charsets.UTF_8), key, walletAtStart,
            ).toByteArray(Charsets.UTF_8)
            ensureStillActive(walletAtStart)
            nextcloudService.uploadToKaChatFolder(
                sealed, name, "application/json",
                keepSpaces = true, folder = target.folder, account = target.account,
            )
        }

        // With nothing uploaded, no file carries a deletion: the deleted portfolio's file stays.
        if (state.portfolios.isNotEmpty()) {
            val deletedIds = state.tombstones.filter { it.kind == PortfolioSync.KIND_PORTFOLIO }.map { it.id }.toSet()
            // a name just written is a current portfolio's, whatever the listing from before said
            val writtenNames = names.values.map { it.lowercase() }.toSet()
            for (found in server.own) {
                val ids = found.backup.sync.portfolios.map { it.id }
                if (found.file.name.lowercase() in writtenNames) continue
                // Only for what this device knows the fate of: never a portfolio it simply doesn't have.
                // This device's own untouched seed, uploaded while it carried a deletion, goes too.
                if (!ids.all { names[it] != null || it in deletedIds || it in seedIds }) continue
                ensureStillActive(walletAtStart)
                nextcloudService.deleteFile(found.file.path, target.account)
            }
        }

        ensureStillActive(walletAtStart)
        // every file of this account read as plain was written again encrypted (or deleted)
        nextcloudService.markPlainPortfoliosRetired(walletAtStart)
        // an edit made during the upload isn't in it: still owed
        if (portfolioChanges.get() == changesAtRead) nextcloudService.setBackupPending(false)
        nextcloudService.stampBackup()
        Log.i(TAG, "Portfolio backed up")
    }

    /** Merges [incoming] into [wallet]'s portfolios (iOS `PortfolioViewModel.importFromArchive`);
     *  rows that arrived still waiting for a price get priced, as iOS's reload restarts its
     *  backfill. Returns whether anything here changed. */
    private suspend fun importPortfolio(wallet: String, incoming: PortfolioSync): Boolean {
        val changed = portfolioLedgerStore.get().importFromArchive(wallet, incoming)
        if (changed) runCatching { portfolioRepository.get().startPriceBackfillIfNeeded(wallet) }
        return changed
    }

    // -------------------------------------------------------------------------
    // What the settings page starts (run here, so leaving the page never cuts one off)
    // -------------------------------------------------------------------------

    /**
     * Connects [NextcloudService.connect] and points at the contacts file this account already has,
     * before anything reads or writes one. What this account made on another device, or in KaChat,
     * then comes back by itself, once - with a toast saying what came back (iOS `connect`). The
     * returned job finishes with the connection; the merge runs on.
     */
    fun connectAsync(serverInput: String, username: String, appPassword: String): Deferred<Unit> = scope.async {
        // the account Connect was tapped in, whatever is active by the time the server answers
        val wallet = nextcloudService.connect(serverInput, username, appPassword)
        if (nextcloudService.activeWalletAddress != wallet) return@async
        nextcloudService.discoverExistingBackupFolder(wallet)?.let { found ->
            if (nextcloudService.activeWalletAddress != wallet) return@async
            Log.i(TAG, "Linked existing backup folder: $found")
            nextcloudService.setBackupFolder(found, forWallet = wallet)
        }
        if (nextcloudService.activeWalletAddress != wallet) return@async
        noteContactActivity()
        scope.launch {
            mergeAfterConnect(wallet)?.let { com.kachat.app.ui.screens.IosToasts.show(it) }
        }
        Unit
    }

    /** Merges both backups into this device, without uploading. Returns the toast, or null when
     *  nothing changed here. Each file is on its own: a problem with one doesn't stop the other. */
    private suspend fun mergeAfterConnect(walletAtStart: String): String? {
        val book = addressBookManager.get()
        val contactsBefore = book.entries.value.size
        try {
            contactsMutex.withLock {
                moveToEncryptedContactsIfNeeded(walletAtStart)
                val raw = nextcloudService.downloadBackup() ?: return@withLock // nothing there yet
                val key = backupEncryptionKey(walletAtStart) ?: throw NextcloudException(BackupCrypto.DECRYPT_FAILED_MESSAGE)
                val plaintext = openContactsFile(raw, walletAtStart) { BackupCrypto.decrypt(it, key) }
                    ?: throw NextcloudException(BackupCrypto.DECRYPT_FAILED_MESSAGE)
                if (nextcloudService.activeWalletAddress != walletAtStart || !book.holdsWallet(walletAtStart)) return null
                book.mergeBackup(plaintext.toByteArray(Charsets.UTF_8))
                Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            noteRejectedLogin(e)
            noteContactsSyncFailure(e, walletAtStart)
            Log.w(TAG, "Reading the contacts file failed: ${e.message}")
        }
        var portfolioChanged = false
        try {
            portfolioChanged = portfolioMutex.withLock { mergePortfoliosFromServer(portfolioTarget(walletAtStart)) }.first
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            noteRejectedLogin(e)
            Log.w(TAG, "Reading the Portfolio backup failed: ${e.message}")
        }
        if (nextcloudService.activeWalletAddress != walletAtStart) return null
        val added = book.entries.value.size - contactsBefore
        return when {
            added > 0 && portfolioChanged ->
                context.getString(com.kachat.app.R.string.ncw_restored_addresses_and_portfolio_toast, added)
            added > 0 -> context.getString(com.kachat.app.R.string.ncw_restored_addresses_toast, added)
            portfolioChanged -> context.getString(com.kachat.app.R.string.ncw_restored_portfolio_toast)
            else -> null
        }
    }

    /** Back Up Contacts Now. */
    fun backUpContactsNowAsync(): Deferred<Unit> = scope.async { runContactsBackup() }

    /** Back Up Portfolio Now. */
    fun backUpPortfolioNowAsync(): Deferred<Unit> = scope.async { runPortfolioBackup() }

    // -------------------------------------------------------------------------
    // Account removal + the periodic fallback
    // -------------------------------------------------------------------------

    /**
     * Account deletion: drops [walletAddress]'s pending uploads and removes the chat-sync keys an
     * older build kept for it - the sibling of [NextcloudService.purgeStoredState], which drops
     * the credentials, toggle, stamps and owed flags themselves.
     */
    fun purgeStoredState(walletAddress: String) {
        if (nextcloudService.activeWalletAddress == walletAddress) cancelPendingWork()
        val suffix = NextcloudService.walletHashSuffix(walletAddress)
        scope.launch {
            dataStore.edit { prefs ->
                for (base in LEGACY_KEY_BASES) {
                    prefs.remove(longPreferencesKey("${base}_$suffix"))
                    prefs.remove(booleanPreferencesKey("${base}_$suffix"))
                    prefs.remove(stringPreferencesKey("${base}_$suffix"))
                }
            }
        }
    }

    private fun ensurePeriodicWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NextcloudAutoSyncWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        )
    }

    private fun cancelScheduledWork() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
