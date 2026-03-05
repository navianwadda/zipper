package com.livetvpro.app.data.repository

import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.utils.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val dataRepository: NativeDataRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend fun getChannelsByCategory(categoryId: String): List<Channel> = withContext(Dispatchers.IO) {
        if (!dataRepository.isDataLoaded()) {
            return@withContext emptyList()
        }

        val allChannels = mutableListOf<Channel>()

        val staticChannels = dataRepository.getChannels().filter { it.categoryId == categoryId }
        allChannels.addAll(staticChannels)

        val category = categoryRepository.getCategories().find { it.id == categoryId }
        if (category?.m3uUrl != null && category.m3uUrl.isNotEmpty()) {
            val m3uChannelsRaw = M3uParser.parseM3uFromUrl(category.m3uUrl)
            val m3uChannels = M3uParser.convertToChannels(m3uChannelsRaw, category.id, category.name)
            allChannels.addAll(m3uChannels)
        }

        return@withContext allChannels
    }
}
