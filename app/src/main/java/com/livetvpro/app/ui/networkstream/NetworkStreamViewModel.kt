package com.livetvpro.app.ui.networkstream

import androidx.lifecycle.ViewModel

class NetworkStreamViewModel : ViewModel() {
    var streamUrl: String = ""
    var cookie: String = ""
    var referer: String = ""
    var origin: String = ""
    var drmLicense: String = ""
    var customUserAgent: String = ""
    var selectedUserAgent: String = "Default"
    var selectedDrmScheme: String = "clearkey"
}
