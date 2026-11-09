package com.kachat.app.ui.screens

import com.kachat.app.util.tr
import androidx.compose.material.icons.filled.IosShare
import com.kachat.app.util.UserFacingError
import com.kachat.app.util.redactedForLog
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.Gravity
import android.view.WindowManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.SubcomposeAsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.iosGlass
import com.kachat.app.ui.theme.iosShadow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.PhoneEnabled
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.FlowRow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kachat.app.R
import com.kachat.app.services.ColdStorageAddressDiscovery
import com.kachat.app.services.KnsInscriptionEngine
import com.kachat.app.services.KnsService
import com.kachat.app.services.UtxoEntry
import com.kachat.app.ui.theme.KaspaBlue
import com.kachat.app.ui.theme.KaspaSubtext
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.KaspaAddress
import com.kachat.app.util.KaspaMass
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.util.ImagePrep
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.copyPrivateKeyWithAutoWipe
import com.kachat.app.viewmodels.ConnectionStatus as ConnStatus
import com.kachat.app.viewmodels.ConnectionViewModel
import com.kachat.app.viewmodels.NodeInfo
import com.kachat.app.viewmodels.SettingsViewModel
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.kachat.app.util.showAddressCopiedToast
import androidx.compose.material.icons.outlined.InsertDriveFile

/**
 * The Wallet tab (iOS 953b255 `ProfileView`, as KaChat's `WalletView`): the total balance, split
 * into the chatting and spending addresses; Send, Receive and Request; Backup Hygiene when due; the
 * Manage Addresses and Chatting Address squares; then Your Domains, Contacts and Help. No profile
 * card: those belong to KaChat and KaPost. About is in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: WalletViewModel,
    navController: NavController,
    portfolioViewModel: com.kachat.app.viewmodels.PortfolioViewModel = hiltViewModel()
) {
    val address by viewModel.address.collectAsState()
    val identityBalanceSompi by viewModel.balanceSompi.collectAsState()
    val chattingSompi by viewModel.hotWalletBalance.chattingSompi.collectAsState()
    /** Every spending address together. Null until it's known, which shows as a dash. */
    val spendingSompi by viewModel.hotWalletBalance.spendingSompi.collectAsState()
    val scrollState = rememberScrollState()

    var showFundIdentityQr by remember { mutableStateOf(false) }
    // Its own state (not shared with any other QR overlay) so its bigger green border can't
    // accidentally affect an unrelated overlay reusing the same flag.
    var showAcceptPaymentQr by remember { mutableStateOf(false) }
    // The address the Receive QR should draw: one that has never been used. Warmed when this
    // screen appears and re-confirmed when the overlay opens - see both effects below. Cleared
    // with an account switch: the head start was for the previous account (iOS 2da5f4b).
    var receiveQrAddress by remember(address) { mutableStateOf<String?>(null) }
    /** A lookup for [receiveQrAddress] is still out - the page says it is preparing one. */
    var receiveQrResolving by remember(address) { mutableStateOf(true) }
    // Re-confirmed on open, but NOT cleared first: the warm-up above has almost always settled
    // it already, so the overlay draws immediately and this only swaps the value if the warmed
    // answer went stale in between. A QR must not change under a pointed camera for any lesser
    // reason, and blanking it first would guarantee a spinner every time.
    LaunchedEffect(showAcceptPaymentQr) {
        if (showAcceptPaymentQr) {
            if (receiveQrAddress == null) receiveQrResolving = true
            // Shown now: it goes on the Manage Addresses list (and is watched for payments).
            viewModel.resolveFreshReceiveAddress(revealing = true) { address ->
                receiveQrAddress = address
                receiveQrResolving = false
            }
        }
    }
    // What the Send and Request buttons open (iOS 953b255 `walletAction`); Receive is the Accept
    // Payment page above, as a sheet.
    var showSendSheet by remember { mutableStateOf(false) }
    var showRequestPayment by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        // The balance requests at most every 30 s: switching tabs back and forth started them
        // each time. Arriving funds still refresh at once (the activity events below), and pull
        // to refresh is always there (iOS 9de981a).
        if (viewModel.claimProfileAppearRefresh()) {
            viewModel.refreshBalance()
            viewModel.refreshSpendingAddress()
        }
        viewModel.refreshOwnedDomains()
        viewModel.loadManageAddresses()
    }
    // Decide the Receive QR's address NOW, not when the button is tapped. The freshness
    // check is a network round trip, so doing it on tap put a spinner between pressing
    // Receive Kaspa and seeing a code. Only picked, not revealed (iOS 24c2094): nothing
    // appears in Manage Addresses until the QR is actually shown.
    LaunchedEffect(address) {
        viewModel.resolveFreshReceiveAddress(revealing = false) { resolved ->
            receiveQrAddress = resolved
            receiveQrResolving = false
        }
    }

    // Funds landing used to show up here only on the next open or a pull-to-refresh - the screen
    // that shows your balance was the one screen not listening for it.
    LaunchedEffect(Unit) {
        viewModel.ownAddressUtxoActivityEvents.collect {
            viewModel.refreshBalance()
            viewModel.refreshSpendingAddress()
        }
    }

    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.refreshBalanceAndAwait()
            viewModel.refreshSpendingBalanceAndAwait()
            viewModel.hotWalletBalance.refresh(force = true)
            pullRefreshState.endRefresh()
        }
    }

    // Re-tapping the Profile tab while already on it (e.g. to back out of a full-screen QR
    // overlay) doesn't re-navigate/recompose this screen — see WalletViewModel.notifyTabReselected.
    val tabReselectSignal by viewModel.tabReselectSignal.collectAsState()
    LaunchedEffect(tabReselectSignal) {
        if (tabReselectSignal.second == "profile") {
            showFundIdentityQr = false
            showAcceptPaymentQr = false
        }
    }

    val dockInset = LocalDockInset.current
    Scaffold(
        containerColor = LocalAppColors.current.background,
        // The page runs on under the dock; its scroll ends with room to clear it.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                // The bell: Kaspa arriving in your wallets and .kachat news (iOS 86471dd, 4500ebc).
                val notifCenterVm: com.kachat.app.viewmodels.NotificationCenterViewModel = hiltViewModel()
                val notifEntries by notifCenterVm.store.entries.collectAsState()
                val notifLastSeen by notifCenterVm.store.lastSeenAt.collectAsState()
                var showNotifCenter by remember { mutableStateOf(false) }
                val notifUnread = notifEntries.count { it.timestampMs > notifLastSeen }
                // The connection dot, the whole hot wallet's balance and the bell, then the backup
                // strip and "Wallet" (iOS 953b255). The share button went with the profile card.
                MainPageHeader(
                    title = stringResource(R.string.wallet_title),
                    balanceCopiesOnTap = true,
                    banner = { AccountBackupBanner(viewModel) { navController.navigate("account_backup") } },
                    actions = {
                        IconButton(
                            onClick = {
                                notifCenterVm.store.reloadIfNeeded()
                                showNotifCenter = true
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box {
                                Icon(
                                    // SF Symbols "bell" is an outline.
                                    Icons.Outlined.Notifications,
                                    contentDescription = tr(R.string.kp_notifications_753a2),
                                    tint = KaspaTeal,
                                    modifier = Modifier.size(22.dp),
                                )
                                if (notifUnread > 0) {
                                    // A plain red DOT (no count) - "there is something unread"
                                    // is the signal.
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0xFFE0245E)),
                                    )
                                }
                            }
                        }
                    },
                )
                if (showNotifCenter) {
                    // A full-height sheet, as iOS presents GlobalNotificationListView.
                    GlobalNotificationSheet(
                        store = notifCenterVm.store,
                        walletViewModel = viewModel,
                        navController = navController,
                        onDismiss = { showNotifCenter = false },
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .padding(top = 8.dp),
            // iOS's VStack(spacing: 16).
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // one of your .kachat names expired and is in grace: renew it in time (iOS 6ac48a7)
            KachatExpiredNamesBanner(walletAddress = address)
            WalletBalanceCard(
                chattingSompi = chattingSompi,
                spendingSompi = spendingSompi,
                portfolioViewModel = portfolioViewModel,
            )
            WalletActionsRow(
                onSend = { showSendSheet = true },
                onReceive = { showAcceptPaymentQr = true },
                onRequest = { showRequestPayment = true },
            )
            // Every six months after a finished backup, under the three buttons (iOS 953b255).
            // Until the backup steps are finished, the strip above the title says so instead.
            val backupPendingAddresses by viewModel.accountBackupStatus.pendingAddresses.collectAsState()
            val backupConfirmedAt by viewModel.accountBackupStatus.confirmedAt.collectAsState()
            if (remember(address, backupConfirmedAt, backupPendingAddresses) { viewModel.accountBackupStatus.isHygieneDue(address) }) {
                BackupHygieneCard { navController.navigate("backup_hygiene") }
            }
            // Testnet only: free TN10 coins for the chatting address, from the official faucet
            // (iOS 182ae68).
            if (com.kachat.app.util.KaspaNetwork.isTestnet && address != null) {
                TestnetFaucetClaimButton(
                    address = address!!,
                    balanceSompi = { viewModel.balanceSompi.value },
                    refreshBalance = { viewModel.refreshBalanceAndAwait() },
                )
            }
            // Which address a send comes from is picked on the send screen (From), so this tab
            // doesn't split them (iOS 953b255 `addressSquares`).
            WalletAddressSquares(
                onManageAddresses = { navController.navigate("manage_addresses") },
                onChattingAddress = { showFundIdentityQr = true },
            )

            // Your domains are a thing you own, not a setting of the profile that happens to use
            // one of them - so they sit here rather than two levels down inside Edit KNS Profile,
            // where nothing on this screen suggested they existed.
            // Every name the account owns: .kachat, KNS, .k and .kaspa (iOS df23b6f, 10e4a1a).
            val ownedKnsDomains by viewModel.ownedDomains.collectAsState()
            val ownedServiceNames by viewModel.nameServices.owned.collectAsState()
            val kachatRevision by viewModel.kachatRegistryRevision.collectAsState()
            var kachatOwnedCount by remember(address) { mutableStateOf(0) }
            // The .k and .kaspa lookups, so the count includes them before the list opens.
            LaunchedEffect(address) {
                address?.let { viewModel.nameServices.refresh(it) }
            }
            // Reloads whenever the registry moves (a registration, sale or transfer lands).
            val kachatCacheContext = LocalContext.current
            LaunchedEffect(address, kachatRevision) {
                val owner = address ?: return@LaunchedEffect
                // the saved list's count at once; the read below corrects it when it answers
                // (KachatOwnedNamesCache, iOS wallet f4f5c0f)
                if (kachatOwnedCount == 0 && com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) {
                    com.kachat.app.services.kachatnames.KachatOwnedNamesCache.load(kachatCacheContext, owner)?.let {
                        kachatOwnedCount = it.names.size
                    }
                }
                viewModel.watchKachatOwnedCount(owner) { kachatOwnedCount = it }
            }
            val domainsCount = kachatOwnedCount + ownedKnsDomains.size + ownedServiceNames.values.sumOf { it.size }
            // iOS: each a card of its own (glassBackground 18) - a Label (white outline symbol,
            // body text), the count for Your Domains, and a caption chevron, padded 16.
            ProfileLinkRow(
                label = stringResource(R.string.kns_domains),
                icon = Icons.Default.AlternateEmail,
                value = "$domainsCount",
                onClick = { navController.navigate("kns_domains") },
            )

            // KaPay: Settings is its own dock tab, and the Address Book opens from here (iOS
            // wallet 040f91a addressBookSection).
            ProfileLinkRow(
                label = stringResource(R.string.ab_address_book),
                icon = Icons.Outlined.Badge,
                onClick = { navController.navigate(com.kachat.app.ui.Screen.AddressBook.route) },
            )

            ProfileLinkRow(
                label = stringResource(R.string.help),
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                onClick = { navController.navigate("help") },
            )

            // Room for the last card to scroll clear of the dock it runs under.
            Spacer(modifier = Modifier.height(dockInset + 8.dp))
        }

        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        }
    }

    // The Chatting Address page, pushed as iOS's NavigationLink pushes it: over the whole screen,
    // bar included, the dock left in place, a back chevron to leave.
    PushedQrPage(visible = showFundIdentityQr, white = true, onBack = { showFundIdentityQr = false }) {
        ChattingAddressQrPage(
            address = address ?: "",
            balanceSompi = identityBalanceSompi,
            dismiss = QrPageDismiss.BACK,
            onDismiss = { showFundIdentityQr = false },
        )
    }
    // Send from the main address with nothing filled in, the Receive Kaspa page as a sheet with
    // Done, or a new payment request (iOS 953b255 `walletAction`).
    if (showAcceptPaymentQr) {
        QrPageSheet(onDismiss = { showAcceptPaymentQr = false }, white = receiveQrAddress != null) {
            ReceiveKaspaQrPage(
                address = receiveQrAddress,
                resolving = receiveQrResolving,
                dismiss = QrPageDismiss.DONE,
                onDismiss = { showAcceptPaymentQr = false },
                walletViewModel = viewModel,
                title = stringResource(R.string.receive_kaspa),
            )
        }
    }
    if (showSendSheet) {
        IosFullSheet(onDismissed = { showSendSheet = false }, swipeToDismiss = false) { close ->
            SpendingAddressSendFlow(
                fromAddress = address ?: "",
                balanceSompi = identityBalanceSompi,
                title = stringResource(R.string.chats_send_kaspa),
                viewModel = viewModel,
                portfolioViewModel = portfolioViewModel,
                onDone = close,
                presentedAsSheet = true,
            )
        }
    }
    if (showRequestPayment) {
        CreatePaymentRequestSheet(walletViewModel = viewModel, onDismissed = { showRequestPayment = false })
    }
}

/**
 * Dedicated KNS domain-management screen — the owned-domain list (star to mark primary, swap
 * icon to transfer), plus inscribing a new domain. Used to live inline as a collapsible section
 * on [ProfileScreen] itself; broken out once the list plus its two dialogs (inscribe/transfer)
 * made that screen too crowded to scan at a glance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnsDomainsScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val ownedDomainAssets by viewModel.ownedDomainAssets.collectAsState()
    // Testnet: a .kachat name opened from the .kachat tab - its live detail (iOS 5df42b4).
    var selectedKachat by remember { mutableStateOf<com.kachat.app.services.kachatnames.NameInfo?>(null) }
    // Inscribe: the .kachat marketplace over Your Domains, whether or not .kachat is in the dock
    // or Kaspa Hub; the list refreshes when it closes (iOS e4da63d).
    var showKachatMarket by remember { mutableStateOf(false) }
    var kachatRefreshRequested by remember { mutableStateOf(false) }
    // The name service whose card was tapped (iOS debd6fd), null on the cards themselves. Held
    // above the marketplace's early return, so closing the marketplace lands back on .kachat.
    var openedService by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<com.kachat.app.services.NameServiceTLD?>(null) }
    val walletAddress by viewModel.address.collectAsState()
    // The cards' counts (iOS 2ee667c), kept while a card's screen is open.
    val ownedServiceNames by viewModel.nameServices.owned.collectAsState()
    val setPrimaryAuthContext = LocalContext.current
    val kachatCount = rememberKachatHeldCount(walletAddress ?: "")
    if (showKachatMarket) {
        androidx.activity.compose.BackHandler { showKachatMarket = false; kachatRefreshRequested = true }
        KachatMarketScreen(
            onBack = { showKachatMarket = false; kachatRefreshRequested = true },
        )
        return
    }
    val primaryDomainName by viewModel.primaryDomainName.collectAsState()
    val setPrimaryState by viewModel.setPrimaryState.collectAsState()
    var selectedDomain by remember { mutableStateOf<com.kachat.app.services.KnsAsset?>(null) }
    var showSendScreen by remember { mutableStateOf(false) }
    var editingDomain by remember { mutableStateOf<com.kachat.app.services.KnsAsset?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshOwnedDomainsAndAwait()
    }
    LaunchedEffect(Unit) {
        viewModel.refreshServiceNamesAndAwait()
    }

    selectedKachat?.let { info ->
        AboveDock { KachatLiveNameDetailScreen(info, onBack = { selectedKachat = null }) }
        return
    }

    // A .kas domain's own profile, edited on its own (iOS 09e0403): .kas profiles are no longer
    // shown in the app, but a domain's profile is still its owner's to change.
    editingDomain?.let { domain ->
        AboveDock { KnsDomainProfileEditorScreen(domain = domain, viewModel = viewModel, onBack = { editingDomain = null }) }
        return
    }

    // Full-screen swap: domain detail (card, primary status, Send entry point) - reached by
    // tapping a card in the list below. No transfer-history section: KNS only exposes a
    // "currently owned assets" endpoint, and this app's own KNS-transfer tracking is a one-shot
    // chat notification, not a persisted per-domain log, so there's no reliable data source yet.
    selectedDomain?.let { domain ->
        if (!showSendScreen) {
            val isPrimary = domain.asset != null && domain.asset == primaryDomainName
            val settingThisOne = setPrimaryState.inFlight && setPrimaryState.assetId == domain.assetId
            AboveDock {
                KnsDomainDetailScreen(
                    domain = domain,
                    isPrimary = isPrimary,
                    settingInFlight = settingThisOne,
                    onSetPrimary = {
                        // signed with this wallet's key: the phone's lock first, like a .kas
                        // send or a profile save (iOS 2da5f4b)
                        val assetId = domain.assetId
                        if (assetId != null && !setPrimaryState.inFlight) {
                            setPrimaryAuthContext.authenticateWithDeviceCredential(
                                title = tr(R.string.kp_confirm_setting_your_primary_domain_2c693),
                                onSuccess = { viewModel.setPrimaryDomain(assetId) },
                            )
                        }
                    },
                    onSend = {
                        viewModel.resetTransferDomainState()
                        showSendScreen = true
                    },
                    onCustomizeProfile = { editingDomain = domain },
                    onBack = { selectedDomain = null }
                )
            }
            return
        } else {
            AboveDock {
                KnsDomainSendScreen(
                    domain = domain,
                    viewModel = viewModel,
                    onDone = {
                        showSendScreen = false
                        selectedDomain = null
                    },
                    onBack = { showSendScreen = false }
                )
            }
            return
        }
    }

    // The service a card opened: its own screen, titled by its ending, holding the list each
    // service's tab used to show (iOS debd6fd - these used to be tabs on this screen).
    openedService?.let { tld ->
        androidx.activity.compose.BackHandler { openedService = null }
        Scaffold(
            containerColor = LocalAppColors.current.background,
            // Runs on under the dock, as iOS's content runs on under its tab bar; each list clears
            // it (its pinned button above the dock, or its scroll's end).
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(tld.suffix, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        SheetBarBackButton(onClick = { openedService = null }, modifier = Modifier.padding(start = 12.dp))
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tld) {
                    com.kachat.app.services.NameServiceTLD.KAS -> KnsDomainListTab(
                        domains = ownedDomainAssets.filter { it.asset != null },
                        primaryDomainName = primaryDomainName,
                        onRefresh = { viewModel.refreshOwnedDomainsAndAwait() },
                        onSelect = { selectedDomain = it },
                    )
                    com.kachat.app.services.NameServiceTLD.K, com.kachat.app.services.NameServiceTLD.KASPA ->
                        ServiceNameListTab(tld = tld, viewModel = viewModel)
                    // Live on both networks (testnet-10, and mainnet since iOS ef6b21e). The
                    // "coming" note stays, unreachable.
                    com.kachat.app.services.NameServiceTLD.KACHAT ->
                        if (com.kachat.app.services.kachatnames.KachatNamesService.isEnabled) {
                            KachatLiveDomainsTab(
                                walletAddress = walletAddress ?: "",
                                onOpen = { selectedKachat = it },
                                onInscribe = { showKachatMarket = true },
                                refreshRequested = kachatRefreshRequested,
                                onRefreshHandled = { kachatRefreshRequested = false },
                            )
                        } else {
                            KachatNamesComingSoon()
                        }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        // Runs on under the dock, as iOS's scroll runs on under its tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr(R.string.kp_your_domains_94f1a), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
    ) { padding ->
        // One square card per name ending, KaChat's own first: .kachat, KNS (.kas), dotk (.k),
        // Kaspa Names (.kaspa) - the same tile look as Kaspa Hub, two across (iOS debd6fd).
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalDockInset.current),
        ) {
            items(com.kachat.app.services.NameServiceTLD.entries.size) { i ->
                val tld = com.kachat.app.services.NameServiceTLD.entries[i]
                // How many names this wallet has with each service, 0 included; null while the
                // answer is not in yet (first load, or the service unreachable) - iOS 2ee667c.
                val count = when (tld) {
                    com.kachat.app.services.NameServiceTLD.KAS -> ownedDomainAssets.count { it.asset != null }
                    com.kachat.app.services.NameServiceTLD.K, com.kachat.app.services.NameServiceTLD.KASPA -> ownedServiceNames[tld]?.size
                    com.kachat.app.services.NameServiceTLD.KACHAT -> kachatCount
                }
                DomainServiceCard(tld = tld, count = count, onClick = { openedService = tld })
            }
        }
    }
}

/**
 * A Kaspa Hub style square (iOS debd6fd `serviceCard`): the name ending big in the brand style,
 * who runs it under it. Same fill, 18 radius and 0.8 white hairline at 15% as the hub tiles.
 */
@Composable
private fun DomainServiceCard(
    tld: com.kachat.app.services.NameServiceTLD,
    /** How many names you hold there; null draws a dash until the answer is in (iOS 2ee667c). */
    count: Int?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(LocalAppColors.current.surface)
            .border(0.8.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // iOS .title2 rounded, heavy, in the accent colour; one line, shrinking to 0.6.
            KachatFitText(
                tld.suffix,
                color = KaspaTeal,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = IosRoundedFontFamily,
                minScale = 0.6f,
            )
            // iOS .caption semibold, secondary.
            Text(
                tld.serviceName,
                color = LocalAppColors.current.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // How many you have there, 0 included; a dash until the answer is in.
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .defaultMinSize(minWidth = 28.dp, minHeight = 22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(KaspaTeal.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    count?.toString() ?: "\u2013",
                    color = KaspaTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A pull-to-refresh list with the service's "Get a ... domain" button pinned under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainTabList(
    tld: com.kachat.app.services.NameServiceTLD,
    onRefresh: suspend () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            onRefresh()
            pullRefreshState.endRefresh()
        }
    }
    // With no "Get a domain" button pinned under it, the list itself clears the dock.
    val hasGetButton = tld.websiteUrl != null && tld.websiteName != null
    val dockInset = LocalDockInset.current
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(16.dp)) }
                content()
                item { Spacer(Modifier.height(if (hasGetButton) 16.dp else 16.dp + dockInset)) }
            }
            PullToRefreshContainer(state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
        }
        GetDomainButton(tld)
    }
}

/**
 * "Get a .kas domain at knsdomains.org" - the pinned button under each outside service's tab,
 * opening that service's own site (iOS 718724b). KaChat creates only its own `.kachat` names; the
 * other services' names are registered with them. The same outlined capsule the in-app Inscribe
 * button was (not the teal fill: the cards above are teal-filled).
 */
@Composable
private fun GetDomainButton(tld: com.kachat.app.services.NameServiceTLD) {
    val url = tld.websiteUrl ?: return
    val site = tld.websiteName ?: return
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Above the dock the page runs under.
            .padding(bottom = LocalDockInset.current)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(LocalAppColors.current.surface)
            .border(1.5.dp, KaspaTeal, RoundedCornerShape(28.dp))
            .clickable { uriHandler.openUriOrToast(url) }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.get_domain_at, tld.suffix, site),
            color = KaspaTeal,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun KnsDomainListTab(
    domains: List<com.kachat.app.services.KnsAsset>,
    primaryDomainName: String?,
    onRefresh: suspend () -> Unit,
    onSelect: (com.kachat.app.services.KnsAsset) -> Unit,
) {
    DomainTabList(tld = com.kachat.app.services.NameServiceTLD.KAS, onRefresh = onRefresh) {
        if (domains.isEmpty()) {
            item {
                Text(text = stringResource(R.string.no_domains_yet), color = LocalAppColors.current.textSecondary, modifier = Modifier.padding(16.dp))
            }
        } else {
            items(domains, key = { it.assetId ?: it.asset ?: it.hashCode().toString() }) { domainAsset ->
                KnsDomainCard(
                    domain = domainAsset,
                    isPrimary = domainAsset.asset == primaryDomainName,
                    modifier = Modifier.clickable { onSelect(domainAsset) }
                )
            }
        }
    }
}

@Composable
private fun ServiceNameListTab(tld: com.kachat.app.services.NameServiceTLD, viewModel: WalletViewModel) {
    val owned by viewModel.nameServices.owned.collectAsState()
    val loading by viewModel.nameServices.loading.collectAsState()
    val failed by viewModel.nameServices.failed.collectAsState()
    val names = owned[tld].orEmpty()
    DomainTabList(tld = tld, onRefresh = { viewModel.refreshServiceNamesAndAwait() }) {
        if (names.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    when {
                        tld in loading && owned[tld] == null -> com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                        tld in failed -> Text(
                            stringResource(R.string.could_not_reach_service, tld.serviceName),
                            color = LocalAppColors.current.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        else -> Text(
                            stringResource(R.string.no_names_yet, tld.suffix),
                            color = LocalAppColors.current.textSecondary,
                        )
                    }
                }
            }
        } else {
            items(names, key = { it.display }) { name ->
                DomainNameCard(
                    title = name.display,
                    badge = if (name.isProvisional) stringResource(R.string.settling) else null,
                )
            }
        }
    }
}

@Composable
private fun KachatNamesComingSoon() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp)
            .padding(bottom = LocalDockInset.current),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(44.dp))
        Text(stringResource(R.string.kachat_names_are_coming), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            stringResource(R.string.kachat_names_coming_body),
            color = LocalAppColors.current.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
        )
    }
}

/** Teal card matching the app's KNS domain branding - used both as the row style in [KnsDomainsScreen] and as the header of [KnsDomainDetailScreen]. */
@Composable
fun KnsDomainCard(domain: com.kachat.app.services.KnsAsset, isPrimary: Boolean = false, modifier: Modifier = Modifier) {
    DomainNameCard(title = domain.asset ?: "", badge = if (isPrimary) stringResource(R.string.primary) else null, modifier = modifier)
}

/** The teal name card, for any name service: the name, and an optional corner badge ("Primary"
 *  on KNS, "Settling" on a `.kaspa` name still inside its settling window) - iOS DomainNameCardView. */
@Composable
fun DomainNameCard(
    title: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    /** A small line in the bottom-right corner (Your Domains > .kachat: when it expires, iOS 72b7dc7). */
    footnote: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(KaspaTeal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (footnote != null) {
            Text(
                footnote,
                color = Color.Black.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
            )
        }
    }
}

