package com.livetvpro.app.ui.navigation

/**
 * Route definitions for androidx.navigation.compose, replacing the old
 * nav_graph.xml fragment-based destinations one-to-one.
 *
 * [Routes.Args] holds the argument *names* (kept identical to the old
 * nav_graph.xml <argument> names) so destinations can read them via
 * SavedStateHandle / NavBackStackEntry.arguments without retyping strings.
 */
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

    object Args {
        const val CATEGORY_ID = "categoryId"
        const val CATEGORY_NAME = "categoryName"
    }

    /** Builds a concrete category_channels route for navigation calls. */
    fun categoryChannels(categoryId: String, categoryName: String): String {
        val encodedId = java.net.URLEncoder.encode(categoryId, "UTF-8")
        val encodedName = java.net.URLEncoder.encode(categoryName, "UTF-8")
        return "category_channels/$encodedId/$encodedName"
    }

    /** Top-level destinations that show the bottom nav / are back-stack roots. */
    val PHONE_TOP_LEVEL = setOf(HOME, LIVE_EVENTS, SPORTS, SETTINGS)
    val TV_TOP_LEVEL = setOf(HOME, LIVE_EVENTS, SPORTS, FAVORITES, SETTINGS)

    /** Destinations that show a refresh icon in the top bar. */
    val REFRESH_DESTINATIONS = setOf(HOME, LIVE_EVENTS, SPORTS, FAVORITES)
    // Note: CATEGORY_CHANNELS is matched by route pattern, handled separately
    // since it carries path arguments (see MainScaffold's startsWith check).
}
