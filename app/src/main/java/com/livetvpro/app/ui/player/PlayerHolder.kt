package com.livetvpro.app.ui.player

import androidx.media3.exoplayer.ExoPlayer

object PlayerHolder {
    var player: ExoPlayer? = null
    var streamUrl: String? = null
    var channelName: String? = null
    
    fun transferPlayer(exoPlayer: ExoPlayer, url: String, name: String) {
        player = exoPlayer
        streamUrl = url
        channelName = name
        android.util.Log.d("PlayerHolder", "Player transferred - no recreation needed!")
    }
    
    fun retrievePlayer(): Triple<ExoPlayer?, String?, String?> {
        val result = Triple(player, streamUrl, channelName)
        return result
    }
    
    fun clearReferences() {
        player = null
        streamUrl = null
        channelName = null
    }
    
    fun releaseAndClear() {
        player?.release()
        player = null
        streamUrl = null
        channelName = null
    }
}
