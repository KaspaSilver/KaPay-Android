package com.kachat.app.ui

import com.kachat.app.util.tr
import com.kachat.app.R
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.navigation.navArgument
import com.kachat.app.ui.screens.*
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.WalletViewModel
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.CheckCircle

/**
 * Top-level navigation destinations.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    // A dock tab in KaPay, Settings last (iOS wallet 040f91a). The dock draws filled symbols, as
    // the iPhone tab bar does (gearshape.fill, wallet.bifold.fill, lock.shield.fill).
    object Settings    : Screen("settings",     "Settings",     Icons.Filled.Settings)
    object Portfolio   : Screen("portfolio",    "Portfolio",    Icons.Default.PieChart)
    // KaChat Wallet calls it "Wallet" in the dock, with a wallet symbol, like KaPosts' wallet tab
    // (iOS AppTab.profile in the wallet). The route stays "profile".
    object Profile     : Screen("profile",      "Wallet",       Icons.Filled.AccountBalanceWallet)
    // Labeled "Storage" (not "Cold Storage") and always in the default tab set, matching iOS's
    // AppTab.coldStorage — hideable like Portfolio/Swap via Settings > Customization > Menu, but
    // no longer a separate opt-in reached through Portfolio's old "Cold Storage Devices" row.
    object ColdStorage : Screen("cold_storage", "Storage",      com.kachat.app.ui.theme.IosSymbols.LockShieldFill)
    // .kachat names (5.2) - Kaspa Hub > .kachat: the marketplace for KaChat's own names (claim,
    // buy and sell, trustless). UI only until the name service is built (iOS AppTab.kachatNames).
    // Drawn as the ".kachat" wordmark wherever tabs are drawn (see tabIconPainter).
    object KachatNames : Screen("kachat_names", ".kachat",      Icons.Default.AlternateEmail)
    // Address Book (iOS AppTab.addressBook, 00767a4) - Kaspa Hub > Address Book: saved Kaspa
    // addresses with names, per wallet, kept in KaChat (it replaced syncing with the phone's
    // Contacts). Your own saved addresses, so Simple Mode shows it.
    // "Address Book" again since it left the dock for the Wallet tab (iOS wallet 040f91a).
    object AddressBook : Screen("address_book", "Contacts", Icons.Outlined.Badge)
    // The chat tabs (Chats, KaPosts, Public Chats, Chess, Swap, Stats, Bots) and Kaspa Hub went
    // with the chats (iOS wallet 301ebfb).
}

/**
 * The placeable tab a route belongs to, for the dock to light while you are on it: the route
 * itself when it is one of the tabs, the tab that opens it when it is a screen further in. Null
 * for a route the dock is not shown over, or one no tab owns.
 */
private fun dockOwnerRoute(route: String?): String? = when (route) {
    null -> null
    "cold_storage_detail/{accountId}", "cold_storage_tx_history/{address}" -> Screen.ColdStorage.route
    "settings_section/{sectionKey}" -> Screen.Settings.route
    Screen.AddressBook.route -> Screen.Profile.route
    "help", "manage_addresses", "identity_address_detail", "spending_address_detail/{index}",
    "kns_domains" -> Screen.Profile.route
    "portfolio_price_chart", "portfolio_value_chart", "portfolio_hashrate_chart" -> Screen.Portfolio.route
    Screen.Portfolio.route, Screen.ColdStorage.route,
    Screen.KachatNames.route -> route
    else -> null
}

/**
 * The name shown inside the Kaspa Hub grid and at the top of the section it opens. Differs from
 * [Screen.label] only where a dock label has to stay short - the bar truncates hard, so the full
 * names live here. Matches iOS's `AppTab.ecosystemTitle`.
 */
val Screen.hubTitle: String
    get() = when (this) {
        Screen.KachatNames -> tr(R.string.kp_kachat_domains_df144)
        else -> label
    }

/**
 * Tabs that always hold a dock slot and cannot be moved into Kaspa Hub (iOS
 * `AppTab.pinnedToDock`).
 *
 * Chats, because it is what KaChat is. Kaspa Hub, because it is what HOLDS whatever is not in the
 * dock - move it inside itself and everything it holds becomes unreachable. Profile, because it is
 * the way to Settings, the account list and the wallet's own addresses, so it must never be a
 * level deeper than the dock. Listed in the order a fresh dock places them, so a missing one can
 * be reinserted sensibly; where they sit among the five is the person's (Customize Dock), that
 * they are there is not.
 */
val PINNED_DOCK_ROUTES = listOf(Screen.Profile.route)

/**
 * KaPay's dock, fixed (iOS wallet `AppTab.dock`): Portfolio, Storage, Wallet (the Profile) in
 * the middle, .kachat and Settings (040f91a). There is no Chats, Kaspa Hub or Customize Dock;
 * [resolveDock] reads this rather than a saved arrangement.
 */
val WALLET_DOCK = listOf(Screen.Portfolio, Screen.ColdStorage, Screen.Profile, Screen.KachatNames, Screen.Settings)

/**
 * KaPay's home (iOS wallet `AppTab.home`, 59593af): the Wallet tab, the middle of the dock -
 * where the app opens and where it lands when the screen it was on goes away.
 */
val HOME_ROUTE = Screen.Profile.route

/** Routes drawn as iOS's Settings sheet (see IosSheetColors). None in KaPay: Settings is a dock
 *  tab (iOS wallet 040f91a), and its pages are pushed inside it. */
val SETTINGS_SHEET_ROUTES = setOf("seed_phrase", "account_backup", "backup_hygiene", "nfc_card_backup")

/** Closes a seed route the lock didn't open in this process (`SeedRouteGate`): only that entry,
 *  and only while it is on top, so a screen already in front is never popped instead. */
private fun popSeedRoute(navController: androidx.navigation.NavHostController, entry: androidx.navigation.NavBackStackEntry) {
    if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack()
}

/** Tabs besides the pinned one: the wallet's own four. */
val ASSIGNABLE_TAB_ROUTES = listOf(
    Screen.Portfolio.route, Screen.ColdStorage.route,
    Screen.KachatNames.route, Screen.Settings.route,
)

/** Route strings for tabs that can never be hidden — see [resolveTabOrder]. */
val ALWAYS_VISIBLE_TAB_ROUTES = setOf(Screen.Profile.route)

// All top-level tabs, in the app's default order (matches iOS's AppTab.defaultOrder). Settings
// isn't a tab at all (matches iOS) — it's reached one tap in from Profile's gear icon instead,
// see ProfileScreen. A default install shows exactly the five the dock can fit - Kaspa Hub,
// Portfolio, Chats, KaPosts, Profile - with Cold Storage, Swap and the websites list still
// ENABLED but living inside Kaspa Hub rather than competing for a dock slot.
val bottomNavItems = listOf(
    Screen.Portfolio,
    Screen.Profile,
    Screen.ColdStorage,
    Screen.AddressBook,
    Screen.KachatNames,
    Screen.Settings,
)

/** The dock renders at most this many items (matches iOS's AppTab.maxDockItems). */
const val MAX_DOCK_ITEMS = 5

/** Slots the person actually fills: the cap minus the pinned tabs (iOS `assignableDockSlots`). */
val ASSIGNABLE_DOCK_SLOTS: Int get() = MAX_DOCK_ITEMS - PINNED_DOCK_ROUTES.size

