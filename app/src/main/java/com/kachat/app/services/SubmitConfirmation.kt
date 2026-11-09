package com.kachat.app.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * A send a node may have received without ever answering, which the network doesn't have yet (iOS
 * 953b255 `SendOutcomeUnknownError`). It may still land, so nothing should offer to send it again:
 * the send screen shows "Not confirmed" with this message and only Done (`SendProgressScreen`).
 */
class SendOutcomeUnknownException(val txId: String, cause: Exception) : IllegalStateException(MESSAGE, cause) {
    companion object {
        const val MESSAGE = "This may have gone through, but the network didn't confirm it. Check your balance before sending again."
    }
}

/**
 * A node answered a submit by rejecting the transaction (bad fee, spent input, orphan, already
 * accepted...), iOS `NodeRejectedTransactionError`, with the node's own words. A node that says it
 * already has this very transaction ([SubmitConfirmation.isOwnDuplicateRejection]) means it was
 * sent. Any other rejection after an earlier try lost its answer keeps that lost answer: the first
 * try may still have landed, so the send ends "Not confirmed", never a failure that invites Try
 * Again (stricter than iOS 0799cec's verdict rule, which money can't afford to get wrong).
 */
class NodeRejectedTransactionException(message: String) : IllegalStateException(message)

/**
 * A send rebuilt at the swipe, from that moment's coins, would pay more than the Review showed
 * (by more than [SendFeeCheck.REVIEW_FEE_MARGIN_SOMPI]): nothing was built for the network, nothing
 * sent (iOS 1652437 `SendFeeChangedError`). The Send screen works the fee out again.
 */
class SendFeeChangedException(val feeSompi: Long) : IllegalStateException(
    com.kachat.app.util.KaspaUnit.label(
        "The network fee is now ${String.format(java.util.Locale.US, "%.8f", feeSompi / 100_000_000.0).trimEnd('0').trimEnd('.')} KAS, " +
            "more than the Review showed. Nothing was sent: check the new fee and swipe again."
    )
)

/** The swipe's fee check (iOS 1652437 `checkFeeAgainstReview`). Pure, so it is unit-tested. */
object SendFeeCheck {
    /** Within this of the Review's fee a rebuilt send still goes out (iOS `reviewFeeMarginSompi`). */
    const val REVIEW_FEE_MARGIN_SOMPI = 1_000L

    /**
     * Throws [SendFeeChangedException] when a transaction spending [inputAmounts] and paying
     * [outputAmounts] pays more than [expectedFeeSompi] plus the margin. Null [expectedFeeSompi]
     * (a send with no Review) checks nothing. What it pays is everything the inputs hold that no
     * output carries: the fee and any change folded into it.
     */
    fun check(inputAmounts: List<Long>, outputAmounts: List<Long>, expectedFeeSompi: Long?) {
        if (expectedFeeSompi == null) return
        val inTotal = com.kachat.app.util.UtxoMath.checkedTotal(inputAmounts) ?: return
        val outTotal = com.kachat.app.util.UtxoMath.checkedTotal(outputAmounts) ?: return
        if (inTotal < outTotal) return
        val fee = inTotal - outTotal
        if (fee > expectedFeeSompi + REVIEW_FEE_MARGIN_SOMPI) throw SendFeeChangedException(fee)
    }
}

/**
 * A failed submit is not always a failed send (iOS ad29da9, audit IOS-014).
 *
 * A node can accept a transaction while its answer times out; the retry that follows (a fresh
 * connection, an allowOrphan resubmit, the REST gateway) then gets "already in the mempool",
 * "already accepted", an orphan or a double-spend verdict for the very same transaction.
 * Reporting that as a failure invites a resend that pays twice. So before a submit error is
 * passed on, the transaction's own id - computed locally, before anything was sent - is looked
 * up: found in a mempool or accepted, the send succeeded.
 *
 * Pure apart from the lookups it is handed, so the decision is unit-tested.
 */
object SubmitConfirmation {

