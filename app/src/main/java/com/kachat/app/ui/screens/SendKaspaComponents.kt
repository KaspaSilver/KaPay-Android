package com.kachat.app.ui.screens

import com.kachat.app.util.tr
import com.kachat.app.services.AddressBookManager
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.foundation.layout.statusBars
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import com.kachat.app.viewmodels.WalletViewModel
import com.kachat.app.services.UtxoEntry
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.KaspaAddress
import com.kachat.app.util.KaspaFiatAmountState
import com.kachat.app.util.KaspaUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The pieces every Send Kaspa screen is built from, so they look and behave the same: the 1:1
// chat's Send KAS sheet, Send Kaspa (Chats > + > Send Kaspa, Donate, the Address Book, an
// address's Send, paying a payment request), KaPosts tips, and KasSigner's send. Every send but
// KasSigner's starts with From (SendFromSection): Chatting Address or Choose an Address. Then the
// recipient (paste, scan a QR, names resolve), the big amount (KAS or your currency, Max), the
// fee speed and coin control, and a slide-to-send button. Mirrors iOS's
// Views/Shared/SendKaspaComponents.swift (4d0324f, 6333dd3; the step pieces - step screens, the
// keypad, the fee editor, the address list - from 05db583, the Contacts pill from 8118f09; see
// "Send steps" below). Change these pieces rather than one screen, so the screens stay matched.

/** The frosted card the Send Kaspa pieces sit on (iOS `sendKaspaGlass`). */
@Composable
fun Modifier.sendKaspaGlass(cornerRadius: Dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(LocalAppColors.current.surface)
        .border(0.8.dp, Color.White.copy(alpha = 0.18f), shape)
}

// MARK: - Recipient

/**
 * Who the Kaspa goes to: an address or a name, with the labelled Paste, Scan QR and Contacts pills
 * under the field (always all three, sized to fit one row - iOS 05db583, 8118f09) and a line
 * saying what the input resolved to. The screen keeps its own lookup logic (it watches [input]);
 * this only shows it. With [lockedAddress] (Compound UTXOs) the recipient is fixed.
 */
@Composable
fun SendRecipientCard(
    input: String,
    onInputChange: (String) -> Unit,
    isResolving: Boolean,
    resolvedAddress: String?,
    resolvedName: String?,
    lookupError: String?,
    isValidAddress: Boolean,
    onScan: () -> Unit,
    lockedAddress: String? = null,
    enabled: Boolean = true,
    /** Every service's answer for a typed name (`NameServicesClient.resolveEverywhere`, .kachat
     *  first) and the one in use: the rest are offered under "Other domains" (iOS 6ac48a7). */
    nameResolutions: List<com.kachat.app.services.NameResolution> = emptyList(),
    selectedTld: com.kachat.app.services.NameServiceTLD? = null,
    onSelectResolution: (com.kachat.app.services.NameResolution) -> Unit = {},
    /** Puts the cursor in the field when it appears empty (a send's first step, iOS 05db583). */
    focusOnAppear: Boolean = false,
) {
    val colors = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val trimmed = input.trim()
    val addressBook = rememberAddressBookEntries()
    var showAddressBook by remember { mutableStateOf(false) }
    if (showAddressBook) {
        AddressBookPickerSheet(onDismiss = { showAddressBook = false }, onSelect = { onInputChange(it.address) })
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!focusOnAppear || lockedAddress != null || trimmed.isNotEmpty()) return@LaunchedEffect
        // The field doesn't exist yet on the tap that presented the screen.
        delay(350)
        runCatching { focus.requestFocus() }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .sendKaspaGlass(20.dp)
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.to), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (lockedAddress != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                MiddleEllipsisText(
                    lockedAddress,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
            Text(stringResource(R.string.consolidating_this_address), color = colors.textSecondary, fontSize = 12.sp)
        } else {
            val placeholder = stringResource(R.string.kaspa_qr_or_domain)
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(KaspaTeal),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Uri,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(placeholder, color = colors.textTertiary, fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            // Always all three, as in KaChat: with no contacts yet Contacts opens on the empty
            // picker (iOS 8118f09).
            SendRecipientInputPills(
                pills = listOf(
                    SendRecipientInputPill(stringResource(R.string.paste), Icons.Default.ContentPaste) {
                        clipboard.getText()?.text?.let { onInputChange(KaspaAddress.fromScanned(it)) }
                    },
                    SendRecipientInputPill(stringResource(R.string.scan_qr), Icons.Default.QrCodeScanner, onScan),
                    SendRecipientInputPill(stringResource(R.string.ab_address_book), Icons.Outlined.Badge) {
                        showAddressBook = true
                    },
                ),
                enabled = enabled,
            )
            // Who the coins are going to - the card Create chat shows (iOS ac0ef19): the domain
            // typed, else the address's own .kachat name (iOS 6ac48a7).
            AddressResolutionCard(address = resolvedAddress ?: trimmed.takeIf { isValidAddress }, domain = resolvedName)
            // The name it has in your Contacts (iOS 00767a4).
            // Only a name you gave it - an entry without one shows nothing here (iOS 2831fab).
            val savedName = remember(addressBook, resolvedAddress, trimmed) {
                AddressBookManager.shared?.entry(resolvedAddress ?: trimmed)?.customName
            }
            if (savedName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Badge, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(14.dp))
                    Text(savedName, color = KaspaTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trimmed.isNotEmpty()) {
                SendRecipientStatusLine(
                    input = trimmed,
                    isResolving = isResolving,
                    resolvedAddress = resolvedAddress,
                    resolvedName = resolvedName,
                    lookupError = lookupError,
                    isValidAddress = isValidAddress,
                )
                if (!isResolving) {
                    OtherDomainsDropdown(nameResolutions, selectedTld, onSelectResolution)
                }
            }
        }
    }
}

/** One of the recipient card's labelled pills (iOS `SendRecipientCard.inputPill`). */
private class SendRecipientInputPill(val title: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Paste, Scan QR, Contacts under the recipient field (iOS 05db583 / 8118f09 `inputPill`): the
 * accent label and icon on a faint accent capsule, 6 apart in one row. Like iOS's
 * `.minimumScaleFactor(0.85)` the labels shrink together, down to 85%, to keep three to a row on
 * the narrowest phones.
 */
@Composable
private fun SendRecipientInputPills(pills: List<SendRecipientInputPill>, enabled: Boolean) {
    val haptic = com.kachat.app.util.rememberHaptics()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gap = 6.dp
    val iconSize = 16.dp
    val iconGap = 5.dp
    val paddingH = 10.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxWidth = constraints.maxWidth
        val titles = pills.map { it.title }
        val scale = remember(titles, maxWidth, density) {
            val style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val textWidth = titles.sumOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
            val chrome = with(density) {
                (paddingH * 2 + iconSize + iconGap).toPx() * titles.size + (gap * (titles.size - 1)).toPx()
            }
            val room = maxWidth - chrome
            if (textWidth <= 0 || room >= textWidth) 1f else (room / textWidth).coerceIn(0.85f, 1f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            pills.forEach { pill ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(iconGap * scale),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(KaspaTeal.copy(alpha = 0.15f))
                        .clickable(enabled = enabled, role = Role.Button) {
                            haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                            pill.onClick()
                        }
                        .padding(horizontal = paddingH, vertical = 8.dp),
                ) {
                    Icon(pill.icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(iconSize * scale))
                    Text(
                        pill.title,
                        color = KaspaTeal,
                        fontSize = 15.sp * scale,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SendRecipientStatusLine(
    input: String,
    isResolving: Boolean,
    resolvedAddress: String?,
    resolvedName: String?,
    lookupError: String?,
    isValidAddress: Boolean,
) {
    val colors = LocalAppColors.current
    when {
        isResolving -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(14.dp), color = colors.textSecondary, strokeWidth = 2.dp)
            Text(stringResource(R.string.looking_up_domain), color = colors.textSecondary, fontSize = 12.sp)
        }
        lookupError != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Cancel, contentDescription = null, tint = colors.danger, modifier = Modifier.size(14.dp))
            Text(lookupError, color = colors.danger, fontSize = 12.sp)
        }
        resolvedAddress != null -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.resolved_name, resolvedName ?: ""), color = colors.success, fontSize = 12.sp)
            }
            MiddleEllipsisText(
                resolvedAddress,
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val tint = if (isValidAddress) colors.success else colors.danger
            Icon(
                if (isValidAddress) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                addressValidityText(input, isValidAddress),
                color = tint,
                fontSize = 12.sp,
            )
        }
    }
}