/**
 * Routes hard-hidden everywhere while Child Mode is on (Settings > Security > Child Mode) —
 * matches iOS's AppTab.isEnabled gate. [resolveTabOrder] below is the single choke point every
 * dock consumer flows through (the bar itself, the Chats-slot cycle, Customize Menu's preview),
 * so while it's on these tabs can't render in the dock NOR ride the Chats-slot cycle, regardless
 * of dock settings. Deliberately derived at render time only: the per-account stored dock prefs
 * (tab_order/hidden_tabs) are never rewritten with the masked state, so turning Child Mode off
 * restores exactly the arrangement the user had before.
 */
val CHILD_MODE_HIDDEN_ROUTES = setOf(
    // A marketplace - buying and selling with real KAS - so Simple Mode hides it, like Swap.
    Screen.KachatNames.route
)

/**
 * Maps persisted route strings (from AppSettingsRepository.tabOrder) back to [Screen] objects,
 * in that order. Any route no longer recognized (e.g. a tab removed in a future update) is
 * dropped, and any [Screen] missing from the persisted list (e.g. a tab added since the user
 * last reordered) is appended at the end — so neither a stale persisted value nor an app update
 * can leave a tab permanently missing or crash on an unknown route. [hiddenTabs] then filters out
 * anything the user unchecked in Settings > Customization > Menu (never applied to
 * [ALWAYS_VISIBLE_TAB_ROUTES]). [childMode] hard-hides [CHILD_MODE_HIDDEN_ROUTES] on top of
 * whatever the user's own dock settings say.
 */
@Deprecated("Superseded by resolveDock. Kept only so the one-time placement migration can read what a user could SEE under the old model.")
fun resolveTabOrder(routes: List<String>, hiddenTabs: Set<String>, childMode: Boolean = false): List<Screen> {
    val byRoute = bottomNavItems.associateBy { it.route }
    val resolved = routes.mapNotNull { byRoute[it] }
    val missing = bottomNavItems.filter { it !in resolved }
    val ordered = resolved + missing
    var visible = ordered.filter { it.route in ALWAYS_VISIBLE_TAB_ROUTES || it.route !in hiddenTabs }
    if (childMode) {
        visible = visible.filter { it.route !in CHILD_MODE_HIDDEN_ROUTES }
    }
    // Dock cap (matches iOS AppTab.visible). Anything enabled that doesn't fit simply isn't in
    // the dock - it is not lost, it is reached through Kaspa Hub. There is no longer a Chats-tab
    // cycle: re-tapping or holding Chats to reach KaPosts and Broadcasts was removed once the Hub
    // gave them a place of their own.
    if (visible.size > MAX_DOCK_ITEMS) {
        visible = visible.take(MAX_DOCK_ITEMS)
    }
    return visible
}

/**
 * The dock, from the user's PLACEMENT rather than from the first few of an order.
 *
 * That derivation is what let a tab silently disappear: nothing hid it, it was simply pushed past
 * the cap by the tabs ahead of it. A tab is now placed in the dock or in the Hub, always exactly
 * one. The pinned tabs are reinserted whatever the stored list says, so no saved - or hand-edited
 * - arrangement can leave the app without them.
 */
fun resolveDock(dockRoutes: List<String>, hiddenTabs: Set<String>, childMode: Boolean = false): List<Screen> =
    // KaPay's dock is fixed: always [WALLET_DOCK] (iOS wallet `AppTab.dock`). A saved arrangement
    // or hidden-tab list is ignored, and there is no Simple Mode to hide .kachat (59593af).
    WALLET_DOCK

/**
 * What the Kaspa Hub grid actually shows: its candidates, minus anything hidden, minus anything
 * that already has its own dock slot.
 *
 * The dock subtraction is the point - a feature sitting in the dock has no reason to also be a
 * tile one level deeper, and listing it twice would just make the grid look padded. Matches iOS's
 * `AppTab.ecosystemSections`.
 *
 * The Kaspa Websites tile is NOT here: unlike iOS it has never been a dock tab on Android (it is
 * a route reached from Profile), so it has nothing to be deduplicated against and the grid always
 * shows it.
 */
@Suppress("UNUSED_PARAMETER")
fun kaspaHubSections(
    dockRoutes: List<String>,
    hubRoutes: List<String>,
    hiddenTabs: Set<String>,
    childMode: Boolean = false,
): List<Screen> = emptyList() // KaChat Wallet has no Kaspa Hub: every tab it has is in the dock.

/**
 * Root composable: bottom nav + NavHost.
 * Wallet onboarding is shown instead when no wallet exists.
 */
@Composable
fun KaChatApp(
    walletViewModel: WalletViewModel = hiltViewModel(),
    pendingWalletActivityKind: String? = null,
    onPendingWalletActivityHandled: () -> Unit = {},
    pendingOwnAddress: String? = null,
    onPendingOwnAddressHandled: () -> Unit = {}
) {
    val isLoggedIn by walletViewModel.isLoggedIn.collectAsState()
    val startupResolved by walletViewModel.startupResolved.collectAsState()

    if (!startupResolved) {
        // Cold start, routing decision still loading (the async biometrics-for-login read that
        // gates auto-login into the last-used account). Compose NOTHING yet — composing the
        // Onboarding/accounts screen as a transient default flashed the account list for a few
        // frames before jumping into the account. The themed Surface behind this composable
        // keeps the window seamlessly on the background color, iOS-style; the wait is a few
        // milliseconds of DataStore read.
        Box(modifier = Modifier.fillMaxSize())
    } else if (!isLoggedIn) {
        CompositionLocalProvider(com.kachat.app.ui.screens.LocalSharedWalletViewModel provides walletViewModel) {
            OnboardingScreen(walletViewModel)
        }
    } else {
        // Every screen's top-bar balance and backup banner read this one instance rather than
        // a hiltViewModel() of their own (a fresh WalletViewModel per screen, each re-running
        // its init) - see LocalSharedWalletViewModel.
        CompositionLocalProvider(com.kachat.app.ui.screens.LocalSharedWalletViewModel provides walletViewModel) {
            MainShell(
                walletViewModel = walletViewModel,
                pendingWalletActivityKind = pendingWalletActivityKind,
                onPendingWalletActivityHandled = onPendingWalletActivityHandled,
                pendingOwnAddress = pendingOwnAddress,
                onPendingOwnAddressHandled = onPendingOwnAddressHandled
            )
        }
    }
}

