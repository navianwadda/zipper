package com.livetvpro.app.utils

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.livetvpro.app.data.local.PreferencesManager
import java.util.Collections
import java.util.IdentityHashMap

object VolumeBoostHelper {

    private const val TAG = "VolumeBoostHelper"

    const val MAX_BOOST_GAIN_MILLIBEL = 2000

    private val enhancers: MutableMap<ExoPlayer, LoudnessEnhancer> =
        Collections.synchronizedMap(IdentityHashMap())

    private val desiredBoost: MutableMap<ExoPlayer, Int> =
        Collections.synchronizedMap(IdentityHashMap())

    private val listeners: MutableMap<ExoPlayer, Player.Listener> =
        Collections.synchronizedMap(IdentityHashMap())

    @Suppress("UNUSED_PARAMETER")
    fun attach(player: ExoPlayer, preferencesManager: PreferencesManager) {
        if (listeners.containsKey(player)) {
            rebuild(player, player.audioSessionId)
            return
        }
        val listener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                rebuild(player, audioSessionId)
            }
        }
        listeners[player] = listener
        player.addListener(listener)
        rebuild(player, player.audioSessionId)
    }

    fun setBoostLevel(player: ExoPlayer, boostPercent: Int) {
        val clamped = boostPercent.coerceIn(0, 100)
        desiredBoost[player] = clamped
        if (enhancers[player] == null && clamped > 0) {
            rebuild(player, player.audioSessionId)
        }
        val enhancer = enhancers[player] ?: return
        try {
            enhancer.setTargetGain((clamped * MAX_BOOST_GAIN_MILLIBEL) / 100)
            enhancer.enabled = clamped > 0
        } catch (e: Throwable) {
            Log.w(TAG, "setBoostLevel failed", e)
        }
    }

    fun release(player: ExoPlayer) {
        try {
            listeners.remove(player)?.let { player.removeListener(it) }
        } catch (e: Throwable) {
        }
        try {
            enhancers.remove(player)?.release()
        } catch (e: Throwable) {
        }
        desiredBoost.remove(player)
    }

    private fun rebuild(player: ExoPlayer, audioSessionId: Int) {
        try {
            enhancers.remove(player)?.release()
        } catch (e: Throwable) {
        }
        if (audioSessionId == 0) return
        try {
            val pending = desiredBoost[player] ?: 0
            val enhancer = LoudnessEnhancer(audioSessionId)
            enhancer.setTargetGain((pending * MAX_BOOST_GAIN_MILLIBEL) / 100)
            enhancer.enabled = pending > 0
            enhancers[player] = enhancer
        } catch (e: Throwable) {
            Log.w(TAG, "LoudnessEnhancer creation failed for session $audioSessionId", e)
        }
    }
}
