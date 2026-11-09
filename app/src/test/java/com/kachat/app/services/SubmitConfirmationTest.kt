package com.kachat.app.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A submit the network accepted is never reported as failed (iOS ad29da9, audit IOS-014). */
class SubmitConfirmationTest {

    private val txId = "ab".repeat(32)

    @Test
    fun `a successful submit carrying the local id returns it without any lookup`() = runBlocking {
        var lookups = 0
        var answered = 0
        val id = SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { lookups++; true }, onAnswered = { answered++ }) { txId.uppercase() }
        assertEquals(txId.uppercase(), id)
        assertEquals(0, lookups)
        assertEquals(1, answered)
    }

    @Test
    fun `an answer with another id only counts if the network has the local one`() = runBlocking {
        var foreign: String? = null
        var answered = 0
        val id = SubmitConfirmation.submitOrConfirmKnown(
            txId,
            isKnown = { it == txId },
            onForeignAnswer = { foreign = it },
            onAnswered = { answered++ },
        ) { "cd".repeat(32) }
        assertEquals(txId, id)
        assertEquals("cd".repeat(32), foreign)
        assertEquals(0, answered)
    }

    @Test
    fun `an answer with another id the network never sees has an unknown outcome`() = runBlocking {
        try {
            SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { false }, checkDelayMs = 0) { "cd".repeat(32) }
            fail("expected an unknown outcome")
        } catch (e: SendOutcomeUnknownException) {
            assertEquals(txId, e.txId)
        }
    }

    @Test
    fun `a lost answer keeps asking and finds the transaction`() = runBlocking {
        var lookups = 0
        val id = SubmitConfirmation.submitOrConfirmKnown(
            txId,
            isKnown = { false },
            lookup = { ++lookups == 3 },
            checkDelayMs = 0,
        ) { throw java.io.IOException("connection reset") }
        assertEquals(txId, id)
        assertEquals(3, lookups)
    }

    @Test
    fun `a lost answer the network never confirms has an unknown outcome, not a failure`() = runBlocking {
        var lookups = 0
        try {
            SubmitConfirmation.submitOrConfirmKnown(
                txId,
                isKnown = { false },
                lookup = { lookups++; false },
                checkDelayMs = 0,
            ) { throw java.io.IOException("connection reset") }
            fail("expected an unknown outcome")
        } catch (e: SendOutcomeUnknownException) {
            assertEquals(txId, e.txId)
        }
        assertEquals(SubmitConfirmation.LOST_ANSWER_CHECKS, lookups)
    }

    @Test
    fun `a node that could not be connected to never saw the transaction`() {
        assertTrue(!SubmitConfirmation.isLostAnswer(java.net.ConnectException("refused")))
        assertTrue(!SubmitConfirmation.isLostAnswer(java.net.UnknownHostException("no host")))
        assertTrue(!SubmitConfirmation.isLostAnswer(IllegalStateException("fee too low")))
    }

    @Test
    fun `an earlier try's lost answer still counts behind a later rejection`() {
        val rejection = IllegalStateException("transaction is an orphan")
        rejection.addSuppressed(java.io.IOException("connection reset"))
        assertTrue(SubmitConfirmation.isLostAnswer(rejection))
    }

    @Test
    fun `an already-in-mempool rejection of a transaction the network has is a sent transaction`() = runBlocking {
        var recovered: Exception? = null
        val id = SubmitConfirmation.submitOrConfirmKnown(
            txId,
            isKnown = { it == txId },
            onRecovered = { recovered = it },
        ) { throw IllegalStateException("transaction $txId is already in the mempool") }
        assertEquals(txId, id)
        assertTrue(recovered is IllegalStateException)
    }

    @Test
    fun `a timeout followed by a successful re-check is a sent transaction`() = runBlocking {
        val id = SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { true }) {
            withTimeout(1) { kotlinx.coroutines.delay(1_000); "never" }
        }
        assertEquals(txId, id)
    }

    @Test
    fun `a rejection of a transaction the network does not have is still a failure`() = runBlocking {
        val error = IllegalStateException("fee too low")
        try {
            SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { false }) { throw error }
            fail("expected the submit's own error")
        } catch (e: IllegalStateException) {
            assertSame(error, e)
        }
    }

    @Test
    fun `without a local id there is no lookup and the error stands`() = runBlocking {
        var lookups = 0
        try {
            SubmitConfirmation.submitOrConfirmKnown("", isKnown = { lookups++; true }) { throw IllegalStateException("x") }
            fail("expected the error")
        } catch (e: IllegalStateException) {
            assertEquals(0, lookups)
        }
    }

    @Test
    fun `a cancelled send stays cancelled`() = runBlocking {
        var lookups = 0
        try {
            SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { lookups++; true }) { throw CancellationException("gone") }
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertEquals(0, lookups)
        }
    }

    @Test
    fun `the lookup asks the mempool then the REST API, twice`() = runBlocking {
        val asked = mutableListOf<String>()
        val known = SubmitConfirmation.isKnown(
            txId,
            inMempool = { asked += "mempool"; false },
            acceptedViaRest = { asked += "rest"; false },
            delayMs = 0,
        )
        assertFalse(known)
        assertEquals(listOf("mempool", "rest", "mempool", "rest"), asked)
    }

    @Test
    fun `a transaction that shows up on the second look is known`() = runBlocking {
        var looks = 0
        val known = SubmitConfirmation.isKnown(
            txId,
            inMempool = { looks++; looks > 1 },
            acceptedViaRest = { false },
            delayMs = 0,
        )
        assertTrue(known)
    }

    @Test
    fun `accepted via REST alone is known`() = runBlocking {
        assertTrue(SubmitConfirmation.isKnown(txId, inMempool = { false }, acceptedViaRest = { true }, delayMs = 0))
    }

    // --- A send that landed never ends as a failure (audit: lost answer, then a node's verdict) ---

    /** A retry's rejection with the first try's lost answer kept on it, as KaspaWalletEngine throws. */
    private fun retryRejection(message: String) = NodeRejectedTransactionException(message).apply {
        addSuppressed(java.io.IOException("deadline exceeded"))
    }

    @Test
    fun `after a lost answer, the retry hearing already-in-the-mempool for our own id is sent without a lookup`() = runBlocking {
        var lookups = 0
        var recovered: Exception? = null
        val id = SubmitConfirmation.submitOrConfirmKnown(
            txId,
            isKnown = { lookups++; false },
            onRecovered = { recovered = it },
            lookup = { lookups++; false },
            checkDelayMs = 0,
        ) { throw retryRejection("Rejected transaction $txId: transaction $txId is already in the mempool") }
        assertEquals(txId, id)
        assertEquals(0, lookups)
        assertTrue(recovered is NodeRejectedTransactionException)
    }

    @Test
    fun `already accepted, or an input already spent by our own transaction, is sent`() {
        assertTrue(SubmitConfirmation.isOwnDuplicateRejection(
            IllegalStateException("Rejected transaction $txId: transaction $txId was already accepted by the consensus"), txId))
        assertTrue(SubmitConfirmation.isOwnDuplicateRejection(
            IllegalStateException("output (${"cd".repeat(32)}: 0) already spent by transaction ${txId.uppercase()} in the memory pool"), txId))
        // kept as a suppressed error of a later one
        val later = IllegalStateException("timeout")
        later.addSuppressed(IllegalStateException("transaction $txId is already in the mempool"))
        assertTrue(SubmitConfirmation.isOwnDuplicateRejection(later, txId))
    }

    @Test
    fun `a double spend by another transaction is not sent, whatever prefix names ours`() {
        val other = "ef".repeat(32)
        val e = IllegalStateException("Rejected transaction $txId: output (${"cd".repeat(32)}: 0) already spent by transaction $other in the memory pool")
        assertFalse(SubmitConfirmation.isOwnDuplicateRejection(e, txId))
        assertFalse(SubmitConfirmation.isOwnDuplicateRejection(IllegalStateException("transaction $other is already in the mempool"), txId))
        assertFalse(SubmitConfirmation.isOwnDuplicateRejection(IllegalStateException("transaction $txId is already in the mempool"), ""))
    }

    @Test
    fun `an orphan-pool hold is neither sent nor a failure`() = runBlocking {
        val held = IllegalStateException("Rejected transaction $txId: orphan transaction $txId is already in the orphan pool")
        assertFalse(SubmitConfirmation.isOwnDuplicateRejection(held, txId))
        assertTrue(SubmitConfirmation.isOwnOrphanDuplicate(held, txId))
        try {
            SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { false }) { throw held }
            fail("expected an unknown outcome")
        } catch (e: SendOutcomeUnknownException) {
            assertEquals(txId, e.txId)
        }
    }

    @Test
    fun `after a lost answer, any other rejection ends Not confirmed, not a failure`() = runBlocking {
        var lookups = 0
        try {
            SubmitConfirmation.submitOrConfirmKnown(
                txId,
                isKnown = { false },
                lookup = { lookups++; false },
                checkDelayMs = 0,
            ) { throw retryRejection("Rejected transaction $txId: fee too low") }
            fail("expected an unknown outcome")
        } catch (e: SendOutcomeUnknownException) {
            assertEquals(txId, e.txId)
        }
        assertEquals(SubmitConfirmation.LOST_ANSWER_CHECKS, lookups)
    }

    @Test
    fun `a clean rejection a node holds only as an orphan is an unknown outcome`() = runBlocking {
        try {
            SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { false }, heldAsOrphan = { it == txId }) {
                throw NodeRejectedTransactionException("Rejected transaction $txId: fee too low")
            }
            fail("expected an unknown outcome")
        } catch (e: SendOutcomeUnknownException) {
            assertEquals(txId, e.txId)
        }
    }

    @Test
    fun `a clean rejection nobody holds is still a plain failure`() = runBlocking {
        val error = NodeRejectedTransactionException("Rejected transaction $txId: fee too low")
        try {
            SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { false }, heldAsOrphan = { false }) { throw error }
            fail("expected the submit's own error")
        } catch (e: NodeRejectedTransactionException) {
            assertSame(error, e)
        }
    }

    // After a rejection only a mempool or the built-in REST server calls it sent (iOS 1652437)

    @Test
    fun `after a node's rejection a REST server typed into Settings doesn't make it sent`() = runBlocking {
        val error = NodeRejectedTransactionException("Rejected transaction $txId: fee too low")
        try {
            SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { true }, isKnownAfterRejection = { false }) { throw error }
            fail("expected the submit's own error")
        } catch (e: NodeRejectedTransactionException) {
            assertSame(error, e)
        }
    }

    @Test
    fun `after a rejection the built-in check can still find it sent`() = runBlocking {
        val id = SubmitConfirmation.submitOrConfirmKnown(txId, isKnown = { false }, isKnownAfterRejection = { it == txId }) {
            throw NodeRejectedTransactionException("Rejected transaction $txId: fee too low")
        }
        assertEquals(txId, id)
    }

    @Test
    fun `a lost answer still asks the full check, any REST server included`() = runBlocking {
        var afterRejection = 0
        val id = SubmitConfirmation.submitOrConfirmKnown(
            txId,
            isKnown = { it == txId },
            isKnownAfterRejection = { afterRejection++; false },
        ) { throw java.io.IOException("timeout") }
        assertEquals(txId, id)
        assertEquals(0, afterRejection)
    }

    @Test
    fun `a lost answer survives a later orphan or already reply, using the full check`() = runBlocking {
        val rejection = NodeRejectedTransactionException("transaction $txId is an orphan where orphan is disallowed")
        rejection.addSuppressed(java.io.IOException("first try timed out"))
        var afterRejection = 0
        try {
            SubmitConfirmation.submitOrConfirmKnown(
                txId,
                isKnown = { false },
                isKnownAfterRejection = { afterRejection++; false },
                lookup = { false },
                checkDelayMs = 0,
            ) { throw rejection }
            fail("expected an unknown outcome")
        } catch (e: SendOutcomeUnknownException) {
            assertEquals(txId, e.txId)
        }
        assertEquals(0, afterRejection)
    }

    @Test
    fun `the built-in REST server is matched case and trailing slash aside`() {
        assertTrue(SubmitConfirmation.isSameRestServer("https://API.kaspa.org/", "https://api.kaspa.org"))
        assertFalse(SubmitConfirmation.isSameRestServer("https://my-rest.example", "https://api.kaspa.org"))
        assertFalse(SubmitConfirmation.isSameRestServer("", ""))
    }

    // A send rebuilt at the swipe never pays more than the Review showed (iOS 1652437)

    @Test
    fun `a rebuilt send within the margin of the Review's fee goes out`() {
        SendFeeCheck.check(listOf(100_000_000L), listOf(99_990_000L), expectedFeeSompi = 10_000L)
        SendFeeCheck.check(listOf(100_000_000L), listOf(99_989_000L), expectedFeeSompi = 10_000L)
        // no Review: nothing to check
        SendFeeCheck.check(listOf(100_000_000L), listOf(1L), expectedFeeSompi = null)
    }

    @Test
    fun `a rebuilt send paying more than the Review showed sends nothing`() {
        try {
            // 10,000 shown; the coins changed and 9,000 sompi of change is now folded in
            SendFeeCheck.check(listOf(50_000_000L, 50_000_000L), listOf(99_981_000L), expectedFeeSompi = 10_000L)
            fail("expected the fee change to stop the send")
        } catch (e: SendFeeChangedException) {
            assertEquals(19_000L, e.feeSompi)
            assertTrue(e.message!!.contains("0.00019"))
        }
    }
}

