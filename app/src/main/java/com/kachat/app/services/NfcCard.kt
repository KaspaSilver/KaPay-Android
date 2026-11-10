package com.kachat.app.services

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Bundle
import com.google.gson.Gson
import com.kachat.app.models.WalletSourceFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume

/**
 * Client for a KaChat-NFC card: an optional backup of a seed phrase the user already has, saved
 * and restored by tapping a Java Card. The card never generates or uses a key. It holds bytes this
 * file has already encrypted, behind a PIN. The commands and backup format are public, in
 * docs/PROTOCOL.md of https://github.com/KaspaSilver/KaChat-NFC, so a card works across apps
 * (iOS 4f5dfd2 `NFCCard`).
 */
object NfcCard {
    private val APPLET_AID = byteArrayOf(0xF0.toByte(), 0x4B, 0x41, 0x43, 0x48, 0x41, 0x54, 0x4E, 0x46, 0x43, 0x01)
    internal val appletAid: ByteArray get() = APPLET_AID.clone()

    const val PIN_LENGTH = 6
    const val TAP_PROMPT = "Hold your card flat against the back of your phone."

    /** iOS's reader session gives up after a minute. */
    private const val TAP_TIMEOUT_MS = 60_000L
    /** A card takes a moment to commit a save. */
    private const val TRANSCEIVE_TIMEOUT_MS = 10_000

    /** In production builds too (iOS 953b255). Phones with no NFC can't read these cards. */
    fun isAvailable(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    /**
     * Runs [work] against one card, for one tap. A tap lasts as long as [work] does, so slow work
     * such as key derivation belongs outside it. Cancelling the caller ends the wait.
     */
    suspend fun <T> run(activity: Activity, work: suspend (NfcCardConnection) -> T): T {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: throw NfcCardError.CardFailed("no NFC")
        if (!adapter.isEnabled) throw NfcCardError.NfcOff
        try {
            val tag = try {
                withTimeout(TAP_TIMEOUT_MS) { awaitTag(activity, adapter) }
            } catch (_: TimeoutCancellationException) {
                throw NfcCardError.TimedOut
            }
            val isoDep = IsoDep.get(tag) ?: throw NfcCardError.NotLoaded
            return withContext(Dispatchers.IO) {
                try {
                    isoDep.connect()
                    isoDep.timeout = TRANSCEIVE_TIMEOUT_MS
                    work(NfcCardConnection { apdu -> isoDep.transceive(apdu) })
                } catch (_: TagLostException) {
                    throw NfcCardError.CardRemoved
                } catch (_: IOException) {
                    throw NfcCardError.CardRemoved
                } finally {
                    runCatching { isoDep.close() }
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                runCatching { adapter.disableReaderMode(activity) }
            }
        }
    }

    private suspend fun awaitTag(activity: Activity, adapter: NfcAdapter): Tag =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                // Reader mode stays on until run() ends, so the system doesn't claim the card
                // mid-tap. Only the first card counts.
                adapter.enableReaderMode(
                    activity,
                    { tag -> if (continuation.isActive) continuation.resume(tag) },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    Bundle(),
                )
            }
        }
}

sealed class NfcCardError(message: String) : Exception(message) {
    object NfcOff : NfcCardError("NFC is off. Turn it on in your phone's settings and try again.")
    object TimedOut : NfcCardError("No card was found in time. Try again, and keep the card flat against the back of your phone.")
    object CardRemoved : NfcCardError("The card moved away too soon. Hold it still and try again. If a save was interrupted, your previous backup on the card is still there.")
    object NotLoaded : NfcCardError("This card doesn't have the KaChat-NFC software yet. Load it from the NFC Card page in Kaspa Quick Start.")
    class WrongPin(val triesRemaining: Int) : NfcCardError(
        if (triesRemaining == 1) "Wrong PIN. 1 try left before this card's PIN is blocked for good."
        else "Wrong PIN. $triesRemaining tries left."
    )
    object PinBlocked : NfcCardError("This card's PIN is blocked after too many wrong tries, so any backup on it can no longer be read. Use your 12 or 24 words instead.")
    object NoBackup : NfcCardError("There's no backup on this card.")
    object NoPin : NfcCardError("This card has no PIN yet, so it has no backup on it.")
    object CardChanged : NfcCardError("This isn't the card you scanned first. Start again with the card you want to use.")
    object BackupTooLarge : NfcCardError("This account's backup is too large for the card.")
    object WrongPassphrase : NfcCardError("That passphrase doesn't match this backup. It has to be exact, including capitals and spaces.")
    object NoPassphrase : NfcCardError("This account has no passphrase, so it can't be backed up to a card.")
    object UnreadableBackup : NfcCardError("This backup can't be read. It may have been made by a newer version of KaPay.")
    object SaveNotConfirmed : NfcCardError("The save couldn't be confirmed, so don't rely on this card. Try again.")
    object SeedUnavailable : NfcCardError("This account's 12 or 24 words couldn't be read on this device.")
    class CardFailed(detail: String) : NfcCardError("The card reported a problem ($detail). Try again.")
}

