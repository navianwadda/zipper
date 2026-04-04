package com.livetvpro.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.livetvpro.app.data.local.entity.ChannelEntity

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY name ASC")
    suspend fun getChannelsByCategory(categoryId: String): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels
        WHERE categoryId = :categoryId
          AND (:group = 'All' OR groupTitle = :group)
          AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun getChannelsPaged(
        categoryId: String,
        group: String = "All",
        query: String = ""
    ): PagingSource<Int, ChannelEntity>

    @Query("""
        SELECT DISTINCT groupTitle FROM channels
        WHERE categoryId = :categoryId
          AND groupTitle != ''
        ORDER BY groupTitle ASC
    """)
    suspend fun getGroups(categoryId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: String)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM channels WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: String): Int
}
