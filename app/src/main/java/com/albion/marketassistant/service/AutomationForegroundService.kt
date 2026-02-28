// FILE: app/src/main/java/com/albion/marketassistant/service/AutomationForegroundService.kt
// UPDATED: Works with new percentage-based automation

package com.albion.marketassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.albion.marketassistant.R
import com.albion.marketassistant.accessibility.MarketAccessibilityService
import com.albion.marketassistant.accessibility.StateMachine
import com.albion.marketassistant.data.*
import com.albion.marketassistant.db.CalibrationDatabase
import com.albion.marketassistant.ui.MainActivity
import com.albion.marketassistant.ui.overlay.FloatingOverlayManager
import kotlinx.coroutines.*

class AutomationForegroundService : Service() {

    companion object {
        private const val TAG = "AutomationService"
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "AlbionAssistant"

        const val ACTION_CREATE_MODE = "com.albion.CREATE_MODE"
        const val ACTION_EDIT_MODE = "com.albion.EDIT_MODE"
        const val ACTION_STOP_MODE = "com.albion.STOP_MODE"
        const val ACTION_PAUSE = "com.albion.PAUSE"
        const val ACTION_RESUME = "com.albion.RESUME"
        const val ACTION_ACCESSIBILITY_READY = "com.albion.ACCESSIBILITY_READY"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateMachine: StateMachine? = null
    private var isAccessibilityReady = false
    private var pendingMode: OperationMode? = null
    private var currentMode: OperationMode? = null
    private var floatingOverlayManager: FloatingOverlayManager? = null
    private var currentCalibration: CalibrationData? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ACCESSIBILITY_READY -> {
                    isAccessibilityReady = true
                    showToast("✓ Accessibility Service Ready")
                    pendingMode?.let { mode ->
                        startAutomationMode(mode)
                        pendingMode = null
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val intentFilter = IntentFilter(ACTION_ACCESSIBILITY_READY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(broadcastReceiver, intentFilter)
        }

        isAccessibilityReady = MarketAccessibilityService.isServiceEnabled()

        floatingOverlayManager = FloatingOverlayManager(this) { action ->
            handleOverlayAction(action)
        }

        startForeground(NOTIFICATION_ID, createNotification("Ready", "Tap Create or Edit to start"))
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_CREATE_MODE -> handleCreateMode()
                ACTION_EDIT_MODE -> handleEditMode()
                ACTION_STOP_MODE -> handleStopMode()
                ACTION_PAUSE -> handlePause()
                ACTION_RESUME -> handleResume()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroying")
        stateMachine?.stop()
        floatingOverlayManager?.hide()
        serviceScope.cancel()
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        super.onDestroy()
    }

    private fun handleOverlayAction(action: String) {
        when (action) {
            "CREATE" -> handleCreateMode()
            "EDIT" -> handleEditMode()
            "PAUSE" -> handlePause()
            "RESUME" -> handleResume()
            "STOP" -> handleStopMode()
            "STATS" -> showStatisticsToast()
        }
    }

    private fun handleCreateMode() {
        if (!checkPermissions()) return
        
        currentMode = OperationMode.NEW_ORDER_SWEEPER
        showToast("🟢 Create Buy Order Mode Starting...")
        startAutomationMode(OperationMode.NEW_ORDER_SWEEPER)
    }

    private fun handleEditMode() {
        if (!checkPermissions()) return
        
        currentMode = OperationMode.ORDER_EDITOR
        showToast("🔵 Edit Buy Order Mode Starting...")
        startAutomationMode(OperationMode.ORDER_EDITOR)
    }

    private fun handlePause() {
        stateMachine?.pause()
        floatingOverlayManager?.updateStatus("PAUSED")
        updateNotification("Paused", "")
        showToast("⏸ Paused")
    }

    private fun handleResume() {
        stateMachine?.resume()
        floatingOverlayManager?.updateStatus("Running")
        showToast("▶ Resumed")
    }

    private fun handleStopMode() {
        stateMachine?.stop()
        stateMachine = null
        currentMode = null
        pendingMode = null
        currentCalibration = null
        floatingOverlayManager?.hide()
        showToast("⏹ Stopped")
        updateNotification("Ready", "Tap Create or Edit to start")
        Log.d(TAG, "Automation stopped")
    }

    private fun checkPermissions(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            showToast("Please grant 'Display over other apps' permission")
            return false
        }

        if (!isAccessibilityReady && !MarketAccessibilityService.isServiceEnabled()) {
            showToast("Please enable Accessibility Service in Settings")
            return false
        }

        isAccessibilityReady = true
        return true
    }

    private fun startAutomationMode(mode: OperationMode) {
        serviceScope.launch {
            try {
                // Load calibration from database
                val database = CalibrationDatabase.getInstance(applicationContext)
                val calibration = database.calibrationDao().getCalibration() ?: CalibrationData()
                currentCalibration = calibration

                val accessibilityService = MarketAccessibilityService.getInstance()

                if (accessibilityService == null) {
                    showToast("❌ Accessibility Service not available")
                    updateNotification("Error", "Service not available")
                    return@launch
                }

                // Pass calibration to the accessibility service
                accessibilityService.setCalibration(calibration)

                // Create state machine
                stateMachine?.stop()
                stateMachine = StateMachine(serviceScope, calibration, accessibilityService, this@AutomationForegroundService)

                // Setup callbacks
                stateMachine?.onStateChange = { state ->
                    val modeText = when (mode) {
                        OperationMode.NEW_ORDER_SWEEPER -> "CREATE"
                        OperationMode.ORDER_EDITOR -> "EDIT"
                        OperationMode.IDLE -> "IDLE"
                    }
                    updateNotification("$modeText: ${state.stateType}", "Items: ${state.itemsProcessed}")
                    floatingOverlayManager?.updateStatus(state.stateType.name)
                    floatingOverlayManager?.updateProgress(state.itemsProcessed, state.statistics.successfulOperations)
                }

                stateMachine?.onError = { error ->
                    showToast("❌ Error: $error")
                    floatingOverlayManager?.updateStatus("ERROR")
                }

                stateMachine?.onPriceSanityError = { error ->
                    showToast("⚠️ SAFETY: $error")
                    floatingOverlayManager?.updateStatus("SAFETY HALT")
                    stateMachine?.pause()
                }

                stateMachine?.onEndOfList = {
                    showToast("✅ All items processed!")
                    floatingOverlayManager?.updateStatus("COMPLETE")
                }

                stateMachine?.onProgressUpdate = { processed, total ->
                    floatingOverlayManager?.updateProgress(processed, total)
                }

                stateMachine?.onScreenshotRequest = {
                    accessibilityService.captureScreen()
                }

                // Start automation
                stateMachine?.startMode(mode)

                val modeName = when (mode) {
                    OperationMode.NEW_ORDER_SWEEPER -> "CREATE BUY ORDER"
                    OperationMode.ORDER_EDITOR -> "EDIT BUY ORDER"
                    OperationMode.IDLE -> "IDLE"
                }

                showToast("✅ $modeName started")
                updateNotification("Running: $modeName", "Initializing...")
                floatingOverlayManager?.show()
                floatingOverlayManager?.updateStatus("Running")

                Log.d(TAG, "Automation mode started: $mode")

            } catch (e: Exception) {
                showToast("❌ Error: ${e.message}")
                Log.e(TAG, "Error starting automation", e)
                updateNotification("Error", e.message ?: "Unknown error")
            }
        }
    }

    private fun showStatisticsToast() {
        val stats = stateMachine?.getStatistics() ?: return
        val message = buildString {
            append("Items: ${stats.successfulOperations}")
            append(" | Errors: ${stats.consecutiveErrors}")
            if (stats.lastPrice != null) {
                append(" | Last Price: ${stats.lastPrice}")
            }
        }
        showToast(message)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Albion Market Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Albion Market Automation Service"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String, subText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛒 Albion Market Assistant")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setSubText(subText)
            .build()
    }

    private fun updateNotification(status: String, subText: String) {
        try {
            val notification = createNotification(status, subText)
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
