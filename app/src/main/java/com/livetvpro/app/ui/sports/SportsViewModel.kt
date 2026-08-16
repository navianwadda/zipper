package com.livetvpro.app.ui.sports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.repository.NativeDataRepository
import com.livetvpro.app.data.repository.FavoritesRepository
import com.livetvpro.app.utils.RetryViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SportsViewModel @Inject constructor(
    private val repository: NativeDataRepository,
    private val favoritesRepository: FavoritesRepository
) : RetryViewModel() {
    private val _channels = MutableLiveData<List<Channel>>()
    private val _filteredChannels = MutableLiveData<List<Channel>>()
    val filteredChannels: LiveData<List<Channel>> = _filteredChannels
    private val _favoriteStatusCache = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteStatusCache.asStateFlow()
    var currentQuery: String = ""
        private set

    init {
        loadData()
        loadFavoriteCache()
    }

    private fun loadFavoriteCache() {
        viewModelScope.launch {
            favoritesRepository.getFavoritesFlow().collect { favorites ->
                _favoriteStatusCache.value = favorites.map { it.id }.toSet()
            }
        }
    }

    override fun loadData() {
        viewModelScope.launch {
            val hasExisting = !_channels.value.isNullOrEmpty()
            try {
                if (!hasExisting) startLoading()
                val sports = repository.getSports()
                if (sports != _channels.value) {
                    _channels.value = sports
                    applyFilter()
                }
                finishLoading(dataIsEmpty = sports.isEmpty())
            } catch (e: OutOfMemoryError) {
                System.gc()
                if (!hasExisting) {
                    _channels.value = emptyList()
                    applyFilter()
                    finishLoading(dataIsEmpty = true, error = Exception("Low memory. Please close other apps and try again."))
                }
            } catch (e: Exception) {
                if (!hasExisting) {
                    _channels.value = emptyList()
                    applyFilter()
                    finishLoading(dataIsEmpty = true, error = e)
                }
            }
        }
    }

    override fun onResume() {
    }

    fun searchSports(query: String) {
        currentQuery = query
        applyFilter()
    }

    private fun applyFilter() {
        val channels = _channels.value ?: emptyList()
        _filteredChannels.value = if (currentQuery.isBlank()) channels
        else channels.filter { it.name.contains(currentQuery, ignoreCase = true) }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val favoriteLinks = channel.links?.map { channelLink ->
                ChannelLink(
                    quality = channelLink.quality,
                    url = channelLink.url,
                    cookie = channelLink.cookie,
                    referer = channelLink.referer,
                    origin = channelLink.origin,
                    userAgent = channelLink.userAgent,
                    xForwardedFor = channelLink.xForwardedFor,
                    drmScheme = channelLink.drmScheme,
                    drmLicenseUrl = channelLink.drmLicenseUrl
                )
            }
            val streamUrlToSave = when {
                channel.streamUrl.isNotEmpty() -> channel.streamUrl
                !favoriteLinks.isNullOrEmpty() -> buildStreamUrlFromLink(favoriteLinks.first())
                else -> ""
            }
            val favoriteChannel = FavoriteChannel(
                id = channel.id,
                name = channel.name,
                logoUrl = channel.logoUrl,
                streamUrl = streamUrlToSave,
                categoryId = channel.categoryId,
                categoryName = "Sports",
                links = favoriteLinks
            )
            if (favoritesRepository.isFavorite(channel.id)) {
                favoritesRepository.removeFavorite(channel.id)
            } else {
                favoritesRepository.addFavorite(favoriteChannel)
            }
        }
    }

    private fun buildStreamUrlFromLink(link: ChannelLink): String {
        val parts = mutableListOf(link.url)
        link.referer?.let { if (it.isNotEmpty()) parts.add("referer=$it") }
        link.cookie?.let { if (it.isNotEmpty()) parts.add("cookie=$it") }
        link.origin?.let { if (it.isNotEmpty()) parts.add("origin=$it") }
        link.userAgent?.let { if (it.isNotEmpty()) parts.add("User-Agent=$it") }
        link.xForwardedFor?.let { if (it.isNotEmpty()) parts.add("X-Forwarded-For=$it") }
        link.drmScheme?.let { if (it.isNotEmpty()) parts.add("drmScheme=$it") }
        link.drmLicenseUrl?.let { if (it.isNotEmpty()) parts.add("drmLicense=$it") }
        return if (parts.size > 1) parts.joinToString("|") else parts[0]
    }

    fun isFavorite(channelId: String): Boolean = _favoriteStatusCache.value.contains(channelId)
}
