package com.kachat.app.ui.screens

import com.kachat.app.util.tr
import androidx.compose.foundation.layout.statusBars
import com.kachat.app.ui.theme.IosSymbols
import com.kachat.app.ui.theme.iosGlass

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.kachat.app.ui.theme.iosShadow
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import com.kachat.app.services.AddressActivityNotifier
import com.kachat.app.services.ColdStorageAddressDiscovery
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Repeat
import com.kachat.app.util.KaspaUnit

/**
 * The half sheet behind every long-press menu and the composer's "+": a title (and an optional
 * line under it), then the [ActionSheetRow]s as square tiles, three to a row. Exactly [height]
 * tall, the way iOS fixes each of these sheets' detent with `ActionSheetTileMetrics.sheetHeight`
 * rather than letting it grow to its content: the title 20 below the top edge, the grabber drawn
 * over the top rather than taking a row of its own, the tiles under the title, room to spare
 * below them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileActionSheet(
    title: String,
    onDismiss: () -> Unit,
    height: Dp,
    subtitle: String? = null,
    subtitleFontSize: TextUnit = 12.sp,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    bottomPadding: Dp = 24.dp,
    /** Told how many tiles the grid holds, for a sheet whose height follows its tile count. */
    onTileCount: ((Int) -> Unit)? = null,
    /** iOS's second `.large` detent: the sheet opens at [height] and drags up to full height. */
    largeDetent: Boolean = false,
    content: @Composable () -> Unit,
) {
    TileActionSheet(
        title = title,
        onDismiss = onDismiss,
        height = height,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        subtitle = subtitle,
        subtitleFontSize = subtitleFontSize,
        subtitleMaxLines = subtitleMaxLines,
        bottomPadding = bottomPadding,
        onTileCount = onTileCount,
        largeDetent = largeDetent,
        content = content,
    )
}

/** The same sheet on a [sheetState] the caller holds (the .kachat Manage Name sheet hides it
 *  itself before acting) - a separate overload so the experimental [SheetState] stays out of
 *  every other caller's signature, as [ActionSheetContainer] does. */
@ExperimentalMaterial3Api
@Composable
fun TileActionSheet(
    title: String,
    onDismiss: () -> Unit,
    height: Dp,
    sheetState: SheetState,
    subtitle: String? = null,
    subtitleFontSize: TextUnit = 12.sp,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    bottomPadding: Dp = 24.dp,
    onTileCount: ((Int) -> Unit)? = null,
    largeDetent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        // Floats on its height detent, as iOS 26's does, settling onto the edges as it grows.
        var float by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            modifier = Modifier.iosFloatingSheet(float),
            shape = iosFloatingSheetShape(float),
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = colors.background,
            dragHandle = null,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                IosSheetDetents(height = height, largeDetent = largeDetent, onFloat = { float = it }) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                title,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    subtitle,
                                    color = colors.textSecondary,
                                    fontSize = subtitleFontSize,
                                    textAlign = TextAlign.Center,
                                    maxLines = subtitleMaxLines,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            ActionSheetTiles(content, onTileCount)
                        }
                    }
                    IosSheetGrabber(Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

/**
 * A sheet's height on iOS's detents: exactly [height] (`.height(...)`), or with [largeDetent]
 * (`[.height(...), .large]`) that height plus the full one - drag up and the sheet grows to the
 * top, following the finger; let go and it settles on whichever detent the drag was heading for
 * (a flick decides it). Dragging down from full height brings it back to [height]; from [height]
 * the drag goes on to the sheet itself, which closes it as before.
 *
 * The drag reaches the enclosing ModalBottomSheet through nested scrolling, so the sheet's own
 * drag-to-dismiss and its scroll-aware settling keep working underneath.
 */
@Composable
fun IosSheetDetents(
    height: Dp,
    largeDetent: Boolean,
    /** How floating the sheet should be drawn: 1 on the height detent, 0 at full height (see
     *  [iosFloatingSheet]). Called as the sheet grows and shrinks. */
    onFloat: ((Float) -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    if (!largeDetent) {
        androidx.compose.runtime.SideEffect { onFloat?.invoke(1f) }
        Box(Modifier.fillMaxWidth().height(height)) { content() }
        return
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        // iOS's .large stops 10 below the status bar, the screen behind showing as a sliver.
        val fullPx = constraints.maxHeight.toFloat() - with(density) { 10.dp.toPx() }
        val fixedPx = with(density) { height.toPx() }.coerceAtMost(fullPx)
        val rangePx = (fullPx - fixedPx).coerceAtLeast(0f)
        // How far above the fixed detent the sheet is drawn: 0 at [height], rangePx at full.
        var extraPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val extra = extraPx.coerceIn(0f, rangePx)
        androidx.compose.runtime.SideEffect { onFloat?.invoke(if (rangePx > 0f) 1f - extra / rangePx else 1f) }
        val latestRange by rememberUpdatedState(rangePx)
        val flickPx = with(density) { 125.dp.toPx() }
        val scrollState = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
            // Down is positive: dragging up grows the sheet, dragging down shrinks it to the
            // fixed detent; what is left over goes on to the sheet (and closes it).
            val old = extraPx.coerceIn(0f, latestRange)
            val new = (old - delta).coerceIn(0f, latestRange)
            extraPx = new
            old - new
        }
        val settle = remember(flickPx) {
            object : androidx.compose.foundation.gestures.FlingBehavior {
                override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
                    val range = latestRange
                    val from = extraPx.coerceIn(0f, range)
                    // At the fixed detent and not heading up: the sheet itself settles (or closes).
                    if (range <= 0f || (from <= 0f && initialVelocity >= 0f)) return initialVelocity
                    val target = when {
                        initialVelocity < -flickPx -> range
                        initialVelocity > flickPx -> 0f
                        else -> if (from > range / 2f) range else 0f
                    }
                    androidx.compose.animation.core.animate(
                        initialValue = from,
                        targetValue = target,
                        initialVelocity = -initialVelocity,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        ),
                    ) { value, _ -> extraPx = value }
                    return 0f
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { (fixedPx + extra).toDp() })
                .scrollable(
                    state = scrollState,
                    orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                    flingBehavior = settle,
                ),
        ) { content() }
    }
}

