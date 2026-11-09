package com.kachat.app.ui.screens

import com.kachat.app.util.tr
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.IosHaptic
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.util.rememberHaptics
import com.kachat.app.viewmodels.WalletViewModel

// Backing up an account, in plain steps, and checking on it every six months (iOS 99ab88a
// AccountBackupView.swift). The text is English only for now, as on iOS.

/** Opens the backup steps behind the seed phrase's biometric gate, when enabled. */
internal fun unlockAccountBackup(context: Context, biometricEnabled: Boolean, show: () -> Unit) {
    val open = { SeedRouteGate.arm(); show() }
    if (biometricEnabled) {
        context.authenticateWithDeviceCredential(title = "Unlock to view your 12 or 24 words", onSuccess = open)
    } else {
        open()
    }
}

/** The typed passphrase is the saved one, compared in NFKD: accounts store it NFKD
 *  ([com.kachat.app.services.WalletManager.nfkd], audit XP-013) and a keyboard may type it composed. */
internal fun passphraseMatches(typed: String, saved: String): Boolean =
    com.kachat.app.services.WalletManager.nfkd(typed) == com.kachat.app.services.WalletManager.nfkd(saved)

/**
 * The seed routes (`SETTINGS_SHEET_ROUTES`: the words, the backup steps, Backup Hygiene, Back Up
 * to NFC Card) open only through [unlockAccountBackup] or the hygiene card. Navigation restores
 * its back stack after the OS kills the app, which would bring one back without the lock, so a
 * route this process didn't open closes itself ([SeedRouteGuard]).
 */
internal object SeedRouteGate {
    @Volatile
    private var armed = false
    private val openedEntries: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    fun arm() {
        armed = true
    }

    /** Whether back-stack entry [entryId] was opened in this process: a new entry takes the arming. */
    fun admit(entryId: String): Boolean {
        if (entryId in openedEntries) return true
        if (!armed) return false
        armed = false
        openedEntries.add(entryId)
        return true
    }
}

/** Shows [content] only for a seed route opened in this process (see [SeedRouteGate]); one
 *  restored after a process death calls [onRefused] (pop) instead. */
@Composable
internal fun SeedRouteGuard(entryId: String, onRefused: () -> Unit, content: @Composable () -> Unit) {
    val admitted = remember(entryId) { SeedRouteGate.admit(entryId) }
    if (admitted) {
        content()
    } else {
        LaunchedEffect(entryId) { onRefused() }
    }
}

/**
 * The strip under the balance on every main screen while an account isn't backed up (iOS 953b255
 * `AccountBackupBanner`). It stays until the backup steps are finished, and turns red once the
 * account holds funds; funds on a spending address count too, since Receive hands those out.
 * [onOpenBackup] runs behind the seed phrase's biometric gate.
 */
