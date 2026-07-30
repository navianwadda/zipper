package com.livetvpro.app.di

import android.content.Context
import androidx.room.Room
import com.livetvpro.app.data.local.AppDatabase
import com.livetvpro.app.data.local.dao.ChannelDao
import com.livetvpro.app.data.local.dao.FavoriteChannelDao
import com.livetvpro.app.data.local.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "live_tv_pro_database"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    @Provides
    @Singleton
    fun provideFavoriteChannelDao(database: AppDatabase): FavoriteChannelDao =
        database.favoriteChannelDao()

    @Provides
    @Singleton
    fun providePlaylistDao(database: AppDatabase): PlaylistDao =
        database.playlistDao()

    @Provides
    @Singleton
    fun provideChannelDao(database: AppDatabase): ChannelDao =
        database.channelDao()
}