// MARK: - Amount

/**
 * The big centred amount and its unit, with the KAS / your-currency switch (showing the converted
 * value) and Max under it. [fiatAmountState] hands the screen the KAS amount text after every edit
 * (its own `onKasTextChange`). [sanitize] cleans what was typed before it's used: by default
 * digits and one decimal point ("," read as "." for comma-decimal keyboards), at most 8 decimals
 * (iOS 16b64bc); [focusOnAppear] puts the cursor in the amount when the screen appears.
 */
@Composable
fun KaspaAmountEntry(
    fiatAmountState: KaspaFiatAmountState,
    priceInCurrency: Double?,
    currencyCode: String,
    onMax: () -> Unit,
    modifier: Modifier = Modifier,
    sanitize: (String) -> String = KaspaUnit::sanitizeAmountInput,
    isEstimatingMax: Boolean = false,
    maxEnabled: Boolean = true,
    focusOnAppear: Boolean = false,
    /** False leaves Max out (asking for an amount, not sending one: Request Payment, iOS c6851d6). */
    showsMax: Boolean = true,
) {
    val colors = LocalAppColors.current
    val focus = remember { FocusRequester() }
    if (focusOnAppear) {
        LaunchedEffect(Unit) {
            // The field doesn't exist yet on the tap that presented the screen.
            delay(300)
            runCatching { focus.requestFocus() }
        }
    }
    val display = fiatAmountState.displayText
    // Sized by what's shown, thousands commas included (iOS 40c5258).
    val shown = KaspaAmountGrouping.grouped(display)
    val fontSize = when {
        shown.length <= 7 -> 52.sp
        shown.length <= 10 -> 40.sp
        else -> 30.sp
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    runCatching { focus.requestFocus() }
                },
        ) {
            val amountLabel = KaspaUnit.label(stringResource(R.string.amount_kas))
            BasicTextField(
                value = display,
                // Shown with thousands commas ("12,345.5"); what the screen gets stays plain (iOS 40c5258).
                onValueChange = { raw ->
                    fiatAmountState.onDisplayTextChange(sanitize(KaspaAmountGrouping.ungrouped(raw, previous = display)), priceInCurrency)
                },
                visualTransformation = KaspaAmountGrouping.visualTransformation,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IosRoundedFontFamily,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(KaspaTeal),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .widthIn(min = 40.dp, max = 260.dp)
                    .focusRequester(focus)
                    .clearAndSetSemantics { contentDescription = amountLabel },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (display.isEmpty()) {
                            Text("0", color = colors.textTertiary, fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = IosRoundedFontFamily)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (fiatAmountState.isFiatMode) currencyCode.uppercase() else KaspaUnit.symbol,
                color = colors.textSecondary,
                fontSize = fontSize * 0.55f,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IosRoundedFontFamily,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (priceInCurrency != null) {
                val switchLabel = stringResource(R.string.payment_switch_currency)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .sendKaspaGlass(14.dp)
                        .clickable { fiatAmountState.toggleMode(priceInCurrency) }
                        .clearAndSetSemantics {
                            contentDescription = switchLabel
                            role = Role.Button
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(14.dp))
                    Text(
                        fiatAmountState.conversionLabelText(priceInCurrency, currencyCode)
                            ?: if (fiatAmountState.isFiatMode) KaspaUnit.symbol else currencyCode.uppercase(),
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                    )
                }
            }
            if (showsMax) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .alpha(if (maxEnabled) 1f else 0.5f)
                    .sendKaspaGlass(14.dp)
                    .clickable(enabled = maxEnabled && !isEstimatingMax, role = Role.Button) { onMax() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                if (isEstimatingMax) {
                    com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(14.dp), color = KaspaTeal, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.max), color = KaspaTeal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
            }
        }
    }
}

/**
 * Thousands separators on an amount field while it's shown ("12,345.5", or "12 345,5" where "," is
 * the decimal point), the plain number kept as the field's value (iOS 40c5258 `KaspaAmountEntry.grouped` / `ungrouped`, `Binding.groupedAmount`).
 * Used by [KaspaAmountEntry], the .kachat offer and list price, Add to Portfolio and a portfolio
 * transaction's quantity: put [visualTransformation] on the field and pass what's typed through
 * [ungrouped] before it's stored. The separators are only drawn, so the value never contains them.
 */
object KaspaAmountGrouping {
    /**
     * Where the device writes the decimal point as "," (de, fr, es, it, pt, ru, tr, id, vi...), a
     * comma is ALWAYS the decimal point - "0,001" pasted from a chat is 0.001, never 1 (iOS 73af28a,
     * IOS-078) - and thousands show as a thin space ("2 500"), which no reader takes for a decimal.
     * Elsewhere thousands show as "," ("12,345.5").
     */
    fun usesCommaDecimal(locale: java.util.Locale = java.util.Locale.getDefault()): Boolean =
        java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','

    fun groupingSeparator(locale: java.util.Locale = java.util.Locale.getDefault()): Char =
        if (usesCommaDecimal(locale)) '\u202F' else ','

    /** The decimal point as the device writes it: "," where [usesCommaDecimal], else ".". */
    fun decimalPoint(locale: java.util.Locale = java.util.Locale.getDefault()): Char =
        if (usesCommaDecimal(locale)) ',' else '.'

    /** "12345.5" -> "12,345.5", or "12 345,5" where "," is the decimal point: a separator every
     *  three digits of the whole part, the decimal point the device's own (so the person sees the
     *  "," they typed and doesn't type it again - iOS b383ff0, IOS-082), the rest as typed. */
    fun grouped(plain: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val dot = plain.indexOf('.')
        val whole = if (dot >= 0) plain.substring(0, dot) else plain
        if (!whole.all { it in '0'..'9' }) return plain
        val out = StringBuilder()
        if (whole.length > 3) {
            val separator = groupingSeparator(locale)
            for ((i, ch) in whole.withIndex()) {
                if (i > 0 && (whole.length - i) % 3 == 0) out.append(separator)
                out.append(ch)
            }
        } else {
            out.append(whole)
        }
        return if (dot >= 0) out.toString() + decimalPoint(locale) + plain.substring(dot + 1) else out.toString()
    }

