package com.kachat.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kachat.app.services.CrashRecorder
import com.kachat.app.services.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application class — required by Hilt for dependency injection.
 * All singleton services are initialized here via Hilt modules in the `di` package.
 *
 * Startup rule: nothing optional may stop the app from opening. A Play build 5.2 would not open
 * on a GrapheneOS phone (no crash log): 5.2 built more services eagerly, and on a hardened ROM
 * the Keystore behind EncryptedSharedPreferences can throw. So every service below is injected
 * as [dagger.Lazy] - Hilt's field injection (inside super.onCreate) creates none of them - and
 * onCreate then builds each one as its own [startupStep], in the same order as before: one that
 * throws is logged and the app opens without it. (The portfolio is built just after, off the main
 * thread.) The lifecycle callbacks below are guarded the same way. [hiltWorkerFactory] is the one
 * plain field (WorkManager's configuration needs it, and it is built from the graph alone).
 */
@HiltAndroidApp
class KaChatApplication : Application(), Configuration.Provider {

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // Before anything else - Hilt builds the whole object graph in onCreate, and a crash in
        // there is exactly the "closes on the splash screen" a user cannot describe. The record
        // is bundled into the diagnostics archive and offered for sharing on the next launch.
        CrashRecorder.install(this)
        // The network this launch runs on (Settings > Connection > Testnet), known before Hilt
        // starts any service - the node pool, wallet and indexers are all set up for it.
        // [base], not `this`: inside attachBaseContext the Application's own applicationContext
        // is still null, and reading prefs through it crashed every launch.
        com.kachat.app.util.KaspaNetwork.init(base)
        com.kachat.app.util.AppText.init(base)
    }

    // @Singleton instances are otherwise only created lazily the first time something actually
    // requests them. PushRegistrationManager's init block observes the active account, the
    // notifications setting and Address Activity, and re-registers (or unregisters) the FCM token with the push service whenever any of it
    // changes. Field-injecting it here guarantees those observers run for the app's whole
    // lifetime, not just after the first screen that happens to request it.
    @Inject
    lateinit var pushRegistrationManager: dagger.Lazy<com.kachat.app.services.PushRegistrationManager>

    @Inject
    lateinit var nodePoolManager: dagger.Lazy<com.kachat.app.services.NodePoolManager>

    // Address-activity notifications (external receipts on spending/cold addresses): per-wallet
    // baseline + diff engine, foreground poll + on-foreground catch-up. See its class doc.
    @Inject
    lateinit var addressActivityNotifier: dagger.Lazy<com.kachat.app.services.AddressActivityNotifier>

    /** The live UTXO subscription to the wallet's own addresses, while the app is open. */
    @Inject
    lateinit var ownAddressSubscription: dagger.Lazy<com.kachat.app.services.OwnAddressSubscription>

    // Continuous automatic Nextcloud sync — same lazy-singleton reasoning as above: its init block observes the connected account + Automatic Sync toggle (schedules or
    // cancels the 6h WorkManager fallback) and the active wallet (silent one-time auto-restore
    // of the shared backup file). Field-injecting it here guarantees those observers run from
    // app startup, not only after the Nextcloud storage screen happens to be opened. The
    // lifecycle triggers below also feed it.
    @Inject
    lateinit var nextcloudSyncService: dagger.Lazy<com.kachat.app.services.NextcloudSyncService>

    // For mirroring the persisted "Verbose API Logging" toggle into ApiLogging.verbose (a plain
    // volatile flag the OkHttp logging interceptor reads per request) — see onCreate below.
    @Inject
    lateinit var appSettingsRepository: dagger.Lazy<com.kachat.app.repository.AppSettingsRepository>

    // Foreground flag: the Received push shows no banner while the app is open (Address Activity
    // announces that receive itself). The lifecycle observer below keeps the flag current.
    @Inject
    lateinit var notificationHelper: dagger.Lazy<com.kachat.app.services.NotificationHelper>

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    // .kachat registrations in flight (testnet only) resume on every foreground, like iOS's
    // app-active (KaChat 5df42b4). Lazy, so a mainnet launch never even builds the names stack.
    @Inject
    lateinit var kachatNamesActions: dagger.Lazy<com.kachat.app.services.kachatnames.KachatNamesActions>

    // Identity is .kachat everywhere (iOS e52357d): every contact name and avatar reads the
    // registry's identity cache and the social image cache through their companions, so both are
    // built at startup - on mainnet too since iOS d36fc42, for address profiles (profilesEnabled).
    @Inject
    lateinit var kachatNamesRegistry: dagger.Lazy<com.kachat.app.services.kachatnames.KachatNamesRegistry>

    @Inject
    lateinit var knsRevealRecovery: dagger.Lazy<com.kachat.app.services.KnsRevealRecovery>

    @Inject
    lateinit var kachatSocialImages: dagger.Lazy<com.kachat.app.services.kachatnames.KachatSocialImageResolver>

    // The Address Book (iOS 00767a4): built at startup because every display name reads it
    // through its companion (addressDisplayName), and it follows the active wallet on its own.
    @Inject
    lateinit var addressBookManager: dagger.Lazy<com.kachat.app.services.AddressBookManager>

    // Payment requests (iOS c6851d6): built at startup because a request card or a pasted link
    // asks whether it is your own through its companion (PaymentRequest.isOwn).
    @Inject
    lateinit var paymentRequestWallet: dagger.Lazy<com.kachat.app.services.PaymentRequestWallet>

    // Portfolio rows still waiting for a price resume pricing whenever a wallet loads (iOS
    // PortfolioViewModel.setCurrentWallet, run by WalletManager on every wallet load) - built at
    // startup so its active-wallet observer runs from launch, not first when a screen with
    // portfolio numbers opens.
    @Inject
    lateinit var portfolioRepository: dagger.Lazy<com.kachat.app.repository.PortfolioRepository>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory()).build()

    /**
     * WorkManager initializes itself the first time anything asks for it - and a singleton built
     * early (NextcloudSyncService's init cancels or schedules its work on a background thread)
     * could ask before the [hiltWorkerFactory] field below had been injected,
     * which crashed with an uninitialized lateinit. The Hilt component already exists by then, so
     * the factory is read straight from it in that window.
     */
    private fun workerFactory(): HiltWorkerFactory =
        if (::hiltWorkerFactory.isInitialized) hiltWorkerFactory
        else dagger.hilt.android.EntryPointAccessors
            .fromApplication(this, WorkerFactoryEntryPoint::class.java)
            .hiltWorkerFactory()

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface WorkerFactoryEntryPoint {
        fun hiltWorkerFactory(): HiltWorkerFactory
    }

    /** One non-essential startup step: a failure is logged and the app opens without it. */
    private fun startupStep(what: String, block: () -> Unit) =
        com.kachat.app.util.StartupSafety.step(TAG, what, block)

    /** For the coroutines launched below: a failure is logged, never the end of the process. */
    private val logOnly = com.kachat.app.util.StartupSafety.logOnlyHandler(TAG)

    override fun onCreate() {
        super.onCreate()

        // How did the previous process end? A native crash or an OS kill never reaches the
        // exception handler installed in attachBaseContext; the system's own exit history does.
        startupStep("process exit history") { CrashRecorder.noteProcessExits(this) }

        // The always-on services, built now exactly as field injection used to build them (same
        // order), each on its own so one failing cannot take the others - or the app - down.
        // Every one of them is reached again through its Lazy below, and from screens.
        startupStep("push registration") { pushRegistrationManager.get() }
        startupStep("node pool") { nodePoolManager.get() }
        startupStep("address activity") { addressActivityNotifier.get() }
        startupStep("Nextcloud sync") { nextcloudSyncService.get() }
        startupStep("settings") { appSettingsRepository.get() }
        startupStep("notifications") { notificationHelper.get() }
        // The Address Book's companion is what every display name reads, and its own init
        // follows each wallet load - so it is still built here, at launch, only guarded.
        startupStep("Address Book") { addressBookManager.get() }
        startupStep("payment requests") { paymentRequestWallet.get() }

        // The .kachat identity caches the display rules read (see the fields above). Every
        // network since iOS d36fc42 (`profilesEnabled`): mainnet has no registry yet, but address
        // profiles need none, so its identities are profile-only (avatars, banners, bios).
        startupStep(".kachat identity caches") {
            if (com.kachat.app.services.kachatnames.KachatNamesService.profilesEnabled) {
                startupStep(".kachat registry") { kachatNamesRegistry.get() }
                startupStep(".kachat social images") { kachatSocialImages.get() }
                // No .kachat sweep of unnamed chat contacts: the wallet has none (iOS wallet 301ebfb).
            }
        }

        // The portfolio's wallet-load price backfill (see the field above): built just after
        // launch, off the main thread, so its active-wallet observer still runs from the start -
        // but a portfolio store the Keystore will not open can never keep the app from opening.
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.Default + logOnly) {
            startupStep("portfolio price backfill") { portfolioRepository.get() }
        }

        // The 15-minute background refresh (see SyncWorker's doc comment): the balance and one
        // receive pass while the app is closed.
        startupStep("wallet sync worker") {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME,
                // UPDATE (not KEEP) so the CONNECTED constraint below reaches devices whose job was
                // enqueued by an older build without it; the schedule itself is unchanged.
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    // No network means the refresh inside can only fail and burn a run
                    // (WorkManager would still wake the process for it) — wait for connectivity.
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }

        // Keep ApiLogging.verbose (read by the OkHttp interceptors in AppModule on every request)
        // in sync with the persisted Verbose API Logging toggle for the process's whole lifetime.
        // A volatile flag instead of collecting the Flow inside the interceptor because that code
        // runs on OkHttp threads for every request.
        ProcessLifecycleOwner.get().lifecycleScope.launch(logOnly) {
            appSettingsRepository.get().verboseApiLogging.collect { com.kachat.app.util.ApiLogging.verbose = it }
        }

        // Foreground/background transitions. Nothing keeps the process alive while backgrounded:
        // the push service and the 15-minute SyncWorker cover a closed app. The node pool reads
        // the same foreground flag (NotificationHelper.appForegroundFlow): backgrounded, it stops
        // probing and closes its connections shortly after, so their keepalive pings stop too.
        val foregroundObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Backgrounded: the Received push shows its banner again.
                startupStep("notifications: background") { notificationHelper.get().setAppForeground(false) }
                startupStep("address activity: background") { addressActivityNotifier.get().onAppBackground() }
                startupStep("own-address subscription: stop") { ownAddressSubscription.get().stop() }
                // Backgrounding is the natural "done" moment - run the Nextcloud
                // automatic sync then: a catch-up when the last upload is an hour old or the
                // persisted dirty flag says a sync is owed (NEXTCLOUD_SYNC.md §6). autoBackupIfDue
                // no-ops unless the Automatic Sync toggle is on and an account is connected; it
                // swallows its own failures (the next trigger or the fallback worker retries).
                owner.lifecycleScope.launch(Dispatchers.IO + logOnly) {
                    nextcloudSyncService.get().autoBackupIfDue()
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                // Foregrounded: Address Activity announces receives itself; a racing push for
                // the same tx collapses via the txId dedupe inside NotificationHelper.
                startupStep("notifications: foreground") { notificationHelper.get().setAppForeground(true) }
                // Immediate catch-up diff for address activity (external receipts while the app
                // was closed, first-run silent baseline seeding), then its foreground poll loop.
                startupStep("address activity: foreground") { addressActivityNotifier.get().onAppForeground() }
                // Live "Received KAS" and balance for the wallet's own addresses (iOS wallet
                // 301ebfb `WalletService.setupUtxoSubscription`).
                startupStep("own-address subscription: start") { ownAddressSubscription.get().start() }
                // KaChat Wallet: the push service watches the wallet's own addresses, and a new
                // spending slot or Cold Storage account adds one - re-sent on foreground, skipped
                // when nothing changed (the registration fingerprint).
                startupStep("push: own addresses") { pushRegistrationManager.get().registerAsync() }
                // A batch of gRPC connections can die silently while backgrounded/asleep (the OS
                // tears down sockets, and each KaspadConnection's own self-reconnect can be
                // suspended along with the rest of the app) - reconnect any that are dead right
                // now instead of waiting for the next 5-30s probe cycle to notice and replace them.
                startupStep("node pool: reconnect") { nodePoolManager.get().reconnectStaleConnections() }
                // This wallet's own profile follows the chain on every network (a profile saved
                // on another device shows here; iOS 5d4ce87), and .kachat registrations in flight
                // (where names are live) resume after a relaunch: commit -> wait ->
                // register continues by itself.
                startupStep(".kachat resume") {
                    if (com.kachat.app.services.kachatnames.KachatNamesService.profilesEnabled) {
                        kachatNamesActions.get().resume()
                    }
                }
                if (runCatching { com.kachat.app.services.kachatnames.KachatNamesService.isLaunched }.getOrDefault(false)) {
                    // .kachat news for the Profile bell (offers, sales, renewal, expiry): the
                    // refresh runs KachatNamesNotifier (iOS 86471dd). Where names are live
                    // (testnet-10, and mainnet since iOS ef6b21e).
                    owner.lifecycleScope.launch(Dispatchers.IO + logOnly) {
                        try {
                            kachatNamesRegistry.get().refreshIfStale()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("KaChatApplication", ".kachat registry refresh failed", e)
                        }
                    }
                }
                // a .kas commit whose reveal never went out (iOS 2da5f4b)
                owner.lifecycleScope.launch(Dispatchers.IO + logOnly) {
                    knsRevealRecovery.get().resume()
                }
                // Nextcloud on launch/foreground: brings in another device's changes, or uploads
                // a change still owed (force-kill, crash, days of disuse). At most once a minute.
                owner.lifecycleScope.launch(Dispatchers.IO + logOnly) {
                    nextcloudSyncService.get().syncWithServerOnOpen()
                }
            }
        }
        startupStep("lifecycle observer") { ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver) }
    }

    private companion object {
        const val TAG = "KaChatApplication"
    }
}
