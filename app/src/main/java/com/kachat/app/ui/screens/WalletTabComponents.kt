package com.kachat.app.ui.screens

import com.kachat.app.util.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.KaspaNetwork
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.formatFiatAmount
import com.kachat.app.viewmodels.PortfolioViewModel

// The Wallet tab's pieces (iOS 953b255 ContactsView.swift, as KaChat's WalletView): see ProfileScreen.

/**
 * The top of the Wallet tab (iOS 953b255 `WalletBalanceCard`): the total balance, what it's worth
 * in your currency, then the chatting and spending addresses. Both parts or a dash: a total missing
 * one of them would read as the whole balance. A tap on the total copies it.
 */
@Composable
internal fun WalletBalanceCard(
    chattingSompi: Long?,
    spendingSompi: Long?,
    portfolioViewModel: PortfolioViewModel,
) {
    val colors = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val haptic = com.kachat.app.util.rememberHaptics()
    val price by portfolioViewModel.currentPriceUsd.collectAsState()
    val currency by portfolioViewModel.currency.collectAsState()
    LaunchedEffect(Unit) { portfolioViewModel.refreshSpotPriceIfStale() }
    val total = if (chattingSompi != null && spendingSompi != null) chattingSompi + spendingSompi else null
    // What the total is worth in your currency. Testnet coins aren't worth anything.
    val fiat = price?.takeIf { total != null && !KaspaNetwork.isTestnet && it > 0.0 }?.let { p ->
        formatFiatAmount((total ?: 0L) / 100_000_000.0 * p, currency, decimals = 4)
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(KaspaTeal.copy(alpha = 0.32f), KaspaTeal.copy(alpha = 0.08f))))
            .border(0.8.dp, KaspaTeal.copy(alpha = 0.35f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr(R.string.kp_total_balance_d4e12), color = colors.textSecondary, fontSize = 15.sp)
            KachatFitText(
                total?.let { KaspaUnit.amount(it) } ?: "--",
                color = colors.textPrimary,
                fontSize = LargeTitleSize,
                fontWeight = FontWeight.Bold,
                minScale = 0.6f,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (total != null) {
                        clipboard.setText(AnnotatedString(KaspaUnit.plain(total)))
                        haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        IosToasts.show(tr(R.string.kp_balance_copied_to_clipboard_877b1))
                    }
                },
            )
            if (fiat != null) {
                Text(fiat, color = colors.textSecondary, fontSize = 15.sp)
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            WalletBalancePart(tr(R.string.kp_chatting_c39dc), chattingSompi, Modifier.weight(1f))
            WalletBalancePart(tr(R.string.kp_spending_45614), spendingSompi, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WalletBalancePart(title: String, sompi: Long?, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = colors.textSecondary, fontSize = 12.sp)
        KachatFitText(
            sompi?.let { KaspaUnit.amount(it) } ?: "--",
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            minScale = 0.7f,
        )
    }
}

/** Send, Receive and Request (iOS 953b255 `walletActionsRow`). */
@Composable
internal fun WalletActionsRow(onSend: () -> Unit, onReceive: () -> Unit, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        WalletActionButton(tr(R.string.kp_send_9bc25), Icons.Filled.ArrowUpward, Modifier.weight(1f), onSend)
        WalletActionButton(tr(R.string.kp_receive_965fb), Icons.Filled.ArrowDownward, Modifier.weight(1f), onReceive)
        WalletActionButton(tr(R.string.kp_request_4aed0), Icons.Outlined.Description, Modifier.weight(1f), onRequest)
    }
}

/** One of Send, Receive and Request (iOS `walletActionButton`): a 60 accent circle with a black
 *  symbol over its subheadline title. */
@Composable
private fun WalletActionButton(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = com.kachat.app.util.rememberHaptics()
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(60.dp).clip(CircleShape).background(KaspaTeal),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(26.dp))
        }
        Text(title, color = LocalAppColors.current.textPrimary, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Manage Addresses and Chatting Address (iOS 953b255 `addressSquares`). */
@Composable
internal fun WalletAddressSquares(onManageAddresses: () -> Unit, onChattingAddress: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WalletAddressSquare(
            icon = Icons.AutoMirrored.Filled.ListAlt,
            title = stringResource(R.string.manage_addresses),
            modifier = Modifier.weight(1f),
            onClick = onManageAddresses,
        )
        WalletAddressSquare(
            icon = Icons.Filled.QrCode,
            title = stringResource(R.string.chatting_address),
            modifier = Modifier.weight(1f),
            onClick = onChattingAddress,
        )
    }
}

/**
 * One square (iOS `addressSquare`): material with the accent symbol in a 48 circle over its title.
 * Square on a phone. As wide as half a tablet, a square would fill most of the screen with nothing,
 * so there it keeps the width and a set height (iOS 652c8af `addressSquareShape`).
 */
@Composable
private fun WalletAddressSquare(icon: ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(18.dp)
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    Column(
        modifier = modifier
            .then(if (isWide) Modifier.height(170.dp) else Modifier.aspectRatio(1f))
            .clip(shape)
            .background(colors.surface)
            .border(0.8.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
        }
        Text(
            title,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
