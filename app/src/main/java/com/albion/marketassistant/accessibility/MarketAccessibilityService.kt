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
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.albion.marketassistant.data.*
import com.albion.marketassistant.ml.OCREngine
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

class MarketAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private var instance: MarketAccessibilityService? = null
        private var mediaProjectionInstance: MediaProjection? = null
        private var mediaProjectionResultCode: Int = 0
        private var mediaProjectionResultData: Intent? = null
        
        fun getInstance(): MarketAccessibilityService? = instance
        fun isServiceEnabled(): Boolean = instance != null
        
        fun setMediaProjectionData(resultCode: Int, data: Intent) {
            mediaProjectionResultCode = resultCode
            mediaProjectionResultData = data
        }
        
        fun getMediaProjectionData(): Pair<Int, Intent?> = Pair(mediaProjectionResultCode, mediaProjectionResultData)
    }

    private var stateMachine: StateMachine? = null
    private var calibration: CalibrationData? = null
    private var ocrEngine: OCREngine? = null

    // Screen capture - MediaProjection based
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0
    private val latestBitmap = AtomicReference<Bitmap?>(null)
    private val captureLock = Any()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ocrEngine = OCREngine()

        // Initialize screen capture if we have MediaProjection data
        setupMediaProjectionIfNeeded()

        val intent = Intent("com.albion.ACCESSIBILITY_READY")
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        releaseMediaProjection()
        latestBitmap.get()?.recycle()
    }

    /**
     * Setup MediaProjection for screen capture
     * This should be called after the user grants permission in MainActivity
     */
    fun setupMediaProjectionIfNeeded() {
        if (mediaProjection != null) return
        
        val (resultCode, data) = getMediaProjectionData()
        if (resultCode != 0 && data != null) {
            try {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = manager.getMediaProjection(resultCode, data)
                setupImageReader()
            } catch (e: Exception) {
                e.printStackTrace()
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
                    // Virtual display stopped
                }
            },
            mainHandler
        )
    }

    fun setCalibration(data: CalibrationData) {
        calibration = data
    }

    fun startAutomation(mode: OperationMode) {
        val cal = calibration ?: CalibrationData()
        stateMachine?.stop()
        stateMachine = StateMachine(serviceScope, cal, UIInteractorImpl(), this)
        stateMachine?.onScreenshotRequest = {
            captureScreen()
        }
        stateMachine?.startMode(mode)
    }

    fun stopAutomation() {
        stateMachine?.stop()
        stateMachine = null
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
     * Returns the latest captured bitmap
     */
    fun captureScreen(): Bitmap? {
        synchronized(captureLock) {
            try {
                // Try to setup if not already done
                if (mediaProjection == null) {
                    setupMediaProjectionIfNeeded()
                }
                
                if (imageReader == null) {
                    return latestBitmap.get()
                }

                val image: Image? = imageReader?.acquireLatestImage()
                if (image == null) {
                    return latestBitmap.get()
                }

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                // Create bitmap
                val bitmapWidth = screenWidth + rowPadding / pixelStride
                val bitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888)
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
                e.printStackTrace()
                return latestBitmap.get()
            }
        }
    }

    /**
     * Perform OCR on screen region
     */
    suspend fun performOCR(region: Rect): List<OCRResult> {
        val screenshot = captureScreen() ?: return emptyList()
        return ocrEngine?.recognizeText(screenshot, region) ?: emptyList()
    }

    private fun releaseMediaProjection() {
        synchronized(captureLock) {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
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

        /**
         * Perform gesture with custom path (for randomization)
         */
        fun performGestureWithPath(path: Path, durationMs: Long): Boolean {
            return performGesture(path, durationMs)
        }

        override fun injectText(text: String): Boolean {
            return try {
                val rootNode = rootInActiveWindow ?: return false

                // Find the focused input field
                val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

                if (focusNode != null) {
                    // Use ACTION_SET_TEXT (no keyboard popup)
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
                val editableNodes = findEditableNodes(rootNode)
                for (node in editableNodes) {
                    val arguments = android.os.Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                    val result = node.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        arguments
                    )
                    node.recycle()
                    if (result) return true
                }

                // Fallback: Simulate keyboard typing for game engines (Unity)
                // Games often don't expose editable nodes to Accessibility
                injectTextViaKeyboard(text)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        /**
         * Fallback text injection using keyboard simulation
         * This works better for game engines like Unity that don't use standard Android input fields
         */
        private fun injectTextViaKeyboard(text: String): Boolean {
            return try {
                // For game engines, we need to simulate actual key presses
                // Since AccessibilityService doesn't directly support key injection,
                // we'll use a workaround by simulating paste or keyboard events
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("price", text)
                clipboard.setPrimaryClip(clip)
                
                // Clipboard is set - user can paste manually if needed
                // Return false to indicate we couldn't inject directly
                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        private fun findEditableNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val result = mutableListOf<AccessibilityNodeInfo>()

            if (node.isEditable && node.isEnabled) {
                result.add(node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    result.addAll(findEditableNodes(child))
                }
            }

            return result
        }

        override fun clearTextField(): Boolean = injectText("")

        override fun dismissKeyboard(): Boolean {
            return try {
                performGlobalAction(GLOBAL_ACTION_BACK)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        /**
         * Get current package name of foreground app
         */
        fun getForegroundPackage(): String? {
            return rootInActiveWindow?.packageName?.toString()
        }

        /**
         * Find node by text
         */
        fun findNodeByText(text: String): AccessibilityNodeInfo? {
            val rootNode = rootInActiveWindow ?: return null
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            return nodes.firstOrNull()
        }

        /**
         * Find node by view ID
         */
        fun findNodeById(id: String): AccessibilityNodeInfo? {
            val rootNode = rootInActiveWindow ?: return null
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            return nodes.firstOrNull()
        }

        /**
         * Click on node
         */
        fun clickNode(node: AccessibilityNodeInfo): Boolean {
            return try {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    // Use parent click if node not clickable
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
                e.printStackTrace()
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

                // Duration in MILLISECONDS (GestureDescription uses milliseconds!)
                // Ensure minimum 50ms and maximum 500ms for 3D game engines
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

                // Wait for gesture to complete (max 2 seconds)
                latch.await(2, TimeUnit.SECONDS)
                success
            } catch (e: Exception) {
                e.printStackTrace()
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