/**
 * Detail screen for a single owned domain - the card itself, primary/status info, and a
 * dedicated Send entry point. Reached by tapping a card in [KnsDomainsScreen]. No transfer
 * history section: KNS only exposes a "currently owned assets" endpoint, and this app's own
 * KNS-transfer tracking isn't a persisted per-domain log, so there's no reliable data source yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnsDomainDetailScreen(
    domain: com.kachat.app.services.KnsAsset,
    isPrimary: Boolean,
    settingInFlight: Boolean,
    onSetPrimary: () -> Unit,
    onSend: () -> Unit,
    /** Opens the full profile editor for this domain (avatar, banner, bio, links). KaChat does not
     *  SHOW .kas profiles anymore - .kachat profiles take priority - but a .kas domain's own
     *  profile can still be customized here (iOS 09e0403). Null where the host offers no editor. */
    onCustomizeProfile: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(domain.asset ?: "", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onSend,
                    enabled = domain.assetId != null,
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, disabledContainerColor = KaspaTeal.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.send), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            KnsDomainCard(domain = domain, isPrimary = isPrimary)
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LocalAppColors.current.surface)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.asset_id), color = LocalAppColors.current.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        domain.assetId ?: "",
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                if (onCustomizeProfile != null && domain.assetId != null) {
                    HorizontalDivider(color = LocalAppColors.current.background)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onCustomizeProfile() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.customize_profile), color = LocalAppColors.current.textPrimary, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = KaspaTeal)
                    }
                }
                HorizontalDivider(color = LocalAppColors.current.background)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!isPrimary && domain.assetId != null) Modifier.clickable(enabled = !settingInFlight) { onSetPrimary() }
                            else Modifier
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isPrimary) stringResource(R.string.primary_domain) else stringResource(R.string.set_as_primary),
                        color = LocalAppColors.current.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (settingInFlight) {
                        com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (isPrimary) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = KaspaTeal
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sends (transfers) a single KNS domain inscription to a recipient address or KNS domain - same
 * UX conventions as the app's KAS send flows (KNS-domain-aware recipient, editable network fee)
 * but with no amount field or coin control, since a domain transfer moves the whole inscription
 * rather than a chosen KAS amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnsDomainSendScreen(
    domain: com.kachat.app.services.KnsAsset,
    viewModel: WalletViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** Non-null: sign/fund the transfer from this spending-chain address index instead of the
     *  identity address (the spending-address detail screen's KNS Domains tab passes it). */
    fromSpendingAddressIndex: Int? = null,
    /** The source address matching [fromSpendingAddressIndex] — recipient validation checks the
     *  recipient differs from the SOURCE, not necessarily from the identity address. */
    sourceAddress: String? = null
) {
    val recipientPreview by viewModel.transferRecipientPreview.collectAsState()
    val transferState by viewModel.transferDomainState.collectAsState()
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    val uriHandler = LocalUriHandler.current
    val deviceAuthContext = LocalContext.current

    var recipientInput by remember { mutableStateOf("") }
    var feeTier by remember { mutableStateOf(ColdFeeTier.NORMAL) }
    var customFeeSompi by remember { mutableStateOf<Long?>(null) }
    var isEditingFee by remember { mutableStateOf(false) }
    var customFeeText by remember { mutableStateOf("") }

    val domainName = domain.asset ?: ""
    val assetId = domain.assetId ?: ""
    val baseFeeSompi = KnsInscriptionEngine.REVEAL_PRIORITY_FEE_SOMPI
    val priorityFeeSompi = customFeeSompi ?: (baseFeeSompi * feeTier.multiplier)
    val inFlight = transferState.status !in listOf(
        WalletViewModel.KnsInscribeUiStatus.IDLE,
        WalletViewModel.KnsInscribeUiStatus.SUCCESS,
        WalletViewModel.KnsInscribeUiStatus.FAILED
    )

    // Progress in its own sheet rather than a spinner inside the Send button: a transfer is a
    // commit, a wait, then a reveal, and a button that just spins through all of it is
    // indistinguishable from being stuck. Not dismissible - leaving mid-transfer would hide a
    // running on-chain operation.
    if (inFlight) {
        DomainTransferProgressSheet(domainName = domainName, status = transferState.status)
    }
    val canSend = !inFlight && recipientPreview?.resolvedAddress != null

    fun trimmedKas(sompi: Long): String {
        var text = "%.8f".format(sompi / 100_000_000.0)
        while (text.endsWith("0")) text = text.dropLast(1)
        if (text.endsWith(".")) text = text.dropLast(1)
        return text
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.send_domain), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarIconButton(Icons.Filled.ArrowBackIosNew, tr(R.string.kp_back_b52b3), onClick = onBack, enabled = !inFlight, modifier = Modifier.padding(start = 12.dp), tint = LocalAppColors.current.textPrimary)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
        bottomBar = {
            if (transferState.status != WalletViewModel.KnsInscribeUiStatus.SUCCESS) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            // Giving a domain away can't be undone, so the phone's lock comes first.
                            // Declining leaves the form as it was (iOS 953b255).
                            deviceAuthContext.authenticateWithDeviceCredential(
                                title = tr(R.string.kp_confirm_sending_this_domain_4afc7),
                                onSuccess = { viewModel.transferDomain(domainName, assetId, priorityFeeSompi, fromSpendingAddressIndex) },
                            )
                        },
                        enabled = canSend,
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, disabledContainerColor = KaspaTeal.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (inFlight) {
                            com.kachat.app.ui.theme.IosActivityIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.send), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (transferState.status == WalletViewModel.KnsInscribeUiStatus.SUCCESS) {
                val result = transferState.result
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = LocalAppColors.current.success, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sent), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(20.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LocalAppColors.current.surface)
                            .padding(16.dp)
                    ) {
                        Text(stringResource(R.string.to), color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                        Text(result?.toAddress ?: "", color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LocalAppColors.current.surface)
                            .clickable { result?.revealTxId?.let { uriHandler.openUriOrToast(kaspaExplorer.txUrl(it)) } }
                            .padding(16.dp)
                    ) {
                        Text("Transaction ID · tap to view in ${kaspaExplorer.displayName}", color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                        Text(result?.revealTxId ?: "", color = KaspaTeal, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(stringResource(R.string.done), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LocalAppColors.current.surface)
                    .padding(16.dp)
            ) {
                Text(domainName, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                Text(assetId, color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }

            when (transferState.status) {
                WalletViewModel.KnsInscribeUiStatus.SUBMITTING_COMMIT -> InscribeProgressRow(stringResource(R.string.submitting_commit_transaction))
                WalletViewModel.KnsInscribeUiStatus.SUBMITTING_REVEAL -> InscribeProgressRow(stringResource(R.string.submitting_reveal_transaction))
                WalletViewModel.KnsInscribeUiStatus.VERIFYING -> InscribeProgressRow(stringResource(R.string.verifying_new_ownership_on_chain_this))
                WalletViewModel.KnsInscribeUiStatus.FAILED -> {
                    Text(transferState.errorMessage ?: stringResource(R.string.something_went_wrong), color = LocalAppColors.current.danger)
                }
                else -> {
                    Text(stringResource(R.string.recipient_address).uppercase(), color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    com.kachat.app.ui.theme.IosTextField(
                        value = recipientInput,
                        onValueChange = {
                            recipientInput = it
                            viewModel.checkTransferRecipient(it, sourceAddress)
                        },
                        placeholder = { Text(stringResource(R.string.kaspa_qr_or_domain)) },
                        singleLine = true,
                        enabled = !inFlight,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LocalAppColors.current.textPrimary,
                            unfocusedTextColor = LocalAppColors.current.textPrimary,
                            focusedBorderColor = KaspaTeal,
                            unfocusedBorderColor = LocalAppColors.current.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    recipientPreview?.let { preview ->
                        Spacer(Modifier.height(4.dp))
                        // the shared card: the domain typed, else the address's own .kachat name
                        AddressResolutionCard(address = preview.resolvedAddress, domain = preview.resolvedName)
                        when {
                            preview.checking -> Text(stringResource(R.string.looking_up_domain), color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                            preview.errorMessage != null -> Text(preview.errorMessage, color = LocalAppColors.current.danger, style = MaterialTheme.typography.bodySmall)
                            preview.resolvedAddress != null && preview.resolvedName != null -> Column {
                                Text(stringResource(R.string.resolved_name, preview.resolvedName), color = LocalAppColors.current.success, style = MaterialTheme.typography.bodySmall)
                                MiddleEllipsisText(
                                    preview.resolvedAddress,
                                    color = LocalAppColors.current.textSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                )
                            }
                            preview.resolvedAddress != null -> Text("Resolves to: ${preview.resolvedAddress}", color = LocalAppColors.current.success, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!preview.checking) {
                            OtherDomainsDropdown(preview.nameResolutions, preview.selectedTld) {
                                viewModel.selectTransferResolution(it, sourceAddress)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.enter_kaspa_address_or_domain_short_kachat_first),
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("FEE", color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LocalAppColors.current.surface)
                    ) {
                        ColdFeeTier.entries.forEach { tier ->
                            val selected = feeTier == tier && customFeeSompi == null
                            Text(
                                tier.label,
                                color = if (selected) Color.Black else LocalAppColors.current.textSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) KaspaTeal else Color.Transparent)
                                    .clickable(enabled = !inFlight) {
                                        feeTier = tier
                                        customFeeSompi = null
                                        isEditingFee = false
                                    }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.network_fee), color = LocalAppColors.current.textPrimary, modifier = Modifier.weight(1f))
                        if (isEditingFee) {
                            com.kachat.app.ui.theme.IosTextField(
                                value = customFeeText,
                                onValueChange = { customFeeText = it },
                                singleLine = true,
                                modifier = Modifier.width(110.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LocalAppColors.current.textPrimary,
                                    unfocusedTextColor = LocalAppColors.current.textPrimary
                                )
                            )
                            IconButton(onClick = {
                                KaspaUnit.sompiFromUserText(customFeeText)?.let { sompi ->
                                    customFeeSompi = sompi
                                }
                                isEditingFee = false
                            }) {
                                Icon(Icons.Default.Check, null, tint = KaspaTeal)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(enabled = !inFlight) {
                                    customFeeText = trimmedKas(priorityFeeSompi)
                                    isEditingFee = true
                                }
                            ) {
                                Text("${trimmedKas(priorityFeeSompi)} ${KaspaUnit.symbol}", color = KaspaTeal, textDecoration = TextDecoration.Underline)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Edit, null, tint = KaspaTeal, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    // iOS KNSDomainSendView's Fee section footer.
                    Text(
                        tr(R.string.kp_if_the_network_is_busy_fast_9cd2d),
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

        }
    }
}

/**
 * Every address of this account (iOS 6333dd3 ManageAddressesView), reached from Profile > Manage
 * Addresses: the chatting address first, then the spending addresses, each with its balance and
 * whether it was used, a "generate new address" action, rename, QR, hide. Which address a send
 * comes from is picked on the send screen itself (From), so there is no "primary" here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAddressesScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onNavigateToTxHistory: (Int) -> Unit = {},
    /** The chatting address's own screen (history, UTXOs, Domains, Send / Receive). */
    onOpenChattingAddress: () -> Unit = {},
    onNavigateToHidden: () -> Unit = {},
    onNavigateToVisibility: () -> Unit = {},
    onAddressPicked: ((com.kachat.app.services.WalletService.SpendingAddressEntry) -> Unit)? = null,
    settingsViewModel: com.kachat.app.viewmodels.SettingsViewModel = hiltViewModel(),
) {
    val addresses by viewModel.manageAddresses.collectAsState()
    val chattingAddress by viewModel.address.collectAsState()
    val chattingBalanceSompi by viewModel.balanceSompi.collectAsState()
    val loading by viewModel.manageAddressesLoading.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var qrAddress by remember { mutableStateOf<String?>(null) }
    var renamingEntry by remember { mutableStateOf<com.kachat.app.services.WalletService.SpendingAddressEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showActionsMenu by remember { mutableStateOf(false) }
    val isDiscoveringAddresses by viewModel.discoveringAddresses.collectAsState()
    val discoveryProgress by viewModel.spendingDiscoveryProgress.collectAsState()
    var discoverySummary by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()

    val domainOwningAddresses by viewModel.domainOwningAddresses.collectAsState()
    // Quantum-safe rows whose key a spend has put on chain (their shield is off for good).
    val quantumSpentAddresses by viewModel.quantumSpentAddresses.collectAsState()
    // The spending addresses under the Chatting Address: unused ones first, newest first - so a
    // freshly generated address lands right under the Chatting Address - then the used ones in
    // number order (iOS wallet 59593af). One still being checked counts as unused until its
    // check says otherwise.
    val visibleAddresses = remember(addresses) {
        fun used(a: com.kachat.app.services.WalletService.SpendingAddressEntry) = a.everUsed || a.balanceSompi > 0
        addresses.filterNot { it.hidden }.sortedWith { a, b ->
            val aUsed = used(a)
            val bUsed = used(b)
            when {
                aUsed != bUsed -> if (aUsed) 1 else -1
                aUsed -> a.index.compareTo(b.index)
                else -> b.index.compareTo(a.index)
            }
        }
    }
    val hiddenAddresses = remember(addresses) { addresses.filter { it.hidden } }

    LaunchedEffect(Unit) {
        viewModel.loadManageAddresses()
    }

    // Pull-to-refresh owns its spinner end-to-end (same pattern as the Profile and Domains
    // screens): await the refresh, then dismiss. NEVER keyed off manageAddressesLoading — that
    // flag only flips on the empty-list initial load (a warm pull leaves it false the whole
    // time, so a spinner waiting on it spun forever) and is also driven by background reloads,
    // which must not surface or dismiss the pull indicator.
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.loadManageAddressesAndAwait()
            pullRefreshState.endRefresh()
        }
    }

    val dockInset = LocalDockInset.current
    Scaffold(
        containerColor = LocalAppColors.current.background,
        // Runs on under the dock, as iOS's list runs on under its tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.manage_addresses), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                // Address Visibility used to be an unlabelled checklist glyph here. It is one
                // of this account's address actions, so it lives with the others in the Address
                // Actions sheet, where it has room to say what it does.
                actions = {},
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
        // The floating glass button behind "Address Actions" (iOS 953b255 `addressActionsButton`),
        // above the dock the list runs under.
        floatingActionButton = {
            if (onAddressPicked == null) {
                Box(Modifier.padding(bottom = dockInset)) {
                    com.kachat.app.ui.theme.IosGlassFab(
                        onClick = { if (!isDiscoveringAddresses) showActionsMenu = true },
                        icon = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.address_actions),
                    )
                    if (isDiscoveringAddresses) {
                        // The spinner in place of the symbol while it runs.
                        Box(Modifier.matchParentSize().clip(CircleShape).background(LocalAppColors.current.surface), contentAlignment = Alignment.Center) {
                            com.kachat.app.ui.theme.IosActivityIndicator(strokeWidth = 2.dp, color = KaspaTeal, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        },
    ) { padding ->
        // Half sheet, opened by the floating button - see ManageAddressesActionsSheet for the
        // discovery readout.
        if (showActionsMenu) {
            ManageAddressesActionsSheet(
                isDiscovering = isDiscoveringAddresses,
                progress = discoveryProgress,
                summary = discoverySummary,
                onGenerate = {
                    showActionsMenu = false
                    viewModel.generateNewSpendingAddress { index ->
                        // The chain-local number, never the raw public index (1000000+ for a quantum slot).
                        if (index != null) IosToasts.show("${com.kachat.app.services.WalletManager.spendingAddressTitle(index)} is ready.")
                        else IosToasts.error("Could not check addresses. Try again when connected.")
                    }
                },
                onDiscover = {
                    discoverySummary = null
                    viewModel.discoverSpendingAddresses { count ->
                        // Found addresses count toward the whole hot wallet (iOS 953b255).
                        viewModel.refreshHotWallet()
                        // The count is addresses that hold a balance or a KNS domain - say so,
                        // rather than "used", which is what the old number implied and was not.
                        val summary = if (count == 0) "No addresses with a balance or domain found."
                        else "Found $count address${if (count == 1) "" else "es"} with a balance or domain."
                        discoverySummary = summary
                        // Closed the sheet and carried on? Then the summary above has nowhere to
                        // render, so say it here instead of finishing silently.
                        if (!showActionsMenu) IosToasts.show(summary)
                    }
                },
                onVisibility = { showActionsMenu = false; onNavigateToVisibility() },
                onDismiss = { showActionsMenu = false; discoverySummary = null },
            )
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Same fix as PortfolioScreen: an idle PullToRefreshContainer "hides" by
                // translating one full container-height above its own position via graphicsLayer,
                // and graphicsLayer translation does NOT clip. On screens whose pull Box is the
                // first child under the Scaffold padding, that resting gray circle lands in the
                // top-app-bar band and the opaque app bar draws over it. clipToBounds keeps the
                // indicator inside this Box: invisible at rest, revealed by a real pull.
                .clipToBounds()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
        // iOS `addressesTabContent`: a plain List on the screen's own background - the account
        // summary edge to edge (20 in), the Notify switch as a plain row, then the chatting
        // address and the spending addresses as glass cards 16 in, 6 above and below each.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            // The total is everything this account holds: the chatting address and every spending
            // address, hidden ones too (iOS 245a5d2). The actions are the floating button (iOS
            // 953b255). No card: iOS `accountSummary` sits straight on the list.
            if (onAddressPicked == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(stringResource(R.string.total_balance), color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                        Text(
                            "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, (chattingBalanceSompi + addresses.sumOf { it.balanceSompi }) / 100_000_000.0),
                            color = LocalAppColors.current.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                }
            }
            run {
                // This account's own switch (a cold-storage account has one in its own menu):
                // the spending addresses' receives, not every address the wallet watches
                // (iOS ba352a2). A plain list row under the summary, no card.
                item {
                    val spendingNotify by settingsViewModel.spendingReceiveNotifications.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                tr(R.string.kp_notify_on_receive_2b2ee),
                                color = LocalAppColors.current.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Text(
                                tr(R.string.kp_when_a_spending_address_receives_kaspa_e64ac),
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        com.kachat.app.ui.theme.IosSwitch(
                            checked = spendingNotify,
                            onCheckedChange = { settingsViewModel.setSpendingReceiveNotifications(it) }
                        )
                    }
                }
            }
            // The chatting address, first in the list: it opens its own screen (history, UTXOs,
            // Domains, Send / Receive), the same one Profile used to.
            val chatting = chattingAddress
            if (onAddressPicked == null && chatting != null) {
                item {
                    ManageChattingAddressRow(
                        address = chatting,
                        balanceSompi = chattingBalanceSompi,
                        onClick = onOpenChattingAddress,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            if (onAddressPicked != null) {
                item {
                    Text(
                        stringResource(R.string.tap_an_address_below_to_swap),
                        color = KaspaTeal,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
            // The Hidden (n) sub-screen survives only for Swap's address picker — the normal
            // screen manages visibility through the checklist button instead (iOS parity).
            if (hiddenAddresses.isNotEmpty() && onAddressPicked != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToHidden)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VisibilityOff, null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Hidden (${hiddenAddresses.size})",
                            color = LocalAppColors.current.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = LocalAppColors.current.textSecondary)
                    }
                }
            }

            if (loading && addresses.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                        com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                    }
                }
            } else if (visibleAddresses.isEmpty()) {
                // iOS `emptyState`.
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.CreditCard, null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(40.dp))
                        Text(tr(R.string.kp_no_spending_addresses_4440e), color = LocalAppColors.current.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            tr(R.string.kp_generate_one_below_to_send_payments_7fc3d),
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(visibleAddresses, key = { it.index }) { entry ->
                    ManageAddressRow(
                        entry = entry,
                        showsDomainTag = entry.address in domainOwningAddresses,
                        quantumSpent = entry.address.lowercase() in quantumSpentAddresses,
                        onClick = { if (onAddressPicked != null) onAddressPicked(entry) else onNavigateToTxHistory(entry.index) },
                        onCopyClick = {
                            clipboardManager.setText(AnnotatedString(entry.address))
                            showAddressCopiedToast(context, entry.address)
                        },
                        onQrClick = { qrAddress = entry.address },
                        onRenameClick = { renamingEntry = entry; renameInput = entry.label ?: "" },
                        onHideClick = {
                            // iOS hideAddress: any address, funded too - it comes back from
                            // Address Visibility (iOS 245a5d2).
                            viewModel.setManageAddressHidden(entry.index, true) { ok ->
                                if (ok) IosToasts.show("Address hidden. Re-enable it in Address Visibility.")
                                else IosToasts.show("This address can't be hidden.")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            item {
                // The last address scrolls clear of the dock the list runs under and out from under
                // the floating button (iOS 953b255).
                Spacer(Modifier.height(72.dp + dockInset))
            }
        }

        // Hard guarantee on top of the clipToBounds above: the indicator is only ever composed
        // while a pull is actually in progress or a refresh is running. At rest (offset 0, not
        // refreshing) there is nothing to draw, so no layout quirk can park the gray circle
        // over the content again.
        if (pullRefreshState.verticalOffset > 0f || pullRefreshState.isRefreshing) {
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        qrAddress?.let { address ->
            QrCodeOverlay(value = address, onDismiss = { qrAddress = null })
        }
        }
        }
    }

    renamingEntry?.let { entry ->
        RenameAddressDialog(
            index = entry.index,
            nameInput = renameInput,
            onNameChange = { renameInput = it },
            onDismiss = { renamingEntry = null },
            onSave = {
                viewModel.setManageAddressLabel(entry.index, renameInput)
                renamingEntry = null
            }
        )
    }
}

/** Rename dialog shared by [ManageAddressesScreen] and [ManageAddressesHiddenScreen] — an empty/blank name clears back to the default "Address #N". */
@Composable
private fun RenameAddressDialog(
    index: Int,
    nameInput: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    com.kachat.app.ui.theme.IosAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppColors.current.surface,
        title = { Text(stringResource(R.string.rename_address), color = LocalAppColors.current.textPrimary) },
        text = {
            Column {
                // iOS's alert message (ManageAddressesView "Rename Address"), and its "Label" field.
                Text(tr(R.string.kp_give_this_address_a_label_to_32e1b), color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                com.kachat.app.ui.theme.IosTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    label = { Text(tr(R.string.kp_label_74341)) },
                    placeholder = { Text(com.kachat.app.services.WalletManager.spendingAddressTitle(index)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary,
                        unfocusedTextColor = LocalAppColors.current.textPrimary,
                        focusedBorderColor = KaspaTeal,
                        unfocusedBorderColor = LocalAppColors.current.textSecondary,
                        focusedLabelColor = KaspaTeal,
                        unfocusedLabelColor = LocalAppColors.current.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
            }
        }
    )
}

/**
 * Every hidden spending address, reached via the "Hidden (N)" link on [ManageAddressesScreen] —
 * the only place a hidden address can be unhidden again. Shares [viewModel]'s own `manageAddresses`
 * list rather than loading a separate one, so it's always in sync with the main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAddressesHiddenScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onNavigateToTxHistory: (Int) -> Unit = {},
    onAddressPicked: ((com.kachat.app.services.WalletService.SpendingAddressEntry) -> Unit)? = null
) {
    val addresses by viewModel.manageAddresses.collectAsState()
    val quantumSpentAddresses by viewModel.quantumSpentAddresses.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var qrAddress by remember { mutableStateOf<String?>(null) }
    var renamingEntry by remember { mutableStateOf<com.kachat.app.services.WalletService.SpendingAddressEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val hiddenAddresses = remember(addresses) {
        addresses.filter { it.hidden }
            .sortedWith(compareByDescending<com.kachat.app.services.WalletService.SpendingAddressEntry> { it.balanceSompi > 0 }.thenByDescending { it.index })
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.hidden_addresses), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { padding ->
        if (hiddenAddresses.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.VisibilityOff, null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.no_hidden_addresses), color = LocalAppColors.current.textSecondary, textAlign = TextAlign.Center)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onAddressPicked != null) {
                        item {
                            Text(
                                stringResource(R.string.tap_an_address_below_to_use),
                                color = KaspaTeal,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    items(hiddenAddresses, key = { it.index }) { entry ->
                        ManageAddressRow(
                            entry = entry,
                            onClick = { if (onAddressPicked != null) onAddressPicked(entry) else onNavigateToTxHistory(entry.index) },
                            onCopyClick = {
                                clipboardManager.setText(AnnotatedString(entry.address))
                                showAddressCopiedToast(context, entry.address)
                            },
                            onQrClick = { qrAddress = entry.address },
                            onRenameClick = { renamingEntry = entry; renameInput = entry.label ?: "" },
                            quantumSpent = entry.address.lowercase() in quantumSpentAddresses,
                        )
                    }
                }
                qrAddress?.let { address ->
                    QrCodeOverlay(value = address, onDismiss = { qrAddress = null })
                }
            }
        }
    }

    renamingEntry?.let { entry ->
        RenameAddressDialog(
            index = entry.index,
            nameInput = renameInput,
            onNameChange = { renameInput = it },
            onDismiss = { renamingEntry = null },
            onSave = {
                viewModel.setManageAddressLabel(entry.index, renameInput)
                renamingEntry = null
            }
        )
    }
}

/**
 * Address Visibility (iOS parity: SpendingAddressVisibilityView, d7e6dd1): first a card per
 * chain - Quantum Addresses and Non-Quantum Addresses - then that chain's checklist
 * ([AddressVisibilityChainList]). One pager over raw indices could never reach the quantum chain,
 * which starts at [com.kachat.app.services.WalletManager.QUANTUM_INDEX_BASE]. Hide All
 * (confirmed) covers both chains and starts the list over with only the chatting address
 * (iOS 245a5d2). The cards and the list read the same loaded rows, so a toggle updates the counts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressVisibilityScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit
) {
    val addresses by viewModel.manageAddresses.collectAsState()
    val chattingBalanceSompi by viewModel.balanceSompi.collectAsState()
    val loading by viewModel.manageAddressesLoading.collectAsState()
    var showHideAllConfirm by remember { mutableStateOf(false) }
    // The chain whose card was tapped (true = quantum), null on the cards themselves.
    var openedChain by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Boolean?>(null) }

    // Refresh on entry: rows inherited from a previous screen visit can carry a stale
    // isCurrent/balance (the primary rotates after every send), and the toggle guards in the
    // checklist read those flags. The live reload also runs the self-heal purge for any wrongly
    // hidden primary/funded index.
    LaunchedEffect(Unit) { viewModel.loadManageAddresses() }

    openedChain?.let { quantum ->
        androidx.activity.compose.BackHandler { openedChain = null }
        AddressVisibilityChainList(viewModel = viewModel, quantum = quantum, onBack = { openedChain = null })
        return
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr(R.string.kp_address_visibility_9c9a7), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    // Hide All leads the bar, Done trails it (iOS 245a5d2).
                    SheetBarButton(stringResource(R.string.hide_all), onClick = { showHideAllConfirm = true }, modifier = Modifier.padding(start = 12.dp))
                },
                actions = {
                    SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { padding ->
        // Two Kaspa Hub style squares, quantum first (iOS d7e6dd1).
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            items(2) { i ->
                val quantum = i == 0
                // "n of m shown": m = this chain's revealed addresses (every row the loaded list
                // holds for it), n = those Manage Addresses shows (not hidden).
                val chain = addresses.filter { it.isQuantum == quantum }
                // The Chatting Address is the legacy list's always-shown #0 (iOS 953b255).
                val chattingRows = if (quantum) 0 else 1
                AddressVisibilityChainCard(
                    quantum = quantum,
                    shown = chain.count { !it.hidden } + chattingRows,
                    total = chain.size + chattingRows,
                    // Hidden addresses count too: their Kaspa is still in the account's total.
                    balanceSompi = (if (quantum) 0L else chattingBalanceSompi) + chain.sumOf { it.balanceSompi },
                    loading = loading,
                    onClick = { openedChain = quantum }
                )
            }
        }
    }

    if (showHideAllConfirm) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showHideAllConfirm = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.hide_all_addresses_title), color = LocalAppColors.current.textPrimary) },
            text = { Text(stringResource(R.string.hide_all_addresses_message), color = LocalAppColors.current.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showHideAllConfirm = false
                    // Every revealed address hidden, both chains.
                    viewModel.hideAllSpendingAddresses()
                }) {
                    Text(stringResource(R.string.hide_all), color = LocalAppColors.current.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideAllConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }
}

/**
 * A Kaspa Hub style square (iOS d7e6dd1 `chainCard`): the chain's glyph, its name, how many of
 * its addresses Manage Addresses shows and what they hold (iOS 953b255). Same fill, 18 radius and
 * 0.8 white hairline at 15% as the hub tiles and the Your Domains cards.
 */
@Composable
private fun AddressVisibilityChainCard(
    quantum: Boolean,
    shown: Int,
    total: Int,
    balanceSompi: Long,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(LocalAppColors.current.surface)
            .border(0.8.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (quantum) {
                // "shield.fill"
                Icon(Icons.Filled.Shield, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(26.dp))
            } else {
                // "circle.grid.2x2.fill": four dots in a square.
                androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                    val r = size.minDimension * 0.2f
                    for (cx in listOf(0.27f, 0.73f)) for (cy in listOf(0.27f, 0.73f)) {
                        drawCircle(KaspaTeal, radius = r, center = Offset(size.width * cx, size.height * cy))
                    }
                }
            }
            // iOS .subheadline semibold, primary, up to two lines.
            Text(
                stringResource(if (quantum) R.string.quantum_addresses else R.string.non_quantum_addresses),
                color = LocalAppColors.current.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (loading) {
                com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(16.dp))
            } else {
                // iOS .caption, secondary.
                Text(
                    stringResource(R.string.addresses_shown_count, shown, total),
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                // iOS .caption semibold, accent once it holds anything.
                KachatFitText(
                    KaspaUnit.amount(balanceSompi),
                    color = if (balanceSompi > 0) KaspaTeal else LocalAppColors.current.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    minScale = 0.7f,
                )
            }
        }
    }
}

/**
 * One chain's Address Visibility checklist (iOS d7e6dd1 `AddressVisibilityChainList`) — a compact
 * checkmark list of EVERY address on that chain, paged 50 at a time from the chain's own start
 * (the quantum chain at [com.kachat.app.services.WalletManager.QUANTUM_INDEX_BASE]), numbered by
 * [com.kachat.app.services.WalletManager.addressNumber] with the Chatting Address as the legacy
 * chain's #0, so dozens can be toggled off the main Manage Addresses list in one sitting. The right
 * arrow never runs out: future pages derive addresses beyond the chain's revealed bound on the
 * fly, and toggling one on raises the bound while keeping the intermediate indices hidden. Every
 * address toggles both ways, funded ones too (iOS 245a5d2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressVisibilityChainList(
    viewModel: WalletViewModel,
    quantum: Boolean,
    onBack: () -> Unit
) {
    val addresses by viewModel.manageAddresses.collectAsState()
    var page by remember { mutableStateOf(0) }
    val pageSize = 50
    val byIndex = remember(addresses) { addresses.associateBy { it.index } }
    // The legacy bound: a quantum row's index sits a million up and must not stretch it.
    val listMax = remember(addresses) { addresses.filterNot { it.isQuantum }.maxOfOrNull { it.index } ?: -1 }
    // This chain's own revealed bound - past it, rows are derived on the fly.
    val chainMax = if (quantum) {
        addresses.filter { it.isQuantum }.maxOfOrNull { it.index }
            ?: (com.kachat.app.services.WalletManager.QUANTUM_INDEX_BASE - 1)
    } else {
        listMax
    }
    // Lazily filled Used/Unused results for rows derived beyond the loaded list.
    val usedCache = remember { mutableStateMapOf<Int, Boolean>() }

    // Raw indices for the rows.
    val localStart = page * pageSize
    val localEnd = localStart + pageSize - 1
    val chainBase = if (quantum) com.kachat.app.services.WalletManager.QUANTUM_INDEX_BASE else 0
    val start = chainBase + localStart
    val end = chainBase + localEnd
    // The numbers on the current page. The legacy chain's first page also holds #0, the Chatting
    // Address (iOS 953b255 `pagerRange`).
    val pagerLower = if (!quantum && page == 0) 0 else com.kachat.app.services.WalletManager.addressNumber(start)
    val pagerUpper = com.kachat.app.services.WalletManager.addressNumber(end)
    val chattingAddress by viewModel.address.collectAsState()
    val chattingBalanceSompi by viewModel.balanceSompi.collectAsState()
    // Addresses for rows the loaded list does not cover, derived OFF the main thread and once
    // per page.
    //
    // This used to call `spendingAddressAt(index)` for each of the 50 rows from inside a
    // `remember` during composition. Every one of those redoes the BIP-39 seed derivation -
    // PBKDF2, 2048 rounds of HMAC-SHA512, deliberately slow - so a page flip did that fifty times
    // on the main thread before it could draw. That is what made this screen unresponsive enough
    // that a row could not be tapped. `deriveSpendingAddresses` pays the seed cost once for the
    // whole range, and it happens in a coroutine on the default dispatcher.
    var derivedAddresses by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    // The page whose derivation has finished: until then its beyond-list rows show "deriving...";
    // after, a row that still has no address is left out (iOS rebuildPage's compactMap).
    var derivedPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(page, chainMax) {
        val needed = (start..end).filter { it > chainMax }
        if (needed.isEmpty()) {
            derivedAddresses = emptyMap()
            derivedPage = page
            return@LaunchedEffect
        }
        derivedPage = null
        derivedAddresses = withContext(Dispatchers.Default) {
            viewModel.spendingAddressesAt(needed)
        }
        derivedPage = page
    }
    // One sweep per page for the Used badges on derived rows, paced so a page flip is a trickle
    // rather than fifty simultaneous history probes. Cache-first: an address already known used
    // never asks the network again (used-ness is monotonic).
    LaunchedEffect(page, derivedAddresses) {
        for ((index, address) in derivedAddresses) {
            if (address.isEmpty() || index in usedCache) continue
            usedCache[index] = viewModel.hasSpendingAddressBeenUsed(address)
            delay(60)
        }
    }
    val pageEntries = remember(byIndex, page, derivedAddresses, derivedPage) {
        (start..end).mapNotNull { index ->
            if (index !in byIndex && derivedPage == page && derivedAddresses[index].isNullOrEmpty()) return@mapNotNull null
            byIndex[index] ?: com.kachat.app.services.WalletService.SpendingAddressEntry(
                index = index,
                address = derivedAddresses[index].orEmpty(),
                balanceSompi = 0L,
                everUsed = false,
                isCurrent = false,
                hidden = true,
                label = null
            )
        }
    }
    val listState = rememberLazyListState()
    // A page flip must land the user at the TOP of the new page, not wherever the previous
    // page left the scroll offset.
    LaunchedEffect(page) { listState.scrollToItem(0) }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(if (quantum) R.string.quantum_addresses else R.string.non_quantum_addresses),
                        color = LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                // Hide All and Done stay on the cards (iOS d7e6dd1); this list only goes back.
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalAppColors.current.background)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { if (page > 0) page -= 1 }, enabled = page > 0) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        "Previous page",
                        tint = if (page > 0) KaspaTeal else LocalAppColors.current.textSecondary.copy(alpha = 0.4f)
                    )
                }
                Text(
                    "#$pagerLower - #$pagerUpper",
                    color = LocalAppColors.current.textSecondary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = { page += 1 }) {
                    Icon(Icons.Default.ChevronRight, "Next page", tint = KaspaTeal)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // #0: always shown on Manage Addresses, so it's checked and tapping it does nothing
            // (iOS 953b255 `chattingRow`).
            val chatting = chattingAddress
            if (!quantum && page == 0 && chatting != null) {
                item(key = "chatting") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LocalAppColors.current.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KaspaTeal.copy(alpha = 0.45f))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("#0", color = LocalAppColors.current.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.chatting_address), color = LocalAppColors.current.textSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Text(
                                "${chatting.take(14)}...${chatting.takeLast(6)}",
                                color = LocalAppColors.current.textPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                        if (chattingBalanceSompi > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "%.4f ${KaspaUnit.symbol}".format(java.util.Locale.US, chattingBalanceSompi / 100_000_000.0),
                                color = KaspaTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            if (pageEntries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.address_visibility_derive_failed),
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(pageEntries, key = { it.index }) { entry ->
                val funded = entry.balanceSompi > 0
                // Every row toggles both ways - funded ones too (Hide All starts the list over).
                // A revealed quantum row sits a million past the legacy listMax, so it shows and
                // toggles by its own hidden flag (iOS ManageAddressesView's row: visible =
                // !hidden); only a slot the list doesn't hold yet is revealed instead.
                val listedQuantum = entry.isQuantum && entry.index in byIndex
                val visible = (listedQuantum || entry.index <= listMax) && !entry.hidden
                // Used-state comes from the page's own sweep below, not a per-row effect: fifty
                // rows each launching their own history probe fired fifty requests the moment a
                // page appeared, which is the other half of what made this screen unusable.
                val used = if (listedQuantum || entry.index <= listMax) entry.everUsed else usedCache[entry.index]
                // The WHOLE row toggles, not just the checkmark.
                val toggleVisibility: () -> Unit = {
                    when {
                        listedQuantum ->
                            viewModel.setManageAddressHidden(entry.index, !entry.hidden)
                        entry.index > listMax ->
                            viewModel.revealSpendingAddress(entry.index)
                        else -> viewModel.setManageAddressHidden(entry.index, !entry.hidden)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalAppColors.current.surface)
                        .clickable(onClick = toggleVisibility)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .alpha(if (visible) 1f else 0.55f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = toggleVisibility,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (visible) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            if (visible) "Visible" else "Hidden",
                            tint = if (visible) KaspaTeal else LocalAppColors.current.textSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // The chain-local number; the shield marks the quantum chain's rows
                            // apart from the legacy chain's same numbers (iOS 0b4e27e).
                            Text(
                                "#${com.kachat.app.services.WalletManager.addressNumber(entry.index)}",
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (entry.isQuantum) {
                                // iOS: SF Symbol "shield" (outline) at .caption2, secondary.
                                Icon(
                                    Icons.Outlined.Shield,
                                    contentDescription = stringResource(R.string.quantum_safe),
                                    tint = LocalAppColors.current.textSecondary,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                            entry.label?.takeIf { it.isNotBlank() }?.let { label ->
                                Text(label, color = LocalAppColors.current.textSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        Text(
                            if (entry.address.isNotEmpty()) "${entry.address.take(14)}...${entry.address.takeLast(6)}" else "deriving...",
                            color = LocalAppColors.current.textPrimary,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        funded -> Text(
                            "%.4f ${KaspaUnit.symbol}".format(java.util.Locale.US, entry.balanceSompi / 100_000_000.0),
                            color = KaspaTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        used != null -> Text(
                            if (used) tr(R.string.kp_used_02c0e) else tr(R.string.kp_unused_41369),
                            color = if (used) LocalAppColors.current.warning else LocalAppColors.current.success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * The steps of [SpendingAddressSendFlow] (iOS 05db583 `WithdrawKaspaView.Step`), in order: who it
 * goes to, which of your addresses it leaves from, how much (on the app's own keypad), the review
 * where only the fee and coin control can change before swiping up, and the review's fee editor.
 * The order is the slide's direction: a later step comes in from the right.
 */
private enum class SpendingSendStep { RECIPIENT, FROM, AMOUNT, REVIEW, FEE }

/**
 * Send Kaspa (iOS `WithdrawKaspaView`): the Wallet tab's Send, a contact's Send, an
 * address's Send, paying a payment request - and, in [isCompoundMode], Compound UTXOs for one
 * address. Every send is steps (iOS 05db583): To ("Who are you sending to?"), From ("Where are
 * you sending from?": the Chatting Address, or Choose an Address with your addresses listed on the
 * screen - skipped when [lockedSource] fixes it), the amount on the app's own keypad
 * ([KaspaKeypadAmountEntry], no system keyboard), then the review with swipe up: the amount, To,
 * From and Total, where only the fee and coin control can change ([SendFeeControls]; a Max amount
 * is worked out again when they do). The fee's Edit opens [SendFeeEditorScreen]; its Confirm comes
 * back to the review. A pre-filled recipient or a payment request opens on From (a request's
 * amount is fixed: no keypad); Compound UTXOs opens on the review - for a spending address
 * ([spendingIndex]) the review of iOS's `SpendingAddressWithdrawView`. Fees, Max, coin control and
 * the send follow the chosen address: the chatting address signs with the identity key
 * ([WalletViewModel.onSendClicked]), a spending address with its own key
 * ([WalletViewModel.withdrawFromSpendingAddress]). Never the external-QR-signer round trip
 * [ColdSendFlow] uses for watch-only keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingAddressSendFlow(
    fromAddress: String,
    balanceSompi: Long,
    title: String,
    viewModel: WalletViewModel,
    onDone: () -> Unit,
    // Compound UTXOs only: the spending-chain index of [fromAddress], or null for the chatting
    // address. Every other send picks its address under From.
    spendingIndex: Int? = null,
    // Pre-fills the recipient with fromAddress itself (a self-send) and auto-fills Max, for the
    // "Compound UTXOs" entry point - merges every UTXO at this address into one. Opens on the
    // review, whose recipient is fixed, matching ColdSendFlow's identical isCompoundMode
    // behavior, since changing it away from fromAddress would defeat the point of a compound send.
    isCompoundMode: Boolean = false,
    /**
     * Shown inside a sheet ([IosFullSheet] - every send entry, as iOS presents them): the steps
     * take no system insets of their own - the sheet already sits clear of them.
     */
    presentedAsSheet: Boolean = false,
    /** Pre-fills the recipient (a contact's Send, iOS `WithdrawKaspaView(prefillAddress:)`):
     *  the send opens on From, and Back still reaches the recipient to change it. */
    prefillAddress: String? = null,
    /** Someone's payment request (a tapped or pasted link, iOS c6851d6 `WithdrawKaspaView
     *  (paymentRequest:)`): its address is locked in, its amount and memo filled in. A request
     *  pasted into the recipient field does the same (not in Compound). */
    paymentRequest: com.kachat.app.models.PaymentRequest? = null,
    /** Opened from one address's history (its Send button, iOS 7a8102b `lockedSource`): the Kaspa
     *  leaves from that address and there is no From step. */
    lockedSource: SendSource? = null,
    portfolioViewModel: com.kachat.app.viewmodels.PortfolioViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val colors = LocalAppColors.current
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    val fiatPriceInCurrency by portfolioViewModel.currentPriceUsd.collectAsState()
    val fiatCurrencyCode by portfolioViewModel.currency.collectAsState()
    var recipientInput by remember(fromAddress) { mutableStateOf(prefillAddress.orEmpty()) }
    var amountInput by remember(fromAddress) { mutableStateOf("") }
    val fiatAmountState = com.kachat.app.util.rememberKaspaFiatAmountState(resetKey = fromAddress, onKasTextChange = { amountInput = it })
    var showScanner by remember { mutableStateOf(false) }
    var manualUtxos by remember { mutableStateOf<List<UtxoEntry>?>(null) }
    var showCoinControl by remember { mutableStateOf(false) }
    var isEstimatingMax by remember { mutableStateOf(false) }
    var feeTier by remember { mutableStateOf(ColdFeeTier.NORMAL) }
    var customExtraFeeSompi by remember { mutableStateOf<Long?>(null) }
    /** The fee being typed on the fee editor, in KAS (iOS 05db583 `feeDraft`). */
    var feeDraft by remember { mutableStateOf("") }
    /// The completed send, which the swipe-up screen shows as Sent (iOS wallet 59593af).
    var sentTransaction by remember { mutableStateOf<SentTransaction?>(null) }
    /** Why the last send (or Max) failed (iOS `errorMessage`): the swipe-up screen's Failed, the
     *  amount's footnote and the review's last line. */
    var errorMessage by remember { mutableStateOf<String?>(null) }
    /** Why the fee couldn't be worked out, under the review (iOS 05db583 `feeError`). */
    var feeError by remember { mutableStateOf<String?>(null) }
    // This presentation's own sends (iOS's per-sheet isSending/sentTransaction): one closed
    // mid-send leaves its send running in the view model, and its outcome is never shown here
    // or on the next send screen.
    val sendId = remember { java.util.UUID.randomUUID().toString() }
    DisposableEffect(sendId) {
        viewModel.sends.open(sendId)
        onDispose { viewModel.sends.close(sendId) }
    }
    val isSending = sendId in viewModel.sends.inFlight.collectAsState().value
    val sendResult = viewModel.sends.results.collectAsState().value[sendId]
    val coroutineScope = rememberCoroutineScope()

    /** The payment request this send pays, if any ([paymentRequest], or one pasted in). */
    var activeRequest by remember { mutableStateOf<com.kachat.app.models.PaymentRequest?>(null) }

    // Where the Kaspa leaves from (the From step, iOS 6333dd3): the chatting address by default.
    // Compound UTXOs has no From - it merges [fromAddress] ([spendingIndex]) itself.
    var source by remember { mutableStateOf<SendSource>(lockedSource ?: SendSource.Chatting) }
    /** The From address's balance: the amount's Available and its check. */
    var sourceBalanceSompi by remember { mutableStateOf<Long?>(null) }
    val chattingAddress by viewModel.address.collectAsState()
    val sourceIndex = if (isCompoundMode) spendingIndex else source.spendingIndex
    val sourceAddress = if (isCompoundMode) fromAddress else (source.address(chattingAddress) ?: fromAddress)
    // A quantum-safe (KIP-22) From prices every input with its redeem script too (iOS
    // signatureScriptSize: kip22SignatureScriptSize).
    val sourceSignatureScriptSize = com.kachat.app.services.WalletManager.signatureScriptSize(sourceIndex)
    // Compound UTXOs of a spending address names it on its Available pill (iOS
    // SpendingAddressWithdrawView's `source.displayLabel`).
    val manageAddressEntries by viewModel.manageAddresses.collectAsState()

    // MARK: Steps (iOS 05db583)

    /** The step on screen; null until the first move (then [initialStep], iOS `step`). */
    var step by remember { mutableStateOf<SpendingSendStep?>(null) }
    /** The amount is Max: a fee, coin or From change works it out again (iOS `usedMax`). A
     *  compound sends everything, so it starts as Max. */
    var usedMax by remember { mutableStateOf(isCompoundMode) }
    /** Bumped to work Max out again after a change it depends on. Max runs from an effect, once
     *  the change has landed, so it prices the new fee, coins or From. */
    var maxRequest by remember { mutableIntStateOf(0) }
    /** The latest Max run: a slower answer to an earlier one (another speed, coins or From) is
     *  dropped (iOS 1652437 `maxRequest`). */
    var maxRun by remember { mutableIntStateOf(0) }
    /** Max worked out from only the largest coins one transaction can spend (iOS 1652437
     *  `maxCapped`): the amount's footnote and the review say so. */
    var maxCapped by remember { mutableStateOf(false) }
    /** Bumped to work the fee out again: Try Again under a fee that couldn't be worked out, or a
     *  send stopped because its fee had changed (iOS 1652437 `feeRefresh`). */
    var feeRefresh by remember { mutableIntStateOf(0) }
    /** Compound UTXOs with coins picked in coin control: Max is what those coins hold, not the
     *  largest batch the address has (iOS setMaxAmount with `manualUtxos`). */
    var compoundCoinsPicked by remember { mutableStateOf(false) }
    /** Fills in a payment request: the address (locked), the exact amount and the memo. Whatever
     *  the amount says is what is sent, through the same exact parser. */
    fun applyRequest(request: com.kachat.app.models.PaymentRequest) {
        activeRequest = request
        recipientInput = request.address
        // the exact amount is what's sent; the field shows it in whichever unit it is in
        fiatAmountState.setExactKas(request.amountText, fiatPriceInCurrency)
        // a request's amount is fixed: an earlier Max must not recalculate over it (iOS 2da5f4b)
        usedMax = false
        maxCapped = false
        maxRun++
        isEstimatingMax = false
    }
    /** The From step's Choose an Address is on (its list shows), before or after a pick. */
    var choosingFrom by remember { mutableStateOf(false) }
    /** The step after To: From, unless the send's From is fixed (an address's own Send). */
    val afterRecipient = if (lockedSource == null) SpendingSendStep.FROM else SpendingSendStep.AMOUNT
    /** The step a send opens on: Compound UTXOs straight to the review (its recipient and amount
     *  are set), a payment request or a pre-filled recipient to From (or the amount, when From is
     *  fixed). */
    val initialStep = when {
        isCompoundMode -> SpendingSendStep.REVIEW
        paymentRequest != null || prefillAddress != null -> afterRecipient
        else -> SpendingSendStep.RECIPIENT
    }
    val currentStep = step ?: initialStep

    /** Where Back goes from a step; null for the first one (its Cancel sits beside Continue). A
     *  payment request opened from a link has no recipient step (its address is fixed). */
    fun backStep(from: SpendingSendStep): SpendingSendStep? = when (from) {
        SpendingSendStep.RECIPIENT -> null
        SpendingSendStep.FROM -> if (paymentRequest != null) null else SpendingSendStep.RECIPIENT
        SpendingSendStep.AMOUNT -> when {
            lockedSource == null -> SpendingSendStep.FROM
            isCompoundMode || paymentRequest != null -> null
            else -> SpendingSendStep.RECIPIENT
        }
        SpendingSendStep.REVIEW -> if (isCompoundMode) null else SpendingSendStep.AMOUNT
        SpendingSendStep.FEE -> SpendingSendStep.REVIEW
    }

    fun go(next: SpendingSendStep) {
        focusManager.clearFocus()
        if (next == SpendingSendStep.RECIPIENT && activeRequest != null && paymentRequest == null) {
            // back from a pasted request: start the recipient over
            activeRequest = null
            recipientInput = ""
            fiatAmountState.onDisplayTextChange("", fiatPriceInCurrency)
            amountInput = ""
            usedMax = false
        }
        step = next
    }

    // coins picked on one address can't pay from another; a Max amount is worked out again for
    // the new From (iOS onChange(of: source))
    fun pickSource(picked: SendSource) {
        if (picked == source) return
        source = picked
        manualUtxos = null
        if (usedMax) maxRequest++
    }

    // The From address's balance (iOS 05db583 `.task(id: source)`).
    LaunchedEffect(sourceAddress) {
        sourceBalanceSompi = null
        sourceBalanceSompi = viewModel.addressBalanceSompi(sourceAddress)
    }

    var isResolvingKns by remember { mutableStateOf(false) }
    var knsResolvedAddress by remember { mutableStateOf<String?>(null) }
    var knsResolvedDomain by remember { mutableStateOf<String?>(null) }
    var knsError by remember { mutableStateOf<String?>(null) }
    // Every service's answer for a typed name and the one in use: the rest go under "Other
    // domains" (iOS 6ac48a7).
    var nameResolutions by remember { mutableStateOf<List<com.kachat.app.services.NameResolution>>(emptyList()) }
    var selectedTld by remember { mutableStateOf<com.kachat.app.services.NameServiceTLD?>(null) }
    // Use one service's answer (the priority one, or a pick from "Other domains").
    val selectResolution: (com.kachat.app.services.NameResolution) -> Unit = { resolution ->
        resolution.address?.let {
            knsResolvedAddress = it
            knsResolvedDomain = resolution.display
            selectedTld = resolution.tld
            knsError = null
        }
    }

    // The fee, as iOS prices it (SendKaspaComponents / SpendingAddressWithdrawView) and as the chat
    // Send KAS sheet already pays it: a base fee at the live quoted rate on the real inputs, plus
    // Fast / Priority / a custom fee as a flat extra on top (SendFeeModel). The send goes out at
    // the rate this screen quoted (its override) and the extra paid exactly - so the fee paid is
    // the fee the Review showed, and a multi-input send doesn't scale it by its input count.
    //
    // The quote is fetched once when the screen opens; until then the base is priced at the
    // network minimum, the floor of any quote.
    var liveFeeRateSompiPerGram by remember { mutableStateOf<Long?>(null) }
    val baseFeeRateSompiPerGram = liveFeeRateSompiPerGram ?: KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM
    // What automatic selection spends and pays for the amount typed (coin control off) - see the
    // preview LaunchedEffect below. Null while there is no amount, while it is being worked out,
    // or when the coins cannot cover it.
    var feePreview by remember { mutableStateOf<com.kachat.app.util.SendFeeModel.Preview?>(null) }
    var isPreviewingFee by remember { mutableStateOf(false) }
    // The one exact parser for typed KAS (iOS 16b64bc): comma or dot, at most 8 decimals.
    val previewAmountSompi = KaspaUnit.sompiFromUserText(amountInput)
    // Base fee on the real inputs: coin control's exact set, else the automatic-selection preview,
    // else (no amount yet) one input.
    // The recipient's real script length when it is known (a P2SH or ECDSA address is a byte
    // longer than the standard one), so the base priced here is the base the send is priced at.
    val feeRecipient = (knsResolvedAddress ?: recipientInput).trim()
    val recipientScriptLen = remember(feeRecipient) {
        if (KaspaAddress.isValid(feeRecipient)) {
            runCatching { KaspaAddress.getScriptPublicKey(feeRecipient).length / 2 }
                .getOrDefault(com.kachat.app.util.SendFeeModel.STANDARD_SCRIPT_LEN)
        } else {
            com.kachat.app.util.SendFeeModel.STANDARD_SCRIPT_LEN
        }
    }
    // The change script the send will pay for: a fresh quantum slot's P2SH script when the
    // change moves there, else the source's own (WalletViewModel.sendChangeScriptLen).
    val changeScriptLen = remember(sourceIndex, feeRecipient) { viewModel.sendChangeScriptLen(sourceIndex, feeRecipient) }
    val manualInputCount = manualUtxos?.size?.takeIf { it > 0 }
    val defaultFeeSompi = when {
        manualInputCount != null -> com.kachat.app.util.SendFeeModel.baseFee(manualInputCount, baseFeeRateSompiPerGram, recipientScriptLen = recipientScriptLen, signatureScriptSize = sourceSignatureScriptSize)
        feePreview != null -> feePreview!!.baseFeeSompi
        else -> com.kachat.app.util.SendFeeModel.baseFee(1, baseFeeRateSompiPerGram, signatureScriptSize = sourceSignatureScriptSize)
    }
    // The preview settled its extra together with the inputs it needs; passing exactly that extra
    // makes the send pick those same inputs and pay the total shown.
    val extraFeeSompi = if (manualInputCount == null && feePreview != null) {
        feePreview!!.extraFeeSompi
    } else {
        com.kachat.app.util.SendFeeModel.extraFee(defaultFeeSompi, feeTier.multiplier, customExtraFeeSompi)
    }
    // Change too small to keep goes to the fee, and the Review counts it (iOS 953b255
    // `SendFeeEstimate`).
    val foldedChangeSompi = remember(manualUtxos, feePreview, previewAmountSompi, baseFeeRateSompiPerGram, extraFeeSompi, recipientScriptLen, changeScriptLen, sourceSignatureScriptSize) {
        val coins = manualUtxos?.takeIf { it.isNotEmpty() }
        if (coins != null && previewAmountSompi != null && previewAmountSompi > 0) {
            com.kachat.app.util.SendFeeModel.manualFoldedChange(
                utxos = coins,
                amountSompi = previewAmountSompi,
                feeRateSompiPerGram = baseFeeRateSompiPerGram,
                extraFeeSompi = extraFeeSompi,
                recipientScriptLen = recipientScriptLen,
                changeScriptLen = changeScriptLen,
                signatureScriptSize = sourceSignatureScriptSize,
            )
        } else {
            feePreview?.foldedChangeSompi ?: 0L
        }
    }
    val effectiveFeeSompi = defaultFeeSompi + extraFeeSompi + foldedChangeSompi

    // Debounced KNS domain resolution - lets typing "name.kas" here resolve the same way Create
    // Chat's own address field already does. Skipped entirely in compound mode, where the
    // recipient is always the locked self-address, never user-typed.
    val resolveContext = LocalContext.current
    LaunchedEffect(recipientInput) {
        knsResolvedAddress = null
        knsResolvedDomain = null
        knsError = null
        nameResolutions = emptyList()
        selectedTld = null
        if (isCompoundMode) {
            isResolvingKns = false
            return@LaunchedEffect
        }
        val trimmed = recipientInput.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("kaspa:", ignoreCase = true) ||
            trimmed.startsWith("kaspatest:", ignoreCase = true) || !com.kachat.app.services.NameServicesClient.looksLikeName(trimmed)
        ) {
            isResolvingKns = false
            return@LaunchedEffect
        }
        isResolvingKns = true
        kotlinx.coroutines.delay(500)
        // Every name service, in priority: the ending typed, else .kachat only (the others wait
        // under Other domains, iOS 5a5122d). The resolved line names which one answered; the
        // others are offered under "Other domains" (iOS 6ac48a7).
        val results = viewModel.resolveEverywhere(trimmed)
        nameResolutions = results
        isResolvingKns = false
        val primary = com.kachat.app.services.NameServicesClient.primary(results, trimmed)
        if (primary?.address != null) {
            selectResolution(primary)
        } else {
            knsError = com.kachat.app.services.NameServicesClient.notFoundMessage(resolveContext, trimmed, results)
        }
    }

    // Automatic selection's preview (coin control off): which coins this amount needs and what
    // they cost, so the fee shown is priced on the real inputs rather than an assumed one -
    // mirrors ColdSendFlow's previewSelection. Debounced; Send waits for it, so it never goes out
    // with an extra worked out for a different amount.
    LaunchedEffect(previewAmountSompi, baseFeeRateSompiPerGram, feeTier, customExtraFeeSompi, manualUtxos, sourceAddress, recipientScriptLen, changeScriptLen, sourceSignatureScriptSize, feeRefresh) {
        feePreview = null
        feeError = null
        if (manualUtxos.isNullOrEmpty() && previewAmountSompi != null && previewAmountSompi > 0) {
            isPreviewingFee = true
            kotlinx.coroutines.delay(400)
            feePreview = try {
                viewModel.previewAutomaticSelection(
                    address = sourceAddress,
                    amountSompi = previewAmountSompi,
                    feeRateSompiPerGram = baseFeeRateSompiPerGram,
                    feeMultiplier = feeTier.multiplier,
                    customExtraFeeSompi = customExtraFeeSompi,
                    recipientScriptLen = recipientScriptLen,
                    signatureScriptSize = sourceSignatureScriptSize,
                    changeScriptLen = changeScriptLen,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SendFlow", "Fee preview failed for ${sourceAddress.redactedForLog()}", e)
                // said under the review (iOS 05db583 feeError)
                feeError = UserFacingError.message(e, "Could not work out the network fee.")
                null
            }
            // No preview (the coins couldn't be read, or don't cover it): the swipe waits for a
            // fee, so the Review says why it can't send (iOS 0799cec, feeError).
            if (feePreview == null && feeError == null) feeError = "Could not work out the network fee."
        }
        isPreviewingFee = false
    }

    LaunchedEffect(Unit) {
        if (isCompoundMode) {
            recipientInput = fromAddress
            isEstimatingMax = true
        } else if (paymentRequest != null && activeRequest == null) {
            applyRequest(paymentRequest)
        }
        // The live quote, once (see baseFeeRateSompiPerGram). Falls back to the network minimum
        // on any failure.
        val quotedRate = viewModel.fetchQuotedFeeRateSompiPerGram()
        liveFeeRateSompiPerGram = quotedRate
        if (isCompoundMode) {
            val run = ++maxRun
            try {
                // Consolidation is bounded by Kaspa's per-transaction mass cap, so pin the send to
                // one transaction's worth of UTXOs (largest-first, ≤MAX_INPUTS_PER_TRANSACTION) and
                // estimate Max over exactly those. This is why Max works even when the address has
                // more UTXOs than a single tx can hold - it consolidates one batch; repeat to reduce.
                val chunk = viewModel.maxConsolidatableChunk(fromAddress, quotedRate, feeTier.multiplier, customExtraFeeSompi, signatureScriptSize = sourceSignatureScriptSize)
                if (chunk != null && run == maxRun) {
                    manualUtxos = chunk.utxos
                    fiatAmountState.setMaxKas(chunk.amountSompi / 100_000_000.0, fiatPriceInCurrency)
                    maxCapped = chunk.cappedAtInputLimit
                }
            } catch (e: Exception) {
                // Leave the field untouched on failure - same as the Max button itself.
            } finally {
                // a Max started since then owns the spinner
                if (run == maxRun) isEstimatingMax = false
            }
        }
    }

    LaunchedEffect(sendResult) {
        val result = sendResult ?: return@LaunchedEffect
        if (result.isSuccess) {
            result.getOrNull()?.let { txId ->
                sentTransaction = SentTransaction(
                    txId = txId,
                    amountSompi = KaspaUnit.sompiFromUserText(amountInput)?.takeIf { it > 0 },
                    // A compound is a self-send: naming the address it just came from reads as a
                    // mistake, so it just says how much moved. A resolved name shows as the name.
                    recipient = (knsResolvedDomain ?: recipientInput.trim()).takeIf { !isCompoundMode },
                )
            }
        } else {
            errorMessage = UserFacingError.message(result.exceptionOrNull(), context.getString(R.string.withdrawal_failed))
            // nothing went out; Try Again goes back to a Review with the fee of today's coins
            // (iOS 1652437)
            if (result.exceptionOrNull() is com.kachat.app.services.SendFeeChangedException) feeRefresh++
        }
        viewModel.sends.consume(sendId)
    }

    // No confirmation sheet: the swipe-up screen itself shows Sent or Failed (iOS wallet 59593af).
    // Back is each step's own: Back to the step before, Cancel on a first step.

    // A payment request pasted or scanned in fills in everything it carries (iOS c6851d6) and
    // moves on from To (iOS 05db583 handleInputChange).
    val onRecipientInput: (String) -> Unit = { input ->
        // Only when the paste is just that link: one inside other text (a message quoting someone's
        // request) isn't necessarily the one meant, and would set the amount and memo unseen
        // (iOS 1652437, as KaPost).
        val trimmedInput = input.trim()
        val pasted = if (isCompoundMode) null
        else (KaChatLink.findFirst(trimmedInput)?.takeIf { it.raw == trimmedInput }?.ref as? KaChatLinkRef.PaymentRequest)?.request
        if (pasted != null) {
            applyRequest(pasted)
            if (currentStep == SpendingSendStep.RECIPIENT) go(afterRecipient)
        } else {
            recipientInput = input
        }
    }

    // The recipient scanner and coin control are sheets over the send, as iOS's
    // SpendingAddressWithdrawView and WithdrawKaspaView present QRScannerView and CoinControlView.
    if (showScanner) {
        QrScannerSheet(
            onScanned = { scanned -> onRecipientInput(KaspaAddress.fromScanned(scanned)) },
            onDismiss = { showScanner = false }
        )
    }

    if (showCoinControl) {
        CoinControlSheet(
            fromAddress = sourceAddress,
            fetchUtxos = { addr -> viewModel.fetchUtxosForCoinControl(addr) },
            // every coin, the ones this send can't spend greyed out with why (iOS 1652437)
            fetchCoinsWithReasons = { addr -> viewModel.fetchCoinControlCoins(addr) },
            initialSelection = manualUtxos,
            onDone = { selection ->
                manualUtxos = selection
                if (isCompoundMode) { compoundCoinsPicked = selection != null }
                if (usedMax) maxRequest++
            },
            onDismiss = { showCoinControl = false }
        )
    }

    val amountSompi = KaspaUnit.sompiFromUserText(amountInput)
    /** The amount as iOS's `amountSompi`: none until it is above zero. */
    val positiveAmountSompi = amountSompi?.takeIf { it > 0L }
    // Only an address of the network the app runs on: the other network's is the same key on
    // another chain, refused with its own reason under the field (iOS ce20e87).
    val isValidAddress = remember(recipientInput) { KaspaAddress.isValidOnActiveNetwork(recipientInput.trim()) }
    val effectiveAddress = knsResolvedAddress ?: recipientInput
    val hasValidRecipient = if (knsResolvedAddress != null) true else (isValidAddress && !isResolvingKns)

    // Max: fee-aware, from the funds this send actually spends. Compound pins one mass-safe
    // transaction's worth of UTXOs (largest-first, <=cap) as the input set - see the compound
    // LaunchedEffect above - unless coins were picked in coin control.
    val setMaxAmount: () -> Unit = {
        usedMax = true
        errorMessage = null
        // Only the newest Max answer counts (iOS 1652437 `maxRequest`): a slower one for an
        // earlier speed, coins or From is dropped, and leaves the spinner to the newest.
        val run = ++maxRun
        coroutineScope.launch {
            isEstimatingMax = true
            try {
                // Room for the base fee and the Fast / Priority / custom extra, the extra derived
                // from Max's own base - never from a fee estimated for whatever amount was typed
                // before, which is how Max used to leave too little room for the extra.
                val maxTier = feeTier
                val maxCustomExtra = customExtraFeeSompi
                val pickedCoins = manualUtxos.takeIf { !isCompoundMode || compoundCoinsPicked }
                if (isCompoundMode && pickedCoins == null) {
                    val chunk = viewModel.maxConsolidatableChunk(fromAddress, liveFeeRateSompiPerGram, maxTier.multiplier, maxCustomExtra, signatureScriptSize = sourceSignatureScriptSize)
                    if (run != maxRun) return@launch
                    if (chunk != null) {
                        manualUtxos = chunk.utxos
                        fiatAmountState.setMaxKas(chunk.amountSompi / 100_000_000.0, fiatPriceInCurrency)
                        maxCapped = chunk.cappedAtInputLimit
                    } else {
                        // nothing to consolidate, or nothing known yet
                        errorMessage = "Nothing to consolidate here yet."
                        maxCapped = false
                    }
                } else {
                    // null = the wallet cannot answer yet (the REST client is created a moment
                    // after launch); 0 = it answered, and the fee eats the balance. Neither may
                    // write "0" into the field, which is what made this read as a dead button.
                    val max = viewModel.estimateMaxSendable(sourceAddress, liveFeeRateSompiPerGram, pickedCoins, maxTier.multiplier, maxCustomExtra, signatureScriptSize = sourceSignatureScriptSize)
                    if (run != maxRun) return@launch
                    maxCapped = max != null && max.cappedAtInputLimit && max.amountSompi > 0L
                    when (val maxSompi = max?.amountSompi) {
                        null -> { errorMessage = "Still connecting. Try Max again in a moment." }
                        0L -> { errorMessage = "Not enough here to cover the network fee." }
                        else -> fiatAmountState.setMaxKas(maxSompi / 100_000_000.0, fiatPriceInCurrency)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (run != maxRun) return@launch
                // Said out loud (the amount's footnote, the review's last line, as iOS's
                // errorMessage), not swallowed - and logged, so there is a trace to diagnose.
                Log.w("SendFlow", "Max estimate failed for ${sourceAddress.redactedForLog()}", e)
                errorMessage = UserFacingError.message(e, "Could not work out the maximum.")
                maxCapped = false
            } finally {
                if (run == maxRun) isEstimatingMax = false
            }
        }
    }
    // A fee, coin or From change after Max (iOS `if usedMax { setMaxAmount() }`): Max is worked
    // out again for it, or the send would need more than the coins hold.
    LaunchedEffect(maxRequest) {
        if (maxRequest > 0) setMaxAmount()
    }
    // Not while the fee preview is being worked out: the send passes the preview's extra.
    // The swipe waits for a settled amount and fee (iOS 0799cec `canSend`): a Max still being
    // worked out, the live quote not in yet, no fee for automatic selection, or a fee that couldn't
    // be worked out, would send something other than what the Review shows.
    val feeKnown = liveFeeRateSompiPerGram != null && (!manualUtxos.isNullOrEmpty() || feePreview != null)
    val canSend = !isSending && hasValidRecipient && (amountSompi ?: 0) > 0 && !isPreviewingFee &&
        !isEstimatingMax && feeKnown && feeError == null
    val send: () -> Unit = {
        errorMessage = null
        amountSompi?.takeIf { canSend }?.let {
            val recipient = effectiveAddress.trim()
            // The rate the Review priced (the quote fetched when the screen opened; canSend waits
            // for it), never a fresh quote at send time that could pay a different fee than shown.
            // The speed or custom fee goes on top as the flat extra shown (SendFeeModel).
            val reviewedRate = liveFeeRateSompiPerGram
            // What the Review shows (folded change included): a send rebuilt from other coins
            // that would pay more stops before anything is signed (iOS 1652437).
            val expectedFee = effectiveFeeSompi
            // A chatting-address request is a plain send too: the wallet has no chats to file a
            // chat payment in (iOS wallet 301ebfb).
            if (sourceIndex != null) {
                viewModel.withdrawFromSpendingAddress(sendId, sourceIndex, recipient, it, feeRateOverride = reviewedRate, manualUtxos = manualUtxos, extraFeeSompi = extraFeeSompi, expectedFeeSompi = expectedFee)
            } else {
                viewModel.onSendClicked(sendId, recipient, it, feeRateOverride = reviewedRate, manualUtxos = manualUtxos, extraFeeSompi = extraFeeSompi, expectedFeeSompi = expectedFee)
            }
        }
    }

    // The fee editor opens on the fee shown; its Confirm sets the extra over the base, never
    // under the network's minimum (iOS 05db583 openFeeEditor / feeStep).
    val openFeeEditor: () -> Unit = {
        feeDraft = com.kachat.app.services.AddressActivityNotifier.formatKas(effectiveFeeSompi)
        go(SpendingSendStep.FEE)
    }

    val screenTitle = when {
        isCompoundMode -> stringResource(R.string.compound_utxos)
        activeRequest != null -> stringResource(R.string.pr_payment_request)
        else -> title
    }
    val stepInsets = if (presentedAsSheet) WindowInsets(0, 0, 0, 0) else WindowInsets.systemBars
    /** The amount is more than the From address holds. */
    val exceedsBalance = sourceBalanceSompi.let { balance -> balance != null && positiveAmountSompi != null && positiveAmountSompi > balance }
    val overBalanceText = tr(R.string.kp_this_is_more_than_the_address_7676b)
    /** Under the typed amount: what's wrong with it, else what's available (iOS amountFootnote). */
    /** Why Max isn't the whole balance: one transaction spends at most
     *  [KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION] coins, and this address holds more (iOS
     *  1652437 `maxCappedNote`; not in strings_kapay.xml yet, so English). */
    val maxCappedNote = com.kachat.app.util.KaspaUtxoSelector.MAX_INPUTS_PER_TRANSACTION.let { limit ->
        if (isCompoundMode) "One transaction can combine $limit coins, so this combines the largest $limit. Compound again for the rest."
        else tr(R.string.kp_max_is_what_one_transaction_can_3bfb0, limit)
    }
    val amountFootnote = errorMessage ?: when {
        exceedsBalance -> overBalanceText
        usedMax && maxCapped -> maxCappedNote
        else -> sourceBalanceSompi?.let { KaspaUnit.label(tr(R.string.kp_available_kas_ba9f1, com.kachat.app.services.AddressActivityNotifier.formatKas(it))) }
    }
    val canContinueFromAmount = hasValidRecipient && positiveAmountSompi != null && !exceedsBalance && !isEstimatingMax
    /** The recipient's name: the domain it resolved from, else its name in your Contacts. */
    val recipientName = knsResolvedDomain
        ?: com.kachat.app.services.AddressBookManager.shared?.entry(effectiveAddress.trim())?.customName
    val sourceTitle = when {
        isCompoundMode -> tr(R.string.kp_this_address_0a1a1)
        else -> when (val from = source) {
            SendSource.Chatting -> stringResource(R.string.chatting_address)
            is SendSource.Spending -> from.label
        }
    }
    val stepBack: (SpendingSendStep) -> (() -> Unit)? = { from -> backStep(from)?.let { back -> { go(back) } } }

    // One step at a time, sliding: the next step in from the right, Back from the left (iOS
    // 05db583 stepTransition, .spring(response: 0.4, dampingFraction: 0.9)).
    AnimatedContent(
        targetState = currentStep,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (slideInHorizontally(SpendingSendStepSpring) { if (forward) it else -it } togetherWith
                slideOutHorizontally(SpendingSendStepSpring) { if (forward) -it else it })
        },
        label = "sendStep",
    ) { shown ->
        when (shown) {
            // MARK: Step 1 - To
            SpendingSendStep.RECIPIENT -> SendStepScreen(
                title = screenTitle,
                onCancel = onDone,
                canContinue = hasValidRecipient,
                onContinue = { go(afterRecipient) },
                insets = stepInsets,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SendStepQuestion(tr(R.string.kp_who_are_you_sending_to_2a7f1))
                    SendRecipientCard(
                        input = recipientInput,
                        onInputChange = onRecipientInput,
                        isResolving = isResolvingKns,
                        resolvedAddress = knsResolvedAddress,
                        resolvedName = knsResolvedDomain,
                        lookupError = knsError,
                        isValidAddress = isValidAddress,
                        onScan = { showScanner = true },
                        nameResolutions = nameResolutions,
                        selectedTld = selectedTld,
                        onSelectResolution = selectResolution,
                        focusOnAppear = true,
                    )
                }
            }

            // MARK: Step 2 - From
            // "Where are you sending from?": the Chatting Address, or Choose an Address with your
            // addresses listed right here (no sheet).
            SpendingSendStep.FROM -> SendStepScreen(
                title = screenTitle,
                onCancel = onDone,
                canContinue = !(choosingFrom && source is SendSource.Chatting),
                onContinue = { go(SpendingSendStep.AMOUNT) },
                onBack = stepBack(SpendingSendStep.FROM),
                insets = stepInsets,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SendStepQuestion(tr(R.string.kp_where_are_you_sending_from_687c0))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectablePill(
                            title = stringResource(R.string.chatting_address),
                            selected = !choosingFrom,
                            modifier = Modifier.weight(1f),
                        ) {
                            choosingFrom = false
                            pickSource(SendSource.Chatting)
                        }
                        SelectablePill(
                            title = stringResource(R.string.pr_choose_an_address),
                            selected = choosingFrom,
                            modifier = Modifier.weight(1f),
                        ) { choosingFrom = true }
                    }
                    if (choosingFrom) {
                        Text(tr(R.string.kp_pick_which_of_your_addresses_the_0693e), color = colors.textSecondary, fontSize = 13.sp)
                        OwnAddressList(
                            selected = if (source is SendSource.Chatting) null else source.address(chattingAddress),
                            walletViewModel = viewModel,
                            onPick = { picked ->
                                pickSource(picked)
                                // the chatting address picked from the list: back on its own pill
                                if (picked is SendSource.Chatting) { choosingFrom = false }
                            },
                        )
                    } else {
                        source.address(chattingAddress)?.let { address ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sendKaspaGlass(16.dp)
                                    .padding(14.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        stringResource(R.string.chatting_address),
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "${address.take(14)}...${address.takeLast(6)}",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                    )
                                }
                                val balance = sourceBalanceSompi
                                if (balance != null) {
                                    Text(
                                        "${KaspaUnit.plain(balance)} ${KaspaUnit.symbol}",
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                                    )
                                } else {
                                    com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(18.dp), color = colors.textSecondary, strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }

            // MARK: Step 3 - Amount
            SpendingSendStep.AMOUNT -> SendStepScreen(
                title = screenTitle,
                onCancel = onDone,
                canContinue = canContinueFromAmount,
                onContinue = { go(SpendingSendStep.REVIEW) },
                onBack = stepBack(SpendingSendStep.AMOUNT),
                continueTitle = tr(R.string.kp_review_e29a7),
                insets = stepInsets,
            ) {
                // "To alice.kachat" (or the address) and "From Chatting Address" (or the picked
                // address), so the amount step says where it is going.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SendStepChip(
                        label = stringResource(R.string.to),
                        value = recipientName ?: effectiveAddress.trim(),
                        isAddress = recipientName == null,
                    )
                    SendStepChip(label = stringResource(R.string.from), value = sourceTitle)
                }
                val request = activeRequest
                if (request != null) {
                    // A request's amount is fixed: no keypad, the request itself instead.
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SpendingSendReviewAmount(
                            title = tr(R.string.kp_you_re_sending_e8358),
                            amountSompi = positiveAmountSompi,
                            priceInCurrency = fiatPriceInCurrency,
                            currencyCode = fiatCurrencyCode,
                        )
                        PaymentRequestSummary(request = request)
                        if (exceedsBalance) {
                            Text(overBalanceText, color = colors.danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    KaspaKeypadAmountEntry(
                        fiatAmountState = fiatAmountState,
                        priceInCurrency = fiatPriceInCurrency,
                        currencyCode = fiatCurrencyCode,
                        onMax = setMaxAmount,
                        modifier = Modifier.weight(1f),
                        isEstimatingMax = isEstimatingMax,
                        maxEnabled = hasValidRecipient,
                        footnote = amountFootnote,
                        footnoteIsError = exceedsBalance || errorMessage != null,
                        onKeyTyped = {
                            usedMax = false
                            errorMessage = null
                        },
                    )
                }
            }

            // MARK: Step 4 - Review
            // Swipe the bar up to send: the whole screen moves up into the BlockDAG while it goes
            // out, then Sent with the transaction, or Failed with the error and Try Again (iOS
            // wallet 59593af SwipeUpToSendScrollView). Its header (Back, the title, Cancel) moves
            // with the swipe.
            SpendingSendStep.REVIEW -> SwipeUpToSendScreen(
                screenTitle = if (isCompoundMode) stringResource(R.string.compound_utxos) else tr(R.string.kp_review_e29a7),
                onCancel = onDone,
                barTitle = stringResource(if (isCompoundMode) R.string.swipe_up_to_consolidate else R.string.swipe_up_to_send),
                isBusy = isSending,
                isEnabled = canSend,
                amountSompi = amountSompi,
                recipient = if (isCompoundMode) null else (knsResolvedDomain ?: effectiveAddress.trim()).takeIf { it.isNotEmpty() },
                sent = sentTransaction,
                failureMessage = errorMessage,
                explorerUrl = { kaspaExplorer.txUrl(it) },
                onDone = {
                    sentTransaction = null
                    onDone()
                },
                onSwipe = send,
                insets = stepInsets,
                onBack = stepBack(SpendingSendStep.REVIEW),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isCompoundMode && spendingIndex != null) {
                        // A spending address's Compound UTXOs: iOS SpendingAddressWithdrawView's
                        // review - this address as the recipient, the amount everything on it.
                        SendRecipientCard(
                            input = recipientInput,
                            onInputChange = {},
                            isResolving = isResolvingKns,
                            resolvedAddress = knsResolvedAddress,
                            resolvedName = knsResolvedDomain,
                            lookupError = knsError,
                            isValidAddress = isValidAddress,
                            onScan = {},
                            lockedAddress = fromAddress,
                        )
                        SpendingSendReviewAmount(
                            title = tr(R.string.kp_consolidating_f7afc),
                            amountSompi = positiveAmountSompi,
                            priceInCurrency = null,
                            currencyCode = fiatCurrencyCode,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        // The address it merges, with what it holds (iOS's Available pill, which
                        // offers no other address in Compound).
                        val label = spendingAddressDisplayLabel(spendingIndex, manageAddressEntries.firstOrNull { it.index == spendingIndex }?.label)
                        SendInfoPill {
                            Text(KaspaUnit.label(tr(R.string.kp_available_kas_ba9f1, com.kachat.app.services.AddressActivityNotifier.formatKas(balanceSompi))) + " · " + label)
                        }
                    } else {
                        SpendingSendReviewAmount(
                            title = if (isCompoundMode) tr(R.string.kp_consolidating_f7afc) else tr(R.string.kp_you_re_sending_e8358),
                            amountSompi = positiveAmountSompi,
                            priceInCurrency = fiatPriceInCurrency,
                            currencyCode = fiatCurrencyCode,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .sendKaspaGlass(20.dp)
                                .padding(14.dp),
                        ) {
                            SpendingSendReviewRow(stringResource(R.string.to)) {
                                if (isCompoundMode) {
                                    Text(tr(R.string.kp_this_address_consolidating_d0ba3), color = colors.textPrimary, fontSize = 15.sp, textAlign = TextAlign.End)
                                } else {
                                    recipientName?.let {
                                        Text(it, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                                    }
                                    MiddleEllipsisText(
                                        effectiveAddress.trim(),
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.divider)
                            SpendingSendReviewRow(stringResource(R.string.from)) {
                                val reviewFromAddress = if (isCompoundMode) fromAddress else source.address(chattingAddress)
                                Text(sourceTitle, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                                reviewFromAddress?.let { address ->
                                    Text(
                                        "${address.take(14)}...${address.takeLast(6)}",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.divider)
                            SpendingSendReviewRow(stringResource(R.string.total)) {
                                when {
                                    positiveAmountSompi != null -> Text(
                                        "${com.kachat.app.services.AddressActivityNotifier.formatKas(positiveAmountSompi + effectiveFeeSompi)} ${KaspaUnit.symbol}",
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                                    )
                                    isPreviewingFee || isEstimatingMax -> com.kachat.app.ui.theme.IosActivityIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = colors.textSecondary,
                                        strokeWidth = 2.dp,
                                    )
                                    else -> Text("—", color = colors.textSecondary, fontSize = 15.sp)
                                }
                            }
                        }

                        activeRequest?.let { PaymentRequestSummary(request = it) }
                    }

                    // The only things that can still change: the fee and the coins it spends.
                    SendFeeControls(
                        feeTier = feeTier,
                        onFeeTierChange = {
                            feeTier = it
                            customExtraFeeSompi = null
                            if (usedMax) maxRequest++
                        },
                        isEstimatingFee = isPreviewingFee,
                        feeText = "${com.kachat.app.services.AddressActivityNotifier.formatKas(effectiveFeeSompi)} ${KaspaUnit.symbol}",
                        coinControlSummary = coinControlSummary(manualUtxos),
                        onCoinControl = { if (!isSending) showCoinControl = true },
                        onEditFee = openFeeEditor,
                    )

                    // The send's error, else the fee's (iOS 1652437: both send screens now).
                    val reviewError = errorMessage ?: feeError
                    if (reviewError != null) {
                        Text(reviewError, color = colors.danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                        // a fee that couldn't be worked out would otherwise keep the swipe off for
                        // good: Try Again works it out again (iOS 1652437 `feeRefresh`)
                        if (errorMessage == null) {
                            Text(
                                stringResource(R.string.try_again),
                                color = KaspaTeal,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { feeRefresh++ }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    } else if (usedMax && maxCapped && !isCompoundMode) {
                        // iOS SpendingAddressWithdrawView's note under the review
                        Text(maxCappedNote, color = colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            // MARK: Network fee (from the review)
            // The whole fee typed on the keypad; Confirm back to the review with the extra over
            // the base fee, never under it.
            SpendingSendStep.FEE -> SendFeeEditorScreen(
                draft = feeDraft,
                onDraftChange = { feeDraft = it },
                onCancel = { go(SpendingSendStep.REVIEW) },
                onConfirm = { typed ->
                    customExtraFeeSompi = (typed - defaultFeeSompi).coerceAtLeast(0L)
                    if (usedMax) maxRequest++
                    go(SpendingSendStep.REVIEW)
                },
                minimumSompi = defaultFeeSompi,
                insets = stepInsets,
            )
        }
    }
}

/** iOS `.spring(response: 0.4, dampingFraction: 0.9)`: stiffness (2*pi/0.4)^2. */
private val SpendingSendStepSpring = androidx.compose.animation.core.spring(
    dampingRatio = 0.9f,
    stiffness = 247f,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * The amount a send's review shows, big, with its value in your currency under it when
 * [priceInCurrency] is known (iOS 05db583 `WithdrawKaspaView.reviewAmount`; with no price, iOS
 * SpendingAddressWithdrawView's "Consolidating"). A spinner until the amount is known.
 */
@Composable
private fun SpendingSendReviewAmount(
    title: String,
    amountSompi: Long?,
    priceInCurrency: Double?,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = colors.textSecondary, fontSize = 15.sp)
        if (amountSompi != null) {
            SpendingSendReviewAmountText("${com.kachat.app.services.AddressActivityNotifier.formatKas(amountSompi)} ${KaspaUnit.symbol}")
            if (priceInCurrency != null && priceInCurrency > 0.0) {
                Text(
                    com.kachat.app.util.formatFiatAmount(amountSompi / 100_000_000.0 * priceInCurrency, currencyCode),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                )
            }
        } else {
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary)
            }
        }
    }
}

/** The review's amount: 40 bold rounded digits on one line, shrinking to half rather than being
 *  cut (iOS `.lineLimit(1).minimumScaleFactor(0.5)`). */
@Composable
private fun SpendingSendReviewAmountText(text: String) {
    val base = 40.sp
    var size by remember(text) { mutableStateOf(base) }
    Text(
        text,
        style = androidx.compose.ui.text.TextStyle(
            color = LocalAppColors.current.textPrimary,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            fontFamily = IosRoundedFontFamily,
            fontFeatureSettings = "tnum",
        ),
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && size.value > base.value * 0.5f) {
                size = (size.value * 0.94f).coerceAtLeast(base.value * 0.5f).sp
            }
        },
    )
}

/** One line of the review's summary (iOS 05db583 `reviewRow`): the grey label, then its value
 *  at the trailing edge, at least 12 apart. */
@Composable
private fun SpendingSendReviewRow(label: String, value: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = LocalAppColors.current.textSecondary, fontSize = 15.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, content = value)
    }
}

/**
 * On-chain transaction history + UTXOs for one spending address, with direct Send/Receive —
 * reached by tapping an address row in [ManageAddressesScreen]/[ManageAddressesHiddenScreen].
 * Visually mirrors [ColdStorageTxHistoryScreen], but Send goes through [SpendingAddressSendFlow]
 * (direct signing with the address's own already-held private key via
 * [WalletViewModel.withdrawFromSpendingAddress]) rather than Cold Storage's external-QR-signer
 * flow, which only makes sense for a watch-only key with no private key on this device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingAddressTxHistoryScreen(
    index: Int,
    onBack: () -> Unit,
    viewModel: WalletViewModel,
    // Created here (not left to SpendingAddressSendFlow's own default) so its live-price fetch -
    // KaspaFiatAmountState.toggleMode silently no-ops until a price has arrived - gets a head
    // start of however long the user spends on this screen before ever tapping Send, instead of
    // starting from zero the instant the send flow itself first composes.
    portfolioViewModel: com.kachat.app.viewmodels.PortfolioViewModel = hiltViewModel(),
    /** Message on a .kachat name's owner, from its detail. */
    /** The row resolved by the History sheet a tapped "Received" notification opens (iOS
     *  13046ad), used only while the shared list hasn't got this index (a failed load). */
    fallbackEntry: com.kachat.app.services.WalletService.SpendingAddressEntry? = null,
    /** Opened as the root of the "Received" History sheet (iOS 13046ad `OwnAddressHistorySheet`):
     *  Done closes it, top left, in place of the back arrow. */
    closesWithDone: Boolean = false,
) {
    val addresses by viewModel.manageAddresses.collectAsState()
    // Looked up from the already-loaded address list (shared with ManageAddressesScreen) rather
    // than threading label/balance through the route - keeps the nav arg down to just the index
    // withdrawFromSpendingAddress actually signs with.
    val entry = remember(addresses, index, fallbackEntry) {
        addresses.firstOrNull { it.index == index } ?: fallbackEntry?.takeIf { it.index == index }
    }
    val address = entry?.address.orEmpty()

    val txHistory by viewModel.spendingAddressTxHistory.collectAsState()
    val isLoadingTxHistory by viewModel.loadingSpendingAddressTxHistory.collectAsState()
    val txHistoryFailed by viewModel.spendingAddressTxHistoryFailed.collectAsState()
    val utxos by viewModel.spendingAddressUtxos.collectAsState()
    val isLoadingUtxos by viewModel.loadingSpendingAddressUtxos.collectAsState()
    val utxosLoadError by viewModel.spendingAddressUtxosError.collectAsState()
    val utxoListAddress: String? = (address as String?)?.takeIf { it.isNotEmpty() }
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    val biometricSpendingKeyEnabled by viewModel.biometricSpendingKeyEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current
    // The tapped transaction, while its action chooser is up.
    var transactionActionTarget by remember { mutableStateOf<ColdStorageAddressDiscovery.AddressTransaction?>(null) }
    // The transaction being filed into a portfolio, if any - see [AddToPortfolioSheet].
    var portfolioCandidate by remember { mutableStateOf<ColdStorageAddressDiscovery.AddressTransaction?>(null) }
    var addedPortfolioName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    var showQr by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showCompoundFlow by remember { mutableStateOf(false) }
    var showPrivateKey by remember { mutableStateOf(false) }
    // The "Address Actions" sheet, and the public-key screen it can open (iOS 2da5f4b).
    var showAddressActions by remember { mutableStateOf(false) }
    var showPublicKey by remember { mutableStateOf(false) }
    var utxoLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var labelingUtxoKey by remember { mutableStateOf<String?>(null) }
    var labelInput by remember { mutableStateOf("") }
    // The .kachat name opened from the Domains tab's .kachat screen (iOS 881ada6).
    var openedKachat by remember { mutableStateOf<com.kachat.app.services.kachatnames.NameInfo?>(null) }
    // The name service opened from the Domains tab (iOS fe52376).
    var openedDomainService by remember { mutableStateOf<com.kachat.app.services.NameServiceTLD?>(null) }

    LaunchedEffect(address) {
        if (address.isNotEmpty()) {
            viewModel.loadSpendingAddressTxHistory(address)
            viewModel.loadSpendingAddressUtxos(address)
            utxoLabels = viewModel.getSpendingUtxoLabels(address)
        }
    }

    // In-place full-screen swap - not a nav route, not an overlay dialog - mirroring
    // ColdStorageTxHistoryScreen's own `if (showSendFlow) { ...; return }` idiom exactly, so Send
    // takes over the whole screen the same way on both the spending and Cold Storage paths.
    // A .kachat name opened from this address's .kachat tab: its live detail, which knows which of
    // your addresses holds it (iOS 881ada6).
    openedKachat?.let { info ->
        AboveDock { KachatLiveNameDetailScreen(info, onBack = { openedKachat = null }) }
        return
    }
    // A name service opened from this address's Domains tab: its names (iOS fe52376).
    openedDomainService?.let { tld ->
        AboveDock {
            AddressDomainServiceScreen(
                address = address,
                tld = tld,
                onBack = { openedDomainService = null },
                onOpenKachat = { openedKachat = it },
            )
        }
        return
    }

    // Send and Compound are sheets over this screen that only Cancel closes, as iOS presents
    // them (.interactiveDismissDisabled()). Send is the Send Kaspa every send is (iOS 6333dd3):
    // From starts at the Chatting Address, and this address is under Choose an Address.
    val sendChattingAddress by viewModel.address.collectAsState()
    val sendChattingBalance by viewModel.balanceSompi.collectAsState()
    val sendFrom = sendChattingAddress
    if (showWithdraw && entry != null && sendFrom != null) {
        IosFullSheet(
            onDismissed = {
                showWithdraw = false
                viewModel.loadSpendingAddressTxHistory(address)
                viewModel.loadSpendingAddressUtxos(address)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = sendFrom,
                balanceSompi = sendChattingBalance,
                title = tr(R.string.kp_send_kaspa_5e5c4),
                viewModel = viewModel,
                portfolioViewModel = portfolioViewModel,
                onDone = close,
                presentedAsSheet = true,
                // Send from this address's own page: it is the From, with no other to pick.
                lockedSource = SendSource.Spending(entry.index, entry.address, spendingAddressDisplayLabel(entry.index, entry.label)),
            )
        }
    }

    if (showCompoundFlow && entry != null) {
        IosFullSheet(
            onDismissed = {
                showCompoundFlow = false
                viewModel.loadSpendingAddressTxHistory(address)
                viewModel.loadSpendingAddressUtxos(address)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = entry.address,
                balanceSompi = entry.balanceSompi,
                title = "Send from ${entry.displayTitle}",
                spendingIndex = entry.index,
                viewModel = viewModel,
                portfolioViewModel = portfolioViewModel,
                isCompoundMode = true,
                onDone = close,
                presentedAsSheet = true,
            )
        }
    }

    val displayName = entry?.label?.takeIf { it.isNotBlank() } ?: com.kachat.app.services.WalletManager.spendingAddressTitle(index)

    Scaffold(
        containerColor = LocalAppColors.current.background,
        // Runs on under Receive / Send and the dock, as iOS's list runs on under its
        // safeAreaInset bar and tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(displayName, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    // At the root of the "Received" History sheet: Done, as iOS's
                    // OwnAddressHistorySheet puts it (13046ad); a back arrow everywhere else.
                    if (closesWithDone) {
                        SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    } else {
                        SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                actions = {
                    AddressOptionsButton(
                        onClick = { if (address.isNotEmpty()) showAddressActions = true },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
        bottomBar = {
            AddressReceiveSendBar(
                receiveEnabled = address.isNotEmpty(),
                sendEnabled = (entry?.balanceSompi ?: 0L) > 0L,
                onReceive = { showQr = true },
                onSend = { showWithdraw = true },
            )
        }
    ) { padding ->
        // What Receive / Send and the dock cover: the lists scroll clear of it.
        val barInset = padding.calculateBottomPadding()
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AddressPageBalanceHeader(entry?.balanceSompi ?: 0L)
            // The app's tab look (Chats / Group Chats / Public Chats), not a segmented control;
            // plain names - History, UTXOs, Domains (iOS b96d727/718b88c, fe52376).
            UnderlineTabBar(
                titles = listOf(stringResource(R.string.address_tab_history), stringResource(R.string.utxos), stringResource(R.string.address_tab_domains)),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(top = 4.dp),
            )
            when (selectedTab) {
                0 -> AddressHistoryList(
                    txHistory = txHistory,
                    isLoading = isLoadingTxHistory,
                    failed = txHistoryFailed,
                    onRetry = { viewModel.loadSpendingAddressTxHistory(address) },
                    onTransactionClick = { transactionActionTarget = it },
                    bottomPadding = barInset,
                )
                2 -> AddressDomainsTab(address, onOpenService = { openedDomainService = it }, bottomPadding = barInset)
                else -> AddressUtxoList(
                    utxos = utxos,
                    isLoading = isLoadingUtxos,
                    loadError = utxosLoadError,
                    labels = utxoLabels,
                    onRetry = { utxoListAddress?.let { viewModel.loadSpendingAddressUtxos(it) } },
                    onCompound = { showCompoundFlow = true },
                    // iOS SpendingAddressTransactionHistoryView's own footer.
                    compoundFooter = stringResource(R.string.compound_utxos_spending_description),
                    onRename = { key ->
                        labelingUtxoKey = key
                        labelInput = utxoLabels[key] ?: ""
                    },
                    bottomPadding = barInset,
                )
            }
        }
    }

    if (showQr && address.isNotEmpty()) {
        QrCodeOverlay(value = address, onDismiss = { showQr = false })
    }

    if (showAddressActions) {
        AddressActionsSheet(
            // A quantum address keeps its key hidden until it spends: that's the point of it.
            showsPublicKey = !com.kachat.app.services.WalletManager.isQuantumIndex(index),
            onViewPrivateKey = {
                showAddressActions = false
                if (biometricSpendingKeyEnabled) {
                    context.authenticateWithDeviceCredential(
                        title = "Unlock to View Private Key",
                        onSuccess = { showPrivateKey = true }
                    )
                } else {
                    showPrivateKey = true
                }
            },
            onViewPublicKey = {
                showAddressActions = false
                showPublicKey = true
            },
            onViewInExplorer = {
                showAddressActions = false
                uriHandler.openUriOrToast(kaspaExplorer.addressUrl(address))
            },
            onDismiss = { showAddressActions = false },
        )
    }

    if (showPublicKey) {
        AddressPublicKeyOverlay(
            address = address,
            note = tr(R.string.kp_this_is_the_public_half_of_c5f98),
            onDismiss = { showPublicKey = false },
        )
    }

    if (showPrivateKey) {
        SpendingAddressPrivateKeyOverlay(privateKeyHex = viewModel.getSpendingPrivateKeyHex(index), onDismiss = { showPrivateKey = false })
    }

    labelingUtxoKey?.let { key ->
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { labelingUtxoKey = null },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.rename_utxo), color = LocalAppColors.current.textPrimary) },
            text = {
                com.kachat.app.ui.theme.IosTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary,
                        unfocusedTextColor = LocalAppColors.current.textPrimary,
                        focusedBorderColor = KaspaTeal,
                        unfocusedBorderColor = LocalAppColors.current.textSecondary,
                        focusedLabelColor = KaspaTeal,
                        unfocusedLabelColor = LocalAppColors.current.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSpendingUtxoLabel(address, key, labelInput)
                    utxoLabels = viewModel.getSpendingUtxoLabels(address)
                    labelingUtxoKey = null
                }) {
                    Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { labelingUtxoKey = null }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    transactionActionTarget?.let { tapped ->
        TransactionActionsSheet(
            tx = tapped,
            onOpenExplorer = {
                transactionActionTarget = null
                uriHandler.openUriOrToast(kaspaExplorer.txUrl(tapped.txId))
            },
            onAddToPortfolio = {
                transactionActionTarget = null
                portfolioCandidate = tapped
            },
            onDismiss = { transactionActionTarget = null },
        )
    }

    portfolioCandidate?.let { candidate ->
        AddToPortfolioSheet(
            tx = candidate,
            address = address,
            viewModel = portfolioViewModel,
            onDismiss = { portfolioCandidate = null },
            onAdded = { addedPortfolioName = it },
        )
    }
    addedPortfolioName?.let { name ->
        PortfolioAddedSnackbar(name) { addedPortfolioName = null }
    }
}

/**
 * On-chain transaction history + UTXOs for the wallet's identity/chatting address, with direct
 * Send/Receive/Export - reached from the Profile screen's "Chatting Address" section's "Manage
 * Address" row. Field-for-field the same screen as [SpendingAddressTxHistoryScreen] (balance
 * header, Transaction History/UTXOs tabs, Compound UTXOs, Address Actions, Receive/Send), just
 * for the single fixed identity address instead of one spending-chain index - reuses the same
 * address-keyed StateFlows/functions on [WalletViewModel] (spendingAddressTxHistory/Utxos,
 * getSpendingUtxoLabels, etc. - all keyed by address string, nothing spending-chain-specific
 * about them) rather than duplicating a second copy of this state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityAddressDetailScreen(
    onBack: () -> Unit,
    viewModel: WalletViewModel,
    portfolioViewModel: com.kachat.app.viewmodels.PortfolioViewModel = hiltViewModel(),
    /** Message on a .kachat name's owner, from its detail. */
    /** Opened as the root of the "Received" History sheet (iOS 13046ad `OwnAddressHistorySheet`):
     *  Done closes it, top left, in place of the back arrow. */
    closesWithDone: Boolean = false,
) {
    val address by viewModel.address.collectAsState()
    val balanceSompi by viewModel.balanceSompi.collectAsState()

    val txHistory by viewModel.spendingAddressTxHistory.collectAsState()
    val isLoadingTxHistory by viewModel.loadingSpendingAddressTxHistory.collectAsState()
    val txHistoryFailed by viewModel.spendingAddressTxHistoryFailed.collectAsState()
    val utxos by viewModel.spendingAddressUtxos.collectAsState()
    val isLoadingUtxos by viewModel.loadingSpendingAddressUtxos.collectAsState()
    val utxosLoadError by viewModel.spendingAddressUtxosError.collectAsState()
    val utxoListAddress: String? = (address as String?)?.takeIf { it.isNotEmpty() }
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    // The identity key is at least as sensitive as the seed phrase itself (it IS the wallet's
    // main spending key) - gated behind the same biometric flag Settings > View Seed Phrase
    // uses, not the lower-stakes biometricSpendingKeyEnabled a derived spending address uses.
    val biometricSeedPhraseEnabled by viewModel.biometricSeedPhraseEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current
    // The tapped transaction, while its action chooser is up.
    var transactionActionTarget by remember { mutableStateOf<ColdStorageAddressDiscovery.AddressTransaction?>(null) }
    // The transaction being filed into a portfolio, if any - see [AddToPortfolioSheet].
    var portfolioCandidate by remember { mutableStateOf<ColdStorageAddressDiscovery.AddressTransaction?>(null) }
    var addedPortfolioName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    var showQr by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showCompoundFlow by remember { mutableStateOf(false) }
    var showPrivateKey by remember { mutableStateOf(false) }
    // The "Address Actions" sheet, and the public-key screen it can open.
    var showAddressActions by remember { mutableStateOf(false) }
    var showPublicKey by remember { mutableStateOf(false) }
    var utxoLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var labelingUtxoKey by remember { mutableStateOf<String?>(null) }
    var labelInput by remember { mutableStateOf("") }
    // The .kachat name opened from the Domains tab's .kachat screen (iOS 881ada6).
    var openedKachat by remember { mutableStateOf<com.kachat.app.services.kachatnames.NameInfo?>(null) }
    // The name service opened from the Domains tab (iOS fe52376).
    var openedDomainService by remember { mutableStateOf<com.kachat.app.services.NameServiceTLD?>(null) }

    LaunchedEffect(address) {
        val addr = address
        if (!addr.isNullOrEmpty()) {
            viewModel.loadSpendingAddressTxHistory(addr)
            viewModel.loadSpendingAddressUtxos(addr)
            utxoLabels = viewModel.getSpendingUtxoLabels(addr)
        }
    }

    // A .kachat name opened from this address's .kachat tab: its live detail, which knows which of
    // your addresses holds it (iOS 881ada6).
    openedKachat?.let { info ->
        AboveDock { KachatLiveNameDetailScreen(info, onBack = { openedKachat = null }) }
        return
    }
    // A name service opened from this address's Domains tab: its names (iOS fe52376).
    openedDomainService?.let { tld ->
        AboveDock {
            AddressDomainServiceScreen(
                address = address.orEmpty(),
                tld = tld,
                onBack = { openedDomainService = null },
                onOpenKachat = { openedKachat = it },
            )
        }
        return
    }

    val flowAddress = address
    // Send and Compound are sheets over this screen that only Cancel closes, as iOS presents
    // WithdrawKaspaView (.interactiveDismissDisabled()).
    if (showWithdraw && flowAddress != null) {
        IosFullSheet(
            onDismissed = {
                showWithdraw = false
                viewModel.loadSpendingAddressTxHistory(flowAddress)
                viewModel.loadSpendingAddressUtxos(flowAddress)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = flowAddress,
                balanceSompi = balanceSompi,
                title = tr(R.string.kp_send_kaspa_5e5c4),
                viewModel = viewModel,
                portfolioViewModel = portfolioViewModel,
                onDone = close,
                presentedAsSheet = true,
                lockedSource = SendSource.Chatting,
            )
        }
    }

    if (showCompoundFlow && flowAddress != null) {
        IosFullSheet(
            onDismissed = {
                showCompoundFlow = false
                viewModel.loadSpendingAddressTxHistory(flowAddress)
                viewModel.loadSpendingAddressUtxos(flowAddress)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = flowAddress,
                balanceSompi = balanceSompi,
                title = tr(R.string.kp_send_kaspa_5e5c4),
                viewModel = viewModel,
                portfolioViewModel = portfolioViewModel,
                isCompoundMode = true,
                onDone = close,
                presentedAsSheet = true,
            )
        }
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        // Runs on under Receive / Send and the dock, as iOS's list runs on under its
        // safeAreaInset bar and tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr(R.string.kp_chatting_address_6d286), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    // At the root of the "Received" History sheet: Done, as iOS's
                    // OwnAddressHistorySheet puts it (13046ad); a back arrow everywhere else.
                    if (closesWithDone) {
                        SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    } else {
                        SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                actions = {
                    AddressOptionsButton(
                        onClick = { if (!address.isNullOrEmpty()) showAddressActions = true },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
        bottomBar = {
            AddressReceiveSendBar(
                receiveEnabled = !address.isNullOrEmpty(),
                sendEnabled = balanceSompi > 0L,
                onReceive = { showQr = true },
                onSend = { showWithdraw = true },
            )
        }
    ) { padding ->
        // What Receive / Send and the dock cover: the lists scroll clear of it.
        val barInset = padding.calculateBottomPadding()
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AddressPageBalanceHeader(balanceSompi)
            // Right above what it switches, the way Chats has it: History, UTXOs, Domains
            // (iOS b96d727/718b88c).
            UnderlineTabBar(
                titles = listOf(stringResource(R.string.address_tab_history), stringResource(R.string.utxos), stringResource(R.string.address_tab_domains)),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(top = 4.dp),
            )
            when (selectedTab) {
                0 -> AddressHistoryList(
                    txHistory = txHistory,
                    isLoading = isLoadingTxHistory,
                    failed = txHistoryFailed,
                    onRetry = { address?.let { viewModel.loadSpendingAddressTxHistory(it) } },
                    onTransactionClick = { transactionActionTarget = it },
                    bottomPadding = barInset,
                )
                2 -> AddressDomainsTab(address.orEmpty(), onOpenService = { openedDomainService = it }, bottomPadding = barInset)
                else -> {
                    // Compound counts only the coins a send can spend: not just spent, not an
                    // immature mining reward (iOS 1652437). Read again whenever the list is.
                    var spendableCoinCount by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(address, utxos) {
                        spendableCoinCount = address?.takeIf { it.isNotEmpty() }?.let { viewModel.spendableCoinCount(it) }
                    }
                    AddressUtxoList(
                        utxos = utxos,
                        isLoading = isLoadingUtxos,
                        loadError = utxosLoadError,
                        labels = utxoLabels,
                        onRetry = { utxoListAddress?.let { viewModel.loadSpendingAddressUtxos(it) } },
                        onCompound = { showCompoundFlow = true },
                        onRename = { key ->
                            labelingUtxoKey = key
                            labelInput = utxoLabels[key] ?: ""
                        },
                        bottomPadding = barInset,
                        spendableCoinCount = spendableCoinCount,
                    )
                }
            }
        }
    }

    val qrAddress = address
    if (showQr && !qrAddress.isNullOrEmpty()) {
        // iOS ChattingAddressManageView shows ChattingAddressQRView with its default caption -
        // the chatting caption Profile uses (since 492aa53: "for chatting and domains only").
        // In a sheet with Close leading, as iOS's ChattingAddressManageView presents it.
        QrPageSheet(onDismiss = { showQr = false }, white = true) {
            ChattingAddressQrPage(
                address = qrAddress,
                balanceSompi = balanceSompi,
                dismiss = QrPageDismiss.CLOSE,
                onDismiss = { showQr = false },
            )
        }
    }

    if (showAddressActions) {
        AddressActionsSheet(
            onViewPrivateKey = {
                showAddressActions = false
                if (biometricSeedPhraseEnabled) {
                    context.authenticateWithDeviceCredential(
                        title = "Unlock to View Private Key",
                        onSuccess = { showPrivateKey = true }
                    )
                } else {
                    showPrivateKey = true
                }
            },
            onViewPublicKey = {
                showAddressActions = false
                showPublicKey = true
            },
            onViewInExplorer = {
                showAddressActions = false
                address?.let { uriHandler.openUriOrToast(kaspaExplorer.addressUrl(it)) }
            },
            onDismiss = { showAddressActions = false },
        )
    }

    if (showPublicKey) {
        AddressPublicKeyOverlay(
            address = address.orEmpty(),
            note = tr(R.string.kp_this_is_the_public_half_of_6a371),
            onDismiss = { showPublicKey = false },
        )
    }

    if (showPrivateKey) {
        SpendingAddressPrivateKeyOverlay(privateKeyHex = viewModel.getPrivateKeyHex(), onDismiss = { showPrivateKey = false })
    }

    labelingUtxoKey?.let { key ->
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { labelingUtxoKey = null },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.rename_utxo), color = LocalAppColors.current.textPrimary) },
            text = {
                com.kachat.app.ui.theme.IosTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary,
                        unfocusedTextColor = LocalAppColors.current.textPrimary,
                        focusedBorderColor = KaspaTeal,
                        unfocusedBorderColor = LocalAppColors.current.textSecondary,
                        focusedLabelColor = KaspaTeal,
                        unfocusedLabelColor = LocalAppColors.current.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val addr = address ?: return@TextButton
                    viewModel.setSpendingUtxoLabel(addr, key, labelInput)
                    utxoLabels = viewModel.getSpendingUtxoLabels(addr)
                    labelingUtxoKey = null
                }) {
                    Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { labelingUtxoKey = null }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    transactionActionTarget?.let { tapped ->
        TransactionActionsSheet(
            tx = tapped,
            onOpenExplorer = {
                transactionActionTarget = null
                uriHandler.openUriOrToast(kaspaExplorer.txUrl(tapped.txId))
            },
            onAddToPortfolio = {
                transactionActionTarget = null
                portfolioCandidate = tapped
            },
            onDismiss = { transactionActionTarget = null },
        )
    }

    portfolioCandidate?.let { candidate ->
        AddToPortfolioSheet(
            tx = candidate,
            address = address.orEmpty(),
            viewModel = portfolioViewModel,
            onDismiss = { portfolioCandidate = null },
            onAdded = { addedPortfolioName = it },
        )
    }
    addedPortfolioName?.let { name ->
        PortfolioAddedSnackbar(name) { addedPortfolioName = null }
    }
}

/**
 * An address's public key, from the Address Actions sheet.
 *
 * Nothing is derived or unlocked to show this: a Kaspa P2PK address IS its public key in bech32,
 * so this decodes the address the screen already has. No FLAG_SECURE, no reveal gate and no
 * clipboard expiry either - unlike the private key alongside it, this is a value you hand out on
 * purpose. Mirrors iOS's `AddressPublicKeyView`.
 */
@Composable
internal fun AddressPublicKeyOverlay(address: String, note: String, onDismiss: () -> Unit) {
    val keyCopyHaptic = com.kachat.app.util.rememberHaptics()
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // null only for an address shape that carries no key (script-hash), which the wallet's own
    // chatting address never is.
    val publicKeyHex = remember(address) {
        runCatching {
            com.kachat.app.util.KaspaAddress.decode(address).second.joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    BackHandler(onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SheetBarBackButton(onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                Text(
                    tr(R.string.kp_public_key_590e3),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KaspaTeal.copy(alpha = 0.1f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.kp_safe_to_share_9107f), color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    note,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            if (publicKeyHex != null) {
                Text(
                    publicKeyHex,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .padding(16.dp),
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(publicKeyHex))
                    keyCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                    IosToasts.show("Public key copied")
                }) {
                    Icon(Icons.Default.ContentCopy, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.kp_copy_public_key_bbbfe), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    tr(R.string.kp_this_address_does_not_carry_a_981c0),
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * Reveals a single spending address's own derived private key - not the wallet's seed phrase -
 * so a specific address's spending capability can be exported/backed up without exposing the
 * rest of the wallet. Mirrors SeedPhraseScreen's reveal flow at the same sensitivity level
 * (FLAG_SECURE screenshot/recording block, tap-to-reveal with a 7s auto-hide timer) rather than
 * inventing a lighter-weight pattern for equally sensitive key material. The caller already
 * gates presentation behind biometrics, same as View Seed Phrase's own entry point.
 */
@Composable
private fun SpendingAddressPrivateKeyOverlay(privateKeyHex: String, onDismiss: () -> Unit) {
    val keyCopyHaptic = com.kachat.app.util.rememberHaptics()
    var revealed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(revealed) {
        if (revealed) {
            delay(7000)
            revealed = false
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                SheetBarButton(stringResource(R.string.done), onClick = onDismiss)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2C1E1E))
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Warning, null, tint = LocalAppColors.current.warning, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(R.string.security_warning),
                        color = LocalAppColors.current.warning,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr(R.string.kp_anyone_with_this_address_s_private_99e2e),
                        color = Color(0xFF948B8B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (!revealed) {
                Column(
                    modifier = Modifier
                        .clickable { revealed = true }
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        null,
                        tint = LocalAppColors.current.textSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        tr(R.string.kp_tap_to_reveal_private_key_711bc),
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text(
                    privateKeyHex,
                    color = LocalAppColors.current.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(LocalAppColors.current.surface)
                        .padding(16.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            if (revealed) {
                TextButton(onClick = {
                    copyPrivateKeyWithAutoWipe(context, privateKeyHex)
                    keyCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                    IosToasts.show("Private key copied. Clipboard will clear in 30s.")
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.copy_private_key_hex), color = KaspaTeal)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SpendingAddressTxHistoryRow(
    tx: ColdStorageAddressDiscovery.AddressTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    // iOS `direction(for:)` is nil - "Transaction", grey, no amount - when nothing left for
    // someone else (sent) or arrived here (received): a self-send or consolidation.
    val directionKnown = tx.amountSompi > 0
    val directionColor = when {
        !directionKnown -> colors.textSecondary
        tx.sent -> colors.danger
        else -> colors.success
    }
    // iOS `.formatted(date: .abbreviated, time: .shortened)`: "Sep 24, 2026 at 2:25 PM".
    val dateStr = tx.blockTimeMillis?.let {
        java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.US).format(java.util.Date(it))
    }

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // "arrow.up.circle.fill" / "arrow.down.circle.fill" at title2: a 22 disc, the arrow cut
        // out of it in the card's colour.
        FilledCircleArrow(up = directionKnown && tx.sent, color = directionColor, cutout = Color(0xFF1C1C1E), size = 22.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when {
                    !directionKnown -> tr(R.string.kp_transaction_1a7b7)
                    tx.sent -> tr(R.string.kp_sent_35f49)
                    else -> tr(R.string.kp_received_27548)
                },
                color = directionColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            MiddleEllipsisText(
                tx.txId,
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            dateStr?.let {
                Text(it, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (directionKnown) {
                Text(
                    "${if (tx.sent) "-" else "+"}%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, tx.amountSompi / 100_000_000.0),
                    color = directionColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            // What this transaction paid the network (iOS 9839906).
            transactionFeeText(tx.feeSompi)?.let {
                Text(it, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
            }
            // iOS "arrow.up.right.square" at caption, accent.
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = KaspaTeal, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun SpendingAddressUtxoRow(
    utxo: ColdStorageAddressDiscovery.AddressUtxo,
    label: String? = null,
    onRenameClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    // iOS `utxoRow`: the coin glyph at title2, the label / amount / outpoint, Coinbase, the pencil.
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (utxo.isCoinbase) {
            // "cube.fill"
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ViewInAr, null, tint = KaspaTeal, modifier = Modifier.requiredSize(28.dp))
            }
        } else {
            // "circle.grid.2x2.fill": four dots in a square.
            androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                val r = size.minDimension * 0.2f
                for (cx in listOf(0.27f, 0.73f)) for (cy in listOf(0.27f, 0.73f)) {
                    drawCircle(KaspaTeal, radius = r, center = Offset(size.width * cx, size.height * cy))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!label.isNullOrBlank()) {
                Text(
                    label,
                    color = KaspaTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, utxo.amountSompi / 100_000_000.0),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            MiddleEllipsisText(
                "${utxo.transactionId}:${utxo.index}",
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (utxo.isCoinbase) {
            Text(
                stringResource(R.string.coinbase),
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier.size(32.dp).clickable(onClick = onRenameClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Edit, stringResource(R.string.rename), tint = KaspaTeal, modifier = Modifier.size(21.dp))
        }
    }
}

/** iOS 26 `.insetGrouped` corner radius and the gap above a list's first section. */
private val InsetGroupedRadius = 26.dp
private val InsetGroupedTopGap = 35.dp

/**
 * One row of an iOS 26 `.insetGrouped` section: the section is a single rounded card 16 in from
 * each edge, so only the first row rounds its top corners and the last its bottom ones, and every
 * row after the first draws the inset hairline separator above itself (from [separatorStart] to
 * 16 short of the trailing edge).
 */
@Composable
private fun Modifier.insetGroupedCell(index: Int, count: Int, separatorStart: Dp = 16.dp): Modifier {
    val colors = LocalAppColors.current
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(
        topStart = if (first) InsetGroupedRadius else 0.dp,
        topEnd = if (first) InsetGroupedRadius else 0.dp,
        bottomStart = if (last) InsetGroupedRadius else 0.dp,
        bottomEnd = if (last) InsetGroupedRadius else 0.dp,
    )
    val divider = colors.divider
    return this
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(shape)
        .background(colors.surface)
        .then(
            if (first) Modifier
            else Modifier.drawBehind {
                val stroke = 0.5.dp.toPx().coerceAtLeast(1f)
                drawLine(
                    color = divider,
                    start = Offset(separatorStart.toPx(), stroke / 2f),
                    end = Offset(size.width - 16.dp.toPx(), stroke / 2f),
                    strokeWidth = stroke,
                )
            }
        )
}

/** A one-row `.insetGrouped` list - the loading, empty and failed states iOS puts in the list. */
@Composable
private fun InsetGroupedSingleRowList(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = InsetGroupedTopGap, bottom = 16.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .insetGroupedCell(0, 1)
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                content()
            }
        }
    }
}

/** "Balance" over the amount at the top of an address's page (iOS: caption, then title3 semibold). */
@Composable
private fun AddressPageBalanceHeader(balanceSompi: Long) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.balance),
            color = LocalAppColors.current.textSecondary,
            fontSize = 12.sp
        )
        Text(
            "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, balanceSompi / 100_000_000.0),
            color = LocalAppColors.current.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
    }
}

/**
 * An address page's History tab - iOS `transactionsList`: one `.insetGrouped` card of
 * transactions, or the spinner, "Could not load transactions." with Try Again, or "No
 * transactions yet." as the list's only row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressHistoryList(
    txHistory: List<ColdStorageAddressDiscovery.AddressTransaction>,
    isLoading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onTransactionClick: (ColdStorageAddressDiscovery.AddressTransaction) -> Unit,
    /** Extra room at the list's end for what floats over the page's foot (Receive / Send, the dock). */
    bottomPadding: Dp = 0.dp,
) {
    val colors = LocalAppColors.current
    when {
        isLoading && txHistory.isEmpty() -> InsetGroupedSingleRowList {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
            }
        }
        // A failed fetch is not an empty history. Saying "No transactions yet." here was a
        // confident answer about someone's money the app had not got.
        txHistory.isEmpty() && failed -> InsetGroupedSingleRowList {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(R.string.kp_could_not_load_transactions_4bd4e), color = colors.textSecondary, fontSize = 17.sp)
                Text(
                    stringResource(R.string.try_again),
                    color = KaspaTeal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
        }
        txHistory.isEmpty() -> InsetGroupedSingleRowList {
            Text(stringResource(R.string.no_transactions_yet), color = colors.textSecondary, fontSize = 17.sp)
        }
        else -> {
            // iOS's `.refreshable`: pull the list down to load the history again.
            val pullState = rememberPullToRefreshState()
            LaunchedEffect(pullState.isRefreshing) {
                if (pullState.isRefreshing) {
                    onRetry()
                    pullState.endRefresh()
                }
            }
            Box(Modifier.fillMaxSize().clipToBounds().nestedScroll(pullState.nestedScrollConnection)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = InsetGroupedTopGap, bottom = 16.dp + bottomPadding),
                ) {
                    // Some pages landed, some did not (iOS `historyIncomplete`): say so rather
                    // than letting a truncated list read as the whole history.
                    if (failed) {
                        item(key = "incomplete") {
                            Text(
                                tr(R.string.kp_some_transactions_could_not_be_loaded_c11c5),
                                color = colors.warning,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
                            )
                        }
                    }
                    itemsIndexed(txHistory, key = { _, tx -> tx.txId }) { i, tx ->
                        SpendingAddressTxHistoryRow(
                            tx = tx,
                            // Tapping a transaction asks what to do with it. It used to open the
                            // explorer outright, which left "Add to Portfolio" on a button most people
                            // never looked for.
                            onClick = { onTransactionClick(tx) },
                            // The separator starts under the text, past the 26 icon and its 12 gap.
                            modifier = Modifier.insetGroupedCell(i, txHistory.size, separatorStart = 54.dp),
                        )
                    }
                }
                if (pullState.verticalOffset > 0f || pullState.isRefreshing) {
                    PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

/**
 * An address page's UTXOs tab - iOS `utxosList`: Compound UTXOs as its own section with its
 * footer when there is more than one coin, then one `.insetGrouped` card of coins, or the
 * spinner, the load-failure row or "No UTXOs." as that section's only row.
 */
@Composable
private fun AddressUtxoList(
    utxos: List<ColdStorageAddressDiscovery.AddressUtxo>,
    isLoading: Boolean,
    loadError: String?,
    labels: Map<String, String>,
    onRetry: () -> Unit,
    onCompound: () -> Unit,
    onRename: (String) -> Unit,
    /** The Compound section's footer: iOS words it differently per page (spending addresses
     *  mention repeating it; the chatting address keeps the original line). */
    compoundFooter: String = stringResource(R.string.compound_utxos_description),
    /** Extra room at the list's end for what floats over the page's foot (Receive / Send, the dock). */
    bottomPadding: Dp = 0.dp,
    /** How many of these coins a send can spend (the chatting address, iOS 1652437
     *  `withdrawalSpendable(utxos).count`): Compound shows only for more than one. Null counts
     *  every listed coin. */
    spendableCoinCount: Int? = null,
) {
    val colors = LocalAppColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = InsetGroupedTopGap, bottom = 16.dp + bottomPadding),
    ) {
        if ((spendableCoinCount ?: utxos.size) > 1) {
            item(key = "compound") {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .insetGroupedCell(0, 1)
                            .clickable(onClick = onCompound)
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.CallMerge, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.compound_utxos),
                            color = KaspaTeal,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // The section footer.
                    Text(
                        compoundFooter,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp, bottom = 24.dp)
                    )
                }
            }
        }
        when {
            isLoading && utxos.isEmpty() -> item(key = "loading") {
                Box(
                    modifier = Modifier.insetGroupedCell(0, 1).padding(horizontal = 16.dp, vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                }
            }
            // The load-failure row when the node could not be asked, otherwise "No UTXOs.".
            utxos.isEmpty() -> item(key = "empty") {
                if (loadError != null) {
                    UtxoLoadFailureRow(detail = loadError, retry = onRetry, modifier = Modifier.insetGroupedCell(0, 1))
                } else {
                    Text(
                        stringResource(R.string.no_utxos),
                        color = colors.textSecondary,
                        fontSize = 17.sp,
                        modifier = Modifier.insetGroupedCell(0, 1).padding(horizontal = 16.dp, vertical = 15.dp),
                    )
                }
            }
            else -> itemsIndexed(utxos, key = { _, u -> "${u.transactionId}:${u.index}" }) { i, utxo ->
                val key = "${utxo.transactionId}:${utxo.index}"
                SpendingAddressUtxoRow(
                    utxo = utxo,
                    label = labels[key],
                    onRenameClick = { onRename(key) },
                    modifier = Modifier.insetGroupedCell(i, utxos.size, separatorStart = 54.dp),
                )
            }
        }
    }
}

/**
 * Receive and Send at the foot of an address's page (iOS `safeAreaInset(edge: .bottom)`): two
 * accent capsules 12 apart, 16 in and 16 off the bottom. Send is "arrow.up.circle.fill" and, with
 * nothing to send, disabled at half opacity as iOS draws it.
 */
@Composable
private fun AddressReceiveSendBar(
    receiveEnabled: Boolean,
    sendEnabled: Boolean,
    onReceive: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            // Above the dock the page runs under (0 where there is none).
            .padding(bottom = LocalDockInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AddressBarCapsule(Icons.Default.QrCode, stringResource(R.string.receive), FontWeight.SemiBold, receiveEnabled, onReceive, Modifier.weight(1f))
        AddressBarCapsule(null, stringResource(R.string.send), FontWeight.Bold, sendEnabled, onSend, Modifier.weight(1f))
    }
}

@Composable
private fun AddressBarCapsule(
    /** Null: iOS's "arrow.up.circle.fill" - a black disc with the arrow cut out in the accent. */
    icon: ImageVector?,
    title: String,
    weight: FontWeight,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp).alpha(if (enabled) 1f else 0.5f),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = KaspaTeal,
            contentColor = Color.Black,
            disabledContainerColor = KaspaTeal,
            disabledContentColor = Color.Black,
        )
    ) {
        if (icon != null) Icon(icon, null, modifier = Modifier.size(18.dp))
        else FilledCircleArrow(up = true, color = Color.Black, cutout = KaspaTeal, size = 17.dp)
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = weight)
    }
}

/** SF Symbols' "arrow.up.circle.fill" / "arrow.down.circle.fill": a [color] disc with the arrow
 *  cut out of it, drawn in [cutout] (what is behind the symbol). */
@Composable
private fun FilledCircleArrow(up: Boolean, color: Color, cutout: Color, size: Dp) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            if (up) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
            null,
            tint = cutout,
            modifier = Modifier.size(size * 0.68f),
        )
    }
}

/**
 * One spending address row in [ManageAddressesScreen] — shared by the main and "Hidden" sections,
 * differing only in whether [WalletService.SpendingAddressEntry.hidden] shows a hide or an unhide
 * swipe action. Hiding is a swipe-left action (matching Chats' swipe-to-delete and Cold Storage's
 * address rows) rather than a permanent icon button, since it's reached for less often than the
 * actions in the overflow menu. Unhiding is always available, but an address can't be hidden while
 * it still holds a balance or is the primary ("Pay in Kaspa") spending address — see
 * [WalletViewModel.setManageAddressHidden], which enforces the same rule as a backstop.
 *
 * Everything besides hide/unhide (copy, QR, set primary, rename) lives behind a single overflow
 * button's actions sheet rather than a row of icons, so the address itself has room to sit
 * on its own line instead of being squeezed by four icon buttons. Send/receive for a specific
 * address live in [SpendingAddressTxHistoryScreen] instead (reached via `onClick`).
 */
/** "Contains domain" capsule tag shown on spending/cold address rows that own at least one KNS
 *  domain — shared component ported from iOS's ContainsDomainTag. */
@Composable
fun ContainsDomainTag() {
    Text(
        text = tr(R.string.kp_contains_domain_8ba89),
        color = KaspaTeal,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(KaspaTeal.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun ManageAddressRow(
    entry: com.kachat.app.services.WalletService.SpendingAddressEntry,
    onClick: () -> Unit,
    onCopyClick: () -> Unit,
    onQrClick: () -> Unit,
    onRenameClick: () -> Unit,
    /** Row-menu "Hide Address" (iOS parity) — same flag the Address Visibility checklist edits.
     *  Null hides the menu entry (e.g. the picker variant of this row). */
    onHideClick: (() -> Unit)? = null,
    /** "Contains domain" tag — this address owns at least one KNS domain (batched lookup). */
    showsDomainTag: Boolean = false,
    /** A quantum-safe (KIP-22) row whose key a spend has put on chain - see [QuantumShield]. */
    quantumSpent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }

    // Swipe-to-hide was retired in favor of the bulk Address Visibility checklist (iOS parity).
    // iOS `addressRow`: a glass card (radius 18), padding 16, the text stacked 6 apart.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .manageAddressGlass()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // iOS: the label and, on a quantum row, its shield, 6 apart.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = entry.displayLabel,
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (entry.isQuantum) {
                    QuantumShield(spent = quantumSpent, funded = entry.balanceSompi > 0L)
                }
            }
            Text(
                text = "${entry.address.take(14)}...${entry.address.takeLast(6)}",
                color = LocalAppColors.current.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(
                text = "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, entry.balanceSompi / 100_000_000.0),
                color = LocalAppColors.current.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Three honest states, never a guess: "Used" is monotonic (persisted used-memory
                // plus live probes, so a confirmed-used address can never flip back) and a
                // balance counts as used, "Unused" only when THIS load's live check actually
                // confirmed it, and a neutral "Checking" (iOS's word) when the probe failed or
                // the row was painted from the snapshot - a failed check must not masquerade as
                // a fresh address.
                val (usedTagText, usedTagColor) = when {
                    entry.everUsed || entry.balanceSompi > 0 -> tr(R.string.kp_used_02c0e) to LocalAppColors.current.warning
                    entry.liveChecked -> tr(R.string.kp_unused_41369) to LocalAppColors.current.success
                    else -> tr(R.string.kp_checking_97876) to LocalAppColors.current.textSecondary
                }
                Text(
                    text = usedTagText,
                    color = usedTagColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (showsDomainTag) {
                    ContainsDomainTag()
                }
            }
        }
        // iOS: a bold "ellipsis" turned upright, secondary, in a 24 frame.
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = { showMenu = true })
                .onGloballyPositioned { coords ->
                    menuAnchor = coords.positionInWindow() + Offset(0f, coords.size.height.toFloat())
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MoreVert, "Address actions", tint = LocalAppColors.current.textSecondary, modifier = Modifier.requiredSize(28.dp))
        }
    }

    // Half sheet rather than a popup menu (Cold Storage parity): the rows have room to say what
    // each one does, which a menu of bare verbs cannot.
    if (showMenu) {
        ManageAddressActionsSheet(
            title = entry.displayLabel,
            subtitle = entry.address,
            onDismiss = { showMenu = false },
        ) {
            ActionSheetRow(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.rename_address),
                subtitle = "Gives this address a label of your own.",
            ) { showMenu = false; onRenameClick() }
            ActionSheetRow(
                icon = Icons.Default.ContentCopy,
                title = stringResource(R.string.copy_address),
                subtitle = "Puts the full address on the clipboard.",
            ) { showMenu = false; onCopyClick() }
            ActionSheetRow(
                icon = Icons.Default.QrCode,
                title = stringResource(R.string.show_qr_code),
                subtitle = "Full screen, for scanning with another device.",
            ) { showMenu = false; onQrClick() }
            // Hide straight from the row - same effect as unchecking it in Address Visibility,
            // without opening that screen. Any address, funded too: it comes back from there
            // (iOS 245a5d2).
            if (onHideClick != null) {
                ActionSheetRow(
                    icon = Icons.Default.VisibilityOff,
                    title = "Hide Address",
                    subtitle = "Removes it from this list. Re-enable it in Address Visibility.",
                    tint = LocalAppColors.current.warning,
                ) { showMenu = false; onHideClick() }
            }
        }
    }
}

/**
 * The quantum (KIP-22) state of a Manage Addresses row, told honestly (iOS `quantumShield`):
 * - shield: the key has never been on chain (receiving reveals nothing) - quantum-safe.
 * - slashed shield: this address SPENT once, so its key is public forever; orange when funds sit
 *   on it again (move them to a fresh address), gray when it is empty.
 * iOS draws SF Symbols "shield.fill" / "shield.slash.fill" at .caption2 (11 pt).
 */
@Composable
private fun QuantumShield(spent: Boolean, funded: Boolean) {
    if (spent) {
        Icon(
            Icons.Filled.RemoveModerator,
            contentDescription = stringResource(R.string.key_revealed_by_a_spend),
            tint = if (funded) LocalAppColors.current.warning else LocalAppColors.current.textSecondary,
            modifier = Modifier.size(11.dp),
        )
    } else {
        Icon(
            Icons.Filled.Shield,
            contentDescription = stringResource(R.string.quantum_safe),
            tint = LocalAppColors.current.success,
            modifier = Modifier.size(11.dp),
        )
    }
}

/** Shared shell for Manage Addresses' per-row half sheets - name and address on top, then rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageAddressActionsSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = colors.background,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        subtitle,
                        color = colors.textSecondary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    content()
                }
            }
        }
    }
}

/**
 * The chatting address's row at the top of [ManageAddressesScreen] (iOS 6333dd3
 * `chattingAddressRow`): "Chatting Address" with its icon, the short address, its balance and
 * "Used", and a chevron - it opens the chatting address's own screen.
 */
@Composable
private fun ManageChattingAddressRow(address: String, balanceSompi: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .manageAddressGlass()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.chatting_address),
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(Icons.Default.Forum, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(14.dp))
            }
            Text(
                text = "${address.take(14)}...${address.takeLast(6)}",
                color = LocalAppColors.current.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(
                text = "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, balanceSompi / 100_000_000.0),
                color = LocalAppColors.current.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = tr(R.string.kp_used_02c0e),
                color = LocalAppColors.current.warning,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        // iOS: "chevron.right" at caption size, secondary.
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LocalAppColors.current.textSecondary,
            modifier = Modifier.size(20.dp).offset(x = 6.dp)
        )
    }
}

/**
 * iOS ManageAddressesView `glassBackground(cornerRadius: 18)` behind the address cards:
 * `.regularMaterial` (it reads #202020 on the black list), a 0.8 white hairline at 18% and a soft
 * shadow (black 12%, radius 10, 5 down).
 */
@Composable
private fun Modifier.manageAddressGlass(): Modifier {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(18.dp)
    val fill = if (colors.isDark) Color(0xFF202020) else colors.surface
    return this
        .iosShadow(18.dp, Color.Black.copy(alpha = 0.12f), 10.dp, 5.dp)
        .clip(shape)
        .background(fill)
        .border(0.8.dp, Color.White.copy(alpha = 0.18f), shape)
}

/**
 * A .kas domain's own profile, from Your Domains > the domain > Customize Profile (iOS 09e0403):
 * the full editor, loaded for that domain on its own and saved to it. The app no longer SHOWS
 * .kas profiles, but a domain's profile is still its owner's to change.
 */
@Composable
fun KnsDomainProfileEditorScreen(
    domain: com.kachat.app.services.KnsAsset,
    viewModel: WalletViewModel,
    onBack: () -> Unit,
) {
    val loading by viewModel.editingDomainLoading.collectAsState()
    val current by viewModel.editingDomain.collectAsState()
    LaunchedEffect(domain.assetId) { viewModel.beginEditingDomain(domain) }
    DisposableEffect(Unit) { onDispose { viewModel.endEditingDomain() } }
    BackHandler(onBack = onBack)
    // The fields seed once per domain, so they wait for its profile rather than seeding empty.
    if (loading || current?.assetId != domain.assetId) {
        Box(Modifier.fillMaxSize().background(LocalAppColors.current.background), contentAlignment = Alignment.Center) {
            com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
        }
        return
    }
    EditKnsProfileScreen(viewModel = viewModel, onBack = onBack, editingDomain = domain)
}

@Composable
private fun InscribeProgressRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = LocalAppColors.current.textPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditKnsProfileScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    /** Edit THIS .kas domain's profile (from Your Domains) rather than the primary's - see
     *  [KnsDomainProfileEditorScreen]. */
    editingDomain: com.kachat.app.services.KnsAsset? = null,
) {
    val primaryProfile by viewModel.knsProfile.collectAsState()
    val domainProfile by viewModel.editingDomainProfile.collectAsState()
    val knsProfile = if (editingDomain != null) domainProfile else primaryProfile
    val primaryDomainName by viewModel.activeProfileDomainName.collectAsState()
    val activeProfileDomainName = editingDomain?.asset ?: primaryDomainName
    val ownedDomainAssets by viewModel.ownedDomainAssets.collectAsState()
    val pendingAvatarUri by viewModel.pendingAvatarUri.collectAsState()
    val pendingBannerUri by viewModel.pendingBannerUri.collectAsState()
    val avatarCleared by viewModel.avatarCleared.collectAsState()
    val bannerCleared by viewModel.bannerCleared.collectAsState()
    val editState by viewModel.editProfileState.collectAsState()
    val showSetupGuides by viewModel.showSetupGuides.collectAsState()
    val deviceAuthContext = LocalContext.current

    var bio by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var telegram by remember { mutableStateOf("") }
    var discord by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var github by remember { mutableStateOf("") }
    var redirect by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    // The domain the fields were last read from. A KNS profile belongs to a DOMAIN, so promoting a
    // different one to primary swaps which avatar, banner and details are in effect - and these
    // fields used to seed exactly once, so the screen kept showing the old domain's values until
    // it was closed and reopened. Keyed on the domain rather than on the profile object so an
    // ordinary refresh of the SAME profile never overwrites what the user is part-way through
    // typing.
    var seededDomain by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(knsProfile, activeProfileDomainName) {
        if (!seeded || seededDomain != activeProfileDomainName) {
            bio = knsProfile?.bio ?: ""
            x = knsProfile?.x ?: ""
            website = knsProfile?.website ?: ""
            telegram = knsProfile?.telegram ?: ""
            discord = knsProfile?.discord ?: ""
            email = knsProfile?.contactEmail ?: ""
            github = knsProfile?.github ?: ""
            redirect = knsProfile?.redirectUrl ?: ""
            // Staged image picks belonged to the previous domain's profile.
            if (seeded) viewModel.clearPendingProfileImages()
            seeded = true
            seededDomain = activeProfileDomainName
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetEditProfileState() }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) viewModel.setPendingAvatar(uri) }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) viewModel.setPendingBanner(uri) }

    var showSaveDialog by remember { mutableStateOf(false) }

    // What "Save" is actually about to submit — each entry becomes its own on-chain commit/reveal
    // transaction, so this doubles as both the confirm dialog's change list and its cost count.
    val pendingChanges = remember(bio, x, website, telegram, discord, email, github, redirect, pendingAvatarUri, pendingBannerUri, avatarCleared, bannerCleared, knsProfile) {
        buildList {
            if (pendingAvatarUri != null) add(tr(R.string.kp_avatar_7631b))
            else if (avatarCleared && !knsProfile?.avatarUrl.isNullOrEmpty()) add("Avatar (removed)")
            if (pendingBannerUri != null) add(tr(R.string.kp_banner_c8af8))
            else if (bannerCleared && !knsProfile?.bannerUrl.isNullOrEmpty()) add("Banner (removed)")
            if (bio.trim() != (knsProfile?.bio ?: "")) add(tr(R.string.kp_bio_b31fc))
            if (x.trim() != (knsProfile?.x ?: "")) add("X")
            if (website.trim() != (knsProfile?.website ?: "")) add(tr(R.string.kp_website_2e8a5))
            if (telegram.trim() != (knsProfile?.telegram ?: "")) add("Telegram")
            if (discord.trim() != (knsProfile?.discord ?: "")) add("Discord")
            if (email.trim() != (knsProfile?.contactEmail ?: "")) add(tr(R.string.kp_email_84add))
            if (github.trim() != (knsProfile?.github ?: "")) add("GitHub")
            if (redirect.trim() != (knsProfile?.redirectUrl ?: "")) add(tr(R.string.kp_redirect_58cfc))
        }
    }

    val inFlight = editState.step !in listOf(
        WalletViewModel.EditProfileStep.IDLE,
        WalletViewModel.EditProfileStep.SUCCESS,
        WalletViewModel.EditProfileStep.PARTIAL_FAILURE,
        WalletViewModel.EditProfileStep.FAILED
    )

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.edit_kns_profile), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarButton(stringResource(R.string.cancel), onClick = onBack, enabled = !inFlight, modifier = Modifier.padding(start = 12.dp))
                },
                actions = {
                    SheetBarButton(
                        stringResource(R.string.save),
                        onClick = { if (pendingChanges.isNotEmpty()) showSaveDialog = true },
                        enabled = !inFlight && pendingChanges.isNotEmpty(),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Re-enters the same guided wizard used to create a profile from scratch - it
            // already knows (via the domain/profile it fetches) to offer skipping domain
            // registration and pre-fill the banner/avatar/detail steps with whatever's already
            // inscribed, so this is a safe re-entry point regardless of how much of a profile
            // already exists. Lives here (rather than next to "KNS Profile" on the Profile tab)
            // since that spot sits directly beside the banner image, which made it unclickable
            // whenever a banner was set.
            // No setup guide here since 5.2 (iOS b000310): the guide is .kachat's, in Edit .kachat
            // Profile. A .kas profile is edited field by field below.

            SettingsSection(title = stringResource(R.string.avatar)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ContactAvatar(
                        imageUrl = if (avatarCleared) null else (pendingAvatarUri?.toString() ?: knsProfile?.avatarUrl),
                        fallbackText = activeProfileDomainName ?: "?",
                        size = 64.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { avatarPicker.launch("image/*") }, enabled = !inFlight) {
                            Text(stringResource(R.string.choose_avatar), color = KaspaTeal, fontWeight = FontWeight.Bold)
                        }
                        if (pendingAvatarUri != null || (!avatarCleared && !knsProfile?.avatarUrl.isNullOrEmpty())) {
                            TextButton(
                                onClick = {
                                    if (pendingAvatarUri != null) viewModel.setPendingAvatar(null) else viewModel.clearExistingAvatar()
                                },
                                enabled = !inFlight
                            ) {
                                // iOS KNSProfileEditorSheet: Label("Remove Avatar", systemImage: "trash").
                                Icon(Icons.Default.Delete, contentDescription = null, tint = LocalAppColors.current.danger, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(R.string.kp_remove_avatar_70929), color = LocalAppColors.current.danger)
                            }
                        }
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.banner)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val previewUrl = if (bannerCleared) null else (pendingBannerUri?.toString() ?: knsProfile?.bannerUrl)
                    if (previewUrl != null) {
                        SubcomposeAsyncImage(
                            model = previewUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LocalAppColors.current.surface)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { bannerPicker.launch("image/*") }, enabled = !inFlight) {
                            Text(stringResource(R.string.choose_banner), color = KaspaTeal, fontWeight = FontWeight.Bold)
                        }
                        if (pendingBannerUri != null || (!bannerCleared && !knsProfile?.bannerUrl.isNullOrEmpty())) {
                            TextButton(
                                onClick = {
                                    if (pendingBannerUri != null) viewModel.setPendingBanner(null) else viewModel.clearExistingBanner()
                                },
                                enabled = !inFlight
                            ) {
                                // iOS KNSProfileEditorSheet: Label("Remove Banner", systemImage: "trash").
                                Icon(Icons.Default.Delete, contentDescription = null, tint = LocalAppColors.current.danger, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(R.string.kp_remove_banner_7fd70), color = LocalAppColors.current.danger)
                            }
                        }
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.profile)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditProfileTextField(stringResource(R.string.bio), bio, { bio = it }, enabled = !inFlight, singleLine = false)
                    EditProfileTextField(stringResource(R.string.x), x, { x = it }, enabled = !inFlight)
                    EditProfileTextField(stringResource(R.string.website), website, { website = it }, enabled = !inFlight)
                    EditProfileTextField(stringResource(R.string.telegram), telegram, { telegram = it }, enabled = !inFlight)
                    EditProfileTextField(stringResource(R.string.discord), discord, { discord = it }, enabled = !inFlight)
                    EditProfileTextField(stringResource(R.string.email), email, { email = it }, enabled = !inFlight)
                    EditProfileTextField(stringResource(R.string.github), github, { github = it }, enabled = !inFlight)
                    EditProfileTextField(stringResource(R.string.redirect), redirect, { redirect = it }, enabled = !inFlight)
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }

    if (showSaveDialog) {
        val terminal = editState.step in listOf(
            WalletViewModel.EditProfileStep.SUCCESS,
            WalletViewModel.EditProfileStep.PARTIAL_FAILURE,
            WalletViewModel.EditProfileStep.FAILED
        )
        fun closeDialog() {
            showSaveDialog = false
            viewModel.resetEditProfileState()
        }
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = {
                when (editState.step) {
                    WalletViewModel.EditProfileStep.IDLE -> showSaveDialog = false
                    else -> if (terminal) closeDialog()
                }
            },
            containerColor = LocalAppColors.current.surface,
            title = {
                Text(
                    when (editState.step) {
                        WalletViewModel.EditProfileStep.IDLE -> tr(R.string.kp_confirm_changes_192f5)
                        WalletViewModel.EditProfileStep.SUCCESS -> "Saved"
                        WalletViewModel.EditProfileStep.PARTIAL_FAILURE -> "Some Changes Failed"
                        WalletViewModel.EditProfileStep.FAILED -> "Save Failed"
                        else -> "Saving..."
                    },
                    color = LocalAppColors.current.textPrimary
                )
            },
            text = {
                when (editState.step) {
                    WalletViewModel.EditProfileStep.IDLE -> Column {
                        Text(
                            "${pendingChanges.size} change${if (pendingChanges.size == 1) "" else "s"}. Each is submitted as its own on-chain transaction from your chatting address:",
                            color = LocalAppColors.current.textPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        pendingChanges.forEach { Text("• $it", color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            KaspaUnit.label(stringResource(R.string.each_transaction_temporarily_uses_2_kas)),
                            color = LocalAppColors.current.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    WalletViewModel.EditProfileStep.UPLOADING_AVATAR,
                    WalletViewModel.EditProfileStep.UPLOADING_BANNER,
                    WalletViewModel.EditProfileStep.SUBMITTING_FIELD -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when (editState.step) {
                                WalletViewModel.EditProfileStep.UPLOADING_AVATAR -> tr(R.string.kp_uploading_avatar_dd3d9)
                                WalletViewModel.EditProfileStep.UPLOADING_BANNER -> tr(R.string.kp_uploading_banner_a4581)
                                else -> "Submitting ${editState.currentFieldLabel}..."
                            },
                            color = LocalAppColors.current.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    WalletViewModel.EditProfileStep.SUCCESS -> Text(
                        if (editState.fieldResults.isEmpty()) "Nothing to save." else "All changes saved.",
                        color = LocalAppColors.current.success,
                        fontWeight = FontWeight.Bold
                    )
                    WalletViewModel.EditProfileStep.PARTIAL_FAILURE -> Column {
                        Text(stringResource(R.string.some_changes_failed_to_save), color = LocalAppColors.current.danger, fontWeight = FontWeight.Bold)
                        editState.fieldResults.filter { !it.success }.forEach {
                            Text("${it.fieldKey}: ${it.errorMessage ?: "failed"}", color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    WalletViewModel.EditProfileStep.FAILED -> Text(
                        editState.fieldResults.firstOrNull { !it.success }?.errorMessage ?: "Save failed",
                        color = LocalAppColors.current.danger
                    )
                }
            },
            confirmButton = {
                when (editState.step) {
                    WalletViewModel.EditProfileStep.IDLE -> TextButton(
                        onClick = {
                            // It spends Kaspa on fees, and the phone's lock comes first (iOS 953b255).
                            deviceAuthContext.authenticateWithDeviceCredential(
                                title = tr(R.string.kp_confirm_saving_this_profile_9c390),
                                onSuccess = {
                                    viewModel.saveKnsProfile(
                                        mapOf(
                                            "bio" to bio,
                                            "x" to x,
                                            "website" to website,
                                            "telegram" to telegram,
                                            "discord" to discord,
                                            "contactEmail" to email,
                                            "github" to github,
                                            "redirectUrl" to redirect
                                        )
                                    )
                                },
                            )
                        }
                    ) {
                        Text(stringResource(R.string.confirm), color = KaspaTeal, fontWeight = FontWeight.Bold)
                    }
                    WalletViewModel.EditProfileStep.SUCCESS,
                    WalletViewModel.EditProfileStep.PARTIAL_FAILURE,
                    WalletViewModel.EditProfileStep.FAILED -> TextButton(onClick = { closeDialog() }) {
                        Text(stringResource(R.string.done), color = KaspaTeal, fontWeight = FontWeight.Bold)
                    }
                    else -> {}
                }
            },
            dismissButton = {
                if (editState.step == WalletViewModel.EditProfileStep.IDLE) {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                    }
                }
            }
        )
    }
}

@Composable
private fun EditProfileTextField(label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean, singleLine: Boolean = true) {
    com.kachat.app.ui.theme.IosTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = LocalAppColors.current.textPrimary,
            unfocusedTextColor = LocalAppColors.current.textPrimary,
            disabledTextColor = LocalAppColors.current.textSecondary,
            focusedBorderColor = KaspaTeal,
            unfocusedBorderColor = LocalAppColors.current.textSecondary,
            focusedLabelColor = KaspaTeal,
            unfocusedLabelColor = LocalAppColors.current.textSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeedPhraseScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val secretCopyHaptic = com.kachat.app.util.rememberHaptics()
    var revealed by remember { mutableStateOf(false) }
    val mnemonic = remember { viewModel.getActiveMnemonic() ?: "" }
    // A passphrase (the "25th word") changes every key: the words alone restore a different,
    // empty wallet, so the backup screen must say so and show it (iOS 3076ab7, IOS-017).
    val passphrase = remember { viewModel.getActivePassphrase() }
    val privateKey = remember { viewModel.getPrivateKeyHex() }
    val words = remember { mnemonic.split(" ") }
    val context = LocalContext.current

    // Blocks screenshots and screen recording of the seed phrase / private key for as long as
    // this screen is on-screen (window-level flag, the standard Android mechanism - unlike iOS,
    // which has no API to block a screenshot outright, only to detect active screen recording
    // after the fact via UIScreen.isCaptured). Cleared on leaving so it doesn't leak onto other
    // screens.
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Auto-hides the words again after a window long enough to write them down (20 s, iOS 99ab88a;
    // the private key reveals stay at 7) instead of leaving them on screen indefinitely once
    // revealed — someone glancing at the phone later shouldn't still see them.
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(20_000)
            revealed = false
        }
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.seed_phrase), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold) },
                actions = {
                    SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { padding ->
        // iOS SeedPhraseView: one column, 24 apart, 16 from the edges, everything from the top down.
        val colors = LocalAppColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.warning.copy(alpha = 0.1f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.security_warning), color = colors.warning, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
                Text(stringResource(R.string.anyone_with_your_seed_phrase_can), color = colors.textSecondary, fontSize = 15.sp)
                Text(tr(R.string.kp_best_practice_is_to_write_your_5cf23), color = colors.textSecondary, fontSize = 15.sp)
            }

            if (passphrase != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = colors.warning, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.seed_phrase_also_uses_passphrase), color = colors.warning, fontSize = 15.sp)
                }
            }

            if (!revealed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IosSystemGray6)
                        .clickable { revealed = true }
                        .padding(60.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(40.dp))
                    Text(
                        stringResource(R.string.tap_to_reveal_seed_phrase),
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // Three even columns, as iOS's grid.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    words.chunked(3).forEachIndexed { rowIndex, rowWords ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowWords.forEachIndexed { columnIndex, word ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(IosSystemGray6)
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        "${rowIndex * 3 + columnIndex + 1}.",
                                        color = colors.textSecondary,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(18.dp),
                                    )
                                    Text(word, color = colors.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 15.sp, maxLines = 1)
                                }
                            }
                            repeat(3 - rowWords.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                // Under the words, with the same FLAG_SECURE window and 20-second auto-hide.
                if (passphrase != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(IosSystemGray6)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.seed_phrase_passphrase_label), color = colors.textSecondary, fontSize = 12.sp)
                        Text(passphrase, color = colors.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                    }
                }
            }

            if (revealed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Both copies go through the sensitive path: marked secret for the system
                    // clipboard and wiped 30 seconds later (copyPrivateKeyWithAutoWipe). Copying
                    // the phrase is how it reaches a password manager, which is a safer home for
                    // it than a photograph of a notebook (iOS f9beba1).
                    TextButton(onClick = {
                        copyPrivateKeyWithAutoWipe(context, mnemonic, label = "recovery phrase")
                        secretCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        IosToasts.show(tr(R.string.kp_words_copied_clipboard_will_clear_in_f6efe))
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr(R.string.kp_copy_12_or_24_words_6b94b), color = KaspaTeal)
                        }
                    }
                    TextButton(onClick = {
                        copyPrivateKeyWithAutoWipe(context, privateKey)
                        // iOS SettingsView: the haptic and the 30-second notice, as the phrase has.
                        secretCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        IosToasts.show(tr(R.string.kp_private_key_hex_copied_clipboard_will_ceebd))
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tag, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_private_key_hex), color = KaspaTeal)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    // 4.0 (matches iOS): null renders the settings HUB (one row per category); a key renders
    // just that category's items as its own page.
    sectionKey: String? = null,
    walletViewModel: WalletViewModel = sharedWalletViewModel(),
    connectionViewModel: ConnectionViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    /** KaPay shows Settings as its own dock tab (iOS wallet 040f91a `isDockTab`): no Done button,
     *  the large "Settings" title under the bar. */
    isDockTab: Boolean = false,
) {
    val balance by walletViewModel.fullBalance.collectAsState()
    val dotColorHex by connectionViewModel.dotColorHex.collectAsState()
    val biometricSeedPhraseEnabled by walletViewModel.biometricSeedPhraseEnabled.collectAsState()
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState()
    // The switch is only true when Android agrees: a stored "on" with the OS permission revoked
    // would show as enabled while nothing could ever arrive.
    val notificationSettingsContext = LocalContext.current
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    notificationSettingsContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
        // Only flip the setting on when Android actually agreed - otherwise the switch would
        // sit on with nothing behind it.
        if (granted) settingsViewModel.setNotificationsEnabled(true)
        // iOS: a refused permission says where to turn it on (the "Notifications Disabled" alert).
        else notificationPermissionDenied = true
    }
    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationSettingsContext.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, notificationSettingsContext.packageName)
            )
        }
    }
    if (notificationPermissionDenied) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { notificationPermissionDenied = false },
            title = { Text(tr(R.string.kp_notifications_disabled_5d140), color = LocalAppColors.current.textPrimary) },
            text = { Text(tr(R.string.kp_to_receive_notifications_please_enable_them_f626c), color = LocalAppColors.current.textPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    notificationPermissionDenied = false
                    notificationSettingsContext.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, notificationSettingsContext.packageName)
                    )
                }) { Text(tr(R.string.kp_open_settings_13463), color = KaspaTeal, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { notificationPermissionDenied = false }) {
                    Text(stringResource(R.string.cancel), color = KaspaTeal)
                }
            },
        )
    }
    val kaspaExplorer by settingsViewModel.kaspaExplorer.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        walletViewModel.refreshBalance()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = LocalAppColors.current.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (sectionKey != null) {
                // A category page is pushed inside the sheet's navigation stack: iOS's inline bar
                // with the back chevron on the left and the page's title in the middle - no
                // connection dot, no balance.
                SettingsSubpageBar(
                    title = settingsSectionTitle(sectionKey),
                    onBack = { navController.popBackStack() },
                )
            } else if (isDockTab) {
                // The Settings tab (iOS wallet 040f91a): the connection dot and the balance in the
                // bar, the backup strip, "Settings" large under it, no Done (iOS 953b255).
                MainPageHeader(
                    title = stringResource(R.string.settings),
                    balanceCopiesOnTap = true,
                    banner = { AccountBackupBanner(walletViewModel) { navController.navigate("account_backup") } },
                )
            } else {
            // iOS's Settings sheet toolbar: the connection dot and Done in glass, the balance
            // between them - no title.
            Row(
                modifier = Modifier
                    .background(LocalAppColors.current.background)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    // measured on the iPhone: the bar's centre 37 below the sheet's top
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .barGlass()
                        .clip(CircleShape)
                        .clickable { ConnectionStatusOverlayState.open() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(12.dp).background(Color(dotColorHex), CircleShape))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { BalanceLabelRow(balance, copyOnTap = true) }
                SheetBarButton(stringResource(R.string.done), onClick = { navController.popBackStack() })
            }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            // The hub keeps its own measured spacing; a category page is an iOS Form, its
            // sections spaced by SettingsFormSection.
            verticalArrangement = if (sectionKey == null) Arrangement.spacedBy(24.dp) else Arrangement.Top
        ) {
            // A Form's first header sits 4 under the inline bar.
            if (sectionKey != null) Spacer(Modifier.height(4.dp))
            if (sectionKey == null) {
                // Flat hub list (matches iOS): no section titles or descriptive text. Alphabetical,
                // with Delete Account and Log Out apart below (iOS 953b255). Its top 122 below the
                // sheet's, as on the iPhone. The dock tab's card sits right under the large
                // "Settings" title instead (13 below it on the iPhone, the header's own margin).
                if (!isDockTab) Spacer(Modifier.height(24.dp))
                Surface(
                    color = LocalAppColors.current.surface,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                    SettingsNavigationItem(stringResource(R.string.about), Icons.Outlined.Info, onClick = { navController.navigate("settings_section/about") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(tr(R.string.kp_account_name_db3b7), Icons.Outlined.Edit, onClick = { navController.navigate("settings_section/account_name") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(tr(R.string.kp_back_up_account_e033f), Icons.Outlined.Key, onClick = { navController.navigate("settings_section/back_up_account") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.connection), Icons.Outlined.CellTower, onClick = { navController.navigate("settings_section/connection") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.customization), Icons.Outlined.Brush, onClick = { navController.navigate("settings_section/customization") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.diagnostics), Icons.Outlined.MonitorHeart, onClick = { navController.navigate("settings_section/diagnostics") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.notifications), Icons.Outlined.NotificationsActive, onClick = { navController.navigate("settings_section/notifications") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.security), com.kachat.app.ui.theme.IosSymbols.LockShield, onClick = { navController.navigate("settings_section/security") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.storage), Icons.Outlined.Storage, onClick = { navController.navigate("settings_section/storage") })
                    }
                }
                // Delete Account and Log Out, their own section at the bottom - Log Out moved here
                // from the Wallet tab (iOS wallet 040f91a): a red label and its red icon, no
                // chevron.
                var showLogoutConfirmation by remember { mutableStateOf(false) }
                var showDeleteAccount by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                // The seed phrase or backup steps opened over the Delete warning, which they close
                // back to (iOS shows them as a sheet over it).
                var returnToDeleteAccount by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (returnToDeleteAccount) {
                        returnToDeleteAccount = false
                        showDeleteAccount = true
                    }
                }
                Surface(
                    color = LocalAppColors.current.surface,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsActionItem(tr(R.string.kp_delete_account_ee1b9), Icons.Outlined.Delete, LocalAppColors.current.danger, labelColor = LocalAppColors.current.danger) {
                            showDeleteAccount = true
                        }
                        SettingsDivider(inset = IosRowTextInset)
                        SettingsActionItem(stringResource(R.string.log_out), Icons.AutoMirrored.Filled.Logout, LocalAppColors.current.danger, labelColor = LocalAppColors.current.danger) {
                            showLogoutConfirmation = true
                        }
                    }
                }
                if (showDeleteAccount) {
                    val backupPendingAddresses by walletViewModel.accountBackupStatus.pendingAddresses.collectAsState()
                    val activeAddress by walletViewModel.address.collectAsState()
                    fun openOverWarning(route: String) {
                        unlockAccountBackup(context, biometricSeedPhraseEnabled) {
                            showDeleteAccount = false
                            returnToDeleteAccount = true
                            navController.navigate(route)
                        }
                    }
                    DeleteAccountSheet(
                        confirmSubtitle = "Removes this account and its data from this device.",
                        notBackedUp = activeAddress != null && activeAddress in backupPendingAddresses,
                        onViewSeedPhrase = { openOverWarning("seed_phrase") },
                        onBackUp = { openOverWarning("account_backup") },
                        onConfirm = {
                            // The phone's lock comes first, whatever the settings say (iOS 953b255).
                            context.authenticateWithDeviceCredential(
                                title = tr(R.string.kp_confirm_deleting_this_account_c4997),
                                onSuccess = { walletViewModel.deleteCurrentAccount() },
                            )
                        },
                        onDismiss = { showDeleteAccount = false },
                    )
                }
                // Half sheet rather than a dialog, matching every other chooser in the app - and
                // it gives the consequence a row of its own beside the action.
                if (showLogoutConfirmation) {
                    ConfirmActionSheet(
                        title = stringResource(R.string.log_out),
                        confirmTitle = stringResource(R.string.log_out),
                        confirmSubtitle = "Signs out of this account. Its data stays on this device.",
                        confirmIcon = Icons.AutoMirrored.Filled.Logout,
                        onConfirm = { walletViewModel.logout() },
                        onDismiss = { showLogoutConfirmation = false },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            if (sectionKey == "about") {
                AccountAboutSection(walletViewModel, navController)
            }

            if (sectionKey == "account_name") {
                AccountNameSection(walletViewModel)
            }

            if (sectionKey == "back_up_account") {
                // iOS 953b255 backUpAccountPage. Complete Your Backup only while it's owed:
                // finishing the steps clears the backup strip.
                val pendingAddresses by walletViewModel.accountBackupStatus.pendingAddresses.collectAsState()
                val settingsAddress by walletViewModel.address.collectAsState()
                if (settingsAddress != null && settingsAddress in pendingAddresses) {
                    SettingsFormSection(title = null, first = true) {
                        SettingsActionItem(tr(R.string.kp_complete_your_backup_b6031), Icons.Filled.GppMaybe, LocalAppColors.current.warning, labelColor = LocalAppColors.current.textPrimary) {
                            unlockAccountBackup(context, biometricSeedPhraseEnabled) { navController.navigate("account_backup") }
                        }
                    }
                }
                SettingsFormSection(title = null, first = settingsAddress !in pendingAddresses) {
                    SettingsActionItem(tr(R.string.kp_view_12_or_24_words_7b376), Icons.Outlined.Key, KaspaTeal, labelColor = LocalAppColors.current.textPrimary) {
                        unlockAccountBackup(context, biometricSeedPhraseEnabled) { navController.navigate("seed_phrase") }
                    }
                    // Without a passphrase, nothing would protect the seed if someone got past the
                    // PIN (iOS 4f5dfd2).
                    val accountHasPassphrase = remember(settingsAddress) { walletViewModel.getActivePassphrase() != null }
                    if (accountHasPassphrase && com.kachat.app.services.NfcCard.isAvailable(context)) {
                        SettingsDivider(inset = IosRowTextInset)
                        SettingsActionItem(tr(R.string.kp_back_up_to_nfc_card_b3b8a), Icons.Filled.Contactless, KaspaTeal, labelColor = LocalAppColors.current.textPrimary) {
                            unlockAccountBackup(context, biometricSeedPhraseEnabled) { navController.navigate("nfc_card_backup") }
                        }
                    }
                }
            }

            if (sectionKey == "customization") {
                // iOS customizationPage: one section - the three pickers.
                SettingsFormSection(title = stringResource(R.string.customization)) {
                    // Appearance / Language / Currency are shared with the accounts screen's App
                    // Settings (iOS AppWideCustomizationPickers).
                    AppWideCustomizationPickers(walletViewModel)
                    // No Customize Dock: KaChat Wallet's tabs are fixed.
                    // No Show Fee Estimate: it only governed the chat composers' fee label; the
                    // wallet always shows the fee wherever KAS is sent (owner's call, iOS 1314382).
                }
            }

            if (sectionKey == "security") {
                SettingsFormSection(title = stringResource(R.string.security)) {
                    // Shared with the accounts screen's App Settings (see AppSettingsScreen.kt) -
                    // every row is app-wide, so both entry points render the exact same items.
                    SecuritySettingsItems(walletViewModel = walletViewModel)
                }
            }

            if (sectionKey == "connection") {
                ConnectionHubSections(
                    explorerName = kaspaExplorer.displayName,
                    onOpenConnectionSettings = { navController.navigate("connection_settings") },
                    onOpenExplorer = { navController.navigate("kaspa_explorer_settings") },
                )
            }

            if (sectionKey == "notifications") {
                // The master switch, at the top of the page it governs (iOS NotificationsHubPage):
                // push carries wallet activity, group mentions, broadcasts and KaPosts too, so it
                // sits above the per-feature pages rather than inside one of them. No header and
                // no descriptive text, as on iOS (953b255).
                SettingsFormSection(title = null, first = true) {
                    SettingsSwitchItem(
                        stringResource(R.string.push_notifications),
                        notificationsEnabled && notificationPermissionGranted,
                    ) { enabled ->
                        if (enabled && !notificationPermissionGranted) {
                            requestNotificationPermission()
                        } else {
                            settingsViewModel.setNotificationsEnabled(enabled)
                        }
                    }
                }

                // KaPay's Notifications is one page (iOS wallet 59593af NotificationsHubPage): the
                // master switch above, then Address Activity, then sound and vibration.
                val addressActivityEnabled by settingsViewModel.addressActivityNotificationsEnabled.collectAsState()
                val soundEnabled by settingsViewModel.notificationSoundEnabled.collectAsState()
                val vibrationEnabled by settingsViewModel.notificationVibrationEnabled.collectAsState()
                SettingsFormSection(title = null) {
                    SettingsSwitchItem(stringResource(R.string.address_activity), addressActivityEnabled) {
                        settingsViewModel.setAddressActivityNotificationsEnabled(it)
                    }
                }
                SettingsFormSection(title = stringResource(R.string.sound_vibration)) {
                    SettingsSwitchItem(stringResource(R.string.play_sound), soundEnabled) {
                        settingsViewModel.setNotificationSoundEnabled(it)
                    }
                    SettingsDivider()
                    SettingsSwitchItem(stringResource(R.string.vibration), vibrationEnabled) {
                        settingsViewModel.setNotificationVibrationEnabled(it)
                    }
                }
            }

            if (sectionKey == "storage") {
                // Storage hub (iOS storagePage): Address Book Photos, Cache and the one cloud
                // provider, with no descriptive text (iOS 953b255). No message retention or
                // on-device message store: the wallet has none (iOS wallet 301ebfb).
                // Your own data, not cache: photos you assigned in the Address Book (iOS cda0d99).
                // Kept apart from Cache, whose "clear" must never delete anything you'd miss.
                run {
                    val addressBook = com.kachat.app.services.AddressBookManager.shared
                    var addressBookPhotoBytes by remember { mutableStateOf<Long?>(null) }
                    var confirmRemoveAddressBookPhotos by remember { mutableStateOf(false) }
                    val photoVersion = addressBook?.photoVersion ?: 0
                    LaunchedEffect(photoVersion) {
                        addressBookPhotoBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            addressBook?.photosBytesOnDevice() ?: 0L
                        }
                    }
                    SettingsFormSection(title = stringResource(R.string.on_this_device)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Badge, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stringResource(R.string.ab_photos_title), color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            val bytes = addressBookPhotoBytes
                            if (bytes != null) {
                                Text(
                                    com.kachat.app.util.IosByteCount.format(context, bytes),
                                    color = LocalAppColors.current.textSecondary,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
                                )
                            } else {
                                com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(16.dp), color = LocalAppColors.current.textSecondary, strokeWidth = 2.dp)
                            }
                        }
                        if ((addressBookPhotoBytes ?: 0L) > 0L) {
                            SettingsDivider(inset = IosRowTextInset)
                            Text(
                                stringResource(R.string.ab_photos_remove),
                                color = LocalAppColors.current.danger,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { confirmRemoveAddressBookPhotos = true }
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                    if (confirmRemoveAddressBookPhotos) {
                        ConfirmActionSheet(
                            title = stringResource(R.string.ab_photos_remove_question),
                            confirmTitle = stringResource(R.string.remove),
                            confirmSubtitle = stringResource(
                                R.string.ab_photos_frees,
                                com.kachat.app.util.IosByteCount.format(context, addressBookPhotoBytes ?: 0L)
                            ),
                            confirmIcon = Icons.Default.Delete,
                            onConfirm = { addressBook?.removeAllPhotos() },
                            onDismiss = { confirmRemoveAddressBookPhotos = false },
                        )
                    }
                }

                // Kept apart from the rest of Storage on purpose: cloud backup decides what happens
                // to things you would miss, and this decides what happens to things you would not.
                SettingsFormSection(title = null) {
                    SettingsNavigationItem(tr(R.string.kp_cache_50338), Icons.Outlined.Delete, onClick = {
                        navController.navigate("cache_settings")
                    })
                }

                // What Nextcloud is for in KaPay: a backup of the Address Book and the Portfolio
                // (iOS wallet 59593af).
                SettingsFormSection(title = stringResource(R.string.ncw_backup)) {
                    SettingsNavigationItem(stringResource(R.string.nextcloud), Icons.Default.Cloud, onClick = {
                        navController.navigate("storage_nextcloud")
                    })
                }
            }

            if (sectionKey == "diagnostics") {
                // Shared with the accounts screen's App Settings (see AppSettingsScreen.kt).
                DiagnosticsSettingsSections(settingsViewModel = settingsViewModel)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    }
}

/** A Settings category page's inline title - iOS's `.navigationTitle` of each page. */
@Composable
private fun settingsSectionTitle(sectionKey: String): String = when (sectionKey) {
    "about" -> stringResource(R.string.about)
    "account_name" -> tr(R.string.kp_account_name_db3b7)
    "back_up_account" -> tr(R.string.kp_back_up_account_e033f)
    "customization" -> stringResource(R.string.customization)
    "security" -> stringResource(R.string.security)
    "connection" -> stringResource(R.string.connection)
    "notifications" -> stringResource(R.string.notifications)
    "storage" -> stringResource(R.string.storage)
    "diagnostics" -> stringResource(R.string.diagnostics)
    else -> ""
}

/** Settings > About (iOS 953b255 `AccountAboutPage`): this account's created date, then the app's
 *  version, links and licenses. */
@Composable
private fun AccountAboutSection(walletViewModel: WalletViewModel, navController: NavController) {
    val context = LocalContext.current
    val address by walletViewModel.address.collectAsState()
    SettingsFormSection(title = null, first = true) {
        // When the account was actually created or imported on this device (see
        // WalletManager.accountAddedAt); an account that predates the stamp shows nothing rather
        // than a made-up date.
        val addedAtMillis = remember(address) { address?.let { walletViewModel.accountAddedAt(it) } }
        if (addedAtMillis != null) {
            // iOS's medium date and short time, in the app's language and the phone's 12/24-hour
            // setting ("Oct 17, 2026 at 1:20 AM", "17.10.2026, 01:20").
            val is24Hour = com.kachat.app.util.is24HourClock()
            ProfileAboutRow(
                stringResource(R.string.created),
                com.kachat.app.util.IosDateStyle.mediumDateShortTime(addedAtMillis, is24Hour),
            )
            ProfileAboutDivider()
        }
        // "5.0 (1)" through the betas, "5.0" at release - so anyone can say which build they are on.
        ProfileAboutRow(stringResource(R.string.version), com.kachat.app.util.AppVersion.display)
        ProfileAboutDivider()
        ProfileAboutRow(
            stringResource(R.string.website),
            "home.kachat.app",
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://home.kachat.app"))) } },
        )
        ProfileAboutDivider()
        ProfileAboutRow(
            stringResource(R.string.support_email),
            "kaspasilver@gmail.com",
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:kaspasilver@gmail.com"))) } },
        )
        ProfileAboutDivider()
        ProfileAboutRow(
            tr(R.string.kp_privacy_policy_9db10),
            null,
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://home.kachat.app/privacy/"))) } },
        )
        ProfileAboutDivider()
        ProfileAboutRow(
            stringResource(R.string.open_source_licenses),
            null,
            onClick = { navController.navigate("open_source_licenses") },
        )
    }
}

/** Settings > Account Name (iOS 953b255 `AccountNamePage`): your own label for this account, saved
 *  when you leave the page. */
@Composable
private fun AccountNameSection(walletViewModel: WalletViewModel) {
    val address by walletViewModel.address.collectAsState()
    val accountName by walletViewModel.accountName.collectAsState()
    var name by remember(address) { mutableStateOf(accountName.orEmpty()) }
    SettingsFormSection(title = null, first = true) {
        com.kachat.app.ui.theme.IosTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            placeholder = { Text(tr(R.string.kp_account_name_abe4d), color = LocalAppColors.current.textSecondary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = LocalAppColors.current.textPrimary),
        )
    }
    val currentName by rememberUpdatedState(name)
    val currentSaved by rememberUpdatedState(accountName)
    val currentAddress by rememberUpdatedState(address)
    DisposableEffect(Unit) {
        onDispose {
            val trimmed = currentName.trim()
            val saving = currentAddress
            if (saving != null && trimmed.isNotEmpty() && trimmed != currentSaved) {
                walletViewModel.renameAccount(saving, trimmed)
            }
        }
    }
}

/**
 * A Form button row with a leading symbol, both in [color] (iOS tints a Button's Label), and while
 * [busy] the activity indicator at the trailing edge. Disabled, it greys out as iOS's does.
 */
@Composable
private fun SettingsBusyActionItem(
    label: String,
    icon: ImageVector,
    busy: Boolean,
    enabled: Boolean,
    color: Color = KaspaTeal,
    onClick: () -> Unit,
) {
    val tint = if (enabled) color else LocalAppColors.current.textSecondary.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = tint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (busy) com.kachat.app.ui.theme.IosActivityIndicator(Modifier.size(20.dp))
    }
}

@Composable
fun SettingsSection(title: String?, headerAction: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    // iOS 26's inset-grouped Form section: the header in title case, 17pt semibold in the
    // secondary label colour (no longer iOS's old small uppercase), over a card with 26pt corners.
    Column {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppColors.current.textSecondary,
                    modifier = Modifier.weight(1f).padding(start = 16.dp, bottom = 8.dp)
                )
                headerAction?.invoke()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(LocalAppColors.current.surface)
        ) {
            content()
        }
    }
}

/** iOS Form spacing, measured on the iPhone: 28 from a card to the next section's header, 15
 *  from a footer to it. */
val IosFormSectionGap = 28.dp
val IosFormFooterGap = 15.dp

/**
 * One section of an iOS Form page: [SettingsSection] (header, card), the [footer] under the card
 * rather than in it, and the gap to the next section ([IosFormSectionGap], or [IosFormFooterGap]
 * after a footer). Lay these out in a plain Column (no spacedBy) - see [SettingsFormPage].
 */
@Composable
fun SettingsFormSection(
    title: String?,
    footer: String? = null,
    /** A footer that reports an outcome (iOS colours an error red, a success green). */
    footerColor: Color = Color.Unspecified,
    /** The page's first section: without a header, iOS still starts its card where a first
     *  header's card would sit. */
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // A section without a header keeps part of a header's room above its card on the
        // iPhone: 32 from the footer above it, against 23 here (measured) - and the whole of it
        // when it opens the page.
        if (title == null) Spacer(Modifier.height(if (first) 24.dp else 9.dp))
        SettingsSection(title = title, content = content)
        if (footer != null) {
            SettingsFormFooter(footer, color = footerColor)
            Spacer(Modifier.height(IosFormFooterGap))
        } else {
            Spacer(Modifier.height(IosFormSectionGap))
        }
    }
}

/** An iOS section footer under its card: 13pt secondary, 16 in, 7 below the card. */
@Composable
fun SettingsFormFooter(text: String, color: Color = Color.Unspecified) {
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = if (color != Color.Unspecified) color else LocalAppColors.current.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp)
    )
}

/**
 * A pushed settings page's bar, iOS's inline navigation bar: the back chevron on a glass disc at
 * the left, [title] centred in headline (17 semibold), and the page's own [trailing] items.
 * No connection dot and no balance - those are the Settings hub's alone.
 */
@Composable
fun SettingsSubpageBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    SheetNavBar(
        title = title,
        // Inside the Settings sheet the status bar is already consumed; on a full-screen page
        // (the accounts screen's App Settings) this keeps the bar below it.
        modifier = Modifier.background(LocalAppColors.current.background).statusBarsPadding(),
        leading = { SheetBarBackButton(onClick = onBack) },
        trailing = trailing,
    )
}

/**
 * A pushed settings page laid out as iOS's inset-grouped Form: [SettingsSubpageBar], then a
 * scrolling column of [SettingsFormSection]s 16 in from the edges, the first header 4 under the bar.
 */
@Composable
fun SettingsFormPage(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = { SettingsSubpageBar(title = title, onBack = onBack, trailing = trailing) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * iOS's inline Picker row (`.pickerStyle(.menu)` in a Form): [label] at the left, the selected
 * option in the accent colour with the menu's up/down chevrons at the right. A tap opens a small
 * menu of [options], a checkmark on the selected one.
 */
@Composable
fun <T> SettingsPickerItem(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = LocalAppColors.current.textPrimary,
            fontSize = 17.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    optionLabel(selected),
                    color = KaspaTeal,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.UnfoldMore, null, tint = KaspaTeal, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        leadingIcon = {
                            if (option == selected) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                            } else {
                                Spacer(Modifier.size(20.dp))
                            }
                        },
                        onClick = {
                            expanded = false
                            if (option != selected) onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

/**
 * iOS's `AppWideCustomizationPickers`: Appearance, Language and Currency as menu Pickers, shared by
 * Settings > Customization and the accounts screen's App Settings (one source of truth).
 */
@Composable
fun AppWideCustomizationPickers(walletViewModel: WalletViewModel) {
    val appearance by walletViewModel.appearance.collectAsState()
    val currencyCode by walletViewModel.currency.collectAsState()
    var language by remember { mutableStateOf(currentAppLanguage()) }

    SettingsPickerItem(
        label = stringResource(R.string.appearance),
        options = com.kachat.app.models.AppAppearance.entries,
        selected = appearance,
        optionLabel = {
            when (it) {
                com.kachat.app.models.AppAppearance.SYSTEM -> stringResource(R.string.appearance_system)
                com.kachat.app.models.AppAppearance.LIGHT -> stringResource(R.string.appearance_light)
                com.kachat.app.models.AppAppearance.DARK -> stringResource(R.string.appearance_dark)
            }
        },
        onSelect = { walletViewModel.setAppearance(it) },
    )
    SettingsDivider()
    SettingsPickerItem(
        label = stringResource(R.string.language),
        options = AppLanguage.entries,
        selected = language,
        optionLabel = { if (it == AppLanguage.SYSTEM) stringResource(R.string.appearance_system) else it.displayName },
        onSelect = {
            language = it
            applyAppLanguage(it)
        },
    )
    SettingsDivider()
    SettingsPickerItem(
        label = stringResource(R.string.currency),
        options = AppCurrency.entries,
        selected = AppCurrency.fromCode(currencyCode),
        optionLabel = { it.displayName },
        onSelect = { walletViewModel.setCurrency(it.code) },
    )
}

/**
 * SF Symbols' chevron.right as iOS draws it at a small text size: a thin open chevron of the
 * given glyph [height] (caption ~11, footnote ~12), in secondary grey.
 */
@Composable
private fun ProfileChevron(height: Dp, stroke: Dp) {
    val color = LocalAppColors.current.textSecondary
    androidx.compose.foundation.Canvas(modifier = Modifier.size(width = height * 0.55f, height = height)) {
        val inset = stroke.toPx() / 2f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(inset, inset)
            lineTo(size.width - inset, size.height / 2f)
            lineTo(inset, size.height - inset)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        )
    }
}

/**
 * Profile's Your Domains / Settings / Help cards (iOS yourDomainsSection, settingsSection,
 * helpSection): a card of its own each (glassBackground 18), padded 16, holding a Label - the
 * primary-coloured outline symbol, the body title 28 in from the symbol's edge - then, for Your
 * Domains, the count in secondary, and the caption chevron.
 */
@Composable
private fun ProfileLinkRow(label: String, icon: ImageVector, value: String? = null, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlass(18.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = colors.textPrimary, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = colors.textSecondary, fontSize = 17.sp, maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        ProfileChevron(height = 11.dp, stroke = 1.6.dp)
    }
}

/**
 * One row of Profile's About card (iOS aboutSection): the body label, then the value in secondary
 * grey (Support Email in the accent, as iOS draws a mail link) or, for a row with no value, the
 * caption-semibold chevron. Padded 16 all round.
 */
@Composable
private fun ProfileAboutRow(
    label: String,
    value: String?,
    valueColor: Color = LocalAppColors.current.textSecondary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LocalAppColors.current.textPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        if (value != null) {
            Text(value, color = valueColor, fontSize = 17.sp, maxLines = 1)
        } else {
            ProfileChevron(height = 11.dp, stroke = 2.dp)
        }
    }
}

/** iOS `Divider().padding(.leading, 16)`: a hairline from the row text to the card's right edge. */
@Composable
private fun ProfileAboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = LocalAppColors.current.divider,
        thickness = 0.5.dp,
    )
}

@Composable
fun SettingsSwitchItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = LocalAppColors.current.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        com.kachat.app.ui.theme.IosSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsNavigationItem(
    label: String,
    icon: ImageVector?,
    value: String = "",
    showIcon: Boolean = true,
    /** iOS tints a row's symbol with the accent; a Label given `.primary` draws it white. */
    iconTint: Color = KaspaTeal,
    /** iOS writes a link row's trailing value in `.caption` (12): pass 12.sp for that. */
    valueFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    valueColor: Color = LocalAppColors.current.textSecondary,
    onClick: () -> Unit = {},
) {
    // iOS 26 list row: 52pt tall, a 24pt symbol in its own column, the label lined up with the
    // separator that starts under it (see SettingsDivider(inset)).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon && icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(text = label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(text = value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontSize = valueFontSize, modifier = Modifier.padding(horizontal = 8.dp))
        }
        IosDisclosureChevron()
    }
}

/** iOS's disclosure chevron at the end of a navigation row. */
@Composable
fun IosDisclosureChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = LocalAppColors.current.textSecondary,
        modifier = Modifier.size(24.dp)
    )
}

