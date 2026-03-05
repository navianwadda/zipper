package com.livetvpro.app.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

        deviceType = when (uiModeManager.currentModeType) {
            Configuration.UI_MODE_TYPE_WATCH -> DeviceType.WATCH
            Configuration.UI_MODE_TYPE_CAR -> DeviceType.AUTOMOTIVE
            Configuration.UI_MODE_TYPE_TELEVISION -> DeviceType.TV
            else -> detectHandheld(context)
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            deviceType = DeviceType.TV
        }

        if (pm.hasSystemFeature("amazon.hardware.fire_tv") && !hasPhysicalTouchInputDevice()) {
            deviceType = DeviceType.TV
        }

        if (deviceType != DeviceType.TV
            && !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
            && !hasPhysicalTouchInputDevice()
            && context.resources.configuration.smallestScreenWidthDp >= 720
        ) {
            deviceType = DeviceType.TV
        }

        if (deviceType == DeviceType.PHONE || deviceType == DeviceType.TABLET
            || deviceType == DeviceType.FOLDABLE
        ) {
            if (isX86OrEmulator()) {
                deviceType = DeviceType.EMULATOR
            }
        }

        hasTouchInput = hasPhysicalTouchInputDevice()
    }

    fun notifyTouchDetected() {
        if (!hasTouchInput) {
            hasTouchInput = true
        }
    }

    private fun hasPhysicalTouchInputDevice(): Boolean {
        return try {
            InputDevice.getDeviceIds().any { id ->
                val device = InputDevice.getDevice(id) ?: return@any false
                if (device.isVirtual) return@any false
                (device.sources and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isX86OrEmulator(): Boolean {
        val supportedAbis = Build.SUPPORTED_ABIS
        val isX86 = supportedAbis.any { it.startsWith("x86") }

        val isEmulatorBuild = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("Emulator", ignoreCase = true)
                || Build.MODEL.contains("Android SDK", ignoreCase = true)
                || Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic"))

        return isX86 || isEmulatorBuild
    }

    private fun detectHandheld(context: Context): DeviceType {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (context.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")) {
                return DeviceType.FOLDABLE
            }
        }

        val smallestWidthDp = context.resources.configuration.smallestScreenWidthDp
        if (smallestWidthDp >= 600) {
            return DeviceType.TABLET
        }

        return DeviceType.PHONE
    }
}
