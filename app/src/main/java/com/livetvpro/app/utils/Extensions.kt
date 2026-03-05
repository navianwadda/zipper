package com.livetvpro.app.utils

import android.view.View

fun View.show() { visibility = View.VISIBLE }
fun View.hide() { visibility = View.GONE }
fun View.isVisible() = visibility == View.VISIBLE
