package com.livetvpro.app.debug

import android.os.Handler
import android.os.Looper

object TestCrash {
    fun trigger() {
        Handler(Looper.getMainLooper()).post {
            throw RuntimeException("Test crash triggered")
        }
    }
}
