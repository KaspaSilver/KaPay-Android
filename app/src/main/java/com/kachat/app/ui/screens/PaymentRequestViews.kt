package com.kachat.app.ui.screens

import com.kachat.app.util.tr
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.kachat.app.util.formatFiatAmount
import com.kachat.app.R
import com.kachat.app.models.PaymentRequest
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.KaspaUnit
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - Opening one

/**
 * The payment request waiting to be shown - a tapped request card, a pasted link, a link opened
 * from outside the app (iOS `PaymentRequestRouter`). [PaymentRequestSheetHost], in MainShell,
 * presents it: Send Kaspa filled in for someone else's, the request itself for your own. Every
 * opening is its own sheet, even of the same request.
 */
object PaymentRequestRouter {
    data class Pending(val request: PaymentRequest, val id: Long)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()
    private var nextId = 0L

    @Synchronized
    fun open(request: PaymentRequest) {
        nextId += 1
        _pending.value = Pending(request, nextId)
    }

    /** The sheet for [id] has gone; a newer opening stays. */
    @Synchronized
    fun dismiss(id: Long) {
        if (_pending.value?.id == id) _pending.value = null
    }
}

/**
 * Pasting a payment request anywhere in KaChat opens Send Kaspa with it (iOS
 * `PaymentRequestPaste`): the chat composers (1:1, group, public), the KaPosts composer. Only text
 * that is the link and nothing else, and only someone else's request: your own pastes as text, so
 * you can send it to the person who should pay.
 */
object PaymentRequestPaste {
    /** Opens [text] when it is someone else's payment request and nothing more; true if it did. */
    fun openIfRequest(text: String?): Boolean {
        val pasted = text ?: return false
        if (!pasted.contains("kachat", ignoreCase = true)) return false
        val match = KaChatLink.findFirst(pasted) ?: return false
        if (pasted.trim() != match.raw) return false
        val request = (match.ref as? KaChatLinkRef.PaymentRequest)?.request ?: return false
        if (request.isOwn) return false
        PaymentRequestRouter.open(request)
        return true
    }

    /**
     * For a text field's change from [old] to [new]: when what was put in at once (a paste) is
     * someone else's payment request, it opens Send Kaspa and the field keeps [old] - returns true,
     * and the caller drops the change.
     */
    fun interceptInsert(old: String, new: String): Boolean {
        // A link is far longer than anything typed in one change.
        if (new.length < 20) return false
        val max = minOf(old.length, new.length)
        var prefix = 0
        while (prefix < max && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < max - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        val inserted = new.substring(prefix, new.length - suffix)
        if (inserted.length < 20) return false
        return openIfRequest(inserted)
    }
}

/**
 * Presents the payment request waiting in [PaymentRequestRouter] (iOS
 * `PaymentRequestSheetPresenter`): your own shows it ([OwnPaymentRequestSheet]); someone else's
 * opens Send Kaspa from the chatting address with the address locked and the amount and memo
 * filled in - every send check as always, and nothing goes out until slide to send.
 */
@Composable
fun PaymentRequestSheetHost(walletViewModel: WalletViewModel) {
    val pending by PaymentRequestRouter.pending.collectAsState()
    val current = pending ?: return
    key(current.id) {
        var isOwn by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) {
            isOwn = withContext(Dispatchers.Default) { current.request.isOwn }
        }
        when (isOwn) {
            null -> Unit
            true -> OwnPaymentRequestSheet(current.request, onDismissed = { PaymentRequestRouter.dismiss(current.id) })
            false -> {
                val address by walletViewModel.address.collectAsState()
                val balanceSompi by walletViewModel.balanceSompi.collectAsState()
                val from = address
                if (from == null) {
                    LaunchedEffect(Unit) { PaymentRequestRouter.dismiss(current.id) }
                } else {
                    IosFullSheet(onDismissed = { PaymentRequestRouter.dismiss(current.id) }, swipeToDismiss = false) { close ->
                        SpendingAddressSendFlow(
                            fromAddress = from,
                            balanceSompi = balanceSompi,
                            title = stringResource(R.string.chats_send_kaspa),
                            viewModel = walletViewModel,
                            onDone = close,
                            presentedAsSheet = true,
                            paymentRequest = current.request,
                        )
                    }
                }
            }
        }
    }
}

