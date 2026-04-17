package com.livetvpro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.livetvpro.app.data.local.dao.ChannelDao
import com.livetvpro.app.data.local.dao.FavoriteChannelDao
import com.livetvpro.app.data.local.dao.PlaylistDao
import com.livetvpro.app.data.local.entity.ChannelEntity
import com.livetvpro.app.data.local.entity.FavoriteChannelEntity
import com.livetvpro.app.data.local.entity.FavoriteChannelConverters
import com.livetvpro.app.data.local.entity.PlaylistEntity

@Database(
    entities = [FavoriteChannelEntity::class, PlaylistEntity::class, ChannelEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(FavoriteChannelConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteChannelDao(): FavoriteChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS channels (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        logoUrl TEXT NOT NULL,
                        streamUrl TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        groupTitle TEXT NOT NULL,
                        linksJson TEXT,
                        team1Logo TEXT NOT NULL,
                        team2Logo TEXT NOT NULL,
                        isLive INTEGER NOT NULL,
                        startTime TEXT NOT NULL,
                        endTime TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_channels_categoryId ON channels(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_channels_groupTitle ON channels(groupTitle)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_channels_categoryId_name ON channels(categoryId, name)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            }
        }

       
        val openCallback = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA journal_mode=WAL")
                db.execSQL("PRAGMA synchronous=NORMAL")
                db.execSQL("PRAGMA cache_size=-4096")   
                db.execSQL("PRAGMA page_size=4096")
                db.execSQL("PRAGMA temp_store=MEMORY")
            }
        }
    }
}