    /**
     * The edited field back to a plain number ("." the decimal point). [edited] is the field's
     * value after the edit and [previous] its value before (both without the drawn separators),
     * so every comma in [edited] was typed or pasted.
     * - Comma-decimal device: the first "," is the decimal point and any further one is dropped,
     *   the value's own point (drawn as ",") counting as one - so a second tap on the key at the
     *   end never moves the point (iOS b383ff0, IOS-082), and a "," typed before the point becomes
     *   the point, as on iOS, Desktop and the extension (XP-027). A pasted "1.234,5" (dots as
     *   grouping, a comma decimal) is 1234.5. Pasted spaces go.
     * - Otherwise: a comma TYPED as the decimal point stays one: typed at the end, or a single
     *   comma not three digits from the end in a number that showed no grouping ("0,5" is 0.5),
     *   or after a lone leading zero ("0,001" is 0.001, as no grouped number starts "0,"). Any
     *   other comma goes.
     */
    fun ungrouped(edited: String, previous: String, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        if (usesCommaDecimal(locale)) {
            var text = edited.replace("\u202F", "").replace(" ", "").replace("\u00A0", "")
            // The field keeps its point as "." but draws it as ",": that point is the "," the
            // reader sees, so it counts as one here - exactly the text iOS edits (XP-027).
            if (previous.contains('.') && text.count { it == '.' } == 1) text = text.replace('.', ',')
            val first = text.indexOf(',')
            if (first >= 0) {
                // a comma is the decimal point; dots beside it can only be grouping
                val head = text.substring(0, first).replace(".", "")
                val tail = text.substring(first + 1).replace(".", "").replace(",", "")
                text = "$head.$tail"
            }
            return text
        }
        if (edited == "$previous,") {
            return previous.replace(",", "") + "."
        }
        val comma = edited.indexOf(',')
        if (!grouped(previous, locale).contains(',') && !edited.contains('.') &&
            edited.count { it == ',' } == 1 &&
            (edited.substring(0, comma) == "0" || comma == 0 || edited.length - (comma + 1) != 3)
        ) {
            return edited.replace(',', '.')
        }
        return edited.replace(",", "")
    }

    /** Draws [grouped] over the field's plain text, the cursor kept on the same digit. */
    val visualTransformation: VisualTransformation = GroupedAmountTransformation
}

private object GroupedAmountTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val plain = text.text
        val shown = KaspaAmountGrouping.grouped(plain)
        val point = KaspaAmountGrouping.decimalPoint()
        if (shown == plain) return TransformedText(text, OffsetMapping.Identity)
        // Each plain character's position in the shown text; a cursor just before a digit that
        // has a comma in front of it sits after that comma.
        val toShown = IntArray(plain.length + 1)
        val toPlain = IntArray(shown.length + 1)
        var o = 0
        for (t in shown.indices) {
            toPlain[t] = o
            // Only the drawn commas are extra: grouped() inserts them, and a groupable whole part
            // has none of its own (the fraction is copied as is, so it lines up 1:1 again). The
            // plain "." is drawn as the device's own decimal point.
            if (o < plain.length && (shown[t] == plain[o] || (plain[o] == '.' && shown[t] == point))) {
                toShown[o] = t
                o++
            }
        }
        toPlain[shown.length] = plain.length
        toShown[plain.length] = shown.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toShown[offset.coerceIn(0, plain.length)]
            override fun transformedToOriginal(offset: Int): Int = toPlain[offset.coerceIn(0, shown.length)]
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

// MARK: - Fee and coin control

/** The localized label for a fee speed (iOS shows `WithdrawFeeTier.rawValue` localized). */
@Composable
fun feeTierLabel(tier: ColdFeeTier): String = stringResource(
    when (tier) {
        ColdFeeTier.NORMAL -> R.string.fee_tier_normal
        ColdFeeTier.FAST -> R.string.fee_tier_fast
        ColdFeeTier.PRIORITY -> R.string.fee_tier_priority
    }
)

/**
 * Network fee (tap the amount to set a custom one, edited in place), the fee speed, and coin
 * control - one card. [feeText] is the fee as shown ("0.0001 KAS"), null while unknown.
 *
 * With [onEditFee] (a step-by-step send's review, iOS 05db583) the fee shows as plain text with
 * an Edit pill that opens the screen's own fee editor ([SendFeeEditorScreen]) instead of being
 * typed here; [isEditingFee], [customFeeText] and their callbacks are then unused.
 */
@Composable
fun SendFeeControls(
    feeTier: ColdFeeTier,
    onFeeTierChange: (ColdFeeTier) -> Unit,
    isEditingFee: Boolean = false,
    customFeeText: String = "",
    onCustomFeeTextChange: (String) -> Unit = {},
    isEstimatingFee: Boolean,
    feeText: String?,
    onStartEditing: () -> Unit = {},
    onCommit: () -> Unit = {},
    showsCoinControl: Boolean = true,
    coinControlSummary: String = "",
    onCoinControl: () -> Unit = {},
    onEditFee: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .sendKaspaGlass(20.dp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.network_fee), color = colors.textPrimary, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            when {
                onEditFee != null -> {
                    if (isEstimatingFee) {
                        com.kachat.app.ui.theme.IosActivityIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.textSecondary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            feeText ?: "—",
                            color = if (feeText == null) colors.textSecondary else colors.textPrimary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .alpha(if (feeText == null) 0.5f else 1f)
                            .clip(CircleShape)
                            .background(KaspaTeal.copy(alpha = 0.15f))
                            .clickable(enabled = feeText != null, role = Role.Button) {
                                haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                                onEditFee()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(15.dp))
                        Text(stringResource(R.string.ab_edit), color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                isEditingFee -> {
                    // Not focused on its own: as on iOS, the keyboard comes up when the field is
                    // tapped, not the moment it appears.
                    BasicTextField(
                        value = customFeeText,
                        onValueChange = onCustomFeeTextChange,
                        singleLine = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp, textAlign = TextAlign.End),
                        cursorBrush = SolidColor(KaspaTeal),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onCommit() }),
                        modifier = Modifier.widthIn(max = 110.dp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterEnd) {
                                if (customFeeText.isEmpty()) {
                                    Text("0.00", color = colors.textTertiary, fontSize = 15.sp)
                                }
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.done),
                        tint = KaspaTeal,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onCommit() },
                    )
                }
                isEstimatingFee -> com.kachat.app.ui.theme.IosActivityIndicator(
                    modifier = Modifier.size(16.dp),
                    color = colors.textSecondary,
                    strokeWidth = 2.dp,
                )
                feeText != null -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(role = Role.Button) { onStartEditing() },
                ) {
                    Text(feeText, color = KaspaTeal, fontSize = 15.sp, textDecoration = TextDecoration.Underline)
                    Icon(Icons.Default.Edit, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(11.dp))
                }
                else -> Text("—", color = colors.textSecondary, fontSize = 15.sp)
            }
        }
        SendFeeTierPicker(feeTier, onFeeTierChange)
        Text(
            if (onEditFee == null) stringResource(R.string.send_fee_tier_footer)
            else tr(R.string.kp_if_the_network_is_busy_fast_227d7),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )

        if (showsCoinControl) {
            HorizontalDivider(color = colors.divider)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onCoinControl() },
            ) {
                Text(stringResource(R.string.coin_control), color = colors.textPrimary, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(coinControlSummary, color = colors.textSecondary, fontSize = 15.sp)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Normal / Fast / Priority as iOS's segmented Picker draws it: a tinted track with the chosen
 *  segment on a raised thumb. */
@Composable
private fun SendFeeTierPicker(selected: ColdFeeTier, onSelect: (ColdFeeTier) -> Unit) {
    val colors = LocalAppColors.current
    val thumb = if (colors.isDark) Color(0xFF636366) else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.textPrimary.copy(alpha = 0.08f))
            .padding(2.dp)
    ) {
        ColdFeeTier.entries.forEach { tier ->
            val isSelected = tier == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) thumb else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(tier) }
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    feeTierLabel(tier),
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "Automatic" or "3 UTXOs selected", for the coin control row. The count stays English, as on
 *  iOS (`coinControlSummary`). */
@Composable
fun coinControlSummary(manualUtxos: List<UtxoEntry>?): String {
    val automatic = stringResource(R.string.automatic)
    return manualUtxos?.let { "${it.size} UTXO${if (it.size == 1) "" else "s"} selected" } ?: automatic
}

/** A small glass capsule for one line of context (available balance, fee). Text inside takes the
 *  pill's caption style and secondary colour unless it sets its own. */
@Composable
fun SendInfoPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    CompositionLocalProvider(
        LocalContentColor provides colors.textSecondary,
        LocalTextStyle provides TextStyle(fontSize = 12.sp, color = colors.textSecondary),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
                .sendKaspaGlass(14.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            content = content,
        )
    }
}

