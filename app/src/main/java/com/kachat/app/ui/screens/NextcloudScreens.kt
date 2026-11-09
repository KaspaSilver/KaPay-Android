package com.kachat.app.ui.screens

import com.kachat.app.util.tr
import com.kachat.app.util.UserFacingError
import com.kachat.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kachat.app.services.NextcloudFile
import com.kachat.app.services.NextcloudService
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import com.kachat.app.util.is24HourClock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.DateFormat
import java.util.Date

/** Reaches the app's Nextcloud services from the Settings > Storage > Nextcloud page. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NextcloudSettingsDeps {
    fun nextcloud(): NextcloudService
    fun nextcloudBackup(): com.kachat.app.services.NextcloudSyncService
}

/**
 * The sections of the Settings > Storage > Nextcloud page ([NextcloudStorageScreen] supplies the
 * Form page around them) - iOS's NextcloudSettingsView (953b255), section for section. The wallet
 * backs up Contacts and the Portfolio, nothing else.
 *
 * Testnet: one row saying Nextcloud is off.
 *
 * Not connected: "Server" (address, username and app password as plain rows in one card, the
 * https warning between them), an error section when connecting failed, and Connect in a section of
 * its own.
 *
 * Connected: Connected Account (server, username); what to do when Nextcloud refused the login;
 * Contact Backup (Automatic Sync, Last synced, Backup Folder, Back Up Contacts Now, the file's last
 * upload - or "No backup on this server yet."); Portfolio Backup (Back Up Portfolio Now, the newest
 * file's last upload); the outcome of a backup started here; Disconnect, confirmed by an alert.
 */
