package com.albion.marketassistant.util

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log

class DeviceUtils(private val context: Context) {

    companion object {
        private const val TAG = "DeviceUtils"
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun getBatteryPercentage(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            100
        }
    }

    fun isCharging(): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun isBatteryLow(threshold: Int = 20): Boolean {
        return getBatteryPercentage() < threshold
    }

    fun isScreenOn(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }
    }

    /**
     * Get the foreground app package name
     * Fixed: Use UsageStatsManager for Android 5.1+
     */
    fun getForegroundAppPackage(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                getForegroundAppPackageViaUsageStats()
                    ?: getForegroundAppPackageLegacy()
            } else {
                getForegroundAppPackageLegacy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app", e)
            null
        }
    }

    /**
     * Get foreground app using UsageStatsManager (Android 5.1+)
     */
    private fun getForegroundAppPackageViaUsageStats(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null
        
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 10000 // Last 10 seconds
            
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            var currentApp: String? = null
            
            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()
                usageEvents.getNextEvent(event)
                
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    currentApp = event.packageName
                }
            }
            
            currentApp
        } catch (e: Exception) {
            Log.e(TAG, "UsageStats method failed", e)
            null
        }
    }

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
            Log.e(TAG, "Legacy method failed", e)
            null
        }
    }

    /**
     * Fixed: Correct package name matching logic
     */
    fun isAppInForeground(packageName: String): Boolean {
        val foregroundPackage = getForegroundAppPackage() ?: return false
        
        // Exact match
        if (foregroundPackage == packageName) return true
        
        // Handle package variations (e.g., com.albiononline vs com.albiononline.albiononline)
        // foregroundPackage.startsWith("${packageName}.") handles game launchers
        // that prefix the base package
        if (foregroundPackage.startsWith("$packageName.")) return true
        
        return false
    }

    fun isPowerSaveMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
    }

    fun getAvailableMemory(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / (1024 * 1024)
    }

    fun isLowMemory(): Boolean {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }

    fun getTotalMemory(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024 * 1024)
    }

    fun getMemoryUsagePercent(): Int {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val usedMemory = memoryInfo.totalMem - memoryInfo.availMem
        return ((usedMemory.toFloat() / memoryInfo.totalMem) * 100).toInt()
    }

    fun hasBeenIdleSince(timestamp: Long, thresholdMs: Long): Boolean {
        return System.currentTimeMillis() - timestamp > thresholdMs
    }

    fun getScreenDimensions(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
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

    fun getRecommendedOcrDelay(baseDelayMs: Long, batteryThreshold: Int = 30): Long {
        val batteryPercent = getBatteryPercentage()
        return when {
            isCharging() -> baseDelayMs
            batteryPercent < batteryThreshold -> baseDelayMs * 2
            isPowerSaveMode() -> baseDelayMs * 3
            isLowMemory() -> baseDelayMs * 2
            else -> baseDelayMs
        }
    }

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
