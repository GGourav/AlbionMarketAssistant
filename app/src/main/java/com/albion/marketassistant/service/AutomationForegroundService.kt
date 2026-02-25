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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.albion.marketassistant.accessibility.MarketAccessibilityService
import com.albion.marketassistant.accessibility.StateMachine
import com.albion.marketassistant.accessibility.UIInteractor
import com.albion.marketassistant.data.CalibrationData
import com.albion.marketassistant.data.OperationMode
import com.albion.marketassistant.data.StateType
import com.albion.marketassistant.db.CalibrationDatabase
import com.albion.marketassistant.ui.MainActivity
import com.albion.marketassistant.ui.overlay.FloatingOverlayManager
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
        const val EXTRA_MODE = "mode"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateMachine: StateMachine? = null
    private var isAccessibilityReady = false
    private var pendingMode: OperationMode? = null
    private var currentMode: OperationMode? = null
    private var floatingOverlayManager: FloatingOverlayManager? = null
    private var totalItemsProcessed = 0
    
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
        
        floatingOverlayManager = FloatingOverlayManager(this) { action ->
            handleOverlayAction(action)
        }
        
        startForeground(NOTIFICATION_ID, createNotification("Ready"))
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
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stateMachine?.stop()
        floatingOverlayManager?.hide()
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
        showToast("Paused")
    }
    
    private fun handleResume() {
        stateMachine?.resume()
        floatingOverlayManager?.updateStatus("Running")
        showToast("Resumed")
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
                    updateNotification("Error: Service not available")
                    return@launch
                }
                
                val uiInteractor: UIInteractor = accessibilityService.getUIInteractor()
                
                stateMachine?.stop()
                stateMachine = StateMachine(serviceScope, calibration, uiInteractor)
                stateMachine?.onStateChange = { state -> 
                    updateNotification("${mode.name}: ${state.stateType}")
                    floatingOverlayManager?.updateStatus(state.stateType.name)
                    
                    when (state.stateType) {
                        StateType.ERROR_PRICE_SANITY -> {
                            showToast("⚠️ PRICE ERROR: ${state.errorMessage}")
                            floatingOverlayManager?.updateStatus("PRICE ERROR!")
                        }
                        StateType.ERROR_TIMEOUT -> {
                            showToast("Timeout - retrying...")
                        }
                        StateType.ERROR_END_OF_LIST -> {
                            showToast("✅ END OF LIST: ${state.errorMessage}")
                            floatingOverlayManager?.updateStatus("DONE!")
                        }
                        StateType.ERROR_WRONG_APP -> {
                            showToast("⚠️ WRONG APP: ${state.currentAppName}")
                            floatingOverlayManager?.updateStatus("WRONG APP!")
                        }
                        else -> {}
                    }
                }
                stateMachine?.onError = { error -> 
                    showToast("Error: $error")
                    floatingOverlayManager?.updateStatus("Error")
                }
                stateMachine?.onPriceSanityError = { error ->
                    showToast("🛡️ SAFETY HALT: $error")
                    floatingOverlayManager?.updateStatus("SAFETY HALT")
                    stateMachine?.pause()
                }
                stateMachine?.onEndOfList = { message ->
                    showToast("✅ $message")
                    floatingOverlayManager?.updateStatus("COMPLETE!")
                }
                stateMachine?.onWrongApp = { appName ->
                    showToast("⚠️ Switched to: $appName - Pausing")
                    floatingOverlayManager?.updateStatus("PAUSED!")
                }
                
                totalItemsProcessed = 0
                stateMachine?.startMode(mode)
                currentMode = mode
                showToast("$mode started")
                updateNotification("Running: $mode")
                
                floatingOverlayManager?.show()
                floatingOverlayManager?.updateStatus("Running")
                
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                e.printStackTrace()
                updateNotification("Error: ${e.message}")
            }
        }
    }
    
    private fun handleStopMode() {
        stateMachine?.stop()
        stateMachine = null
        currentMode = null
        pendingMode = null
        
        floatingOverlayManager?.hide()
        
        showToast("Stopped")
        updateNotification("Ready")
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
    
    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Albion Market Assistant")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun updateNotification(status: String) {
        try {
            val notification = createNotification(status)
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
