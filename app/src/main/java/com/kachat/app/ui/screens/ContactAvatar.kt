package com.kachat.app.ui.screens

import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors

// Moved out of the deleted ChatsScreen.kt (iOS wallet 301ebfb keeps KNSAvatarView for the wallet).

/**
 * Contact avatar — the single place the app's avatar resolution order lives, used everywhere a
 * contact is shown:
 *
 *   1. the photo you assigned to [address] in Contacts (iOS 953b255).
 *   2. [imageUrl] — the contact's KNS profile photo (a remote https URL), when they have one.
 *   3. [backupPhotoBase64] — the photo carried in the chat backup (e.g. one set on desktop).
 *   4. the person glyph.
 *
 * KaChat no longer reads the phone's Contacts (iOS 00767a4), so there is no Contacts-app photo.
 * The image goes through Coil (memory/disk caches, off-main-thread decode); one that fails to
 * load falls through to the backup photo rather than dead-ending on the glyph.
 *
 * On testnet identity is `.kachat`: for an [address] the first step is its `.kachat` avatar (from
 * its profile's social link, looked up on this device), never the KNS [imageUrl] - one change for
 * the chat list, chat header, groups, public chats, calls, chess and contacts (iOS e52357d
 * `KNSAvatarView`). Mainnet, or no [address]: [imageUrl] as before.
 */
@Composable
fun ContactAvatar(
    imageUrl: String?,
    fallbackText: String,
    size: Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalAppColors.current.surface,
    fontSize: TextUnit = 16.sp,
    backupPhotoBase64: String? = null,
    /** Whose avatar this is (a `kaspa:` / `kaspatest:` address), for your Contacts photo and the
     *  testnet `.kachat` rule. */
    address: String? = null,
    /** False on the Contacts editor, which draws its own pending or removed photo. */
    showAssignedPhoto: Boolean = true,
) {
    // The photo you assigned to this address in Contacts. Your own choice, so it wins over the
    // avatar the address set for itself, as it does on the Contacts screen (iOS 953b255).
    val addressBook = if (showAssignedPhoto && address != null) com.kachat.app.services.AddressBookManager.shared else null
    val photoVersion = addressBook?.photoVersion ?: 0
    val assignedPhoto = remember(address, photoVersion, addressBook) { addressBook?.photo(address)?.asImageBitmap() }
    val shownUrl = if (address != null && com.kachat.app.services.kachatnames.KachatNamesService.isEnabled) {
        kachatAvatarUrl(address)
    } else {
        imageUrl
    }
    val candidates = remember(shownUrl) {
        listOfNotNull(shownUrl?.takeIf { it.isNotBlank() })
    }
    // Cross-platform backup photo (base64 JPEG); the last fallback before the glyph. Decoded off
    // the main thread and kept app-wide, so opening a list decodes each photo once rather than
    // every visible row on the main thread every time the screen comes back.
    val backupBitmap by produceState(
        initialValue = backupPhotoBase64?.let { backupAvatarCache.get(it) },
        backupPhotoBase64
    ) {
        val base64 = backupPhotoBase64
        if (base64.isNullOrBlank()) { value = null; return@produceState }
        backupAvatarCache.get(base64)?.let { value = it; return@produceState }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { decodeBase64Avatar(base64) }
            ?.also { backupAvatarCache.put(base64, it) }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (assignedPhoto != null) {
            Image(
                bitmap = assignedPhoto,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            AvatarImageChain(candidates, fallbackText, fontSize, backupBitmap)
        }
    }
}

/**
 * [address]'s `.kachat` avatar - its profile's avatar link (the registry's cached identity) as the
 * platform shows it (the social image cache, looked up when missing or stale). Re-renders when
 * either answer lands. Null when it has none (iOS e52357d; mainnet too since d36fc42, where the
 * identity is profile-only).
 */
@Composable
fun kachatAvatarUrl(address: String): String? {
    val resolver = com.kachat.app.services.kachatnames.KachatSocialImageResolver.instance ?: return null
    val entries by resolver.entries.collectAsState()
    val link = com.kachat.app.services.kachatnames.KachatNamesRegistry.cachedIdentityOf(address)?.profile?.avatar
    LaunchedEffect(link) { resolver.refreshIfStale(link) }
    return resolver.cached(link, entries)?.avatar
}

private val backupAvatarCache = android.util.LruCache<String, ImageBitmap>(64)

private fun decodeBase64Avatar(base64: String?): ImageBitmap? {
    if (base64.isNullOrBlank()) return null
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        // Backup photos arrive in other devices' archives; the claimed size is not trusted.
        com.kachat.app.util.SafeBitmapDecode.decode(bytes, maxDimension = 512)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/**
 * Renders [candidates] in order, dropping to the next on load failure; then the backup photo, then
 * the glyph. A plain AsyncImage over the glyph rather than SubcomposeAsyncImage: subcomposition per
 * avatar is what Coil advises against in scrolling lists, and every list in the app draws these.
 */
@Composable
private fun AvatarImageChain(candidates: List<String>, fallbackText: String, fontSize: TextUnit, backupBitmap: ImageBitmap? = null) {
    var failedCount by remember(candidates) { mutableIntStateOf(0) }
    val current = candidates.getOrNull(failedCount)
    // A new request once User Info > Rescan Profile downloaded this picture again (iOS bc95d17).
    val model = rememberProfileImageModel(current)
    if (current == null) {
        if (backupBitmap != null) {
            Image(
                bitmap = backupBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AvatarInitials(fallbackText, fontSize)
        }
        return
    }
    var loaded by remember(current) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The glyph shows while the image loads, as the loading slot did before.
        if (!loaded) AvatarInitials(fallbackText, fontSize)
        coil.compose.AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            onError = { failedCount++ }
        )
    }
}

@Composable
private fun AvatarInitials(text: String, fontSize: TextUnit) {
    // 4.0 (matches iOS): no photo shows a person glyph, not initials - initials read like
    // random letters for KNS-less addresses and looked inconsistent next to real avatars.
    // iOS KNSAvatarView.fallbackAvatar: a filled person on the accent at 20%.
    Box(
        modifier = Modifier.fillMaxSize().background(KaspaTeal.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = KaspaTeal,
            modifier = Modifier.fillMaxSize(0.5f)
        )
    }
}