/** See MainShell: Android 13+'s notification permission, asked once per install from the tabs. */
@Composable
private fun NotificationPermissionOnce(
    settingsViewModel: com.kachat.app.viewmodels.SettingsViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) settingsViewModel.setNotificationsEnabled(false)
    }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val prefs = context.getSharedPreferences("kachat_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)) return@LaunchedEffect
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"

/**
 * The ONE [ColdStorageViewModel] every cold-storage screen shares, scoped to the nav graph.
 *
 * They used to hang off `getBackStackEntry("cold_storage")`, which only exists when you entered
 * through the Storage tab. Reached from the Kaspa Hub - which renders the list inline under
 * "kaspa_hub" - that route never exists, so each screen got its OWN empty instance: the account
 * detail could not find the list's addresses, and an address's page had no row for itself, so it
 * fell back to showing the raw address as its title instead of "Address #N". The graph entry is
 * always on the back stack, so this is one instance no matter how you arrived.
 */
@Composable
private fun sharedColdStorageViewModel(
    navController: androidx.navigation.NavHostController
): com.kachat.app.viewmodels.ColdStorageViewModel =
    androidx.hilt.navigation.compose.hiltViewModel(
        androidx.compose.runtime.remember(navController) {
            navController.getBackStackEntry(navController.graph.id)
        }
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainShell(
    walletViewModel: WalletViewModel = hiltViewModel(),
    pendingWalletActivityKind: String? = null,
    onPendingWalletActivityHandled: () -> Unit = {},
    pendingOwnAddress: String? = null,
    onPendingOwnAddressHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // The whole stack, for the dock to light the tab a pushed screen sits in.
    val dockBackStack by navController.currentBackStack.collectAsState()
    val currentDestination = navBackStackEntry?.destination
    // The tabs keep the dock, and so do the screens pushed inside them that iOS keeps its tab
    // bar over.
    val onTabRoute = currentDestination?.hierarchy?.any { dest ->
        bottomNavItems.any { it.route == dest.route } ||
            // Cold Storage keeps its dock the whole way down, the way iOS's tab bar does: an
            // account and one of its addresses are still browsing your own holdings, not a
            // "pushed" screen you want the full height for.
            dest.route == "cold_storage_detail/{accountId}" ||
            dest.route == "cold_storage_tx_history/{address}" ||
            // Help is pushed inside the Profile tab on iOS (ProfileHelpView): its tab bar stays.
            dest.route == "help" ||
            // Settings' category pages keep the dock, as iOS's tab keeps its bar (040f91a).
            dest.route == "settings_section/{sectionKey}" ||
            // So are Manage Addresses, its address pages and Your Domains (NavigationLinks in
            // ContactsView / ManageAddressesView) and the Portfolio charts (navigationDestination)
            // - all keep iOS's tab bar.
            dest.route == "manage_addresses" ||
            dest.route == "identity_address_detail" ||
            dest.route == "spending_address_detail/{index}" ||
            dest.route == "kns_domains" ||
            dest.route == "portfolio_price_chart" ||
            dest.route == "portfolio_value_chart" ||
            dest.route == "portfolio_hashrate_chart"
    } == true

    // The dock renders straight from the persisted arrangement (WalletViewModel.tabOrder, via
    // resolveDock): tabs are arranged in Settings > Customization, not by dragging them here.
    // Reading it live means toggling a tab there updates the bar immediately, without needing to
    // leave and reopen it.
    val persistedTabOrder by walletViewModel.tabOrder.collectAsState()
    val hiddenTabs by walletViewModel.hiddenTabs.collectAsState()
    val hideBottomBar by walletViewModel.hideBottomBar.collectAsState()
    // Child Mode hides CHILD_MODE_HIDDEN_ROUTES - derived fresh on every render, never baked into
    // the stored dock prefs.
    val childModeEnabled by walletViewModel.childModeEnabled.collectAsState()
    val dockRoutes by walletViewModel.dockTabs.collectAsState()
    val localTabOrder = resolveDock(dockRoutes, hiddenTabs, childModeEnabled)
    val currentTopRoute = currentDestination?.route

    // Child Mode just turned on (or the app landed on a now-hidden screen): snap home.
    LaunchedEffect(childModeEnabled, currentTopRoute) {
        if (childModeEnabled && currentTopRoute != null && currentTopRoute in CHILD_MODE_HIDDEN_ROUTES) {
            navController.popBackStack(HOME_ROUTE, false)
        }
    }


    // Wallet address-activity receipt ("Received X KAS" on a spending or cold-storage address).
    // Cold storage has its own tab; spending addresses live in Manage Addresses, which is a real
    // route here (on iOS it is a sheet several screens deep, so iOS only fronts the app).
    LaunchedEffect(pendingWalletActivityKind) {
        val kind = pendingWalletActivityKind
        if (kind != null) {
            if (kind == com.kachat.app.services.NotificationHelper.KIND_COLD) {
                navController.navigate(Screen.ColdStorage.route) { launchSingleTop = true }
            } else {
                navController.navigate("manage_addresses") { launchSingleTop = true }
            }
            onPendingWalletActivityHandled()
        }
    }

    // A "Received" receipt that named one of your addresses: that address's History, in a sheet
    // over whatever tab is showing (iOS 13046ad). MainActivity holds the address until this shell
    // exists, so a cold start (still on the loading route when the tap arrived) lands here too -
    // iOS's OwnAddressRoute.pending.
    var ownAddressSheet by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingOwnAddress) {
        val address = pendingOwnAddress
        if (address != null) {
            ownAddressSheet = address
            onPendingOwnAddressHandled()
        }
    }
    // A bell "Received" / "Balance increased" row that carries its address: the same sheet
    // (iOS 95e2cba, OwnAddressRoute.pending + .openOwnAddress).
    val pendingBellOwnAddress by OwnAddressRoute.pending.collectAsState()
    LaunchedEffect(pendingBellOwnAddress) {
        val address = pendingBellOwnAddress ?: return@LaunchedEffect
        ownAddressSheet = address
        OwnAddressRoute.pending.value = null
    }
    ownAddressSheet?.let { address ->
        // keyed so a second tap on another address resolves afresh rather than reusing the first
        key(address) {
            OwnAddressHistorySheet(
                address = address,
                walletViewModel = walletViewModel,
                coldStorageViewModel = { sharedColdStorageViewModel(navController) },
                onDismiss = { ownAddressSheet = null },
            )
        }
    }

    // A .kachat registration in flight: its progress half sheet, back up after a relaunch (iOS 61fb0fc).
    com.kachat.app.ui.screens.KachatRegistrationPresenter()

    // A .kachat name notification: the .kachat screen opens the name (iOS beeedd4). The name stays
    // pending until that screen takes it, so a cold start lands on it too.
    val pendingKachatName by com.kachat.app.ui.screens.KachatDeepLink.pendingName.collectAsState()
    LaunchedEffect(pendingKachatName) {
        // Only where names are live (iOS 7227d69 `KachatLive.isEnabled` = isLaunched).
        if (pendingKachatName != null && com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) {
            navController.navigate(Screen.KachatNames.route) { launchSingleTop = true }
        }
    }

    // `kapay://market` (iOS 953b255): the .kachat tab, whose screen opens the Marketplace page.
    val pendingMarketplace by com.kachat.app.ui.screens.KachatDeepLink.pendingMarketplace.collectAsState()
    LaunchedEffect(pendingMarketplace) {
        if (pendingMarketplace) navController.navigate(Screen.KachatNames.route) { launchSingleTop = true }
    }

    // A payment request tapped, pasted or opened from outside the app (iOS c6851d6
    // PaymentRequestSheetPresenter): Send Kaspa with it filled in, or your own to copy or share.
    com.kachat.app.ui.screens.PaymentRequestSheetHost(walletViewModel = walletViewModel)

    // KaChat Wallet shows no welcome guide after an account is added - KaChat's walks through
    // chats and the dock, which the wallet doesn't have (iOS wallet MainTabView) - so nothing waits
    // on it: the request is dropped and the onboarding sync hold lifted.
    val pendingWelcomeGuide by walletViewModel.pendingWelcomeGuide.collectAsState()
    LaunchedEffect(pendingWelcomeGuide) {
        if (pendingWelcomeGuide) walletViewModel.consumePendingWelcomeGuide()
        walletViewModel.releaseOnboardingHoldIfIdle()
    }

    // KaChat Wallet asks once, the first time its tabs show, for permission to notify - KaChat
    // asks at launch, over the sign-in screen (iOS wallet 1806a69). Denied, notifications are
    // switched off, which also takes the device off the push service.
    NotificationPermissionOnce()

    // Frosted glass under the floating dock: the NavHost below is the backdrop, and the dock
    // draws a blurred copy of whatever sits behind it (RenderEffect on Android 12+, a
    // translucent scrim below that) - the way iOS's bar is a material rather than a colour.
    val dockHaze = remember { HazeState() }
    Scaffold(
        containerColor = LocalAppColors.current.background,
        bottomBar = {
            // Over a white page (Profile's QR pages) iOS's tab bar glass turns light, in dark mode
            // too: the glass takes on what is behind it.
            CompositionLocalProvider(
                LocalAppColors provides if (com.kachat.app.ui.screens.DockOverLightPage.active) com.kachat.app.ui.theme.LightAppColors else LocalAppColors.current
            ) {
                // Only show the floating tab bar on the top-level tab destinations —
                // "pushed" detail screens (chat thread, settings sub-screens, etc.) fill
                // the whole screen with their own Scaffold and must not have this
                // overlaid on top of them (it was blocking the chat input entirely).
                if (onTabRoute && !hideBottomBar) {
                    // Red dot on the Profile tab while the notification bell (which lives on the
                    // Profile screen) holds unread entries.
                    val dockNotifVm: com.kachat.app.viewmodels.NotificationCenterViewModel = hiltViewModel()
                    // The store is a singleton keyed per wallet internally; reload whenever the
                    // active account changes so a switch never leaves the previous account's unread
                    // dot (and entries) showing until something else happens to poke it.
                    val dockNotifAddress by walletViewModel.address.collectAsState()
                    LaunchedEffect(dockNotifAddress) {
                        dockNotifVm.store.reloadIfNeeded()
                    }
                    val dockNotifEntries by dockNotifVm.store.entries.collectAsState()
                    val dockNotifLastSeen by dockNotifVm.store.lastSeenAt.collectAsState()
                    val dockNotifUnread = dockNotifEntries.count { it.timestampMs > dockNotifLastSeen }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // navigationBarsPadding() first so the 24dp visual margin sits above
                            // the system nav bar (gesture pill or 3-button bar) rather than being
                            // eaten by it — its height isn't the same on every device, so a fixed
                            // dp value alone left the tab bar sitting under/behind it on some phones.
                            .navigationBarsPadding()
                            // iOS 26's tab bar: 21pt in from each side (measured on the iPhone).
                            .padding(bottom = 12.dp, start = 21.dp, end = 21.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Sliding a finger along the bar carries the lens; releasing lands on the
                        // tab under it, the way the glass follows a finger on iOS. Null while nobody
                        // is dragging, so the lens follows the selection instead.
                        var dockDragIndex by remember { mutableStateOf<Int?>(null) }

                        /** What a tap on a dock tab does - shared so a drag lands in the same place. */
                        fun goToDockTab(screen: Screen) {
                            val onOwnRoute = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            if (onOwnRoute) {
                                walletViewModel.notifyTabReselected(screen.route)
                                return
                            }
                            val poppedToExisting = navController.popBackStack(
                                route = screen.route,
                                inclusive = false,
                                saveState = true,
                            )
                            if (!poppedToExisting) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }

                        // The slide handler below is keyed only on the tab order and the slot width,
                        // so it is created once and outlives every navigation. Calling goToDockTab
                        // from it directly used the closure from THAT first composition, whose
                        // currentDestination was the tab you started on: sliding back onto that tab
                        // later read as "already here" and did nothing. This always holds the current one.
                        val latestGoToDockTab by androidx.compose.runtime.rememberUpdatedState<(Screen) -> Unit> {
                            goToDockTab(it)
                        }

                        // Which tab the glass pill sits under - the same test each item makes for
                        // itself below, so the pill and the lit icon can never disagree.
                        // The tab you are IN, the way iOS's tab bar keeps the tab that pushed a screen
                        // lit. A dock tab's own route lights itself. A screen that is not a dock tab
                        // lights the tab it belongs to (chess lobbies and tournaments belong to Chess,
                        // a public chat room to Public Chats, an account to Cold Storage) - and when
                        // that tab is not in the dock it lives in the Kaspa Hub, which is always there,
                        // so the Hub stays lit. It used to light nothing on any of those screens, so the
                        // glass pill vanished the moment you opened 1v1 or Tournaments from the Hub.
                        val selectedDockIndex = run {
                            val direct = localTabOrder.indexOfFirst { screen ->
                                currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            }
                            if (direct >= 0) return@run direct
                            // The tab whose stack the screen was pushed onto: the nearest dock tab
                            // under it. iOS keeps that tab lit - a room opened from Chats lights
                            // Chats, not Public Chats or the Hub.
                            val underneath = dockBackStack.asReversed().drop(1).firstNotNullOfOrNull { entry ->
                                localTabOrder.indexOfFirst { it.route == entry.destination.route }.takeIf { it >= 0 }
                            }
                            if (underneath != null) return@run underneath
                            val owner = dockOwnerRoute(currentTopRoute) ?: return@run -1
                            localTabOrder.indexOfFirst { it.route == owner }
                        }
                        // A new dock arrangement builds a fresh bar (iOS d7c3fcf keys its TabView the
                        // same way), so every icon, label and the glass lens start from the tabs the
                        // bar now holds rather than from whichever tab held that slot before. The key
                        // changes only when the arrangement does.
                        val dockArrangementKey = localTabOrder.joinToString(",") { it.route }
                        key(dockArrangementKey) {
                        // Fixed at the bar's own height so the lens can stand proud of it without
                        // growing the dock or shifting it up the screen.
                        // iOS's bar is 60pt tall (measured on the iPhone 17).
                        BoxWithConstraints(Modifier.fillMaxWidth().height(60.dp)) {
                        // The pill slides between tabs rather than appearing under the new one, the
                        // way iOS's glass tab bar moves: one piece of glass that travels.
                        val dockItemWidth = (maxWidth - 4.dp) / localTabOrder.size.coerceAtLeast(1)
                        val pillIndex = dockDragIndex ?: selectedDockIndex
                        val pillOffset by animateDpAsState(
                            targetValue = 2.dp + dockItemWidth * pillIndex.coerceAtLeast(0),
                            // Under a finger it keeps up; left to itself it settles with a spring.
                            animationSpec = if (dockDragIndex != null) tween(90) else spring(dampingRatio = 0.75f, stiffness = 420f),
                            label = "dockPill",
                        )
                        // The bar itself: the backdrop blurred, tinted with the surface colour so it
                        // still reads as a bar in either theme. It holds no items - they are in the
                        // Row below, over the pill.
                        Box(
                            Modifier
                                .height(60.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(30.dp))
                                // The shape goes to hazeChild itself: it paints the blurred backdrop
                                // to the shape it is given, and its default is a rectangle - which is
                                // what put a square panel behind the rounded bar.
                                .hazeChild(
                                    state = dockHaze,
                                    shape = RoundedCornerShape(30.dp),
                                    style = HazeStyle(
                                        // iOS's bar on black reads #191919 (the toolbar glass)
                                        tint = com.kachat.app.ui.screens.toolbarGlassFill().copy(alpha = 0.92f),
                                        blurRadius = 24.dp,
                                    ),
                                )
                                .border(0.5.dp, com.kachat.app.ui.screens.toolbarGlassEdge(), RoundedCornerShape(30.dp)),
                        )
                        // Drawn between the bar and its icons: a brighter pane of the same glass.
                        // Also while a finger is sliding: on a screen that is not a dock tab nothing is
                        // lit, and the slide used to carry no lens at all - it looked like it had not
                        // registered, though releasing still went to the tab.
                        if (dockDragIndex == null && selectedDockIndex >= 0) {
                            // At rest iOS 26 marks the tab with a filled capsule - #3A3A3A on the
                            // #191919 bar, 4pt inside its height (measured on the iPhone) - and no rim.
                            val restShape = RoundedCornerShape(percent = 50)
                            Box(
                                Modifier
                                    .offset(x = pillOffset)
                                    .padding(vertical = 4.dp)
                                    .width(dockItemWidth)
                                    .height(52.dp)
                                    .clip(restShape)
                                    .background(
                                        if (LocalAppColors.current.isDark) Color(0xFF3A3A3A)
                                        else Color.Black.copy(alpha = 0.07f)
                                    ),
                            )
                        } else if (dockDragIndex != null) {
                            // While a finger slides, the lens: a nearly clear bubble with a bright
                            // rim that stands a little proud of the bar, as iOS's does.
                            val pillShape = RoundedCornerShape(percent = 50)
                            Box(
                                Modifier
                                    .offset(x = pillOffset - 3.dp, y = (-4).dp)
                                    .width(dockItemWidth + 6.dp)
                                    .height(68.dp)
                                    .clip(pillShape)
                                    .hazeChild(
                                        state = dockHaze,
                                        shape = pillShape,
                                        style = HazeStyle(
                                            tint = Color.White.copy(alpha = 0.06f),
                                            blurRadius = 30.dp,
                                        ),
                                    )
                                    // The rim catches the light round its edge rather than being one
                                    // flat stroke - iOS's is iridescent.
                                    .border(
                                        width = 1.5.dp,
                                        brush = Brush.linearGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.55f),
                                                KaspaTeal.copy(alpha = 0.55f),
                                                Color.White.copy(alpha = 0.30f),
                                            ),
                                        ),
                                        shape = pillShape,
                                    ),
                            )
                        }
                        val dockDensity = LocalDensity.current
                        Row(
                            modifier = Modifier
                                .height(60.dp)
                                .fillMaxWidth()
                                // Watched on the INITIAL pass, so a slide can take over from the
                                // items underneath. A press that never moves is left to their tap;
                                // anything that travels carries the lens, whether it slides straight
                                // away or is held first, as on the iPhone. Nothing else on the bar
                                // wants this finger - tabs are arranged in Settings, not by dragging.
                                // Gesture navigation reserves a strip at each screen edge for Back,
                                // wider than the dock's side margin at higher Back sensitivity. A slide
                                // starting on the outermost tab began inside it, and Android took it.
                                .systemGestureExclusion()
                                .pointerInput(localTabOrder, dockItemWidth) {
                                    val slotPx = with(dockDensity) { dockItemWidth.toPx() }
                                    val startPadPx = with(dockDensity) { 2.dp.toPx() }
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        var sliding = false
                                        try {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                if (sliding) {
                                                    dockDragIndex?.let { index ->
                                                        localTabOrder.getOrNull(index)?.let { latestGoToDockTab(it) }
                                                    }
                                                }
                                                dockDragIndex = null
                                                break
                                            }
                                            val movedX = kotlin.math.abs(change.position.x - down.position.x)
                                            if (!sliding && movedX > viewConfiguration.touchSlop) sliding = true
                                            if (sliding) {
                                                change.consume()
                                                dockDragIndex = (((change.position.x - startPadPx) / slotPx).toInt())
                                                    .coerceIn(0, localTabOrder.lastIndex.coerceAtLeast(0))
                                            }
                                        }
                                        } finally {
                                            // A slide the system takes away - the back gesture, an
                                            // incoming call - cancels this rather than delivering a
                                            // release, and the lens used to stay stranded wherever the
                                            // finger was until the next touch.
                                            dockDragIndex = null
                                        }
                                    }
                                }
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            localTabOrder.forEach { screen ->
                                // Keyed by route (not position), so a tab's own state stays with that
                                // tab when the arrangement changes in Settings rather than with
                                // whichever slot happens to render it.
                                key(screen.route) {
                                    // Standing ON this tab's own route, which is what the reselect
                                    // gesture below keys off.
                                    val onOwnRoute =
                                        currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                    // Cold Storage's account and address-history screens keep the
                                    // dock, so the Storage tab stays lit while you are down there
                                    // rather than the bar going blank - iOS's tab bar does. Lit only:
                                    // a tap from down there still has to pop back up to the list, so
                                    // this must not feed the reselect check.
                                    // Lit by the same answer the glass pill sits under - see
                                    // selectedDockIndex - so the two can never disagree. Lit only: a
                                    // tap from a screen below the tab still has to pop back up to it,
                                    // so this must not feed the reselect check (onOwnRoute does).
                                    val selected = localTabOrder.getOrNull(selectedDockIndex) == screen
                                    Box(
                                        modifier = Modifier
                                            .height(52.dp)
                                            .weight(1f)
                                            .clip(RoundedCornerShape(26.dp))
                                            .clickable {
                                                // Already there — let that screen know it was re-tapped (so it can
                                                // dismiss its own transient UI, e.g. a full-screen QR overlay) rather
                                                // than running the popBackStack/navigate logic below, which for a tab
                                                // with nothing "pushed" above it falls through to navigate() and
                                                // lands back on the graph's start destination (Chats) instead of
                                                // staying put.
                                                if (onOwnRoute) {
                                                    walletViewModel.notifyTabReselected(screen.route)
                                                    return@clickable
                                                }

                                                // Tapping back to the graph's start destination (Chats) from a
                                                // "pushed" screen like Broadcasts via navigate()+popUpTo alone is
                                                // silently a no-op in Navigation Compose — popBackStack to the
                                                // route directly first (it's already on the back stack) actually
                                                // pops it. Only fall back to navigate() when the tab isn't already
                                                // present on the stack (first visit to a peer tab).
                                                val poppedToExisting = navController.popBackStack(
                                                    route = screen.route,
                                                    inclusive = false,
                                                    saveState = true
                                                )
                                                if (!poppedToExisting) {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box {
                                                Icon(
                                                    painter = screen.dockIconPainter(),
                                                    contentDescription = screen.label,
                                                    tint = if (selected) KaspaTeal else LocalAppColors.current.textPrimary,
                                                    modifier = screen.dockIconModifier(24.dp)
                                                )
                                                // How many are waiting, not just that some are. A
                                                // bare dot said "something happened"; the number is
                                                // the difference between deciding to look now and
                                                // deciding to look later, and the dock is where that
                                                // decision gets made.
                                                val badgeCount = when (screen) {
                                                    Screen.Profile -> dockNotifUnread
                                                    else -> 0
                                                }
                                                if (badgeCount > 0 && screen == Screen.Profile) {
                                                    // Profile: a red DOT while the bell holds anything
                                                    // unread - the bell itself is a dot, so the dock
                                                    // says the same thing. KaPosts keeps its count.
                                                    // The entries/lastSeen flows above recompose this
                                                    // from any tab, on tab switches and at launch.
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .offset(x = 4.dp, y = (-2).dp)
                                                            .size(10.dp)
                                                            .clip(RoundedCornerShape(50))
                                                            .background(Color(0xFFE0245E)),
                                                    )
                                                } else if (badgeCount > 0) {
                                                    // A BOX with a minimum square size, not a Text
                                                    // with padding. Padding alone gave a one-digit
                                                    // badge its text's width plus 8dp against its
                                                    // height plus 2dp - wider than it was tall, which
                                                    // is the oval. defaultMinSize floors both to the
                                                    // same 16dp, so one digit is a circle and only a
                                                    // longer label stretches it into a pill.
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            // Offset out over the icon's corner so a
                                                            // two- or three-character badge grows
                                                            // outward instead of shoving the icon.
                                                            .offset(x = 8.dp, y = (-6).dp)
                                                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                                            .clip(RoundedCornerShape(50))
                                                            .background(Color(0xFFE0245E))
                                                            .padding(horizontal = 4.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            // Capped in the LABEL, not in the stored
                                                            // count: the real number survives a long
                                                            // absence and only its rendering shortens.
                                                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = screen.label,
                                                color = if (selected) KaspaTeal else LocalAppColors.current.textPrimary,
                                                // Longer labels ("Public Chats") don't fit at 10sp once there are
                                                // enough tabs that each weight(1f) slot narrows below their natural
                                                // width — shrink just those instead of letting them clip/wrap and
                                                // get cut off by the fixed-height Box. Twelve characters need a third
                                                // step: at six tabs on a 320dp screen a slot is ~53dp, which 8sp
                                                // would run past, and softWrap = false clips rather than wraps.
                                                fontSize = when {
                                                    screen.label.length > 11 -> 7.sp
                                                    screen.label.length > 9 -> 8.sp
                                                    else -> 10.sp
                                                },
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                // iOS 26's tab labels are medium weight, selected or not -
                                                // only the colour marks the selected tab.
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                        } // key(dockArrangementKey)
                        }
                    }
                }
        }
        }
    ) { innerPadding ->
        // The green dot's half sheet, hosted once for the whole app rather than by each of the
        // eight headers that draw a dot - so it comes up OVER whatever you were reading.
        ConnectionStatusOverlayHost()

        // Only the bottom-tab destinations (Settings/Chats/Profile) sit inside this
        // shell's floating nav bar and need innerPadding reserved beneath them.
        // "Pushed" detail screens (chat thread, settings sub-screens, etc.) fill the
        // whole screen with their own Scaffold — applying innerPadding to the NavHost
        // as a whole left permanent dead space at the bottom of every one of those,
        // which became a visible gap once a Scaffold there also added imePadding().
        // Settings is a sheet on iOS, so its whole stack draws with the sheet palette - one step
        // lighter than a tab in dark mode (see IosSheetColors).
        // Always the same provider (only the value changes), so moving in and out of Settings
        // never rebuilds the NavHost.
        val inSettingsSheet = currentTopRoute in SETTINGS_SHEET_ROUTES
        val baseColors = LocalAppColors.current
        CompositionLocalProvider(
            LocalAppColors provides if (inSettingsSheet && baseColors.isDark) com.kachat.app.ui.theme.DarkSheetAppColors else baseColors
        ) {
        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE,
            // In the Settings stack the whole host draws as iOS's sheet: its rounded top 10 below
            // the status bar, black above it. The status-bar padding is consumed here, so the
            // settings screens' own statusBarsPadding adds nothing inside the sheet.
            // One fixed modifier chain: only the values change with inSettingsSheet, so no inset
            // modifier ever comes or goes (swapping one in and out is what froze the public room
            // with an endless consumed-insets loop). Outside Settings the insets are zero, the
            // padding 0 and the clip a plain rectangle - the same as no modifier at all.
            // What the dock's glass samples.
            modifier = Modifier
                .windowInsetsPadding(if (inSettingsSheet) WindowInsets.statusBars else WindowInsets(0))
                .padding(top = if (inSettingsSheet) 10.dp else 0.dp)
                .clip(if (inSettingsSheet) com.kachat.app.ui.theme.IosSheetShape else androidx.compose.ui.graphics.RectangleShape)
                .haze(state = dockHaze),
            // NavHost's own default is a 700ms crossfade — noticeably sluggish for something
            // that happens on every single tab switch/screen push. A short, snappy fade reads
            // as instant without the jarring hard-cut of no animation at all.
            enterTransition = { fadeIn(animationSpec = tween(150)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(150)) },
            popExitTransition = { fadeOut(animationSpec = tween(150)) }
        ) {
            // Settings isn't a bottom-tab destination (matches iOS - reached one tap in from
            // Profile's gear icon), so it gets the normal NavHost-level fade like any other
            // pushed detail screen, not the instant tab-swap treatment below.
            // KaPay: Settings is a dock tab (iOS wallet 040f91a) - the instant tab swap, no Done
            // button, its content stopping above the dock.
            composable(
                Screen.Settings.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                Box(modifier = Modifier.background(LocalAppColors.current.background).padding(bottom = innerPadding.calculateBottomPadding())) {
                    SettingsScreen(navController, walletViewModel = walletViewModel, isDockTab = true)
                }
            }
            composable(
                Screen.Portfolio.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    PortfolioScreen(navController = navController, walletViewModel = walletViewModel)
                }
            }
            composable(
                Screen.Profile.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    ProfileScreen(
                        viewModel = walletViewModel,
                        navController = navController
                    )
                }
            }
            // Full-screen KAS price / portfolio value charts, opened from the Portfolio squares.
            // Both share the Portfolio tab's PortfolioViewModel instance so price history / selected range / summary are already
            // loaded and stay consistent - the squares always push these from the Portfolio tab.
            composable("portfolio_price_chart") { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    try { navController.getBackStackEntry(Screen.Portfolio.route) } catch (e: IllegalArgumentException) { null }
                }
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    PortfolioPriceChartScreen(
                        navController = navController,
                        viewModel = if (parentEntry != null) hiltViewModel(parentEntry) else hiltViewModel()
                    )
                }
            }
            composable("portfolio_value_chart") { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    try { navController.getBackStackEntry(Screen.Portfolio.route) } catch (e: IllegalArgumentException) { null }
                }
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    PortfolioValueChartScreen(
                        navController = navController,
                        viewModel = if (parentEntry != null) hiltViewModel(parentEntry) else hiltViewModel()
                    )
                }
            }

            composable("portfolio_hashrate_chart") { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    try { navController.getBackStackEntry(Screen.Portfolio.route) } catch (e: IllegalArgumentException) { null }
                }
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    PortfolioHashrateChartScreen(
                        navController = navController,
                        viewModel = if (parentEntry != null) hiltViewModel(parentEntry) else hiltViewModel()
                    )
                }
            }

            composable("seed_phrase") { entry ->
                // closes itself when restored after a process death, without the lock
                com.kachat.app.ui.screens.SeedRouteGuard(entry.id, onRefused = { popSeedRoute(navController, entry) }) {
                    SeedPhraseScreen(
                        viewModel = walletViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("account_backup") { entry ->
                // closes itself when restored after a process death, without the lock
                com.kachat.app.ui.screens.SeedRouteGuard(entry.id, onRefused = { popSeedRoute(navController, entry) }) {
                    com.kachat.app.ui.screens.AccountBackupScreen(
                        viewModel = walletViewModel,
                        onClose = { navController.popBackStack() }
                    )
                }
            }
            composable("nfc_card_backup") { entry ->
                // closes itself when restored after a process death, without the lock
                com.kachat.app.ui.screens.SeedRouteGuard(entry.id, onRefused = { popSeedRoute(navController, entry) }) {
                    com.kachat.app.ui.screens.NfcCardBackupScreen(
                        viewModel = walletViewModel,
                        onClose = { navController.popBackStack() }
                    )
                }
            }
            composable("backup_hygiene") { entry ->
                // closes itself when restored after a process death, without the lock
                com.kachat.app.ui.screens.SeedRouteGuard(entry.id, onRefused = { popSeedRoute(navController, entry) }) {
                    com.kachat.app.ui.screens.BackupHygieneScreen(
                        viewModel = walletViewModel,
                        onClose = { navController.popBackStack() }
                    )
                }
            }

            composable(
                Screen.ColdStorage.route,
                // One of the five real bottom tabs now (see bottomNavItems), so it gets the same
                // instant tab-swap treatment as Portfolio/Chats/Swap/Profile above, not the
                // NavHost-level fade.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                // Runs on under the dock like the other tab screens; its Paste kpub / Scan
                // buttons are lifted above it (LocalDockInset).
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    ColdStorageListScreen(
                        navController = navController,
                        walletViewModel = walletViewModel,
                        viewModel = sharedColdStorageViewModel(navController)
                    )
                }
            }

            composable(
                "cold_storage_detail/{accountId}",
                arguments = listOf(navArgument("accountId") { type = NavType.StringType })
            ) { backStackEntry ->
                // Shares the one graph-scoped instance - see [sharedColdStorageViewModel]. A
                // fresh instance per destination meant deleteAccount() updated its own _accounts
                // flow only, leaving the list stale until something else recomposed it.
                val sharedViewModel = sharedColdStorageViewModel(navController)
                // The floating dock stays visible here (see onTabRoute): the page runs on under
                // it the way the tab screens do (LocalDockInset).
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    ColdStorageDetailScreen(
                        accountId = backStackEntry.arguments?.getString("accountId") ?: "",
                        navController = navController,
                        viewModel = sharedViewModel
                    )
                }
            }

            composable(
                "cold_storage_tx_history/{address}",
                arguments = listOf(navArgument("address") { type = NavType.StringType })
            ) { backStackEntry ->
                // The SAME instance the list and the account detail use - see
                // [sharedColdStorageViewModel]. It has to be: this screen reads the account's
                // address rows to find the one it is showing, which is what gives it a label
                // ("Address #7") instead of falling back to printing the raw address.
                // (Manage Addresses/Manage Addresses Hidden used to reuse this route too, before
                // getting their own "spending_address_detail/{index}" route below — its Send
                // button unconditionally opened Cold Storage's external-QR-signer flow, which
                // can't work for a spending address whose private key already lives in this wallet.)
                val sharedViewModel = sharedColdStorageViewModel(navController)
                // Dock visible here too: the page runs on under it, and its own Receive / Send
                // bar is lifted above it (LocalDockInset).
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    ColdStorageTxHistoryScreen(
                        address = backStackEntry.arguments?.getString("address") ?: "",
                        onBack = { navController.popBackStack() },
                        viewModel = sharedViewModel,
                    )
                }
            }

            composable(
                "cold_storage_visibility/{accountId}",
                arguments = listOf(navArgument("accountId") { type = NavType.StringType })
            ) { backStackEntry ->
                // Shares ColdStorageDetailScreen's own ViewModel instance (the only screen this
                // one is ever reached from) rather than getting a fresh one scoped to this
                // destination — a fresh instance would need its own full gap-limit rescan just to
                // reconstruct a list the detail screen already has loaded, showing an empty state
                // the whole time that rescan is in flight. Sharing also means visibility edits
                // show on the detail list the instant this screen pops.
                ColdStorageAddressVisibilityScreen(
                    accountId = backStackEntry.arguments?.getString("accountId") ?: "",
                    viewModel = sharedColdStorageViewModel(navController),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                "spending_address_detail/{index}",
                arguments = listOf(navArgument("index") { type = NavType.IntType })
            ) { backStackEntry ->
                // Deliberately its OWN route/screen rather than reusing
                // "cold_storage_tx_history/{address}" (which Manage Addresses used to route
                // through) — that screen's Send button unconditionally opens the external-QR-
                // signer flow for a watch-only key, which is broken for a spending address: its
                // private key already lives in this wallet, so it needs direct signing via
                // WalletViewModel.withdrawFromSpendingAddress instead. Index (not address) is the
                // route arg since that's what withdrawFromSpendingAddress signs by.
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    SpendingAddressTxHistoryScreen(
                        index = backStackEntry.arguments?.getInt("index") ?: 0,
                        onBack = { navController.popBackStack() },
                        viewModel = walletViewModel,
                    )
                }
            }

            // The chatting/identity address's "Manage Address" row (Profile > Chatting Address) -
            // field-for-field the same screen as spending_address_detail, just for the single
            // fixed identity address instead of a spending-chain index. Used to reuse
            // "cold_storage_tx_history/{address}" (labeled "Transaction History"), which had the
            // exact same watch-only-signer mismatch spending addresses had before getting their
            // own route above.
            composable("identity_address_detail") {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    IdentityAddressDetailScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = walletViewModel,
                    )
                }
            }

            composable("manage_addresses") {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    ManageAddressesScreen(
                        viewModel = walletViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToTxHistory = { index -> navController.navigate("spending_address_detail/$index") },
                        onOpenChattingAddress = { navController.navigate("identity_address_detail") },
                        onNavigateToHidden = { navController.navigate("manage_addresses_hidden") },
                        onNavigateToVisibility = { navController.navigate("manage_addresses_visibility") }
                    )
                }
            }

            composable("manage_addresses_visibility") {
                AddressVisibilityScreen(
                    viewModel = walletViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("manage_addresses_hidden") {
                ManageAddressesHiddenScreen(
                    viewModel = walletViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToTxHistory = { index -> navController.navigate("spending_address_detail/$index") }
                )
            }

            // The .kachat profile setup guide (iOS b000310) - from Help.
            composable("kachat_setup_guide") {
                com.kachat.app.ui.screens.KachatSetupGuideScreen(onClose = { navController.popBackStack() })
            }

            composable("kns_domains") {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    KnsDomainsScreen(
                        viewModel = walletViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // A category page, pushed inside the Settings tab with the dock still showing, as iOS's
            // NavigationStack keeps its tab bar.
            composable("settings_section/{sectionKey}") { backStackEntry ->
                Box(modifier = Modifier.background(LocalAppColors.current.background).padding(bottom = innerPadding.calculateBottomPadding())) {
                    SettingsScreen(
                        navController = navController,
                        // The shared instance, so Account Name renames the account every screen
                        // reads (the sign-in list included) and a page doesn't re-run its init.
                        walletViewModel = walletViewModel,
                        sectionKey = backStackEntry.arguments?.getString("sectionKey")
                    )
                }
            }

            // Settings > Storage sub-pages, one per backup provider (see StorageScreens.kt) -
            // the Storage section itself is just the hub of rows pointing here, like iOS.
            composable("storage_nextcloud") {
                NextcloudStorageScreen(onBack = { navController.popBackStack() })
            }

            composable("help") {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                    HelpScreen(
                        onBack = { navController.popBackStack() },
                        onKnsGuide = { navController.navigate("kachat_setup_guide") }
                    )
                }
            }

            composable("cache_settings") {
                val cacheManager: com.kachat.app.services.CacheManager = hiltViewModel<com.kachat.app.viewmodels.CacheViewModel>().cacheManager
                CacheSettingsScreen(
                    onBack = { navController.popBackStack() },
                    cacheManager = cacheManager,
                )
            }

            composable("open_source_licenses") {
                OpenSourceLicensesScreen(onBack = { navController.popBackStack() })
            }

            composable("connection_settings") {
                ConnectionSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("kaspa_explorer_settings") {
                KaspaExplorerSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // The .kachat names marketplace, a dock tab.
            composable(Screen.KachatNames.route) {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    com.kachat.app.ui.screens.KachatMarketScreen(
                        onBack = null,
                        banner = {
                            com.kachat.app.ui.screens.AccountBackupBanner(walletViewModel) { navController.navigate("account_backup") }
                        },
                    )
                }
            }
            composable(Screen.AddressBook.route) {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    // Opened from the Wallet tab now (iOS wallet 040f91a), so it has a way back.
                    com.kachat.app.ui.screens.AddressBookScreen(
                        onBack = { navController.popBackStack() },
                        walletViewModel = walletViewModel,
                    )
                }
            }

        }
        }

        // iOS's in-app toast for every screen: over a tab it sits above the dock, as iOS's sits
        // above the tab bar; a pushed screen has it above the navigation bar and keyboard.
        val dockShown = onTabRoute && !hideBottomBar
        com.kachat.app.ui.screens.IosToastHost(
            modifier = Modifier.padding(bottom = if (dockShown) innerPadding.calculateBottomPadding() else 0.dp),
            bottomInsets = if (dockShown) {
                androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            } else {
                androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)
            },
        )
    }
}

