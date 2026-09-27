package com.livetvpro.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.livetvpro.app.data.local.PreferencesManager
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.Category
import com.livetvpro.app.data.models.Channel
import com.livetvpro.app.data.repository.CategoryRepository
import com.livetvpro.app.data.repository.ChannelRepository
import com.livetvpro.app.data.repository.WatchHistoryEntry
import com.livetvpro.app.data.repository.WatchHistoryRepository
import com.livetvpro.app.utils.RetryViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val channelRepository: ChannelRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val preferencesManager: PreferencesManager,
    private val themeManager: ThemeManager
) : RetryViewModel() {

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _filteredCategories = MutableLiveData<List<Category>>()
    val filteredCategories: LiveData<List<Category>> = _filteredCategories

    /** Channels matching the current query across every category / playlist. */
    private val _searchResults = MutableLiveData<List<Channel>>(emptyList())
    val searchResults: LiveData<List<Channel>> = _searchResults

    /** Most recently watched channels, newest first. */
    val continueWatching: LiveData<List<WatchHistoryEntry>> =
        watchHistoryRepository.getHistoryFlow().asLiveData()

    private val _recentSearches = MutableLiveData<List<String>>(emptyList())
    val recentSearches: LiveData<List<String>> = _recentSearches

    val primaryColorFlow: StateFlow<Int> = themeManager.primaryColorFlow

    var currentSearchQuery = ""
        private set

    private var channelSearchJob: Job? = null

    init {
        loadData()
        refreshRecentSearches()
    }

    override fun loadData() {
        viewModelScope.launch {
            try {
                startLoading()
                val categories = categoryRepository.getCategories()
                _categories.value = categories
                applyFilter()
                finishLoading(dataIsEmpty = categories.isEmpty())
            } catch (e: Exception) {
                _categories.value = emptyList()
                _filteredCategories.value = emptyList()
                finishLoading(dataIsEmpty = true, error = e)
            }
        }
    }

    override fun onResume() {
    }

    fun refreshSilent() {
        viewModelScope.launch {
            try {
                val categories = categoryRepository.getCategories()
                _categories.value = categories
                applyFilter()
            } catch (e: Exception) {
            }
        }
    }

    fun searchCategories(query: String) {
        currentSearchQuery = query
        applyFilter()
        searchChannels(query)
    }

    private fun searchChannels(query: String) {
        channelSearchJob?.cancel()
        channelSearchJob = viewModelScope.launch {
            val results = if (query.isBlank()) {
                emptyList()
            } else {
                try {
                    channelRepository.searchChannels(query)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            if (query.trim() == currentSearchQuery.trim()) {
                _searchResults.value = results
            }
        }
    }

    fun refreshRecentSearches() {
        _recentSearches.value = preferencesManager.getRecentSearches()
    }

    fun clearRecentSearches() {
        preferencesManager.clearRecentSearches()
        _recentSearches.value = emptyList()
    }

    fun clearContinueWatching() {
        viewModelScope.launch {
            try {
                watchHistoryRepository.clearAll()
            } catch (e: Exception) {
            }
        }
    }

    private fun applyFilter() {
        try {
            val allCategories = _categories.value ?: emptyList()
            _filteredCategories.value = if (currentSearchQuery.isBlank()) {
                allCategories
            } else {
                allCategories.filter {
                    it.name.contains(currentSearchQuery, ignoreCase = true) ||
                    it.slug.contains(currentSearchQuery, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
        }
    }
}
