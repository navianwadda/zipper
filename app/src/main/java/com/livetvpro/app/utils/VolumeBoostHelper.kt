package com.livetvpro.app.utils

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.livetvpro.app.data.local.PreferencesManager
import java.util.Collections
import java.util.IdentityHashMap

object VolumeBoostHelper {

    const val MAX_BOOST_GAIN_MILLIBEL = 1000

    private val enhancers: MutableMap<ExoPlayer, LoudnessEnhancer> =
        Collections.synchronizedMap(IdentityHashMap())

    private val desiredBoost: MutableMap<ExoPlayer, Int> =
        Collections.synchronizedMap(IdentityHashMap())

    fun attach(player: ExoPlayer, preferencesManager: PreferencesManager) {
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                reattach(player, audioSessionId, preferencesManager.isVolumeBoostingEnabled())
            }
        })
        if (player.audioSessionId != 0) {
            reattach(player, player.audioSessionId, preferencesManager.isVolumeBoostingEnabled())
        }
    }

    fun setBoostLevel(player: ExoPlayer, boostPercent: Int) {
        val clamped = boostPercent.coerceIn(0, 100)
        desiredBoost[player] = clamped
        try {
            val enhancer = enhancers[player] ?: return
            enhancer.setTargetGain((clamped * MAX_BOOST_GAIN_MILLIBEL) / 100)
            enhancer.enabled = clamped > 0
        } catch (e: Exception) {
        }
    }

    fun release(player: ExoPlayer) {
        try {
            enhancers.remove(player)?.release()
        } catch (e: Exception) {
        }
        desiredBoost.remove(player)
    }

    private fun reattach(player: ExoPlayer, audioSessionId: Int, allowed: Boolean) {
        try {
            enhancers.remove(player)?.release()
            if (audioSessionId == 0 || !allowed) return
            val pending = desiredBoost[player] ?: 0
            val enhancer = LoudnessEnhancer(audioSessionId)
            enhancer.setTargetGain((pending * MAX_BOOST_GAIN_MILLIBEL) / 100)
            enhancer.enabled = pending > 0
            enhancers[player] = enhancer
        } catch (e: Exception) {
        }
    }
}
