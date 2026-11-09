package com.kachat.app.services

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.Gson
import com.kachat.app.models.PortfolioEntity
import com.kachat.app.models.PortfolioFeeRecord
import com.kachat.app.models.PortfolioTransactionEntity
import com.kachat.app.services.database.KaChatDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a wallet's portfolios are saved, and what the Nextcloud backup needs from them
 * (iOS `PortfolioLedgerStore`, 11f1548). Every portfolio and ledger-row write goes through here:
 *
 * - a portfolio or row that is new, or changed in anything but its stamp, is stamped
 *   `updatedAtMillis` now - centrally, at save, as iOS's `PortfolioLedgerStore.stamped`;
 * - a deleted portfolio or row is recorded as a tombstone (per wallet), so a merge or restore
 *   never brings it back; a deleted portfolio takes its rows and fees with it;
 * - any change marks the backup to upload ([NextcloudSyncService.notePortfolioChanged], iOS 953b255
 *   `NextcloudService.notePortfolioChanged`).
 *
 * The seed "Portfolio 1" and a restore's merged result are written as they are, unstamped and
 * without marking anything ([insertSeed], [importFromArchive]), as on iOS.
 *
 * Also holds the per-wallet fee list (network fees an imported address paid, iOS 61eff0f): one
 * JSON list per wallet in SharedPreferences, the keys unchanged from when PortfolioRepository kept it.
 */
