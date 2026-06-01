package com.livetvpro.app.ui.sports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.ChannelLink
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.repository.FavoritesRepository
import com.livetvpro.app.data.repository.NativeDataRepository
import com.livetvpro.app.utils.RetryViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SportsViewModel @Inject constructor(
    private val nativeDataRepository: NativeDataRepository,
    private val favoritesRepository: FavoritesRepository
) : RetryViewModel() {

    private val _allChannels = MutableLiveData<List<Channel>>(emptyList())

    private val _filteredChannels = MutableLiveData<List<Channel>>(emptyList())
    val filteredChannels: LiveData<List<Channel>> = _filteredChannels

    private val _favoriteStatusCache = MutableStateFlow<Set<String>>(emptySet())

    private var currentQuery = ""

    init {
        loadFavoriteCache()
        loadData()
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
            startLoading()
            try {
                val channels = nativeDataRepository.getSports()
                _allChannels.value = channels
                applyFilter()
                finishLoading(dataIsEmpty = channels.isEmpty())
            } catch (e: OutOfMemoryError) {
                System.gc()
                finishLoading(dataIsEmpty = true, error = Exception("Low memory. Please close other apps and try again."))
            } catch (e: Exception) {
                finishLoading(dataIsEmpty = true, error = e)
            }
        }
    }

    override fun onResume() {}

    fun searchSports(query: String) {
        currentQuery = query
        applyFilter()
    }

    private fun applyFilter() {
        val all = _allChannels.value ?: emptyList()
        _filteredChannels.value = if (currentQuery.isBlank()) {
            all
        } else {
            all.filter { it.name.contains(currentQuery, ignoreCase = true) }
        }
    }

    fun isFavorite(channelId: String): Boolean = _favoriteStatusCache.value.contains(channelId)

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            if (favoritesRepository.isFavorite(channel.id)) {
                favoritesRepository.removeFavorite(channel.id)
            } else {
                val favoriteLinks = channel.links?.map { link ->
                    ChannelLink(
                        quality       = link.quality,
                        url           = link.url,
                        cookie        = link.cookie,
                        referer       = link.referer,
                        origin        = link.origin,
                        userAgent     = link.userAgent,
                        xForwardedFor = link.xForwardedFor,
                        drmScheme     = link.drmScheme,
                        drmLicenseUrl = link.drmLicenseUrl
                    )
                }
                val streamUrlToSave = when {
                    channel.streamUrl.isNotEmpty() -> channel.streamUrl
                    !favoriteLinks.isNullOrEmpty()  -> favoriteLinks.first().url
                    else                            -> ""
                }
                favoritesRepository.addFavorite(
                    FavoriteChannel(
                        id           = channel.id,
                        name         = channel.name,
                        logoUrl      = channel.logoUrl,
                        streamUrl    = streamUrlToSave,
                        categoryId   = channel.categoryId,
                        categoryName = channel.categoryName,
                        links        = favoriteLinks
                    )
                )
            }
        }
    }
}
