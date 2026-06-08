package com.livetvpro.app.ui.networkstream

import androidx.lifecycle.ViewModel
import com.livetvpro.app.data.local.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NetworkStreamViewModel @Inject constructor(
    private val themeManager: ThemeManager
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
}
