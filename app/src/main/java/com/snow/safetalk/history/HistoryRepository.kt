package com.snow.safetalk.history

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow

data class GlobalStats(
    val total: Int = 0,
    val safe: Int = 0,
    val suspicious: Int = 0,
    val danger: Int = 0
)

class HistoryRepository(private val database: HistoryDatabase) {

    private val dao = database.historyDao()

    /**
     * Fetch a paginated list of history items, mapped correctly to AnalysisResultUi.
     * Also returns the total matching item count for UI page bounds calculation.
     */
    suspend fun getHistoryPaginated(
        riskLabel: RiskLabel?,
        sinceTimestamp: Long,
        page: Int,
        pageSize: Int = 10,
        retentionDays: Int
    ): Pair<List<AnalysisResultUi>, Int> {
        return withContext(Dispatchers.IO) {
            triggerRetentionCleanup(retentionDays)
            val offset = page * pageSize
            val entities = dao.getHistoryPaginated(riskLabel, sinceTimestamp, pageSize, offset)
            val totalCount = dao.getHistoryCount(riskLabel, sinceTimestamp)
            entities.map { it.toUiModel() } to totalCount
        }
    }

    /**
     * Inserts a new analysis result, subsequently triggering the configured retention cleanup.
     */
    suspend fun addResult(newResult: AnalysisResultUi, retentionDays: Int) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                dao.upsert(newResult.toEntity())
                triggerRetentionCleanup(retentionDays)
            }
        }
    }

    /**
     * Mark a record as read.
     */
    suspend fun markRead(id: String) {
        withContext(Dispatchers.IO) {
            dao.markRead(id)
        }
    }

    /**
     * Soft delete multiple records, keeping them invisible from history UI
     * but strictly preserving them for the Statistics aggregates.
     */
    suspend fun softDelete(ids: List<String>) {
        withContext(Dispatchers.IO) {
            // SQLite has a parameter limit (usually around 999). 
            // Chunking the list is a safe practice for bulk operations.
            ids.chunked(900).forEach { chunk ->
                dao.softDelete(chunk)
            }
        }
    }

    /**
     * Retrieve single record by ID.
     */
    suspend fun getById(id: String): AnalysisResultUi? {
        return withContext(Dispatchers.IO) {
            dao.getById(id)?.toUiModel()
        }
    }

    /**
     * Deletes rows older than configurable retention days.
     * Hard-deleting rows permanently removes them from both UI and Stats.
     * This is an automated DB protection mechanism decoupled from user history "soft-deletions".
     */
    private suspend fun triggerRetentionCleanup(retentionDays: Int) {
        val retentionMillis = retentionDays * 24L * 60L * 60L * 1000L
        val cutoff = System.currentTimeMillis() - retentionMillis
        dao.trimOlderThan(cutoff)
    }

    /**
     * Retrieve the single newest active record for Home preview.
     */
    fun getLatestResultFlow(): Flow<AnalysisResultUi?> {
        return dao.getLatestResultFlow().map { it?.toUiModel() }
    }

    fun getHistoryCountFlow(): Flow<Int> {
        return dao.getHistoryCountFlow()
    }

    fun getGlobalStatsFlow(): Flow<GlobalStats> {
        return combine(
            dao.getGlobalTotalCountFlow(),
            dao.getGlobalSafeCountFlow(),
            dao.getGlobalSuspiciousCountFlow(),
            dao.getGlobalDangerCountFlow()
        ) { total, safe, suspicious, danger ->
            GlobalStats(total, safe, suspicious, danger)
        }
    }
}