/** Shares [link] through the system share sheet (iOS `ShareLink`). */
private fun sharePaymentRequestLink(context: android.content.Context, link: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

/** iOS's `Label("Share", systemImage: "square.and.arrow.up")` button. */
@Composable
private fun PaymentRequestShareButton(enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Outlined.IosShare, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.share), color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: - Request Payment (Chats > + > Request Payment, and a 1:1 chat's + sheet)

private enum class PaymentRequestDestination {
    /** A brand-new spending address, never revealed, used or handed out. */
    FRESH,
    /** One of your addresses, picked from the list: the chatting address or a spending one. */
    CHOSEN,
}

/**
 * Request Payment's tile icon, for the New sheet and a 1:1 chat's + sheet: iOS's SF Symbol
 * "doc.text" (semibold) drawn to its proportions - a rounded page with its top-right corner folded
 * down and two short lines of text in the middle. Material's Description reads as a different,
 * squarer page with longer lines, and is already the File tile's icon next to this one.
 */
val RequestPaymentIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    val ink = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "RequestPaymentDocText",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // the page, the fold's diagonal closing it
        path(
            stroke = ink,
            strokeLineWidth = 2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12.86f, 3.36f)
            lineTo(7.04f, 3.36f)
            arcTo(1.7f, 1.7f, 0f, false, false, 5.34f, 5.06f)
            lineTo(5.34f, 18.94f)
            arcTo(1.7f, 1.7f, 0f, false, false, 7.04f, 20.64f)
            lineTo(16.96f, 20.64f)
            arcTo(1.7f, 1.7f, 0f, false, false, 18.66f, 18.94f)
            lineTo(18.66f, 9.17f)
            close()
        }
        // the folded corner
        path(
            stroke = ink,
            strokeLineWidth = 2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(11.96f, 3.36f)
            lineTo(11.96f, 9.16f)
            arcTo(1f, 1f, 0f, false, false, 12.96f, 10.16f)
            lineTo(18.66f, 10.16f)
        }
        // the two lines of text
        path(
            stroke = ink,
            strokeLineWidth = 1.1f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        ) {
            moveTo(8.8f, 13.8f)
            lineTo(15.02f, 13.8f)
            moveTo(8.8f, 16.68f)
            lineTo(15.02f, 16.68f)
        }
    }.build()
}

/**
 * Makes a payment request link (iOS `CreatePaymentRequestView`, c6851d6 / 7c71a98 / 6594936,
 * 86dfd2d / 6dee531 / 338d9f5 / 3f5f79c / 05db583): paid to a Fresh Address or one you choose, an
 * exact amount (KAS or your currency) and what it's for - an optional memo up to
 * [PaymentRequest.MEMO_LIMIT] characters. Signed by your chatting address when it goes out, so
 * whoever pays sees it is from you.
 *
 * Like a send, in steps (iOS 05db583), the send's step screens the other way round: "Where do you
 * want to be paid?" (Fresh Address, or Choose an Address with your addresses listed right on the
 * screen, [OwnAddressList]), "How much?" on the app's keypad ([KaspaKeypadAmountEntry], no Max),
 * then the review with the memo, Copy Payment Request (the bottom button) and Share.
 *
 * Fresh Address (the default) is a brand-new spending address, never revealed, used or handed out:
 * the screen shows the next unrevealed slot (derived, not reserved) and reserves it only when the
 * request is copied or shared - opening the screen and cancelling uses none up. Once per screen:
 * Copy and Share both reuse it, so no two requests share an address.
 *
 * It gives the link: Copy Payment Request or Share. Sending it into a chat went with the chats
 * (iOS wallet 301ebfb).
 */
