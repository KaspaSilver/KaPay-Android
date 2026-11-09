package com.kachat.app.services

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A connected Nextcloud account (server + login + app password). */
data class NextcloudAccount(
    val server: String,
    val username: String,
    val appPassword: String,
    /** Where the contacts and Portfolio files live - null means the default "KaChat" folder at the
     *  files root, "" the files root itself (iOS 953b255). */
    val backupFolder: String? = null,
) {
    val displayName: String
        get() {
            val host = server.toHttpUrlOrNull()?.host ?: server
            return "$username@$host"
        }
}

/** One entry from a WebDAV folder listing. [path] is relative to the user's files root, e.g. "Photos/cat.jpg". */
data class NextcloudFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val contentType: String?,
    val size: Long?,
    val modifiedMs: Long?
) {
    /** Content-Type first, file extension as fallback — servers without a mimetype mapping for
     *  HEIC/MOV and friends report `application/octet-stream`, which would otherwise hide real
     *  media from the picker entirely. */
    val isImage: Boolean
        get() = contentType?.startsWith("image/") == true || extension in IMAGE_EXTENSIONS

    val isVideo: Boolean
        get() = contentType?.startsWith("video/") == true || extension in VIDEO_EXTENSIONS

    private val extension: String get() = path.substringAfterLast('.', "").lowercase()

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "heic", "heif", "bmp", "tiff")
        val VIDEO_EXTENSIONS = setOf("mov", "mp4", "m4v", "webm", "mkv", "avi")
    }
}

/**
 * The one ordering every Nextcloud listing surface uses: phone-gallery order.
 *
 * Folders stay grouped ahead of files (so a folder never lands in the middle of the thumbnail
 * grid), and within each group entries run newest-first by `getlastmodified`. Entries whose date
 * the server omitted or that failed to parse sort last rather than interleaving randomly, and name
 * is the tiebreak so equal timestamps stay deterministic.
 *
 * Applied once in [NextcloudService.listFolder]; screens only filter, never re-sort.
 */
private val NEWEST_FIRST: Comparator<NextcloudFile> =
    compareByDescending<NextcloudFile> { it.isDirectory }
        .thenByDescending { it.modifiedMs ?: Long.MIN_VALUE }
        .thenBy { it.name.lowercase() }

fun List<NextcloudFile>.sortedNewestFirst(): List<NextcloudFile> = sortedWith(NEWEST_FIRST)

/**
 * What went wrong talking to Nextcloud, in words the person can act on (iOS `NextcloudError`).
 * Deliberately NOT an IOException: [com.kachat.app.util.UserFacingError] shows an app-authored
 * exception's own message, where an IOException would read as a generic network error - and
 * "Nextcloud rejected the username or app password." is exactly what the reader needs to see.
 */
open class NextcloudException(message: String) : Exception(message)

/** A non-2xx answer (iOS `NextcloudError.httpError`): [code] lets a caller tell a 404 apart. */
class NextcloudHttpException(val code: Int) : NextcloudException("Nextcloud returned HTTP $code.")

/** A 401 (iOS `NextcloudError.badCredentials`): it won't fix itself, so automatic work stops
 *  ([NextcloudService.needsReconnect]). */
class NextcloudRejectedLoginException : NextcloudException("Nextcloud rejected the username or app password.")

/**
 * The user's own Nextcloud server, for one thing: backing up Contacts and the Portfolio
 * (iOS `NextcloudService`, 953b255). Connect with an app password. Two kinds of file:
 * - [BACKUP_FILE_NAME] in the backup folder: the contacts, encrypted ([BackupCrypto]). It's the same
 *   file KaChat uses, with the same key, so both apps and all of an account's devices sync through
 *   it. KaChat's NEXTCLOUD_SYNC.md, "Contacts only", is the contract.
 * - One file per portfolio in the backup folder, named what the portfolio is named ("Long Term.json"):
 *   its transactions and fees plus what was deleted ([PortfolioBackupFile]), in the same encrypted v1
 *   envelope as the contacts ([BackupCrypto]; a plain one from an earlier build is still read). The file
 *   says which account it belongs to, and another account's is left alone.
 * The contacts' earlier plain files ([PLAIN_CONTACTS_FILE_NAMES]) and the one Portfolio file the app
 * used to write ([SINGLE_PORTFOLIO_FILE_NAMES]) are merged in once and never written again. KaChat's
 * `kachat-backup.json` may share the folder; the wallet never touches it. When and what to upload is
 * [NextcloudSyncService]'s; this class holds the account, the per-wallet settings and the WebDAV
 * calls. Import / Export in Contacts and the Portfolio also reach the KaChat folder
 * ([uploadToKaChatFolder], [downloadFile], [listFolder]).
 *
 * Credentials live in their own `EncryptedSharedPreferences` file (same secure-prefs pattern as
 * [ColdStorageManager]). Everything is scoped to the ACTIVE WALLET ACCOUNT, matching iOS: every
 * stored key (credentials, toggle, last-backup stamp, pending flag) carries the active wallet's
 * 8-byte SHA256 hash suffix (iOS's `KeychainService.walletHashSuffix`), and the service follows
 * [WalletManager.activeAddressFlow] so an account switch/logout/delete swaps the whole state and
 * one account's login never leaks into another.
 */
