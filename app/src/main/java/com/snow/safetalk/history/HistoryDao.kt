package com.snow.safetalk.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
@JvmSuppressWildcards
interface HistoryDao {

    @Query("""
        SELECT * FROM analysis_history 
        WHERE isHiddenFromHistory = 0 
        AND (:riskLabel IS NULL OR label = :riskLabel)
        AND analyzedAt >= :sinceTimestamp
        ORDER BY analyzedAt DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getHistoryPaginated(riskLabel: RiskLabel?, sinceTimestamp: Long, limit: Int, offset: Int): List<HistoryEntity>

    @Query("""
        SELECT COUNT(*) FROM analysis_history 
        WHERE isHiddenFromHistory = 0 
        AND (:riskLabel IS NULL OR label = :riskLabel)
        AND analyzedAt >= :sinceTimestamp
    """)
    suspend fun getHistoryCount(riskLabel: RiskLabel?, sinceTimestamp: Long): Int

    @Query("SELECT * FROM analysis_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HistoryEntity): Long

    @Query("UPDATE analysis_history SET isHiddenFromHistory = 1 WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>): Int

    @Query("UPDATE analysis_history SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String): Int

    @Query("DELETE FROM analysis_history WHERE analyzedAt < :cutoffTimestamp")
    suspend fun trimOlderThan(cutoffTimestamp: Long): Int

    @Query("SELECT * FROM analysis_history WHERE isHiddenFromHistory = 0 ORDER BY analyzedAt DESC LIMIT 1")
    fun getLatestResultFlow(): kotlinx.coroutines.flow.Flow<HistoryEntity?>

    @Query("SELECT COUNT(*) FROM analysis_history WHERE isHiddenFromHistory = 0")
    fun getHistoryCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    // ── Global Statistics (Ignored isHiddenFromHistory) ───────────────────────
    
    @Query("SELECT COUNT(*) FROM analysis_history")
    fun getGlobalTotalCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM analysis_history WHERE riskScore >= 70")
    fun getGlobalDangerCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM analysis_history WHERE riskScore BETWEEN 40 AND 69")
    fun getGlobalSuspiciousCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM analysis_history WHERE riskScore < 40")
    fun getGlobalSafeCountFlow(): kotlinx.coroutines.flow.Flow<Int>
}
