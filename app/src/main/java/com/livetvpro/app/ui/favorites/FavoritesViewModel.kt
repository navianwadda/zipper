package com.livetvpro.app.ui.favorites

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.models.FavoriteChannel
import com.livetvpro.app.data.repository.FavoritesRepository
import com.livetvpro.app.data.repository.NativeDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val nativeDataRepository: NativeDataRepository
) : ViewModel() {

    val favorites = favoritesRepository.getFavoriteIdsFlow()
        .map { ids -> resolveFavorites(ids) }
        .asLiveData()

    private val _filteredFavorites = MutableLiveData<List<FavoriteChannel>>(emptyList())
    val filteredFavorites: androidx.lifecycle.LiveData<List<FavoriteChannel>> = _filteredFavorites
    private var searchQuery = ""

    private fun resolveFavorites(ids: List<String>): List<FavoriteChannel> {
        val liveChannels = try {
            nativeDataRepository.getChannels()
        } catch (e: OutOfMemoryError) {
            System.gc()
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val byId = liveChannels.associateBy { it.id }
        return ids.map { id ->
            val channel = byId[id]
            if (channel != null) {
                FavoriteChannel(
                    id = channel.id,
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                    streamUrl = channel.streamUrl,
                    categoryId = channel.categoryId,
                    categoryName = channel.categoryName,
                    links = channel.links
                )
            } else {
                FavoriteChannel(id = id, name = id)
            }
        }
    }

    fun searchFavorites(query: String) {
        searchQuery = query
        val all = favorites.value ?: emptyList()
        _filteredFavorites.value = if (query.isBlank()) all
        else all.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun onFavoritesChanged(all: List<FavoriteChannel>) {
        _filteredFavorites.value = if (searchQuery.isBlank()) all
        else all.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    fun getLiveChannel(channelId: String): Channel? {
        return try {
            nativeDataRepository.getChannels().find { it.id == channelId }
        } catch (e: OutOfMemoryError) {
            System.gc()
            null
        } catch (e: Exception) {
            null
        }
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

    fun removeFavorite(channelId: String) {
        viewModelScope.launch {
            favoritesRepository.removeFavorite(channelId)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            favoritesRepository.clearAll()
        }
    }
}
