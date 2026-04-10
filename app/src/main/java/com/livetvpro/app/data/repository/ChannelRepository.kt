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
        private const val INSERT_CHUNK = 500
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
                com.google.gson.Gson().fromJson(it, Array<com.livetvpro.app.data.models.ChannelLink>::class.java)?.toList()
            }
            entity.toChannel(links)
        }
    }

    suspend fun getChannelsByCategory(categoryId: String): List<Channel> =
        channelDao.getChannelsByCategory(categoryId).map { entity ->
            val links = entity.linksJson?.let {
                com.google.gson.Gson().fromJson(it, Array<com.livetvpro.app.data.models.ChannelLink>::class.java)?.toList()
            }
            entity.toChannel(links)
        }

    suspend fun getGroups(categoryId: String): List<String> =
        channelDao.getGroups(categoryId)

    suspend fun syncCategory(categoryId: String) = withContext(Dispatchers.IO) {
        if (!dataRepository.isDataLoaded()) return@withContext

        channelDao.deleteByCategory(categoryId)

        val staticChannels = dataRepository.getChannels().filter { it.categoryId == categoryId }
        staticChannels.chunked(INSERT_CHUNK).forEach { chunk ->
            channelDao.insertAll(chunk.map { it.toEntity() })
        }

        val category = categoryRepository.getCategories().find { it.id == categoryId }
        if (category?.m3uUrl != null && category.m3uUrl.isNotEmpty()) {
            val raw = M3uParser.parseM3uFromUrl(category.m3uUrl)
            M3uParser.convertToChannels(raw, categoryId, category.name)
                .chunked(INSERT_CHUNK)
                .forEach { chunk -> channelDao.insertAll(chunk.map { it.toEntity() }) }
        }
    }

    suspend fun syncPlaylist(
        playlistId: String,
        playlistTitle: String,
        source: String,
        isFile: Boolean,
        application: android.app.Application
    ) = withContext(Dispatchers.IO) {
        val channels = if (isFile) {
            val uri = android.net.Uri.parse(source)
            val content = application.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: return@withContext
            val parsed = M3uParser.parseM3uContent(content)
            M3uParser.convertToChannels(parsed, playlistId, playlistTitle)
        } else {
            val raw = M3uParser.parseM3uFromUrl(source)
            M3uParser.convertToChannels(raw, playlistId, playlistTitle)
        }

        channelDao.deleteByCategory(playlistId)
        channels.chunked(INSERT_CHUNK).forEach { chunk ->
            channelDao.insertAll(chunk.map { it.toEntity() })
        }
    }

    suspend fun isCategorySynced(categoryId: String): Boolean =
        channelDao.countByCategory(categoryId) > 0
}