@Composable
internal fun AccountBackupBanner(
    viewModel: WalletViewModel = sharedWalletViewModel(),
    onOpenBackup: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val address by viewModel.address.collectAsState()
    val pendingAddresses by viewModel.accountBackupStatus.pendingAddresses.collectAsState()
    val chattingSompi by viewModel.balanceSompi.collectAsState()
    val spendingSompi by viewModel.hotWalletBalance.spendingSompi.collectAsState()
    val biometricEnabled by viewModel.biometricSeedPhraseEnabled.collectAsState()
    val current = address
    if (current == null || current !in pendingAddresses) return
    val hasFunds = chattingSompi > 0L || (spendingSompi ?: 0L) > 0L
    val tint = if (hasFunds) colors.danger else colors.warning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // solid under the tint: the screen scrolls behind this strip
            .background(colors.background)
            .background(tint.copy(alpha = 0.12f))
            .clickable { unlockAccountBackup(context, biometricEnabled, onOpenBackup) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.GppMaybe, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                tr(R.string.kp_backup_your_account_to_prevent_loss_d08a4),
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (hasFunds) tr(R.string.kp_you_now_have_funds_that_aren_0754e)
                else tr(R.string.kp_if_you_lose_this_phone_your_3b972),
                color = if (hasFunds) colors.danger else colors.textSecondary,
                fontSize = 11.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The Wallet tab's card under Send, Receive and Request every six months after a finished backup. */
@Composable
internal fun BackupHygieneCard(onClick: () -> Unit) {
    // opens backup_hygiene, a seed route (SeedRouteGate)
    BackupCardFrame(tint = KaspaTeal, icon = Icons.Filled.VerifiedUser, onClick = { SeedRouteGate.arm(); onClick() }) {
        Text(
            tr(R.string.kp_backup_hygiene_650c9),
            color = LocalAppColors.current.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BackupCardFrame(
    tint: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.1f))
            .border(1.dp, tint.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.weight(1f)) { content() }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = LocalAppColors.current.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The backup screens' frame: the inline title, an optional Back, and Close. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onClose: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    if (onBack != null) {
                        SheetBarButton(stringResource(R.string.back), onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                actions = {
                    SheetBarButton(stringResource(R.string.close), onClick = onClose, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
        content = content,
    )
}

/** iOS `Color(.systemGray6)`, what the words, fields and seed cells sit on. In a dark sheet it is
 *  the sheet's own colour, so they read as plain rows, as on iOS. */
internal val IosSystemGray6: Color
    @Composable get() = if (LocalAppColors.current.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

/** iOS's field here: body text with 12 around it on systemGray6, 10 rounded. The Password keyboard
 *  even while revealed, so no keyboard learns the words or the passphrase. */
@Composable
private fun BackupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    isWrong: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** Check your paper's boxes: an outline, so they look like fields (iOS 4f5dfd2). */
    outlined: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        isWrong -> colors.danger
        !outlined -> Color.Transparent
        focused -> KaspaTeal
        else -> colors.textSecondary.copy(alpha = 0.5f)
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp),
        cursorBrush = SolidColor(KaspaTeal),
        interactionSource = interaction,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosSystemGray6)
            .border(1.5.dp, borderColor, shape),
        decorationBox = { inner ->
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, color = colors.textSecondary, fontSize = 17.sp)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** A passphrase being typed back, with an eye to show or hide it. */
@Composable
private fun BackupPassphraseField(value: String, onValueChange: (String) -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    BackupTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = tr(R.string.kp_passphrase_89950),
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            Icon(
                if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (reveal) tr(R.string.kp_hide_34d8b) else "Show",
                tint = LocalAppColors.current.textSecondary,
                modifier = Modifier.size(22.dp).clickable { reveal = !reveal },
            )
        },
    )
}

private enum class BackupStep { WHY, PREPARE, WORDS, WORD_CHECK, PASSPHRASE, PASSPHRASE_CHECK, TIPS }

private const val WORDS_PER_PAGE = 6
private const val BACKUP_WORDS_TO_CHECK = 4
private const val HYGIENE_WORDS_TO_CHECK = 5

/**
 * Backing up an account's seed phrase and passphrase: why, getting ready, the words a few at a
 * time, proving they were written down, then where to keep them. Words are written by hand: this
 * flow never offers a copy (Settings > Back Up Account > View 12 or 24 Words does). Android can stop a screenshot where
 * iOS can only say so afterwards, so every step showing a word or the passphrase is FLAG_SECURE.
 * Plain [remember] throughout: composition memory only, never saved state (audit AND-031).
 */
@Composable
fun AccountBackupScreen(viewModel: WalletViewModel, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val haptic = rememberHaptics()
    val words = remember { viewModel.getActiveMnemonic()?.split(" ")?.filter { it.isNotBlank() }.orEmpty() }
    val passphrase = remember { viewModel.getActivePassphrase() }

    if (words.isEmpty()) {
        BackHandler(onBack = onClose)
        BackupScaffold(title = tr(R.string.kp_back_up_account_e033f), onClose = onClose) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    tr(R.string.kp_this_account_s_12_or_24_c7182),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    var step by remember { mutableStateOf(BackupStep.WHY) }
    var showExplainer by remember { mutableStateOf(false) }
    var hasPenAndPaper by remember { mutableStateOf(false) }
    var isPrivate by remember { mutableStateOf(false) }
    var noPhotos by remember { mutableStateOf(false) }
    var wordPage by remember { mutableIntStateOf(0) }
    var checkIndexes by remember { mutableStateOf(emptyList<Int>()) }
    var answers by remember { mutableStateOf(emptyMap<Int, String>()) }
    var wrongIndexes by remember { mutableStateOf(emptySet<Int>()) }
    var passphraseAnswer by remember { mutableStateOf("") }
    var passphraseWrong by remember { mutableStateOf(false) }

    val pageCount = maxOf(1, (words.size + WORDS_PER_PAGE - 1) / WORDS_PER_PAGE)
    // This account's steps, for the progress bar: the passphrase's only when it has one.
    val steps = BackupStep.entries.filter {
        passphrase != null || (it != BackupStep.PASSPHRASE && it != BackupStep.PASSPHRASE_CHECK)
    }

    fun startWordCheck() {
        checkIndexes = words.indices.shuffled().take(BACKUP_WORDS_TO_CHECK).sorted()
        answers = emptyMap()
        wrongIndexes = emptySet()
        step = BackupStep.WORD_CHECK
    }

    fun goBack() {
        when (step) {
            BackupStep.WHY -> onClose()
            BackupStep.PREPARE -> step = BackupStep.WHY
            BackupStep.WORDS -> if (wordPage > 0) wordPage-- else step = BackupStep.PREPARE
            BackupStep.WORD_CHECK -> {
                wordPage = pageCount - 1
                step = BackupStep.WORDS
            }
            // Back to a check already passed keeps its words and answers: going back is for
            // looking again, not for being tested on new words (iOS 0799cec).
            BackupStep.PASSPHRASE -> step = BackupStep.WORD_CHECK
            BackupStep.PASSPHRASE_CHECK -> step = BackupStep.PASSPHRASE
            BackupStep.TIPS -> if (passphrase != null) {
                step = BackupStep.PASSPHRASE_CHECK
            } else {
                step = BackupStep.WORD_CHECK
            }
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(step) { focusManager.clearFocus() }

    if (showExplainer) {
        BackHandler { showExplainer = false }
        SeedPhraseExplainerPage(onBack = { showExplainer = false })
        return
    }
    BackHandler(onBack = ::goBack)

    if (step == BackupStep.WORDS || step == BackupStep.WORD_CHECK || step == BackupStep.PASSPHRASE || step == BackupStep.PASSPHRASE_CHECK) {
        SecureWindowWhileShown()
    }

    BackupScaffold(
        title = tr(R.string.kp_back_up_account_e033f),
        onBack = if (step == BackupStep.WHY) null else ::goBack,
        onClose = onClose,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // One step's page: progress on top, the content scrolling, its button always in reach.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LinearProgressIndicator(
                    progress = { (steps.indexOf(step) + 1).toFloat() / steps.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = KaspaTeal,
                    trackColor = colors.surfaceVariant,
                )
                when (step) {
                    BackupStep.WHY -> {
                        BackupHeading(tr(R.string.kp_protect_your_funds_90513))
                        BackupPoint(Icons.Filled.Key, tr(R.string.kp_your_12_or_24_words_are_b8157),
                            tr(R.string.kp_these_secret_words_are_created_just_f772d))
                        BackupPoint(Icons.Filled.MobileOff, tr(R.string.kp_your_phone_can_t_be_your_31328),
                            tr(R.string.kp_if_you_lose_break_or_replace_0cc22))
                        BackupPoint(Icons.Filled.PersonOff, tr(R.string.kp_nobody_can_reset_them_e56e8),
                            tr(R.string.kp_there_is_no_password_reset_kapay_84370))
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showExplainer = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.what_is_a_seed_phrase), color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    BackupStep.PREPARE -> {
                        BackupHeading(tr(R.string.kp_get_ready_70c1b), tr(R.string.kp_your_secret_words_are_next_before_2c363))
                        BackupCheckbox(tr(R.string.kp_i_have_a_pen_and_paper_690e2), hasPenAndPaper) { hasPenAndPaper = it }
                        BackupCheckbox(tr(R.string.kp_nobody_can_see_my_screen_00503), isPrivate) { isPrivate = it }
                        BackupCheckbox(tr(R.string.kp_i_won_t_take_a_photo_2db3f), noPhotos) { noPhotos = it }
                    }
                    BackupStep.WORDS -> {
                        val first = wordPage * WORDS_PER_PAGE
                        val end = minOf(first + WORDS_PER_PAGE, words.size)
                        BackupHeading(tr(R.string.kp_write_down_these_words_b5060), tr(R.string.kp_write_them_on_paper_in_this_9e327))
                        Text(tr(R.string.kp_words_lld_to_lld_of_lld_dda4f, first + 1, end, words.size), color = KaspaTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (index in first until end) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(IosSystemGray6)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Text("${index + 1}.", color = colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
                                    Text(words[index], color = colors.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    BackupStep.WORD_CHECK -> {
                        BackupHeading(tr(R.string.kp_check_your_paper_f59d2), tr(R.string.kp_tap_each_box_and_type_that_a3484))
                        for (index in checkIndexes) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(tr(R.string.kp_word_lld_1751b, index + 1), color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                BackupTextField(
                                    value = answers[index].orEmpty(),
                                    onValueChange = {
                                        answers = answers + (index to it)
                                        wrongIndexes = wrongIndexes - index
                                    },
                                    placeholder = tr(R.string.kp_type_word_lld_here_3e53c, index + 1),
                                    isWrong = index in wrongIndexes,
                                    outlined = true,
                                )
                            }
                        }
                        if (wrongIndexes.isNotEmpty()) {
                            Text(tr(R.string.kp_that_doesn_t_match_check_your_cecc6), color = colors.danger, fontSize = 13.sp)
                            Text(
                                tr(R.string.kp_see_my_words_again_5c21f),
                                color = KaspaTeal,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                    wordPage = 0
                                    step = BackupStep.WORDS
                                },
                            )
                        }
                    }
                    BackupStep.PASSPHRASE -> {
                        BackupHeading(
                            tr(R.string.kp_write_down_your_passphrase_cf4ca),
                            tr(R.string.kp_this_account_also_needs_its_passphrase_3d1f9),
                        )
                        Text(
                            passphrase.orEmpty(),
                            color = colors.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(IosSystemGray6)
                                .padding(14.dp),
                        )
                    }
                    BackupStep.PASSPHRASE_CHECK -> {
                        BackupHeading(tr(R.string.kp_check_your_passphrase_c4b05), tr(R.string.kp_enter_it_from_what_you_wrote_4c114))
                        BackupPassphraseField(passphraseAnswer) {
                            passphraseAnswer = it
                            passphraseWrong = false
                        }
                        if (passphraseWrong) {
                            Text(tr(R.string.kp_that_doesn_t_match_check_your_84b86), color = colors.danger, fontSize = 13.sp)
                        }
                    }
                    BackupStep.TIPS -> {
                        BackupHeading(tr(R.string.kp_keep_it_safe_9a0b3), tr(R.string.kp_your_copy_is_right_now_keep_036e7))
                        BackupPoint(Icons.Filled.Description, tr(R.string.kp_keep_it_on_paper_430c4),
                            tr(R.string.kp_store_it_somewhere_safe_and_private_8204a))
                        BackupPoint(Icons.Filled.CloudOff, tr(R.string.kp_never_keep_it_online_2bd9d),
                            tr(R.string.kp_no_photos_screenshots_cloud_storage_notes_c2ec4))
                        BackupPoint(Icons.Filled.PanTool, tr(R.string.kp_never_share_it_b2b75),
                            tr(R.string.kp_nobody_needs_your_words_kapay_and_05f52))
                        if (passphrase != null) {
                            BackupPoint(Icons.Filled.Splitscreen, tr(R.string.kp_keep_your_passphrase_apart_30774),
                                tr(R.string.kp_store_it_away_from_your_12_6edea))
                        }
                        BackupPoint(Icons.Filled.Settings, tr(R.string.kp_you_can_see_it_again_7c2d7),
                            tr(R.string.kp_your_12_or_24_words_stay_d1f9a))
                    }
                }
            }
            // iOS `.bar`: a shade off the sheet.
            Box(Modifier.fillMaxWidth().background(colors.textPrimary.copy(alpha = 0.04f)).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                when (step) {
                    BackupStep.WHY -> CreateWalletNextButton(tr(R.string.kp_start_backup_cf379), enabled = true) { step = BackupStep.PREPARE }
                    BackupStep.PREPARE -> CreateWalletNextButton(tr(R.string.kp_show_my_12_or_24_words_0d647), enabled = hasPenAndPaper && isPrivate && noPhotos) {
                        wordPage = 0
                        step = BackupStep.WORDS
                    }
                    BackupStep.WORDS -> CreateWalletNextButton(
                        if (wordPage + 1 < pageCount) tr(R.string.kp_next_words_0028c) else tr(R.string.kp_i_wrote_them_down_96c10),
                        enabled = true,
                    ) {
                        if (wordPage + 1 < pageCount) wordPage++ else startWordCheck()
                    }
                    BackupStep.WORD_CHECK -> CreateWalletNextButton(
                        tr(R.string.kp_check_my_words_f4432),
                        enabled = checkIndexes.all { answers[it].orEmpty().isNotBlank() },
                    ) {
                        wrongIndexes = checkIndexes.filter { answers[it].orEmpty().trim().lowercase() != words[it] }.toSet()
                        if (wrongIndexes.isEmpty()) {
                            step = if (passphrase != null) BackupStep.PASSPHRASE else BackupStep.TIPS
                        } else {
                            haptic(IosHaptic.ERROR)
                        }
                    }
                    BackupStep.PASSPHRASE -> CreateWalletNextButton(tr(R.string.kp_i_wrote_it_down_11a8a), enabled = true) {
                        passphraseAnswer = ""
                        passphraseWrong = false
                        step = BackupStep.PASSPHRASE_CHECK
                    }
                    BackupStep.PASSPHRASE_CHECK -> CreateWalletNextButton(tr(R.string.kp_check_my_passphrase_de92a), enabled = passphraseAnswer.isNotEmpty()) {
                        // NFKD on both sides: the saved passphrase is NFKD, so a precomposed "café" matches
                        if (passphrase != null && passphraseMatches(passphraseAnswer, passphrase)) {
                            step = BackupStep.TIPS
                        } else {
                            passphraseWrong = true
                            haptic(IosHaptic.ERROR)
                        }
                    }
                    BackupStep.TIPS -> CreateWalletNextButton(tr(R.string.kp_finish_backup_09c9e), enabled = true) {
                        viewModel.markAccountBackupConfirmed()
                        haptic(IosHaptic.SUCCESS)
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupHeading(title: String, detail: String? = null) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (detail != null) Text(detail, color = colors.textSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun BackupPoint(icon: ImageVector, title: String, detail: String) {
    val colors = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = colors.textSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun BackupCheckbox(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) KaspaTeal else colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
        Text(title, color = colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

/**
 * Checks the written backup still works: five words of the seed phrase, asked in random order on
 * the app's own keyboard, then the passphrase if the account has one, since the words alone
 * wouldn't bring it back. Can't Complete This goes through the backup steps again. Either one
 * starts the six months over; closing leaves the card up.
 */
@Composable
fun BackupHygieneScreen(viewModel: WalletViewModel, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = rememberHaptics()
    val biometricEnabled by viewModel.biometricSeedPhraseEnabled.collectAsState()
    val seedWords = remember { viewModel.getActiveMnemonic()?.split(" ")?.filter { it.isNotBlank() }.orEmpty() }
    val passphrase = remember { viewModel.getActivePassphrase() }
    // Which words are asked (positions in the phrase), in the order they're asked.
    val asked = remember { seedWords.indices.shuffled().take(HYGIENE_WORDS_TO_CHECK) }
    val wordList = remember { viewModel.bip39Words }
    var answers by remember { mutableStateOf(List(asked.size) { "" }) }
    var activeSlot by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf(false) }
    var checkingPassphrase by remember { mutableStateOf(false) }
    var passphraseAnswer by remember { mutableStateOf("") }
    var passed by remember { mutableStateOf(false) }
    var redoingBackup by remember { mutableStateOf(false) }

    if (redoingBackup) {
        AccountBackupScreen(viewModel, onClose)
        return
    }
    BackHandler(onBack = onClose)
    if (!passed) SecureWindowWhileShown()

    fun finish() {
        viewModel.markAccountBackupConfirmed()
        haptic(IosHaptic.SUCCESS)
        passed = true
    }

    @Composable
    fun cantComplete() {
        Text(
            tr(R.string.kp_can_t_complete_this_bb2a4),
            color = KaspaTeal,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { unlockAccountBackup(context, biometricEnabled) { redoingBackup = true } }
                .padding(vertical = 4.dp),
        )
    }

    BackupScaffold(title = tr(R.string.kp_backup_hygiene_650c9), onClose = onClose) { padding ->
        when {
            passed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(56.dp))
                Text(tr(R.string.kp_your_backup_is_good_b254a), color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(tr(R.string.kp_we_ll_check_again_in_6_cdcf2), color = colors.textSecondary, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                CreateWalletNextButton(tr(R.string.kp_done_e9b45), enabled = true, onClick = onClose)
            }
            checkingPassphrase -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(tr(R.string.kp_now_enter_your_passphrase_79377), color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    tr(R.string.kp_your_12_or_24_words_alone_047c3),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                BackupPassphraseField(passphraseAnswer) {
                    passphraseAnswer = it
                    wrong = false
                }
                if (wrong) Text(tr(R.string.kp_that_doesn_t_match_check_your_cce6a), color = colors.danger, fontSize = 13.sp)
                CreateWalletNextButton(tr(R.string.kp_check_my_passphrase_de92a), enabled = passphraseAnswer.isNotEmpty()) {
                    if (passphrase != null && passphraseMatches(passphraseAnswer, passphrase)) {
                        finish()
                    } else {
                        wrong = true
                        haptic(IosHaptic.ERROR)
                    }
                }
                cantComplete()
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    tr(R.string.kp_let_s_ensure_your_account_is_ff3d5),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    tr(R.string.kp_enter_these_lld_of_your_12_30ba6, asked.size),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                SeedPhraseKeyboard(
                    wordCount = asked.size,
                    words = answers,
                    activeSlot = activeSlot,
                    wordList = wordList,
                    modifier = Modifier.weight(1f),
                    slotNumbers = asked.map { it + 1 },
                    onWordsChange = {
                        answers = it
                        wrong = false
                    },
                    onActiveSlotChange = { activeSlot = it },
                )
                if (wrong) Text(tr(R.string.kp_some_of_those_words_don_t_96de4), color = colors.danger, fontSize = 13.sp)
                CreateWalletNextButton(
                    tr(R.string.kp_check_my_words_f4432),
                    enabled = asked.isNotEmpty() && answers.all { it.lowercase() in wordList },
                ) {
                    if (asked.indices.all { seedWords[asked[it]] == answers[it].lowercase() }) {
                        wrong = false
                        if (passphrase != null) checkingPassphrase = true else finish()
                    } else {
                        wrong = true
                        haptic(IosHaptic.ERROR)
                    }
                }
                cantComplete()
            }
        }
    }
}
