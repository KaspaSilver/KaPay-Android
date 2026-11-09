package com.kachat.app.services

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.util.KaspaMessageSigner
import com.kachat.app.util.Schnorr
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers this device's FCM token with the KaChat indexer's `/v1/push` API so the server can
 * deliver native push notifications - for KaPay, "Received KAS" on the wallet's own addresses
 * (`watch_only_addresses`) and .kachat name events (`primary_address`) - while the app is
 * backgrounded or killed.
 *
 * The registration is authenticated exactly like the iOS client: a BIP-340 Schnorr signature over
 * a canonical, newline-joined preimage, in the **LegacyV1** auth shape (no group ids), matched by
 * the Rust server (`kasia-indexer`) in `build_auth_preimage`. The chat fields (contacts, aliases,
 * rooms, KaPosts) still go out, empty, so the shape never changes (iOS wallet 301ebfb).
 *
 * Idempotent and safe to call repeatedly — the server upserts by device token, and an unchanged
 * registration snapshot (see [lastRegisteredFingerprint]) short-circuits before any network I/O.
 *
 * Beyond the explicit [registerAsync]/[onTokenRefreshed] entry points, the init block observes
 * the active account, the notifications setting and Address Activity, and re-registers
 * (debounced 2s) whenever any of it changes. The same observer unregisters when notifications are
 * switched off or the last account disappears.
 *
 * [PushState.setActive] is flipped true only after a registration round-trip succeeds while
 * system notifications are deliverable, and false on failure/unregister.
 */