/** Where a row's text starts when it has a leading symbol: 16 + 24 + 16. Separators inset to it. */
val IosRowTextInset = 56.dp

@Composable
fun SettingsActionItem(
    label: String,
    icon: ImageVector,
    color: Color,
    /** Defaults to [color]; set it when the label should read differently from the icon. */
    labelColor: Color = color,
    /** A row that opens a page (iOS's Danger Zone) carries the disclosure chevron. */
    showChevron: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = labelColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (showChevron) IosDisclosureChevron()
    }
}

@Composable
fun SettingsInfoItem(label: String, value: String, valueColor: Color = LocalAppColors.current.textSecondary, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, color = valueColor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SettingsDivider(inset: androidx.compose.ui.unit.Dp = 16.dp) {
    // iOS separators start where the row's text starts and stop short of the right edge.
    HorizontalDivider(modifier = Modifier.padding(start = inset, end = 16.dp), color = LocalAppColors.current.divider, thickness = 0.5.dp)
}

/**
 * Settings > Connection (iOS `ConnectionHubPage`, 0c1af03; 010167f): the Testnet switch first -
 * with the restart note while the running network differs, and no footer - then Connection
 * Settings, then Kaspa Explorer in its own section. Shared by the in-account
 * Settings page and the accounts screen's App Settings.
 */
@Composable
fun ConnectionHubSections(
    explorerName: String,
    onOpenConnectionSettings: () -> Unit,
    onOpenExplorer: () -> Unit,
    connectionViewModel: com.kachat.app.viewmodels.ConnectionViewModel = hiltViewModel(),
) {
    val selected by com.kachat.app.util.KaspaNetwork.selected.collectAsState()
    val isTestnet = selected == com.kachat.app.util.KaspaNetwork.Type.TESTNET
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val colors = LocalAppColors.current
    SettingsFormSection(title = stringResource(R.string.connection)) {
        // Mainnet <-> testnet, first in the section (no footer). The choice applies at the next
        // launch, all at once (iOS e6f664f): until then the running app stays on its network.
        // Each network keeps its own connection settings, so Connection Settings below shows
        // the chosen network's values - the ones the app uses after the restart.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Science, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(R.string.testnet),
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            com.kachat.app.ui.theme.IosSwitch(
                checked = isTestnet,
                onCheckedChange = { on ->
                    connectionViewModel.switchNetwork(if (on) com.kachat.app.util.KaspaNetwork.Type.TESTNET else com.kachat.app.util.KaspaNetwork.Type.MAINNET)
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
                onColor = colors.warning,
            )
        }
        if (selected != com.kachat.app.util.KaspaNetwork.launch) {
            SettingsDivider(inset = IosRowTextInset)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.warning, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(if (isTestnet) R.string.testnet_restart_to_testnet else R.string.testnet_restart_to_mainnet),
                    color = colors.textPrimary,
                    fontSize = 15.sp
                )
            }
        }
        SettingsDivider(inset = IosRowTextInset)
        SettingsNavigationItem(stringResource(R.string.connection_settings), Icons.Default.Language, onClick = onOpenConnectionSettings)
    }
    // Its own section, no header; the explorer's name in iOS's caption.
    SettingsFormSection(title = null) {
        SettingsNavigationItem(
            stringResource(R.string.kaspa_explorer),
            Icons.Default.Explore,
            explorerName,
            valueFontSize = 12.sp,
            onClick = onOpenExplorer,
        )
    }
}

