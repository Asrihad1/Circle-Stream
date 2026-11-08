package com.circleftp.stream.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Upsert
    suspend fun upsertProgress(entity: WatchHistoryEntity)

    @Query("SELECT positionMs FROM watch_history WHERE mediaId = :mediaId OR streamUrl = :streamUrl ORDER BY lastUpdated DESC LIMIT 1")
    suspend fun getResumePosition(mediaId: String, streamUrl: String): Long?

    @Query("DELETE FROM watch_history WHERE streamUrl = :streamUrl AND mediaId != :keepMediaId")
    suspend fun deleteDuplicateStreams(streamUrl: String, keepMediaId: String)

    @Query("DELETE FROM watch_history WHERE isSeries = 1 AND parentSeriesId = :parentSeriesId AND parentSeriesId IS NOT NULL AND mediaId != :keepMediaId")
    suspend fun deleteOlderEpisodesOfSeries(parentSeriesId: String, keepMediaId: String)

    @Transaction
    suspend fun upsertCleanWatchHistory(entity: WatchHistoryEntity) {
        deleteDuplicateStreams(entity.streamUrl, entity.mediaId)
        if (entity.isSeries && !entity.parentSeriesId.isNullOrBlank()) {
            deleteOlderEpisodesOfSeries(entity.parentSeriesId, entity.mediaId)
        }
        upsertProgress(entity)
    }

    /**
     * Emits in-progress media items (watched at least 3 seconds and under 95% completion)
     * ordered by most recently watched.
     */
    @Query(
        """
        SELECT * FROM watch_history
        WHERE durationMs > 0
          AND positionMs >= 3000
          AND (CAST(positionMs AS REAL) / CAST(durationMs AS REAL)) < 0.95
        ORDER BY lastUpdated DESC
        LIMIT 30
        """
    )
    fun getContinueWatchingFlow(): Flow<List<WatchHistoryEntity>>

    /**
     * Fetches completed/watched items that are >= 92% complete.
     */
    @Query(
        """
        SELECT * FROM watch_history 
        WHERE durationMs > 0 
        AND (CAST(positionMs AS FLOAT) / CAST(durationMs AS FLOAT)) >= 0.92
        ORDER BY lastUpdated DESC
        """
    )
    fun getCompletedWatchedFlow(): Flow<List<WatchHistoryEntity>>

    /**
     * Manually marks an entity as watched (100% completed) and updates lastUpdated.
     */
    suspend fun markAsWatched(entity: WatchHistoryEntity) {
        val targetDuration = if (entity.durationMs > 0L) entity.durationMs else 1000L
        val updated = entity.copy(
            positionMs = targetDuration,
            durationMs = targetDuration,
            lastUpdated = System.currentTimeMillis()
        )
        upsertCleanWatchHistory(updated)
    }

    @Query("DELETE FROM watch_history WHERE mediaId = :mediaId")
    suspend fun removeFromHistory(mediaId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllHistory()
}
