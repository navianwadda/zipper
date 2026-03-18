package com.livetvpro.app.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

object DeviceUtils {

    enum class DeviceType {
        PHONE,
        TABLET,
        TV,
        WATCH,
        AUTOMOTIVE,
        FOLDABLE,
        EMULATOR
    }

    var deviceType: DeviceType = DeviceType.PHONE
        private set

    var hasTouchInput: Boolean = false
        private set

    val isTvDevice: Boolean get() = deviceType == DeviceType.TV
    val isTablet: Boolean get() = deviceType == DeviceType.TABLET
    val isPhone: Boolean get() = deviceType == DeviceType.PHONE
    val isWatch: Boolean get() = deviceType == DeviceType.WATCH
    val isAutomotive: Boolean get() = deviceType == DeviceType.AUTOMOTIVE
    val isFoldable: Boolean get() = deviceType == DeviceType.FOLDABLE
    val isEmulator: Boolean get() = deviceType == DeviceType.EMULATOR

    fun init(context: Context) {
        val pm = context.packageManager

        deviceType = when {
            isTvHardware(pm) -> DeviceType.TV
            isWatchHardware(context) -> DeviceType.WATCH
            isAutomotiveHardware(pm) -> DeviceType.AUTOMOTIVE
            else -> detectHandheld(context)
        }

        if (deviceType == DeviceType.PHONE || deviceType == DeviceType.TABLET
            || deviceType == DeviceType.FOLDABLE
        ) {
            if (isEmulatorBuild()) {
                deviceType = DeviceType.EMULATOR
            }
        }

        hasTouchInput = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    fun notifyTouchDetected() {
        if (!hasTouchInput) {
            hasTouchInput = true
        }
    }

    private fun isTvHardware(pm: PackageManager): Boolean {
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
            || pm.hasSystemFeature("amazon.hardware.fire_tv")
    }

    private fun isWatchHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    }

    private fun isAutomotiveHardware(pm: PackageManager): Boolean {
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    private fun isEmulatorBuild(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("Emulator", ignoreCase = true)
                || Build.MODEL.contains("Android SDK", ignoreCase = true)
                || Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic"))
    }

    private fun detectHandheld(context: Context): DeviceType {
        val pm = context.packageManager

        if (pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)) {
            return DeviceType.FOLDABLE
        }

        val smallestWidthDp = context.resources.configuration.smallestScreenWidthDp
        if (smallestWidthDp >= 600) {
            return DeviceType.TABLET
        }

        return DeviceType.PHONE
    }
}
