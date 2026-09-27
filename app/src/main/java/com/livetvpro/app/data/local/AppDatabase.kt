package com.livetvpro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.livetvpro.app.data.local.dao.ChannelDao
import com.livetvpro.app.data.local.dao.FavoriteChannelDao
import com.livetvpro.app.data.local.dao.PlaylistDao
import com.livetvpro.app.data.local.dao.WatchHistoryDao
import com.livetvpro.app.data.local.entity.ChannelEntity
import com.livetvpro.app.data.local.entity.FavoriteChannelEntity
import com.livetvpro.app.data.local.entity.FavoriteChannelConverters
import com.livetvpro.app.data.local.entity.PlaylistEntity
import com.livetvpro.app.data.local.entity.WatchHistoryEntity

@Database(
    entities = [FavoriteChannelEntity::class, PlaylistEntity::class, ChannelEntity::class, WatchHistoryEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(FavoriteChannelConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteChannelDao(): FavoriteChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun watchHistoryDao(): WatchHistoryDao

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
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE playlists SET position = (SELECT COUNT(*) FROM playlists p2 WHERE p2.createdAt <= playlists.createdAt) - 1")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_channels_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        addedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO favorite_channels_new (id, addedAt)
                    SELECT id, addedAt FROM favorite_channels
                """.trimIndent())
                db.execSQL("DROP TABLE favorite_channels")
                db.execSQL("ALTER TABLE favorite_channels_new RENAME TO favorite_channels")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorite_channels ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorite_channels ADD COLUMN logoUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorite_channels ADD COLUMN streamUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorite_channels ADD COLUMN categoryId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorite_channels ADD COLUMN categoryName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorite_channels ADD COLUMN linksJson TEXT")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watch_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        logoUrl TEXT NOT NULL,
                        streamUrl TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        groupTitle TEXT NOT NULL,
                        linksJson TEXT,
                        linkIndex INTEGER NOT NULL,
                        watchedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