    /** Between the two lookups: a just-accepted transaction may not have propagated yet. */
    const val RECHECK_DELAY_MS = 1_500L

    /** After a lost answer, how many more times the network is asked, and how far apart (iOS
     *  953b255 `confirmAfterLostAnswer`). */
    const val LOST_ANSWER_CHECKS = 6
    const val LOST_ANSWER_CHECK_DELAY_MS = 3_000L

    /** After a node's answer, how often and how far apart the network is asked whether it can see
     *  the transaction, sending it again if not (iOS 953b255 `checkedSubmit`). */
    const val RESEND_CHECKS = 2
    const val RESEND_DELAY_MS = 5_000L

    /** How long a send rejected as an orphan gives its parent to reach the node (iOS 4eb492f). */
    const val PARENT_WAIT_MS = 1_500L

    /** Whether a submit error is the node's "orphan where orphan is disallowed" (iOS
     *  `NodePoolService.isOrphanRejection`). */
    fun isOrphanRejection(e: Throwable): Boolean = (e.message ?: e.toString()).lowercase().contains("orphan")

    /**
     * Runs [submit] with [allowOrphan] (iOS 4eb492f, NodePoolService.submitTransaction). A
     * rejection as an orphan "where orphan is disallowed" means the transaction's inputs came from
     * a node that already has their parent transaction (the UTXO query), but the node this submit
     * reached hasn't caught up yet - a reaction or message sent right after the previous one. Then
     * the parent gets [delayMs] to propagate and the submit runs once more; if that is refused too,
     * the node is asked to hold it as an orphan until the parent arrives ([onHeldAsOrphan] is told
     * the id). When neither goes through, the first rejection is what is thrown, as before, with
     * the later tries' errors as suppressed (a lost answer among them still counts, iOS 953b255,
     * [isLostAnswer]). Any other error, or a submit that already allowed orphans, is thrown at once.
     * A cancelled send stays cancelled (a timeout is not a cancellation of the send).
     * [escalateOrphan] false leaves out the orphan hold, for a send that moves money and must not
     * sit in a node's orphan pool (iOS 2da5f4b).
     */
    suspend fun submitWaitingForParent(
        allowOrphan: Boolean,
        delayMs: Long = PARENT_WAIT_MS,
        escalateOrphan: Boolean = true,
        onHeldAsOrphan: (String) -> Unit = {},
        submit: suspend (allowOrphan: Boolean) -> String,
    ): String {
        try {
            return submit(allowOrphan)
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            if (allowOrphan || !isOrphanRejection(e)) throw e
            delay(delayMs)
            attempt(e) { submit(false) }?.let { return it }
            if (escalateOrphan) attempt(e) { submit(true) }?.let { id ->
                onHeldAsOrphan(id)
                return id
            }
            throw e
        }
    }

