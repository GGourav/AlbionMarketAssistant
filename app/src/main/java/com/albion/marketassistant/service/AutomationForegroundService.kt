package com.albion.marketassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.albion.marketassistant.R
import com.albion.marketassistant.data.AutomationMode
import com.albion.marketassistant.data.AutomationState
import com.albion.marketassistant.data.CalibrationData
import com.albion.marketassistant.data.RandomizationSettings
import com.albion.marketassistant.data.SessionStatistics
import com.albion.marketassistant.ml.ColorDetector
import com.albion.marketassistant.ocr.TesseractOcrEngine
import com.albion.marketassistant.util.RandomizationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AutomationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "automation_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.albion.marketassistant.START"
        const val ACTION_STOP = "com.albion.marketassistant.STOP"
        const val ACTION_PAUSE = "com.albion.marketassistant.PAUSE"
        const val ACTION_RESUME = "com.albion.marketassistant.RESUME"
        
        const val EXTRA_MODE = "mode"
        const val EXTRA_MEDIA_RESULT_CODE = "media_result_code"
        const val EXTRA_MEDIA_RESULT_DATA = "media_result_data"
        
        const val PREF_DEBUG_MODE = "debug_mode"
        
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        
        private val _currentState = MutableStateFlow("IDLE")
        val currentState: StateFlow<String> = _currentState
        
        private val _statistics = MutableStateFlow(SessionStatistics())
        val statistics: StateFlow<SessionStatistics> = _statistics
        
        private val _progress = MutableStateFlow("")
        val progress: StateFlow<String> = _progress
        
        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420
    
    private lateinit var windowManager: WindowManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var sharedPreferences: SharedPreferences
    
    private lateinit var stateMachine: StateMachine
    private lateinit var ocrEngine: TesseractOcrEngine
    private lateinit var colorDetector: ColorDetector
    
    private var calibrationData: CalibrationData = CalibrationData()
    private var randomizationSettings: RandomizationSettings = RandomizationSettings()
    
    private var automationJob: Job? = null
    private var scope: CoroutineScope? = null
    
    private var isPaused = false
    private var debugMode = false
    
    override fun onCreate() {
        super.onCreate()
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        
        debugMode = sharedPreferences.getBoolean(PREF_DEBUG_MODE, false)
        
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        createNotificationChannel()
        
        ocrEngine = TesseractOcrEngine(this)
        colorDetector = ColorDetector()
        
        RandomizationHelper.initialize(randomizationSettings)
        
        stateMachine = StateMachine(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            ocrEngine = ocrEngine,
            colorDetector = colorDetector,
            debugMode = debugMode
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_MODE, AutomationMode::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_MODE) as? AutomationMode
                } ?: AutomationMode.CREATE_BUY_ORDER
                
                val resultCode = intent.getIntExtra(EXTRA_MEDIA_RESULT_CODE, -1)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_MEDIA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_MEDIA_RESULT_DATA)
                }
                
                if (resultCode != -1 && resultData != null) {
                    startAutomation(mode, resultCode, resultData)
                }
            }
            ACTION_STOP -> {
                stopAutomation()
                stopSelf()
            }
            ACTION_PAUSE -> {
                isPaused = true
                updateProgress("PAUSED")
            }
            ACTION_RESUME -> {
                isPaused = false
                updateProgress("RESUMED")
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stopAutomation()
        ocrEngine.close()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Automation Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Albion Market Assistant automation service"
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AutomationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Albion Market Assistant")
            .setContentText("Automation running - ${_progress.value}")
            .setSmallIcon(R.drawable.ic_notification)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun startAutomation(mode: AutomationMode, resultCode: Int, resultData: Intent) {
        startForeground(NOTIFICATION_ID, createNotification())
        
        _isRunning.value = true
        _lastError.value = null
        
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
        setupImageReader()
        
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        
        automationJob = scope?.launch {
            try {
                stateMachine.initialize(calibrationData, mode)
                
                while (_isRunning.value) {
                    if (!isPaused) {
                        val result = stateMachine.executeNextStep()
                        
                        _currentState.value = result.state.name
                        _statistics.value = stateMachine.getStatistics()
                        updateProgress(result.message)
                        
                        if (!result.success) {
                            _lastError.value = result.message
                            if (debugMode) {
                                updateProgress("ERROR: ${result.message}")
                            }
                        }
                        
                        delay(result.delay + RandomizationHelper.getRandomDelay())
                        
                        if (debugMode && stateMachine.getStatistics().priceUpdates >= 1) {
                            updateProgress("DEBUG MODE: Stopped after 1 item")
                            delay(3000)
                            stopAutomation()
                            break
                        }
                        
                        if (result.state == AutomationState.COMPLETED ||
                            result.state == AutomationState.STOPPED) {
                            break
                        }
                    } else {
                        delay(500)
                    }
                    
                    updateNotification()
                }
            } catch (e: Exception) {
                _lastError.value = "Automation error: ${e.message}"
                updateProgress("ERROR: ${e.message}")
            }
        }
    }
    
    private fun stopAutomation() {
        _isRunning.value = false
        automationJob?.cancel()
        automationJob = null
        scope?.cancel()
        scope = null
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        _currentState.value = "STOPPED"
    }
    
    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )
        
        imageReader?.setOnImageAvailableListener({ reader ->
            val image: Image? = reader.acquireLatestImage()
            image?.let {
                processImage(it)
                it.close()
            }
        }, Handler(Looper.getMainLooper()))
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }
    
    private fun processImage(image: Image) {
        // Process captured screen image for OCR and color detection
    }
    
    private fun updateProgress(message: String) {
        _progress.value = message
    }
    
    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    fun setCalibrationData(data: CalibrationData) {
        calibrationData = data
        stateMachine.updateCalibration(data)
    }
    
    fun setRandomizationSettings(settings: RandomizationSettings) {
        randomizationSettings = settings
        RandomizationHelper.initialize(settings)
    }
}
