// FILE: app/src/main/java/com/albion/marketassistant/service/AutomationForegroundService.kt
// UPDATED: v3 - Fixed HANDLE_ERROR_POPUP with step logging + correct +1 outbid flow

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
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
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
        
        // Keys for SharedPreferences
        const val PREF_DEBUG_MODE = "debug_mode"
        const val PREF_MAX_ITEMS_CREATE = "max_items_create"
        const val PREF_MAX_ORDERS_EDIT = "max_orders_edit"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateMachine: StateMachine? = null
    private var isAccessibilityReady = false
    private var pendingMode: OperationMode? = null
    private var currentMode: OperationMode? = null
    private var floatingOverlayManager: FloatingOverlayManager? = null
    private var currentCalibration: CalibrationData? = null
    private lateinit var sharedPreferences: SharedPreferences

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
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

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
        val debugMode = sharedPreferences.getBoolean(PREF_DEBUG_MODE, false)
        showToast("🟢 Create Buy Order Mode${if (debugMode) " [DEBUG]" else ""}")
        startAutomationMode(OperationMode.NEW_ORDER_SWEEPER)
    }

    private fun handleEditMode() {
        if (!checkPermissions()) return
        
        currentMode = OperationMode.ORDER_EDITOR
        val debugMode = sharedPreferences.getBoolean(PREF_DEBUG_MODE, false)
        showToast("🔵 Edit Buy Order Mode${if (debugMode) " [DEBUG]" else ""}")
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
                // Get debug mode from SharedPreferences
                val debugMode = sharedPreferences.getBoolean(PREF_DEBUG_MODE, false)
                
                // Load calibration from database
                val database = CalibrationDatabase.getInstance(applicationContext)
                var calibration = database.calibrationDao().getCalibration() ?: CalibrationData()
                
                // Override max items from SharedPreferences if set
                val maxItemsCreate = sharedPreferences.getInt(PREF_MAX_ITEMS_CREATE, -1)
                val maxOrdersEdit = sharedPreferences.getInt(PREF_MAX_ORDERS_EDIT, -1)
                
                if (maxItemsCreate > 0 || maxOrdersEdit > 0) {
                    calibration = calibration.copy(
                        createMode = calibration.createMode.copy(
                            maxItemsToProcess = if (maxItemsCreate > 0) maxItemsCreate else calibration.createMode.maxItemsToProcess
                        ),
                        editMode = calibration.editMode.copy(
                            maxOrdersToEdit = if (maxOrdersEdit > 0) maxOrdersEdit else calibration.editMode.maxOrdersToEdit
                        )
                    )
                }
                
                // In debug mode, only process 1 item
                if (debugMode) {
                    calibration = calibration.copy(
                        createMode = calibration.createMode.copy(maxItemsToProcess = 1),
                        editMode = calibration.editMode.copy(maxOrdersToEdit = 1)
                    )
                }
                
                currentCalibration = calibration

                val accessibilityService = MarketAccessibilityService.getInstance()

                if (accessibilityService == null) {
                    showToast("❌ Accessibility Service not available")
                    updateNotification("Error", "Service not available")
                    return@launch
                }

                // Pass calibration to the accessibility service
                accessibilityService.setCalibration(calibration)

                // Create state machine with debug mode
                stateMachine?.stop()
                stateMachine = StateMachine(
                    scope = serviceScope,
                    calibration = calibration,
                    accessibilityService = accessibilityService,
                    context = this@AutomationForegroundService,
                    debugMode = debugMode
                )

                // Setup callbacks
                stateMachine?.onStateChange = { state ->
                    val modeText = when (mode) {
                        OperationMode.NEW_ORDER_SWEEPER -> "CREATE"
                        OperationMode.ORDER_EDITOR -> "EDIT"
                        OperationMode.IDLE -> "IDLE"
                    }
                    
                    val statusText = "${state.stateType.name}"
                    val subText = "Items: ${state.itemsProcessed}"
                    
                    updateNotification("$modeText: $statusText", subText)
                    floatingOverlayManager?.updateStatus(statusText)
                    floatingOverlayManager?.updateProgress(state.itemsProcessed, 
                        if (mode == OperationMode.NEW_ORDER_SWEEPER) calibration.createMode.maxItemsToProcess 
                        else calibration.editMode.maxOrdersToEdit)
                    
                    // Show error if any
                    state.errorMessage?.let { error ->
                        if (error.contains("FAILED") || error.contains("ERROR")) {
                            showErrorDialog(error)
                        }
                    }
                }

                stateMachine?.onError = { error ->
                    showErrorDialog(error)
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

                stateMachine?.onStepUpdate = { step ->
                    // Update notification with current step
                    updateNotification(step, "Mode: ${mode.name}")
                }

                // Start automation
                stateMachine?.startMode(mode)

                val modeName = when (mode) {
                    OperationMode.NEW_ORDER_SWEEPER -> "CREATE BUY ORDER"
                    OperationMode.ORDER_EDITOR -> "EDIT BUY ORDER"
                    OperationMode.IDLE -> "IDLE"
                }

                val debugText = if (debugMode) " [DEBUG - 1 item only]" else ""
                showToast("✅ $modeName started$debugText")
                updateNotification("Running: $modeName$debugText", "Initializing...")
                floatingOverlayManager?.show()
                floatingOverlayManager?.updateStatus("Running")

                Log.d(TAG, "Automation mode started: $mode, debug=$debugMode")

            } catch (e: Exception) {
                val errorMsg = "HANDLE_ERROR_POPUP\nStart failed: ${e.message}\nScreen: Check device settings"
                showErrorDialog(errorMsg)
                Log.e(TAG, "Error starting automation", e)
                updateNotification("Error", e.message ?: "Unknown error")
            }
        }
    }

    private fun showErrorDialog(error: String) {
        // Show a toast with the full error
        showToast("❌ $error")
        
        // Also log it
        Log.e(TAG, "═══════════════════════════════════════")
        Log.e(TAG, error)
        Log.e(TAG, "═══════════════════════════════════════")
    }

    private fun showStatisticsToast() {
        val stats = stateMachine?.getStatistics() ?: return
        val lastStep = stateMachine?.getLastStep() ?: "N/A"
        val message = buildString {
            append("Items: ${stats.successfulOperations}")
            append(" | Errors: ${stats.consecutiveErrors}")
            if (stats.lastPrice != null) {
                append(" | Last Price: ${stats.lastPrice}")
            }
            append("\nLast Step: $lastStep")
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
