package com.livetvpro.app.ui.categories

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.repository.CategoryRepository
import com.livetvpro.app.data.repository.ChannelRepository
import com.livetvpro.app.data.repository.FavoritesRepository
import com.livetvpro.app.data.repository.PlaylistRepository
import com.livetvpro.app.utils.AndroidRetryViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryChannelsViewModel @Inject constructor(
    application: Application,
    private val channelRepository: ChannelRepository,
    private val categoryRepository: CategoryRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playlistRepository: PlaylistRepository,
    private val savedStateHandle: SavedStateHandle
) : AndroidRetryViewModel(application) {

    private val _searchQuery   = MutableStateFlow("")
    private val _selectedGroup = MutableStateFlow("All")
    private val _categoryId    = MutableStateFlow<String?>(null)

    val currentSearchQuery: String get() = _searchQuery.value

    private val _favoriteStatusCache = MutableStateFlow<Set<String>>(emptySet())
    val favoriteStatusCache: kotlinx.coroutines.flow.StateFlow<Set<String>> = _favoriteStatusCache

    val categoryName: String = savedStateHandle.get<String>("categoryName") ?: "Channels"

    private val _categoryGroups = MutableLiveData<List<String>>(emptyList())
    val categoryGroups: LiveData<List<String>> = _categoryGroups

    private val _currentGroup = MutableLiveData<String>("All")
    val currentGroup: LiveData<String> = _currentGroup

    var lastLoadedCategoryId: String? = null
        private set

    val channelsPaged: Flow<PagingData<Channel>> = combine(
        _categoryId,
        _searchQuery.debounce(200),
        _selectedGroup
    ) { categoryId, query, group ->
        Triple(categoryId, query, group)
    }
        .distinctUntilChanged()
        .flatMapLatest { (categoryId, query, group) ->
            if (categoryId == null) {
                kotlinx.coroutines.flow.flowOf(PagingData.empty())
            } else {
                channelRepository.getChannelsPaged(
                    categoryId = categoryId,
                    group      = group,
                    query      = query
                )
            }
        }
        .cachedIn(viewModelScope)

    init {
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
        lastLoadedCategoryId?.let { loadChannels(it) }
    }

    override fun onResume() {}

    fun loadChannels(categoryId: String) {
        viewModelScope.launch {
            startLoading()
            lastLoadedCategoryId = categoryId

            try {
                val playlist = playlistRepository.getPlaylistById(categoryId)

                if (playlist != null) {
                    channelRepository.syncPlaylist(
                        playlistId    = playlist.id,
                        playlistTitle = playlist.title,
                        source        = if (playlist.isFile) playlist.filePath else playlist.url,
                        isFile        = playlist.isFile,
                        application   = getApplication()
                    )
                } else {
                    channelRepository.syncCategory(categoryId)
                }

                _categoryId.value = categoryId
                loadGroups(categoryId)
                val isEmpty = !channelRepository.isCategorySynced(categoryId)
                finishLoading(dataIsEmpty = isEmpty)
            } catch (e: OutOfMemoryError) {
                System.gc()
                finishLoading(dataIsEmpty = true, error = Exception("Low memory. Please close other apps and try again."))
            } catch (e: Exception) {
                finishLoading(dataIsEmpty = true, error = e)
            }
        }
    }

    private suspend fun loadGroups(categoryId: String) {
        val groups = channelRepository.getGroups(categoryId)
        _categoryGroups.value = if (groups.isNotEmpty()) listOf("All") + groups else emptyList()
    }

    fun searchChannels(query: String) {
        _searchQuery.value = query
    }

    fun selectGroup(group: String) {
        _selectedGroup.value = group
        _currentGroup.value  = group
    }

    fun refreshChannels() {
        val categoryId = lastLoadedCategoryId ?: return
        viewModelScope.launch {
            startLoading()
            try {
                val playlist = playlistRepository.getPlaylistById(categoryId)
                if (playlist != null) {
                    channelRepository.syncPlaylist(
                        playlistId    = playlist.id,
                        playlistTitle = playlist.title,
                        source        = if (playlist.isFile) playlist.filePath else playlist.url,
                        isFile        = playlist.isFile,
                        application   = getApplication()
                    )
                } else {
                    channelRepository.syncCategory(categoryId)
                }
                loadGroups(categoryId)
                val isEmpty = !channelRepository.isCategorySynced(categoryId)
                finishLoading(dataIsEmpty = isEmpty)
            } catch (e: OutOfMemoryError) {
                System.gc()
                finishLoading(dataIsEmpty = true, error = Exception("Low memory. Please close other apps and try again."))
            } catch (e: Exception) {
                finishLoading(dataIsEmpty = true, error = e)
            }
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

    fun isFavorite(channelId: String): Boolean =
        _favoriteStatusCache.value.contains(channelId)
}