@Composable
fun SettingsFooter(text: String) {
    // iOS section footer: 13pt secondary label.
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = LocalAppColors.current.textSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/**
 * The Security section's rows. The biometric toggles and Child Mode are app-wide, so the same
 * items render both in the in-account Settings > Security page and in the accounts screen's App
 * Settings sheet (see AppSettingsScreen.kt), one source of truth. Caller supplies the enclosing [SettingsSection] and the navigation to the Child Mode screen
 * (each host has its own NavHost).
 */
@Composable
fun SecuritySettingsItems(
    walletViewModel: WalletViewModel,
) {
    val biometricSeedPhraseEnabled by walletViewModel.biometricSeedPhraseEnabled.collectAsState()
    val biometricAccountLoginEnabled by walletViewModel.biometricAccountLoginEnabled.collectAsState()
    val biometricSpendingKeyEnabled by walletViewModel.biometricSpendingKeyEnabled.collectAsState()
    val securityContext = LocalContext.current
    // A biometric lock that can be switched off without passing the lock is not a lock - anyone
    // holding an unlocked phone could have turned all three off and then read the seed phrase.
    // So the switch itself is gated, in BOTH directions: turning one on is a claim about who is
    // holding the device too. The setting is only written after auth succeeds, so a cancelled
    // prompt leaves the switch exactly where it was rather than flipping and flipping back.
    fun gated(reason: String, apply: (Boolean) -> Unit): (Boolean) -> Unit = { enabled ->
        securityContext.authenticateWithDeviceCredential(
            title = reason,
            onSuccess = { apply(enabled) },
        )
    }
    SettingsSwitchItem(
        stringResource(R.string.biometrics_for_seed_phrase),
        biometricSeedPhraseEnabled,
        onCheckedChange = gated(stringResource(R.string.unlock_change_seed_phrase_lock)) {
            walletViewModel.setBiometricSeedPhraseEnabled(it)
        },
    )
    SettingsDivider()
    SettingsSwitchItem(
        stringResource(R.string.biometrics_for_account_login),
        biometricAccountLoginEnabled,
        onCheckedChange = gated(stringResource(R.string.unlock_change_account_login_lock)) {
            walletViewModel.setBiometricAccountLoginEnabled(it)
        },
    )
    SettingsDivider()
    SettingsSwitchItem(
        stringResource(R.string.biometrics_for_address_private_keys),
        biometricSpendingKeyEnabled,
        onCheckedChange = gated(stringResource(R.string.unlock_change_private_key_lock)) {
            walletViewModel.setBiometricSpendingKeyEnabled(it)
        },
    )
    // No Simple (Child) Mode: KaPay removed it (iOS wallet 59593af).
}

/**
 * Settings > Diagnostics as iOS's DiagnosticsSettingsPage: the export button (its explanation in
 * the row, under the title) in a "Diagnostics" section, then Verbose API Logging in a section of
 * its own with its footer. The export gathers app/device/settings/node-pool info and is
 * account-tolerant (see DiagnosticsExportService's no-active-account fallbacks), so the same
 * sections render in the in-account Settings and in the accounts screen's App Settings.
 */
@Composable
fun DiagnosticsSettingsSections(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    connectionViewModel: ConnectionViewModel = hiltViewModel(),
) {
    val diagnosticsExportState by settingsViewModel.diagnosticsExportState.collectAsState()
    val verboseApiLogging by connectionViewModel.verboseApiLogging.collectAsState()
    val context = LocalContext.current
    val diagnosticsExportInFlight = diagnosticsExportState.status == SettingsViewModel.ExportStatus.IN_PROGRESS
    // iOS reports a failed export as a toast; only a run started here is reported.
    var exportArmed by remember { mutableStateOf(false) }
    LaunchedEffect(diagnosticsExportState.status) {
        when (diagnosticsExportState.status) {
            SettingsViewModel.ExportStatus.IN_PROGRESS -> exportArmed = true
            SettingsViewModel.ExportStatus.FAILED -> if (exportArmed) {
                exportArmed = false
                IosToasts.error(diagnosticsExportState.message ?: tr(R.string.kp_failed_to_export_diagnostics_e3777))
            }
            else -> exportArmed = false
        }
    }

    SettingsFormSection(title = stringResource(R.string.diagnostics)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !diagnosticsExportInFlight) {
                    settingsViewModel.exportDiagnostics { uri ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, tr(R.string.kp_export_diagnostics_archive_c09a8)))
                    }
                }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // iOS: ladybug.fill in title3 and the title in body medium, both accent, 10 apart,
            // the spinner at the end while it runs; then the explanation in footnote.
            val tint = if (diagnosticsExportInFlight) KaspaTeal.copy(alpha = 0.5f) else KaspaTeal
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.export_diagnostics_archive),
                    color = tint,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (diagnosticsExportInFlight) com.kachat.app.ui.theme.IosActivityIndicator(Modifier.size(20.dp))
            }
            Text(
                stringResource(R.string.exports_app_device_info_connection_settings),
                color = LocalAppColors.current.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
    // Moved here from Connection Settings, as on iOS: it is a troubleshooting switch.
    SettingsFormSection(title = null) {
        SettingsSwitchItem(stringResource(R.string.verbose_api_logging), verboseApiLogging) {
            connectionViewModel.setVerboseApiLogging(it)
        }
    }
}

