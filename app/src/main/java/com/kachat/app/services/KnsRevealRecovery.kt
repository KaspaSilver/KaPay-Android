package com.kachat.app.services

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kachat.app.util.KaspaTransactionId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A .kas action is two transactions: the commit locks 2 KAS or so at a script, and the reveal
 * spends it. If the reveal never goes out (the commit's answer was lost, the app was closed, a
 * node refused it) those coins stay at a script nothing else knows about. Each commit is filed
 * here before it is sent and the reveal is tried again when the app becomes active
 * (KaChatApplication).
 *
 * Only for an hour: the reveal performs what the person asked for (a transfer, a profile change),
 * and doing that long after they saw it fail would be a surprise. Nothing secret is filed; the
 * key is read from the wallet at the time (iOS 2da5f4b `KNSRevealRecovery`).
 */
@Singleton
class KnsRevealRecovery @Inject constructor(
    @ApplicationContext context: Context,
    private val walletManager: WalletManager,
    private val nodePoolManager: NodePoolManager,
    /** Lazy: the engine files its commits here. */
    private val engine: dagger.Lazy<KnsInscriptionEngine>,
) {
    private class Pending(
        val commitTxId: String,
        val wallet: String,
        val redeemScriptHex: String,
        val commitAddress: String,
        val commitScriptPubKeyHex: String,
        val commitAmountSompi: Long,
        val revealAmountSompi: Long,
        /** null: the chatting address */
        val ownerSpendingIndex: Int?,
        val changeAddress: String,
        val revealTargetAddress: String,
        val priorityFeeSompi: Long,
        val createdAt: Long,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val storeLock = Any()
    /** Commits whose own send is still running: it does the reveal itself. */
    private val inFlow = ConcurrentHashMap.newKeySet<String>()
    private val resuming = AtomicBoolean(false)

    private fun load(): List<Pending> = synchronized(storeLock) {
        val raw = prefs.getString(KEY, null) ?: return@synchronized emptyList()
        runCatching {
            JsonParser.parseString(raw).asJsonArray.map { element ->
                val o = element.asJsonObject
                Pending(
                    commitTxId = o.get("commitTxId").asString,
                    wallet = o.get("wallet").asString,
                    redeemScriptHex = o.get("redeemScriptHex").asString,
                    commitAddress = o.get("commitAddress").asString,
                    commitScriptPubKeyHex = o.get("commitScriptPubKeyHex").asString,
                    commitAmountSompi = o.get("commitAmountSompi").asLong,
                    revealAmountSompi = o.get("revealAmountSompi").asLong,
                    ownerSpendingIndex = o.get("ownerSpendingIndex")?.takeIf { !it.isJsonNull }?.asInt,
                    changeAddress = o.get("changeAddress").asString,
                    revealTargetAddress = o.get("revealTargetAddress").asString,
                    priorityFeeSompi = o.get("priorityFeeSompi").asLong,
                    createdAt = o.get("createdAt").asLong,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Hand-written JSON, so no field name depends on R8. Written through before it returns: the
     *  commit goes out right after. */
    private fun save(list: List<Pending>) = synchronized(storeLock) {
        if (list.isEmpty()) {
            prefs.edit().remove(KEY).commit()
        } else {
            val array = JsonArray()
            for (p in list) {
                array.add(JsonObject().apply {
                    addProperty("commitTxId", p.commitTxId)
                    addProperty("wallet", p.wallet)
                    addProperty("redeemScriptHex", p.redeemScriptHex)
                    addProperty("commitAddress", p.commitAddress)
                    addProperty("commitScriptPubKeyHex", p.commitScriptPubKeyHex)
                    addProperty("commitAmountSompi", p.commitAmountSompi)
                    addProperty("revealAmountSompi", p.revealAmountSompi)
                    p.ownerSpendingIndex?.let { addProperty("ownerSpendingIndex", it) }
                    addProperty("changeAddress", p.changeAddress)
                    addProperty("revealTargetAddress", p.revealTargetAddress)
                    addProperty("priorityFeeSompi", p.priorityFeeSompi)
                    addProperty("createdAt", p.createdAt)
                })
            }
            prefs.edit().putString(KEY, array.toString()).commit()
        }
    }

    /** Files a commit about to be sent; returns its id, computed here. */
    suspend fun begin(
        commitTx: RawTransaction,
        commitAddress: String,
        redeemScript: ByteArray,
        commitScriptPubKeyHex: String,
        commitAmountSompi: Long,
        revealAmountSompi: Long,
        ownerAddress: String,
        ownerSpendingIndex: Int?,
        changeAddress: String,
        revealTargetAddress: String,
        priorityFeeSompi: Long,
    ): String = withContext(Dispatchers.IO) {
        val id = KaspaTransactionId.compute(commitTx).lowercase()
        inFlow.add(id)
        synchronized(storeLock) {
            save(load().filter { it.commitTxId != id } + Pending(
                commitTxId = id,
                wallet = walletManager.getActiveAccount()?.address ?: ownerAddress,
                redeemScriptHex = redeemScript.joinToString("") { "%02x".format(it) },
                commitAddress = commitAddress,
                commitScriptPubKeyHex = commitScriptPubKeyHex,
                commitAmountSompi = commitAmountSompi,
                revealAmountSompi = revealAmountSompi,
                ownerSpendingIndex = ownerSpendingIndex,
                changeAddress = changeAddress,
                revealTargetAddress = revealTargetAddress,
                priorityFeeSompi = priorityFeeSompi,
                createdAt = System.currentTimeMillis(),
            ))
        }
        id
    }

    /** The commit's own send is over, however it went: anything left is now [resume]'s. */
    fun endFlow(id: String) {
        inFlow.remove(id.lowercase())
    }

    /** The reveal went out. */
    fun finished(id: String) {
        val key = id.lowercase()
        synchronized(storeLock) { save(load().filter { it.commitTxId != key }) }
    }

    /** Sends the reveal of any commit left on chain with none, for the signed-in account. */
    suspend fun resume(): Unit = withContext(Dispatchers.IO) {
        val wallet = walletManager.getActiveAccount()?.address ?: return@withContext
        if (!resuming.compareAndSet(false, true)) return@withContext
        try {
            val now = System.currentTimeMillis()
            // an hour on, the action is dropped (see above)
            synchronized(storeLock) {
                val all = load()
                val kept = all.filter { now - it.createdAt <= MAX_AGE_MS }
                for (p in all - kept.toSet()) Log.i(TAG, "gave up on the reveal of commit ${p.commitTxId.take(12)}")
                if (kept.size != all.size) save(kept)
            }
            for (p in load()) {
                if (p.wallet != wallet || p.commitTxId in inFlow) continue
                try {
                    // not landed yet, or already spent by its reveal: an empty answer is either
                    val utxos = nodePoolManager.getUtxosByAddress(p.commitAddress) ?: continue
                    if (utxos.none { it.outpoint.transactionId.equals(p.commitTxId, ignoreCase = true) && it.outpoint.index == COMMIT_OUTPUT_INDEX }) continue
                    if (walletManager.getActiveAccount()?.address != wallet) continue
                    val key = p.ownerSpendingIndex?.let { walletManager.getSpendingPrivateKeyBytes(it) } ?: walletManager.getPrivateKeyBytes()
                    val commit = KnsInscriptionEngine.CommitResult(
                        commitTxId = p.commitTxId,
                        redeemScript = hexToBytes(p.redeemScriptHex),
                        commitScriptPubKeyHex = p.commitScriptPubKeyHex,
                        commitAmountSompi = p.commitAmountSompi,
                        revealAmountSompi = p.revealAmountSompi,
                    )
                    val txId = engine.get().buildAndSubmitReveal(commit, p.revealTargetAddress, p.changeAddress, key, p.priorityFeeSompi)
                    Log.i(TAG, "sent the reveal of a commit left without one: ${txId.take(12)}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "reveal of commit ${p.commitTxId.take(12)} failed again: ${e.message ?: e}")
                }
            }
        } finally {
            resuming.set(false)
        }
    }

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }

    private companion object {
        const val TAG = "KnsRevealRecovery"
        const val PREFS = "kachat_kns_pending_reveals"
        const val KEY = "pending_reveals_v1"
        const val MAX_AGE_MS = 3_600_000L
        /** The commit's first output is the one the reveal spends (KnsInscriptionEngine). */
        const val COMMIT_OUTPUT_INDEX = 0
    }
}
