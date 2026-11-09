package com.kachat.app.ui.screens

import com.kachat.app.util.tr
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.services.NfcCard
import com.kachat.app.services.NfcCardBackupFormat
import com.kachat.app.services.NfcCardBackupPayload
import com.kachat.app.services.NfcCardConnection
import com.kachat.app.services.NfcCardError
import com.kachat.app.services.NfcCardStatus
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// NFC card backup and restore (iOS 4f5dfd2 NFCCardBackupView, NFCCardRestoreView). The text is
// English only for now, as on iOS. Android has no system sheet for a tap, so NfcTapSheet stands in
// for iOS's reader session.

/** What a screen needs to run a tap: a sheet saying to hold the card, Cancel, and the one job. */
private class NfcTapState(private val activity: Activity?) {
    var message by mutableStateOf<String?>(null)
    var job: Job? = null

    /** Runs [work] against one card with the sheet up; the sheet goes when it ends. */
    suspend fun <T> run(work: suspend (NfcCardConnection) -> T): T {
        val host = activity ?: throw NfcCardError.CardFailed("no screen")
        message = NfcCard.TAP_PROMPT
        try {
            return NfcCard.run(host, work)
        } finally {
            message = null
        }
    }

    fun cancel() {
        job?.cancel()
    }
}

@Composable
private fun rememberNfcTapState(): NfcTapState {
    val activity = LocalContext.current as? Activity
    return remember(activity) { NfcTapState(activity) }
}

@Composable
private fun NfcTapSheet(state: NfcTapState) {
    val message = state.message ?: return
    ActionSheetContainer(
        title = tr(R.string.kp_nfc_card_43502),
        subtitle = null,
        onDismiss = { state.cancel() },
        headerIcon = {
            Icon(Icons.Default.Contactless, contentDescription = null, tint = KaspaTeal, modifier = Modifier.padding(bottom = 8.dp).size(36.dp))
        },
    ) {
        Text(
            message,
            color = LocalAppColors.current.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ActionSheetRow(
            icon = Icons.Default.Close,
            title = stringResource(R.string.cancel),
            subtitle = "Stop looking for a card.",
            onClick = { state.cancel() },
        )
    }
}

/** A PIN or passphrase entry, shown as typed so a mistake is caught before it costs a PIN try. The
 *  Password keyboards, so it's never saved or suggested. A PIN field only takes digits, up to the
 *  PIN's length. */
@Composable
private fun NfcCardSecretField(title: String, value: String, onValueChange: (String) -> Unit, isNumeric: Boolean) {
    val colors = LocalAppColors.current
    BasicTextField(
        value = value,
        onValueChange = { typed ->
            onValueChange(if (isNumeric) typed.filter { it in '0'..'9' }.take(NfcCard.PIN_LENGTH) else typed)
        },
        singleLine = true,
        textStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp),
        cursorBrush = SolidColor(KaspaTeal),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) KeyboardType.NumberPassword else KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(IosSystemGray6),
        decorationBox = { inner ->
            Box(Modifier.padding(16.dp)) {
                if (value.isEmpty()) Text(title, color = colors.textSecondary, fontSize = 17.sp)
                inner()
            }
        },
    )
}

