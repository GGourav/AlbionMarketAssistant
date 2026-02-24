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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.albion.marketassistant.accessibility.MarketAccessibilityService
import com.albion.marketassistant.data.CalibrationData
import com.albion.marketassistant.data.OperationMode
import com.albion.marketassistant.db.CalibrationDatabase
import com.albion.marketassistant.accessibility.StateMachine
import com.albion.marketassistant.ui.MainActivity
import kotlinx.coroutines.*

class AutomationForegroundService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "AlbionAssistant"
        
        const val ACTION_START_MODE = "com.albion.START_MODE"
        const val ACTION_STOP_MODE = "com.albion.STOP_MODE"
        const val ACTION_ACCESSIBILITY_READY = "com.albion.ACCESSIBILITY_READY"
        const val EXTRA_MODE = "mode"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateMachine: StateMachine? = null
    private var isAccessibilityReady = false
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ACCESSIBILITY_READY -> {
                    isAccessibilityReady = true
                    showToast("Accessibility Ready")
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val intentFilter = IntentFilter().apply { addAction(ACTION_ACCESSIBILITY_READY) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(broadcastReceiver, intentFilter)
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_MODE -> handleStartMode(intent)
                ACTION_STOP_MODE -> handleStopMode()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stateMachine?.stop()
        serviceScope.cancel()
        try { unregisterReceiver(broadcastReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }
    
    private fun handleStartMode(intent: Intent) {
        if (!isAccessibilityReady) {
            showToast("Enable Accessibility Service in Settings")
            return
        }
        
        val mode = intent.getSerializableExtra(EXTRA_MODE) as? OperationMode ?: return
        
        serviceScope.launch {
            try {
                val database = Room.databaseBuilder(applicationContext, CalibrationDatabase::class.java, "calibration_db").build()
                val calibration = database.calibrationDao().getCalibration() ?: CalibrationData()
                val accessibilityService = MarketAccessibilityService.getInstance()
                
                if (accessibilityService == null) {
                    showToast("Accessibility Service not available")
                    return@launch
                }
                
                val uiInteractor = accessibilityService.getUIInteractor()
                
                stateMachine = StateMachine(serviceScope, calibration, uiInteractor)
                stateMachine?.onStateChange = { updateNotification("${mode.name}: ${it.stateType}") }
                stateMachine?.onError = { showToast("Error: $it"); handleStopMode() }
                
                stateMachine?.startMode(mode)
                showToast("$mode started")
                updateNotification("Running: $mode")
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun handleStopMode() {
        stateMachine?.stop()
        showToast("Stopped")
        updateNotification("Ready")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Albion Assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Albion Market Assistant")
            .setContentText("Ready")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(status: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Albion Market Assistant")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {}
    }
    
    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
