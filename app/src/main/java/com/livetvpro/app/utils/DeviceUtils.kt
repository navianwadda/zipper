package com.livetvpro.app.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice

object DeviceUtils {

    enum class DeviceType {
        PHONE,
        TABLET,
        TV,
        WATCH,
        AUTOMOTIVE,
        FOLDABLE,
        EMULATOR,
        DESKTOP
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
    val isDesktop: Boolean get() = deviceType == DeviceType.DESKTOP

    fun init(context: Context) {
        val pm = context.packageManager

        deviceType = when {
            isTvHardware(pm, context) -> DeviceType.TV
            isWatchHardware(pm)      -> DeviceType.WATCH
            isAutomotiveHardware(pm) -> DeviceType.AUTOMOTIVE
            isWsaDesktop()           -> DeviceType.DESKTOP
            isEmulatorBuild()        -> DeviceType.EMULATOR
            else                     -> detectHandheld(context)
        }

        hasTouchInput = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    fun notifyTouchDetected() {
        if (!hasTouchInput) {
            hasTouchInput = true
        }
    }

    private fun isTvHardware(pm: PackageManager, context: Context): Boolean {
        // Use InputDevice to detect a physical touchscreen — this checks actual hardware
        // and cannot be fooled by the manifest declaring touchscreen as required="false",
        // which causes hasSystemFeature(FEATURE_TOUCHSCREEN) to return false on some OEMs
        // even on real touch phones, making them incorrectly classified as TV devices.
        if (hasPhysicalTouchscreen()) return false

        // Guard against phones being misclassified as TV when rotated to landscape.
        // The touchscreen InputDevice check above can miss on some devices at init time
        // (e.g. when rotated, input devices may not yet be fully reported). Phones have
        // smallestScreenWidthDp well below 450dp, while real TV boxes are 600dp+.
        if (context.resources.configuration.smallestScreenWidthDp < 450) return false

        // Double-check with UiModeManager — the authoritative TV signal on Android.
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true

        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
            || pm.hasSystemFeature("amazon.hardware.fire_tv")
    }

    private fun hasPhysicalTouchscreen(): Boolean {
        // Check all connected input devices for a touchscreen source.
        // This is hardware-level and unaffected by manifest uses-feature declarations.
        return InputDevice.getDeviceIds().any { id ->
            val device = InputDevice.getDevice(id) ?: return@any false
            !device.isVirtual &&
                (device.sources and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN
        }
    }

    private fun isWatchHardware(pm: PackageManager): Boolean {
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
    }

    private fun isAutomotiveHardware(pm: PackageManager): Boolean {
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    private fun isWsaDesktop(): Boolean {
        return Build.MANUFACTURER.equals("Microsoft", ignoreCase = true)
            && Build.MODEL.contains("Subsystem for Android", ignoreCase = true)
    }

    private fun isEmulatorBuild(): Boolean {
        return Build.HARDWARE.equals("goldfish", ignoreCase = true)
            || Build.HARDWARE.equals("ranchu", ignoreCase = true)
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("Android SDK built for x86", ignoreCase = true)
            || Build.MODEL.contains("Emulator", ignoreCase = true)
            || Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }

    private fun detectHandheld(context: Context): DeviceType {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)) {
            return DeviceType.FOLDABLE
        }
        if (context.resources.configuration.smallestScreenWidthDp >= 600) {
            return DeviceType.TABLET
        }
        return DeviceType.PHONE
    }
}