/** The applet's answer to SELECT. */
data class NfcCardStatus(
    val pinSet: Boolean,
    val hasBackup: Boolean,
    val pinBlocked: Boolean,
    val triesRemaining: Int,
    val maxBackup: Int,
)

/** One card, for the length of one tap. [transceive] sends an APDU and returns the reply with its
 *  two status bytes last. */
class NfcCardConnection(private val transceive: (ByteArray) -> ByteArray) {

    private class Reply(val data: ByteArray, val status: Int)

    private fun send(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray, expectsResponse: Boolean): Reply {
        val apdu = ByteArrayOutputStream()
        apdu.write(byteArrayOf(cla.toByte(), ins.toByte(), p1.toByte(), p2.toByte()))
        if (data.isNotEmpty()) {
            apdu.write(data.size)
            apdu.write(data)
        }
        // Le 0 asks for up to 256 bytes.
        if (expectsResponse) apdu.write(0)
        val reply = transceive(apdu.toByteArray())
        if (reply.size < 2) throw NfcCardError.CardFailed("short reply")
        val status = ((reply[reply.size - 2].toInt() and 0xFF) shl 8) or (reply[reply.size - 1].toInt() and 0xFF)
        return Reply(reply.copyOf(reply.size - 2), status)
    }

    private fun command(ins: Int, p1p2: Int = 0, data: ByteArray = ByteArray(0), expectsResponse: Boolean = false): ByteArray {
        val reply = send(0x80, ins, (p1p2 shr 8) and 0xFF, p1p2 and 0xFF, data, expectsResponse)
        when {
            reply.status == 0x9000 -> return reply.data
            reply.status in 0x63C0..0x63CF -> throw NfcCardError.WrongPin(reply.status and 0x000F)
            reply.status == 0x6983 -> throw NfcCardError.PinBlocked
            reply.status == 0x6A88 -> throw NfcCardError.NoBackup
            else -> throw NfcCardError.CardFailed("%04X".format(reply.status))
        }
    }

    fun select(): NfcCardStatus {
        val reply = send(0x00, 0xA4, 0x04, 0x00, NfcCard.appletAid, expectsResponse = true)
        if (reply.status == 0x6A82) throw NfcCardError.NotLoaded
        if (reply.status != 0x9000) throw NfcCardError.CardFailed("%04X".format(reply.status))
        val bytes = reply.data
        if (bytes.size != 7 || bytes[0].toInt() != 1) throw NfcCardError.UnreadableBackup
        val flags = bytes[3].toInt()
        return NfcCardStatus(
            pinSet = flags and 0x01 != 0,
            hasBackup = flags and 0x02 != 0,
            pinBlocked = flags and 0x04 != 0,
            triesRemaining = bytes[4].toInt() and 0xFF,
            maxBackup = ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[6].toInt() and 0xFF),
        )
    }

    fun setPin(pin: String) {
        command(0x21, data = pin.toByteArray(Charsets.UTF_8))
    }

    fun verifyPin(pin: String) {
        command(0x20, data = pin.toByteArray(Charsets.UTF_8))
    }

    /** The card checks the hash before the new copy replaces the old one, so an interrupted save
     *  leaves the previous backup in place. */
    fun writeBackup(backup: ByteArray) {
        command(0xD0, p1p2 = backup.size)
        var offset = 0
        while (offset < backup.size) {
            val end = minOf(offset + CHUNK_SIZE, backup.size)
            command(0xD1, p1p2 = offset, data = backup.copyOfRange(offset, end))
            offset = end
        }
        command(0xD2, data = MessageDigest.getInstance("SHA-256").digest(backup))
    }

    fun readBackup(): ByteArray {
        val backup = ByteArrayOutputStream()
        while (true) {
            val chunk = command(0xB0, p1p2 = backup.size(), expectsResponse = true)
            if (chunk.isEmpty()) return backup.toByteArray()
            backup.write(chunk)
        }
    }

    fun wipe() {
        command(0xE0)
    }

    private companion object {
        const val CHUNK_SIZE = 200
    }
}

