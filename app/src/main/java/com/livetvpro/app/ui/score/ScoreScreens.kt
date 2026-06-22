package com.livetvpro.app.ui.score

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.livetvpro.app.utils.NativeListenerManager

@Composable
fun CricketScoreScreen(listenerManager: NativeListenerManager) {
    val url = remember { listenerManager.getCricLiveUrl() }
    ScoreWebScreen(url = url)
}

@Composable
fun FootballScoreScreen(listenerManager: NativeListenerManager) {
    val url = remember { listenerManager.getFootLiveUrl() }
    ScoreWebScreen(url = url)
}
