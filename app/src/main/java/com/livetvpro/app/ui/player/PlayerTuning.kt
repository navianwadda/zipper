package com.livetvpro.app.ui.player

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

object PlayerTuning {

    private fun isConstrained(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice == true || (am?.memoryClass ?: 256) <= 128
        val only32Bit = Build.SUPPORTED_64_BIT_ABIS.isNullOrEmpty()
        return lowRam || only32Bit
    }

    fun loadControl(context: Context): LoadControl {
        if (!isConstrained(context)) return DefaultLoadControl.Builder().build()
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 20_000, 1_500, 3_000)
            .setTargetBufferBytes(12 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
    }
}
