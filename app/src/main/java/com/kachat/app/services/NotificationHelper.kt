package com.kachat.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kachat.app.MainActivity
import com.kachat.app.R
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.util.tr
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wallet's notifications: "Received KAS" on its own addresses ([showAddressActivity], from
 * [AddressActivityNotifier] and the address_activity push) and .kachat name events
 * ([showKachatName]). The chat banners went with the chats (iOS wallet 301ebfb). Settings >
 * Notifications controls whether these fire at all, and whether they carry sound/vibration.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsRepository
) {
    // Whether the app is on screen right now - set by KaChatApplication's process lifecycle
    // observer.
    private val appForeground = MutableStateFlow(false)

    // Recently-notified transaction ids, so the same message reaching two delivery paths at once
    // (in-app poll/scan AND an FCM push, both live while the app is foregrounded) posts exactly
    // one banner. LRU-capped; access is synchronized because the claimants run on IO coroutines
    // and the FCM service thread.
    private val notifiedTxIds = object : LinkedHashMap<String, Boolean>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 200
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // A channel's sound/vibration is fixed by the OS once created — the app can't
            // flip it later, only the user can (in system notification settings). The only
            // way an in-app Sound/Vibration toggle can actually do anything on API 26+ is
            // to pre-create one channel per combination and post through whichever one
            // matches the current settings.
            val manager = context.getSystemService(NotificationManager::class.java)
            CHANNELS.forEach { (channelId, soundOn, vibrationOn) ->
                // KaChat Wallet: its notifications are receipts and .kachat events, and the icon
                // never carries a badge (iOS wallet a8dc101) - no launcher dot either. All four
                // carry the same notifications; each is named for its sound/vibration so the
                // system's list tells them apart (re-creating a channel renames it in place).
                val channelName = context.getString(channelNameRes(soundOn, vibrationOn))
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Received KAS and .kachat names"
                    enableVibration(vibrationOn)
                    if (!soundOn) setSound(null, null)
                    setShowBadge(false)
                }
                manager?.createNotificationChannel(channel)
            }
            // The pre-4.0 background-sync foreground service ("KaChat - Checking for new
            // messages") is gone — remote push is the closed-app delivery path now. Deleting its
            // channel removes the stale "Background sync" entry from the system's notification
            // settings on devices that upgraded, and guarantees nothing can post through it.
            manager?.deleteNotificationChannel("kachat_sync_service")
        }
    }

    /** See [appForeground] — driven by KaChatApplication's ProcessLifecycleOwner observer. */
    fun setAppForeground(foreground: Boolean) {
        appForeground.value = foreground
    }

    /** Whether the app is on screen. No longer a banner gate: while push is active the remote
     *  push is the only banner source, foreground included (see [PushState]); the txId ledger
     *  in [claimTxId] stays so the push's own two paths (FCM handler and a push-triggered sync)
     *  cannot double-post. */
    val isAppInForeground: Boolean get() = appForeground.value

    /** Reactive form of [isAppInForeground] — drives lifecycle-scoped loops that must start on
     *  foreground and stop on background (e.g. NextcloudSyncService's remote change watcher). */
    val appForegroundFlow: StateFlow<Boolean> = appForeground.asStateFlow()

    /**
     * Claims [txId] for notification purposes. Returns true when this caller is the first to
     * claim it (post the banner); false when another path already did (skip — it's a duplicate).
     * A null/blank id always claims successfully (nothing to dedupe on).
     */
    @Synchronized
    private fun claimTxId(txId: String?): Boolean {
        if (txId.isNullOrBlank()) return true
        return notifiedTxIds.put(txId, true) == null
    }

    /**
     * Records [txId] as notified WITHOUT posting a banner. Called by a local delivery path that
     * decrypted the tx and deliberately chose silence (mentions-only group without a mention,
     * muted member, reaction to someone else's message in a mentions-only group): the silence is
     * a final decision, so claiming here guarantees a racing FCM push for the same tx can never
     * post the banner the local path just suppressed.
     */
    @Synchronized
    fun claimWithoutNotifying(txId: String?) {
        if (!txId.isNullOrBlank()) notifiedTxIds[txId] = true
    }

    /** Claims [txId] for a banner another path posts itself: false when one was already shown
     *  for it. A plain payment to the chatting address can banner from the payment sync and from
     *  [AddressActivityNotifier] (iOS 56b75c8) - whichever comes first is the only one. */
    fun claimForBanner(txId: String): Boolean = claimTxId(txId)

    /**
     * A `.kachat` name event (an offer, a sale, an accepted offer, a renewal reminder - iOS
     * thread "kachat-names"): tapping it opens that name (iOS beeedd4).
     */
    suspend fun showKachatName(title: String, text: String, name: String, dedupeKey: String) {
        if (!settings.notificationsEnabled.first()) return
        if (!claimTxId("kachat_name_$dedupeKey")) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_KACHAT_NAME, name)
        }
        val notificationId = "kachat_name_$dedupeKey".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val soundEnabled = settings.notificationSoundEnabled.first()
        val vibrationEnabled = settings.notificationVibrationEnabled.first()
        val notification = NotificationCompat.Builder(context, channelFor(soundEnabled, vibrationEnabled))
            .setSmallIcon(R.drawable.ic_kachat_wallet_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            // The lock screen shows only a neutral line, never the name or an amount.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channelFor(soundEnabled, vibrationEnabled), R.string.notif_public_kachat_name))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(!soundEnabled && !vibrationEnabled)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
            .apply { if (!soundEnabled) setSound(null) }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    /** Wallet address-activity notification ("Received X KAS" on a spending/cold address) — see
     *  [AddressActivityNotifier]. Gated only by the global notifications toggle here; the
     *  Address Activity setting itself is checked by the notifier before calling.
     *
     *  [kind] is [KIND_COLD] or [KIND_SPENDING] (iOS sends the same string in the notification's
     *  `userInfo["kind"]`): the tap opens Storage or Manage Addresses respectively, so the
     *  receipt lands on a screen that actually shows the address it was about. Without it the
     *  tap only re-opened whatever screen the app was last on.
     *
     *  [address] names the one address the Kaspa landed on (iOS 13046ad, `userInfo["address"]`):
     *  the tap then opens that address's own History, in a sheet over the current tab, and
     *  [kind] is only the fallback for a receipt that spans several addresses. */
    suspend fun showAddressActivity(
        title: String,
        text: String,
        dedupeKey: String,
        kind: String = KIND_SPENDING,
        address: String? = null,
    ) {
        if (!settings.notificationsEnabled.first()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WALLET_ACTIVITY_KIND, kind)
            if (!address.isNullOrEmpty()) putExtra(EXTRA_WALLET_ACTIVITY_ADDRESS, address)
        }
        val notificationId = "addr_activity_$dedupeKey".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val soundEnabled = settings.notificationSoundEnabled.first()
        val vibrationEnabled = settings.notificationVibrationEnabled.first()
        val notification = NotificationCompat.Builder(context, channelFor(soundEnabled, vibrationEnabled))
            .setSmallIcon(R.drawable.ic_kachat_wallet_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            // The lock screen shows only a neutral line, never the amount or the address.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channelFor(soundEnabled, vibrationEnabled), R.string.notif_public_received))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(!soundEnabled && !vibrationEnabled)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
            .apply { if (!soundEnabled) setSound(null) }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    /** The stand-in a locked screen shows for a [NotificationCompat.VISIBILITY_PRIVATE]
     *  notification: the app's icon and name with one neutral line. */
    private fun publicVersion(channelId: String, @StringRes text: Int) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_kachat_wallet_notification)
            .setContentTitle(tr(text))
            .build()

    companion object {
        const val CHANNEL_ID = "kachat_messages_sound_vibrate"
        /** A `.kachat` name notification: the name (without `.kachat`) a tap opens (iOS beeedd4). */
        const val EXTRA_KACHAT_NAME = "kachat_name"
        /** [KIND_COLD] / [KIND_SPENDING] — which wallet screen an address-activity tap opens. */
        const val EXTRA_WALLET_ACTIVITY_KIND = "wallet_activity_kind"
        const val KIND_COLD = "cold"
        const val KIND_SPENDING = "spending"
        /** The chatting address (iOS 56b75c8): its receipt always names the address, so the tap
         *  opens its History; as a fallback it lands where a spending receipt does. */
        const val KIND_CHATTING = "chatting"
        /** The single own address an address-activity notification is about; its tap opens
         *  that address's History (iOS 13046ad). Absent for a receipt across several addresses. */
        const val EXTRA_WALLET_ACTIVITY_ADDRESS = "wallet_activity_address"

        // (channelId, soundEnabled, vibrationEnabled)
        private val CHANNELS = listOf(
            Triple("kachat_messages_sound_vibrate", true, true),
            Triple("kachat_messages_sound_only", true, false),
            Triple("kachat_messages_vibrate_only", false, true),
            Triple("kachat_messages_silent", false, false)
        )

        @StringRes
        private fun channelNameRes(soundEnabled: Boolean, vibrationEnabled: Boolean): Int = when {
            soundEnabled && vibrationEnabled -> R.string.notif_channel_sound_vibrate
            soundEnabled -> R.string.notif_channel_sound_only
            vibrationEnabled -> R.string.notif_channel_vibrate_only
            else -> R.string.notif_channel_silent
        }

        internal fun channelFor(soundEnabled: Boolean, vibrationEnabled: Boolean) =
            CHANNELS.first { it.second == soundEnabled && it.third == vibrationEnabled }.first
    }
}