// MARK: - Send steps
//
// Every send is the same steps (iOS 05db583, its CLAUDE.md "Send Kaspa (and every other send)"):
// 1. To: [SendStepScreen] + [SendStepQuestion] ("Who are you sending to?") + [SendRecipientCard]
//    (Paste, Scan QR and Contacts under the field). Cancel sits at the bottom beside Continue
//    ([SendContinueButton]) on a first step; later steps have Back and Cancel in the header
//    ([SendStepHeader]).
// 2. From: "Where are you sending from?": the Chatting Address and Choose an Address pills;
//    Choose an Address lists your addresses right on the screen ([OwnAddressList], no sheet).
// 3. Amount: [KaspaKeypadAmountEntry], the app's own number pad ([KaspaKeypad],
//    [KeypadAmountText]), so no system keyboard covers the screen; "To" and "From" chips
//    ([SendStepChip]), the KAS / currency switch and Max.
// 4. Review: [SwipeUpToSendScreen] with Back. Only the fee and coin control can change
//    ([SendFeeControls]); the fee's Edit (`onEditFee`) opens [SendFeeEditorScreen].

/**
 * The header every send step draws in place of a top bar (iOS 05db583 `SendStepHeader`): Back (or
 * Cancel on the first step) on the left, the title in the middle, Cancel on the right once there
 * is a Back. Without [onCancel] (a step whose Cancel sits at the bottom, beside Continue) only the
 * title and Back show. Back is the accent chevron on a 40 glass disc, Cancel the accent word on a
 * 40-high glass capsule.
 */
@Composable
fun SendStepHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
            .heightIn(min = 40.dp),
    ) {
        SendFitText(
            title,
            style = TextStyle(color = LocalAppColors.current.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            minScale = 0.8f,
            // room for the buttons only when there are any
            modifier = Modifier.padding(horizontal = if (onBack != null || onCancel != null) 96.dp else 16.dp),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                SheetBarIconButton(
                    Icons.Filled.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    iconSize = 18.dp,
                )
            } else if (onCancel != null) {
                SheetBarButton(stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.height(40.dp))
            }
            Spacer(Modifier.weight(1f))
            if (onBack != null && onCancel != null) {
                SheetBarButton(stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.height(40.dp))
            }
        }
    }
}

/**
 * A send step's Continue (iOS 05db583 `SendContinueButton`): a 56-high accent capsule pinned at
 * the bottom with black text, faded and inert while [isEnabled] is false; full width, or beside a
 * glass Cancel capsule when the step has [onCancel] (its header then leaves Cancel out). [title]
 * defaults to "Continue". Draws its own bar background; the caller pads it for the insets.
 */
@Composable
fun SendContinueButton(
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (onCancel != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, KaspaTeal.copy(alpha = 0.35f), CircleShape)
                    .clickable(role = Role.Button, onClick = onCancel),
            ) {
                Text(stringResource(R.string.cancel), color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(CircleShape)
                // Disabled, the iPhone draws it gray, not dimmed teal: a disabled plain button
                // resolves the accent to the system's disabled tint (#3F403F, the text #1F1F1F
                // on it, measured in dark mode).
                .background(
                    when {
                        isEnabled -> KaspaTeal
                        colors.isDark -> Color(0xFF3F403F)
                        else -> Color(0xFFD1D1D6)
                    }
                )
                .clickable(enabled = isEnabled, role = Role.Button) {
                    haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                    onClick()
                },
        ) {
            Text(
                title ?: stringResource(R.string.continue_label),
                color = if (isEnabled) Color.Black else Color.Black.copy(alpha = 0.5f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * One step of a send - who to, from where, how much, a fee, a price (iOS 05db583
 * `SendStepScreen`): [SendStepHeader], the step's [content] (top-aligned in the height left; it
 * does not scroll by itself, so a step that can grow puts its own `verticalScroll` inside), and
 * [SendContinueButton] pinned at the bottom. The first step (no [onBack]) has Cancel beside
 * Continue; a later one has Back and Cancel in its header. [continueTitle] defaults to "Continue".
 *
 * [insets] are the system insets it keeps clear of itself, as on [SwipeUpToSendScreen]: the
 * system bars when it fills the window, none inside a sheet that already pads for them. The
 * keyboard is always kept clear of the bottom button. The system Back is the header's Back, or
 * Cancel on a first step.
 */
@Composable
fun SendStepScreen(
    title: String,
    onCancel: () -> Unit,
    canContinue: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    continueTitle: String? = null,
    insets: WindowInsets = WindowInsets.systemBars,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler { (onBack ?: onCancel)() }
    Column(
        modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background),
    ) {
        SendStepHeader(
            title = title,
            onBack = onBack,
            onCancel = if (onBack == null) null else onCancel,
            modifier = Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Top)),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            content = content,
        )
        SendContinueButton(
            isEnabled = canContinue,
            onClick = onContinue,
            title = continueTitle,
            onCancel = if (onBack == null) onCancel else null,
            modifier = Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Bottom).union(WindowInsets.ime)),
        )
    }
}

/**
 * "To alice.kachat", "From Chatting Address": one line of what a step is about, as a glass chip
 * above its content (the amount step; iOS 05db583 `SendStepChip`). [label] in grey, then [value]
 * semibold - or, with [isAddress], monospaced and cut in the middle.
 */