    /** [block]'s id, or null when it failed (its error kept on [first]); a cancelled send stays
     *  cancelled. */
    private suspend fun attempt(first: Exception, block: suspend () -> String): String? = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        currentCoroutineContext().ensureActive()
        first.addSuppressed(e)
        null
    }

    /**
     * Runs [submit]. On any error, [txId] is looked up with [isKnown]; if the network has it,
     * [txId] is returned as the sent transaction's id and [onRecovered] is told why, otherwise
     * the submit's own error is thrown. A cancelled send stays cancelled (a timeout is not a
     * cancellation of the send).
     *
     * When the error is a lost answer rather than the node's verdict ([isLostAnswer]), [lookup]
     * asks on for longer, and a transaction still not found ends in [SendOutcomeUnknownException]
     * rather than a failure: it may yet land (iOS 953b255 `confirmAfterLostAnswer`). A node saying
     * it already has [txId] ([isOwnDuplicateRejection]) is a sent transaction at once; one holding
     * it only as an orphan ([heldAsOrphan], [isOwnOrphanDuplicate]) is an unknown outcome.
     *
     * Nodes are unauthenticated, so a "sent" answer is only a claim: its id has to be [txId], and
     * any other answer is treated as no answer ([onForeignAnswer] is told it). [onAnswered] runs
     * once a node's answer did carry [txId] (iOS 953b255 `checkedSubmit`). Without a local id
     * ([txId] empty) the submit runs exactly as before, unchecked.
     */
    suspend fun submitOrConfirmKnown(
        txId: String,
        isKnown: suspend (String) -> Boolean,
        onRecovered: (Exception) -> Unit = {},
        onForeignAnswer: (String) -> Unit = {},
        onAnswered: () -> Unit = {},
        lookup: suspend (String) -> Boolean = isKnown,
        checkDelayMs: Long = LOST_ANSWER_CHECK_DELAY_MS,
        /** Whether some node holds [txId] in its orphan pool only. Not a sent transaction (it lands
         *  only if its parent does), but not a failure either: after a rejection it ends in
         *  [SendOutcomeUnknownException], since a resend could pay twice. */
        heldAsOrphan: suspend (String) -> Boolean = { false },
        /** [isKnown] after a node's rejection (no lost answer, [isLostAnswer]): a node turned the
         *  transaction down, so only a mempool or the built-in REST server may call it sent, never
         *  a REST server typed into Settings (iOS 1652437 `isTransactionKnown(afterRejection:)`). */
        isKnownAfterRejection: suspend (String) -> Boolean = isKnown,
        submit: suspend () -> String,
    ): String {
        val answer = try {
            submit()
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            currentCoroutineContext().ensureActive()
            // A node that already has this very transaction (its id in the node's words): sent.
            if (isOwnDuplicateRejection(e, txId)) {
                onRecovered(e)
                return txId
            }
            val knownCheck = if (isLostAnswer(e)) isKnown else isKnownAfterRejection
            if (txId.isNotEmpty() && asked(knownCheck, txId)) {
                onRecovered(e)
                return txId
            }
            if (txId.isEmpty()) throw e
            // Any earlier try that lost its answer stays lost through a later rejection.
            if (isLostAnswer(e)) return confirmAfterLostAnswer(txId, e, lookup, checkDelayMs)
            if (isOwnOrphanDuplicate(e, txId) || asked(heldAsOrphan, txId)) throw SendOutcomeUnknownException(txId, e)
            throw e
        }
        if (txId.isEmpty()) return answer
        if (!answer.equals(txId, ignoreCase = true)) {
            onForeignAnswer(answer)
            if (asked(isKnown, txId)) return txId
            return confirmAfterLostAnswer(
                txId,
                java.io.IOException("A node answered a submit with a different transaction id"),
                lookup,
                checkDelayMs,
            )
        }
        onAnswered()
        return answer
    }

    /** A node may have the transaction and its answer never came, so a resend could pay twice:
     *  asks the network for longer, then reports the outcome as unknown. */
    private suspend fun confirmAfterLostAnswer(
        txId: String,
        cause: Exception,
        lookup: suspend (String) -> Boolean,
        checkDelayMs: Long,
    ): String {
        repeat(LOST_ANSWER_CHECKS) {
            delay(checkDelayMs)
            if (asked(lookup, txId)) return txId
        }
        throw SendOutcomeUnknownException(txId, cause)
    }

    /** A lookup that fails says nothing about the transaction: never a verdict on the send. */
    private suspend fun asked(lookup: suspend (String) -> Boolean, txId: String): Boolean = try {
        lookup(txId)
    } catch (e: Exception) {
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        currentCoroutineContext().ensureActive()
        false
    }

    /**
     * Whether a submit may have reached a node and no answer came back (iOS 953b255
     * `SubmitAnswerLostError`), as opposed to the node's own refusal, or a connection that never
     * opened and so can't have sent anything. Any earlier try of the same transaction that lost
     * its answer counts: they are kept as suppressed errors, or as the cause of a reworded one.
     */
    fun isLostAnswer(e: Throwable): Boolean {
        if (e.suppressedExceptions.any { isLostAnswer(it) }) return true
        return when (e) {
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException,
            is javax.net.ssl.SSLHandshakeException -> false
            // gRPC's UNAVAILABLE wraps the connect failures above.
            is io.grpc.StatusException, is io.grpc.StatusRuntimeException -> e.cause?.let { neverConnected(it) } != true
            is TimeoutCancellationException, is java.io.IOException -> true
            is retrofit2.HttpException -> e.code() >= 500
            else -> e.cause?.let { isLostAnswer(it) } == true
        }
    }

    /**
     * Whether [e], or an earlier try kept on it (suppressed, or a cause), is a node saying it
     * already has [txId] itself - kaspad's "transaction <id> is already in the mempool",
     * "transaction <id> was already accepted by the consensus", or "output ... already spent by
     * transaction <id> in the memory pool" (and the Go node's "already have transaction <id>"). The
     * id has to be in the sentence where the node names the transaction it holds: rusty-kaspa
     * prefixes every rejection with "Rejected transaction <id>:", and a double spend by ANOTHER
     * transaction must not read as sent. An orphan-pool hold never counts ([isOwnOrphanDuplicate]).
     */
    fun isOwnDuplicateRejection(e: Throwable, txId: String): Boolean {
        if (txId.isEmpty()) return false
        val id = txId.lowercase()
        return errorChain(e).any { t ->
            val m = t.message?.lowercase() ?: return@any false
            !m.contains("orphan") && (
                m.contains("transaction $id is already in the mempool") ||
                    m.contains("transaction $id was already accepted") ||
                    m.contains("already spent by transaction $id") ||
                    m.contains("already have transaction $id")
                )
        }
    }

    /** Whether [e] (or an earlier try kept on it) is a node saying it holds [txId] in its orphan
     *  pool ("orphan transaction <id> is already in the orphan pool"): it may land once its parent
     *  does, so it is neither sent nor failed. */
    fun isOwnOrphanDuplicate(e: Throwable, txId: String): Boolean {
        if (txId.isEmpty()) return false
        val id = txId.lowercase()
        return errorChain(e).any { t ->
            val m = t.message?.lowercase() ?: return@any false
            m.contains("transaction $id is already in the orphan pool")
        }
    }

    /** [e], its suppressed errors and causes, each once (bounded). */
    private fun errorChain(e: Throwable): List<Throwable> {
        val out = mutableListOf<Throwable>()
        val queue = ArrayDeque<Throwable>().apply { add(e) }
        while (queue.isNotEmpty() && out.size < 32) {
            val next = queue.removeFirst()
            if (out.any { it === next }) continue
            out += next
            next.cause?.let { queue.addLast(it) }
            next.suppressedExceptions.forEach { queue.addLast(it) }
        }
        return out
    }

    private fun neverConnected(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(8).any {
            it is java.net.UnknownHostException || it is java.net.ConnectException ||
                it is java.net.NoRouteToHostException || it is javax.net.ssl.SSLHandshakeException
        }

    /** Whether two REST base URLs name the same server: case and a trailing slash aside (iOS
     *  1652437 `isAcceptedViaREST`'s built-in check). */
    fun isSameRestServer(a: String, b: String): Boolean {
        fun norm(url: String) = url.trim().removeSuffix("/").lowercase()
        return norm(a).isNotEmpty() && norm(a) == norm(b)
    }

    /**
     * Whether the network has [txId]: in some node's mempool ([inMempool]), or accepted
     * ([acceptedViaRest]). Asked twice, [delayMs] apart.
     */
    suspend fun isKnown(
        txId: String,
        inMempool: suspend (String) -> Boolean,
        acceptedViaRest: suspend (String) -> Boolean,
        delayMs: Long = RECHECK_DELAY_MS,
    ): Boolean {
        for (attempt in 0 until 2) {
            if (attempt > 0) delay(delayMs)
            if (inMempool(txId)) return true
            if (acceptedViaRest(txId)) return true
        }
        return false
    }
}
