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

    val isBigScreenLayout: Boolean get() = isTvDevice || isDesktop

    fun init(context: Context) {
        val pm = context.packageManager

        deviceType = when {
            isTvHardware(pm, context) -> DeviceType.TV
            isWatchHardware(pm)      -> DeviceType.WATCH
            isAutomotiveHardware(pm) -> DeviceType.AUTOMOTIVE
            isDesktopRuntime()       -> DeviceType.DESKTOP
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
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)) return true
        if (pm.hasSystemFeature("amazon.hardware.fire_tv")) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) return false
        if (hasPhysicalTouchscreen()) return false
        if (context.resources.configuration.smallestScreenWidthDp < 450) return false
        return false
    }

    private fun hasPhysicalTouchscreen(): Boolean {
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

    private fun isDesktopRuntime(): Boolean {
        return isWindowsSubsystemForAndroid() ||
            isBlueStacksHardware() ||
            isGenymotionHardware() ||
            isWaydroidHardware() ||
            isAnboxHardware() ||
            isNoxHardware() ||
            isLdPlayerHardware() ||
            isMemuHardware()
    }

    private fun isWindowsSubsystemForAndroid(): Boolean {
        return Build.MANUFACTURER.equals("Microsoft", ignoreCase = true)
            && Build.MODEL.contains("Subsystem for Android", ignoreCase = true)
    }

    private fun isBlueStacksHardware(): Boolean {
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        val model = Build.MODEL
        val product = Build.PRODUCT
        val hardware = Build.HARDWARE
        val fingerprint = Build.FINGERPRINT

        val matchesBuildProps = manufacturer.contains("BlueStacks", ignoreCase = true) ||
            brand.contains("BlueStacks", ignoreCase = true) ||
            model.contains("BlueStacks", ignoreCase = true) ||
            product.contains("BlueStacks", ignoreCase = true) ||
            hardware.contains("BlueStacks", ignoreCase = true) ||
            fingerprint.contains("BlueStacks", ignoreCase = true) ||
            hardware.equals("vbox86", ignoreCase = true)

        if (matchesBuildProps) return true

        return !getSystemProperty("ro.bluestacks.host_ver").isNullOrBlank()
    }

    private fun isGenymotionHardware(): Boolean {
        return Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            Build.PRODUCT.contains("vbox86p", ignoreCase = true) ||
            !getSystemProperty("ro.genymotion.version").isNullOrBlank()
    }

    private fun isWaydroidHardware(): Boolean {
        val hardware = Build.HARDWARE
        val product = Build.PRODUCT
        val device = Build.DEVICE

        val matchesBuildProps = hardware.contains("waydroid", ignoreCase = true) ||
            product.contains("waydroid", ignoreCase = true) ||
            device.contains("waydroid", ignoreCase = true)

        if (matchesBuildProps) return true

        return !getSystemProperty("ro.waydroid.version").isNullOrBlank() ||
            !getSystemProperty("persist.waydroid.version").isNullOrBlank()
    }

    private fun isAnboxHardware(): Boolean {
        return !getSystemProperty("anbox.status").isNullOrBlank() ||
            !getSystemProperty("ro.anbox.version").isNullOrBlank() ||
            Build.PRODUCT.contains("anbox", ignoreCase = true) ||
            Build.DEVICE.contains("anbox", ignoreCase = true)
    }

    private fun isMemuHardware(): Boolean {
        return Build.MANUFACTURER.contains("microvirt", ignoreCase = true) ||
            Build.BRAND.contains("microvirt", ignoreCase = true) ||
            Build.PRODUCT.contains("memu", ignoreCase = true) ||
            Build.MODEL.contains("memu", ignoreCase = true)
    }

    private fun isNoxHardware(): Boolean {
        return Build.MANUFACTURER.contains("nox", ignoreCase = true) ||
            Build.BRAND.contains("nox", ignoreCase = true) ||
            Build.MODEL.contains("nox", ignoreCase = true) ||
            Build.BOARD.contains("nox", ignoreCase = true) ||
            Build.HARDWARE.contains("nox", ignoreCase = true) ||
            !getSystemProperty("ro.nox.version").isNullOrBlank()
    }

    private fun isLdPlayerHardware(): Boolean {
        return Build.MANUFACTURER.contains("ldplayer", ignoreCase = true) ||
            Build.BRAND.contains("ldplayer", ignoreCase = true) ||
            Build.MODEL.contains("ldplayer", ignoreCase = true) ||
            Build.PRODUCT.contains("ldplayer", ignoreCase = true) ||
            Build.HARDWARE.contains("ldplayer", ignoreCase = true) ||
            !getSystemProperty("ro.ldplayer.version").isNullOrBlank()
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun isEmulatorBuild(): Boolean {
        return Build.HARDWARE.equals("goldfish", ignoreCase = true)
            || Build.HARDWARE.equals("ranchu", ignoreCase = true)
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("Android SDK built for x86", ignoreCase = true)
            || Build.MODEL.contains("Emulator", ignoreCase = true)
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