@Singleton
class NextcloudService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletManager: WalletManager
) {
    companion object {
        private const val TAG = "NextcloudService"

        private const val SECURE_PREFS_NAME = "nextcloud_secure_prefs"
        // Base names only: the active wallet's hash suffix is appended via `scopedKey`. The bare
        // names are the LEGACY pre-per-account entries, read once by the migration then deleted.
        private const val PREF_SERVER = "server"
        private const val PREF_USERNAME = "username"
        private const val PREF_APP_PASSWORD = "app_password"
        /** [NextcloudAccount.backupFolder]; "" is the files root, absent the default. */
        private const val PREF_BACKUP_FOLDER = "backup_folder"
        /** Settings > Storage > Nextcloud > "Automatic Sync", per wallet. Default off: a
         *  connection is not a request to start uploading (iOS `kachat_nextcloud_auto_backup`). */
        private const val PREF_WALLET_AUTO_BACKUP = "wallet_auto_backup"
        /** When this wallet's backup last uploaded (epoch ms). */
        private const val PREF_WALLET_LAST_BACKUP_MS = "wallet_last_backup_ms"
        /** The Portfolio changed since the last successful upload. Persisted, so a kill before the
         *  upload still backs up on the next background or launch. */
        private const val PREF_WALLET_PENDING_BACKUP = "wallet_pending_backup"
        /** When the contacts last synced automatically: Settings' "Last synced", and the floor
         *  between automatic uploads (iOS `kachat_nextcloud_last_auto_backup`). */
        private const val PREF_WALLET_LAST_AUTO_SYNC_MS = "wallet_last_auto_sync_ms"
        /** The contacts' dirty flag: a contact changed after the last successful automatic upload
         *  (iOS `kachat_nextcloud_pending_sync`). */
        private const val PREF_WALLET_PENDING_SYNC = "wallet_pending_sync"
        /** The server ETag of the last contacts file this device merged or wrote, normalized
         *  (iOS `kachat_nextcloud_last_etag`). */
        private const val PREF_WALLET_LAST_ETAG = "wallet_last_etag"
        /** This wallet's earlier plain contacts files have been merged into its contacts. */
        private const val PREF_WALLET_PLAIN_CONTACTS_MERGED = "wallet_plain_contacts_merged"
        /** This wallet's earlier single Portfolio file has been merged in. */
        private const val PREF_WALLET_SINGLE_PORTFOLIO_MERGED = "wallet_single_portfolio_merged"
        /** This wallet's Portfolio files are all encrypted now: a plain one isn't read any more
         *  (iOS `kachat_nextcloud_plain_portfolios_retired`). */
        private const val PREF_WALLET_PLAIN_PORTFOLIOS_RETIRED = "wallet_plain_portfolios_retired"
        /** Why the contacts last failed to sync (another account's file in the Backup Folder, say),
         *  until a sync succeeds or the folder changes: Settings shows it. */
        private const val PREF_WALLET_CONTACTS_SYNC_ERROR = "wallet_contacts_sync_error"
        /** Global, not per wallet: every login ("server|user", lowercased) whose ownerless single
         *  Portfolio file has been merged here. It goes to one account, not every one on that login
         *  (iOS `kachat_nextcloud_single_portfolio_logins`). */
        private const val PREF_SINGLE_PORTFOLIO_LOGINS = "single_portfolio_logins"

        // Never read any more: kept so a disconnect or account removal still clears a value an
        // older build stored (iOS keeps KaChat's in `storedKeys` too).
        private const val PREF_START_FOLDER = "start_folder"
        private const val PREF_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val PREF_LAST_AUTO_BACKUP_MS = "last_auto_backup_ms"
        private const val PREF_MEDIA_SEND_ENABLED = "media_send_enabled"
        private const val PREF_TALK_CALLS = "talk_calls_available"

        /** Every per-wallet key base — the unit `disconnect`/`purgeStoredState`/migration act on. */
        private val ALL_PREF_BASES = listOf(
            PREF_SERVER, PREF_USERNAME, PREF_APP_PASSWORD, PREF_BACKUP_FOLDER,
            PREF_WALLET_AUTO_BACKUP, PREF_WALLET_LAST_BACKUP_MS, PREF_WALLET_PENDING_BACKUP,
            PREF_WALLET_LAST_AUTO_SYNC_MS, PREF_WALLET_PENDING_SYNC, PREF_WALLET_LAST_ETAG,
            PREF_WALLET_PLAIN_CONTACTS_MERGED, PREF_WALLET_SINGLE_PORTFOLIO_MERGED, PREF_WALLET_CONTACTS_SYNC_ERROR,
            PREF_WALLET_PLAIN_PORTFOLIOS_RETIRED,
            PREF_START_FOLDER, PREF_AUTO_BACKUP_ENABLED, PREF_LAST_AUTO_BACKUP_MS,
            PREF_MEDIA_SEND_ENABLED, PREF_TALK_CALLS
        )

        /** First 8 bytes of SHA256(walletAddress) as hex — byte-identical to iOS's
         *  `KeychainService.walletHashSuffix` and desktop's per-account scheme. */
        fun walletHashSuffix(walletAddress: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(walletAddress.toByteArray(Charsets.UTF_8))
                .take(8)
                .joinToString("") { "%02x".format(it) }

        /** The KaChat folder at the files root: the default backup folder, where Contacts and
         *  Portfolio exports go, and where the earlier plain files are. */
        const val BACKUP_FOLDER_NAME = "KaChat"

        /** The contacts, encrypted, shared with KaChat (iOS 953b255 `backupFileName`). */
        const val BACKUP_FILE_NAME = "kachat-contacts.json"
        /** KaChat's old chat backup, which may share the folder. KaPay never touches it. */
        private const val CHAT_BACKUP_FILE_NAME = "kachat-backup.json"
        /** The plain files the contacts were kept in before, newest first: "KaPay Contacts.json" until
         *  2026-11-05, "KaPay Address Book.json" (the Address Book became Contacts, 2026-10-28) and the
         *  KaChat Wallet name (the app's name until 2026-10-27). Read once, never written. */
        val PLAIN_CONTACTS_FILE_NAMES = listOf("KaPay Contacts.json", "KaPay Address Book.json", "KaChat Wallet Address Book.json")
        /** The one file the Portfolio used to be kept in, newest name first. Read once, never written. */
        val SINGLE_PORTFOLIO_FILE_NAMES = listOf("KaPay Portfolio.json", "KaChat Wallet Portfolio.json")

        /** A backup file is read whole; this bounds it (iOS `maxBytes: 50_000_000`). */
        const val BACKUP_MAX_BYTES = 50_000_000L

        /** Pauses between PUT attempts while the file is locked - half a minute in all. */
        private val LOCK_RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)
        /** Bounds for [discoverExistingBackupFolder] - a connect must not turn into a full crawl. */
        private const val FOLDER_DISCOVERY_MAX_FOLDERS = 40
        private const val FOLDER_DISCOVERY_MAX_DEPTH = 3

        private val ETAG_REGEX = Regex("<[^<>]*getetag[^<>]*>([^<]+)</[^<>]*getetag[^<>]*>", RegexOption.IGNORE_CASE)

        /** Pulls the getetag value out of a Depth-0 multistatus without a full XML pass - servers
         *  vary the namespace prefix (`d:`, `D:`, none), so any tag whose name contains "getetag"
         *  matches. */
        internal fun parseEtagFromMultistatus(xml: String): String? {
            val raw = ETAG_REGEX.find(xml)?.groupValues?.get(1) ?: return null
            return normalizeEtag(raw.replace("&quot;", "\"").replace("&amp;", "&")).takeIf { it.isNotEmpty() }
        }

        /** Strips the weak-validator prefix and surrounding quotes so a PUT response header ETag
         *  and a PROPFIND getetag for the same content compare equal. */
        internal fun normalizeEtag(raw: String): String = raw.trim().removePrefix("W/").trim('"')

        /** Keeps stored filenames WebDAV/URL-safe (iOS sanitizedFilename): letters, marks and digits
         *  of any script stay (CharacterSet.alphanumerics), plus "._-" and, with [keepSpaces], the
         *  space; everything else becomes "_". */
        internal fun sanitizedFilename(name: String, keepSpaces: Boolean = false): String {
            val disallowed = if (keepSpaces) Regex("[^\\p{L}\\p{M}\\p{N}._ -]") else Regex("[^\\p{L}\\p{M}\\p{N}._-]")
            return name.replace(disallowed, "_").ifEmpty { "file" }
        }

        /** Contacts and chat files, which can be large and are never portfolios, and anything that
         *  isn't JSON or is too large to read whole. */
        internal fun isPortfolioFileCandidate(name: String, isDirectory: Boolean = false, size: Long? = null): Boolean {
            val lower = name.lowercase()
            if (isDirectory || !lower.endsWith(".json") || (size ?: 0L) > BACKUP_MAX_BYTES) return false
            val notPortfolios = (listOf(BACKUP_FILE_NAME, CHAT_BACKUP_FILE_NAME) + PLAIN_CONTACTS_FILE_NAMES).map { it.lowercase() }
            return lower !in notPortfolios && !lower.startsWith("kapay contacts ")
        }

        /**
         * Each portfolio's file name: its own name, with a number only where another file already has
         * that name. The same portfolios get the same names on every device (iOS `portfolioFileNames`).
         */
        internal fun portfolioFileNames(portfolios: List<SyncPortfolio>, taken: Set<String>): Map<String, String> {
            val used = taken.toMutableSet()
            val names = HashMap<String, String>()
            for (portfolio in portfolios.sortedWith(compareBy<SyncPortfolio> { it.createdAtMillis }.thenBy { it.id })) {
                val trimmed = portfolio.name.trim()
                // 60 characters as a person counts them (never half an emoji), as Swift's prefix(60).
                val breaks = java.text.BreakIterator.getCharacterInstance().apply { setText(trimmed) }
                var end = 0
                var count = 0
                while (count < 60) {
                    val next = breaks.next()
                    if (next == java.text.BreakIterator.DONE) { end = trimmed.length; break }
                    end = next
                    count++
                }
                var base = if (trimmed.isEmpty()) "Portfolio" else sanitizedFilename(trimmed.substring(0, end), keepSpaces = true)
                // A name no number can make allowed ("KaPay Contacts ...") uses "Portfolio", as KaChat
                // Desktop does, so both apps give it the same file; numbering it alone never ended
                // (iOS 4523157).
                if (!isPortfolioFileCandidate("$base 2.json")) base = "Portfolio"
                var candidate = "$base.json"
                var number = 2
                while (candidate.lowercase() in used || !isPortfolioFileCandidate(candidate)) {
                    candidate = "$base $number.json"
                    number++
                }
                used += candidate.lowercase()
                names[portfolio.id] = candidate
            }
            return names
        }

        /** Normalizes user input ("mycloud.duckdns.org", trailing slashes, an accidental
         *  "/index.php" suffix) into a clean base URL, defaulting to https. Null if it doesn't
         *  parse as a URL at all, or if it explicitly asks for http:// — credentials must never
         *  travel unencrypted, so plaintext HTTP is rejected outright (the settings form shows
         *  the same rule inline before this is ever reached). */
        fun normalizeServer(input: String): String? {
            var raw = input.trim()
            if (raw.isEmpty()) return null
            if (raw.lowercase().startsWith("http://")) return null
            if (!raw.lowercase().startsWith("https://")) {
                raw = "https://$raw"
            }
            raw = raw.trimEnd('/')
            if (raw.lowercase().endsWith("/index.php")) raw = raw.dropLast("/index.php".length)
            val url = raw.toHttpUrlOrNull() ?: return null
            return if (url.host.isEmpty()) null else raw
        }

        /** WebDAV's getlastmodified is RFC 1123 ("Mon, 11 Aug 2026 20:14:07 GMT"). */
        private fun rfc1123Formatter() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        private const val BAD_CREDENTIALS = "Nextcloud rejected the username or app password."
        private const val MALFORMED = "Unexpected response from the Nextcloud server."
    }

    // The same encrypted file as always. Built at startup (NextcloudSyncService): where the
    // Keystore refuses to open it (seen on hardened ROMs), Nextcloud shows as not connected for
    // this session and the file - the saved account included - is left as it is for the next
    // launch, rather than the app failing to open.
    private val prefs = com.kachat.app.util.StartupSafety.encryptedPrefsOrInMemory(
        context,
        SECURE_PREFS_NAME,
        "NextcloudService"
    )

    // A longer read timeout than the default: Nextcloud can sit silent for a while storing a
    // backup with many photos in it.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _account = MutableStateFlow<NextcloudAccount?>(null)
    val account: StateFlow<NextcloudAccount?> = _account.asStateFlow()

    /** Settings > Storage > Nextcloud > "Automatic Sync", per wallet account. Default off. */
    private val _autoBackupEnabled = MutableStateFlow(false)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    /** When the active wallet's backup last uploaded (epoch ms), null = never. */
    private val _lastBackupMs = MutableStateFlow<Long?>(null)
    val lastBackupMs: StateFlow<Long?> = _lastBackupMs.asStateFlow()

    /** When the active wallet's contacts last synced automatically (epoch ms), null = never. */
    private val _lastAutoSyncMs = MutableStateFlow<Long?>(null)
    val lastAutoSyncMs: StateFlow<Long?> = _lastAutoSyncMs.asStateFlow()

    /** Nextcloud refused this login (a revoked app password, say). Automatic Sync stops asking until
     *  the next launch or connection, and Settings says what to do (iOS 953b255). */
    private val _needsReconnect = MutableStateFlow(false)
    val needsReconnect: StateFlow<Boolean> = _needsReconnect.asStateFlow()

    /** Why the active wallet's contacts last failed to sync, kept until a sync succeeds or the
     *  Backup Folder changes; null when the last one went through. Settings shows it. */
    private val _contactsSyncError = MutableStateFlow<String?>(null)
    val contactsSyncError: StateFlow<String?> = _contactsSyncError.asStateFlow()

    /** [activeWalletAddress] as a flow, updated once the wallet's whole state has loaded. */
    private val _activeWallet = MutableStateFlow<String?>(null)
    val activeWallet: StateFlow<String?> = _activeWallet.asStateFlow()

    /** The Portfolio files last read, so an unchanged file isn't downloaded again. A null backup is
     *  a file that isn't one of this account's portfolios; [damaged] is this account's envelope that
     *  won't open. Keyed by wallet and path: what a file reads as depends on the account's key. */
    private data class CachedPortfolioFile(
        val modifiedMs: Long?,
        val size: Long?,
        val backup: PortfolioBackupFile.Decoded?,
        val encrypted: Boolean = false,
        val damaged: Boolean = false,
    )
    private val portfolioFileCache = java.util.concurrent.ConcurrentHashMap<String, CachedPortfolioFile>()

    /** The active wallet's address — every credential/settings read and write is scoped to it.
     *  Null (signed out / no wallet yet / testnet) presents as disconnected and persists nothing. */
    @Volatile
    private var currentWalletAddress: String? = null

    /** Cached per-wallet suffix (8-byte SHA256 hex, see [walletHashSuffix]) for the pref keys. */
    @Volatile
    private var currentSuffix: String? = null

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + com.kachat.app.util.StartupSafety.logOnlyHandler("NextcloudService")
    )

    init {
        // Load the current wallet's state synchronously (the settings screens may compose before
        // the collector's first dispatch), then follow every switch/logout/delete after that.
        setCurrentWallet(walletManager.activeAddressFlow.value, force = true)
        serviceScope.launch {
            walletManager.activeAddressFlow.collect { setCurrentWallet(it) }
        }
    }

    val isConnected: Boolean get() = _account.value != null

    /** The wallet this service is scoped to (null signed out or on testnet) - what a backup or
     *  restore snapshots at its start and re-checks before every write. */
    val activeWalletAddress: String? get() = currentWalletAddress

    /**
     * Off for the whole testnet launch (iOS af8ec68): the backup files are one per account across
     * both networks, so testnet could otherwise write testnet data over the mainnet backup. With
     * no account nothing backs up or restores, and every Nextcloud option hides as if
     * disconnected. The mainnet login is untouched and comes back on the next mainnet launch.
     */
    val isOffForTestnet: Boolean get() = com.kachat.app.util.KaspaNetwork.isTestnet

    /** The folder the contacts and Portfolio files are in: the one chosen or found ("" is the files
     *  root), or "KaChat" by default (iOS 953b255). Exports stay in the KaChat folder. */
    val backupFolderPath: String get() = _account.value?.backupFolder ?: BACKUP_FOLDER_NAME

    // -------------------------------------------------------------------------
    // Wallet scoping
    // -------------------------------------------------------------------------

    /** The active wallet's pref key for [base], or null when signed out (in which case nothing
     *  is read or written). */
    private fun scopedKey(base: String): String? = currentSuffix?.let { "${base}_$it" }

    private fun scopedKey(base: String, suffix: String): String = "${base}_$suffix"

    /**
     * Points the service at [requestedAddress]'s stored Nextcloud state, or clears everything for
     * null. Driven by [WalletManager.activeAddressFlow], which fires on every wallet load,
     * account switch, logout and delete ([NextcloudSyncService] cancels any pending backup when
     * the wallet changes).
     */
    private fun setCurrentWallet(requestedAddress: String?, force: Boolean = false) {
        val walletAddress = if (isOffForTestnet) null else requestedAddress
        if (!force && walletAddress == currentWalletAddress) return

        portfolioFileCache.clear()
        _needsReconnect.value = false
        currentWalletAddress = walletAddress
        currentSuffix = walletAddress?.let { walletHashSuffix(it) }

        if (walletAddress == null) {
            _account.value = null
            _autoBackupEnabled.value = false
            _lastBackupMs.value = null
            _lastAutoSyncMs.value = null
            _contactsSyncError.value = null
            _activeWallet.value = null
            return
        }

        migrateLegacyGlobalStateIfNeeded()

        _account.value = loadAccount()
        _autoBackupEnabled.value = _account.value != null &&
            (scopedKey(PREF_WALLET_AUTO_BACKUP)?.let { prefs.getBoolean(it, false) } ?: false)
        _lastBackupMs.value = scopedKey(PREF_WALLET_LAST_BACKUP_MS)?.let { prefs.getLong(it, 0L) }?.takeIf { it > 0 }
        _lastAutoSyncMs.value = scopedKey(PREF_WALLET_LAST_AUTO_SYNC_MS)?.let { prefs.getLong(it, 0L) }?.takeIf { it > 0 }
        _contactsSyncError.value = scopedKey(PREF_WALLET_CONTACTS_SYNC_ERROR)?.let { prefs.getString(it, null) }
        _activeWallet.value = walletAddress
    }

    /**
     * One-time migration off the pre-per-wallet storage: the single global credential entry
     * moves to the active wallet's scoped keys — the account that was actually using the login
     * keeps it — then the global entries are deleted so no other account ever sees them again.
     */
    private fun migrateLegacyGlobalStateIfNeeded() {
        val suffix = currentSuffix ?: return
        val legacyServer = prefs.getString(PREF_SERVER, null)
        val legacyUsername = prefs.getString(PREF_USERNAME, null)
        val legacyPassword = prefs.getString(PREF_APP_PASSWORD, null)
        val anyLegacy = ALL_PREF_BASES.any { prefs.contains(it) }
        if (!anyLegacy) return

        val editor = prefs.edit()
        if (legacyServer != null && legacyUsername != null && legacyPassword != null &&
            prefs.getString(scopedKey(PREF_SERVER, suffix), null) == null
        ) {
            editor.putString(scopedKey(PREF_SERVER, suffix), legacyServer)
            editor.putString(scopedKey(PREF_USERNAME, suffix), legacyUsername)
            editor.putString(scopedKey(PREF_APP_PASSWORD, suffix), legacyPassword)
            prefs.getString(PREF_BACKUP_FOLDER, null)?.let { editor.putString(scopedKey(PREF_BACKUP_FOLDER, suffix), it) }
        }
        for (base in ALL_PREF_BASES) editor.remove(base)
        editor.apply()
        Log.i(TAG, "Migrated the global Nextcloud login to the active wallet's per-account storage")
    }

    /**
     * Deletes a wallet's stored Nextcloud login and settings outright — used when that account
     * is removed from this device entirely. Storage only; if the wallet is still the active one,
     * the in-memory state clears too.
     */
    fun purgeStoredState(walletAddress: String) {
        val suffix = walletHashSuffix(walletAddress)
        val editor = prefs.edit()
        for (base in ALL_PREF_BASES) editor.remove(scopedKey(base, suffix))
        editor.apply()
        if (walletAddress == currentWalletAddress) {
            _account.value = null
            _autoBackupEnabled.value = false
            _lastBackupMs.value = null
            _lastAutoSyncMs.value = null
            _contactsSyncError.value = null
            _needsReconnect.value = false
            portfolioFileCache.clear()
        }
    }

    private fun loadAccount(): NextcloudAccount? {
        val server = scopedKey(PREF_SERVER)?.let { prefs.getString(it, null) } ?: return null
        val username = scopedKey(PREF_USERNAME)?.let { prefs.getString(it, null) } ?: return null
        val appPassword = scopedKey(PREF_APP_PASSWORD)?.let { prefs.getString(it, null) } ?: return null
        return NextcloudAccount(
            server = server,
            username = username,
            appPassword = appPassword,
            backupFolder = scopedKey(PREF_BACKUP_FOLDER)?.let { prefs.getString(it, null) },
        )
    }

    /** Saves [account] as [walletAddress]'s login - the wallet an operation snapshotted at its
     *  start, never whichever is active when the server answers. Shown only while it is active. */
    private fun persistAccount(account: NextcloudAccount, walletAddress: String) {
        val suffix = walletHashSuffix(walletAddress)
        prefs.edit()
            .putString(scopedKey(PREF_SERVER, suffix), account.server)
            .putString(scopedKey(PREF_USERNAME, suffix), account.username)
            .putString(scopedKey(PREF_APP_PASSWORD, suffix), account.appPassword)
            .apply {
                if (account.backupFolder != null) putString(scopedKey(PREF_BACKUP_FOLDER, suffix), account.backupFolder)
                else remove(scopedKey(PREF_BACKUP_FOLDER, suffix))
            }
            .apply()
        if (currentWalletAddress == walletAddress) _account.value = account
    }

    /** The login stored under [suffix], if any. */
    private fun storedAccount(suffix: String): NextcloudAccount? {
        val server = prefs.getString(scopedKey(PREF_SERVER, suffix), null) ?: return null
        val username = prefs.getString(scopedKey(PREF_USERNAME, suffix), null) ?: return null
        val appPassword = prefs.getString(scopedKey(PREF_APP_PASSWORD, suffix), null) ?: return null
        return NextcloudAccount(server, username, appPassword, prefs.getString(scopedKey(PREF_BACKUP_FOLDER, suffix), null))
    }

    /** Another account stored on this device (any suffix but [excludingSuffixes]) signs in with
     *  [account]'s server, user and app password: revoking it would sign that one out too. */
    private fun isAppPasswordUsedElsewhere(account: NextcloudAccount, excludingSuffixes: Set<String>): Boolean {
        val prefix = "${PREF_APP_PASSWORD}_"
        return prefs.all.keys.asSequence()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .filter { it !in excludingSuffixes }
            .mapNotNull { storedAccount(it) }
            .any {
                it.server.trimEnd('/').equals(account.server.trimEnd('/'), ignoreCase = true) &&
                    it.username == account.username && it.appPassword == account.appPassword
            }
    }

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    /**
     * Verifies the credentials against the OCS user endpoint (the cheapest authenticated call),
     * then persists them for the wallet active when it started, which it returns (and throws,
     * saving nothing, if another account became active meanwhile). Throws with a user-facing
     * message — a 401 says exactly what's wrong. The merge a fresh connection runs once by itself
     * is [NextcloudSyncService.connectAsync]'s.
     */
    suspend fun connect(serverInput: String, username: String, appPassword: String): String {
        if (isOffForTestnet) throw NextcloudException(context.getString(com.kachat.app.R.string.nextcloud_off_on_testnet))
        // The login is saved to the account Connect was tapped in, never one switched to meanwhile.
        val walletAtStart = currentWalletAddress
            ?: throw NextcloudException("Sign in to an account before connecting Nextcloud.")
        if (serverInput.trim().lowercase().startsWith("http://")) {
            throw NextcloudException("Use https. Unencrypted connections are not supported.")
        }
        val server = normalizeServer(serverInput)
            ?: throw NextcloudException("That doesn't look like a valid server URL.")
        val user = username.trim()
        val password = appPassword.trim()
        if (user.isEmpty() || password.isEmpty()) throw NextcloudException(BAD_CREDENTIALS)
        val candidate = NextcloudAccount(server = server, username = user, appPassword = password)

        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$server/ocs/v2.php/cloud/user?format=json")
                .header("Authorization", basicAuth(candidate))
                .header("OCS-APIRequest", "true")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw NextcloudRejectedLoginException()
                if (!response.isSuccessful) throw NextcloudHttpException(response.code)
                val body = response.body?.string() ?: throw NextcloudException(MALFORMED)
                // ocs.data existing at all is the success signal; its contents aren't needed.
                runCatching { JSONObject(body).getJSONObject("ocs").getJSONObject("data") }.getOrNull()
                    ?: throw NextcloudException(MALFORMED)
            }
        }
        if (currentWalletAddress != walletAtStart) {
            throw NextcloudException("The account changed while connecting. Nothing was saved; connect again from this account.")
        }
        persistAccount(candidate, walletAtStart)
        _needsReconnect.value = false
        // A connection is not a request to start uploading: Automatic Sync stays as it was
        // stored (off unless this wallet turned it on before).
        _autoBackupEnabled.value = prefs.getBoolean(walletKey(PREF_WALLET_AUTO_BACKUP, walletAtStart), false)
        _lastAutoSyncMs.value = prefs.getLong(walletKey(PREF_WALLET_LAST_AUTO_SYNC_MS, walletAtStart), 0L).takeIf { it > 0 }
        return walletAtStart
    }

    /**
     * Forgets this wallet's Nextcloud login and settings. The app password the user made for
     * KaPay is revoked on the server first, while the credentials still exist to authenticate the
     * request - best effort, the local copy goes whatever the server says (iOS `disconnect`).
     * The files themselves stay on the server.
     */
    fun disconnect() {
        _account.value?.let { account ->
            // Not while another account on this device still signs in with the same app password.
            val shared = isAppPasswordUsedElsewhere(account, setOfNotNull(currentSuffix))
            if (shared) Log.i(TAG, "Not revoking the app password: another account here uses it")
            else serviceScope.launch(Dispatchers.IO) { revokeAppPassword(account) }
        }
        val editor = prefs.edit()
        // Only the active wallet's entries — other accounts' logins stay untouched.
        currentSuffix?.let { suffix ->
            for (base in ALL_PREF_BASES) editor.remove(scopedKey(base, suffix))
        }
        // Belt and braces: if a legacy global entry somehow still exists, remove it too so
        // disconnect can never appear to "come back" via migration.
        for (base in ALL_PREF_BASES) editor.remove(base)
        editor.apply()
        portfolioFileCache.clear()
        _needsReconnect.value = false
        _account.value = null
        _autoBackupEnabled.value = false
        _lastBackupMs.value = null
        _lastAutoSyncMs.value = null
        _contactsSyncError.value = null
    }

    /**
     * Removing an account: the app password its stored login holds, revoked while the login still
     * exists to authenticate with, signed in or not (iOS `revokeStoredAppPassword`). [removing] is
     * every address of the account going (both networks'); another account here that still signs
     * in with the same server, user and app password keeps it. Best effort.
     */
    suspend fun revokeStoredAppPassword(walletAddress: String, removing: Collection<String> = listOf(walletAddress)) {
        val account = storedAccount(walletHashSuffix(walletAddress)) ?: return
        if (isAppPasswordUsedElsewhere(account, removing.map { walletHashSuffix(it) }.toSet() + walletHashSuffix(walletAddress))) {
            Log.i(TAG, "Not revoking the app password: another account here uses it")
            return
        }
        withContext(Dispatchers.IO) { revokeAppPassword(account) }
    }

    /** Revokes [account]'s app password on its server (iOS `revokeAppPassword`), so a
     *  disconnected or deleted wallet leaves nothing that can still log in. Best effort. */
    private fun revokeAppPassword(account: NextcloudAccount) {
        try {
            val request = Request.Builder()
                .url("${account.server.trimEnd('/')}/ocs/v2.php/core/apppassword")
                .delete()
                .header("Authorization", basicAuth(account))
                .header("OCS-APIRequest", "true")
                .build()
            // best effort, and an account removal waits on it (iOS 2da5f4b)
            client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                Log.i(TAG, "App password revoke answered HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "App password revoke failed: ${e.message}")
        }
    }

    /** A 401: automatic work stops until the next launch or connection (iOS `noteRejectedLogin`). */
    fun markNeedsReconnect() {
        _needsReconnect.value = true
    }

    // -------------------------------------------------------------------------
    // Backup folder
    // -------------------------------------------------------------------------

    /** Persists the backup folder ("" = the files root, null = the default "KaChat" folder). */
    fun setBackupFolder(path: String?, forWallet: String? = null) {
        val wallet = currentWalletAddress ?: return
        // a folder found for one account is never saved to another switched to meanwhile
        if (forWallet != null && forWallet != wallet) return
        val current = storedAccount(walletHashSuffix(wallet)) ?: return
        persistAccount(current.copy(backupFolder = path?.trim()), wallet)
        // A different file may be there, and this one may not be; nor may the last error.
        setLastKnownEtag(null, wallet)
        setContactsSyncError(null, wallet)
    }

    /**
     * Finds the folder this account already keeps its contacts file in, so a fresh connection
     * doesn't start a second one beside it. Prefers a folder that actually holds the file; one
     * merely NAMED KaChat is the fallback. Breadth-first and bounded (iOS 953b255).
     *
     * Only [walletAddress]'s own contacts file counts: its envelope carries this account's
     * `walletHint` (or, with no hint, opens with this account's key). A folder holding another
     * account's file - a second seed on the same Nextcloud login - is passed over, and not taken
     * as the named fallback either, so this account never adopts a file it can't sync.
     */
    suspend fun discoverExistingBackupFolder(walletAddress: String): String? {
        val account = storedAccount(walletHashSuffix(walletAddress)) ?: return null
        val hint = BackupCrypto.walletHint(walletAddress)
        val queue = ArrayDeque<String>().apply { add("") }
        var visited = 0
        var namedCandidate: String? = null

        while (queue.isNotEmpty() && visited < FOLDER_DISCOVERY_MAX_FOLDERS) {
            val path = queue.removeFirst()
            visited++
            val entries = try {
                listFolder(account, path)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }

            // A folder holding this account's file IS the answer - stop looking.
            val contactsFile = entries.firstOrNull { !it.isDirectory && it.name == BACKUP_FILE_NAME }
            val holdsOthers = contactsFile != null && !isOwnContactsFile(account, contactsFile, walletAddress, hint)
            if (contactsFile != null && !holdsOthers) return path
            for (entry in entries.filter { it.isDirectory }) {
                if (namedCandidate == null && entry.name.equals(BACKUP_FOLDER_NAME, ignoreCase = true) &&
                    !folderHoldsOthersContacts(account, entry.path, walletAddress, hint)
                ) {
                    namedCandidate = entry.path
                }
                if (entry.path.split("/").count { it.isNotEmpty() } < FOLDER_DISCOVERY_MAX_DEPTH) {
                    queue.add(entry.path)
                }
            }
        }
        return namedCandidate
    }

    /** [folder] holds a contacts file that isn't [walletAddress]'s. */
    private suspend fun folderHoldsOthersContacts(account: NextcloudAccount, folder: String, walletAddress: String, hint: String): Boolean {
        val file = try {
            listFolder(account, folder).firstOrNull { !it.isDirectory && it.name == BACKUP_FILE_NAME }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return false
        return !isOwnContactsFile(account, file, walletAddress, hint)
    }

    /** A contacts file is [walletAddress]'s when its envelope carries this account's `walletHint`,
     *  or carries none and opens with this account's key. Anything that can't be read isn't. */
    private suspend fun isOwnContactsFile(account: NextcloudAccount, file: NextcloudFile, walletAddress: String, hint: String): Boolean {
        val text = try {
            String(downloadFile(account, file.path, BACKUP_MAX_BYTES), Charsets.UTF_8)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        if (!BackupCrypto.isEnvelope(text)) return false
        val fileHint = BackupCrypto.envelopeWalletHint(text)
        if (fileHint != null) return fileHint.equals(hint, ignoreCase = true)
        val key = withContext(Dispatchers.IO) { portfolioKeyFor(walletAddress) } ?: return false
        return withContext(Dispatchers.IO) { runCatching { BackupCrypto.decrypt(text, key) }.isSuccess }
    }

    // -------------------------------------------------------------------------
    // Automatic Sync settings (per wallet; NextcloudSyncService acts on them)
    // -------------------------------------------------------------------------

    /** Stores the Automatic Sync toggle. [NextcloudSyncService.setAutoBackupEnabled] is the
     *  setter the screen uses: it also schedules or cancels the uploads. */
    fun setAutoBackupEnabled(enabled: Boolean) {
        val key = scopedKey(PREF_WALLET_AUTO_BACKUP) ?: return
        prefs.edit().putBoolean(key, enabled).apply()
        _autoBackupEnabled.value = enabled
    }

    /** Whether the active wallet owes the server a Portfolio upload. */
    val isBackupPending: Boolean
        get() = scopedKey(PREF_WALLET_PENDING_BACKUP)?.let { prefs.getBoolean(it, false) } ?: false

    fun setBackupPending(pending: Boolean) {
        val key = scopedKey(PREF_WALLET_PENDING_BACKUP) ?: return
        prefs.edit().putBoolean(key, pending).apply()
    }

    /** A backup finished for the active wallet: it is stamped now, and the login clearly works
     *  (iOS `stampBackup`). */
    fun stampBackup() {
        val now = System.currentTimeMillis()
        _needsReconnect.value = false
        scopedKey(PREF_WALLET_LAST_BACKUP_MS)?.let { prefs.edit().putLong(it, now).apply() }
        _lastBackupMs.value = now
    }

    /** A snapshotted wallet's key for [base] (never the current one - the wallet may have switched
     *  mid-flight). */
    private fun walletKey(base: String, walletAddress: String) = scopedKey(base, walletHashSuffix(walletAddress))

    /** [walletAddress]'s contacts are owed an automatic upload. */
    fun isContactsDirty(walletAddress: String): Boolean =
        prefs.getBoolean(walletKey(PREF_WALLET_PENDING_SYNC, walletAddress), false)

    fun setContactsDirty(dirty: Boolean, walletAddress: String) {
        prefs.edit().putBoolean(walletKey(PREF_WALLET_PENDING_SYNC, walletAddress), dirty).apply()
    }

    /** An automatic contacts sync finished for [walletAddress]. */
    fun recordAutoSync(walletAddress: String) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(walletKey(PREF_WALLET_LAST_AUTO_SYNC_MS, walletAddress), now).apply()
        if (currentWalletAddress == walletAddress) _lastAutoSyncMs.value = now
    }

    /** The ETag of the last contacts file [walletAddress] merged or wrote. Persisted per wallet, so
     *  a change that lands while the app is in the background is caught by the first poll after it
     *  comes back, and a device never downloads its own write. */
    fun lastKnownEtag(walletAddress: String): String? =
        prefs.getString(walletKey(PREF_WALLET_LAST_ETAG, walletAddress), null)

    fun setLastKnownEtag(etag: String?, walletAddress: String) {
        val editor = prefs.edit()
        val key = walletKey(PREF_WALLET_LAST_ETAG, walletAddress)
        if (etag != null) editor.putString(key, etag) else editor.remove(key)
        editor.apply()
    }

    /** Records why [walletAddress]'s contacts failed to sync (null: the last sync went through). */
    fun setContactsSyncError(message: String?, walletAddress: String) {
        val key = walletKey(PREF_WALLET_CONTACTS_SYNC_ERROR, walletAddress)
        val editor = prefs.edit()
        if (message != null) editor.putString(key, message) else editor.remove(key)
        editor.apply()
        if (currentWalletAddress == walletAddress) _contactsSyncError.value = message
    }

    /** The active wallet's earlier plain contacts files are in (true while signed out). */
    val plainContactsMerged: Boolean
        get() = scopedKey(PREF_WALLET_PLAIN_CONTACTS_MERGED)?.let { prefs.getBoolean(it, false) } ?: true

    fun markPlainContactsMerged(walletAddress: String) {
        prefs.edit().putBoolean(walletKey(PREF_WALLET_PLAIN_CONTACTS_MERGED, walletAddress), true).apply()
    }

    /** [walletAddress]'s earlier single Portfolio file is in. */
    fun isSinglePortfolioMerged(walletAddress: String): Boolean =
        prefs.getBoolean(walletKey(PREF_WALLET_SINGLE_PORTFOLIO_MERGED, walletAddress), false)

    fun markSinglePortfolioMerged(walletAddress: String) {
        prefs.edit().putBoolean(walletKey(PREF_WALLET_SINGLE_PORTFOLIO_MERGED, walletAddress), true).apply()
    }

    /** [walletAddress]'s Portfolio files have all been written encrypted by a full backup, so a plain
     *  one isn't read any more (iOS `plainPortfoliosRetiredKey`). */
    fun isPlainPortfoliosRetired(walletAddress: String): Boolean =
        prefs.getBoolean(walletKey(PREF_WALLET_PLAIN_PORTFOLIOS_RETIRED, walletAddress), false)

    fun markPlainPortfoliosRetired(walletAddress: String) {
        prefs.edit().putBoolean(walletKey(PREF_WALLET_PLAIN_PORTFOLIOS_RETIRED, walletAddress), true).apply()
    }

    private fun loginId(account: NextcloudAccount) = "${account.server.trimEnd('/').lowercase()}|${account.username.lowercase()}"

    /** An account on this device has already taken [account]'s login's ownerless single Portfolio
     *  file (iOS `singlePortfolioLoginsKey`): a second account on the same login doesn't. */
    fun isSinglePortfolioLoginClaimed(account: NextcloudAccount): Boolean =
        loginId(account) in prefs.getStringSet(PREF_SINGLE_PORTFOLIO_LOGINS, null).orEmpty()

    fun claimSinglePortfolioLogin(account: NextcloudAccount) {
        val logins = prefs.getStringSet(PREF_SINGLE_PORTFOLIO_LOGINS, null).orEmpty() + loginId(account)
        prefs.edit().putStringSet(PREF_SINGLE_PORTFOLIO_LOGINS, logins).apply()
    }

    // -------------------------------------------------------------------------
    // The contacts file (WebDAV PUT/GET of one encrypted file; iOS 953b255)
    // -------------------------------------------------------------------------

    /**
     * Uploads the contacts to `<backup folder>/kachat-contacts.json`, creating the folder chain
     * first. Overwrites in place: every caller goes through [NextcloudSyncService.runContactsBackup]
     * and the body is a merge, never a replacement. Returns the uploaded file's ETag when the server
     * sent one (`OC-ETag` first - Nextcloud's canonical header - then `ETag`), normalized.
     */
    suspend fun uploadBackup(data: ByteArray): String? = withContext(Dispatchers.IO) {
        val account = requireAccount()
        val folder = backupFolderPath
        ensureFolder(account, folder)
        val put = Request.Builder()
            .url(davUrl(account, "$folder/$BACKUP_FILE_NAME"))
            .put(data.toRequestBody("application/json".toMediaType()))
            .header("Authorization", basicAuth(account))
            .build()
        var attempt = 0
        while (true) {
            client.newCall(put).execute().use { response ->
                if (response.code == 401) throw NextcloudRejectedLoginException()
                if (response.code != 423) {
                    if (!response.isSuccessful) throw NextcloudHttpException(response.code)
                    val raw = response.header("OC-ETag") ?: response.header("ETag")
                    return@withContext raw?.let { normalizeEtag(it) }?.takeIf { it.isNotEmpty() }
                }
            }
            // Nextcloud's file lock: another device is reading or writing the file, and a lock held
            // by a live request clears the moment that request ends. Wait it out a few times before
            // calling it stale.
            if (attempt >= LOCK_RETRY_DELAYS_MS.size) {
                throw NextcloudException(
                    "Nextcloud has the backup file locked (HTTP 423): another device or sync is reading " +
                        "or writing it right now. Try again in a minute. If it keeps happening, the lock is " +
                        "stale on the server - clear it with occ (maintenance mode on, empty the file locks, off)."
                )
            }
            Log.i(TAG, "Backup file locked (HTTP 423), retrying in ${LOCK_RETRY_DELAYS_MS[attempt]}ms")
            kotlinx.coroutines.delay(LOCK_RETRY_DELAYS_MS[attempt])
            attempt++
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    /**
     * The contacts file's bytes, or null when there is no file yet (a 404 on it or its folder). Every
     * other failure throws before any caller could write: a 2xx HTML body is a reverse-proxy, login
     * or maintenance page standing in for the server, and a download short of the advertised length
     * is a transfer problem. A short file must never read as a damaged one and be overwritten.
     */
    suspend fun downloadBackup(): String? = withContext(Dispatchers.IO) {
        val account = requireAccount()
        val request = Request.Builder()
            .url(davUrl(account, "$backupFolderPath/$BACKUP_FILE_NAME"))
            .header("Authorization", basicAuth(account))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw NextcloudRejectedLoginException()
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw NextcloudHttpException(response.code)
            if ("html" in response.header("Content-Type").orEmpty().lowercase()) throw NextcloudException(MALFORMED)
            val body = response.body ?: throw NextcloudException(MALFORMED)
            val expected = body.contentLength().takeIf { it > 0 }
            // Read whole, so bounded like every other backup file (iOS `maxBytes: 50_000_000`).
            val tooLarge = "The backup file on Nextcloud is too large to read."
            if (expected != null && expected > BACKUP_MAX_BYTES) throw NextcloudException(tooLarge)
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > BACKUP_MAX_BYTES) throw NextcloudException(tooLarge)
                }
            }
            if (out.size() == 0) throw NextcloudHttpException(response.code)
            // A connection closed gracefully mid-body just ends the stream, so short of the
            // advertised length has to be caught here.
            if (expected != null && out.size().toLong() < expected) {
                throw NextcloudException(
                    "The backup download stopped early (${out.size()} of $expected bytes). " +
                        "Nothing on the server was changed, so trying again is safe."
                )
            }
            out.toString("UTF-8")
        }
    }

    /** The contacts file's current ETag via a Depth-0 PROPFIND requesting only `getetag`. null = no
     *  file yet (404); throws on any other failure, so the watcher can back off. */
    suspend fun fetchBackupEtag(): String? = withContext(Dispatchers.IO) {
        val account = requireAccount()
        val body = """
            <?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:getetag/></d:prop></d:propfind>
        """.trimIndent().toRequestBody("application/xml".toMediaType())
        val request = Request.Builder()
            .url(davUrl(account, "$backupFolderPath/$BACKUP_FILE_NAME"))
            .method("PROPFIND", body)
            .header("Depth", "0")
            .header("Authorization", basicAuth(account))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw NextcloudRejectedLoginException()
            if (response.code == 404) return@withContext null
            if (response.code != 207) throw NextcloudHttpException(response.code)
            val xml = response.body?.string() ?: throw NextcloudException(MALFORMED)
            parseEtagFromMultistatus(xml) ?: throw NextcloudException(MALFORMED)
        }
    }

    /** The contacts file's server-side metadata (null = no file yet, or unreadable). */
    suspend fun contactsFileInfo(): NextcloudFile? {
        val listing = try {
            listFolder(backupFolderPath)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return listing.firstOrNull { it.name == BACKUP_FILE_NAME && !it.isDirectory }
    }

    // -------------------------------------------------------------------------
    // The Portfolio files (iOS 953b255)
    // -------------------------------------------------------------------------

    /** One account's Portfolio file found in the backup folder. [encrypted] false: a plain file from
     *  an earlier build, rewritten encrypted at the next backup even when nothing in it changed. */
    data class ServerPortfolioFile(val file: NextcloudFile, val backup: PortfolioBackupFile.Decoded, val encrypted: Boolean = true)

    /** The active account's envelope key for its Portfolio files, the contacts' key (set by
     *  [NextcloudSyncService]); null when it isn't [wallet]'s or can't be had. */
    @Volatile
    var portfolioKeyFor: (wallet: String) -> ByteArray? = { null }

    /** What the backup folder holds, as far as the Portfolio is concerned. */
    data class ServerPortfolios(
        val own: List<ServerPortfolioFile> = emptyList(),
        /** Lowercased names of everything else there: another account's portfolio, or any other
         *  file. A portfolio's file goes beside these, never over them. */
        val taken: Set<String> = emptySet(),
    )

    /** Reads the backup folder. Any file that can't be downloaded throws, so nothing is written or
     *  deleted on a partial view of what's there. Without the account's key nothing is read. */
    suspend fun readServerPortfolios(target: BackupTarget): ServerPortfolios =
        readServerPortfolios(target.wallet, target.account, target.folder)

    /** The active wallet with its own stored login and backup folder, read together: what a backup
     *  snapshots at its start and uses for every read, PUT and DELETE, so a wallet switch midway
     *  can never send one account's files to another's Nextcloud. Null while none is connected. */
    data class BackupTarget(val wallet: String, val account: NextcloudAccount, val folder: String)

    fun backupTarget(): BackupTarget? {
        val wallet = currentWalletAddress ?: return null
        // the login stored for this very wallet, not the in-memory one a switch may be replacing
        val account = storedAccount(walletHashSuffix(wallet)) ?: return null
        return BackupTarget(wallet, account, account.backupFolder ?: BACKUP_FOLDER_NAME)
    }

    /**
     * What each file is, as KaPay iPhone reads them (its per-portfolio encryption, 2026-11-06):
     * - an envelope that opens with this account's key and holds this account's `walletAddress` is
     *   this account's;
     * - an envelope with this account's `walletHint` that won't open is this account's damaged file:
     *   its name may be used again and written over;
     * - any other envelope, or a file that isn't a portfolio, is someone else's: left alone, and
     *   numbered around;
     * - a plain file (no envelope) is read as before, and if it's this account's it's rewritten
     *   encrypted at the next backup; once a full backup has run, or any of this account's files
     *   is encrypted, plain files are someone else's ([isPlainPortfoliosRetired]), since anyone
     *   can write one.
     * Off the main thread whoever calls it: the key derivation, decryption and JSON parsing are
     * real work (Settings' backup info calls this from a composable's scope).
     */
    private suspend fun readServerPortfolios(walletAtStart: String, account: NextcloudAccount, folder: String): ServerPortfolios = withContext(Dispatchers.IO) {
        val key = portfolioKeyFor(walletAtStart) ?: throw NextcloudException(BackupCrypto.KEY_UNAVAILABLE_MESSAGE)
        val hint = BackupCrypto.walletHint(walletAtStart)
        val listing = try {
            listFolder(account, folder)
        } catch (e: NextcloudHttpException) {
            if (e.code != 404) throw e
            return@withContext ServerPortfolios()
        }
        val own = mutableListOf<ServerPortfolioFile>()
        val taken = mutableSetOf<String>()
        // A plain file carries no proof of who wrote it (the owner inside is a public address), so
        // plain files count only until this account's own have been encrypted (iOS 1652437).
        val plainRetired = isPlainPortfoliosRetired(walletAtStart)
        for (file in listing) {
            if (file.isDirectory) continue
            var read: CachedPortfolioFile? = null
            if (isPortfolioFileCandidate(file.name, size = file.size)) {
                val cacheKey = "$walletAtStart\u0000${file.path}"
                val cached = portfolioFileCache[cacheKey]
                read = if (file.modifiedMs != null && cached != null && cached.modifiedMs == file.modifiedMs && cached.size == file.size) {
                    cached
                } else {
                    readPortfolioFile(downloadFile(account, file.path, BACKUP_MAX_BYTES), key, hint, file)
                        .also { portfolioFileCache[cacheKey] = it }
                }
            }
            val backup = read?.backup
            if (backup != null && backup.type == PortfolioBackupFile.KIND && backup.version <= PortfolioBackupFile.CURRENT_VERSION &&
                backup.walletAddress?.lowercase() == walletAtStart.lowercase() &&
                !(read?.encrypted != true && plainRetired)
            ) {
                own += ServerPortfolioFile(file, backup, encrypted = read?.encrypted ?: false)
            } else if (read?.damaged != true) {
                taken += file.name.lowercase()
            }
        }
        // Once any of this account's files is encrypted, a plain one proves nothing (its owner is a
        // public address): it may have been planted, so it is left alone like anyone else's (iOS 2da5f4b).
        if (own.any { it.encrypted }) {
            own.filterNot { it.encrypted }.forEach { taken += it.file.name.lowercase() }
            own.removeAll { !it.encrypted }
        }
        ServerPortfolios(own, taken)
    }

    /** One downloaded file, opened with [key] if it's an envelope. */
    private fun readPortfolioFile(data: ByteArray, key: ByteArray, hint: String, file: NextcloudFile): CachedPortfolioFile {
        val text = String(data, Charsets.UTF_8)
        if (BackupCrypto.envelopeVersion(text) == null) {
            val plain = runCatching { PortfolioBackupFile.decode(data) }.getOrNull()
            return CachedPortfolioFile(file.modifiedMs, file.size, plain, encrypted = false)
        }
        val opened = if (BackupCrypto.isEnvelope(text)) runCatching { BackupCrypto.decrypt(text, key) }.getOrNull() else null
        if (opened == null) {
            // this account's file that won't open, or someone else's
            val ours = BackupCrypto.isEnvelope(text) && BackupCrypto.envelopeWalletHint(text)?.equals(hint, ignoreCase = true) == true
            return CachedPortfolioFile(file.modifiedMs, file.size, null, encrypted = true, damaged = ours)
        }
        val backup = runCatching { PortfolioBackupFile.decode(opened.toByteArray(Charsets.UTF_8)) }.getOrNull()
        return CachedPortfolioFile(file.modifiedMs, file.size, backup, encrypted = true)
    }

    // -------------------------------------------------------------------------
    // The backup files
    // -------------------------------------------------------------------------

    /** The backup files' server-side metadata (a null file = not backed up yet, or unreadable). */
    data class BackupInfo(
        val contacts: NextcloudFile? = null,
        val portfolio: NextcloudFile? = null,
    )

    /** What the backup folder holds of the backup (iOS `fetchBackupInfo`). A missing folder lists as
     *  a 404, which also just means "no backup yet". */
    suspend fun fetchWalletBackupInfo(): BackupInfo = withContext(Dispatchers.IO) {
        val contacts = contactsFileInfo()
        val target = backupTarget() ?: return@withContext BackupInfo(contacts = contacts)
        val server = try {
            readServerPortfolios(target)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext BackupInfo(contacts = contacts)
        }
        // the newest of the portfolios' files
        BackupInfo(contacts = contacts, portfolio = server.own.map { it.file }.maxByOrNull { it.modifiedMs ?: Long.MIN_VALUE })
    }

    /** A plain file in the KaChat folder under [name], else under a name it had before ([legacy],
     *  newest first); null when none is there (iOS `downloadBackupFile`). Any other failure throws. */
    suspend fun downloadBackupFile(name: String, legacy: List<String>, account: NextcloudAccount = requireAccount()): ByteArray? {
        for (candidate in listOf(name) + legacy) {
            try {
                return downloadFile(account, "$BACKUP_FOLDER_NAME/$candidate", BACKUP_MAX_BYTES)
            } catch (e: NextcloudHttpException) {
                if (e.code != 404) throw e
            }
        }
        return null
    }

    /** Deletes a file from the user's Nextcloud. 404: nothing there, which is the state asked for. */
    suspend fun deleteFile(path: String, account: NextcloudAccount = requireAccount()): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(davUrl(account, path))
            .delete()
            .header("Authorization", basicAuth(account))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw NextcloudRejectedLoginException()
            if (!response.isSuccessful && response.code != 404) throw NextcloudHttpException(response.code)
        }
    }

    // -------------------------------------------------------------------------
    // WebDAV plumbing
    // -------------------------------------------------------------------------

    private fun basicAuth(account: NextcloudAccount): String =
        "Basic " + android.util.Base64.encodeToString(
            "${account.username}:${account.appPassword}".toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )

    private fun requireAccount(): NextcloudAccount =
        _account.value ?: throw NextcloudException(BAD_CREDENTIALS)

    /** WebDAV URL for a path relative to the user's files root, each segment percent-encoded. */
    private fun davUrl(account: NextcloudAccount, relativePath: String): HttpUrl {
        val base = account.server.toHttpUrlOrNull()
            ?: throw NextcloudException("That doesn't look like a valid server URL.")
        val builder = base.newBuilder()
        for (part in "remote.php/dav/files/${account.username}/$relativePath".split("/")) {
            if (part.isNotEmpty()) builder.addPathSegment(part)
        }
        return builder.build()
    }

    /** Makes [folder], level by level: MKCOL is not recursive, and 405 means the level already
     *  exists. The files root ("") needs nothing. */
    private fun ensureFolder(account: NextcloudAccount, folder: String) {
        var level = ""
        for (part in folder.split("/").filter { it.isNotEmpty() }) {
            level = if (level.isEmpty()) part else "$level/$part"
            val mkcol = Request.Builder().url(davUrl(account, level)).method("MKCOL", null)
                .header("Authorization", basicAuth(account)).build()
            client.newCall(mkcol).execute().use { response ->
                if (response.code == 401) throw NextcloudRejectedLoginException()
                if (!response.isSuccessful && response.code != 405) throw NextcloudHttpException(response.code)
            }
        }
    }

    /**
     * Uploads [bytes] as [filename] into the KaChat folder, or [folder] ("" is the files root),
     * creating it if it isn't there yet, and returns the stored path. A file of the same name is
     * replaced. [keepSpaces] keeps a name the user gave (a portfolio's, "KaChat Address Book")
     * readable in Nextcloud instead of turning its spaces into underscores (iOS 87b2a0b).
     */
    suspend fun uploadToKaChatFolder(
        bytes: ByteArray,
        filename: String,
        contentType: String,
        keepSpaces: Boolean = false,
        folder: String? = null,
        /** A backup's snapshotted login ([BackupTarget]); the current one otherwise. */
        account: NextcloudAccount? = null,
    ): String = withContext(Dispatchers.IO) {
        val account = account ?: requireAccount()
        val target = folder ?: BACKUP_FOLDER_NAME
        ensureFolder(account, target)
        val storedName = sanitizedFilename(filename, keepSpaces)
        val path = if (target.isEmpty()) storedName else "$target/$storedName"
        val mediaType = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val put = Request.Builder().url(davUrl(account, path)).put(bytes.toRequestBody(mediaType))
            .header("Authorization", basicAuth(account)).build()
        client.newCall(put).execute().use { response ->
            if (response.code == 401) throw NextcloudRejectedLoginException()
            if (!response.isSuccessful) throw NextcloudHttpException(response.code)
        }
        path
    }

    /** A file's bytes, for importing it. Every failure throws so the caller can say what went
     *  wrong ([NextcloudHttpException] carries the status); capped at [maxBytes]. */
    suspend fun downloadFile(relativePath: String, maxBytes: Long = 10_000_000L): ByteArray =
        downloadFile(requireAccount(), relativePath, maxBytes)

    private suspend fun downloadFile(account: NextcloudAccount, relativePath: String, maxBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        val get = Request.Builder().url(davUrl(account, relativePath)).header("Authorization", basicAuth(account)).build()
        client.newCall(get).execute().use { response ->
            if (response.code == 401) throw NextcloudRejectedLoginException()
            if (!response.isSuccessful) throw NextcloudHttpException(response.code)
            val body = response.body ?: throw NextcloudException(MALFORMED)
            if (body.contentLength() > maxBytes) throw NextcloudException("That file is too large to import.")
            val bytes = body.bytes()
            if (bytes.size > maxBytes) throw NextcloudException("That file is too large to import.")
            bytes
        }
    }

    /** Lists one folder (non-recursive) of the connected account's files via a Depth-1 PROPFIND
     *  - the import file pickers' data source. */
    suspend fun listFolder(relativePath: String = ""): List<NextcloudFile> = listFolder(requireAccount(), relativePath)

    private suspend fun listFolder(account: NextcloudAccount, relativePath: String): List<NextcloudFile> = withContext(Dispatchers.IO) {
        val davBasePath = "/remote.php/dav/files/${account.username}"
        // Normalized form of the listed folder, for the parser's self-entry exclusion below.
        val listedPath = relativePath.split("/").filter { it.isNotEmpty() }.joinToString("/")

        val body = """
            <?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:displayname/><d:resourcetype/><d:getcontenttype/><d:getcontentlength/><d:getlastmodified/></d:prop>
            </d:propfind>
        """.trimIndent().toRequestBody("application/xml".toMediaType())

        val request = Request.Builder()
            .url(davUrl(account, relativePath))
            .method("PROPFIND", body)
            .header("Depth", "1")
            .header("Authorization", basicAuth(account))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw NextcloudRejectedLoginException()
            if (response.code != 207) throw NextcloudHttpException(response.code)
            val xml = response.body?.string() ?: throw NextcloudException(MALFORMED)
            parseMultistatus(xml, davBasePath, listedPath)
        }
    }

    /**
     * Minimal WebDAV `multistatus` parser for folder listings. Namespace-aware, matching on local
     * names only (servers vary between `d:` and `D:` prefixes).
     *
     * A Depth-1 PROPFIND's multistatus includes the listed folder ITSELF as one of its responses —
     * without excluding it, every folder appears to contain itself (an infinite "Photos inside
     * Photos" loop when browsing). The exclusion must compare full relative paths, not just check
     * "is this the root": listing "a/b" includes an entry whose path is "a/b" at any depth.
     */
    private fun parseMultistatus(xml: String, davBasePath: String, listedPath: String): List<NextcloudFile> {
        val results = mutableListOf<NextcloudFile>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(xml.reader())
        }
        val dateFormatter = rfc1123Formatter()

        var inResponse = false
        var href = ""
        var displayName: String? = null
        var contentType: String? = null
        var contentLength: Long? = null
        var modifiedMs: Long? = null
        var isCollection = false
        var text = StringBuilder()

        fun appendCurrent() {
            val decoded = Uri.decode(href)
            val baseIndex = decoded.indexOf(davBasePath)
            if (baseIndex < 0) return
            val relative = decoded.substring(baseIndex + davBasePath.length).trim('/')
            if (relative.isEmpty() || relative == listedPath) return // the listed folder itself
            val fallbackName = relative.substringAfterLast('/')
            results.add(
                NextcloudFile(
                    path = relative,
                    name = displayName ?: fallbackName,
                    isDirectory = isCollection,
                    contentType = contentType,
                    size = contentLength,
                    modifiedMs = modifiedMs
                )
            )
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when (parser.name) {
                        "response" -> {
                            inResponse = true
                            href = ""
                            displayName = null
                            contentType = null
                            contentLength = null
                            modifiedMs = null
                            isCollection = false
                        }
                        "collection" -> isCollection = true
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val trimmed = text.toString().trim()
                    when (parser.name) {
                        "href" -> if (inResponse && href.isEmpty()) href = trimmed
                        "displayname" -> if (inResponse && displayName == null && trimmed.isNotEmpty()) displayName = trimmed
                        "getcontenttype" -> if (inResponse && trimmed.isNotEmpty()) contentType = trimmed
                        "getcontentlength" -> if (inResponse) contentLength = trimmed.toLongOrNull()
                        "getlastmodified" -> if (inResponse && trimmed.isNotEmpty()) {
                            modifiedMs = runCatching { dateFormatter.parse(trimmed)?.time }.getOrNull()
                        }
                        "response" -> {
                            inResponse = false
                            appendCurrent()
                        }
                    }
                }
            }
            event = parser.next()
        }
        return results.sortedNewestFirst()
    }

}