/**
 * iOS 26 floats a sheet that rests on a height detent (not `.large`): 8 in from the screen's
 * sides and bottom with every corner rounded, its content still clear of the home indicator.
 * Pass as the ModalBottomSheet's modifier, with [IosFloatingSheetShape] as its shape.
 */
fun Modifier.iosFloatingSheet(): Modifier = iosFloatingSheet(1f)

/**
 * The same, [float] of the way: 1 floating, 0 on the screen's edges. A sheet that can also grow
 * to `.large` floats on its height detent and settles onto the edges as it grows, as iOS 26's
 * does - pair with [iosFloatingSheetShape] and [IosSheetDetents]'s `onFloat`.
 */
fun Modifier.iosFloatingSheet(float: Float): Modifier = this
    .padding(horizontal = (8 * float).dp)
    .offset(y = (-8 * float).dp)

/** The floating sheet's outline: iOS 26 rounds all four corners of a sheet that floats. */
val IosFloatingSheetShape = RoundedCornerShape(36.dp)

/** The outline [float] of the way to floating: the bottom corners round as the sheet lifts. */
fun iosFloatingSheetShape(float: Float): RoundedCornerShape =
    if (float >= 1f) IosFloatingSheetShape
    else RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = (36 * float).dp, bottomEnd = (36 * float).dp)

/**
 * iOS's sheet drag indicator (`.presentationDragIndicator(.visible)`): a 36 x 5 rounded bar 5
 * below the sheet's top edge, drawn over the content rather than above it, so a sheet's height
 * and its title's place match iOS's.
 */