@Singleton
class PortfolioLedgerStore @Inject constructor(
    @ApplicationContext context: Context,
    private val database: KaChatDatabase,
    // Lazy: NextcloudSyncService reads and merges the portfolios through this class.
    private val nextcloudSyncService: dagger.Lazy<NextcloudSyncService>,
) {
    private val gson = Gson()

    /** Every portfolio and row write, one at a time: a Nextcloud merge ([importFromArchive]) reads,
     *  merges and writes back, and an edit saved in between would otherwise be written over. */
    private val writeMutex = Mutex()

    // -------------------------------------------------------------------------
    // Portfolios
    // -------------------------------------------------------------------------

    /** Saves [rows]: each one new or changed (anything but its stamp) is stamped now. */
    suspend fun savePortfolios(rows: List<PortfolioEntity>) = writeMutex.withLock {
        if (rows.isEmpty()) return@withLock
        val dao = database.portfolioDefinitionDao()
        val now = System.currentTimeMillis()
        val changed = rows.mapNotNull { row ->
            val stored = dao.getById(row.id)
            if (stored != null && stored.copy(updatedAtMillis = null) == row.copy(updatedAtMillis = null)) null
            else row.copy(updatedAtMillis = now)
        }
        if (changed.isEmpty()) return@withLock
        dao.insertAll(changed)
        noteChanged()
    }

    /** A wallet's first "Portfolio 1": never stamped, so it stays a seed until it is touched. */
    suspend fun insertSeed(seed: PortfolioEntity) = writeMutex.withLock {
        database.portfolioDefinitionDao().insert(seed.copy(updatedAtMillis = null))
    }

    /**
     * Deletes portfolio [id] of [walletAddress] with its rows and fees, recording the portfolio
     * and each row as deleted (iOS deletePortfolio + forgetPortfolio).
     */
    suspend fun deletePortfolio(walletAddress: String, id: String) = writeMutex.withLock {
        val rows = database.portfolioDao().getAllTransactionsForWalletOnce(walletAddress).filter { it.portfolioId == id }
        database.portfolioDefinitionDao().delete(id)
        database.portfolioDao().deleteAllForPortfolio(id)
        val now = System.currentTimeMillis()
        recordDeletions(
            walletAddress,
            listOfNotNull(PortfolioSync.canonicalPortfolioId(id)?.let { PortfolioTombstone(PortfolioSync.KIND_PORTFOLIO, it, now) }) +
                rows.map { PortfolioTombstone(PortfolioSync.KIND_TRANSACTION, it.id, now) }
        )
        synchronized(feeLock) {
            val fees = loadFees(walletAddress)
            val kept = fees.filter { it.portfolioId != id }
            if (kept.size != fees.size) saveFeesRaw(walletAddress, kept)
        }
        noteChanged()
    }

    // -------------------------------------------------------------------------
    // Ledger rows
    // -------------------------------------------------------------------------

    suspend fun saveTransaction(row: PortfolioTransactionEntity) = saveTransactions(listOf(row))

    /** Saves [rows]: each one new or changed (anything but its stamp) is stamped now. */
    suspend fun saveTransactions(rows: List<PortfolioTransactionEntity>) = writeMutex.withLock {
        if (rows.isEmpty()) return@withLock
        val dao = database.portfolioDao()
        val now = System.currentTimeMillis()
        val changed = rows.mapNotNull { row ->
            val stored = dao.getById(row.id)
            if (stored != null && stored.copy(updatedAtMillis = null) == row.copy(updatedAtMillis = null)) null
            else row.copy(updatedAtMillis = now)
        }
        if (changed.isEmpty()) return@withLock
        dao.insertAll(changed)
        noteChanged()
    }

    /** Deletes [ids] of [walletAddress]'s ledger, recording each as deleted. */
    suspend fun deleteTransactions(walletAddress: String, ids: Collection<String>) = writeMutex.withLock {
        if (ids.isEmpty()) return@withLock
        val dao = database.portfolioDao()
        ids.toList().chunked(DELETE_CHUNK).forEach { dao.deleteByIds(it) }
        val now = System.currentTimeMillis()
        recordDeletions(walletAddress, ids.map { PortfolioTombstone(PortfolioSync.KIND_TRANSACTION, it, now) })
        noteChanged()
    }

    // -------------------------------------------------------------------------
    // Fees
    // -------------------------------------------------------------------------

    private val feePrefs = context.getSharedPreferences("kachat_portfolio_fees", Context.MODE_PRIVATE)
    private val feeLock = Any()
    private val _feeVersion = MutableStateFlow(0)
    /** Bumped on every fee save, so a reader of [loadFees] re-reads. */
    val feeVersion: StateFlow<Int> = _feeVersion.asStateFlow()

    private fun feesKey(walletAddress: String) = "fees_" + walletAddress.replace(":", "_")

    fun loadFees(walletAddress: String): List<PortfolioFeeRecord> {
        if (walletAddress.isEmpty()) return emptyList()
        val json = feePrefs.getString(feesKey(walletAddress), null) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<PortfolioFeeRecord>::class.java)?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFeesRaw(walletAddress: String, fees: List<PortfolioFeeRecord>) {
        if (walletAddress.isEmpty()) return
        feePrefs.edit().putString(feesKey(walletAddress), gson.toJson(fees)).apply()
        _feeVersion.value = _feeVersion.value + 1
    }

    /** Rewrites [walletAddress]'s fees; a change marks the backup dirty (iOS persistFees). */
    fun updateFees(walletAddress: String, transform: (List<PortfolioFeeRecord>) -> List<PortfolioFeeRecord>) {
        val changed = synchronized(feeLock) {
            val before = loadFees(walletAddress)
            val after = transform(before)
            if (after == before) false else { saveFeesRaw(walletAddress, after); true }
        }
        if (changed) noteChanged()
    }

    // -------------------------------------------------------------------------
    // Tombstones (NEXTCLOUD_SYNC.md section 5, Portfolios)
    // -------------------------------------------------------------------------

    private val tombstonePrefs = context.getSharedPreferences("kachat_portfolio_tombstones", Context.MODE_PRIVATE)
    private val tombstoneLock = Any()

    private fun tombstonesKey(walletAddress: String) = "tombstones_" + walletAddress.replace(":", "_")

    fun loadTombstones(walletAddress: String): List<PortfolioTombstone> {
        if (walletAddress.isEmpty()) return emptyList()
        val json = tombstonePrefs.getString(tombstonesKey(walletAddress), null) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<PortfolioTombstone>::class.java)?.toList().orEmpty()
                .filter { !it.kind.isNullOrEmpty() && !it.id.isNullOrEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveTombstones(walletAddress: String, tombstones: List<PortfolioTombstone>) {
        if (walletAddress.isEmpty()) return
        tombstonePrefs.edit().putString(tombstonesKey(walletAddress), gson.toJson(tombstones)).apply()
    }

    /** Adds deletions to [walletAddress]'s tombstones (the newest per item kept). */
    private fun recordDeletions(walletAddress: String, new: List<PortfolioTombstone>) {
        if (new.isEmpty()) return
        synchronized(tombstoneLock) {
            saveTombstones(walletAddress, PortfolioSync.mergeTombstones(listOf(loadTombstones(walletAddress), new)))
        }
    }

    // -------------------------------------------------------------------------
    // Nextcloud backup (one file per portfolio, iOS 953b255)
    // -------------------------------------------------------------------------

    /**
     * [walletAddress]'s portfolios as the backup carries them (before [PortfolioSync.forArchive]).
     * Rows the sync can't carry - a pre-portfolio row not yet claimed by a portfolio - are left
     * out here and left alone by [importFromArchive].
     */
    suspend fun syncState(walletAddress: String): PortfolioSync {
        if (walletAddress.isEmpty()) return PortfolioSync()
        return PortfolioSync(
            portfolios = database.portfolioDefinitionDao().getPortfoliosOnce(walletAddress).mapNotNull { PortfolioSync.of(it) },
            transactions = database.portfolioDao().getAllTransactionsForWalletOnce(walletAddress).mapNotNull { PortfolioSync.of(it) },
            fees = loadFees(walletAddress).mapNotNull { PortfolioSync.ofFee(it) },
            tombstones = loadTombstones(walletAddress),
        )
    }

    /**
     * A restore into [walletAddress] (the archive's own wallet, or an unstamped archive's): merges
     * the archive's portfolios into this wallet's - newest edit or deletion wins per item - and
     * writes the result as it is (iOS `PortfolioViewModel.importFromArchive`). An archive that
     * adds nothing writes nothing. A portfolio this device already has keeps its own id spelling,
     * so its rows, fees and the active selection stay attached.
     *
     * Returns true when it wrote the merged result (the caller then resumes the price backfill,
     * as iOS's reload after a restore does), false when there was nothing to change.
     */
    suspend fun importFromArchive(walletAddress: String, archive: PortfolioSync): Boolean = writeMutex.withLock {
        if (walletAddress.isEmpty()) return@withLock false
        // Never another wallet's: a portfolio or row whose id another wallet on this device holds
        // stays with that wallet (Room's REPLACE would otherwise move it to this one).
        val ownOnly = withoutOtherWallets(walletAddress, archive)
        if (ownOnly.isEmpty) return@withLock false
        // Another device's untouched "Portfolio 1" (in a file only to carry a deletion) is never
        // taken: this device has its own, and would show two (iOS 1652437).
        val incoming = ownOnly.copy(
            portfolios = ownOnly.portfolios.filterNot { PortfolioSync.isPristineSeed(it, ownOnly.transactions, ownOnly.fees) },
        )
        val feesAtStart = loadFees(walletAddress)
        val local = syncState(walletAddress)
        var merged = PortfolioSync.merge(listOf(local, incoming))
        // compared in the merge's own order, so an archive that adds nothing writes nothing
        if (merged == PortfolioSync.merge(listOf(local))) return@withLock false

        val defDao = database.portfolioDefinitionDao()
        val rowDao = database.portfolioDao()
        val localPortfolios = defDao.getPortfoliosOnce(walletAddress)
        // Every portfolio here was deleted on another device: back to an untouched one, as a new
        // wallet starts. Refusing the import left the deleted one here, to come back from an edit
        // (iOS 2da5f4b). A portfolio the sync can't carry (no UUID id) stays, so none is added beside it.
        if (merged.portfolios.isEmpty() && localPortfolios.all { PortfolioSync.canonicalPortfolioId(it.id) != null }) {
            merged = merged.copy(
                portfolios = listOf(
                    SyncPortfolio(
                        id = java.util.UUID.randomUUID().toString().uppercase(),
                        name = PortfolioSync.SEED_NAME,
                        sortOrder = 0,
                        createdAtMillis = System.currentTimeMillis(),
                        updatedAtMillis = null,
                    )
                )
            )
        }
        val spelling = localPortfolios.associateBy({ it.id.uppercase() }, { it.id })
        fun localId(canonical: String) = spelling[canonical] ?: canonical

        val keptPortfolioIds = merged.portfolios.map { localId(it.id) }.toSet()
        val goneRowIds = local.transactions.map { it.id }.toSet() - merged.transactions.map { it.id }.toSet()
        database.withTransaction {
            for (p in localPortfolios) {
                if (p.id !in keptPortfolioIds && PortfolioSync.canonicalPortfolioId(p.id) != null) defDao.delete(p.id)
            }
            defDao.insertAll(merged.portfolios.map {
                PortfolioEntity(
                    id = localId(it.id),
                    walletAddress = walletAddress,
                    name = it.name,
                    sortOrder = it.sortOrder,
                    createdAtMillis = it.createdAtMillis,
                    updatedAtMillis = it.updatedAtMillis,
                )
            })
            goneRowIds.toList().chunked(DELETE_CHUNK).forEach { rowDao.deleteByIds(it) }
            rowDao.insertAll(merged.transactions.map {
                PortfolioTransactionEntity(
                    id = it.id,
                    walletAddress = walletAddress,
                    portfolioId = localId(it.portfolioId),
                    type = it.type,
                    amountSompi = it.amountSompi,
                    fiatValue = it.fiatValue,
                    timestampMillis = it.timestampMillis,
                    notes = it.notes,
                    sourceAddress = it.sourceAddress,
                    sourceTxId = it.sourceTxId,
                    updatedAtMillis = it.updatedAtMillis,
                )
            })
        }
        synchronized(feeLock) {
            // A fee saved since the snapshot (fees aren't behind writeMutex) is merged in, not lost.
            val feesNow = loadFees(walletAddress)
            val fees = if (feesNow == feesAtStart) merged.fees else PortfolioSync.merge(listOf(
                merged,
                PortfolioSync(portfolios = merged.portfolios, fees = feesNow.mapNotNull { PortfolioSync.ofFee(it) }, tombstones = merged.tombstones),
            )).fees
            saveFeesRaw(walletAddress, fees.map { it.copy(portfolioId = localId(it.portfolioId)) })
        }
        synchronized(tombstoneLock) { saveTombstones(walletAddress, merged.tombstones) }
        Log.i(TAG, "Merged the backup's portfolios: ${merged.portfolios.size} portfolios, ${merged.transactions.size} rows")
        true
    }

    /**
     * [archive] without what another wallet on this device holds: its portfolios (with their rows
     * and fees) and its rows, by id. Tombstones stay - they only ever delete this wallet's own.
     */
    private suspend fun withoutOtherWallets(walletAddress: String, archive: PortfolioSync): PortfolioSync {
        val (otherPortfolios, otherRows) = idsOfOtherWallets(walletAddress)
        if (otherPortfolios.isEmpty() && otherRows.isEmpty()) return archive
        val kept = archive.copy(
            portfolios = archive.portfolios.filter { it.id.uppercase() !in otherPortfolios },
            transactions = archive.transactions.filter { it.id !in otherRows && it.portfolioId.uppercase() !in otherPortfolios },
            fees = archive.fees.filter { it.portfolioId.uppercase() !in otherPortfolios },
        )
        if (kept != archive) Log.w(TAG, "Left out of the merge what another account on this device holds")
        return kept
    }

    /** Portfolio ids (uppercased) and row ids another wallet on this device holds. */
    private suspend fun idsOfOtherWallets(walletAddress: String): Pair<Set<String>, Set<String>> =
        database.portfolioDefinitionDao().idsOfOtherWallets(walletAddress).map { it.uppercase() }.toSet() to
            database.portfolioDao().idsOfOtherWallets(walletAddress).toSet()

    /**
     * Whether [archive] holds anything another wallet on this device already has: the test the
     * ownerless earlier single Portfolio file must pass before it's merged into [walletAddress]
     * (its portfolios are then some other account's, not this one's).
     */
    suspend fun holdsOtherWalletsItems(walletAddress: String, archive: PortfolioSync): Boolean {
        val (otherPortfolios, otherRows) = idsOfOtherWallets(walletAddress)
        return archive.portfolios.any { it.id.uppercase() in otherPortfolios } ||
            archive.transactions.any { it.id in otherRows || it.portfolioId.uppercase() in otherPortfolios }
    }

    /**
     * Delete Account: [walletAddress]'s portfolios, rows, fees and tombstones, from this device
     * (iOS `clearAllLocalData`). Not a user's edit, so nothing is recorded as deleted and no backup
     * is owed: the backup files on Nextcloud stay there.
     */
    suspend fun removeAllData(walletAddress: String) = writeMutex.withLock {
        database.withTransaction {
            database.portfolioDao().deleteAllForWallet(walletAddress)
            database.portfolioDefinitionDao().deleteAllForWallet(walletAddress)
        }
        synchronized(feeLock) { feePrefs.edit().remove(feesKey(walletAddress)).apply() }
        _feeVersion.value = _feeVersion.value + 1
        synchronized(tombstoneLock) { tombstonePrefs.edit().remove(tombstonesKey(walletAddress)).apply() }
    }

    /** A portfolio edit: with Automatic Sync on, the backup uploads a few seconds later. */
    private fun noteChanged() {
        runCatching { nextcloudSyncService.get().notePortfolioChanged() }
    }

    companion object {
        private const val TAG = "PortfolioLedgerStore"
        /** Ids per DELETE ... IN (...): well under SQLite's bound-variable limit. */
        private const val DELETE_CHUNK = 500
    }
}