/**
 * What's encrypted on the card: everything an account needs beyond the words, because some
 * accounts can't be restored correctly from the words alone. The BIP39 passphrase is deliberately
 * not here: it's what protects the account if someone gets past the PIN. [chattingAddress] lets a
 * restore catch a mistyped passphrase, which would otherwise open a different, empty account.
 * Field names are the JSON the iPhone reads and writes.
 */
data class NfcCardBackupPayload(
    val version: Int = 2,
    val words: List<String>,
    val chattingAddress: String,
    val family: String,
    val chattingAddressIndex: Int,
    val alias: String,
) {
    /** The derivation family, or null for one this app doesn't have. */
    val sourceFamily: WalletSourceFamily? get() = familyFromCard(family)

    companion object {
        /** The names on the card are iOS's `WalletSourceFamily` raw values, shared across apps. */
        fun familyName(family: WalletSourceFamily): String = when (family) {
            WalletSourceFamily.KASPA_STANDARD -> "kaspaStandard"
            WalletSourceFamily.KASPA_LEGACY_972 -> "kaspaLegacy972"
            WalletSourceFamily.CHAINGE -> "chainge"
            WalletSourceFamily.ONE_KEY -> "oneKey"
        }

        fun familyFromCard(name: String): WalletSourceFamily? =
            WalletSourceFamily.entries.firstOrNull { familyName(it) == name }
    }
}

/**
 * The backup format in docs/PROTOCOL.md: magic, PBKDF2 iterations, salt, nonce, then AES-256-GCM.
 * The key comes from the card's PIN, so nothing extra to remember. A six-digit PIN can be guessed
 * offline by anyone who extracts the chip, which is why the passphrase stays off the card. Key
 * derivation takes a moment, so call these away from the main thread.
 */
object NfcCardBackupFormat {
    private val MAGIC = "KNB1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 600_000
    private const val MAXIMUM_ITERATIONS = 10_000_000
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH = 16
    private const val HEADER_LENGTH = 4 + 4 + SALT_LENGTH + NONCE_LENGTH

    private val gson = Gson()

    fun seal(payload: NfcCardBackupPayload, pin: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val nonce = ByteArray(NONCE_LENGTH).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_LENGTH)
            .put(MAGIC).putInt(ITERATIONS).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pin, salt, ITERATIONS), GCMParameterSpec(TAG_LENGTH * 8, nonce))
        cipher.updateAAD(header)
        // doFinal returns the ciphertext with its tag last, the layout on the card.
        return header + cipher.doFinal(gson.toJson(payload).toByteArray(Charsets.UTF_8))
    }

    fun open(backup: ByteArray, pin: String): NfcCardBackupPayload {
        if (backup.size <= HEADER_LENGTH + TAG_LENGTH ||
            !backup.copyOfRange(0, 4).contentEquals(MAGIC)
        ) throw NfcCardError.UnreadableBackup
        val count = ByteBuffer.wrap(backup, 4, 4).int
        if (count < ITERATIONS || count > MAXIMUM_ITERATIONS) throw NfcCardError.UnreadableBackup
        val salt = backup.copyOfRange(8, 8 + SALT_LENGTH)
        val nonce = backup.copyOfRange(8 + SALT_LENGTH, HEADER_LENGTH)
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(pin, salt, count), GCMParameterSpec(TAG_LENGTH * 8, nonce))
            cipher.updateAAD(backup, 0, HEADER_LENGTH)
            cipher.doFinal(backup, HEADER_LENGTH, backup.size - HEADER_LENGTH)
        } catch (_: java.security.GeneralSecurityException) {
            throw NfcCardError.UnreadableBackup
        }
        val payload = runCatching {
            gson.fromJson(String(plaintext, Charsets.UTF_8), NfcCardBackupPayload::class.java)
        }.getOrNull() ?: throw NfcCardError.UnreadableBackup
        if (payload.version != 2) throw NfcCardError.UnreadableBackup
        // Gson fills a missing field with null whatever the Kotlin type says: a payload without
        // its words, address or family is unreadable here, not a crash later in the restore.
        @Suppress("SENSELESS_COMPARISON")
        if (payload.words == null || payload.words.isEmpty() || payload.words.any { it == null || it.isBlank() } ||
            payload.chattingAddress == null || payload.chattingAddress.isBlank() ||
            payload.family == null || payload.chattingAddressIndex < 0
        ) throw NfcCardError.UnreadableBackup
        val alias: String? = payload.alias
        return if (alias == null) payload.copy(alias = "") else payload
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        // NFKD, so the same password typed on another device or keyboard gives the same key.
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFKD)
        val spec = PBEKeySpec(normalized.toCharArray(), salt, iterations, 32 * 8)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }
}
