package com.livetvpro.app.ui.navigation

object Routes {
    const val HOME = "home"
    const val CATEGORY_CHANNELS = "category_channels/{categoryId}/{categoryName}"
    const val LIVE_EVENTS = "live_events"
    const val SPORTS = "sports"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val PLAYLISTS = "playlists"
    const val NETWORK_STREAM = "network_stream"
    const val CRICKET_SCORE = "cricket_score"
    const val FOOTBALL_SCORE = "football_score"
    const val DEVICE_ID = "device_id"
    const val APPEARANCE = "appearance"
    const val PLAYER_LAYOUTS = "player_layouts"

    object Args {
        const val CATEGORY_ID = "categoryId"
        const val CATEGORY_NAME = "categoryName"
    }

    fun categoryChannels(categoryId: String, categoryName: String): String {
        val encodedId = java.net.URLEncoder.encode(categoryId, "UTF-8").replace("+", "%20")
        val encodedName = java.net.URLEncoder.encode(categoryName, "UTF-8").replace("+", "%20")
        return "category_channels/$encodedId/$encodedName"
    }

    val PHONE_TOP_LEVEL = setOf(HOME, LIVE_EVENTS, SPORTS, SETTINGS)
    val TV_TOP_LEVEL = setOf(HOME, LIVE_EVENTS, SPORTS, FAVORITES, SETTINGS)

    val REFRESH_DESTINATIONS = setOf(HOME, LIVE_EVENTS, SPORTS, FAVORITES)
}