@Composable
fun CreatePaymentRequestSheet(
    walletViewModel: WalletViewModel,
    onDismissed: () -> Unit,
    portfolioViewModel: com.kachat.app.viewmodels.PortfolioViewModel = hiltViewModel(),
) {
    IosFullSheet(onDismissed = onDismissed, swipeToDismiss = true) { close ->
        val colors = LocalAppColors.current
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        val focusManager = LocalFocusManager.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val haptic = com.kachat.app.util.rememberHaptics()
        val fiatPriceInCurrency by portfolioViewModel.currentPriceUsd.collectAsState()
        val fiatCurrencyCode by portfolioViewModel.currency.collectAsState()
        val chattingAddress by walletViewModel.address.collectAsState()

        var step by remember { mutableStateOf(PaymentRequestStep.DESTINATION) }
        var destination by remember { mutableStateOf(PaymentRequestDestination.FRESH) }
        /** The brand-new address reserved for this request - only once it's copied or shared. */
        var freshAddress by remember { mutableStateOf<String?>(null) }
        /** The next brand-new address, shown before it's reserved (the slot after the last revealed one). */
        var nextFreshAddress by remember { mutableStateOf<String?>(null) }
        var chosenAddress by remember { mutableStateOf<String?>(null) }
        var amountInput by remember { mutableStateOf("") }
        val fiatAmountState = com.kachat.app.util.rememberKaspaFiatAmountState(onKasTextChange = { amountInput = it })
        var memo by remember { mutableStateOf("") }
        var copied by remember { mutableStateOf(false) }
        var isWorking by remember { mutableStateOf(false) }
        var sendError by remember { mutableStateOf<String?>(null) }

        // derived, not reserved: opening the screen and cancelling uses no address up
        LaunchedEffect(Unit) { nextFreshAddress = walletViewModel.nextFreshSpendingAddress() }

        val amountSompi = KaspaUnit.sompiFromUserText(amountInput)?.takeIf { it > 0 }
        /** Ready to hand out: an amount, and an address (or a fresh one to be made). */
        val canMake = amountSompi != null && (destination == PaymentRequestDestination.FRESH || chosenAddress != null)
        /** The address the request pays: the fresh one (reserved, else the one it will be), or the pick. */
        val payeeAddress = when (destination) {
            PaymentRequestDestination.FRESH -> freshAddress ?: nextFreshAddress
            PaymentRequestDestination.CHOSEN -> chosenAddress
        }
        LaunchedEffect(amountInput, memo, destination, chosenAddress) { copied = false }

        /** To another step; the slide's direction follows the steps' order (iOS `go(to:)`). */
        fun go(next: PaymentRequestStep) {
            focusManager.clearFocus()
            step = next
        }

        /**
         * The request to hand out, unsigned (signed by the chatting address as it goes out - the
         * key is only derived then). A fresh address is reserved here, the first time it's needed.
         */
        suspend fun makeRequest(): PaymentRequest? {
            val sompi = amountSompi ?: return null
            val address = when (destination) {
                PaymentRequestDestination.FRESH -> freshAddress ?: run {
                    val reserved = walletViewModel.reserveFreshSpendingAddress()
                    if (reserved == null) {
                        sendError = context.getString(R.string.pr_couldnt_make_address)
                        return null
                    }
                    freshAddress = reserved
                    reserved
                }
                PaymentRequestDestination.CHOSEN -> chosenAddress ?: return null
            }
            return PaymentRequest.create(address, sompi, memo, address == chattingAddress)
        }

        /** The request's link, signed by the chatting address (or unsigned when its key can't be had). */
        suspend fun signedLink(unsigned: PaymentRequest): String = withContext(Dispatchers.Default) {
            (com.kachat.app.services.PaymentRequestWallet.shared?.sign(unsigned) ?: unsigned).shareUrlString
        }

        fun copy() {
            isWorking = true
            sendError = null
            scope.launch {
                try {
                    val unsigned = makeRequest() ?: return@launch
                    clipboard.setText(AnnotatedString(signedLink(unsigned)))
                    haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                    copied = true
                } finally {
                    isWorking = false
                }
            }
        }

        // Share waits for the fresh address before the share sheet opens.
        fun share() {
            isWorking = true
            sendError = null
            scope.launch {
                try {
                    val unsigned = makeRequest() ?: return@launch
                    sharePaymentRequestLink(context, signedLink(unsigned))
                } finally {
                    isWorking = false
                }
            }
        }

        // The sheet already keeps clear of the system bars.
        val noInsets = WindowInsets(0, 0, 0, 0)
        val title = stringResource(R.string.pr_request_payment)

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                // iOS: the next step moves in from the trailing edge, Back brings one in from the leading
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(PaymentRequestStepSpring) { if (forward) it else -it } togetherWith
                    slideOutHorizontally(PaymentRequestStepSpring) { if (forward) -it else it })
            },
            label = "requestPaymentStep",
            modifier = Modifier.fillMaxSize(),
        ) { shown ->
            when (shown) {
                // MARK: Where
                PaymentRequestStep.DESTINATION -> SendStepScreen(
                    title = title,
                    onCancel = close,
                    canContinue = payeeAddress != null,
                    onContinue = { go(PaymentRequestStep.AMOUNT) },
                    insets = noInsets,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SendStepQuestion(tr(R.string.kp_where_do_you_want_to_be_8db47))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SelectablePill(
                                title = stringResource(R.string.pr_fresh_address),
                                selected = destination == PaymentRequestDestination.FRESH,
                                modifier = Modifier.weight(1f),
                            ) {
                                destination = PaymentRequestDestination.FRESH
                            }
                            SelectablePill(
                                title = stringResource(R.string.pr_choose_an_address),
                                selected = destination == PaymentRequestDestination.CHOSEN,
                                modifier = Modifier.weight(1f),
                            ) {
                                destination = PaymentRequestDestination.CHOSEN
                            }
                        }
                        when (destination) {
                            PaymentRequestDestination.FRESH -> {
                                val fresh = freshAddress ?: nextFreshAddress
                                if (fresh != null) {
                                    AddressResolutionCard(address = fresh)
                                    Text(
                                        tr(R.string.kp_a_brand_new_address_of_yours_4c465),
                                        color = colors.textSecondary,
                                        fontSize = 13.sp,
                                    )
                                } else {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                        com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary)
                                    }
                                }
                            }
                            PaymentRequestDestination.CHOSEN -> {
                                // the list right here, no sheet: tap the address that gets paid
                                Text(stringResource(R.string.pr_pick_address), color = colors.textSecondary, fontSize = 13.sp)
                                OwnAddressList(
                                    selected = chosenAddress,
                                    walletViewModel = walletViewModel,
                                    onPick = { picked -> chosenAddress = picked.address(chattingAddress) },
                                )
                            }
                        }
                    }
                }

                // MARK: How much
                PaymentRequestStep.AMOUNT -> SendStepScreen(
                    title = title,
                    onCancel = close,
                    canContinue = amountSompi != null,
                    onContinue = { go(PaymentRequestStep.REVIEW) },
                    onBack = { go(PaymentRequestStep.DESTINATION) },
                    continueTitle = tr(R.string.kp_review_e29a7),
                    insets = noInsets,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SendStepQuestion(tr(R.string.kp_how_much_fa22a))
                        payeeAddress?.let { address ->
                            SendInfoPill {
                                Text(tr(R.string.kp_paid_to_91219))
                                Text("${address.take(14)}...${address.takeLast(6)}", fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    KaspaKeypadAmountEntry(
                        fiatAmountState = fiatAmountState,
                        priceInCurrency = fiatPriceInCurrency,
                        currencyCode = fiatCurrencyCode,
                        onMax = {},
                        showsMax = false,
                        modifier = Modifier.weight(1f),
                    )
                }

                // MARK: Review
                PaymentRequestStep.REVIEW -> SendStepScreen(
                    title = tr(R.string.kp_review_e29a7),
                    onCancel = close,
                    canContinue = canMake && !isWorking,
                    onContinue = { copy() },
                    onBack = { go(PaymentRequestStep.AMOUNT) },
                    continueTitle = stringResource(if (copied) R.string.ab_copied else R.string.pr_copy),
                    insets = noInsets,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(tr(R.string.kp_you_re_requesting_c02d6), color = colors.textSecondary, fontSize = 15.sp)
                            amountSompi?.let { sompi ->
                                PaymentRequestFitText(
                                    "${KaspaUnit.plain(sompi)} ${KaspaUnit.symbol}",
                                    style = TextStyle(
                                        color = colors.textPrimary,
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = IosRoundedFontFamily,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    minScale = 0.5f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                fiatPriceInCurrency?.takeIf { it > 0.0 }?.let { price ->
                                    Text(
                                        formatFiatAmount(sompi / 100_000_000.0 * price, fiatCurrencyCode),
                                        color = colors.textSecondary,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }

                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                tr(R.string.kp_paid_to_91219).uppercase(),
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            AddressResolutionCard(address = payeeAddress)
                        }

                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val placeholder = stringResource(R.string.pr_memo_placeholder)
                            BasicTextField(
                                value = memo,
                                onValueChange = { value ->
                                    memo = if (PaymentRequest.characterCount(value) > PaymentRequest.MEMO_LIMIT) {
                                        PaymentRequest.takeCharacters(value, PaymentRequest.MEMO_LIMIT)
                                    } else value
                                },
                                maxLines = 4,
                                textStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp),
                                cursorBrush = SolidColor(KaspaTeal),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sendKaspaGlass(14.dp)
                                    .padding(14.dp)
                                    .semantics { contentDescription = placeholder },
                                decorationBox = { inner ->
                                    Box {
                                        if (memo.isEmpty()) Text(placeholder, color = colors.textTertiary, fontSize = 17.sp)
                                        inner()
                                    }
                                },
                            )
                            Text(
                                "${PaymentRequest.characterCount(memo)}/${PaymentRequest.MEMO_LIMIT}",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            stringResource(R.string.pr_memo_shows_note),
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        sendError?.let {
                            Text(it, color = colors.danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }

                        PaymentRequestSharePill(enabled = canMake && !isWorking) { share() }

                        Text(
                            stringResource(R.string.pr_anyone_note),
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Request Payment's steps, in order (iOS 05db583 `CreatePaymentRequestView.Step`). */
private enum class PaymentRequestStep { DESTINATION, AMOUNT, REVIEW }

/** iOS `.spring(response: 0.4, dampingFraction: 0.9)`: stiffness (2*pi/0.4)^2. */
private val PaymentRequestStepSpring = spring(dampingRatio = 0.9f, stiffness = 247f, visibilityThreshold = IntOffset.VisibilityThreshold)

/** The review's Share (iOS 05db583): `Label("Share", systemImage: "square.and.arrow.up")` in the
 *  accent colour on an accent capsule at 15%. */
@Composable
private fun PaymentRequestSharePill(enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(50))
            .background(KaspaTeal.copy(alpha = 0.15f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Outlined.IosShare, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.share), color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One line that shrinks to fit its width rather than being cut, down to [minScale] of [style]'s
 *  size (iOS `.lineLimit(1).minimumScaleFactor`) - the review's amount. SendKaspaComponents'
 *  `SendFitText` is the same but private there. */
@Composable
private fun PaymentRequestFitText(
    text: String,
    style: TextStyle,
    minScale: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxWidth = constraints.maxWidth
        val scale = remember(text, style, maxWidth) {
            if (maxWidth == Constraints.Infinity) {
                1f
            } else {
                val width = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
                if (width <= maxWidth) 1f else (maxWidth.toFloat() / width).coerceIn(minScale, 1f)
            }
        }
        Text(
            text,
            style = style.copy(fontSize = style.fontSize * scale),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - Paying one

/**
 * What a payment request asks for, above the amount on Send Kaspa (iOS `PaymentRequestSummary`):
 * the amount, the memo, how it is paid, and who made it - "Requested by" with that address once
 * the signature checked out, else "Not signed".
 */
@Composable
fun PaymentRequestSummary(request: PaymentRequest, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .sendKaspaGlass(14.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.pr_requested).uppercase(),
            color = KaspaTeal,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${request.amountText} ${KaspaUnit.symbol}",
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (request.memo.isNotEmpty()) {
            Text(request.memo, color = colors.textPrimary, fontSize = 15.sp)
        }
        // iOS's summary says nothing about how it is paid: there are no chat payments in KaPay.

        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 4.dp))

        val creator = request.creator
        if (creator != null) {
            // Who made it - checked against the signature in the link when it was opened.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.pr_requested_by), color = colors.success, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            AddressResolutionCard(address = creator, addressOnly = true)
            if (!creator.equals(request.address, ignoreCase = true)) {
                Text(stringResource(R.string.pr_signed_by_their_chatting), color = colors.textSecondary, fontSize = 12.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                Text(stringResource(R.string.pr_not_signed), color = colors.warning, fontSize = 12.sp)
            }
        }
    }
}

/**
 * One of your own payment requests, tapped in a chat (iOS `OwnPaymentRequestView`, 1fe8388): what
 * it asks for and where it pays into, with its link to copy or share again. Nothing to pay - it's
 * yours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnPaymentRequestSheet(request: PaymentRequest, onDismissed: () -> Unit) {
    IosFullSheet(onDismissed = onDismissed, swipeToDismiss = true) { close ->
        BackHandler(onBack = close)
        val colors = LocalAppColors.current
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        val haptic = com.kachat.app.util.rememberHaptics()
        var copied by remember { mutableStateOf(false) }
        Scaffold(
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // iOS 26's sheet bar: the title in headline, Done on a glass capsule.
                SheetNavBar(
                    title = stringResource(R.string.pr_your_payment_request),
                    trailing = { SheetBarButton(stringResource(R.string.done), onClick = close) },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "${request.amountText} ${KaspaUnit.symbol}",
                        color = colors.textPrimary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IosRoundedFontFamily,
                        textAlign = TextAlign.Center,
                    )
                    if (request.memo.isNotEmpty()) {
                        Text(request.memo, color = colors.textPrimary, fontSize = 17.sp, textAlign = TextAlign.Center)
                    }
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // iOS OwnPaymentRequestView: one heading and one footer, whichever address.
                    Text(
                        tr(R.string.kp_paid_to_your_address_11aa0).uppercase(),
                        color = KaspaTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AddressResolutionCard(address = request.address)
                    Text(
                        stringResource(R.string.pr_memo_shows_note),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                }

                SendActionButton(
                    title = stringResource(if (copied) R.string.ab_copied else R.string.pr_copy),
                    isBusy = false,
                    isEnabled = true,
                    requiresSlide = false,
                    onSend = {
                        clipboard.setText(AnnotatedString(request.shareUrlString))
                        haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        copied = true
                    },
                )
                PaymentRequestShareButton { sharePaymentRequestLink(context, request.shareUrlString) }
            }
        }
    }
}
