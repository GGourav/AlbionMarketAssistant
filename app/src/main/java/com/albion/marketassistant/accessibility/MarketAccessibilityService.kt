package com.albion.marketassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.albion.marketassistant.data.*
import com.albion.marketassistant.ml.OCREngine
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MarketAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MarketAccessibilitySvc"
        
        @Volatile
        private var instance: MarketAccessibilityService? = null
        
        private var mediaProjectionResultCode: Int = 0
        private var mediaProjectionResultData: Intent? = null
        private val projectionLock = Any()
        
        fun getInstance(): MarketAccessibilityService? = instance
        fun isServiceEnabled(): Boolean = instance != null
        
        fun setMediaProjectionData(resultCode: Int, data: Intent) {
            synchronized(projectionLock) {
                mediaProjectionResultCode = resultCode
                mediaProjectionResultData = data.clone() as Intent
            }
        }
        
        fun getMediaProjectionData(): Pair<Int, Intent?> {
            synchronized(projectionLock) {
                return Pair(mediaProjectionResultCode, mediaProjectionResultData?.clone() as Intent?)
            }
        }
        
        fun clearMediaProjectionData() {
            synchronized(projectionLock) {
                mediaProjectionResultCode = 0
                mediaProjectionResultData = null
            }
        }
    }

    private var stateMachine: StateMachine? = null
    private var calibration: CalibrationData? = null

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0
    private val latestBitmap = AtomicReference<Bitmap?>(null)
    private val captureLock = Any()
    private val isCapturing = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        mainHandler.post {
            setupMediaProjectionIfNeeded()
        }

        val intent = Intent("com.albion.ACCESSIBILITY_READY")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        
        Log.d(TAG, "Service connected - Ready for Albion Online automation")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        Log.d(TAG, "Service destroying")
        instance = null
        serviceScope.cancel()
        releaseMediaProjection()
        latestBitmap.getAndSet(null)?.recycle()
        OCREngine.close()
        super.onDestroy()
    }

    fun setupMediaProjectionIfNeeded() {
        synchronized(captureLock) {
            if (mediaProjection != null) return
            
            val (resultCode, data) = getMediaProjectionData()
            if (resultCode != 0 && data != null) {
                try {
                    val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = manager.getMediaProjection(resultCode, data)
                    mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            Log.d(TAG, "MediaProjection stopped")
                            releaseMediaProjection()
                        }
                    }, mainHandler)
                    setupImageReader()
                    Log.d(TAG, "MediaProjection setup successful - ${screenWidth}x${screenHeight}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to setup MediaProjection", e)
                }
            }
        }
    }

    private fun setupImageReader() {
        if (mediaProjection == null) return
        
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AlbionMarketCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            object : VirtualDisplay.Callback() {
                override fun onStopped() {
                    Log.d(TAG, "VirtualDisplay stopped")
                }
            },
            mainHandler
        )
        
        Log.d(TAG, "ImageReader setup: ${screenWidth}x${screenHeight}")
    }

    fun setCalibration(data: CalibrationData) {
        calibration = data
        Log.d(TAG, "Calibration set: ${data.createMode.maxItemsToProcess} items max")
    }

    fun startAutomation(mode: OperationMode) {
        val cal = calibration ?: CalibrationData()
        stateMachine?.stop()
        stateMachine = StateMachine(serviceScope, cal, this, this)
        stateMachine?.onScreenshotRequest = {
            captureScreen()
        }
        stateMachine?.onStateChange = { state ->
            Log.d(TAG, "State: ${state.stateType} - ${state.errorMessage ?: "OK"}")
        }
        stateMachine?.onError = { error ->
            Log.e(TAG, "Automation error: $error")
        }
        stateMachine?.startMode(mode)
        Log.d(TAG, "Automation started: $mode")
    }

    fun stopAutomation() {
        stateMachine?.stop()
        stateMachine = null
        Log.d(TAG, "Automation stopped")
    }

    fun pauseAutomation() {
        stateMachine?.pause()
    }

    fun resumeAutomation() {
        stateMachine?.resume()
    }

    fun isRunning(): Boolean = stateMachine != null && stateMachine?.isPaused() == false

    fun getStateMachine(): StateMachine? = stateMachine

    fun captureScreen(): Bitmap? {
        if (!isCapturing.compareAndSet(false, true)) {
            return latestBitmap.get()
        }
        
        try {
            synchronized(captureLock) {
                if (mediaProjection == null) {
                    setupMediaProjectionIfNeeded()
                }
                
                if (imageReader == null) {
                    Log.w(TAG, "ImageReader not available")
                    return latestBitmap.get()
                }

                val image: Image? = imageReader?.acquireLatestImage()
                if (image == null) {
                    return latestBitmap.get()
                }

                var bitmap: Bitmap? = null
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    val bitmapWidth = screenWidth + rowPadding / pixelStride
                    bitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val finalBitmap = if (rowPadding > 0) {
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                        bitmap.recycle()
                        cropped
                    } else {
                        bitmap
                    }

                    val oldBitmap = latestBitmap.getAndSet(finalBitmap)
                    oldBitmap?.recycle()

                    return finalBitmap
                } catch (e: Exception) {
                    image.close()
                    bitmap?.recycle()
                    Log.e(TAG, "Error capturing screen", e)
                    return latestBitmap.get()
                }
            }
        } finally {
            isCapturing.set(false)
        }
    }

    suspend fun performOCR(region: Rect): List<OCRResult> {
        val screenshot = captureScreen() ?: return emptyList()
        return OCREngine.recognizeText(screenshot, region)
    }

    private fun releaseMediaProjection() {
        synchronized(captureLock) {
            try {
                virtualDisplay?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing virtual display", e)
            }
            virtualDisplay = null
            
            try {
                imageReader?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing image reader", e)
            }
            imageReader = null
            
            try {
                mediaProjection?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping media projection", e)
            }
            mediaProjection = null
            
            Log.d(TAG, "MediaProjection released")
        }
    }

    fun performTap(x: Int, y: Int, durationMs: Long = 80): Boolean {
        return performGesture(
            Path().apply { moveTo(x.toFloat(), y.toFloat()) },
            durationMs.coerceIn(50, 500)
        )
    }

    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 400): Boolean {
        return performGesture(
            Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            },
            durationMs.coerceIn(100, 1000)
        )
    }

    private fun performGesture(path: Path, durationMs: Long): Boolean {
        return try {
            var success = false
            val latch = CountDownLatch(1)

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()

            mainHandler.post {
                success = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        latch.countDown()
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        latch.countDown()
                    }
                }, null)

                if (!success) {
                    latch.countDown()
                }
            }

            latch.await(3, TimeUnit.SECONDS)
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error performing gesture", e)
            false
        }
    }

    fun getScreenWidth(): Int = screenWidth
    fun getScreenHeight(): Int = screenHeight
    
    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow
}
