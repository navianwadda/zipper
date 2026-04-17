package com.livetvpro.app.ui.player

import com.livetvpro.app.data.models.Channel

object ChannelListCache {
    private val store = mutableMapOf<String, List<Channel>>()

    fun put(key: String, channels: List<Channel>) {
        store[key] = channels
    }

    fun get(key: String): List<Channel>? = store[key]

    fun remove(key: String) {
        store.remove(key)
    }

    fun clear() {
        store.clear()
    }
}