@Composable
fun NextcloudSettingsSection() {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val deps = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, NextcloudSettingsDeps::class.java)
    }
    val service = remember { deps.nextcloud() }
    val backup = remember { deps.nextcloudBackup() }
    val account by service.account.collectAsState()
    val autoBackupEnabled by service.autoBackupEnabled.collectAsState()
    val lastAutoSyncMs by service.lastAutoSyncMs.collectAsState()
    val needsReconnect by service.needsReconnect.collectAsState()
    val contactsSyncError by service.contactsSyncError.collectAsState()
    val scope = rememberCoroutineScope()
    val is24Hour = is24HourClock()

    // iOS keeps all of this in the view's own @State: an outcome shows only for what was started
    // on this visit. The work itself runs in NextcloudSyncService's scope, so leaving the page
    // never cuts a backup off half way.
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var backupInfo by remember { mutableStateOf(NextcloudService.BackupInfo()) }
    var isBackingUp by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupErrorMessage by remember { mutableStateOf<String?>(null) }
    var showBackupFolderPicker by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(account?.server) {
        if (account != null) backupInfo = service.fetchWalletBackupInfo()
    }

    // Merge-on-upload: both read the server's copy first, so a manual backup can never undo another
    // device's newer change, and it brings those changes in here (iOS 953b255).
    fun backUp(success: String, work: () -> kotlinx.coroutines.Deferred<Unit>) {
        if (isBackingUp) return
        isBackingUp = true
        backupStatusMessage = null
        backupErrorMessage = null
        scope.launch {
            try {
                work().await()
                backupInfo = service.fetchWalletBackupInfo()
                backupStatusMessage = success
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                backupErrorMessage = UserFacingError.message(e, "Backup failed")
            } finally {
                isBackingUp = false
            }
        }
    }

    // Testnet: Nextcloud is off until mainnet is back (iOS af8ec68).
    if (service.isOffForTestnet) {
        SettingsFormSection(title = null) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.nextcloud_off_on_testnet), color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val connected = account
    if (connected == null) {
        var server by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var appPassword by remember { mutableStateOf("") }
        val canConnect = server.isNotBlank() && username.isNotBlank() && appPassword.isNotBlank() && !isConnecting
        // https only. Bare hostnames are fine (NextcloudService.normalizeServer defaults them to
        // https, and refuses http:// as a backstop); an explicit http:// is flagged inline here
        // and refused again by Connect.
        val serverIsPlainHttp = server.trim().lowercase().startsWith("http://")
        val useHttps = stringResource(R.string.nc_use_https)

        SettingsFormSection(title = stringResource(R.string.nc_server)) {
            NcFormField(
                value = server,
                onValueChange = { server = it },
                placeholder = stringResource(R.string.nc_server_placeholder),
                keyboardType = KeyboardType.Uri,
            )
            if (serverIsPlainHttp) {
                SettingsDivider()
                NcCaptionRow(useHttps, colors.danger)
            }
            SettingsDivider()
            NcFormField(
                value = username,
                onValueChange = { username = it },
                placeholder = stringResource(R.string.nc_username),
            )
            SettingsDivider()
            NcFormField(
                value = appPassword,
                onValueChange = { appPassword = it },
                placeholder = stringResource(R.string.nc_app_password),
                secure = true,
            )
        }

        errorMessage?.let { message ->
            SettingsFormSection(title = null) {
                NcCaptionRow(message, colors.danger)
            }
        }

        SettingsFormSection(title = null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canConnect) {
                        // Refuse cleartext servers outright instead of attempting a connection.
                        if (server.trim().lowercase().startsWith("http://")) {
                            errorMessage = useHttps
                            return@clickable
                        }
                        isConnecting = true
                        errorMessage = null
                        scope.launch {
                            try {
                                backup.connectAsync(server, username, appPassword).await()
                                appPassword = ""
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errorMessage = UserFacingError.message(e, "Could not connect")
                            } finally {
                                isConnecting = false
                            }
                        }
                    }
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting) {
                    com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        stringResource(R.string.nc_connect),
                        color = if (canConnect) KaspaTeal else ncTertiaryLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    } else {
        val host = connected.server.toHttpUrlOrNull()?.host ?: connected.server

        SettingsFormSection(title = stringResource(R.string.nc_connected_account)) {
            NcValueRow(stringResource(R.string.nc_server), host, truncation = NcTruncation.MIDDLE)
            SettingsDivider()
            NcValueRow(stringResource(R.string.nc_username), connected.username)
        }

        if (needsReconnect) {
            SettingsFormSection(title = null) {
                NcCaptionRow(tr(R.string.kp_nextcloud_refused_this_login_disconnect_then_ad7fe), colors.danger)
            }
        }

        // Why the contacts last failed to sync (another account's file in the Backup Folder,
        // say): it stays here until a sync succeeds or the folder changes.
        contactsSyncError?.takeIf { !needsReconnect }?.let { message ->
            SettingsFormSection(title = null) {
                NcCaptionRow(message, colors.danger)
            }
        }

        SettingsFormSection(
            title = tr(R.string.kp_contact_backup_31fab),
            footer = tr(R.string.kp_your_contacts_are_backed_up_here_78c34),
        ) {
            SettingsSwitchItem(stringResource(R.string.nc_automatic_sync), autoBackupEnabled) { enabled ->
                backup.setAutoBackupEnabled(enabled)
            }
            lastAutoSyncMs?.let { lastSynced ->
                SettingsDivider()
                NcValueRow(
                    stringResource(R.string.nc_last_synced),
                    com.kachat.app.util.IosDateStyle.mediumDateShortTime(lastSynced, is24Hour),
                    caption = true,
                )
            }
            SettingsDivider()
            val backupFolder = connected.backupFolder
            NcValueRow(
                label = stringResource(R.string.nc_backup_folder),
                value = when {
                    backupFolder == null -> stringResource(R.string.nc_default_folder, NextcloudService.BACKUP_FOLDER_NAME)
                    backupFolder.isEmpty() -> stringResource(R.string.pnc_all_files)
                    else -> backupFolder
                },
                truncation = NcTruncation.HEAD,
                onClick = { showBackupFolderPicker = true },
            )
            SettingsDivider()
            NcLabelButtonRow(
                label = tr(R.string.kp_back_up_contacts_now_2a9bd),
                icon = Icons.Outlined.UploadFile,
                enabled = !isBackingUp,
                busy = isBackingUp,
            ) {
                backUp(tr(R.string.kp_contacts_backed_up_0904f)) { backup.backUpContactsNowAsync() }
            }
            NcLastBackupRow(backupInfo.contacts, is24Hour)
        }

        SettingsFormSection(
            title = tr(R.string.kp_portfolio_backup_fe098),
            // iOS 1652437's new wording; English until strings_kapay.xml carries its translations
            footer = tr(R.string.kp_each_portfolio_has_its_own_file_934c3),
        ) {
            NcLabelButtonRow(
                label = tr(R.string.kp_back_up_portfolio_now_210ae),
                icon = Icons.Outlined.UploadFile,
                enabled = !isBackingUp,
                busy = isBackingUp,
            ) {
                backUp(tr(R.string.kp_portfolio_backed_up_9a773)) { backup.backUpPortfolioNowAsync() }
            }
            NcLastBackupRow(backupInfo.portfolio, is24Hour)
        }

        if (backupStatusMessage != null || backupErrorMessage != null) {
            SettingsFormSection(title = null) {
                backupStatusMessage?.let { message -> NcCaptionRow(message, colors.success) }
                backupErrorMessage?.let { message -> NcCaptionRow(message, colors.danger) }
            }
        }

        SettingsFormSection(title = null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDisconnectConfirm = true }
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(stringResource(R.string.nc_disconnect), color = colors.danger, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (showBackupFolderPicker) {
        NextcloudFolderSelectDialog(
            service = service,
            onDismiss = { showBackupFolderPicker = false },
            onSelect = { path ->
                showBackupFolderPicker = false
                service.setBackupFolder(path)
                // The contacts file lives per folder: re-check what exists at the new destination.
                scope.launch { backupInfo = service.fetchWalletBackupInfo() }
            },
        )
    }
    if (showDisconnectConfirm) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.nc_disconnect_title), color = colors.textPrimary) },
            text = {
                Text(
                    tr(R.string.kp_this_revokes_the_app_password_on_65220),
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    service.disconnect()
                    backupInfo = NextcloudService.BackupInfo()
                    backupStatusMessage = null
                    backupErrorMessage = null
                }) {
                    Text(stringResource(R.string.nc_disconnect), color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = colors.textSecondary)
                }
            }
        )
    }
}

