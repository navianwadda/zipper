package com.livetvpro.app.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.models.Playlist
import com.livetvpro.app.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val themeManager: ThemeManager,
) : ViewModel() {

    val primaryColorFlow: StateFlow<Int> = themeManager.primaryColorFlow

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists()
                .onStart { _isLoading.value = true }
                .catch { e ->
                    _error.value = "Failed to load playlists: ${e.message}"
                    emit(emptyList())
                }
                .onEach { _isLoading.value = false }
                .collect { _playlists.value = it }
        }
    }

    fun addPlaylist(title: String, url: String = "", isFile: Boolean = false, filePath: String = "") {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                playlistRepository.addPlaylist(title, url, isFile, filePath)
            } catch (e: Exception) {
                _error.value = "Failed to add playlist: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updatePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                playlistRepository.updatePlaylist(playlist)
            } catch (e: Exception) {
                _error.value = "Failed to update playlist: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                playlistRepository.deletePlaylist(playlist)
            } catch (e: Exception) {
                _error.value = "Failed to delete playlist: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
