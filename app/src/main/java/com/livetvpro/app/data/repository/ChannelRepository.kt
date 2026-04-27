package com.livetvpro.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.livetvpro.app.data.local.dao.ChannelDao
import com.livetvpro.app.data.local.entity.toEntity
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.utils.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val dataRepository: NativeDataRepository,
    private val categoryRepository: CategoryRepository,
    private val channelDao: ChannelDao
) {
    companion object {
        private const val PAGE_SIZE = 50
        private const val INSERT_CHUNK = 100
        private val gson = com.google.gson.Gson()
    }

    fun getChannelsPaged(
        categoryId: String,
        group: String = "All",
        query: String = ""
    ): Flow<PagingData<Channel>> = Pager(
        config = PagingConfig(
            pageSize           = PAGE_SIZE,
            prefetchDistance   = PAGE_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            channelDao.getChannelsPaged(
                categoryId = categoryId,
                group      = group,
                query      = query
            )
        }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            val links = entity.linksJson?.let {
                gson.fromJson(it, Array<com.livetvpro.app.data.models.ChannelLink>::class.java)?.toList()
            }
            entity.toChannel(links)
        }
    }

    suspend fun getChannelsByCategory(categoryId: String): List<Channel> =
        channelDao.getChannelsByCategory(categoryId).map { entity ->
            val links = entity.linksJson?.let {
                gson.fromJson(it, Array<com.livetvpro.app.data.models.ChannelLink>::class.java)?.toList()
            }
            entity.toChannel(links)
        }

    suspend fun getGroups(categoryId: String): List<String> =
        channelDao.getGroups(categoryId)

    suspend fun syncCategory(categoryId: String) = withContext(Dispatchers.IO) {
        if (!dataRepository.isDataLoaded()) return@withContext

        try {
            val nativeChannels = dataRepository.getChannels().filter { it.categoryId == categoryId }
            if (nativeChannels.isNotEmpty()) {
                channelDao.replaceCategoryChannels(categoryId, nativeChannels.map { it.toEntity() })
            }
        } catch (e: OutOfMemoryError) {
            System.gc()
            throw Exception("Low memory while syncing category")
        }

        val category = categoryRepository.getCategories().find { it.id == categoryId }
        if (category?.m3uUrl != null && category.m3uUrl.isNotEmpty()) {
            streamInsertM3u(category.m3uUrl, categoryId, category.name)
        }
    }

    suspend fun syncPlaylist(
        playlistId: String,
        playlistTitle: String,
        source: String,
        isFile: Boolean,
        application: android.app.Application
    ) = withContext(Dispatchers.IO) {
        if (isFile) {
            try {
                val uri = android.net.Uri.parse(source)
                val content = application.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@withContext
                val channels = M3uParser.convertToChannels(
                    M3uParser.parseM3uContent(content), playlistId, playlistTitle
                )
                channelDao.replaceCategoryChannels(playlistId, channels.map { it.toEntity() })
            } catch (e: OutOfMemoryError) {
                System.gc()
                throw Exception("Low memory while loading playlist")
            }
        } else {
            streamInsertM3u(source, playlistId, playlistTitle)
        }
    }

    private suspend fun streamInsertM3u(url: String, categoryId: String, categoryName: String) {
        try {
            val channels = M3uParser.convertToChannels(
                M3uParser.parseM3uFromUrl(url), categoryId, categoryName
            )
            channelDao.replaceCategoryChannels(categoryId, channels.map { it.toEntity() })
        } catch (e: OutOfMemoryError) {
            System.gc()
            throw Exception("Low memory while loading M3U")
        }
    }

    suspend fun isCategorySynced(categoryId: String): Boolean =
        channelDao.countByCategory(categoryId) > 0
}
