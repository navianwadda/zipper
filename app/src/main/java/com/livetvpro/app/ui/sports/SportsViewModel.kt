package com.livetvpro.app.ui.sports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.repository.NativeDataRepository
import com.livetvpro.app.data.repository.FavoritesRepository
import com.livetvpro.app.utils.RetryViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    val favoriteStatusCache: kotlinx.coroutines.flow.StateFlow<Set<String>> = _favoriteStatusCache
    var currentQuery: String = ""
        private set

    init {
        loadData()
        loadFavoriteCache()
    }

    private fun loadFavoriteCache() {
        viewModelScope.launch {
            favoritesRepository.getFavoriteIdsFlow().collect { ids ->
                _favoriteStatusCache.value = ids.toSet()
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
            if (favoritesRepository.isFavorite(channel.id)) {
                favoritesRepository.removeFavorite(channel.id)
            } else {
                favoritesRepository.addFavorite(channel.id)
            }
        }
    }

    fun isFavorite(channelId: String): Boolean = _favoriteStatusCache.value.contains(channelId)
}
