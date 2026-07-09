package com.livetvpro.app.ui.networkstream

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.livetvpro.app.data.local.NetworkStreamHistoryEntry
import com.livetvpro.app.data.local.NetworkStreamHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NetworkStreamHistoryViewModel @Inject constructor(
    private val historyManager: NetworkStreamHistoryManager,
) : ViewModel() {

    var newestFirst by mutableStateOf(historyManager.isNewestFirst())
        private set

    var entries by mutableStateOf<List<NetworkStreamHistoryEntry>>(emptyList())
        private set

    init {
        refresh()
    }

    fun setNewestFirst(value: Boolean) {
        newestFirst = value
        historyManager.setNewestFirst(value)
        refresh()
    }

    fun removeEntry(id: Long) {
        historyManager.removeEntry(id)
        refresh()
    }

    fun clearAll() {
        historyManager.clearAll()
        refresh()
    }

    private fun refresh() {
        entries = historyManager.getEntries(newestFirst)
    }
}
