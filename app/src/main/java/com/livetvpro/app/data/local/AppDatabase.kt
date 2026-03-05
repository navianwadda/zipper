package com.livetvpro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.livetvpro.app.data.local.dao.FavoriteChannelDao
import com.livetvpro.app.data.local.dao.PlaylistDao
import com.livetvpro.app.data.local.entity.FavoriteChannelEntity
import com.livetvpro.app.data.local.entity.FavoriteChannelConverters
import com.livetvpro.app.data.local.entity.PlaylistEntity

@Database(
    entities = [FavoriteChannelEntity::class, PlaylistEntity::class],
    version = 1,  
    exportSchema = false
)
@TypeConverters(FavoriteChannelConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteChannelDao(): FavoriteChannelDao
    abstract fun playlistDao(): PlaylistDao
}
