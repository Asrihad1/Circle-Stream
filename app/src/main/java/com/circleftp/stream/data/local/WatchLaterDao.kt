package com.circleftp.stream.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchLaterDao {

    @Upsert
    suspend fun addToWatchLater(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE mediaId = :mediaId")
    suspend fun removeFromWatchLater(mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE mediaId = :mediaId)")
    fun isWatchLater(mediaId: String): Flow<Boolean>

    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun getAllWatchLater(): Flow<List<WatchLaterEntity>>

    @Query("DELETE FROM watch_later")
    suspend fun clearAllWatchLater()
}

