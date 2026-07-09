package com.livetvpro.app.ui.networkstream

import androidx.lifecycle.ViewModel
import com.livetvpro.app.data.local.NetworkStreamHistoryManager
import com.livetvpro.app.data.local.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NetworkStreamViewModel @Inject constructor(
    private val themeManager: ThemeManager,
    private val historyManager: NetworkStreamHistoryManager,
) : ViewModel() {
    val primaryColorFlow: StateFlow<Int> = themeManager.primaryColorFlow
    var streamUrl: String = ""
    var cookie: String = ""
    var referer: String = ""
    var origin: String = ""
    var drmLicense: String = ""
    var customUserAgent: String = ""
    var selectedUserAgent: String = "Default"
    var selectedDrmScheme: String = "clearkey"

    fun recordPlayed(url: String) = historyManager.addEntry(url)
}
