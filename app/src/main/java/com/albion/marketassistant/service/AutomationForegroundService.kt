package com.albion.marketassistant.service

import android.app.*
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.albion.marketassistant.data.OperationMode
import com.albion.marketassistant.statemachine.StateMachine
import com.albion.marketassistant.accessibility.UIInteractor
import com.albion.marketassistant.ml.OCREngine
import com.albion.marketassistant.ml.ColorDetector
import com.albion.marketassistant.media.ScreenCaptureManager
import com.albion.marketassistant.db.CalibrationDatabase
import com.albion.marketassistant.accessibility.MarketAccessibilityService
import kotlinx.coroutines.*

class AutomationForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateMachine: StateMachine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Setup Notification (Required for Foreground Services)
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("Albion Assistant Running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)

        // 2. Initialize the Engine
        val mode = intent?.getSerializableExtra("MODE") as? OperationMode ?: OperationMode.IDLE
        val resultData = intent?.getParcelableExtra<Intent>("SCREEN_CAPTURE_INTENT")

        if (resultData != null) {
            startAutomation(mode, resultData)
        }

        return START_STICKY
    }

    private fun startAutomation(mode: OperationMode, data: Intent) {
        serviceScope.launch {
            val db = CalibrationDatabase.getInstance(this@AutomationForegroundService)
            val config = db.calibrationDao().getCalibration() ?: com.albion.marketassistant.data.CalibrationData()
            
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(Activity.RESULT_OK, data)
            
            val captureManager = ScreenCaptureManager(this@AutomationForegroundService, projection)
            val accessibilityService = MarketAccessibilityService.getInstance()
            
            if (accessibilityService != null) {
                stateMachine = StateMachine(
                    UIInteractor(accessibilityService),
                    OCREngine(),
                    ColorDetector(),
                    captureManager,
                    config
                )
                stateMachine?.start(mode)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("CHANNEL_ID", "Bot Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateMachine?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }
}
