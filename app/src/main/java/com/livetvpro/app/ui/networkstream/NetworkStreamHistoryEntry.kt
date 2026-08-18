package com.livetvpro.app.data.local

data class NetworkStreamHistoryEntry(
    val id: Long,
    val url: String,
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val drmLicense: String = "",
    val selectedUserAgent: String = "Default",
    val customUserAgent: String = "",
    val drmScheme: String = "clearkey",
    val customHeaders: String = "",
)
