package com.kachat.app.services

import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The cross-platform "Encrypted Backup Envelope (v1)" codec (iOS 953b255 `BackupEnvelope`; the same
 * file as KaChat-Android's, whose `kachat-contacts.json` this app shares). The contacts file carries
 * this envelope as its entire content:
 *
 * ```json
 * {
 *   "kachatEncryptedBackup": 1,
 *   "cipher": "aes-256-gcm",
 *   "nonce": "<base64, 12 random bytes, fresh per write>",
 *   "ciphertext": "<base64, AES-256-GCM ciphertext with the 16-byte tag appended>",
 *   "walletHint": "<first 8 bytes of SHA-256(walletAddress), hex>"
 * }
 * ```
 *
 * The key is derived identically on every platform:
 * `SHA-256(identity_private_key_bytes || UTF8("kachat-backup-v1"))`, so any device holding the seed
 * can read the file, in either app, and nothing else can. Writers ALWAYS encrypt. [envelopeWalletHint]
 * lets a reader skip a foreign wallet's file without decrypting anything.
 */
object BackupCrypto {

    /** The exact user-facing error for a ciphertext this account's key cannot open. */
    const val DECRYPT_FAILED_MESSAGE =
        "Could not decrypt the backup. It may belong to a different account."

    /** iOS `EnvelopeError.keyUnavailable`. */
    const val KEY_UNAVAILABLE_MESSAGE =
        "The wallet key needed to encrypt the backup is not available."

    private const val MARKER = "kachatEncryptedBackup"
    private const val CIPHER_NAME = "aes-256-gcm"
    private const val KEY_CONTEXT = "kachat-backup-v1"
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    /** The envelope version this client writes and reads. */
    const val ENVELOPE_VERSION = 1

    /**
     * `key = SHA-256(identity private key raw 32 bytes || UTF8("kachat-backup-v1"))` - the private
     * key is the chatting address's ([WalletManager.getPrivateKeyBytes]).
     */
    fun deriveKey(identityPrivateKeyBytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(identityPrivateKeyBytes)
        digest.update(KEY_CONTEXT.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }

    /** First 8 bytes of SHA-256(walletAddress) as lowercase hex - byte-identical to
     *  [NextcloudService.walletHashSuffix] and iOS's `KeychainService.walletHashSuffix`. */
    fun walletHint(walletAddress: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(walletAddress.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }

    /** True when [content] is a v1 envelope (`kachatEncryptedBackup == 1`). */
    fun isEnvelope(content: String): Boolean = envelopeVersion(content) == ENVELOPE_VERSION

    /**
     * The `kachatEncryptedBackup` version of a JSON object that carries the envelope marker, or
     * null when [content] isn't one (or isn't JSON). A newer envelope isn't v1, so it is never
     * read or written over.
     */
    fun envelopeVersion(content: String): Int? {
        if (!content.contains("\"$MARKER\"")) return null
        val envelope = runCatching { JsonParser.parseString(content) }.getOrNull() as? JsonObject ?: return null
        val marker = envelope.get(MARKER) as? JsonPrimitive ?: return null
        return runCatching { marker.asInt }.getOrNull()
    }

    /** The envelope's `walletHint`, or null when absent or unreadable. */
    fun envelopeWalletHint(content: String): String? = runCatching {
        val envelope = JsonParser.parseString(content) as? JsonObject
        (envelope?.get("walletHint") as? JsonPrimitive)?.asString?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Encrypts [plaintextJson] into a fresh v1 envelope (new random nonce every call). */
    fun encrypt(plaintextJson: String, key: ByteArray, walletAddress: String): String {
        val nonce = ByteArray(NONCE_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        // Java's GCM output is ciphertext || 16-byte tag - exactly the envelope's wire form.
        val ciphertext = cipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put(MARKER, 1)
            .put("cipher", CIPHER_NAME)
            .put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .put("walletHint", walletHint(walletAddress))
            .toString()
    }

    /**
     * Decrypts a v1 envelope back to its JSON. ANY failure - wrong key (different seed), corrupt
     * ciphertext, malformed envelope - throws `IllegalStateException(DECRYPT_FAILED_MESSAGE)`;
     * callers on an upload path let that propagate BEFORE anything uploads, so a file that cannot be
     * read is never overwritten.
     */
    fun decrypt(envelopeJson: String, key: ByteArray): String {
        try {
            val envelope = JSONObject(envelopeJson)
            check(envelope.optInt(MARKER, 0) == 1)
            check(envelope.optString("cipher").equals(CIPHER_NAME, ignoreCase = true))
            val nonce = Base64.decode(envelope.getString("nonce"), Base64.DEFAULT)
            val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.DEFAULT)
            check(nonce.size == NONCE_LENGTH)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException(DECRYPT_FAILED_MESSAGE)
        }
    }
}