@Composable
private fun NfcCardNote(icon: ImageVector, tint: Color, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.1f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text, color = LocalAppColors.current.textSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NfcCardHeading(title: String, detail: String? = null) {
    val colors = LocalAppColors.current
    Text(title, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (detail != null) Text(detail, color = colors.textSecondary, fontSize = 17.sp)
}

@Composable
private fun NfcCardAlert(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(tr(R.string.kp_nfc_card_43502), color = colors.textPrimary) },
        text = { Text(message, color = colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun NfcCardTextButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        title,
        color = if (enabled) KaspaTeal else LocalAppColors.current.textSecondary,
        fontSize = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

private enum class NfcBackupStep { INTRO, DETAILS, ERASE, SAVED, ERASED }

/**
 * Settings > Back Up to NFC Card, behind the seed phrase's biometric gate and only offered for an
 * account with a passphrase. Two taps: the first reads the card to learn whether it needs a new PIN
 * or already holds a backup, the second saves. The passphrase is never written to the card. It
 * never clears the "not backed up" strip at the top of each tab: the written seed phrase stays the
 * main copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcCardBackupScreen(viewModel: WalletViewModel, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val tap = rememberNfcTapState()
    var step by remember { mutableStateOf(NfcBackupStep.INTRO) }
    var card by remember { mutableStateOf<NfcCardStatus?>(null) }
    var pin by remember { mutableStateOf("") }
    // A new PIN, typed again: a typo here would lock the backup behind a PIN nobody knows (iOS 1652437).
    var confirmPin by remember { mutableStateOf("") }
    var pinCleared by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEraseConfirmation by remember { mutableStateOf(false) }
    var showReplaceConfirmation by remember { mutableStateOf(false) }
    val pinValid = NfcCard.isValidPin(pin)
    // The PIN can go to the card: a valid one, typed twice when it's a new one.
    val canSave = card?.let { pinValid && (it.pinSet || pin == confirmPin) } == true

    fun start(block: suspend () -> Unit) {
        tap.job = scope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NfcCardError) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    fun scanCard() = start {
        val status = tap.run { connection -> connection.select() }
        if (status.pinBlocked) throw NfcCardError.PinBlocked
        card = status
        step = NfcBackupStep.DETAILS
    }

    fun backToStart() {
        pin = ""
        confirmPin = ""
        card = null
        step = NfcBackupStep.INTRO
    }

    fun save() {
        val scanned = card ?: return
        if (!canSave || busy) return
        val enteredPin = pin
        start {
            try {
                // the payload's address derivation (PBKDF2) runs off the main thread too
                val backup = withContext(Dispatchers.Default) {
                    NfcCardBackupFormat.seal(viewModel.nfcCardBackupPayload(), enteredPin)
                }
                if (backup.size > scanned.maxBackup) throw NfcCardError.BackupTooLarge
                tap.message = "Saving. Keep the card still."
                tap.run { connection ->
                    val current = connection.select()
                    if (current.pinBlocked) throw NfcCardError.PinBlocked
                    // The confirmations were for the card scanned first. An interrupted save can
                    // legitimately leave that card with its new PIN and no backup yet.
                    // A PIN where the scanned card had none is another card too (this save sets
                    // the PIN it then finds, and records that below): stopped before verifyPin,
                    // which would cost that card a try with a PIN that was never its own (iOS 1652437).
                    if ((current.hasBackup && !scanned.hasBackup) || (scanned.pinSet && !current.pinSet) ||
                        (!scanned.pinSet && current.pinSet)
                    ) {
                        throw NfcCardError.CardChanged
                    }
                    if (!current.pinSet) {
                        connection.setPin(enteredPin)
                        // The card has this PIN now, whatever happens next: a retry after an
                        // interrupted save finds it set and asks for it as the card's PIN.
                        card = scanned.copy(pinSet = true, pinBlocked = false, triesRemaining = current.triesRemaining)
                        confirmPin = ""
                    }
                    connection.verifyPin(enteredPin)
                    connection.writeBackup(backup)
                    if (!connection.readBackup().contentEquals(backup)) throw NfcCardError.SaveNotConfirmed
                }
                // What's on the card has to open with this PIN, not just match what was sent.
                withContext(Dispatchers.Default) { NfcCardBackupFormat.open(backup, enteredPin) }
                pin = ""
                confirmPin = ""
                step = NfcBackupStep.SAVED
            } catch (e: NfcCardError.WrongPin) {
                pin = ""
                confirmPin = ""
                card = scanned.copy(pinSet = card?.pinSet ?: scanned.pinSet, pinBlocked = false, triesRemaining = e.triesRemaining)
                throw e
            } catch (e: NfcCardError) {
                if (e === NfcCardError.PinBlocked || e === NfcCardError.CardChanged) backToStart()
                throw e
            }
        }
    }

    fun eraseBackup() {
        if (!pinValid) return
        val enteredPin = pin
        start {
            try {
                pinCleared = tap.run { connection ->
                    val current = connection.select()
                    if (current.pinBlocked) throw NfcCardError.PinBlocked
                    if (!current.pinSet) throw NfcCardError.NoPin
                    if (!current.hasBackup) throw NfcCardError.NoBackup
                    connection.verifyPin(enteredPin)
                    connection.wipe()
                    // Cards loaded with older software keep their PIN. The erase has already
                    // worked, so a card pulled away here only costs the wording.
                    runCatching { connection.select() }.getOrNull()?.pinSet == false
                }
                pin = ""
                step = NfcBackupStep.ERASED
            } catch (e: Exception) {
                pin = ""
                throw e
            }
        }
    }

    BackHandler(enabled = !busy) { onClose() }
    if (step == NfcBackupStep.DETAILS || step == NfcBackupStep.ERASE) SecureWindowWhileShown()
    DisposableEffect(Unit) { onDispose { pin = ""; confirmPin = "" } }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr(R.string.kp_nfc_card_backup_2e2bb), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    if (step != NfcBackupStep.SAVED && step != NfcBackupStep.ERASED) {
                        SheetBarButton(stringResource(R.string.cancel), onClick = { if (!busy) onClose() }, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (step) {
                NfcBackupStep.INTRO -> {
                    NfcCardHeading(tr(R.string.kp_back_up_to_an_nfc_card_54860), tr(R.string.kp_saves_this_account_s_12_or_a4fe2))
                    NfcCardNote(Icons.Default.Key, KaspaTeal, tr(R.string.kp_your_passphrase_is_never_saved_to_8d788))
                    NfcCardNote(Icons.Default.Warning, colors.warning, tr(R.string.kp_this_isn_t_cold_storage_the_fa643))
                    NfcCardNote(Icons.Default.SdCard, KaspaTeal, tr(R.string.kp_your_card_needs_the_kachat_nfc_f6761))
                    CreateWalletNextButton(tr(R.string.kp_scan_card_75864), enabled = !busy, busy = busy) { scanCard() }
                    NfcCardTextButton(tr(R.string.kp_erase_a_backup_on_a_card_40415), enabled = !busy) { step = NfcBackupStep.ERASE }
                }
                NfcBackupStep.DETAILS -> card?.let { scanned ->
                    if (scanned.hasBackup) {
                        NfcCardNote(Icons.Default.Warning, colors.warning, tr(R.string.kp_this_card_already_holds_a_backup_978f8))
                    }
                    if (scanned.pinSet) {
                        NfcCardSecretField(tr(R.string.kp_card_pin_db690), pin, { pin = it }, isNumeric = true)
                        if (scanned.triesRemaining < 5) {
                            Text(tr(R.string.kp_lld_pin_tries_left_on_this_b4833, scanned.triesRemaining), color = colors.warning, fontSize = 13.sp)
                        }
                    } else {
                        NfcCardSecretField(tr(R.string.kp_new_6_digit_pin_8f8c9), pin, { pin = it }, isNumeric = true)
                        NfcCardSecretField(tr(R.string.kp_re_enter_pin_f202a), confirmPin, { confirmPin = it }, isNumeric = true)
                        if (NfcCard.isValidPin(confirmPin) && pin != confirmPin) {
                            Text(tr(R.string.kp_the_pins_don_t_match_c33cf), color = colors.danger, fontSize = 13.sp)
                        }
                        Text(tr(R.string.kp_pick_one_you_ll_remember_after_c9945), color = colors.textSecondary, fontSize = 13.sp)
                    }
                    CreateWalletNextButton(
                        if (scanned.hasBackup) tr(R.string.kp_replace_backup_ee652) else tr(R.string.kp_save_to_card_08b74),
                        enabled = canSave && !busy,
                        busy = busy,
                    ) {
                        if (scanned.hasBackup) showReplaceConfirmation = true else save()
                    }
                }
                NfcBackupStep.ERASE -> {
                    NfcCardHeading(tr(R.string.kp_erase_a_backup_fd130), tr(R.string.kp_enter_the_card_s_pin_then_d3078))
                    NfcCardSecretField(tr(R.string.kp_card_pin_db690), pin, { pin = it }, isNumeric = true)
                    CreateWalletNextButton(tr(R.string.kp_erase_backup_06550), enabled = pinValid && !busy, busy = busy) { showEraseConfirmation = true }
                    NfcCardTextButton(stringResource(R.string.back), enabled = !busy) {
                        pin = ""
                        step = NfcBackupStep.INTRO
                    }
                }
                NfcBackupStep.SAVED -> NfcCardFinished(
                    title = tr(R.string.kp_backup_saved_66cbc),
                    detail = tr(R.string.kp_it_was_read_back_from_the_40719),
                    onDone = onClose,
                )
                NfcBackupStep.ERASED -> NfcCardFinished(
                    title = tr(R.string.kp_backup_erased_b8d59),
                    detail = if (pinCleared) tr(R.string.kp_the_backup_and_pin_are_gone_e12ff)
                    else tr(R.string.kp_the_backup_is_gone_from_this_12881),
                    onDone = onClose,
                )
            }
        }
    }

    NfcTapSheet(tap)
    NfcCardAlert(errorMessage) { errorMessage = null }
    if (showEraseConfirmation) {
        ConfirmActionSheet(
            title = tr(R.string.kp_erase_the_backup_on_this_card_d056f),
            confirmTitle = tr(R.string.kp_erase_backup_06550),
            confirmSubtitle = tr(R.string.kp_this_can_t_be_undone_fe63a),
            confirmIcon = Icons.Default.Delete,
            onConfirm = { eraseBackup() },
            onDismiss = { showEraseConfirmation = false },
        )
    }
    if (showReplaceConfirmation) {
        ConfirmActionSheet(
            title = tr(R.string.kp_replace_the_backup_on_this_card_bbd6b),
            confirmTitle = tr(R.string.kp_replace_backup_ee652),
            confirmSubtitle = tr(R.string.kp_the_old_backup_is_removed_once_333be),
            confirmIcon = Icons.Default.Contactless,
            onConfirm = { save() },
            onDismiss = { showReplaceConfirmation = false },
        )
    }
}

@Composable
private fun NfcCardFinished(title: String, detail: String, onDone: () -> Unit) {
    val colors = LocalAppColors.current
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = colors.success,
        modifier = Modifier.fillMaxWidth().wrapContentWidth().size(40.dp),
    )
    Text(
        title,
        color = colors.textPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(detail, color = colors.textSecondary, fontSize = 17.sp)
    CreateWalletNextButton(stringResource(R.string.done), enabled = true, onClick = onDone)
}

/**
 * Import Account > Restore from NFC Card: the PIN and a tap read the backup, then the passphrase,
 * which was never on the card, opens the account. A wrong passphrase needs no second tap, since the
 * backup is in memory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcCardRestoreScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val tap = rememberNfcTapState()
    var pin by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf<NfcCardBackupPayload?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val importState by viewModel.importWalletState.collectAsState()
    val importing = importState.status == WalletViewModel.ImportWalletStatus.IMPORTING
    val pinValid = NfcCard.isValidPin(pin)

    fun readCard() {
        if (!pinValid) return
        val enteredPin = pin
        tap.job = scope.launch {
            busy = true
            try {
                val backup = tap.run { connection ->
                    tap.message = "Reading. Keep the card still."
                    val current = connection.select()
                    if (current.pinBlocked) throw NfcCardError.PinBlocked
                    if (!current.pinSet) throw NfcCardError.NoPin
                    if (!current.hasBackup) throw NfcCardError.NoBackup
                    connection.verifyPin(enteredPin)
                    connection.readBackup()
                }
                payload = withContext(Dispatchers.Default) { NfcCardBackupFormat.open(backup, enteredPin) }
                pin = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pin = ""
                errorMessage = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    fun restore() {
        val read = payload ?: return
        val entered = passphrase
        scope.launch {
            busy = true
            try {
                viewModel.restoreFromNfcCard(read, entered)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // NfcCardError says what's wrong; anything else is reported, not a crash
                errorMessage = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    BackHandler(enabled = busy || importing) {}
    SecureWindowWhileShown()
    DisposableEffect(Unit) {
        onDispose {
            pin = ""
            passphrase = ""
            payload = null
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.import_account), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = { SheetBarBackButton({ if (!busy && !importing) onBack() }, Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val read = payload
            if (read == null) {
                NfcCardHeading(tr(R.string.kp_restore_from_an_nfc_card_ff14a), tr(R.string.kp_enter_the_card_s_pin_then_9fce9))
                NfcCardSecretField(tr(R.string.kp_card_pin_db690), pin, { pin = it }, isNumeric = true)
                NfcCardNote(Icons.Default.Warning, colors.warning, tr(R.string.kp_after_5_wrong_tries_the_pin_8649d))
                CreateWalletNextButton(tr(R.string.kp_scan_card_75864), enabled = pinValid && !busy, busy = busy) { readCard() }
            } else {
                NfcCardHeading(tr(R.string.kp_enter_your_passphrase_ea44e), tr(R.string.kp_the_backup_was_read_from_your_94f4f))
                NfcCardSecretField(tr(R.string.kp_passphrase_89950), passphrase, { passphrase = it }, isNumeric = false)
                val family = read.sourceFamily
                if (passphrase.isNotEmpty() && family != null) {
                    PassphraseAddressPreview(
                        passphrase = passphrase,
                        previewAddress = { viewModel.previewAddress(read.words, it, family, read.chattingAddressIndex) },
                        previewBalance = { viewModel.previewAddressBalanceSompi(it) },
                        showsFundsHint = true,
                    )
                }
                CreateWalletNextButton(
                    tr(R.string.kp_restore_account_d7da8),
                    enabled = passphrase.isNotEmpty() && !busy && !importing,
                    busy = busy || importing,
                ) { restore() }
            }
        }
    }

    NfcTapSheet(tap)
    NfcCardAlert(errorMessage) { errorMessage = null }
    NfcCardAlert(importState.errorMessage.takeIf { importState.status == WalletViewModel.ImportWalletStatus.FAILED }) {
        viewModel.resetImportWalletState()
    }
}
