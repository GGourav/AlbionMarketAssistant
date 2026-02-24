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
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.albion.marketassistant.R
import com.albion.marketassistant.accessibility.MarketAccessibilityService
import com.albion.marketassistant.data.CalibrationData
import com.albion.marketassistant.data.OperationMode
import com.albion.marketassistant.db.CalibrationDatabase
import com.albion.marketassistant.media.ScreenCaptureManager
import com.albion.marketassistant.ml.ColorDetector
import com.albion.marketassistant.ml.OCREngine
import com.albion.marketassistant.statemachine.StateMachine
import com.albion.marketassistant.ui.MainActivity
import kotlinx.coroutines.*

class AutomationForegroundService : Service() {
    
    companion object {
        private const val TAG = "AutomationService"
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "AlbionMarketAssistant"
        
        const val ACTION_START_MODE = "com.albion.START_MODE"
        const val ACTION_STOP_MODE = "com.albion.STOP_MODE"
        const val ACTION_REQUEST_MEDIA_PROJECTION = "com.albion.REQUEST_MEDIA_PROJECTION"
        const val ACTION_ACCESSIBILITY_READY = "com.albion.ACCESSIBILITY_READY"
        const val ACTION_POPUP_OPENED = "com.albion.POPUP_OPENED"
        
        const val EXTRA_MODE = "mode"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var stateMachine: StateMachine? = null
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var isAccessibilityReady = false
    private var mediaProjectionManager: MediaProjectionManager? = null
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ACCESSIBILITY_READY -> {
                    isAccessibilityReady = true
                    showToast("Accessibility Connected")
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_ACCESSIBILITY_READY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(broadcastReceiver, intentFilter)
        }
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_MODE -> handleStartMode(intent)
                ACTION_STOP_MODE -> handleStopMode()
                ACTION_REQUEST_MEDIA_PROJECTION -> handleMediaProjectionRequest()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stateMachine?.stop()
        screenCaptureManager?.release()
        serviceScope.cancel()
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
    
    private fun handleStartMode(intent: Intent) {
        if (!isAccessibilityReady) {
            showToast("ERROR: Enable Accessibility Service")
            return
        }
        
        val mode = intent.getSerializableExtra(EXTRA_MODE) as? OperationMode
        if (mode == null) {
            showToast("ERROR: Invalid mode")
            return
        }
        
        serviceScope.launch {
            try {
                val database = Room.databaseBuilder(
                    applicationContext,
                    CalibrationDatabase::class.java,
                    "calibration_db"
                ).build()
                
                val calibration = database.calibrationDao().getCalibration() ?: CalibrationData()
                
                val colorDetector = ColorDetector()
                val ocrEngine = OCREngine()
                val accessibilityService = MarketAccessibilityService.instance
                
                if (accessibilityService == null) {
                    showToast("ERROR: Accessibility Service not available")
                    return@launch
                }
                
                val uiInteractor = accessibilityService.getUIInteractor()
                
                if (screenCaptureManager == null) {
                    showToast("ERROR: MediaProjection not initialized")
                    return@launch
                }
                
                stateMachine = StateMachine(
                    scope = serviceScope,
                    calibration = calibration,
                    colorDetector = colorDetector,
                    ocrEngine = ocrEngine,
                    uiInteractor = uiInteractor,
                    screenCaptureManager = screenCaptureManager!!
                )
                
                stateMachine?.onStateChange = { state ->
                    updateNotification("${mode.name}: ${state.stateType}")
                }
                
                stateMachine?.onError = { error ->
                    showToast("Error: $error")
                    handleStopMode()
                }
                
                stateMachine?.startMode(mode)
                showToast("${mode.name} started")
                updateNotification("Running: ${mode.name}")
                
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
    
    private fun handleMediaProjectionRequest() {
        showToast("MediaProjection request")
    }
    
    fun setMediaProjection(mediaProjection: android.media.projection.MediaProjection) {
        serviceScope.launch {
            try {
                val displayMetrics = resources.displayMetrics
                
                screenCaptureManager = ScreenCaptureManager(
                    context = this@AutomationForegroundService,
                    mediaProjection = mediaProjection,
                    width = displayMetrics.widthPixels,
                    height = displayMetrics.heightPixels
                )
                
                showToast("Screen capture ready")
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Albion Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Automation status"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
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
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