@Composable
fun IosSheetGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 5.dp)
            .size(width = 36.dp, height = 5.dp)
            .clip(CircleShape)
            .background(LocalAppColors.current.textSecondary.copy(alpha = 0.5f)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheetContainer(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    /** Extra line under the subtitle - the txid on the transaction menu, for instance. */
    detail: String? = null,
    /** What iOS shows as a grouped List (the reactions sheet): the grouped light palette - see
     *  [com.kachat.app.ui.theme.IosSheetColors]. */
    grouped: Boolean = false,
    /** Above the title: the Delete Account warning's triangle. */
    headerIcon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ActionSheetContainer(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        headerIcon = headerIcon,
        grouped = grouped,
        // Opens EXPANDED, not half-height. Partial expansion caps the opening height at about
        // half the screen, so a sheet with more than a few options opened already cut off - the
        // 1:1 composer's + menu hid Send Handshake below the fold, and an option you cannot see
        // is an option that does not exist. Expanded still wraps its content, so a short sheet
        // looks exactly as it did.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        detail = detail,
        content = content,
    )
}

/**
 * The same sheet on a [sheetState] the caller holds, for a caller that hides the sheet itself and
 * acts once it has gone down (the .kachat Manage Name sheet, iOS f61b978). A separate overload so
 * the experimental [SheetState] stays out of every other caller's signature.
 */
@ExperimentalMaterial3Api
@Composable
fun ActionSheetContainer(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    detail: String? = null,
    grouped: Boolean = false,
    headerIcon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosSheetColors(grouped = grouped) {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            // A height-detent sheet on iOS 26, so it floats (see iosFloatingSheet).
            modifier = Modifier.iosFloatingSheet(),
            shape = IosFloatingSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = colors.background,
            // iOS's grabber sits over the content, not in a band of its own above the title.
            dragHandle = null,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Box(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // For the rare sheet taller than the screen itself - expanded tops out at full
                        // height, and without this the overflow would be unreachable rather than merely
                        // below the fold.
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // iOS: the title in headline 20 below the sheet's top, 4 more under the
                    // title block before the rows (16 with the stack's spacing).
                    Column(
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        headerIcon?.invoke()
                        Text(
                            title,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!detail.isNullOrBlank()) {
                            Text(
                                detail,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    content()
                }
                IosSheetGrabber(Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/**
 * The options button at the top right of every address page (the chatting address, each spending
 * address, each Cold Storage address). Opens [AddressActionsSheet] (iOS 2da5f4b).
 */
@Composable
fun AddressOptionsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = com.kachat.app.util.rememberHaptics()
    SheetBarIconButton(
        Icons.Default.MoreHoriz,
        stringResource(R.string.address_actions),
        onClick = {
            haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
            onClick()
        },
        modifier = modifier,
    )
}

/**
 * The same sheet on every address page (iOS 2da5f4b `AddressActionsSheet`). [onViewPrivateKey] is
 * null where the app doesn't hold the key (Cold Storage); [showsPublicKey] is false on a quantum
 * address, which keeps its key hidden until it spends, so it has none to show.
 */
@Composable
fun AddressActionsSheet(
    onViewPublicKey: () -> Unit,
    onViewInExplorer: () -> Unit,
    onDismiss: () -> Unit,
    showsPublicKey: Boolean = true,
    onViewPrivateKey: (() -> Unit)? = null,
) {
    ActionSheetContainer(
        title = stringResource(R.string.address_actions),
        subtitle = null,
        onDismiss = onDismiss,
    ) {
        if (onViewPrivateKey != null) {
            ActionSheetRow(
                icon = Icons.Default.Key,
                title = "View Private Key",
                subtitle = "The key that spends this address. Never share it.",
                onClick = onViewPrivateKey,
            )
        }
        if (showsPublicKey) {
            ActionSheetRow(
                icon = Icons.Default.Numbers,
                title = "View Public Key",
                subtitle = "The public half of this address, for anyone who asks for it.",
                onClick = onViewPublicKey,
            )
        }
        ActionSheetRow(
            icon = Icons.Default.Public,
            title = stringResource(R.string.view_in_explorer),
            subtitle = "Opens this address on your chosen block explorer.",
            onClick = onViewInExplorer,
        )
    }
}

/**
 * One option in an [ActionSheetContainer]. Same shape as iOS's `ActionSheetRow`: a row with its
 * line saying what it does - or, inside [ActionSheetTiles], a square tile (iOS cdac6d0).
 */
@Composable
fun ActionSheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = KaspaTeal,
    /** iOS `isDisabled`: shown, dimmed to half, and not tappable. */
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ActionSheetRowFrame(
        icon = { size -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size)) },
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        enabled = enabled,
        // iOS: the tile's title takes the tint, unless it is the accent.
        tileTitleColor = if (tint == KaspaTeal) null else tint,
    )
}

/**
 * The same row with an asset image in place of the icon glyph - the Kaspa logo on "Pay in
 * Kaspa", which every menu in the app shows with the logo rather than a stand-in. Mirrors iOS's
 * `ActionSheetRow(customIcon:)`. The image is drawn as-is, untinted.
 */
@Composable
fun ActionSheetRow(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ActionSheetRowFrame(
        // A touch larger as a tile, like iOS's custom icon (26 pt against the glyphs' 24).
        icon = { size ->
            Icon(icon, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(if (size > 22.dp) 26.dp else size))
        },
        title = title,
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
private fun ActionSheetRowFrame(
    icon: @Composable (Dp) -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    /** The tile form's title colour; null is the primary text colour. */
    tileTitleColor: Color? = null,
) {
    val colors = LocalAppColors.current
    if (LocalActionSheetTiles.current) {
        // The square form, for long-press menus: the icon and a short title, so a glance says
        // what each one does. The explanatory line becomes the TalkBack hint (iOS cdac6d0).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (enabled) 1f else 0.45f)
                .iosGlass(18.dp)
                .clickable(enabled = enabled) { onClick() }
                .semantics { contentDescription = "$title. $subtitle" }
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // iOS's 24pt semibold symbol fills its size; a Material glyph fills about 20 of 24.
            Box(modifier = Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                icon(28.dp)
            }
            Text(
                title,
                color = tileTitleColor ?: colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    // iOS's row: the glyph centred in 28, 12 to the words - the title in .subheadline semibold
    // (taking the tint unless it is the accent), the line under it in .caption, 2 apart - on the
    // frosted card (glassBackground, 16 corners, its shadow).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .iosGlass(16.dp)
            .clickable(enabled = enabled) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { icon(22.dp) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = tileTitleColor ?: colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
        }
    }
}

/** Set by [ActionSheetTiles]: its [ActionSheetRow]s draw as square tiles instead of rows. */
private val LocalActionSheetTiles = staticCompositionLocalOf { false }

/**
 * Lays the [ActionSheetRow]s inside it out as square tiles, three to a row - the look of every
 * long-press menu in the app (messages, chat rows, group and room circles, room rows), so each
 * option reads at a glance instead of as a paragraph (iOS cdac6d0 `ActionSheetTiles`). Other
 * half sheets (confirmations, account menus) keep the row form.
 *
 * Each child is one tile: the columns share the width evenly, and a short last row keeps the
 * column width rather than stretching. A [TileActionSheet] holds them at the height iOS gives its
 * detent ([ActionSheetTileMetrics.sheetHeight]).
 */
@Composable
fun ActionSheetTiles(content: @Composable () -> Unit, onTileCount: ((Int) -> Unit)? = null) {
    CompositionLocalProvider(LocalActionSheetTiles provides true) {
        Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
            onTileCount?.invoke(measurables.size)
            val spacing = ActionSheetTileMetrics.spacing.roundToPx()
            val tileHeight = ActionSheetTileMetrics.tileHeight.roundToPx()
            val width = constraints.maxWidth
            val tileWidth = ((width - spacing * 2) / 3).coerceAtLeast(0)
            val placeables = measurables.map { it.measure(Constraints.fixed(tileWidth, tileHeight)) }
            val rows = (placeables.size + 2) / 3
            val height = if (rows == 0) 0 else rows * tileHeight + (rows - 1) * spacing
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.place((index % 3) * (tileWidth + spacing), (index / 3) * (tileHeight + spacing))
                }
            }
        }
    }
}

/** Sizes for [ActionSheetTiles], and a sheet height to fit them (iOS `ActionSheetTileMetrics`). */
object ActionSheetTileMetrics {
    val tileHeight = 96.dp
    val spacing = 12.dp

    /** The grid's height for [count] tiles (one row at least). */
    fun gridHeight(count: Int): Dp {
        val rows = maxOf(1, (count + 2) / 3)
        return tileHeight * rows + spacing * (rows - 1)
    }

    /** A [TileActionSheet]'s height: its title area, the grid, and the bottom padding. */
    fun sheetHeight(tiles: Int, header: Dp = 70.dp): Dp = header + gridHeight(tiles) + 44.dp
}

/**
 * The rename half of an [ActionSheetContainer] that renames something in place: a field, a Cancel
 * that walks back to the options, and a Save that commits and closes.
 */
@Composable
fun ColumnScope.ActionSheetRenameFields(
    value: String,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    label: String = stringResource(R.string.name),
) {
    val colors = LocalAppColors.current
    val focusRequester = remember { FocusRequester() }
    // The keyboard should be up the moment the field appears - the tap that revealed it was
    // already the decision to type.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    com.kachat.app.ui.theme.IosTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onSave() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = KaspaTeal,
            unfocusedBorderColor = colors.textSecondary,
            focusedLabelColor = KaspaTeal,
            unfocusedLabelColor = colors.textSecondary,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
        ) {
            Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onSave,
            enabled = value.isNotBlank(),
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, contentColor = Color.Black),
        ) {
            Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * What to do with a transaction you tapped in an address history: look at it, or record it.
 *
 * Shared by cold storage history, the spending-address history and the chatting address, all
 * three of which used to raise their own identical popup menu.
 */
@Composable
fun TransactionActionsSheet(
    tx: ColdStorageAddressDiscovery.AddressTransaction,
    onOpenExplorer: () -> Unit,
    onAddToPortfolio: () -> Unit,
    onDismiss: () -> Unit,
) {
    ActionSheetContainer(
        title = tr(R.string.kp_transaction_1a7b7),
        subtitle = summary(tx, com.kachat.app.util.is24HourClock()),
        detail = AddressActivityNotifier.shortAddress(tx.txId),
        onDismiss = onDismiss,
    ) {
        ActionSheetRow(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            title = "Open in Explorer",
            subtitle = "Opens this transaction on the block explorer.",
            onClick = onOpenExplorer,
        )
        ActionSheetRow(
            icon = Icons.Default.PieChart,
            title = tr(R.string.kp_add_to_portfolio_9eb23),
            subtitle = "Records it as a buy or a sell in a portfolio of your choosing.",
            onClick = onAddToPortfolio,
        )
    }
}

/** "Sent 12.5 KAS on Sep 3, 2026, 2:02 PM" - what is about to be acted on, in one line. */
private fun summary(tx: ColdStorageAddressDiscovery.AddressTransaction, is24Hour: Boolean): String {
    val direction = if (tx.sent) tr(R.string.kp_sent_35f49) else tr(R.string.kp_received_27548)
    val amount = AddressActivityNotifier.formatKas(tx.amountSompi)
    val time = tx.blockTimeMillis?.let {
        com.kachat.app.util.IosDateStyle.mediumDateShortTime(it, is24Hour)
    }
    return if (time == null) "$direction $amount ${KaspaUnit.symbol}" else "$direction $amount ${KaspaUnit.symbol} on $time"
}

/**
 * A yes/no confirmation, in a half sheet.
 *
 * The app's confirmations were AlertDialogs - a stack of bare verbs with the reason squeezed into
 * a small line above them. As a sheet the consequence gets a full row of its own next to the
 * action it belongs to, which is what someone about to log out or delete something is actually
 * reading for.
 */
@Composable
fun ConfirmActionSheet(
    title: String,
    confirmTitle: String,
    /** What actually happens if they go ahead - the row's second line. */
    confirmSubtitle: String,
    confirmIcon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true,
    /** An optional SECOND way to go ahead that takes more with it - iOS's "Delete and Remove
     *  Nextcloud Backup" beside a plain Delete. All four are needed for the row to appear, so
     *  every existing caller is unchanged. */
    secondaryTitle: String? = null,
    secondarySubtitle: String? = null,
    secondaryIcon: ImageVector? = null,
    onSecondary: (() -> Unit)? = null,
) {
    ActionSheetContainer(title = title, subtitle = null, onDismiss = onDismiss) {
        ActionSheetRow(
            icon = confirmIcon,
            title = confirmTitle,
            subtitle = confirmSubtitle,
            tint = if (isDestructive) LocalAppColors.current.danger else KaspaTeal,
            onClick = { onDismiss(); onConfirm() },
        )
        if (secondaryTitle != null && secondarySubtitle != null && secondaryIcon != null && onSecondary != null) {
            ActionSheetRow(
                icon = secondaryIcon,
                title = secondaryTitle,
                subtitle = secondarySubtitle,
                tint = LocalAppColors.current.danger,
                onClick = { onDismiss(); onSecondary() },
            )
        }
        ActionSheetRow(
            icon = Icons.Default.Close,
            title = tr(R.string.kp_cancel_77dfd),
            subtitle = "Leave everything as it is.",
            onClick = onDismiss,
        )
    }
}

/**
 * The warning before an account is deleted, from Settings or the sign-in screen (iOS 99ab88a).
 * Nothing can bring the account back without its seed phrase (and passphrase), so it says that
 * first. Deleting stays possible when no backup was made: an empty account someone doesn't want
 * shouldn't have to be backed up first.
 */
@Composable
fun DeleteAccountSheet(
    /** What goes: the Delete row's second line. */
    confirmSubtitle: String,
    /** This account hasn't finished the backup steps: the warning says so. */
    notBackedUp: Boolean = false,
    /** Settings only: a signed-out account on the sign-in screen has no seed phrase open to show. */
    onViewSeedPhrase: (() -> Unit)? = null,
    /** Settings only: into the backup steps, in place of the seed phrase row. */
    onBackUp: (() -> Unit)? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ActionSheetContainer(
        title = tr(R.string.kp_delete_account_ee1b9),
        subtitle = null,
        onDismiss = onDismiss,
        headerIcon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = LocalAppColors.current.danger,
                modifier = Modifier.padding(bottom = 8.dp).size(36.dp),
            )
        },
    ) {
        val colors = LocalAppColors.current
        if (notBackedUp) {
            Text(
                tr(R.string.kp_this_account_isn_t_backed_up_9646a),
                color = colors.danger,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (onBackUp == null) {
                Text(
                    // The backup strip, not a card on the Wallet tab (iOS 0799cec).
                    tr(R.string.kp_to_back_it_up_first_sign_fdb56),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            tr(R.string.kp_make_sure_your_12_or_24_81a5d),
            color = colors.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (notBackedUp && onBackUp != null) {
            ActionSheetRow(
                icon = Icons.Outlined.GppMaybe,
                title = tr(R.string.kp_back_up_account_e033f),
                subtitle = "Write down your 12 or 24 words before you delete.",
                onClick = onBackUp,
            )
        } else if (onViewSeedPhrase != null) {
            ActionSheetRow(
                icon = Icons.Outlined.Key,
                title = "View 12 or 24 Words and Passphrase",
                subtitle = "The passphrase shows too, if this account has one.",
                onClick = onViewSeedPhrase,
            )
        }
        ActionSheetRow(
            icon = Icons.Outlined.Delete,
            title = tr(R.string.kp_delete_account_ee1b9),
            subtitle = confirmSubtitle,
            tint = colors.danger,
            onClick = { onDismiss(); onConfirm() },
        )
        ActionSheetRow(
            icon = Icons.Default.Close,
            title = tr(R.string.kp_cancel_77dfd),
            subtitle = "Leave everything as it is.",
            onClick = onDismiss,
        )
    }
}

/**
 * A completed Kaspa send, for [SentConfirmationSheet].
 *
 * [amountSompi] and [recipient] are optional: a consolidation self-send has no meaningful "to
 * whom", and naming the address it just came from reads as a mistake.
 */
data class SentTransaction(
    val txId: String,
    val amountSompi: Long? = null,
    val recipient: String? = null,
)

/**
 * A sheet that iOS draws as a `NavigationStack` holding a `List` or `Form` with an inline title
 * (chat info's ".kachat Names" and "KNS Domains"): the grouped palette, the title centred in the
 * bar, then one 26pt-cornered section of rows - [content] draws the rows, [IosListRowDivider]
 * between them. Opens at half height and drags up to full (`[.medium, .large]`), with the grabber.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosInlineListSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val halfScreen = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp / 2).dp
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
                    Column(Modifier.fillMaxSize()) {
                        IosSheetNavBar(title = title)
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(colors.surface),
                                content = content,
                            )
                        }
                    }
                    IosSheetGrabber(Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

/** The hairline between two rows of an [IosInlineListSheet], inset from the leading edge as
 *  iOS's separator is (past the icon, when the row has one). */
@Composable
fun IosListRowDivider(start: Dp = 16.dp) {
    HorizontalDivider(
        color = LocalAppColors.current.divider,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = start),
    )
}

/**
 * The app's underline tab bar - bold teal labels, dimmed when unselected, a teal bar under the
 * selected one and a divider below: the Chats / Group Chats / Public Chats bar, shared by Your
 * Domains and Connection Settings (iOS UnderlineTabBar, ee01f81).
 */
@Composable
fun UnderlineTabBar(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // iOS's UnderlineTabBar exactly: equal-width 15pt bold labels that shrink to fit (down to
    // 75%) rather than being cut - "Translation" stays whole - and its underline and hairline.
    HubUnderlineTabBar(titles = titles, selectedIndex = selectedIndex, onSelect = onSelect, modifier = modifier)
}