@OptIn(FlowPreview::class)
@Singleton
class PushRegistrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletManager: WalletManager,
    private val networkService: NetworkService,
    private val settings: AppSettingsRepository,
    private val pushState: PushState,
    /** KaChat Wallet's own addresses for "Received" pushes (watch_only_addresses). */
    private val addressActivityNotifierLazy: dagger.Lazy<AddressActivityNotifier>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.kachat.app.util.StartupSafety.logOnlyHandler("PushRegistrationManager"))
    // Serialize registrations so an account switch + a token refresh can't race.
    private val mutex = Mutex()

    /**
     * SHA-256 of the last payload the server ACCEPTED (token + wallet + watched sets). A register
     * whose snapshot hashes identically is skipped entirely — this is what lets the startup
     * trigger from WalletViewModel and the init observer's initial emission coalesce into a
     * single network round-trip. Cleared on failure and on unregister so the next trigger, even
     * with unchanged inputs, genuinely re-registers (e.g. notifications toggled off then on).
     */
    @Volatile
    private var lastRegisteredFingerprint: String? = null

    /** KaChat Wallet: already unregistered because Address Activity left nothing to watch, so a
     *  later trigger with still nothing to watch doesn't call the service again. */
    @Volatile
    private var leftWithNothingToWatch = false

    /**
     * Everything the registration payload is derived from, observed as one combined flow.
     * [notificationsEnabled] rides along so flipping the setting re-triggers [onSnapshot]
     * (where it decides register vs unregister) - it is NOT part of the fingerprint. The chat
     * inputs (contacts, rooms, groups, KaPosts) went with the chats (iOS wallet 301ebfb).
     */
    private data class Snapshot(
        val activeAddress: String?,
        val notificationsEnabled: Boolean,
        /** KaChat Wallet: Address Activity and the spending addresses' switch decide which own
         *  addresses the service watches, so a flip re-registers at once. */
        val addressActivity: Pair<Boolean, Boolean>,
    )

    init {
        scope.launch {
            combine(
                walletManager.activeAddressFlow,
                settings.notificationsEnabled,
                combine(settings.addressActivityNotificationsEnabled, settings.spendingReceiveNotifications) { a, b -> a to b },
            ) { activeAddress, notificationsEnabled, addressActivity ->
                Snapshot(activeAddress, notificationsEnabled, addressActivity)
            }
                .distinctUntilChanged()
                // Coalesce bursts (an account switch, a few switches in a row) into one
                // challenge/register round-trip.
                .debounce(2_000)
                .collect { snapshot ->
                    runCatching { onSnapshot(snapshot) }
                        .onFailure { Log.w(TAG, "push (re)registration failed: ${it.message}") }
                }
        }
    }

    private suspend fun onSnapshot(snapshot: Snapshot) {
        when {
            // Last account deleted/wiped: the signing key is already gone, so the best we can do
            // is an unsigned unregister (the same fallback iOS uses when auth can't be built).
            // The signed path for a deliberate deletion is WalletViewModel calling
            // [unregisterAsync] BEFORE the wallet is removed.
            snapshot.activeAddress == null -> unregister(signed = false)
            // Notifications switched off in Settings: mirror iOS's disablePushNotifications(),
            // which unregisters the device outright rather than letting the server keep pushing.
            !snapshot.notificationsEnabled -> unregister(signed = true)
            else -> register(null)
        }
    }

    /**
     * Fire-and-forget (re)registration. Call on wallet unlock, account switch, app foreground, or
     * when the watched set changes. No-ops when there's no wallet, notifications are off, or FCM
     * is unavailable (e.g. no google-services.json / no Play services).
     */
    fun registerAsync() {
        scope.launch {
            runCatching { register(null) }
                .onFailure { Log.w(TAG, "push registration failed: ${it.message}") }
        }
    }

    /** Called from [KaChatFirebaseMessagingService.onNewToken] when FCM rotates the token. */
    fun onTokenRefreshed(token: String) {
        scope.launch {
            runCatching { register(token) }
                .onFailure { Log.w(TAG, "push re-registration failed: ${it.message}") }
        }
    }

    /**
     * Fire-and-forget unregister that snapshots the active account's record SYNCHRONOUSLY, so
     * callers about to destroy the wallet (account deletion, logout) can invoke it first and the
     * challenge/sign round-trip still has the key even though the wallet is gone by the time the
     * coroutine runs — the same ordering iOS uses (unregister BEFORE clearing the wallet). The
     * key itself is derived from that record in the coroutine, off the main thread.
     */
    fun unregisterAsync() {
        // Only the account record is taken here (cheap); the key is derived from it on the IO
        // scope, so Log Out and Delete don't run PBKDF2 on the main thread.
        val account = walletManager.getActiveAccount()
        scope.launch {
            val material = account?.let {
                try { SigningMaterial(walletManager.identityPrivateKeyBytesFor(it), it.address.trim()) } catch (_: Exception) { null }
            }
            runCatching { unregisterInternal(material) }
                .onFailure { Log.w(TAG, "push unregister failed: ${it.message}") }
        }
    }

    private suspend fun unregister(signed: Boolean) {
        val material = if (signed) {
            try { signingMaterial() } catch (_: Exception) { null }
        } else null
        unregisterInternal(material)
    }

    private data class SigningMaterial(val privateKey: ByteArray, val walletAddress: String)

    private fun signingMaterial(): SigningMaterial =
        SigningMaterial(walletManager.getPrivateKeyBytes(), walletManager.getAddress().trim())

    private suspend fun unregisterInternal(material: SigningMaterial?) = mutex.withLock { unregisterLocked(material) }

    /** [unregisterInternal]'s work, for a caller that already holds [mutex] (Kotlin's is not reentrant). */
    private suspend fun unregisterLocked(material: SigningMaterial?) {
        // Whatever happens below, this device is no longer in remote-push mode: the pollers must
        // resume posting notifications, and the next register must not be fingerprint-skipped.
        pushState.setActive(false)
        lastRegisteredFingerprint = null

        val api = pushApiOrNull() ?: return
        val token = try { FirebaseMessaging.getInstance().token.await().trim() } catch (e: Exception) {
            pushState.recordAttempt("unregister", succeeded = false, error = "FCM token unavailable: ${e.message}", fcmTokenPresent = false)
            return
        }
        if (token.isEmpty()) return

        val auth = material?.let {
            runCatching { buildAuth(it, "DELETE", "/v1/push/unregister", token, watchedAddresses = emptyList(), primaryAddress = "") }
                // Best-effort unsigned fallback, same as iOS's unregister when auth can't be built.
                .getOrNull()
        }
        try {
            api.unregister(PushUnregisterRequest(deviceToken = token, auth = auth))
            pushState.recordAttempt("unregister", succeeded = true, error = null, fcmTokenPresent = true)
            Log.i(TAG, "push unregistered")
        } catch (e: HttpException) {
            // A signed attempt the server rejects (e.g. token bound to a wallet we no longer
            // hold the key for) still deserves the unsigned best-effort try before giving up.
            val recovered = auth != null && runCatching {
                api.unregister(PushUnregisterRequest(deviceToken = token, auth = null))
            }.isSuccess
            if (recovered) {
                pushState.recordAttempt("unregister", succeeded = true, error = null, fcmTokenPresent = true)
                Log.i(TAG, "push unregistered (unsigned fallback)")
            } else {
                pushState.recordAttempt("unregister", succeeded = false, error = describeError(e), fcmTokenPresent = true)
                Log.w(TAG, "push unregister rejected: ${describeError(e)}")
            }
        }
    }

    // MARK: - No push service on this network (testnet) - iOS df52425

    private val gson = com.google.gson.Gson()
    private val pushPrefs by lazy { context.getSharedPreferences("kachat_push_registration", Context.MODE_PRIVATE) }

    /** The push API, or null when this network has no push service (a blank URL - testnet). It
     *  used to wait for a client that a blank URL never builds. */
    private suspend fun pushApiOrNull(): PushApi? {
        if (settings.pushIndexerUrl.first().isBlank()) return null
        return networkService.pushApi.first { it != null }
    }

    /**
     * No push service on this network: the registration made on mainnet is withdrawn with one
     * unsigned DELETE to the service the device last registered with (unsigned because the signed
     * form binds the address of the network being left), and this device counts as unregistered -
     * otherwise the mainnet service kept pushing mainnet messages into the testnet account.
     */
    private suspend fun leavePushServiceOfOtherNetworkIfNeeded() {
        pushState.setActive(false)
        lastRegisteredFingerprint = null
        // Registrations made before this was recorded went to the shipped default service.
        val registeredBase = pushPrefs.getString(KEY_REGISTERED_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: com.kachat.app.repository.AppSettingsRepository.DEFAULT_PUSH_INDEXER_URL.takeIf { !pushPrefs.getBoolean(KEY_LEFT, false) }
            ?: return
        pushPrefs.edit().remove(KEY_REGISTERED_BASE_URL).putBoolean(KEY_LEFT, true).apply()
        val token = try { FirebaseMessaging.getInstance().token.await().trim() } catch (e: Exception) { return }
        if (token.isEmpty()) return
        if (sendUnsignedUnregister(registeredBase, token)) {
            Log.i(TAG, "No push service on this network; sent unregister to $registeredBase")
        }
    }

    /**
     * The push URL changed since this device last registered - most often the Testnet switch
     * taking effect at launch, since each network has its own push service (e6339ff) - and the
     * OLD service still holds this token with the old network's address, contacts, groups and
     * rooms, and keeps pushing them (audit AND-006). Port of iOS
     * `unregisterFromSupersededServiceIfNeeded`: the stored base is forgotten first (one attempt
     * per supersession - a dead old host must not be retried forever), then one unsigned DELETE
     * goes to it (unsigned: the signed form binds the address of the network being left), errors
     * ignored. Runs before every registration, ahead of the fingerprint skip.
     */
    private fun unregisterFromSupersededServiceIfNeeded(token: String, currentBase: String) {
        val oldBase = supersededBase(pushPrefs.getString(KEY_REGISTERED_BASE_URL, null), currentBase) ?: return
        pushPrefs.edit().remove(KEY_REGISTERED_BASE_URL).apply()
        if (sendUnsignedUnregister(oldBase, token)) {
            Log.i(TAG, "Sent unregister to the superseded push service $oldBase")
        }
    }

    /** One unsigned `DELETE <base>/v1/push/unregister` for [token]. False on any failure, which
     *  callers only log - both uses are best effort. */
    private fun sendUnsignedUnregister(base: String, token: String): Boolean = try {
        val body = gson.toJson(PushUnregisterRequest(deviceToken = token, auth = null))
            .toRequestBody("application/json".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(base.trim().trimEnd('/') + "/v1/push/unregister")
            .delete(body)
            .build()
        leaveClient.newCall(request).execute().close()
        true
    } catch (e: Exception) {
        Log.w(TAG, "Unregister from $base failed: ${e.message}")
        false
    }

    private val leaveClient by lazy {
        okhttp3.OkHttpClient.Builder().callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    private suspend fun register(tokenOverride: String?) = mutex.withLock {
        if (!walletManager.hasWallet()) return@withLock
        // Signed out stays unregistered, a cold start on the sign-in screen included (iOS has no
        // current wallet then); login() registers.
        if (walletManager.isLoggedOut) return@withLock
        // no account active (the active one was deleted): nothing registers until a sign-in
        if (walletManager.getActiveAccount() == null) return@withLock
        if (!settings.notificationsEnabled.first()) return@withLock
        if (settings.pushIndexerUrl.first().isBlank()) {
            withContext(Dispatchers.IO) { leavePushServiceOfOtherNetworkIfNeeded() }
            return@withLock
        }

        val api = pushApiOrNull() ?: return@withLock
        val token = try {
            (tokenOverride ?: FirebaseMessaging.getInstance().token.await()).trim()
        } catch (e: Exception) {
            // No google-services.json / no Play services / Firebase not initialized.
            pushState.recordAttempt("register", succeeded = false, error = "FCM token unavailable: ${e.message}", fcmTokenPresent = false)
            throw e
        }
        if (token.isEmpty()) {
            pushState.recordAttempt("register", succeeded = false, error = "FCM returned an empty token", fcmTokenPresent = false)
            return@withLock
        }
        withContext(Dispatchers.IO) { unregisterFromSupersededServiceIfNeeded(token, settings.pushIndexerUrl.first()) }

        val privateKey = walletManager.getPrivateKeyBytes()
        // Kaspa addresses are canonical lowercase bech32, so this already matches the server's
        // RpcAddress round-trip (normalize_wallet_address / derive_wallet_address).
        val walletAddress = walletManager.getAddress().trim()
        // KaChat Wallet has no chats, groups, public chats or KaPosts: it registers only its own
        // addresses (watch_only_addresses, "Received KAS") and its main address as
        // primary_address (.kachat name events) - iOS wallet 1806a69, PUSH_SERVICE.md. The chat
        // fields still go out, empty (KaPosts kinds at their defaults), so the registration and
        // its signed preimage keep the shape the service expects (iOS wallet 301ebfb).
        val kaPostsPubkey: String? = null
        val kaPostsKinds = KaPostsNotifyKinds()
        val kaPostsWatchAuthors = emptyList<String>()
        val watchedAddresses = emptyList<String>()
        val aliases = emptyList<String>()
        val broadcastChannels = emptyList<String>()
        val hiddenSenders = emptyMap<String, List<String>>()
        val watchedGroupIds = emptyList<String>()
        val watchOnlyAddresses = addressActivityNotifierLazy.get().pushWatchOnlyAddresses()
        // The app's own package (kapay.kachat.app, debug builds included): the push service sends
        // a KaPay device only the wallet's pushes.
        val apnsTopic = context.packageName.removeSuffix(".debug")
        // Nothing to watch: leave the service instead. The main address alone is enough - .kachat
        // name events go to primary_address whether or not Address Activity (the watch-only list)
        // is on, so switching that off must not unregister (iOS wallet 2c59051).
        if (walletAddress.isEmpty() && watchOnlyAddresses.isEmpty()) {
            if (!leftWithNothingToWatch) {
                unregisterLocked(SigningMaterial(privateKey, walletAddress))
                leftWithNothingToWatch = true
            }
            return@withLock
        }
        leftWithNothingToWatch = false

        // Skip the network round-trip entirely when the server-accepted snapshot is unchanged —
        // this is what collapses the startup trigger + init-observer + foreground triggers into
        // one actual registration.
        val fingerprint = registrationFingerprint(
            token, walletAddress, watchedAddresses, aliases, broadcastChannels, hiddenSenders, kaPostsPubkey,
            watchedGroupIds, kaPostsKinds, kaPostsWatchAuthors, watchOnlyAddresses,
        )
        if (fingerprint == lastRegisteredFingerprint) return@withLock

        try {
            submitRegistration(
                api, token, privateKey, walletAddress, kaPostsPubkey,
                watchedAddresses, aliases, broadcastChannels, hiddenSenders, watchedGroupIds, kaPostsKinds,
                kaPostsWatchAuthors, watchOnlyAddresses, apnsTopic,
            )
        } catch (e: HttpException) {
            // Wallet-binding conflict: this token is still bound to a previously active wallet
            // (server keys registrations by token but binds them to a wallet). Mirror iOS's
            // recovery: unregister the stale binding, then retry the registration ONCE with a
            // fresh challenge.
            if (!isWalletBindingConflict(e)) throw e
            Log.i(TAG, "push register hit wallet-binding conflict, attempting recovery unregister")
            unregisterForRecovery(api, token, privateKey, walletAddress)
            submitRegistration(
                api, token, privateKey, walletAddress, kaPostsPubkey,
                watchedAddresses, aliases, broadcastChannels, hiddenSenders, watchedGroupIds, kaPostsKinds,
                kaPostsWatchAuthors, watchOnlyAddresses, apnsTopic,
            )
        }

        lastRegisteredFingerprint = fingerprint
        // Remote-push mode is only real if the system will actually show what FCM delivers;
        // with POST_NOTIFICATIONS denied the pollers' (equally invisible) banners are moot
        // anyway, but keeping the flag honest costs nothing.
        pushState.setActive(NotificationManagerCompat.from(context).areNotificationsEnabled())
        pushState.recordAttempt("register", succeeded = true, error = null, fcmTokenPresent = true)
        // The service this device is registered with - withdrawn from on a network without one.
        pushPrefs.edit().putString(KEY_REGISTERED_BASE_URL, settings.pushIndexerUrl.first()).putBoolean(KEY_LEFT, false).apply()
        Log.i(
            TAG,
            "push registered (watched=${watchedAddresses.size}, watchOnly=${watchOnlyAddresses.size}, aliases=${aliases.size}, groups=${watchedGroupIds.size}, " +
                "channels=${broadcastChannels.size}, hiddenRooms=${hiddenSenders.size}, active=${pushState.isActive})"
        )
    }

    private suspend fun submitRegistration(
        api: PushApi,
        token: String,
        privateKey: ByteArray,
        walletAddress: String,
        // null while Child Mode is on — the server then sends no KaPosts pings to this device.
        kaPostsPubkey: String?,
        watchedAddresses: List<String>,
        aliases: List<String>,
        broadcastChannels: List<String>,
        hiddenSenders: Map<String, List<String>>,
        watchedGroupIds: List<String>,
        kaPostsKinds: KaPostsNotifyKinds,
        kaPostsWatchAuthors: List<String>,
        watchOnlyAddresses: List<String>,
        apnsTopic: String?,
    ) {
        try {
            val auth = buildAuth(
                SigningMaterial(privateKey, walletAddress),
                method = "POST",
                path = "/v1/push/register",
                deviceToken = token,
                watchedAddresses = watchedAddresses,
                aliases = aliases,
                watchedGroupIds = watchedGroupIds,
                primaryAddress = walletAddress,
            )
            api.register(
                PushRegistrationRequest(
                    deviceToken = token,
                    platform = "android",
                    watchedAddresses = watchedAddresses,
                    // Non-null (even empty) would force TransitionalGroups; send null when there
                    // are no groups so DM-only devices stay on the simpler LegacyV1 shape.
                    watchedGroupIds = watchedGroupIds.ifEmpty { null },
                    primaryAddress = walletAddress,
                    aliases = aliases,
                    watchedBroadcastChannels = broadcastChannels,
                    hiddenBroadcastSenders = hiddenSenders,
                    kaPostsPubkey = kaPostsPubkey,
                    kaPostsNotifyLikes = kaPostsKinds.likes,
                    kaPostsNotifyDislikes = kaPostsKinds.dislikes,
                    kaPostsNotifyComments = kaPostsKinds.comments,
                    kaPostsNotifyReposts = kaPostsKinds.reposts,
                    kaPostsNotifyFollows = kaPostsKinds.follows,
                    kaPostsWatchAuthors = kaPostsWatchAuthors,
                    watchOnlyAddresses = watchOnlyAddresses,
                    apnsTopic = apnsTopic,
                    auth = auth,
                )
            )
        } catch (e: Exception) {
            // Any failed attempt drops us out of remote-push mode so the pollers keep notifying,
            // and forgets the fingerprint so the next trigger retries for real.
            pushState.setActive(false)
            lastRegisteredFingerprint = null
            pushState.recordAttempt(
                "register",
                succeeded = false,
                error = describeError(e),
                fcmTokenPresent = true,
            )
            Log.w(TAG, "register attempt failed: ${describeError(e)}")
            throw e
        }
    }

    /** HTTP failures with status + server error body when available; else the exception message. */
    private fun describeError(e: Exception): String = when (e) {
        is HttpException -> {
            val body = runCatching { e.response()?.errorBody()?.string()?.take(200) }.getOrNull()
            "HTTP ${e.code()}${if (body.isNullOrBlank()) "" else ": $body"}"
        }
        else -> e.message ?: e.javaClass.simpleName
    }

    /** Server contract (iOS parity): 401 + "bound to another wallet" in the error body. */
    private fun isWalletBindingConflict(e: HttpException): Boolean {
        if (e.code() != 401) return false
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull() ?: return false
        return body.lowercase().contains("bound to another wallet")
    }

    /** Signed unregister (unsigned fallback) used only inside the binding-conflict recovery path — deliberately does NOT touch pushState/fingerprint like [unregisterInternal] does, since the caller immediately re-registers. */
    private suspend fun unregisterForRecovery(
        api: PushApi,
        token: String,
        privateKey: ByteArray,
        walletAddress: String,
    ) {
        val auth = runCatching {
            buildAuth(
                SigningMaterial(privateKey, walletAddress),
                "DELETE", "/v1/push/unregister", token,
                watchedAddresses = emptyList(), primaryAddress = "",
            )
        }.getOrNull()
        try {
            api.unregister(PushUnregisterRequest(deviceToken = token, auth = auth))
        } catch (_: HttpException) {
            // The binding belongs to a wallet we can't sign for — try the unsigned form; if the
            // server refuses that too, let the retried register surface the real error.
            runCatching { api.unregister(PushUnregisterRequest(deviceToken = token, auth = null)) }
        }
    }

    private fun registrationFingerprint(
        token: String,
        walletAddress: String,
        watchedAddresses: List<String>,
        aliases: List<String>,
        broadcastChannels: List<String>,
        hiddenSenders: Map<String, List<String>>,
        kaPostsPubkey: String?,
        watchedGroupIds: List<String>,
        kaPostsKinds: KaPostsNotifyKinds,
        kaPostsWatchAuthors: List<String>,
        watchOnlyAddresses: List<String>,
    ): String = sha256Hex(
        listOf(
            token,
            walletAddress,
            canonicalizeAddresses(watchedAddresses).joinToString(","),
            canonicalizeAliases(aliases).joinToString(","),
            broadcastChannels.sorted().joinToString(","),
            hiddenSenders.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" },
            kaPostsPubkey.orEmpty(),
            canonicalizeAddresses(watchedGroupIds).joinToString(","),
            kaPostsKinds.fingerprint(),
            kaPostsWatchAuthors.sorted().joinToString("+"),
            watchOnlyAddresses.sorted().joinToString("+"),
        ).joinToString("\n")
    )

    /**
     * Fetches a fresh single-use challenge and signs the canonical preimage for [method]/[path]
     * with [material]'s key. Works from the captured [SigningMaterial] alone (never re-reads the
     * wallet), so [unregisterAsync]'s snapshot stays signable after the wallet is deleted.
     */
    private suspend fun buildAuth(
        material: SigningMaterial,
        method: String,
        path: String,
        deviceToken: String,
        watchedAddresses: List<String>,
        aliases: List<String> = emptyList(),
        watchedGroupIds: List<String> = emptyList(),
        primaryAddress: String,
        /** The group line rides only when group ids are registered (TransitionalGroups); the
         *  ring passes false explicitly - see [PushAuthPreimage.build]. */
        includeWatchedGroupIds: Boolean = watchedGroupIds.isNotEmpty(),
    ): PushAuthRequest {
        val api = pushApiOrNull()
            ?: throw IllegalStateException("push API unavailable")
        val walletPubkey = Schnorr.publicKeyXOnly(material.privateKey).toHex()
        val challenge = api.challenge()
        // The signed request's validity window must sit inside [issued_at_ms, expires_at_ms].
        val timestampMs = System.currentTimeMillis()
            .coerceIn(challenge.issuedAtMs, challenge.expiresAtMs)
        val preimage = PushAuthPreimage.build(
            method = method,
            path = path,
            deviceToken = deviceToken,
            watchedAddresses = watchedAddresses,
            watchedGroupIds = watchedGroupIds,
            primaryAddress = primaryAddress,
            aliases = aliases,
            walletPubkey = walletPubkey,
            walletAddress = material.walletAddress,
            nonce = challenge.nonce,
            timestampMs = timestampMs,
            expiresAtMs = challenge.expiresAtMs,
            includeWatchedGroupIds = includeWatchedGroupIds,
        )
        val signature = KaspaMessageSigner.sign(
            preimage,
            material.privateKey,
            KaspaMessageSigner.SigningMode.SHA256_DIGEST,
        )
        return PushAuthRequest(
            authVersion = 1,
            walletPubkey = walletPubkey,
            walletAddress = material.walletAddress,
            nonce = challenge.nonce,
            timestampMs = timestampMs,
            expiresAtMs = challenge.expiresAtMs,
            signature = signature,
        )
    }

    private fun sha256Hex(value: String): String = PushAuthPreimage.sha256Hex(value)

    private fun canonicalizeAddresses(values: List<String>): List<String> = PushAuthPreimage.canonicalizeAddresses(values)

    private fun canonicalizeAliases(values: List<String>): List<String> = PushAuthPreimage.canonicalizeAliases(values)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /**
         * The push service this device must leave before registering at [currentBase]: the base
         * it last registered with ([storedBase]), when there is one and it is a different
         * service. Trailing slashes and letter case don't make a different service. Null with no
         * current base - the no-service path (leavePushServiceOfOtherNetworkIfNeeded) owns that.
         */
        internal fun supersededBase(storedBase: String?, currentBase: String): String? {
            val old = storedBase?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val current = currentBase.trim().takeIf { it.isNotEmpty() } ?: return null
            return if (old.trimEnd('/').equals(current.trimEnd('/'), ignoreCase = true)) null else old
        }

        private const val KEY_REGISTERED_BASE_URL = "registered_base_url"
        private const val KEY_LEFT = "left_push_service"
        // Shared with KaChatFirebaseMessagingService so `adb logcat -s KaChatPush` shows the
        // whole story: registration attempts, their outcomes, token rotations, and every
        // received push.
        internal const val TAG = "KaChatPush"
    }
}
