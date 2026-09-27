package com.livetvpro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livetvpro.app.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getAllFlow(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query(
        """
        DELETE FROM watch_history
        WHERE id NOT IN (
            SELECT id FROM watch_history ORDER BY watchedAt DESC LIMIT :maxItems
        )
        """
    )
    suspend fun trim(maxItems: Int)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