/** One backup file's last upload, or that it isn't on the server yet (iOS `lastBackupRow`): the
 *  date and size in `.caption`, secondary. A file whose date the server omitted shows no row. */
@Composable
private fun NcLastBackupRow(file: NextcloudFile?, is24Hour: Boolean) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val modified = file?.modifiedMs
    if (file != null && modified != null) {
        SettingsDivider(inset = IosRowTextInset)
        NcValueRow(
            stringResource(R.string.nc_last_backup),
            com.kachat.app.util.IosDateStyle.mediumDateShortTime(modified, is24Hour) +
                (file.size?.let { " · " + com.kachat.app.util.IosByteCount.format(context, it) } ?: ""),
            caption = true,
        )
    } else if (file == null) {
        SettingsDivider(inset = IosRowTextInset)
        NcCaptionRow(stringResource(R.string.nc_no_backup_yet), colors.textSecondary)
    }
}

/** iOS's tertiaryLabel, translucent as iOS draws it: a Form field's placeholder and a disabled
 *  button's title. */
@Composable
private fun ncTertiaryLabel(): Color =
    if (LocalAppColors.current.isDark) Color(0x4DEBEBF5) else Color(0x4D3C3C43)

/**
 * A plain Form text field (iOS's TextField / RevealableSecureField in a Form): no box of its own,
 * the row is the field - 17pt text, the placeholder in tertiaryLabel, no capitalisation or
 * autocorrection. [secure] hides the text behind the eye toggle.
 */
