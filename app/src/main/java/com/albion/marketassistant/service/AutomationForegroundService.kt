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
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.albion.marketassistant.R
import com.albion.marketassistant.accessibility.MarketAccessibilityService
import com.albion.marketassistant.accessibility.StateMachine
import com.albion.marketassistant.accessibility.UIInteractor
import com.albion.marketassistant.data.*
import com.albion.marketassistant.db.CalibrationDatabase
import com.albion.marketassistant.ui.MainActivity
import com.albion.marketassistant.ui.overlay.FloatingOverlayManager
import com.albion.marketassistant.util.DeviceUtils
import kotlinx.coroutines.*

class AutomationForegroundService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "AlbionAssistant"

        const val ACTION_START_MODE = "com.albion.START_MODE"
        const val ACTION_STOP_MODE = "com.albion.STOP_MODE"
        const val ACTION_ACCESSIBILITY_READY = "com.albion.ACCESSIBILITY_READY"
        const val ACTION_CREATE_MODE = "com.albion.CREATE_MODE"
        const val ACTION_EDIT_MODE = "com.albion.EDIT_MODE"
        const val ACTION_PAUSE = "com.albion.PAUSE"
        const val ACTION_RESUME = "com.albion.RESUME"
        const val ACTION_RESET_ESCAPE = "com.albion.RESET_ESCAPE"
        const val ACTION_EXPORT_STATS = "com.albion.EXPORT_STATS"
        const val ACTION_TOGGLE_OVERLAY = "com.albion.TOGGLE_OVERLAY"
        const val EXTRA_MODE = "mode"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateMachine: StateMachine? = null
    private var isAccessibilityReady = false
    private var pendingMode: OperationMode? = null
    private var currentMode: OperationMode? = null
    private var floatingOverlayManager: FloatingOverlayManager? = null
    private var deviceUtils: DeviceUtils? = null
    private var statsUpdateJob: Job? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ACCESSIBILITY_READY -> {
                    isAccessibilityReady = true
                    showToast("Accessibility Service Ready")
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
        deviceUtils = DeviceUtils(this)

        floatingOverlayManager = FloatingOverlayManager(this) { action ->
            handleOverlayAction(action)
        }

        startForeground(NOTIFICATION_ID, createNotification("Ready", null))
        startBatteryMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_MODE -> handleStartMode(intent)
                ACTION_STOP_MODE -> handleStopMode()
                ACTION_CREATE_MODE -> handleCreateMode()
                ACTION_EDIT_MODE -> handleEditMode()
                ACTION_PAUSE -> handlePause()
                ACTION_RESUME -> handleResume()
                ACTION_RESET_ESCAPE -> handleResetEscape()
                ACTION_EXPORT_STATS -> handleExportStats()
                ACTION_TOGGLE_OVERLAY -> handleToggleOverlay()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateMachine?.stop()
        floatingOverlayManager?.hide()
        statsUpdateJob?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) { }
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
            "EXPORT" -> handleExportStats()
        }
    }

    private fun handleStartMode(intent: Intent) {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_MODE, OperationMode::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_MODE) as? OperationMode
        } ?: return

        if (!Settings.canDrawOverlays(this)) {
            showToast("Please allow 'Display over other apps' permission")
            return
        }

        if (!isAccessibilityReady && !MarketAccessibilityService.isServiceEnabled()) {
            showToast("Please enable Accessibility Service in Settings")
            pendingMode = mode
            return
        }

        isAccessibilityReady = true
        startAutomationMode(mode)
    }

    private fun handleCreateMode() {
        if (!checkPermissions()) return
        currentMode = OperationMode.NEW_ORDER_SWEEPER
        startAutomationMode(OperationMode.NEW_ORDER_SWEEPER)
        showToast("Create Order mode started")
    }

    private fun handleEditMode() {
        if (!checkPermissions()) return
        currentMode = OperationMode.ORDER_EDITOR
        startAutomationMode(OperationMode.ORDER_EDITOR)
        showToast("Edit Order mode started")
    }

    private fun handlePause() {
        stateMachine?.pause()
        floatingOverlayManager?.updateStatus("PAUSED")
        updateNotification("Paused", null)
        showToast("Paused")
    }

    private fun handleResume() {
        stateMachine?.resume()
        floatingOverlayManager?.updateStatus("Running")
        showToast("Resumed")
    }

    private fun handleResetEscape() {
        MarketAccessibilityService.getInstance()?.getUIInteractor()?.dismissKeyboard()
        showToast("Reset - dismissing popups")
    }

    private fun handleExportStats() {
        serviceScope.launch {
            stateMachine?.exportSessionLog()
            showToast("Stats exported")
        }
    }

    private fun handleToggleOverlay() {
        if (floatingOverlayManager?.isVisible() == true) {
            floatingOverlayManager?.hide()
        } else {
            floatingOverlayManager?.show()
        }
    }

    private fun checkPermissions(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            showToast("Please allow 'Display over other apps' permission")
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
                val database = CalibrationDatabase.getInstance(applicationContext)
                val calibration = database.calibrationDao().getCalibration() ?: CalibrationData()

                val accessibilityService = MarketAccessibilityService.getInstance()

                if (accessibilityService == null) {
                    showToast("Accessibility Service not available")
                    updateNotification("Error: Service not available", null)
                    return@launch
                }

                val uiInteractor: UIInteractor = accessibilityService.getUIInteractor()

                stateMachine?.stop()
                stateMachine = StateMachine(serviceScope, calibration, uiInteractor, this@AutomationForegroundService)

                stateMachine?.onStateChange = { state ->
                    updateNotification("${mode.name}: ${state.stateType}", state.statistics)

                    floatingOverlayManager?.updateStatus(
                        when (state.stateType) {
                            StateType.PAUSED -> "PAUSED"
                            StateType.ERROR_PRICE_SANITY -> "PRICE ERROR!"
                            StateType.ERROR_WINDOW_LOST -> "WINDOW LOST"
                            StateType.ERROR_STUCK_DETECTED -> "STUCK!"
                            StateType.ERROR_END_OF_LIST -> "END OF LIST"
                            StateType.ERROR_BATTERY_LOW -> "LOW BATTERY"
                            StateType.RECOVERING -> "RECOVERING..."
                            else -> state.stateType.name
                        }
                    )

                    floatingOverlayManager?.updateStatistics(state.statistics)

                    when (state.stateType) {
                        StateType.ERROR_PRICE_SANITY -> {
                            showToast("PRICE SANITY ERROR: ${state.errorMessage}")
                            floatingOverlayManager?.updateStatus("PRICE ERROR!")
                        }
                        StateType.ERROR_WINDOW_LOST -> showToast("Game window lost!")
                        StateType.ERROR_STUCK_DETECTED -> showToast("Stuck detected - recovering...")
                        StateType.ERROR_END_OF_LIST -> showToast("End of list reached!")
                        StateType.ERROR_BATTERY_LOW -> showToast("Battery too low - paused")
                        else -> {}
                    }
                }

                stateMachine?.onError = { error ->
                    showToast("Error: $error")
                    floatingOverlayManager?.updateStatus("Error")
                }

                stateMachine?.onPriceSanityError = { error ->
                    showToast("SAFETY HALT: $error")
                    floatingOverlayManager?.updateStatus("SAFETY HALT")
                    stateMachine?.pause()
                }

                stateMachine?.onEndOfList = {
                    showToast("Completed all items!")
                    floatingOverlayManager?.updateStatus("COMPLETE")
                }

                stateMachine?.onWindowLost = {
                    showToast("Albion window lost - please return to game")
                }

                stateMachine?.onStatisticsUpdate = { stats ->
                    floatingOverlayManager?.updateStatistics(stats)
                }

                stateMachine?.startMode(mode)
                currentMode = mode
                showToast("$mode started")
                updateNotification("Running: $mode", null)

                floatingOverlayManager?.show()
                floatingOverlayManager?.updateStatus("Running")

                startStatisticsUpdates()

            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                e.printStackTrace()
                updateNotification("Error: ${e.message}", null)
            }
        }
    }

    private fun startStatisticsUpdates() {
        statsUpdateJob?.cancel()
        statsUpdateJob = serviceScope.launch {
            while (true) {
                delay(5000)
                stateMachine?.getStatistics()?.let { stats ->
                    floatingOverlayManager?.updateStatistics(stats)
                }
            }
        }
    }

    private fun startBatteryMonitoring() {
        serviceScope.launch {
            while (true) {
                delay(30000)

                val batterySettings = calibration.battery

                if (batterySettings.enableBatteryOptimization) {
                    deviceUtils?.let { utils ->
                        if (utils.isBatteryLow(batterySettings.pauseOnBatteryBelow) && !utils.isCharging()) {
                            if (stateMachine != null && !stateMachine!!.isPaused()) {
                                showToast("Battery low (${utils.getBatteryPercentage()}%) - pausing")
                                stateMachine?.pause()
                                floatingOverlayManager?.updateStatus("LOW BATTERY")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleStopMode() {
        stateMachine?.stop()
        stateMachine = null
        currentMode = null
        pendingMode = null
        statsUpdateJob?.cancel()
        floatingOverlayManager?.hide()
        showToast("Stopped")
        updateNotification("Ready", null)
    }

    private fun showStatisticsToast() {
        val stats = stateMachine?.getStatistics() ?: return
        val message = buildString {
            append("Cycles: ${stats.totalCycles}")
            append(" | Success: ${stats.successfulOperations}")
            append(" | Failed: ${stats.failedOperations}")
            append(" | Rate: ${(stats.getSuccessRate() * 100).toInt()}%")
        }
        showToast(message)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Albion Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Albion Market Assistant Service"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String, stats: SessionStatistics?): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Albion Market Assistant")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)

        stats?.let { s ->
            val statsText = "OK:${s.successfulOperations} FAIL:${s.failedOperations} | ${(s.getSuccessRate() * 100).toInt()}%"
            builder.setSubText(statsText)
        }

        return builder.build()
    }

    private fun updateNotification(status: String, stats: SessionStatistics?) {
        try {
            val notification = createNotification(status, stats)
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private val calibration: CalibrationData
        get() = CalibrationData()
}
