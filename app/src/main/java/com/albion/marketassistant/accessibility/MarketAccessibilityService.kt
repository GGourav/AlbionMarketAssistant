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
        
        // Fixed: Clear these on service destroy to prevent memory leaks
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

    // Screen capture - MediaProjection based
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

        // Initialize screen capture if we have MediaProjection data
        mainHandler.post {
            setupMediaProjectionIfNeeded()
        }

        val intent = Intent("com.albion.ACCESSIBILITY_READY")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        Log.d(TAG, "Service destroying")
        instance = null
        serviceScope.cancel()
        releaseMediaProjection()
        
        // Fixed: Clear cached bitmap
        latestBitmap.getAndSet(null)?.recycle()
        
        // Fixed: Close OCR engine
        OCREngine.close()
        
        super.onDestroy()
    }

    /**
     * Setup MediaProjection for screen capture
     */
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
                    Log.d(TAG, "MediaProjection setup successful")
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
        Log.d(TAG, "Calibration set")
    }

    fun startAutomation(mode: OperationMode) {
        val cal = calibration ?: CalibrationData()
        stateMachine?.stop()
        stateMachine = StateMachine(serviceScope, cal, UIInteractorImpl(), this)
        stateMachine?.onScreenshotRequest = {
            captureScreen()
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

    fun getUIInteractor(): UIInteractor = UIInteractorImpl()

    fun getStateMachine(): StateMachine? = stateMachine

    /**
     * Capture screen using MediaProjection
     * Fixed: Proper bitmap recycling and thread safety
     */
    fun captureScreen(): Bitmap? {
        // Prevent concurrent captures
        if (!isCapturing.compareAndSet(false, true)) {
            return latestBitmap.get()
        }
        
        try {
            synchronized(captureLock) {
                // Try to setup if not already done
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

                    // Create bitmap
                    val bitmapWidth = screenWidth + rowPadding / pixelStride
                    bitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    // Crop if needed
                    val finalBitmap = if (rowPadding > 0) {
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                        bitmap.recycle()
                        cropped
                    } else {
                        bitmap
                    }

                    // Update cached bitmap
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

    /**
     * Perform OCR on screen region
     */
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

    inner class UIInteractorImpl : UIInteractor {

        override fun performTap(x: Int, y: Int, durationMs: Long): Boolean {
            return performGesture(
                createTapPath(x, y),
                durationMs
            )
        }

        override fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
            return performGesture(
                createSwipePath(startX, startY, endX, endY),
                durationMs
            )
        }

        fun performGestureWithPath(path: Path, durationMs: Long): Boolean {
            return performGesture(path, durationMs)
        }

        override fun injectText(text: String): Boolean {
            return try {
                val rootNode = rootInActiveWindow ?: return false

                // Find the focused input field
                val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

                if (focusNode != null) {
                    val arguments = android.os.Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                    val result = focusNode.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        arguments
                    )
                    focusNode.recycle()
                    if (result) return true
                }

                // Try to find any editable field
                val editableNode = findFirstEditableNode(rootNode)
                if (editableNode != null) {
                    val arguments = android.os.Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                    val result = editableNode.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        arguments
                    )
                    editableNode.recycle()
                    if (result) return true
                }

                // Fallback: clipboard
                injectTextViaClipboard(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error injecting text", e)
                false
            }
        }

        /**
         * Fallback text injection using clipboard
         */
        private fun injectTextViaClipboard(text: String): Boolean {
            return try {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("price", text)
                clipboard.setPrimaryClip(clip)
                Log.d(TAG, "Text copied to clipboard: $text")
                false // Indicates we couldn't inject directly
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard fallback failed", e)
                false
            }
        }

        /**
         * Fixed: Find first editable node and properly recycle others
         */
        private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isEditable && node.isEnabled) {
                return node // Return without recycling - caller must recycle
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findFirstEditableNode(child)
                    if (result != null) {
                        return result
                    }
                    // Only recycle if we're not returning this node
                    if (result == null && child != null) {
                        child.recycle()
                    }
                }
            }
            return null
        }

        override fun clearTextField(): Boolean = injectText("")

        override fun dismissKeyboard(): Boolean {
            return try {
                performGlobalAction(GLOBAL_ACTION_BACK)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing keyboard", e)
                false
            }
        }

        fun getForegroundPackage(): String? {
            return rootInActiveWindow?.packageName?.toString()
        }

        fun findNodeByText(text: String): AccessibilityNodeInfo? {
            val rootNode = rootInActiveWindow ?: return null
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            return nodes.firstOrNull()
        }

        fun findNodeById(id: String): AccessibilityNodeInfo? {
            val rootNode = rootInActiveWindow ?: return null
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            return nodes.firstOrNull()
        }

        fun clickNode(node: AccessibilityNodeInfo): Boolean {
            return try {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            parent.recycle()
                            return result
                        }
                        val nextParent = parent.parent
                        parent.recycle()
                        parent = nextParent
                    }
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clicking node", e)
                false
            }
        }

        private fun createTapPath(x: Int, y: Int): Path {
            return Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
        }

        private fun createSwipePath(startX: Int, startY: Int, endX: Int, endY: Int): Path {
            return Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
        }

        private fun performGesture(path: Path, durationMs: Long): Boolean {
            return try {
                var success = false

                // Duration in MILLISECONDS
                val duration = durationMs.coerceIn(50L, 500L)

                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build()

                val latch = CountDownLatch(1)

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

                latch.await(2, TimeUnit.SECONDS)
                success
            } catch (e: Exception) {
                Log.e(TAG, "Error performing gesture", e)
                false
            }
        }
    }
}

interface UIInteractor {
    fun performTap(x: Int, y: Int, durationMs: Long): Boolean
    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean
    fun injectText(text: String): Boolean
    fun clearTextField(): Boolean
    fun dismissKeyboard(): Boolean
}