/**
 * Kaspa-logo total-balance row — Android port of iOS's BalanceToolbarLabel
 * (SettingsView.swift): 15dp Kaspa logo + full 8-decimal balance in bold with
 * tabular figures and secondary color, so the header balance reads identically
 * on every main page (via BalanceTopBarLabel below).
 */
@Composable
fun BalanceLabelRow(
    balance: String,
    modifier: Modifier = Modifier,
    /** Wallet and Settings: a tap copies the exact figure (iOS's balance toolbar label, "Balance
     *  copied to clipboard."), nothing while it's still a dash (iOS 953b255). */
    copyOnTap: Boolean = false,
) {
    val balanceClipboard = LocalClipboardManager.current
    val balanceHaptic = com.kachat.app.util.rememberHaptics()
    Row(
        modifier = modifier.then(
            if (copyOnTap) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (balance.startsWith("--")) return@clickable
                balanceClipboard.setText(AnnotatedString(balance.substringBefore(' ')))
                balanceHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                IosToasts.show(tr(R.string.kp_balance_copied_to_clipboard_877b1))
            } else Modifier
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_kaspa_logo),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = balance,
            color = LocalAppColors.current.textSecondary,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFeatureSettings = "tnum"
            )
        )
    }
}

/**
 * The activity's one [WalletViewModel], provided by `KaChatApp` around the onboarding screens and
 * MainShell. Inside a navigation destination a plain `hiltViewModel()` makes a new instance per
 * destination, each running its init again (balance refresh, push registration, a DataStore
 * write) and holding its own copy of state such as the account name; shared pieces that take a
 * WalletViewModel default to this one instead.
 */
