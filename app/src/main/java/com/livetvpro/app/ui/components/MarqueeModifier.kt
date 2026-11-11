package com.livetvpro.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.marqueeIf(enabled: Boolean, velocity: Dp = 30.dp): Modifier =
    if (enabled) this.basicMarquee(iterations = Int.MAX_VALUE, velocity = velocity) else this
