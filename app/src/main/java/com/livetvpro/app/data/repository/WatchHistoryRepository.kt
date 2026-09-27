package com.livetvpro.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.livetvpro.app.data.local.dao.WatchHistoryDao
import com.livetvpro.app.data.local.entity.WatchHistoryEntity
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class WatchHistoryEntry(
    val channel: Channel,
    val linkIndex: Int = -1,
    val watchedAt: Long = 0L
)

@Singleton
class WatchHistoryRepository @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao
) {
    companion object {
        private const val MAX_ENTRIES = 15
        private val gson = Gson()
    }

    fun getHistoryFlow(): Flow<List<WatchHistoryEntry>> {
        return watchHistoryDao.getAllFlow().map { entities ->
            entities.map { it.toWatchHistoryEntry() }
        }
    }

    suspend fun record(channel: Channel, linkIndex: Int = -1) {
        if (channel.id.isBlank()) return
        if (channel.streamUrl.isBlank() && channel.links.isNullOrEmpty()) return

        val linksJson = channel.links
            ?.takeIf { it.isNotEmpty() }
            ?.let { links -> runCatching { gson.toJson(links) }.getOrNull() }

        watchHistoryDao.upsert(
            WatchHistoryEntity(
                id           = channel.id,
                name         = channel.name,
                logoUrl      = channel.logoUrl,
                streamUrl    = channel.streamUrl,
                categoryId   = channel.categoryId,
                categoryName = channel.categoryName,
                groupTitle   = channel.groupTitle,
                linksJson    = linksJson,
                linkIndex    = linkIndex,
                watchedAt    = System.currentTimeMillis()
            )
        )
        watchHistoryDao.trim(MAX_ENTRIES)
    }

    suspend fun clearAll() {
        watchHistoryDao.clearAll()
    }

    private fun WatchHistoryEntity.toWatchHistoryEntry(): WatchHistoryEntry {
        val links = if (!linksJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<ChannelLink>>() {}.type
                gson.fromJson<List<ChannelLink>>(linksJson, type)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val resolvedStreamUrl = streamUrl.ifEmpty {
            links?.firstOrNull()?.url ?: ""
        }

        return WatchHistoryEntry(
            channel = Channel(
                id           = id,
                name         = name,
                logoUrl      = logoUrl,
                streamUrl    = resolvedStreamUrl,
                categoryId   = categoryId,
                categoryName = categoryName,
                groupTitle   = groupTitle,
                links        = links
            ),
            linkIndex  = linkIndex,
            watchedAt  = watchedAt
        )
    }
}
