package com.kachat.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.kachat.app.R

/**
 * Copies sensitive text (a private key hex) to the system clipboard and auto-wipes it 30 seconds
 * later — but only if the clipboard still holds that exact value (i.e. the user hasn't copied
 * something else in the meantime). A main-looper [Handler] is used rather than a composition-scoped
 * coroutine so the wipe still fires after the user navigates away from the screen that copied it.
 * Mirrors the iOS 30-second clipboard clear for private-key material.
 *
 * The recovery phrase goes through here too (iOS f9beba1): copying it is how it reaches a
 * password manager, and the same sensitive marking and 30-second wipe apply.
 */
/**
 * The one standardized confirmation toast for copying an address to the clipboard, shared by
 * every address-copy site in the app (iOS shows the identical text): "Address kaspa:qz3x...m2aj...8f2k copied".
 *
 * The shortened form is three segments of the payload - the first 4 characters, 4 characters from
 * the exact middle, and the last 4 - joined with "...", keeping the network prefix. Three segments
 * (rather than the ends-only [KaspaAddress.shortDisplay]) make lookalike-address swaps harder to
 * miss when the user glances at the confirmation. Short or abnormal values (no prefix, tiny
 * payload - e.g. a foreign chain's swap deposit address) fall back to the plain string.
 */
fun showAddressCopiedToast(context: Context, address: String, haptic: Boolean = true) {
    // iOS plays Haptics.success() with nearly every address copy; the few that don't (a group
    // member's or a public room sender's address) pass false.
    if (haptic) {
        var c: Context? = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.window?.decorView?.playHaptic(IosHaptic.SUCCESS)
    }
    com.kachat.app.ui.screens.IosToasts.show(context.getString(R.string.address_copied_short, addressCopiedDisplay(address)))
}

/**
 * The three-segment shortened form used by [showAddressCopiedToast]; see its doc comment. iOS's
 * `addressToastShortened`: trimmed, the prefix through its colon kept (none without one), and the
 * payload shortened only when it is at least 24 characters, so the segments cannot overlap.
 */
internal fun addressCopiedDisplay(address: String): String {
    val trimmed = address.trim()
    val colon = trimmed.indexOf(':')
    val prefix = if (colon >= 0) trimmed.substring(0, colon + 1) else ""
    val body = if (colon >= 0) trimmed.substring(colon + 1) else trimmed
    if (body.length < 24) return trimmed
    val midStart = body.length / 2 - 2
    return "$prefix${body.take(4)}...${body.substring(midStart, midStart + 4)}...${body.takeLast(4)}"
}

fun copyPrivateKeyWithAutoWipe(context: Context, value: String, label: String = "private key") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Marks the clip sensitive on Android 13+: the system clipboard preview redacts the key
        // instead of flashing it on screen, and clipboard-history/sync surfaces treat it as
        // secret. No-op below API 33.
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    pendingClipSecret = value
    val appContext = context.applicationContext
    // When it's due survives the process (no secret is written: the label tells ours apart), so
    // the next return to the app wipes it if the Handler died with the process.
    appContext.getSharedPreferences(CLIP_WIPE_PREFS, Context.MODE_PRIVATE).edit()
        .putLong(KEY_WIPE_AT, System.currentTimeMillis() + CLIP_WIPE_DELAY_MS)
        .putString(KEY_WIPE_LABEL, label)
        .apply()
    scheduleClipWipe(appContext, CLIP_WIPE_DELAY_MS)
}

private const val CLIP_WIPE_PREFS = "kachat_clipboard_wipe"
private const val KEY_WIPE_AT = "wipe_at"
private const val KEY_WIPE_LABEL = "label"
private const val CLIP_WIPE_DELAY_MS = 30_000L
private val clipWipeToken = Any()
private val clipWipeHandler by lazy { Handler(Looper.getMainLooper()) }

/** The secret last copied, in memory only; after a process death the clip's label stands in. */
@Volatile
private var pendingClipSecret: String? = null

private fun scheduleClipWipe(context: Context, delayMs: Long) {
    clipWipeHandler.removeCallbacksAndMessages(clipWipeToken)
    clipWipeHandler.postAtTime({ wipeSensitiveClipIfDue(context) }, clipWipeToken, android.os.SystemClock.uptimeMillis() + delayMs)
}

/**
 * Wipes a secret copied by [copyPrivateKeyWithAutoWipe] once its 30 seconds are up: from the
 * Handler, and from MainActivity whenever the app is back in front, which catches a wipe the
 * Handler missed (the process died, or it fired while the app couldn't read the clipboard).
 *
 * From Android 10 an app that isn't in front reads the clipboard as empty, so a clip it can't
 * read is wiped too: it's ours or unreadable. A clip it can read is wiped only if it is still
 * the secret (or, after a process death, still carries its label). A wipe not yet due is
 * rescheduled.
 */
fun wipeSensitiveClipIfDue(context: Context) {
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences(CLIP_WIPE_PREFS, Context.MODE_PRIVATE)
    val wipeAt = prefs.getLong(KEY_WIPE_AT, 0L)
    if (wipeAt == 0L) return
    val remaining = wipeAt - System.currentTimeMillis()
    if (remaining > 0) {
        scheduleClipWipe(appContext, remaining)
        return
    }
    val label = prefs.getString(KEY_WIPE_LABEL, null)
    val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard != null) {
        val clip = try { clipboard.primaryClip } catch (e: Exception) { null }
        val stillOurs = if (clip == null || clip.itemCount == 0) {
            true
        } else {
            val secret = pendingClipSecret
            if (secret != null) {
                clip.getItemAt(0)?.coerceToText(appContext)?.toString() == secret
            } else {
                clip.description?.label?.toString() == label
            }
        }
        if (stillOurs) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }
    pendingClipSecret = null
    prefs.edit().remove(KEY_WIPE_AT).remove(KEY_WIPE_LABEL).apply()
}
