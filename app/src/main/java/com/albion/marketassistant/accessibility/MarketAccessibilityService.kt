package com.albion.marketassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.albion.marketassistant.data.*
import com.albion.marketassistant.ml.OCREngine
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.min

class MarketAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private var instance: MarketAccessibilityService? = null
        fun getInstance(): MarketAccessibilityService? = instance
        fun isServiceEnabled(): Boolean = instance != null
    }

    private var stateMachine: StateMachine? = null
    private var calibration: CalibrationData? = null
    private var ocrEngine: OCREngine? = null

    // Screen capture
    private var imageReader: ImageReader? = null
    private var lastScreenshot: Bitmap? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ocrEngine = OCREngine()

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
        imageReader?.close()
        lastScreenshot?.recycle()
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
     * Capture screen using accessibility service
     * Note: This requires CAPTURE_SCREEN capability in accessibility service config
     */
    fun captureScreen(): Bitmap? {
        // For now, return null - screen capture via accessibility requires additional setup
        // In production, use MediaProjection API or root access
        return null
    }

    /**
     * Perform OCR on screen region
     */
    suspend fun performOCR(region: Rect): List<OCRResult> {
        val screenshot = captureScreen() ?: return emptyList()
        return ocrEngine?.recognizeText(screenshot, region) ?: emptyList()
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
                
                // Simulate Ctrl+V (paste) - works on some games
                // Note: This requires additional implementation with InputConnection
                // For now, return false to indicate keyboard injection not fully supported
                // but clipboard is set, user can manually paste if needed
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