val LocalSharedWalletViewModel = androidx.compose.runtime.staticCompositionLocalOf<WalletViewModel?> { null }

/** [LocalSharedWalletViewModel], or a `hiltViewModel()` where none is provided (a preview). */
@Composable
fun sharedWalletViewModel(): WalletViewModel = LocalSharedWalletViewModel.current ?: hiltViewModel()

/**
 * Self-contained variant for screens that don't already collect the wallet balance: the
 * whole hot wallet ([WalletViewModel.fullBalance]), refreshed on appearance and retried while
 * it's unknown (iOS 953b255 `BalanceToolbarLabel`).
 */
@Composable
fun BalanceTopBarLabel(
    modifier: Modifier = Modifier,
    copyOnTap: Boolean = false,
    walletViewModel: WalletViewModel = sharedWalletViewModel()
) {
    val balance by walletViewModel.fullBalance.collectAsState()
    val account by walletViewModel.address.collectAsState()
    LaunchedEffect(Unit) { walletViewModel.refreshBalance() }
    LaunchedEffect(account) { walletViewModel.hotWalletBalance.refreshUntilKnown() }
    BalanceLabelRow(balance, modifier, copyOnTap = copyOnTap)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusScreen(onBack: () -> Unit, viewModel: ConnectionViewModel = hiltViewModel()) {
    // The way into Connection Settings, from Actions (iOS 953b255).
    var showConnectionSettings by remember { mutableStateOf(false) }
    if (showConnectionSettings) {
        BackHandler { showConnectionSettings = false }
        ConnectionSettingsScreen(onBack = { showConnectionSettings = false }, viewModel = viewModel)
        return
    }
    val network by viewModel.network.collectAsState()
    // The indexer this launch is connected to (a Testnet switch waits for the next launch).
    val indexerUrl by viewModel.runningIndexerUrl.collectAsState()
    val pushIndexerUrl by viewModel.runningPushIndexerUrl.collectAsState()

    val activeNodes by viewModel.activeNodes.collectAsState()
    val allNodes by viewModel.allNodes.collectAsState()
    // Pool sections only make sense when the POOL is choosing the node. Pinned to a specific
    // node (the shipped default or the user's own), there is no pool at work - discovery is
    // off and the app only ever talks to that one address - so pool status, pool actions, and
    // the node lists would just show a pool of one. Null (not yet loaded) hides them too, so
    // a pinned user never sees them flash on screen entry.
    val nodeSelectionIsAutomatic by viewModel.nodeSelectionIsAutomatic.collectAsState()
    val showPoolSections = nodeSelectionIsAutomatic == true
    val status by viewModel.status.collectAsState()
    val dotColorHex by viewModel.dotColorHex.collectAsState()
    val lastSyncAt by viewModel.lastSyncAt.collectAsState()
    val nodeConnectionsBlocked by viewModel.nodeConnectionsBlocked.collectAsState()
    val scrollState = rememberScrollState()
    val isRefreshingPool by viewModel.isRefreshingPool.collectAsState()
    val isReconnecting by viewModel.isReconnecting.collectAsState()
    val poolActionsEnabled = !isRefreshingPool && !isReconnecting
    var showClearPoolConfirm by remember { mutableStateOf(false) }
    val connectionClipboard = LocalClipboardManager.current
    val connectionHaptic = com.kachat.app.util.rememberHaptics()
    val connectionContext = LocalContext.current
    // iOS: tapping an active node copies its host:port.
    val copyNodeEndpoint: (NodeInfo) -> Unit = { node ->
        connectionClipboard.setText(AnnotatedString(node.ip))
        connectionHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
        IosToasts.show(connectionContext.getString(R.string.node_endpoint_copied))
    }

    // status is derived from the exact same latency threshold as dotColorHex, so
    // the text here can never contradict the dot's color: green only says
    // Connected/Healthy, orange only says Degraded, red only says Disconnected/Unhealthy.
    val statusColor = Color(dotColorHex)
    val statusText = when (status) {
        ConnStatus.CONNECTED -> "Connected"
        ConnStatus.DEGRADED -> "Degraded"
        ConnStatus.DISCONNECTED -> "Disconnected"
    }
    val poolHealthText = when (status) {
        ConnStatus.CONNECTED -> "Healthy"
        ConnStatus.DEGRADED -> "Degraded"
        ConnStatus.DISCONNECTED -> "Unhealthy"
    }
    // "Verified" = currently reachable at all (Active or Suspect), a broader real count
    // than "Active" (Active additionally requires being in-sync and not recently failing).
    // Excludes nodes that haven't been probed even once yet (latency == "—") — those also
    // report as "Suspect" (see NodeRegistry.statusOf's lastProbe == null branch), but counting
    // them as "Verified" is misleading: right after a fresh launch/DNS-seed resolution this made
    // the whole pool look "Verified" before a single probe had actually completed.
    val verifiedCount = remember(allNodes) { allNodes.count { (it.status == "Active" || it.status == "Suspect") && it.latency != "—" } }

    if (showClearPoolConfirm) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showClearPoolConfirm = false },
            title = { Text(stringResource(R.string.clear_connection_pool_confirm_title), color = LocalAppColors.current.textPrimary) },
            text = { Text(stringResource(R.string.clear_connection_pool_confirm_message), color = LocalAppColors.current.textPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    showClearPoolConfirm = false
                    viewModel.clearPool()
                }) { Text(stringResource(R.string.clear), color = LocalAppColors.current.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearPoolConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = KaspaTeal, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    // iOS presents Connection Status as a sheet holding a Form (ConnectionStatusDetailView): the
    // grouped sheet palette, rounded top 10 below the status bar, the screen behind dimmed, an
    // inline bar with Done on glass.
    val primaryHost = activeNodes.firstOrNull()?.ip?.let { ip ->
        com.kachat.app.services.grpc.parseNodeAddress(ip)?.host ?: ip.substringAfter("://")
    }
    // iOS writes "138 ms" and colours it by speed: green under 100, the primary colour under
    // 200, orange under 500, red above.
    val latencyMs = activeNodes.firstOrNull()?.latency?.removeSuffix("ms")?.trim()?.toIntOrNull()
    val latencyColor = when {
        latencyMs == null -> LocalAppColors.current.textSecondary
        latencyMs < 100 -> LocalAppColors.current.success
        latencyMs < 200 -> LocalAppColors.current.textPrimary
        latencyMs < 500 -> LocalAppColors.current.warning
        else -> LocalAppColors.current.danger
    }
    // iOS shows the indexer's host alone (URLComponents.host): no scheme, port or path; "None"
    // when there is none (testnet, iOS wallet dd8afc4).
    fun hostOf(url: String) =
        if (url.isBlank()) tr(R.string.kp_none_6eef6) else url.substringAfter("://").substringBefore("/").substringBefore(":")
    val indexerHost = hostOf(indexerUrl)

    IosSheetPage(grouped = true) {
        SheetNavBar(
            title = stringResource(R.string.connection_status),
            trailing = { SheetBarButton(stringResource(R.string.done), onClick = onBack) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            SettingsSection(title = stringResource(R.string.connection_status)) {
                ConnectionStatusRow(stringResource(R.string.status), statusText, dot = statusColor)
                SettingsDivider()
                ConnectionStatusRow(
                    stringResource(R.string.protocol),
                    if (activeNodes.firstOrNull()?.ip?.let { com.kachat.app.services.grpc.parseNodeAddress(it)?.secure } == true) {
                        "gRPC (secure)"
                    } else {
                        "gRPC (plaintext)"
                    }
                )
                // Honest all-blocked explanation: only shown once the pool has concluded that
                // NO node answers at all on this network (see NodePoolManager's
                // nodeConnectionsBlocked doc) - a plain red "Disconnected" with no reason
                // otherwise reads as an app bug on firewalled/captive networks. Laid out as
                // iOS's "Node Network: Blocked" row with its caption under it.
                if (nodeConnectionsBlocked && status == ConnStatus.DISCONNECTED) {
                    SettingsDivider()
                    ConnectionStatusRow(tr(R.string.kp_node_network_aa907), tr(R.string.kp_blocked_99613), valueColor = LocalAppColors.current.warning)
                    Text(
                        stringResource(R.string.node_connections_blocked),
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }
                // iOS leaves the node and latency rows out until there is one.
                if (primaryHost != null) {
                    SettingsDivider()
                    ConnectionStatusRow(stringResource(R.string.connected_node), primaryHost, monospaced = true)
                }
                if (latencyMs != null) {
                    SettingsDivider()
                    ConnectionStatusRow(stringResource(R.string.latency), tr(R.string.kp_lld_ms_1fe6b, latencyMs), valueColor = latencyColor)
                }
                SettingsDivider()
                ConnectionStatusRow(stringResource(R.string.indexer), indexerHost, monospaced = true)
                // The push service, only when it isn't the indexer (iOS ConnectionStatusDetailView).
                if (pushIndexerUrl != indexerUrl) {
                    SettingsDivider()
                    ConnectionStatusRow(tr(R.string.kp_push_register_eb20b), hostOf(pushIndexerUrl), monospaced = true)
                }
                SettingsDivider()
                ConnectionStatusRow(stringResource(R.string.last_sync), lastSyncAt)
            }

            if (showPoolSections) {
                SettingsSection(title = stringResource(R.string.pool_status)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        PoolStatItem(stringResource(R.string.active), activeNodes.size.toString(), LocalAppColors.current.success)
                        PoolStatItem(stringResource(R.string.verified), verifiedCount.toString(), Color(0xFF2196F3))
                        PoolStatItem(stringResource(R.string.total), allNodes.size.toString(), LocalAppColors.current.textSecondary)
                    }
                    SettingsDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.pool_health), color = LocalAppColors.current.textPrimary)
                        Text(poolHealthText, color = statusColor)
                    }
                }
            }

            // iOS's Actions section: plain text buttons, a spinner in the row while it runs, no
            // toast; Clear asks first. The primary node's host is the section's footer.
            Column {
                SettingsSection(title = stringResource(R.string.actions)) {
                    // Refresh/Clear operate on pool discovery, which is off while pinned to a
                    // specific node - only Reconnect still means something there.
                    if (showPoolSections) {
                        ConnectionActionRow(
                            stringResource(R.string.refresh_pool),
                            color = KaspaTeal,
                            busy = isRefreshingPool,
                            enabled = poolActionsEnabled,
                        ) { viewModel.refreshPool() }
                        SettingsDivider()
                        ConnectionActionRow(
                            stringResource(R.string.clear_connection_pool),
                            color = LocalAppColors.current.danger,
                            busy = false,
                            enabled = poolActionsEnabled,
                        ) { showClearPoolConfirm = true }
                        SettingsDivider()
                    }
                    ConnectionActionRow(
                        stringResource(R.string.reconnect),
                        color = KaspaTeal,
                        busy = isReconnecting,
                        enabled = poolActionsEnabled,
                    ) { viewModel.reconnect() }
                    SettingsDivider()
                    SettingsNavigationItem(tr(R.string.kp_connection_settings_1c79b), null, onClick = { showConnectionSettings = true })
                }
                primaryHost?.let { primary ->
                    Text(
                        text = tr(R.string.kp_primary_020f6, primary),
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
                    )
                }
            }

            KaspaNodeQuickAccessSection(viewModel)

            if (showPoolSections) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(LocalAppColors.current.success, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.active_nodes), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textSecondary)
                    }
                    Text(text = activeNodes.size.toString(), color = LocalAppColors.current.textSecondary)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(LocalAppColors.current.surface)
                ) {
                    // iOS: an empty Active Nodes section says so, in italic secondary text.
                    if (activeNodes.isEmpty()) NodeListEmptyRow(stringResource(R.string.no_active_nodes))
                    activeNodes.forEachIndexed { index, node ->
                        Box(Modifier.clickable { copyNodeEndpoint(node) }) { ActiveNodeRow(node) }
                        if (index < activeNodes.size - 1) SettingsDivider()
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // iOS's All Nodes header: the server-rack glyph, then the count of every
                    // known node - the active ones included, as iOS lists them again here.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.all_nodes), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textSecondary)
                    }
                    Text(text = allNodes.size.toString(), color = LocalAppColors.current.textSecondary)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(LocalAppColors.current.surface)
                ) {
                    // allNodes is already sorted by state, then latency (NodePoolManager.publish),
                    // as iOS sorts its All Nodes list.
                    if (allNodes.isEmpty()) NodeListEmptyRow(stringResource(R.string.no_nodes_discovered))
                    allNodes.forEachIndexed { index, node ->
                        AllNodeRow(node)
                        if (index < allNodes.size - 1) SettingsDivider()
                    }
                }
            }


            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

