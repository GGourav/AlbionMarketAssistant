package com.albion.marketassistant.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager

/**
 * Utility class for device-related operations
 * Battery monitoring, window verification, etc.
 */
class DeviceUtils(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Get current battery percentage
     */
    fun getBatteryPercentage(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            100 // Assume full battery if can't read
        }
    }

    /**
     * Check if device is charging
     */
    fun isCharging(): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * Check if battery is low (below threshold)
     */
    fun isBatteryLow(threshold: Int = 20): Boolean {
        return getBatteryPercentage() < threshold
    }

    /**
     * Check if screen is on
     */
    fun isScreenOn(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }
    }

    /**
     * Get the current foreground app package name
     */
    fun getForegroundAppPackage(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val appTasks = activityManager.appTasks
                if (appTasks.isNotEmpty()) {
                    appTasks[0].taskInfo.baseActivity?.packageName
                } else {
                    getForegroundAppPackageLegacy()
                }
            } else {
                getForegroundAppPackageLegacy()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Legacy method for getting foreground app
     */
    @Suppress("DEPRECATION")
    private fun getForegroundAppPackageLegacy(): String? {
        return try {
            val runningTasks = activityManager.getRunningTasks(1)
            if (runningTasks.isNotEmpty()) {
                runningTasks[0].topActivity?.packageName
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a specific app is in foreground
     */
    fun isAppInForeground(packageName: String): Boolean {
        val foregroundPackage = getForegroundAppPackage()
        return foregroundPackage == packageName
    }

    /**
     * Check if the device is in power save mode
     */
    fun isPowerSaveMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
    }

    /**
     * Get available memory in MB
     */
    fun getAvailableMemory(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / (1024 * 1024)
    }

    /**
     * Check if device is under memory pressure
     */
    fun isLowMemory(): Boolean {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }

    /**
     * Get total memory in MB
     */
    fun getTotalMemory(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024 * 1024)
    }

    /**
     * Calculate memory usage percentage
     */
    fun getMemoryUsagePercent(): Int {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val usedMemory = memoryInfo.totalMem - memoryInfo.availMem
        return ((usedMemory.toFloat() / memoryInfo.totalMem) * 100).toInt()
    }

    /**
     * Check if device has been idle for a while
     * Useful for detecting if user is away
     */
    fun hasBeenIdleSince(timestamp: Long, thresholdMs: Long): Boolean {
        return System.currentTimeMillis() - timestamp > thresholdMs
    }

    /**
     * Get device screen dimensions
     */
    fun getScreenDimensions(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val point = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getSize(point)
            Pair(point.x, point.y)
        }
    }

    /**
     * Calculate recommended OCR frequency based on device state
     */
    fun getRecommendedOcrDelay(baseDelayMs: Long, batteryThreshold: Int = 30): Long {
        val batteryPercent = getBatteryPercentage()
        
        return when {
            isCharging() -> baseDelayMs // Normal speed when charging
            batteryPercent < batteryThreshold -> baseDelayMs * 2 // Slower when battery low
            isPowerSaveMode() -> baseDelayMs * 3 // Much slower in power save
            isLowMemory() -> baseDelayMs * 2 // Slower when memory pressure
            else -> baseDelayMs
        }
    }

    /**
     * Check if device is in a suitable state for automation
     */
    fun isSuitableForAutomation(
        minBattery: Int = 15,
        requireScreenOn: Boolean = true,
        requiredApp: String? = null
    ): Pair<Boolean, String> {
        if (requireScreenOn && !isScreenOn()) {
            return Pair(false, "Screen is off")
        }

        if (getBatteryPercentage() < minBattery && !isCharging()) {
            return Pair(false, "Battery too low (${getBatteryPercentage()}%)")
        }

        if (requiredApp != null && !isAppInForeground(requiredApp)) {
            return Pair(false, "Required app not in foreground")
        }

        if (isLowMemory()) {
            return Pair(false, "Low memory condition")
        }

        return Pair(true, "OK")
    }

    /**
     * Device state summary for logging
     */
    fun getDeviceStateSummary(): String {
        return buildString {
            append("Battery: ${getBatteryPercentage()}%")
            append(if (isCharging()) " (charging)" else "")
            append(", Screen: ${if (isScreenOn()) "on" else "off"}")
            append(", Memory: ${getMemoryUsagePercent()}%")
            append(if (isPowerSaveMode()) ", PowerSave" else "")
        }
    }
}
