package com.kachat.app.services

import com.kachat.app.models.WalletSourceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The card commands and backup format in KaChat-NFC's docs/PROTOCOL.md, as iOS 4f5dfd2 uses them. */
class NfcCardTest {

    private val payload = NfcCardBackupPayload(
        words = listOf("abandon", "about"),
        chattingAddress = "kaspa:qtest",
        family = "kaspaLegacy972",
        chattingAddressIndex = 3,
        alias = "My Account",
    )

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `a backup sealed here opens with its PIN only`() {
        val sealed = NfcCardBackupFormat.seal(payload, "123456")
        assertEquals("KNB1", String(sealed, 0, 4, Charsets.US_ASCII))
        assertEquals(payload, NfcCardBackupFormat.open(sealed, "123456"))
        assertThrows(NfcCardError.UnreadableBackup::class.java) { NfcCardBackupFormat.open(sealed, "654321") }
    }

    /** Sealed by iOS's own code (CryptoKit, CommonCrypto, JSONEncoder, as in NFCCard.swift). */
    @Test
    fun `a backup in the iPhone's format opens here`() {
        val sealed = hex(IPHONE_FORMAT_BACKUP)
        val opened = NfcCardBackupFormat.open(sealed, "123456")
        assertEquals(listOf("abandon", "about"), opened.words)
        assertEquals("kaspa:qtest", opened.chattingAddress)
        assertEquals(WalletSourceFamily.KASPA_LEGACY_972, opened.sourceFamily)
        assertEquals(3, opened.chattingAddressIndex)
        assertEquals("My Account", opened.alias)
    }

    @Test
    fun `a changed header or too few iterations is refused`() {
        val sealed = NfcCardBackupFormat.seal(payload, "123456")
        val otherSalt = sealed.clone().also { it[10] = (it[10] + 1).toByte() }
        assertThrows(NfcCardError.UnreadableBackup::class.java) { NfcCardBackupFormat.open(otherSalt, "123456") }
        val weak = sealed.clone().also { it[4] = 0; it[5] = 0; it[6] = 0; it[7] = 1 }
        assertThrows(NfcCardError.UnreadableBackup::class.java) { NfcCardBackupFormat.open(weak, "123456") }
        assertThrows(NfcCardError.UnreadableBackup::class.java) { NfcCardBackupFormat.open(ByteArray(10), "123456") }
    }

    @Test
    fun `family names are the ones on the card`() {
        assertEquals("kaspaStandard", NfcCardBackupPayload.familyName(WalletSourceFamily.KASPA_STANDARD))
        assertEquals("kaspaLegacy972", NfcCardBackupPayload.familyName(WalletSourceFamily.KASPA_LEGACY_972))
        assertEquals("oneKey", NfcCardBackupPayload.familyName(WalletSourceFamily.ONE_KEY))
        assertNull(NfcCardBackupPayload.familyFromCard("chainge"))
    }

    @Test
    fun `a PIN is six digits`() {
        assertTrue(NfcCard.isValidPin("012345"))
        assertFalse(NfcCard.isValidPin("12345"))
        assertFalse(NfcCard.isValidPin("1234567"))
        assertFalse(NfcCard.isValidPin("12345a"))
    }

    /** A card that answers from a script, recording what it was sent. */
    private class FakeCard(vararg replies: String) {
        val sent = mutableListOf<String>()
        private val queue = replies.toMutableList()
        val connection = NfcCardConnection { apdu ->
            sent += apdu.joinToString("") { "%02X".format(it) }
            queue.removeAt(0).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }

    @Test
    fun `select reads the status reply`() {
        val card = FakeCard("0101000305" + "0800" + "9000")
        val status = card.connection.select()
        assertEquals("00A404000BF04B41434841544E46430100", card.sent.single())
        assertEquals(NfcCardStatus(pinSet = true, hasBackup = true, pinBlocked = false, triesRemaining = 5, maxBackup = 2048), status)
    }

    @Test
    fun `a card without the applet says so`() {
        assertThrows(NfcCardError.NotLoaded::class.java) { FakeCard("6A82").connection.select() }
    }

    @Test
    fun `status words become the right errors`() {
        val wrong = assertThrows(NfcCardError.WrongPin::class.java) { FakeCard("63C3").connection.verifyPin("123456") }
        assertEquals(3, wrong.triesRemaining)
        assertThrows(NfcCardError.PinBlocked::class.java) { FakeCard("6983").connection.verifyPin("123456") }
        assertThrows(NfcCardError.NoBackup::class.java) { FakeCard("6A88").connection.readBackup() }
        assertThrows(NfcCardError.CardFailed::class.java) { FakeCard("6985").connection.wipe() }
    }

    @Test
    fun `a backup is written in chunks, then committed by its hash`() {
        val backup = ByteArray(450) { it.toByte() }
        val card = FakeCard("9000", "9000", "9000", "9000", "9000")
        card.connection.writeBackup(backup)
        assertEquals("80D0" + "01C2", card.sent[0])
        assertTrue(card.sent[1].startsWith("80D10000C8"))
        assertTrue(card.sent[2].startsWith("80D100C8C8"))
        assertTrue(card.sent[3].startsWith("80D1019032"))
        assertTrue(card.sent[4].startsWith("80D2000020"))
    }

    @Test
    fun `a backup is read until the card has no more`() {
        val card = FakeCard("0102" + "9000", "03" + "9000", "9000")
        assertArrayEquals(hex("010203"), card.connection.readBackup())
        assertEquals(listOf("80B0000000", "80B0000200", "80B0000300"), card.sent)
    }

    private companion object {
        const val IPHONE_FORMAT_BACKUP = "4B4E4231000927C0000102030405060708090A0B0C0D0E0F6465666768696A6B6C6D6E6FF804BD02386B31C31EE9E0C28F66B62A2BCA8AA37BFF13FA3DB9AD98F200BB2C562DF802C3C687275ACD5BB347E94D72B19E6CC75ABEF63294D44BB3503D5C17CC04AF11F530153E04A520BE5EE483799E7532BC68572055AA929B6996D5DE9FBDB70289821368099E2DCA13039EF66ED7DCFCD578C08327AB6F4ED896815740289F0C40999A02DC6F51A03D9FD44D5FE062F80DA067FC9700C1F82BE5F0EC5E26"
    }
}