@Composable
fun SendStepChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isAddress: Boolean = false,
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .sendKaspaGlass(18.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, color = colors.textSecondary, fontSize = 15.sp, maxLines = 1)
        if (isAddress) {
            MiddleEllipsisText(
                value,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Text(
                value,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** The question a step asks, big, at the top of its content ("Who are you sending to?"): title2
 *  bold, leading (iOS 05db583 `SendStepQuestion`). */
@Composable
fun SendStepQuestion(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = LocalAppColors.current.textPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A review's fee editor, opened by its fee row's Edit (iOS 05db583 `SendFeeEditorScreen`): the
 * whole network fee typed on [KaspaKeypad] (8 decimals), Confirm hands it back in sompi, Cancel
 * (at the bottom, beside Confirm) leaves it as it was. Says the network's minimum when known
 * ([minimumSompi]: "The network needs at least 0.0002 KAS. A lower fee is raised to that." - the
 * caller does the raising) and warns, in orange, at more than ten times [minimumSompi], else ten
 * times [referenceSompi] (the fee it opened with). Confirm needs a fee above zero.
 */
@Composable
fun SendFeeEditorScreen(
    draft: String,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (Long) -> Unit,
    modifier: Modifier = Modifier,
    minimumSompi: Long? = null,
    referenceSompi: Long? = null,
    insets: WindowInsets = WindowInsets.systemBars,
) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    val draftSompi = KaspaUnit.sompiFromUserText(draft)?.takeIf { it > 0L }
    val reference = minimumSompi ?: referenceSompi
    val isHigh = draftSompi != null && reference != null && reference > 0L && draftSompi > reference * 10
    val minimumLine = minimumSompi?.let {
        KaspaUnit.label(tr(R.string.kp_the_network_needs_at_least_kas_ee488, KaspaUnit.plain(it)))
    }
    val footnote = if (isHigh) listOfNotNull(tr(R.string.kp_this_is_much_more_than_the_2ef8c), minimumLine).joinToString(" ") else minimumLine
    val feeLabel = stringResource(R.string.network_fee)

    SendStepScreen(
        title = feeLabel,
        onCancel = onCancel,
        canContinue = draftSompi != null,
        onContinue = {
            if (draftSompi != null) {
                haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                onConfirm(draftSompi)
            }
        },
        modifier = modifier,
        continueTitle = stringResource(R.string.confirm),
        insets = insets,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                tr(R.string.kp_set_your_network_fee_2787a),
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            KeypadAmountText(typed = draft, unit = KaspaUnit.symbol, accessibilityName = feeLabel)
            Text(
                footnote ?: " ",
                color = if (isHigh) colors.warning else colors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
            )
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            KaspaKeypad(
                onKey = { key ->
                    val text = KaspaKeypadKey.applying(key, draft, maxDecimals = 8)
                    if (text == draft) {
                        // iOS Haptics.impact(.rigid): the key couldn't apply
                        haptic(com.kachat.app.util.IosHaptic.IMPACT_MEDIUM)
                    } else {
                        onDraftChange(text)
                    }
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/**
 * The amount on a send's amount step, typed on the app's own number pad ([KaspaKeypad]), so no
 * system keyboard comes up (iOS 05db583 `KaspaKeypadAmountEntry`): the big amount and its unit
 * ([KeypadAmountText]), the KAS / your-currency switch and Max, a [footnote] (Available, or why
 * the amount can't be sent - red with [footnoteIsError]), then the keys. Fills the height it is
 * given, the amount centred in the room above the keys.
 *
 * [fiatAmountState] hands the screen the KAS amount text after every key (its own
 * `onKasTextChange`, as with [KaspaAmountEntry]); KAS takes up to 8 decimals, your currency 2.
 * [onKeyTyped] runs on any key that changed the amount (not Max): the screen stops treating the
 * amount as Max. [showsMax] false leaves Max out (an offer, a price, Request Payment).
 */
@Composable
fun KaspaKeypadAmountEntry(
    fiatAmountState: KaspaFiatAmountState,
    priceInCurrency: Double?,
    currencyCode: String,
    onMax: () -> Unit,
    modifier: Modifier = Modifier,
    isEstimatingMax: Boolean = false,
    maxEnabled: Boolean = true,
    showsMax: Boolean = true,
    footnote: String? = null,
    footnoteIsError: Boolean = false,
    onKeyTyped: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
        KeypadAmountText(
            typed = fiatAmountState.displayText,
            unit = if (fiatAmountState.isFiatMode) currencyCode.uppercase() else KaspaUnit.symbol,
            accessibilityName = KaspaUnit.label(stringResource(R.string.amount_kas)),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            if (priceInCurrency != null) {
                val switchLabel = stringResource(R.string.payment_switch_currency)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .sendKaspaGlass(14.dp)
                        .clickable { fiatAmountState.toggleMode(priceInCurrency) }
                        .clearAndSetSemantics {
                            contentDescription = switchLabel
                            role = Role.Button
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(14.dp))
                    Text(
                        fiatAmountState.conversionLabelText(priceInCurrency, currencyCode)
                            ?: if (fiatAmountState.isFiatMode) KaspaUnit.symbol else currencyCode.uppercase(),
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                    )
                }
            }
            if (showsMax) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .alpha(if (maxEnabled) 1f else 0.5f)
                        .sendKaspaGlass(14.dp)
                        .clickable(enabled = maxEnabled && !isEstimatingMax, role = Role.Button) { onMax() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    if (isEstimatingMax) {
                        com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(14.dp), color = KaspaTeal, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.max), color = KaspaTeal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
        Text(
            footnote ?: " ",
            color = if (footnoteIsError) colors.danger else colors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 20.dp, end = 20.dp),
        )
        Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
        KaspaKeypad(
            onKey = { key ->
                val typed = fiatAmountState.displayText
                val text = KaspaKeypadKey.applying(key, typed, maxDecimals = if (fiatAmountState.isFiatMode) 2 else 8)
                if (text == typed) {
                    // iOS Haptics.impact(.rigid): the key couldn't apply
                    haptic(com.kachat.app.util.IosHaptic.IMPACT_MEDIUM)
                } else {
                    onKeyTyped()
                    fiatAmountState.onDisplayTextChange(text, priceInCurrency)
                }
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/**
 * The big number a keypad types into (iOS 05db583 `KeypadAmountText`): [typed] grouped
 * ("12,345.5" - [KaspaAmountGrouping.grouped]; a grey "0" while empty) in bold rounded digits
 * and its [unit] at half the size, sharing a baseline, centred. 60sp up to 7 characters, 46 up to
 * 10, 34 beyond, and shrinking further (to half) rather than being cut. Read out as
 * [accessibilityName] with the amount as its value.
 */
@Composable
fun KeypadAmountText(
    typed: String,
    unit: String,
    accessibilityName: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val display = if (typed.isEmpty()) "0" else KaspaAmountGrouping.grouped(typed)
    val fontSize = when {
        display.length <= 7 -> 60.sp
        display.length <= 10 -> 46.sp
        else -> 34.sp
    }
    val amountStyle = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        fontFamily = IosRoundedFontFamily,
        fontFeatureSettings = "tnum",
    )
    val unitStyle = TextStyle(fontSize = fontSize * 0.5f, fontWeight = FontWeight.SemiBold, fontFamily = IosRoundedFontFamily)
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clearAndSetSemantics {
                contentDescription = accessibilityName
                stateDescription = display
            },
    ) {
        val maxWidth = constraints.maxWidth
        // iOS `.minimumScaleFactor(0.5)` on the amount: the unit and the gap stay as they are.
        val scale = remember(display, unit, fontSize, maxWidth, density) {
            if (maxWidth == Constraints.Infinity) {
                1f
            } else {
                val amountWidth = measurer.measure(display, amountStyle, maxLines = 1, softWrap = false).size.width
                val unitWidth = measurer.measure(unit, unitStyle, maxLines = 1, softWrap = false).size.width
                val room = maxWidth - unitWidth - with(density) { 8.dp.toPx() }
                if (amountWidth <= 0 || room >= amountWidth) 1f else (room / amountWidth).coerceIn(0.5f, 1f)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Text(
                display,
                style = amountStyle.copy(
                    fontSize = fontSize * scale,
                    color = if (typed.isEmpty()) colors.textSecondary else colors.textPrimary,
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                unit,
                style = unitStyle.copy(color = colors.textSecondary),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** A key of [KaspaKeypad] (iOS 05db583 `KaspaKeypad.Key`). [Clear] is a held delete. */
sealed class KaspaKeypadKey {
    data class Digit(val value: Int) : KaspaKeypadKey()
    object Decimal : KaspaKeypadKey()
    object Delete : KaspaKeypadKey()
    object Clear : KaspaKeypadKey()

    companion object {
        /**
         * The amount text after [key] (iOS `KaspaKeypad.applying`): plain digits with "." as the
         * point, at most [maxDecimals] decimals (8 for KAS, 2 for a currency) and 11 whole digits
         * (more than all the Kaspa there will ever be). A leading zero gives way to the next
         * digit; the point on an empty amount writes "0."; [maxDecimals] 0 takes no point. A key
         * that can't apply leaves the text as it was (the caller plays a firmer haptic then).
         */
        fun applying(key: KaspaKeypadKey, text: String, maxDecimals: Int): String = when (key) {
            Clear -> ""
            Delete -> text.dropLast(1)
            Decimal -> when {
                text.contains('.') || maxDecimals == 0 -> text
                text.isEmpty() -> "0."
                else -> "$text."
            }
            is Digit -> {
                val point = text.indexOf('.')
                when {
                    point >= 0 -> if (text.length - point - 1 >= maxDecimals) text else text + key.value
                    text == "0" -> key.value.toString()
                    text.length >= 11 -> text
                    else -> text + key.value
                }
            }
        }
    }
}

private val KaspaKeypadRows: List<List<KaspaKeypadKey>> = listOf(
    listOf(KaspaKeypadKey.Digit(1), KaspaKeypadKey.Digit(2), KaspaKeypadKey.Digit(3)),
    listOf(KaspaKeypadKey.Digit(4), KaspaKeypadKey.Digit(5), KaspaKeypadKey.Digit(6)),
    listOf(KaspaKeypadKey.Digit(7), KaspaKeypadKey.Digit(8), KaspaKeypadKey.Digit(9)),
    listOf(KaspaKeypadKey.Decimal, KaspaKeypadKey.Digit(0), KaspaKeypadKey.Delete),
)

/**
 * The app's own number pad (iOS 05db583 `KaspaKeypad`): 1-9, the decimal point (the device's
 * own, "," or "."), 0 and delete - hold delete to clear ([KaspaKeypadKey.Clear]). Four rows of
 * three 58-high keys, 4 apart, 30sp rounded digits; a pressed key gets a soft rounded highlight
 * and shrinks a little. A light haptic on every key. Used where a system keyboard would cover the
 * screen (a send's amount step, the fee editor); apply a key with [KaspaKeypadKey.applying].
 */
@Composable
fun KaspaKeypad(
    onKey: (KaspaKeypadKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KaspaKeypadRows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    KaspaKeypadKeyButton(key, onKey, Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun KaspaKeypadKeyButton(key: KaspaKeypadKey, onKey: (KaspaKeypadKey) -> Unit, modifier: Modifier) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // iOS KaspaKeypadKeyStyle: .easeOut(duration: 0.12)
    val pressSpec = androidx.compose.animation.core.tween<Float>(durationMillis = 120, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, animationSpec = pressSpec, label = "keypadKeyScale")
    val highlight by animateFloatAsState(if (pressed) 0.08f else 0f, animationSpec = pressSpec, label = "keypadKeyHighlight")
    val shape = RoundedCornerShape(16.dp)
    val description = when (key) {
        is KaspaKeypadKey.Digit -> key.value.toString()
        KaspaKeypadKey.Decimal -> tr(R.string.kp_decimal_point_429e5)
        KaspaKeypadKey.Delete, KaspaKeypadKey.Clear -> stringResource(R.string.delete)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(colors.textPrimary.copy(alpha = highlight))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = null,
                onLongClick = if (key == KaspaKeypadKey.Delete) {
                    {
                        haptic(com.kachat.app.util.IosHaptic.IMPACT_MEDIUM)
                        onKey(KaspaKeypadKey.Clear)
                    }
                } else null,
                onClick = {
                    haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                    onKey(key)
                },
            )
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onKey(key)
                    true
                }
            },
    ) {
        when (key) {
            is KaspaKeypadKey.Digit, KaspaKeypadKey.Decimal -> Text(
                if (key is KaspaKeypadKey.Digit) key.value.toString() else KaspaAmountGrouping.decimalPoint().toString(),
                color = colors.textPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = IosRoundedFontFamily,
            )
            KaspaKeypadKey.Delete, KaspaKeypadKey.Clear -> Icon(
                Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** One line that shrinks to fit its width rather than being cut, down to [minScale] of [style]'s
 *  size (iOS `.lineLimit(1).minimumScaleFactor`), then ellipsized. */
@Composable
private fun SendFitText(
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

// MARK: - From (which of your addresses a send comes out of)

/** A spending address as the Send screens name it: its nickname, else "Address #N", numbered on
 *  its own chain (iOS `SpendingAddressEntry.displayLabel`, English like iOS). */
fun spendingAddressDisplayLabel(index: Int, label: String?): String =
    label?.trim()?.takeIf { it.isNotEmpty() } ?: com.kachat.app.services.WalletManager.spendingAddressTitle(index)

/**
 * Where a send comes from (iOS 6333dd3 `SendSource`): the chatting address (every send screen's
 * default), or one of the spending addresses picked under Choose an Address.
 */
sealed class SendSource {
    object Chatting : SendSource()
    data class Spending(val index: Int, val address: String, val label: String) : SendSource()

    /** The spending index the send signs with, or null for the chatting address. */
    val spendingIndex: Int? get() = (this as? Spending)?.index

    /** The address the Kaspa leaves from - [chattingAddress] for the chatting address. */
    fun address(chattingAddress: String?): String? = when (this) {
        Chatting -> chattingAddress
        is Spending -> address
    }
}

/**
 * A pill to pick one of a few choices, like the amount's currency switch: the chosen one filled
 * in teal (iOS 6333dd3 `SelectablePill`). Send screens' From, Request Payment's address.
 */
@Composable
fun SelectablePill(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(KaspaTeal)
                else Modifier.background(colors.surface).border(1.dp, KaspaTeal.copy(alpha = 0.35f), shape)
            )
            .clickable(role = Role.Tab) {
                haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 6.dp),
    ) {
        Text(
            title,
            color = if (selected) Color.Black else colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The From section every send screen starts with (iOS 6333dd3 `SendFromSection`), so it is
 * always plain which address the Kaspa leaves from: Chatting Address (the default) or Choose an
 * Address (any of your addresses, each marked used or unused), and the chosen address with its
 * balance. The balance is loaded here ([onAvailableSompiChange], null while it loads) - the
 * screen's Available.
 */
@Composable
fun SendFromSection(
    source: SendSource,
    onSourceChange: (SendSource) -> Unit,
    availableSompi: Long?,
    onAvailableSompiChange: (Long?) -> Unit,
    walletViewModel: WalletViewModel,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Opened from one address's own page (its history's Send, iOS 7a8102b): that address only,
     *  no Chatting Address / Choose an Address choice. */
    isLocked: Boolean = false,
) {
    val colors = LocalAppColors.current
    val chattingAddress by walletViewModel.address.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val sourceAddress = source.address(chattingAddress)
    val currentOnAvailable by rememberUpdatedState(onAvailableSompiChange)
    LaunchedEffect(source, chattingAddress) {
        currentOnAvailable(null)
        val address = sourceAddress ?: return@LaunchedEffect
        currentOnAvailable(walletViewModel.addressBalanceSompi(address))
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.from).uppercase(),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (!isLocked) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectablePill(
                    title = stringResource(R.string.chatting_address),
                    selected = source is SendSource.Chatting,
                    modifier = Modifier.weight(1f),
                ) { if (enabled) onSourceChange(SendSource.Chatting) }
                SelectablePill(
                    title = stringResource(R.string.pr_choose_an_address),
                    selected = source !is SendSource.Chatting,
                    modifier = Modifier.weight(1f),
                ) { if (enabled) showPicker = true }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .sendKaspaGlass(14.dp)
                .padding(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when (source) {
                        SendSource.Chatting -> stringResource(R.string.chatting_address)
                        is SendSource.Spending -> source.label
                    },
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sourceAddress != null) {
                    Text(
                        "${sourceAddress.take(14)}...${sourceAddress.takeLast(6)}",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }
            if (availableSompi != null) {
                Text(
                    "${KaspaUnit.plain(availableSompi)} ${KaspaUnit.symbol}",
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
            } else {
                com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(18.dp), color = colors.textSecondary, strokeWidth = 2.dp)
            }
        }
    }

    if (showPicker) {
        OwnAddressPicker(
            title = stringResource(R.string.send_from),
            selected = sourceAddress,
            walletViewModel = walletViewModel,
            onPick = onSourceChange,
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * The amount step's From (iOS 05db583 `SendFromCompactRow`): the two pills of [SendFromSection] -
 * Chatting Address and Choose an Address (any of your addresses, in [OwnAddressPicker]'s sheet;
 * once picked, the pill shows its name) - after a grey FROM, without the address card under them,
 * so a keypad has room. The balance it loads ([onAvailableSompiChange], null while it loads) is
 * the amount's Available. With [isLocked] (opened from one address's own page) that address is
 * the one pill.
 */
@Composable
fun SendFromCompactRow(
    source: SendSource,
    onSourceChange: (SendSource) -> Unit,
    onAvailableSompiChange: (Long?) -> Unit,
    walletViewModel: WalletViewModel,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
) {
    val colors = LocalAppColors.current
    val chattingAddress by walletViewModel.address.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val sourceAddress = source.address(chattingAddress)
    val currentOnAvailable by rememberUpdatedState(onAvailableSompiChange)
    LaunchedEffect(source, chattingAddress) {
        currentOnAvailable(null)
        val address = sourceAddress ?: return@LaunchedEffect
        currentOnAvailable(walletViewModel.addressBalanceSompi(address))
    }
    val chattingTitle = stringResource(R.string.chatting_address)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.from).uppercase(),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (isLocked) {
            SelectablePill(
                title = (source as? SendSource.Spending)?.label ?: chattingTitle,
                selected = true,
                modifier = Modifier.weight(1f),
            ) {}
        } else {
            SelectablePill(
                title = chattingTitle,
                selected = source is SendSource.Chatting,
                modifier = Modifier.weight(1f),
            ) { onSourceChange(SendSource.Chatting) }
            SelectablePill(
                title = (source as? SendSource.Spending)?.label ?: stringResource(R.string.pr_choose_an_address),
                selected = source !is SendSource.Chatting,
                modifier = Modifier.weight(1f),
            ) { showPicker = true }
        }
    }

    if (showPicker) {
        OwnAddressPicker(
            title = stringResource(R.string.send_from),
            selected = sourceAddress,
            walletViewModel = walletViewModel,
            onPick = onSourceChange,
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * Your addresses to pick from, as a sheet (iOS 6333dd3 / 05db583 `OwnAddressPicker`): a send's
 * Choose an Address. The list itself is [OwnAddressList]; picking a row closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnAddressPicker(
    title: String,
    selected: String?,
    walletViewModel: WalletViewModel,
    onPick: (SendSource) -> Unit,
    onDismiss: () -> Unit,
    /** The .kachat market signs its funding inputs as plain P2PK, which a quantum-safe (KIP-22)
     *  address can't be - its picker leaves them out (iOS `excludeQuantum`). */
    excludeQuantum: Boolean = false,
) {
    val halfScreen = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp / 2).dp
    val pick: (SendSource) -> Unit = { source ->
        onPick(source)
        onDismiss()
    }

    com.kachat.app.ui.theme.IosSheetColors(grouped = true) {
        val colors = LocalAppColors.current
        // Floats on its height detent, as iOS 26's does, settling onto the edges as it grows.
        var sheetFloat by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            modifier = Modifier.iosFloatingSheet(sheetFloat),
            shape = iosFloatingSheetShape(sheetFloat),
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            dragHandle = null,
        ) {
            Column(Modifier.navigationBarsPadding()) {
                IosSheetDetents(height = halfScreen, largeDetent = true, onFloat = { sheetFloat = it }) {
                    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
                        SheetNavBar(
                            title = title,
                            leading = { SheetBarButton(stringResource(R.string.cancel), onClick = onDismiss) },
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            OwnAddressList(
                                selected = selected,
                                walletViewModel = walletViewModel,
                                onPick = pick,
                                excludeQuantum = excludeQuantum,
                            )
                        }
                    }
                    IosSheetGrabber(Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

/**
 * Your addresses as a list to sit on a screen - Request Payment's "Where do you want to be
 * paid?", a send's From step - or in [OwnAddressPicker]'s sheet (iOS 05db583 `OwnAddressList`):
 * the chatting address, then the spending addresses (the ones Manage Addresses shows, plus hidden
 * ones holding Kaspa), each a tappable glass card with its name, Used / Unused, the shortened
 * address and the balance; the [selected] one has a filled check and an accent edge, the rest an
 * empty circle. Whether an address was ever used: a balance means used, else the cached answer,
 * else a history lookup, eight at a time; "Checking" until answered, never a possibly-wrong
 * "Unused". Not scrollable itself: put it in the screen's scroll.
 */
@Composable
fun OwnAddressList(
    selected: String?,
    walletViewModel: WalletViewModel,
    onPick: (SendSource) -> Unit,
    modifier: Modifier = Modifier,
    /** Leaves quantum-safe (KIP-22) addresses out (the .kachat market, iOS `excludeQuantum`). */
    excludeQuantum: Boolean = false,
) {
    val colors = LocalAppColors.current
    val chattingAddress by walletViewModel.address.collectAsState()
    val chattingBalance by walletViewModel.balanceSompi.collectAsState()
    val allEntries by walletViewModel.manageAddresses.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        walletViewModel.loadManageAddressesAndAwait()
        loaded = true
    }
    val entries = remember(allEntries, excludeQuantum) {
        allEntries.filter { !it.hidden || it.balanceSompi > 0 }
            .filter { !excludeQuantum || !it.isQuantum }
            .sortedBy { it.index }
    }
    /** Used / unused per address; missing while its lookup is out (or failed). */
    val used = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val items = buildList {
        chattingAddress?.let { add(it to chattingBalance) }
        entries.forEach { add(it.address to it.balanceSompi) }
    }
    LaunchedEffect(items) {
        val toProbe = mutableListOf<String>()
        for ((address, balance) in items) {
            if (used[address] == true) continue
            if (balance > 0L) {
                used[address] = true
                continue
            }
            val cached = walletViewModel.cachedSpendingAddressUsedState(address)
            if (cached != null) used[address] = cached else toProbe.add(address)
        }
        // a few at a time, like Manage Addresses (a burst gets rate-limited)
        val gate = kotlinx.coroutines.sync.Semaphore(8)
        kotlinx.coroutines.coroutineScope {
            toProbe.forEach { address ->
                launch {
                    gate.acquire()
                    try {
                        // a failed lookup leaves it Checking, never a guessed Unused
                        walletViewModel.spendingAddressUsedState(address)?.let { used[address] = it }
                    } finally {
                        gate.release()
                    }
                }
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val chatting = chattingAddress
        if (chatting != null) {
            OwnAddressListSectionTitle(stringResource(R.string.chatting_address))
            OwnAddressListRow(
                title = stringResource(R.string.chatting_address),
                address = chatting,
                balance = chattingBalance,
                used = used[chatting],
                isSelected = chatting == selected,
                onClick = { onPick(SendSource.Chatting) },
            )
        }
        OwnAddressListSectionTitle(stringResource(R.string.pr_spending_addresses), Modifier.padding(top = 8.dp))
        if (entries.isEmpty() && !loaded) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary)
            }
        } else {
            entries.forEach { entry ->
                val label = spendingAddressDisplayLabel(entry.index, entry.label)
                OwnAddressListRow(
                    title = label,
                    address = entry.address,
                    balance = entry.balanceSompi,
                    used = used[entry.address],
                    isSelected = entry.address == selected,
                    onClick = { onPick(SendSource.Spending(entry.index, entry.address, label)) },
                )
            }
        }
    }
}

/** A section title of [OwnAddressList]: caption, semibold, grey, upper case. */
@Composable
private fun OwnAddressListSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        color = LocalAppColors.current.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

/** One address of [OwnAddressList]: a glass card, edged in the accent when [isSelected]. */
@Composable
private fun OwnAddressListRow(
    title: String,
    address: String,
    balance: Long?,
    used: Boolean?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .sendKaspaGlass(16.dp)
            .border(1.5.dp, KaspaTeal.copy(alpha = if (isSelected) 0.9f else 0f), shape)
            .clickable(role = Role.Button) {
                haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                onClick()
            }
            .semantics { this.selected = isSelected }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                OwnAddressUsedTag(used)
            }
            Text(
                if (address.length > 20) "${address.take(14)}...${address.takeLast(6)}" else address,
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        if (balance != null) {
            Text(
                "${KaspaUnit.plain(balance)} ${KaspaUnit.symbol}",
                color = if (balance > 0L) colors.textPrimary else colors.textSecondary,
                fontSize = 15.sp,
                maxLines = 1,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        Icon(
            if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) KaspaTeal else colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Used / Unused, or Checking while the lookup is out (iOS `usedTag`, 338d9f5). */
@Composable
private fun OwnAddressUsedTag(used: Boolean?) {
    val colors = LocalAppColors.current
    when (used) {
        true -> Text(
            stringResource(R.string.pr_used),
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.textSecondary.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        false -> Text(
            stringResource(R.string.pr_unused),
            color = colors.success,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.success.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        null -> Text(
            stringResource(R.string.pr_checking),
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

// MARK: - Send button

/**
 * The send button: slide the white knob to the right end to send, so a payment can't go out on a
 * stray touch. Haptics on the first move and on reaching the end; letting go before the end
 * springs it back; after a send that didn't go through (an error, the small-amount question) it
 * resets by itself, and again when the send finishes. Accessibility services get a plain click
 * action. With [requiresSlide] false it's an ordinary tap in the same look (KasSigner's "Build
 * Unsigned Transaction", which moves nothing by itself). Mirrors iOS's `SendActionButton`
 * (afaad34, was HoldToSendButton).
 */
@Composable
fun SendActionButton(
    title: String,
    isBusy: Boolean,
    isEnabled: Boolean,
    onSend: () -> Unit,
    requiresSlide: Boolean = true,
) {
    // iOS: a light impact as the knob leaves the start, a medium one when it reaches the end.
    val haptic = com.kachat.app.util.rememberHaptics()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnSend by rememberUpdatedState(onSend)
    val currentBusy by rememberUpdatedState(isBusy)
    val active = isEnabled && !isBusy
    val currentActive by rememberUpdatedState(active)
    val height = 56.dp
    val inset = 4.dp
    val knob = height - inset * 2
    // How far the knob has travelled, in px.
    var offset by remember { mutableFloatStateOf(0f) }
    var reachedEnd by remember { mutableStateOf(false) }
    var resetJob by remember { mutableStateOf<Job?>(null) }

    fun reset() {
        reachedEnd = false
        resetJob?.cancel()
        resetJob = scope.launch {
            // iOS .spring(response: 0.35, dampingFraction: 0.8).
            animate(offset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 322f)) { value, _ -> offset = value }
        }
    }

    // The send finished (or failed after starting): put the knob back.
    LaunchedEffect(isBusy) { if (!isBusy && offset != 0f) reset() }

    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(KaspaTeal.copy(alpha = if (isEnabled || isBusy) 1f else 0.4f))
            .then(
                if (requiresSlide) Modifier
                else Modifier.pointerInput(Unit) { detectTapGestures(onTap = { if (currentActive) currentOnSend() }) }
            )
            .clearAndSetSemantics {
                contentDescription = title
                role = Role.Button
                onClick(label = title) {
                    if (active) currentOnSend()
                    true
                }
            },
    ) {
        val knobPx = with(density) { knob.toPx() }
        val insetPx = with(density) { inset.toPx() }
        val maxOffset = (constraints.maxWidth - knobPx - insetPx * 2).coerceAtLeast(1f)
        val progress = if (requiresSlide) (offset / maxOffset).coerceIn(0f, 1f) else 0f

        if (requiresSlide) {
            // The trail behind the knob.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(inset * 2 + knob + with(density) { offset.toDp() })
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isBusy) 1f else 1f - progress),
        ) {
            if (isBusy) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text(title, color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
        }
        if (requiresSlide && !isBusy) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset((insetPx + offset).roundToInt(), 0) }
                    .size(knob)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(maxOffset) {
                        detectHorizontalDragGestures(
                            onDragStart = { if (currentActive) resetJob?.cancel() },
                            onDragEnd = {
                                if (!currentActive) return@detectHorizontalDragGestures
                                if (offset >= maxOffset * 0.95f) {
                                    offset = maxOffset
                                    currentOnSend()
                                    // A send that didn't start (dust question, a validation
                                    // error) leaves it not busy: put the knob back.
                                    scope.launch {
                                        delay(600)
                                        if (!currentBusy) reset()
                                    }
                                } else {
                                    reset()
                                }
                            },
                            onDragCancel = { reset() },
                        ) { change, dragAmount ->
                            if (!currentActive) return@detectHorizontalDragGestures
                            change.consume()
                            val next = (offset + dragAmount).coerceIn(0f, maxOffset)
                            if (offset == 0f && next > 0f) haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                            offset = next
                            if (offset >= maxOffset && !reachedEnd) {
                                reachedEnd = true
                                haptic(com.kachat.app.util.IosHaptic.IMPACT_MEDIUM)
                            } else if (offset < maxOffset) {
                                reachedEnd = false
                            }
                        }
                    },
            ) {
                Icon(
                    Icons.Default.KeyboardDoubleArrowRight,
                    contentDescription = null,
                    tint = KaspaTeal,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * The line under an address field: "Valid address", the other network's reason ("This is a
 * Testnet address. KaChat is on Mainnet."), or "Invalid address format" (iOS
 * `KaspaAddress.validityText`, ce20e87).
 */
@Composable
fun addressValidityText(address: String, isValid: Boolean): String = when {
    isValid -> stringResource(R.string.valid_address)
    else -> KaspaAddress.otherNetworkMessageRes(address)?.let { stringResource(it) }
        ?: stringResource(R.string.invalid_address_format)
}