/** The italic secondary line iOS's Active Nodes / All Nodes sections show while empty. */
@Composable
private fun NodeListEmptyRow(text: String) {
    Text(
        text,
        color = LocalAppColors.current.textSecondary,
        fontSize = 17.sp,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

/** A label/value row of iOS ConnectionStatusDetailView's first section: the label 17pt, the value
 *  trailing in the secondary colour (or [valueColor]) - a host name in monospaced caption, as iOS
 *  writes the node and indexer - and, for Status, a 10pt dot in [dot]'s colour before it. */
@Composable
private fun ConnectionStatusRow(
    label: String,
    value: String,
    valueColor: Color = LocalAppColors.current.textSecondary,
    monospaced: Boolean = false,
    dot: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = LocalAppColors.current.textPrimary, fontSize = 17.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        if (dot != null) {
            Box(modifier = Modifier.size(10.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            value,
            color = valueColor,
            fontSize = if (monospaced) 12.sp else 17.sp,
            fontFamily = if (monospaced) FontFamily.Monospace else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of iOS ConnectionStatusDetailView's Actions section: the label as a plain text button,
 *  and while [busy], the activity indicator at the trailing edge. Greyed out while disabled. */
@Composable
private fun ConnectionActionRow(label: String, color: Color, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (enabled) color else LocalAppColors.current.textSecondary.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (busy) com.kachat.app.ui.theme.IosActivityIndicator(Modifier.size(20.dp))
    }
}

/** Settings > Connection > Kaspa Explorer, iOS's KaspaExplorerSettingsView: the explorers under an
 *  "Explorer" header, a checkmark on the chosen one, and iOS's footer under the card. */
@Composable
fun KaspaExplorerSettingsScreen(onBack: () -> Unit, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val kaspaExplorer by settingsViewModel.kaspaExplorer.collectAsState()

    SettingsFormPage(title = stringResource(R.string.kaspa_explorer), onBack = onBack) {
        SettingsFormSection(title = stringResource(R.string.explorer)) {
            com.kachat.app.models.KaspaExplorer.entries.forEachIndexed { index, explorer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { settingsViewModel.updateKaspaExplorer(explorer) }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(explorer.displayName, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (kaspaExplorer == explorer) {
                        Icon(Icons.Default.Check, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                    }
                }
                if (index < com.kachat.app.models.KaspaExplorer.entries.lastIndex) {
                    SettingsDivider()
                }
            }
        }
    }
}

/**
 * A single dropdown for quickly picking which node to connect to: the default node (recommended),
 * automatic discovery, or any address already saved in the IP Address Book. Used by both
 * [ConnectionStatusScreen] (tap the status dot) and [ConnectionSettingsScreen] (above the
 * [AddressBookSection], which is where the address book itself is managed - adding, removing, and
 * labeling entries) - there's no free-text entry anymore, only this dropdown.
 */
@Composable
private fun KaspaNodeQuickAccessSection(viewModel: ConnectionViewModel) {
    val trustedNodeAddress by viewModel.trustedNodeAddress.collectAsState()
    val savedNodeAddresses by viewModel.savedNodeAddresses.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val normalizedTrusted = trustedNodeAddress.trim()
    val defaultAddress = com.kachat.app.repository.AppSettingsRepository.DEFAULT_TRUSTED_NODE_ADDRESS
    val matchingSaved = savedNodeAddresses.firstOrNull { it.address.trim() == normalizedTrusted }

    val selectedLabel = when {
        normalizedTrusted.isEmpty() -> stringResource(R.string.automatic_scan)
        normalizedTrusted == defaultAddress.trim() -> stringResource(R.string.default_recommended)
        matchingSaved != null -> matchingSaved.label.ifBlank { matchingSaved.address }
        else -> normalizedTrusted
    }

    // iOS: a Picker row in a Form - the "Kaspa Node" label, the current choice trailing in the
    // accent colour with the menu's up/down chevrons - and the explanation as the section's
    // footer, under the card rather than in it.
    Column {
    SettingsSection(title = stringResource(R.string.kaspa_node)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.kaspa_node),
                color = LocalAppColors.current.textPrimary,
                fontSize = 17.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            Spacer(Modifier.weight(1f))
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedLabel,
                        color = KaspaTeal,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.UnfoldMore, null, tint = KaspaTeal, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    // The shipped default node is a mainnet node - not offered on testnet (iOS bdae4b7).
                    val selectedNetwork by com.kachat.app.util.KaspaNetwork.selected.collectAsState()
                    if (selectedNetwork == com.kachat.app.util.KaspaNetwork.Type.MAINNET || normalizedTrusted == defaultAddress.trim()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.default_recommended)) },
                        onClick = {
                            expanded = false
                            viewModel.setTrustedNodeAddress(defaultAddress)
                        }
                    )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.automatic_scan)) },
                        onClick = {
                            expanded = false
                            viewModel.setTrustedNodeAddress("")
                        }
                    )
                    savedNodeAddresses.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.label.ifBlank { entry.address }) },
                            onClick = {
                                expanded = false
                                viewModel.setTrustedNodeAddress(entry.address)
                            }
                        )
                    }
                }
            }
        }
        if (normalizedTrusted.isNotEmpty()) {
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.connected_only_to_this_node), color = KaspaTeal, fontSize = 13.sp)
            }
        }
    }
    }
}

/**
 * "IP Address Book" - the full management UI (add/remove/label saved addresses), used only by
 * [ConnectionSettingsScreen], right below its own [KaspaNodeQuickAccessSection]. The trusted-node
 * override itself is now dropdown-only (default/automatic/a saved address) - no free-text entry -
 * so this section is just the address book, feeding entries into that dropdown.
 */
