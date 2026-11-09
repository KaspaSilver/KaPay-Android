package com.kachat.app.util

import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kachat.app.R

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * The lock the prompt accepts. androidx.biometric 1.1.0 refuses `BIOMETRIC_STRONG or
 * DEVICE_CREDENTIAL` on API 28-29 (a Class 2 biometric could stand in for the PIN there), which
 * made every check fail on those phones; `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` is the combination
 * it supports there. API 26-27 and 30+ keep the strong one.
 */
private val allowedAuthenticators: Int
    get() = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P || Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
        BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }

/** A screen lock or biometric is set up, so [authenticateWithDeviceCredential] can ask. */
fun Context.hasDeviceCredential(): Boolean =
    BiometricManager.from(this).canAuthenticate(allowedAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Gates [onSuccess] behind whatever the device's own lock screen is set to (fingerprint/face
 * unlock or PIN/pattern/password) — used for the wallet's most sensitive actions (viewing the
 * seed phrase, unlocking a saved account after logout, sending Kaspa, deleting an account) so they can't be reached by anyone who
 * just picked up an already-unlocked phone.
 *
 * A phone with no secure lock screen at all used to fall straight through to [onSuccess]: the
 * "gate" on the recovery phrase was then no gate, and anyone holding the unlocked phone could
 * read it. It now refuses, says what to set, and calls [onFailure] — matching iOS's `DeviceAuth`.
 */
fun Context.authenticateWithDeviceCredential(
    title: String,
    subtitle: String? = null,
    onSuccess: () -> Unit,
    onFailure: () -> Unit = {}
) {
    val activity = findFragmentActivity()
    if (activity == null) {
        // MainActivity is an AppCompatActivity, so this is unreachable in the app as it stands.
        // Refusing rather than passing keeps it that way: a future host with no FragmentActivity
        // must not silently turn the gate off.
        onFailure()
        return
    }
    if (BiometricManager.from(activity).canAuthenticate(allowedAuthenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        presentScreenLockRequired(activity)
        onFailure()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .apply { subtitle?.let { setSubtitle(it) } }
        .setAllowedAuthenticators(allowedAuthenticators)
        .build()
    prompt.authenticate(promptInfo)
}

/**
 * One dialog, from wherever the reveal was asked: the callers are spread over several screens
 * that have no shared message surface. Mirrors iOS's `presentPasscodeRequired`, naming Android's
 * own setting instead of the iPhone's.
 *
 * Already a window of its own, as iOS 1652437 made its alert: a platform dialog on the activity,
 * added above every sheet and dialog open at the time and owned by none of them, so a caller that
 * closes its sheet and asks straight after (Delete Account) doesn't take the dialog with it. One at
 * a time, as iOS's `passcodeAlertWindow == nil` guard.
 */
private fun presentScreenLockRequired(activity: FragmentActivity) {
    if (screenLockAlert?.get()?.isShowing == true || activity.isFinishing || activity.isDestroyed) return
    val dialog = AlertDialog.Builder(activity)
        .setTitle(activity.getString(R.string.screen_lock_required))
        .setMessage(activity.getString(R.string.screen_lock_required_message))
        .setPositiveButton(android.R.string.ok, null)
        .show()
    screenLockAlert = java.lang.ref.WeakReference(dialog)
}

/** The Screen Lock Required dialog while it's up; weak, so a closed activity's dialog isn't kept. */
private var screenLockAlert: java.lang.ref.WeakReference<AlertDialog>? = null
