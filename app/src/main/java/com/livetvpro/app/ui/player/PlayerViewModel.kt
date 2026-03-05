package com.livetvpro.app.ui.player

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.models.LiveEvent
import com.livetvpro.app.data.repository.ChannelRepository
import com.livetvpro.app.data.repository.FavoritesRepository
import com.livetvpro.app.data.repository.LiveEventRepository
import com.livetvpro.app.data.repository.NativeDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.app.Application
import android.net.Uri
import android.util.Log
import com.livetvpro.app.data.repository.PlaylistRepository
import com.livetvpro.app.utils.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request


@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val application: Application,
    private val favoritesRepository: FavoritesRepository,
    private val channelRepository: ChannelRepository,
    private val liveEventRepository: LiveEventRepository,
    private val nativeDataRepository: NativeDataRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _relatedItems = MutableLiveData<List<Channel>>()
    val relatedItems: LiveData<List<Channel>> = _relatedItems

    private val _relatedLiveEvents = MutableLiveData<List<LiveEvent>>()
    val relatedLiveEvents: LiveData<List<LiveEvent>> = _relatedLiveEvents

    private val _isFavorite = MutableLiveData<Boolean>()
    val isFavorite: LiveData<Boolean> = _isFavorite

    private val _refreshedChannel = MutableLiveData<Channel?>()
    val refreshedChannel: LiveData<Channel?> = _refreshedChannel

    private val _channelListItems = MutableLiveData<List<Channel>>(emptyList())
    val channelListItems: LiveData<List<Channel>> = _channelListItems

    fun loadAllChannelsForList(categoryId: String) {
        viewModelScope.launch {
            try {
                val channels = when {
                    categoryId == "sports_slug" -> nativeDataRepository.getSports()
                    categoryId.isNotEmpty() -> {
                        // Check if this categoryId belongs to a playlist first
                        val playlist = playlistRepository.getPlaylistById(categoryId)
                        if (playlist != null) {
                            loadChannelsFromPlaylist(playlist)
                        } else {
                            channelRepository.getChannelsByCategory(categoryId)
                        }
                    }
                    else -> withContext(Dispatchers.IO) { nativeDataRepository.getChannels() }
                }
                _channelListItems.postValue(channels)
            } catch (e: Exception) {
                _channelListItems.postValue(emptyList())
            }
        }
    }

    fun refreshChannelData(channelId: String) {
        viewModelScope.launch {
            try {
                val allChannels = nativeDataRepository.getChannels()
                val freshChannel = allChannels.find { it.id == channelId }
                if (freshChannel != null) {
                    _refreshedChannel.postValue(freshChannel)
                }
            } catch (e: Exception) {
            }
        }
    }

    fun checkFavoriteStatus(channelId: String) {
        viewModelScope.launch {
            val result = favoritesRepository.isFavorite(channelId)
            _isFavorite.value = result
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val currentStatus = favoritesRepository.isFavorite(channel.id)
            if (currentStatus) {
                favoritesRepository.removeFavorite(channel.id)
                _isFavorite.value = false
            } else {
                val favoriteLinks = channel.links?.map { channelLink ->
                    ChannelLink(
                        quality = channelLink.quality,
                        url = channelLink.url,
                        cookie = channelLink.cookie,
                        referer = channelLink.referer,
                        origin = channelLink.origin,
                        userAgent = channelLink.userAgent,
                        drmScheme = channelLink.drmScheme,
                        drmLicenseUrl = channelLink.drmLicenseUrl
                    )
                }
                
                val favorite = FavoriteChannel(
                    id = channel.id,
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                    streamUrl = channel.streamUrl,
                    categoryId = channel.categoryId,
                    categoryName = channel.categoryName,
                    links = favoriteLinks
                )
                favoritesRepository.addFavorite(favorite)
                _isFavorite.value = true
            }
        }
    }

    fun loadRandomRelatedChannels(
        categoryId: String, 
        currentChannelId: String,
        groupFilter: String? = null
    ) {
        if (categoryId == "sports_slug") {
            loadRandomRelatedSports(currentChannelId)
            return
        }
        viewModelScope.launch {
            try {

                val playlist = playlistRepository.getPlaylistById(categoryId)
                
                val allChannels = if (playlist != null) {

                    loadChannelsFromPlaylist(playlist)
                } else {

                    channelRepository.getChannelsByCategory(categoryId)
                }
                

                var availableChannels = allChannels.filter { it.id != currentChannelId }
                

                if (!groupFilter.isNullOrEmpty() && groupFilter != "All") {
                    availableChannels = availableChannels.filter { 
                        it.groupTitle == groupFilter 
                    }
                }
                

                if (availableChannels.isEmpty()) {
                    _relatedItems.postValue(emptyList())
                    return@launch
                }
                

                val targetCount = minOf(9, availableChannels.size)
                val randomChannels = availableChannels.shuffled().take(targetCount)
                
                _relatedItems.postValue(randomChannels)
                
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading random channels", e)
                _relatedItems.postValue(emptyList())
            }
        }
    }


    fun loadRandomRelatedSports(currentChannelId: String) {
        viewModelScope.launch {
            try {
                val allSports = nativeDataRepository.getSports()
                val available = allSports.filter { it.id != currentChannelId }
                if (available.isEmpty()) {
                    _relatedItems.postValue(emptyList())
                    return@launch
                }
                val targetCount = minOf(9, available.size)
                _relatedItems.postValue(available.shuffled().take(targetCount))
            } catch (e: Exception) {
                _relatedItems.postValue(emptyList())
            }
        }
    }


    fun loadRelatedChannels(categoryId: String, currentChannelId: String) {
        viewModelScope.launch {
            try {
                val allChannels = channelRepository.getChannelsByCategory(categoryId)
                val availableChannels = allChannels.filter { it.id != currentChannelId }

                if (availableChannels.isEmpty()) {
                    _relatedItems.postValue(emptyList())
                    return@launch
                }

                val currentIndex = allChannels.indexOfFirst { it.id == currentChannelId }
                val targetCount = 9 
                
                val related = if (currentIndex != -1) {
                    val relatedList = mutableListOf<Channel>()
                    val beforeCount = 4
                    val afterCount = 5
                    
                    val beforeStart = maxOf(0, currentIndex - beforeCount)
                    for (i in beforeStart until currentIndex) {
                        if (allChannels[i].id != currentChannelId) relatedList.add(allChannels[i])
                    }
                    
                    val afterEnd = minOf(allChannels.size - 1, currentIndex + afterCount)
                    for (i in (currentIndex + 1)..afterEnd) {
                        if (allChannels[i].id != currentChannelId) relatedList.add(allChannels[i])
                    }
                    
                    if (relatedList.size < targetCount && availableChannels.size > relatedList.size) {
                        val extra = availableChannels
                            .filter { it !in relatedList }
                            .shuffled()
                            .take(targetCount - relatedList.size)
                        relatedList.addAll(extra)
                    }
                    relatedList
                } else {
                    availableChannels.shuffled().take(targetCount)
                }
                
                _relatedItems.postValue(related)
            } catch (e: Exception) {
                _relatedItems.postValue(emptyList())
            }
        }
    }
    
    fun setRelatedChannels(channels: List<Channel>) {
        _relatedItems.postValue(channels.take(9))
    }

    fun loadRelatedEvents(currentEventId: String) {
        viewModelScope.launch {
            try {
                val allEvents = liveEventRepository.getLiveEvents()
                val currentTime = System.currentTimeMillis()
                
                val eventsWithStatus = allEvents.map { event ->
                    event to event.getStatus(currentTime)
                }
                
                val liveEvents = eventsWithStatus.filter { (event, status) ->
                    event.id != currentEventId && status == com.livetvpro.app.data.models.EventStatus.LIVE
                }
                
                val upcomingEvents = eventsWithStatus.filter { (event, status) ->
                    event.id != currentEventId && status == com.livetvpro.app.data.models.EventStatus.UPCOMING
                }
                
                val relatedEvents = (liveEvents.map { it.first } + upcomingEvents.map { it.first }).take(6)
                
                _relatedLiveEvents.postValue(relatedEvents)
                
            } catch (e: Exception) {
                _relatedLiveEvents.postValue(emptyList())
            }
        }
    }

    suspend fun getAllEvents(): List<LiveEvent> {
        return try {
            liveEventRepository.getLiveEvents()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun loadChannelsFromPlaylist(
        playlist: com.livetvpro.app.data.models.Playlist
    ): List<Channel> {
        return withContext(Dispatchers.IO) {
            try {
                val m3uContent = if (playlist.isFile) {
                    readFileContent(playlist.filePath)
                } else {
                    fetchUrlContent(playlist.url)
                }
                val m3uChannels = M3uParser.parseM3uContent(m3uContent)
                M3uParser.convertToChannels(
                    m3uChannels = m3uChannels,
                    categoryId = playlist.id,
                    categoryName = playlist.title
                )
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading playlist channels", e)
                emptyList()
            }
        }
    }

    private suspend fun readFileContent(uriString: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                application.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("Could not read file")
            } catch (e: Exception) {
                throw Exception("Failed to read file: ${e.message}")
            }
        }
    }

    private suspend fun fetchUrlContent(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Failed to fetch playlist: HTTP ${response.code}")
                    }
                    response.body?.string() ?: throw Exception("Empty response")
                }
            } catch (e: Exception) {
                throw Exception("Failed to fetch URL: ${e.message}")
            }
        }
    }

}