@Composable
private fun NcFormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    secure: Boolean = false,
) {
    val colors = LocalAppColors.current
    var revealed by remember { mutableStateOf(false) }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = if (secure) 6.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(KaspaTeal),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = if (secure) KeyboardType.Password else keyboardType,
            ),
            visualTransformation = if (secure && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle, color = ncTertiaryLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
            }
        )
        if (secure) {
            IconButton(onClick = { revealed = !revealed }, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(if (revealed) R.string.hide_password else R.string.show_password),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private enum class NcTruncation { NONE, MIDDLE, HEAD }

/**
 * A Form row with its title at the left and a value at the right (iOS's HStack { Text; Spacer;
 * Text.foregroundColor(.secondary) }). [caption] writes the value in `.caption` (12pt);
 * [truncation] keeps it on one line, giving way in the middle or at the start. With [onClick]
 * the row is a button.
 */
@Composable
private fun NcValueRow(
    label: String,
    value: String,
    caption: Boolean = false,
    truncation: NcTruncation = NcTruncation.NONE,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val valueStyle = if (caption) {
        MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp, lineHeight = 16.sp)
    } else {
        MaterialTheme.typography.bodyLarge
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        val valueModifier = Modifier.weight(1f)
        when (truncation) {
            NcTruncation.MIDDLE -> MiddleEllipsisText(
                value,
                modifier = valueModifier,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
                style = valueStyle,
            )
            NcTruncation.HEAD -> NcHeadEllipsisText(value, valueModifier, colors.textSecondary, valueStyle)
            NcTruncation.NONE -> Text(value, modifier = valueModifier, color = colors.textSecondary, style = valueStyle, textAlign = TextAlign.End)
        }
    }
}

/** One line that gives way at its START when it does not fit - iOS's `.truncationMode(.head)`,
 *  so a folder path keeps the folder's own name. */
@Composable
private fun NcHeadEllipsisText(text: String, modifier: Modifier, color: Color, style: TextStyle) {
    val measurer = rememberTextMeasurer()
    val merged = style.merge(TextStyle(color = color, textAlign = TextAlign.End))
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val bounded = constraints.hasBoundedWidth
        val shown = remember(text, maxWidth, bounded, merged) {
            val fits = { candidate: String ->
                !measurer.measure(
                    AnnotatedString(candidate),
                    style = merged,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxWidth),
                ).hasVisualOverflow
            }
            if (!bounded || fits(text)) {
                text
            } else {
                var low = 0
                var high = text.length - 1
                var best = "…"
                while (low <= high) {
                    val mid = (low + high) ushr 1
                    val candidate = "…" + text.takeLast(mid)
                    if (fits(candidate)) {
                        best = candidate
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                best
            }
        }
        Text(shown, style = merged, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.fillMaxWidth())
    }
}

/** A row that is only a line of `.caption` text (12pt) in [color] - notes, errors, outcomes. */
@Composable
private fun NcCaptionRow(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = color, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/**
 * A Form button drawn as iOS's Label (symbol, then title) in the accent - tertiaryLabel when
 * disabled - with a spinner at the trailing edge while [busy].
 */
@Composable
private fun NcLabelButtonRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) KaspaTeal else ncTertiaryLabel()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (busy) com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(20.dp))
    }
}

/**
 * Settings > Storage > Nextcloud > Backup Folder (iOS 953b255, NextcloudFolderSelectView): browses
 * the connected account's folders from All Files and hands back the one chosen ("" = the files root).
 */