@Composable
private fun AddressBookSection(viewModel: ConnectionViewModel) {
    val savedNodeAddresses by viewModel.savedNodeAddresses.collectAsState()
    val pinnedNodeAddress by viewModel.trustedNodeAddress.collectAsState()
    val nodeBookHaptic = com.kachat.app.util.rememberHaptics()

    SettingsSection(title = stringResource(R.string.ip_address_book)) {
        var newLabel by remember { mutableStateOf("") }
        var newAddress by remember { mutableStateOf("") }
        var addError by remember { mutableStateOf<String?>(null) }
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current

        TextField(
            value = newLabel,
            onValueChange = { newLabel = it },
            placeholder = { Text(stringResource(R.string.label_optional), color = LocalAppColors.current.textTertiary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(50.dp).clip(RoundedCornerShape(12.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LocalAppColors.current.surfaceVariant,
                unfocusedContainerColor = LocalAppColors.current.surfaceVariant,
                focusedTextColor = LocalAppColors.current.textPrimary,
                unfocusedTextColor = LocalAppColors.current.textPrimary,
                cursorColor = KaspaTeal,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = newAddress,
                onValueChange = { newAddress = it; addError = null },
                placeholder = { Text(stringResource(R.string.host_port_or_grpcs_host), color = LocalAppColors.current.textTertiary) },
                modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = LocalAppColors.current.surfaceVariant,
                    unfocusedContainerColor = LocalAppColors.current.surfaceVariant,
                    focusedTextColor = LocalAppColors.current.textPrimary,
                    unfocusedTextColor = LocalAppColors.current.textPrimary,
                    cursorColor = KaspaTeal,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = {
                    val trimmed = newAddress.trim()
                    if (trimmed.isEmpty()) return@IconButton
                    if (com.kachat.app.services.grpc.parseNodeAddress(trimmed) == null) {
                        addError = "Enter as host:port or grpcs://host"
                    } else {
                        addError = null
                        viewModel.addSavedNodeAddress(newLabel.trim(), trimmed)
                        newLabel = ""
                        newAddress = ""
                    }
                },
                modifier = Modifier.size(40.dp).background(KaspaTeal, CircleShape)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
            }
        }
        addError?.let {
            Text(it, color = LocalAppColors.current.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (savedNodeAddresses.isEmpty()) {
            Text(
                stringResource(R.string.no_saved_addresses),
                color = LocalAppColors.current.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            savedNodeAddresses.forEachIndexed { index, entry ->
                if (index > 0) SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(entry.address))
                            nodeBookHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                            IosToasts.show("Node address copied.")
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (entry.label.isNotBlank()) {
                            Text(entry.label, color = LocalAppColors.current.textPrimary)
                            Text(
                                entry.address,
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(entry.address, color = LocalAppColors.current.textPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }
                    // Immediate delete (matching this screen's convention: row taps copy with a
                    // toast, no confirm dialogs anywhere), so a toast confirms the removal.
                    // Deleting the entry the Kaspa Node dropdown currently pins does NOT touch
                    // node selection: only the book entry goes, the pinned address stays set
                    // and the dropdown falls back to showing the raw address (see
                    // KaspaNodeQuickAccessSection's selectedLabel).
                    IconButton(onClick = {
                        viewModel.removeSavedNodeAddress(entry.id)
                        nodeBookHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        // iOS deleteSavedNodeAddress: which of the two cases it was.
                        IosToasts.show(
                            context.getString(
                                if (pinnedNodeAddress.trim().isNotEmpty() && pinnedNodeAddress.trim() == entry.address.trim()) R.string.node_removed_still_connected
                                else R.string.node_address_removed
                            )
                        )
                    }) {
                        // iOS: a red trash glyph, read out as "Delete saved address".
                        Icon(Icons.Default.Delete, contentDescription = tr(R.string.kp_delete_saved_address_94994), tint = LocalAppColors.current.danger)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(onBack: () -> Unit, viewModel: ConnectionViewModel = hiltViewModel()) {
    val network by viewModel.network.collectAsState()
    val knsApiUrl by viewModel.knsApiUrl.collectAsState()
    // The .kachat indexer (Domains), editable (iOS wallet 59593af).
    val indexerUrl by viewModel.indexerUrl.collectAsState()
    var editingKachatIndexerUrl by remember { mutableStateOf(false) }
    val kaspaRestApiUrl by viewModel.kaspaRestApiUrl.collectAsState()
    val pushIndexerUrl by viewModel.pushIndexerUrl.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            SettingsSubpageBar(
                title = stringResource(R.string.connection_settings),
                onBack = onBack,
                trailing = { SheetBarButton(stringResource(R.string.save), onClick = onBack) },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // One tab per kind of connection, so the page stays short as the app talks to more
            // services: push registration, the Kaspa node, name services, the block explorer
            // (iOS ee01f81). Every field keeps its value while you switch between them.
            // KaChat Wallet has no Indexer tab (no chats) and no Translation tab (no KaPosts) -
            // iOS wallet 783e13f. Their saved values are untouched: .kachat still reads the message
            // indexer's address, at its default.
            val connectionTabs = listOf(
                stringResource(R.string.connection_tab_push),
                stringResource(R.string.connection_tab_node),
                stringResource(R.string.connection_tab_domains),
                stringResource(R.string.connection_tab_explorer),
            )
            var selectedConnectionTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
            UnderlineTabBar(
                titles = connectionTabs,
                selectedIndex = selectedConnectionTab,
                onSelect = { selectedConnectionTab = it },
            )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
        ) {
            // Each section spaces itself, its footer under the card (SettingsFormSection).
            Spacer(Modifier.height(16.dp))
            when (selectedConnectionTab) {
            0 -> {
            SettingsFormSection(title = tr(R.string.kp_push_registration_8cbac)) {
                ConnectionUrlField(label = tr(R.string.kp_push_indexer_url_69a84), value = pushIndexerUrl, placeholder = NO_INDEXER_PLACEHOLDER)
            }

            // Verbose API Logging lives in Settings > Diagnostics, as on iOS.
            }
            1 -> {
            KaspaNodeQuickAccessSection(viewModel)
            Spacer(Modifier.height(24.dp))
            AddressBookSection(viewModel)
            }
            2 -> {
            // .kachat first: it is what every address field resolves before anything else (iOS 6ac48a7).
            // Names are read through this indexer when it serves this network's registry and keeps
            // up, else straight from the chain (KachatNamesRegistry.chooseSource) - editable so
            // anyone can run their own (iOS wallet 59593af), with where names are read from now.
            SettingsFormSection(title = stringResource(R.string.kn_kachat_names)) {
                ConnectionUrlField(
                    label = tr(R.string.kp_kachat_indexer_url_da526),
                    value = indexerUrl,
                    placeholder = NO_INDEXER_PLACEHOLDER,
                    onClick = { editingKachatIndexerUrl = true }
                )
                SettingsDivider()
                KachatNamesSourceRow()
            }
            // The other name services this app reads (see NameServicesClient). Shown, not
            // editable; the .kas URL follows the selected network.
            SettingsFormSection(title = stringResource(R.string.other_domains_title)) {
                val isMainnet = network.equals("mainnet", ignoreCase = true)
                ConnectionUrlField(label = ".kas", value = knsApiUrl)
                listOf(com.kachat.app.services.NameServiceTLD.K, com.kachat.app.services.NameServiceTLD.KASPA).forEach { tld ->
                    ConnectionUrlField(
                        label = tld.suffix,
                        value = tld.apiBaseUrl(isMainnet) ?: stringResource(R.string.not_available_on_this_network),
                    )
                }
            }
            }
            else -> {
            SettingsFormSection(title = stringResource(R.string.kaspa_explorer_api)) {
                ConnectionUrlField(label = tr(R.string.kp_kaspa_rest_api_url_ffe93), value = kaspaRestApiUrl)
            }
            }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
        }
    }

    // The .kachat indexer, typed in (iOS wallet 59593af): https only, blank for KaChat's own.
    if (editingKachatIndexerUrl) {
        var draft by remember { mutableStateOf(indexerUrl) }
        var rejected by remember { mutableStateOf(false) }
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { editingKachatIndexerUrl = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text(tr(R.string.kp_kachat_indexer_url_da526), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    com.kachat.app.ui.theme.IosTextField(
                        value = draft,
                        onValueChange = { draft = it; rejected = false },
                        placeholder = {
                            Text(
                                if (network.equals("mainnet", ignoreCase = true)) com.kachat.app.repository.AppSettingsRepository.DEFAULT_INDEXER_URL else NO_INDEXER_PLACEHOLDER,
                                color = LocalAppColors.current.textTertiary
                            )
                        },
                        singleLine = true,
                        isError = rejected,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            rejected -> "The URL must start with https://"
                            network.equals("mainnet", ignoreCase = true) -> "Leave blank for KaChat's own indexer"
                            else -> "Leave blank for none - testnet has no default indexer"
                        },
                        color = if (rejected) Color(0xFFE57373) else LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.setKachatIndexerUrl(draft)) editingKachatIndexerUrl = false else rejected = true
                }) { Text(tr(R.string.kp_save_efc00), color = KaspaTeal) }
            },
            dismissButton = {
                TextButton(onClick = { editingKachatIndexerUrl = false }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }
}

/**
 * "Reading names from": the .kachat source KachatNamesRegistry last chose (iOS wallet 59593af
 * `kachatSourceLabel`) - the indexer and its host, or the chain when the indexer isn't usable.
 */
@Composable
private fun KachatNamesSourceRow() {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr(R.string.kp_reading_names_from_e8cbd), color = colors.textPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (!com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) {
            Text(tr(R.string.kp_not_live_on_this_network_252c1), color = colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.End)
            return@Row
        }
        val vm: KachatLiveViewModel = hiltViewModel()
        val source by vm.registry.source.collectAsState()
        val refreshing by vm.registry.isRefreshing.collectAsState()
        val lastError by vm.registry.lastError.collectAsState()
        LaunchedEffect(Unit) { runCatching { vm.registry.refreshIfStale() } }
        Column(horizontalAlignment = Alignment.End) {
            when (val s = source) {
                is com.kachat.app.services.kachatnames.KachatNamesRegistry.Source.Indexer -> {
                    Text(tr(R.string.kp_indexer_e6397), color = colors.textSecondary, fontSize = 15.sp)
                    Text(runCatching { java.net.URI(s.base).host }.getOrNull() ?: s.base, color = colors.textSecondary, fontSize = 12.sp)
                }
                com.kachat.app.services.kachatnames.KachatNamesRegistry.Source.Chain -> {
                    Text(tr(R.string.kp_the_chain_1ada5), color = colors.textSecondary, fontSize = 15.sp)
                    Text(tr(R.string.kp_indexer_not_usable_right_now_e22da), color = colors.textSecondary, fontSize = 12.sp)
                }
                null -> if (refreshing) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary, modifier = Modifier.size(18.dp))
                } else {
                    Text(if (lastError == null) tr(R.string.kp_checking_494d0) else tr(R.string.kp_couldn_t_check_eded8), color = colors.textSecondary, fontSize = 15.sp)
                }
            }
        }
    }
}

/** An indexer field's text while it is blank - testnet has no default indexer (iOS wallet dd8afc4). */
private const val NO_INDEXER_PLACEHOLDER = "None - enter an indexer URL"

/** Read-only for now — editing these let a mistyped URL crash the whole app (fixed at the NetworkService layer too, but not editable at all is safer). */
@Composable
fun ConnectionUrlField(label: String, value: String, placeholder: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(16.dp)
    ) {
        Text(label, color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        // iOS shows an address in the body font, monospaced.
        val showPlaceholder = value.isBlank() && placeholder != null
        Text(
            text = if (showPlaceholder) placeholder.orEmpty() else value,
            color = if (showPlaceholder) LocalAppColors.current.textTertiary else LocalAppColors.current.textPrimary,
            fontSize = 17.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun ConnectionInfoRow(label: String, value: String, valueColor: Color = LocalAppColors.current.textSecondary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label == "Status") {
                Box(modifier = Modifier.size(8.dp).background(valueColor, CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PoolStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActiveNodeRow(node: NodeInfo) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // weight(1f) here (not on the status badge) so a long address — real discovered
            // peers can be IPv6 literals like [2601:680:cc80:5630:e1e5:e6fa:c86b:b946]:16111,
            // much longer than the old hardcoded IPv4 seeds — elides instead of squeezing the
            // badge down to near-zero width, which forced its own text to wrap letter by letter.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Shield, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(node.ip, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.background(Color(0xFF1E3A1E), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(node.status, color = Color(node.color), fontSize = 10.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row {
                Text(node.type, color = Color(0xFF2196F3), fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text(node.latency, color = LocalAppColors.current.warning, fontSize = 10.sp)
            }
            Text(tr(R.string.kp_daa_39148, node.daaScore), color = LocalAppColors.current.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun AllNodeRow(node: NodeInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // weight(1f) here (not on the latency/status side) so a long address — real discovered
        // peers can be IPv6 literals like [2601:680:cc80:5630:e1e5:e6fa:c86b:b946]:16111, much
        // longer than the old hardcoded IPv4 seeds — elides instead of squeezing the status
        // badge down to near-zero width, which forced its own text to wrap letter by letter.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.size(8.dp).background(Color(node.color), CircleShape))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Shield, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(node.ip, color = LocalAppColors.current.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(node.latency, color = LocalAppColors.current.warning, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.background(Color(0x33FF3B30), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(node.status, color = Color(node.color), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun rememberQrBitmapPainter(
    content: String,
    size: Int = 512,
    padding: Int = 0
): BitmapPainter {
    val density = LocalDensity.current
    val sizePx = with(density) { size.dp.roundToPx() }
    
    val bitmap = remember(content) {
        if (content.isEmpty()) {
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
        } else {
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                mapOf(EncodeHintType.MARGIN to padding)
            )
            val w = matrix.width
            val h = matrix.height
            // One setPixels() beats width*height individual bounds-checked setPixel() calls
            // (~262k for a 512px QR) by 1-2 orders of magnitude - this runs on the main thread
            // inside remember{}, and the animated (KSPT) variant re-encodes every 2.5s.
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    pixels[row + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.asImageBitmap()
        }
    }
    return BitmapPainter(bitmap)
}

/**
 * Byte-mode QR encoding for raw binary payloads (e.g. a KSPT transaction frame) — ISO-8859-1 maps
 * every byte 1:1 to a char and back, so wrapping [bytes] this way and forcing ZXing's
 * `CHARACTER_SET` hint round-trips the exact bytes through its String-based encoder in byte mode,
 * matching KasSigner's own raw-byte QR encoding (`qrcode::QrCode::new(&[u8])`) on the other end.
 */
@Composable
fun rememberQrBitmapPainter(
    bytes: ByteArray,
    size: Int = 512,
    padding: Int = 0
): BitmapPainter {
    val density = LocalDensity.current
    val sizePx = with(density) { size.dp.roundToPx() }

    val bitmap = remember(bytes) {
        if (bytes.isEmpty()) {
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
        } else {
            val content = String(bytes, Charsets.ISO_8859_1)
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                mapOf(EncodeHintType.MARGIN to padding, EncodeHintType.CHARACTER_SET to "ISO-8859-1")
            )
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    pixels[row + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            bmp.asImageBitmap()
        }
    }
    return BitmapPainter(bitmap)
}

/**
 * Cycles through [frames] at a fixed interval — the display half of KasSigner's animated multi-
 * frame QR protocol (see [com.kachat.app.util.QrFrameChunker]). A single-frame list just renders
 * a static code with no play/pause controls or frame counter.
 */
@Composable
fun AnimatedQrDisplay(frames: List<ByteArray>, modifier: Modifier = Modifier, frameDelayMs: Long = 2500L) {
    var frameIndex by remember(frames) { mutableStateOf(0) }
    // Matches KasSee's own 2.5s auto-advance (kassee/web/js/app.js's displayKsptQr) — a scanning
    // camera needs real time to lock onto and decode each frame; a faster cycle (this used to
    // default to 200ms) skips past frames before the scanner ever catches them.
    var isPlaying by remember(frames) { mutableStateOf(true) }

    LaunchedEffect(frames, isPlaying) {
        if (frames.size <= 1 || !isPlaying) return@LaunchedEffect
        while (true) {
            delay(frameDelayMs)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        val painter = rememberQrBitmapPainter(bytes = frames[frameIndex.coerceIn(frames.indices)])
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(2.dp, KaspaTeal, RoundedCornerShape(20.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(painter = painter, contentDescription = stringResource(R.string.qr_code_2), modifier = Modifier.fillMaxSize())
        }
        if (frames.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                frames.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            // Fixed, not theme-based — this always sits on a forced-white card
                            // (see ColdSendFlow), so the inactive dot needs to read against white
                            // specifically rather than whatever the app's own surfaceVariant is.
                            .background(if (i == frameIndex) KaspaTeal else Color(0xFFD0D0D5))
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Manual stepping works whether playing or paused — tapping prev/next doesn't
                // require pausing first, matching KasSee's own frame-nav buttons.
                IconButton(onClick = { frameIndex = (frameIndex - 1 + frames.size) % frames.size }) {
                    Icon(Icons.Default.SkipPrevious, "Previous frame", tint = KaspaTeal)
                }
                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = KaspaTeal
                    )
                }
                IconButton(onClick = { frameIndex = (frameIndex + 1) % frames.size }) {
                    Icon(Icons.Default.SkipNext, "Next frame", tint = KaspaTeal)
                }
                Text(tr(R.string.kp_frame_lld_lld_5cb10, frameIndex + 1, frames.size), color = Color(0xFF6B6B70), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** How an [AddressQrPage] is closed - iOS puts a different control in its bar for each way it
 *  presents the page. */
enum class QrPageDismiss {
    /** Pushed (Profile): a back chevron, leading. */
    BACK,
    /** In a sheet with Close leading (Chatting Address details). */
    CLOSE,
    /** In a sheet with Done trailing (the Chats New sheet). */
    DONE,
}

/**
 * iOS's `ChattingAddressQRView`: the full white page with an address's QR code - the code in a
 * 3pt accent frame, the whole address under it, what the address is for, and "Tap anywhere to
 * copy" - placed a third of the way down. Its bar shows the address's balance in the title's
 * place (iOS's principal toolbar item, which takes precedence over the navigation title), or "—"
 * while it is unknown, with [dismiss]'s control. A tap anywhere copies the address.
 */
@Composable
fun AddressQrPage(
    address: String,
    balanceSompi: Long?,
    caption: String,
    dismiss: QrPageDismiss,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = com.kachat.app.util.rememberHaptics()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                clipboardManager.setText(AnnotatedString(address))
                haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                com.kachat.app.util.showAddressCopiedToast(context, address, haptic = false)
            },
    ) {
        AddressQrPageBar(dismiss = dismiss, onDismiss = onDismiss) {
            Text(
                balanceSompi?.let { "%.8f %s".format(java.util.Locale.US, it / 100_000_000.0, KaspaUnit.symbol) } ?: "—",
                // iOS's `.primary`, as iOS draws it: the page forces no colour scheme, so in dark
                // mode the balance is the theme's white over the white page, as on the iPhone.
                color = LocalAppColors.current.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    // iOS strokes 3pt on the edge and clips it to the shape: 1.5 shows.
                    .border(1.5.dp, KaspaTeal, RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                Image(
                    painter = rememberQrBitmapPainter(address),
                    contentDescription = stringResource(R.string.qr_code),
                    modifier = Modifier.size(240.dp),
                )
            }
            Text(
                address,
                color = Color.Black,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Text(
                caption,
                color = Color.Black.copy(alpha = 0.6f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            Text(
                stringResource(R.string.tap_anywhere_to_copy),
                color = Color.Black.copy(alpha = 0.4f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The page's navigation bar: [dismiss]'s control and [center] in the title's place. */
@Composable
private fun AddressQrPageBar(dismiss: QrPageDismiss, onDismiss: () -> Unit, center: @Composable () -> Unit) {
    // A sheet's bar is 56 tall on iOS, a pushed page's 44.
    // iOS 26: each control on its own glass, 16 in from the edge.
    Box(Modifier.fillMaxWidth().height(if (dismiss == QrPageDismiss.BACK) 52.dp else 60.dp).padding(horizontal = 16.dp)) {
        // iOS's glass takes on what is under it: over the white page the control is light glass
        // with a black chevron, in dark mode too. (The balance keeps the theme's colour, below.)
        CompositionLocalProvider(LocalAppColors provides com.kachat.app.ui.theme.LightAppColors) {
            when (dismiss) {
                QrPageDismiss.BACK -> SheetBarBackButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart))
                QrPageDismiss.CLOSE -> SheetBarButton(stringResource(R.string.close), onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart))
                QrPageDismiss.DONE -> SheetBarButton(stringResource(R.string.done), onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        Box(Modifier.align(Alignment.Center)) { center() }
    }
}

/**
 * The chatting address's QR page ([AddressQrPage] with the chatting caption, iOS
 * `ChattingAddressQRView`'s default).
 */
@Composable
fun ChattingAddressQrPage(address: String, balanceSompi: Long?, dismiss: QrPageDismiss, onDismiss: () -> Unit) {
    AddressQrPage(
        address = address,
        balanceSompi = balanceSompi,
        caption = stringResource(R.string.qr_chatting_address_subtitle),
        dismiss = dismiss,
        onDismiss = onDismiss,
    )
}

/**
 * Receive Kaspa's page (iOS `ProfileView.ReceiveKaspaQRView`): a fresh, never-used receive address
 * ([address], resolved by the caller with WalletViewModel.resolveFreshReceiveAddress) on the white
 * QR page, its balance looked up once it is known. Until then the same page says "Preparing a
 * fresh address" under a spinner, on the app's own background - in place, not over everything -
 * or, once the lookup has finished with nothing, that the spending address is still unlocking.
 * Showing an old address for a frame and swapping it is worse, since a QR is scanned the instant
 * it appears. [title] is the bar's title while there is no code (the Chats sheet names it).
 */
@Composable
fun ReceiveKaspaQrPage(
    address: String?,
    resolving: Boolean,
    dismiss: QrPageDismiss,
    onDismiss: () -> Unit,
    walletViewModel: WalletViewModel,
    title: String? = null,
) {
    if (address != null) {
        // A failed lookup leaves the balance unknown ("—") rather than 0 KAS.
        val balanceSompi by produceState<Long?>(initialValue = null, address) {
            value = runCatching {
                val coins = walletViewModel.fetchUtxosForCoinControl(address)
                com.kachat.app.util.UtxoMath.checkedTotal(coins.map { it.utxoEntry.amount })
            }.getOrNull()
        }
        AddressQrPage(
            address = address,
            balanceSompi = balanceSompi,
            caption = stringResource(R.string.qr_receive_address_subtitle),
            dismiss = dismiss,
            onDismiss = onDismiss,
        )
        return
    }
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        AddressQrPageBar(dismiss = dismiss, onDismiss = onDismiss) {
            if (title != null) Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (resolving) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary)
                    Text(stringResource(R.string.receive_preparing_fresh_address), color = colors.textSecondary, fontSize = 15.sp)
                }
            } else {
                Text(
                    stringResource(R.string.spending_address_unlocking),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/**
 * A QR page presented the way iOS presents it from a sheet: a full-height sheet of its own over
 * the screen (the Chats New sheet's two, Chatting Address details' code). White while the code
 * shows, so the page reaches the sheet's edges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPageSheet(onDismiss: () -> Unit, white: Boolean, content: @Composable () -> Unit) {
    com.kachat.app.ui.theme.IosSheetColors(grouped = true) {
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = if (white) Color.White else LocalAppColors.current.background,
            dragHandle = null,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    IosToastHost(bottomInsets = SheetToastInsets)
                }
            }
        }
    }
}

/** True while a white page shows over a tab - the dock draws its glass light then. */
object DockOverLightPage {
    var active by mutableStateOf(false)
}

/**
 * A QR page pushed onto a screen (Profile's two): it covers that screen's content in place and
 * slides in from the right, the way iOS pushes it - the dock stays where it is, as iOS's tab bar
 * does - and Back pops it.
 */
@Composable
fun PushedQrPage(visible: Boolean, white: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    // The dock's glass goes light over the white page, as iOS's tab bar does.
    if (visible && white) {
        DisposableEffect(Unit) {
            DockOverLightPage.active = true
            onDispose { DockOverLightPage.active = false }
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInHorizontally { it },
        exit = androidx.compose.animation.slideOutHorizontally { it },
    ) {
        BackHandler(onBack = onBack)
        // The status bar keeps the theme's icons over the white page: iOS's status bar follows
        // the colour scheme (light in dark mode), not what is drawn under it.
        // The page's colour runs up under the status bar, as iOS's white page does - and down
        // under the dock: the tab's content stops above the dock, but iOS's page runs on behind
        // its tab bar, so paint past the bottom edge rather than leave a black band there.
        val pageColor = if (white) Color.White else LocalAppColors.current.background
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(pageColor, size = androidx.compose.ui.geometry.Size(size.width, size.height + 200.dp.toPx()))
            }
        ) {
            // Laid out above the dock, as iOS's page is inside its safe area; the colour runs on under it.
            Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = LocalDockInset.current)) { content() }
        }
    }
}

/** Full-bleed QR overlay over the current screen's content area — matches iOS's push-navigated
 *  QR screens (full-screen white, dismissed via a back arrow/system back, never by a stray tap).
 *  Tapping anywhere on the overlay content copies the address — the whole screen is the copy
 *  target, so there's no dedicated copy button, just the quiet hint under the address. [message],
 *  if given, renders as a caption below the code (e.g. funding guidance). */
@Composable
fun QrCodeOverlay(
    value: String,
    onDismiss: () -> Unit,
    message: String? = null,
    borderColor: Color = KaspaTeal,
    borderWidth: Dp = 2.dp,
    /**
     * How many lines the value text may take. Two suits an address; a kpub is roughly twice as
     * long and would be silently truncated, which is worse than wrapping when someone is
     * comparing one by eye.
     */
    valueMaxLines: Int = 2,
    /** "Done" top right instead of "Close" top left - the Chats New sheet's QR pages, which iOS
     *  shows in a sheet of their own with a Done button (e6400d6). */
    dismissAsDone: Boolean = false,
    /** The toast a tap shows, when [value] isn't an address - "kpub copied to clipboard." for
     *  a kpub (iOS ColdStorageView's kpub QR). Null: the standard address toast. */
    copiedToast: String? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val qrCopyHaptic = com.kachat.app.util.rememberHaptics()

    // Its own window, not a Box drawn into the current screen: as inline content, the app's
    // floating dock stayed on top of the code, which is both wrong to look at and something a
    // camera can read the edge of. A Dialog is above everything, and it takes system back.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
    // Full-screen white, not the app's own themed background — a bright, high-contrast quiet
    // zone around the code is what actually gets a reliable scan on another device's camera,
    // regardless of whether KaChat itself is in light or dark mode.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // The entire overlay is the copy target (no ripple — the whole screen flashing on
            // tap would look broken). The back arrow below still consumes its own taps, so
            // dismissal stays on the arrow / system back only.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                clipboardManager.setText(AnnotatedString(value))
                // iOS: every one of these full-screen QR copies plays the success haptic.
                qrCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                if (copiedToast != null) IosToasts.show(copiedToast)
                else com.kachat.app.util.showAddressCopiedToast(context, value, haptic = false)
            },
        contentAlignment = Alignment.Center
    ) {
        // "Close", not a back arrow: this is a modal you dismiss, not somewhere you navigated
        // to. Same word, same corner as iOS's toolbar button on every one of these.
        SheetBarButton(
            stringResource(if (dismissAsDone) R.string.done else R.string.close),
            onClick = onDismiss,
            modifier = Modifier
                .align(if (dismissAsDone) Alignment.TopEnd else Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val qrPainter = rememberQrBitmapPainter(value)
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = qrPainter,
                    contentDescription = stringResource(R.string.qr_code),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = value,
                color = Color.Black,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = valueMaxLines,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.tap_anywhere_to_copy),
                color = Color(0xFF9A9AA0),
                style = MaterialTheme.typography.bodySmall
            )
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    color = Color(0xFF6B6B70),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
    }
}

/** One row in the group-member address list - supports both a raw Kaspa address and a KNS domain, resolved the same way the single-contact flow's address field does. */
/**
 * "Other domains": what the same typed name points to on the other name services, under the name
 * it resolved to, each one selectable (iOS a0dbc15, 6ac48a7 `OtherDomainsDropdown`). Every field
 * that takes an address shows it; the order is always .kachat first. Unregistered, unreachable or
 * not-yet-live ones are listed but not selectable. Opens by itself when nothing was picked but
 * another service has the name.
 */
@Composable
fun OtherDomainsDropdown(
    resolutions: List<com.kachat.app.services.NameResolution>,
    selected: com.kachat.app.services.NameServiceTLD?,
    onPick: (com.kachat.app.services.NameResolution) -> Unit,
) {
    val others = resolutions.filter { it.tld != selected }
    if (others.isEmpty()) return
    val openByItself = selected == null && resolutions.any { it.address != null }
    var expanded by remember(resolutions) { mutableStateOf(openByItself) }
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        // iOS DisclosureGroup: the label on the left, its chevron at the row's end, pointing right
        // while closed and turning down when open.
        val chevronTurn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 90f else 0f, label = "otherDomainsChevron")
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.other_domains), color = KaspaTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = KaspaTeal,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = chevronTurn }
            )
        }
        if (expanded) {
            others.forEach { resolution ->
                val address = resolution.address
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = address != null) {
                            onPick(resolution)
                            expanded = false
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            resolution.display,
                            color = if (address == null) colors.textSecondary else colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        MiddleEllipsisText(
                            address ?: stringResource(
                                when {
                                    resolution.notLive -> R.string.coming_soon
                                    resolution.failed -> R.string.couldnt_check
                                    else -> R.string.not_registered
                                }
                            ),
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                    if (address != null) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * The half sheet behind "Address Actions" on Manage Addresses. Mirrors Cold Storage's
 * [ColdStorageAddressActionsSheet], including Address Visibility living here rather than as a
 * toolbar glyph.
 *
 * Discovery reports its position as it walks, and that lands HERE rather than dismissing: a scan
 * is one balance lookup per address until the gap limit is reached, so it runs for a while, and a
 * closed sheet with a toast at the end said nothing about whether anything was happening.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageAddressesActionsSheet(
    isDiscovering: Boolean,
    progress: com.kachat.app.services.SpendingAddressDiscovery.DiscoveryProgress?,
    summary: String?,
    onGenerate: () -> Unit,
    onDiscover: () -> Unit,
    onVisibility: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            // Freely dismissable mid-scan now, by the swipe as well as by the button above: the scan
            // belongs to the ViewModel rather than to the sheet, so closing it abandons nothing - the
            // Address Actions button keeps its spinner and the result still arrives. Holding the
            // sheet open was only ever protecting a progress readout.
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = colors.background,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.address_actions),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )

                    if (isDiscovering) {
                        Spacer(Modifier.height(8.dp))
                        com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                        Text(
                            tr(R.string.kp_checking_address_lld_5b84c, progress?.checkingIndex ?: 0),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Text(
                            if ((progress?.foundCount ?: 0) == 0) tr(R.string.kp_no_addresses_with_a_balance_or_3a874)
                            else tr(R.string.kp_lld_found_so_far_c6c1d, progress?.foundCount),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            tr(R.string.kp_checks_the_first_thousand_addresses_whatever_90acf),
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        // The scan runs in the ViewModel's own scope, not the sheet's, so closing the
                        // sheet does not stop it - it keeps running and reports what it found. Holding
                        // the sheet open for the length of a thousand-address sweep was the only reason
                        // to sit and watch it.
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            Text(tr(R.string.kp_close_and_keep_scanning_b66cc), color = KaspaTeal, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        ActionSheetRow(
                            icon = Icons.Default.AddCircleOutline,
                            title = stringResource(R.string.generate_new_spending_address),
                            subtitle = "Reveals the next unused address in this wallet.",
                            onClick = onGenerate,
                        )
                        ActionSheetRow(
                            icon = Icons.Default.Search,
                            title = stringResource(R.string.discover_addresses),
                            subtitle = stringResource(R.string.finds_addresses_balance_or_domain),
                            onClick = onDiscover,
                        )
                        ActionSheetRow(
                            icon = Icons.Default.Checklist,
                            title = tr(R.string.kp_address_visibility_9c9a7),
                            subtitle = "Check off every address you want on the list, in one sitting.",
                            onClick = onVisibility,
                        )
                        if (summary != null) {
                            Text(summary, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * What a domain transfer is doing right now.
 *
 * The steps come from [WalletViewModel.KnsInscribeUiStatus], which the transfer already reports;
 * this only gives them a place to be seen. The fractions are approximate on purpose: the waits
 * are network-dependent, and a bar that moves in known steps beats a spinner that says nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainTransferProgressSheet(
    domainName: String,
    status: WalletViewModel.KnsInscribeUiStatus,
) {
    val colors = LocalAppColors.current
    val (label, fraction) = when (status) {
        WalletViewModel.KnsInscribeUiStatus.CHECKING_AVAILABILITY -> "Preparing transfer" to 0.1f
        WalletViewModel.KnsInscribeUiStatus.FETCHING_FEE -> "Working out the fee" to 0.2f
        WalletViewModel.KnsInscribeUiStatus.SUBMITTING_COMMIT -> "Submitting commit transaction" to 0.4f
        WalletViewModel.KnsInscribeUiStatus.SUBMITTING_REVEAL -> "Submitting reveal transaction" to 0.75f
        WalletViewModel.KnsInscribeUiStatus.VERIFYING -> "Confirming the new owner" to 0.95f
        else -> "Working" to 0.1f
    }

    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            // Held open for the duration: the work keeps running either way, and a dismissed sheet
            // would leave an on-chain transfer with nothing reporting on it.
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            dragHandle = null,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp, top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        tr(R.string.kp_sending_fb66f, domainName),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = KaspaTeal,
                        trackColor = colors.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(label, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        tr(R.string.kp_a_domain_transfer_is_two_transactions_6e907),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
