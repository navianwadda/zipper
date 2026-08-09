package com.livetvpro.app.data.repository

import com.livetvpro.app.data.local.dao.FavoriteChannelDao
import com.livetvpro.app.data.local.entity.FavoriteChannelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val favoriteDao: FavoriteChannelDao
) {

    fun getFavoriteIdsFlow(): Flow<List<String>> {
        return favoriteDao.getAllFavorites().map { entities -> entities.map { it.id } }
    }

    suspend fun addFavorite(channelId: String) {
        favoriteDao.insertFavorite(FavoriteChannelEntity(id = channelId))
    }

    suspend fun removeFavorite(channelId: String) {
        favoriteDao.deleteFavoriteById(channelId)
    }

    suspend fun isFavorite(channelId: String): Boolean {
        return favoriteDao.isFavorite(channelId)
    }

    suspend fun clearAll() {
        favoriteDao.deleteAllFavorites()
    }

    suspend fun getFavoritesCount(): Int {
        return favoriteDao.getFavoritesCount()
    }
}