@Composable
private fun NextcloudFolderSelectDialog(
    service: NextcloudService,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    var pathStack by remember { mutableStateOf(listOf("")) }
    val currentPath = pathStack.last()
    var folders by remember { mutableStateOf<List<NextcloudFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        isLoading = true
        errorMessage = null
        folders = emptyList()
        try {
            // Newest-first comes from listFolder; this only drops the non-folder entries.
            folders = service.listFolder(currentPath).filter { it.isDirectory }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = UserFacingError.message(e, "Could not load this folder.")
        }
        isLoading = false
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                SheetBarButton(
                    stringResource(if (pathStack.size > 1) R.string.back else R.string.cancel),
                    onClick = { if (pathStack.size > 1) pathStack = pathStack.dropLast(1) else onDismiss() },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    currentPath.substringAfterLast('/').ifEmpty { stringResource(R.string.pnc_all_files) },
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 88.dp),
                )
            }
            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                SettingsFormSection(title = null, first = true) {
                    NcLabelButtonRow(
                        label = if (currentPath.isEmpty()) tr(R.string.kp_use_all_files_bab36) else tr(R.string.kp_use_this_folder_3bfd5),
                        icon = Icons.Outlined.CheckCircle,
                        enabled = true,
                        busy = false,
                    ) {
                        onSelect(currentPath)
                    }
                }
                val message = errorMessage
                if (folders.isNotEmpty() || message != null || !isLoading) {
                    SettingsFormSection(title = null) {
                        folders.forEachIndexed { index, folder ->
                            if (index > 0) SettingsDivider(inset = IosRowTextInset)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pathStack = pathStack + folder.path.trim('/') }
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    folder.name,
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
                            }
                        }
                        if (message != null) NcCaptionRow(message, colors.danger)
                        if (!isLoading && folders.isEmpty() && message == null) {
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(tr(R.string.kp_no_subfolders_fd56f), color = colors.textSecondary, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pick one file from the connected Nextcloud (iOS aa5d783, NextcloudFileSelectView): browsing
 * starts in the KaChat folder, with All Files for the root; only [allowedExtensions] can be picked
 * and other files show dimmed. A KaChat folder that doesn't exist yet reads as empty rather than an
 * error.
 */
@Composable
fun NextcloudFileSelectDialog(
    service: NextcloudService,
    allowedExtensions: Set<String>,
    onDismiss: () -> Unit,
    onPick: (NextcloudFile) -> Unit,
) {
    val colors = LocalAppColors.current
    val kachatFolder = NextcloudService.BACKUP_FOLDER_NAME
    var pathStack by remember { mutableStateOf(listOf(kachatFolder)) }
    val currentPath = pathStack.last()
    var entries by remember { mutableStateOf<List<NextcloudFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        isLoading = true
        errorMessage = null
        entries = try {
            service.listFolder(currentPath)
        } catch (e: Exception) {
            // The KaChat folder is only made on the first export or backup.
            if (currentPath == kachatFolder && e.message?.contains("404") == true) emptyList()
            else {
                errorMessage = UserFacingError.message(e, "Could not load this folder.")
                emptyList()
            }
        }
        isLoading = false
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
            // iOS 26's bar: both buttons on glass capsules 16 in, the title in headline.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SheetBarButton(
                    stringResource(if (pathStack.size > 1) R.string.back else R.string.cancel),
                    onClick = { if (pathStack.size > 1) pathStack = pathStack.dropLast(1) else onDismiss() },
                )
                Text(
                    currentPath.substringAfterLast('/').ifEmpty { stringResource(R.string.pnc_all_files) },
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                SheetBarButton(
                    stringResource(R.string.pnc_all_files),
                    onClick = { if (currentPath.isNotEmpty()) pathStack = pathStack + "" },
                    enabled = currentPath.isNotEmpty(),
                )
            }
            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal) }
                errorMessage != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(errorMessage ?: "", color = colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.pnc_folder_empty), color = colors.textSecondary)
                }
                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                    items(entries.size) { index ->
                        val file = entries[index]
                        val pickable = file.isDirectory || file.name.substringAfterLast('.', "").lowercase() in allowedExtensions
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = pickable) {
                                    if (file.isDirectory) pathStack = pathStack + file.path.trim('/') else onPick(file)
                                }
                                .alpha(if (pickable) 1f else 0.4f)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (file.isDirectory) KaspaTeal else colors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(file.name, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                            if (file.isDirectory) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.textSecondary)
                        }
                        HorizontalDivider(Modifier.padding(start = 54.dp), color = colors.divider, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