/**
 * The own address a tapped Profile-bell wallet row asks [MainShell] to open in its History sheet
 * (iOS 95e2cba `OwnAddressRoute.pending`). MainShell takes it and clears it.
 */
object OwnAddressRoute {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

/**
 * One of your own addresses, opened from a tapped "Received" notification on its History tab
 * (iOS 13046ad `OwnAddressHistorySheet`): the chatting address, a spending address or a
 * cold-storage address, each on the same screen you'd reach it through (Profile > Chatting
 * Address, Manage Addresses, Cold Storage), resolved with its balance for the header. Those
 * screens already open on History; at the root of this sheet each puts Done top left in place of
 * its back arrow (closesWithDone), where iOS's sheet has its Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnAddressHistorySheet(
    address: String,
    walletViewModel: WalletViewModel,
    coldStorageViewModel: @Composable () -> com.kachat.app.viewmodels.ColdStorageViewModel,
    onDismiss: () -> Unit,
) {
    val coldViewModel = coldStorageViewModel()
    var resolved by remember { mutableStateOf<OwnAddressResolution?>(null) }
    // Bumped by Try Again, which resolves the address afresh.
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(address, attempt) {
        resolved = null
        resolved = when {
            walletViewModel.address.value?.equals(address, ignoreCase = true) == true -> OwnAddressResolution.Chatting
            // its balance couldn't be read: a screen showing 0 would say it's empty (iOS 0799cec)
            !walletViewModel.canReadOwnAddressCoins(address) -> OwnAddressResolution.Unreadable
            else -> walletViewModel.resolveOwnSpendingAddress(address)?.let { OwnAddressResolution.Spending(it) }
                ?: coldViewModel.resolveOwnColdAddress(address)?.let { OwnAddressResolution.Cold(it) }
                ?: OwnAddressResolution.Unknown
        }
    }
    com.kachat.app.ui.theme.IosSheetColors(grouped = true) {
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = LocalAppColors.current.background,
            dragHandle = null,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Box(Modifier.fillMaxSize()) {
                    when (val target = resolved) {
                        OwnAddressResolution.Chatting -> IdentityAddressDetailScreen(
                            onBack = onDismiss,
                            viewModel = walletViewModel,
                            closesWithDone = true,
                        )
                        is OwnAddressResolution.Spending -> SpendingAddressTxHistoryScreen(
                            index = target.entry.index,
                            onBack = onDismiss,
                            viewModel = walletViewModel,
                            fallbackEntry = target.entry,
                            closesWithDone = true,
                        )
                        is OwnAddressResolution.Cold -> ColdStorageTxHistoryScreen(
                            address = target.row.address,
                            onBack = onDismiss,
                            viewModel = coldViewModel,
                            resolvedRow = target.row,
                            closesWithDone = true,
                        )
                        OwnAddressResolution.Unreadable, OwnAddressResolution.Unknown, null -> Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                                TextButton(onClick = onDismiss) {
                                    Text(
                                        androidx.compose.ui.res.stringResource(com.kachat.app.R.string.done),
                                        color = KaspaTeal,
                                    )
                                }
                            }
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (target == null) {
                                    CircularProgressIndicator(color = KaspaTeal)
                                } else if (target == OwnAddressResolution.Unreadable) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Text(
                                            tr(R.string.kp_couldn_t_load_the_coins_at_373c0),
                                            color = LocalAppColors.current.textSecondary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                        )
                                        TextButton(onClick = { attempt++ }) {
                                            Text(
                                                androidx.compose.ui.res.stringResource(com.kachat.app.R.string.try_again),
                                                color = KaspaTeal,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        androidx.compose.ui.res.stringResource(com.kachat.app.R.string.own_address_not_in_wallet),
                                        color = LocalAppColors.current.textSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    // Copies from these screens confirm in the sheet's own toast.
                    com.kachat.app.ui.screens.IosToastHost(bottomInsets = com.kachat.app.ui.screens.SheetToastInsets)
                }
            }
        }
    }
}

/** Which of your addresses a "Received" notification named (iOS 13046ad `Resolved`). */
private sealed interface OwnAddressResolution {
    data object Chatting : OwnAddressResolution
    data class Spending(val entry: com.kachat.app.services.WalletService.SpendingAddressEntry) : OwnAddressResolution
    data class Cold(val row: com.kachat.app.viewmodels.ColdStorageViewModel.AddressRow) : OwnAddressResolution
    data object Unknown : OwnAddressResolution
    /** Its balance couldn't be read: a screen showing 0 would say it's empty, and its Send would
     *  be off (iOS 0799cec). */
    data object Unreadable : OwnAddressResolution
}
