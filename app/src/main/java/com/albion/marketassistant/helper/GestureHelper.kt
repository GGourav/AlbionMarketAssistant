// FILE: app/src/main/java/com/albion/marketassistant/helper/GestureHelper.kt
// UPDATED: v3 - Fixed HANDLE_ERROR_POPUP with step logging + correct +1 outbid flow
// Target: iQOO Neo 6 (1080×2400) - works on any screen size

package com.albion.marketassistant.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.res.Resources
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GestureHelper - Percentage-based gesture automation with STEP LOGGING
 * 
 * CRITICAL: All coordinates are percentages (0.0 to 1.0)
 * - xPercent=0.5 means center of screen horizontally
 * - yPercent=0.5 means center of screen vertically
 * 
 * Every tap has a stepName for debugging - shows exactly which step fails
 */
class GestureHelper(
    private val service: AccessibilityService,
    private val debugMode: Boolean = false,
    private val onStepCallback: ((String) -> Unit)? = null
) {

    companion object {
        private const val TAG = "GestureHelper"
        
        // Default delays optimized for iQOO Neo 6
        const val DEFAULT_TAP_DELAY_MS: Long = 1400      // Longer for game response
        const val DEFAULT_SWIPE_DELAY_MS: Long = 1500
        const val DEFAULT_GESTURE_DURATION_MS: Long = 100 // Slightly longer for 3D game
        const val DEFAULT_SWIPE_DURATION_MS: Long = 500
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getWidth(): Int = Resources.getSystem().displayMetrics.widthPixels
    private fun getHeight(): Int = Resources.getSystem().displayMetrics.heightPixels

    /**
     * Perform a tap at percentage coordinates with STEP NAME for logging
     * This is the key fix - every tap tells us what it's doing
     */
    suspend fun tap(
        xPercent: Double, 
        yPercent: Double, 
        delayAfterMs: Long = DEFAULT_TAP_DELAY_MS,
        stepName: String = ""
    ): Boolean = withContext(Dispatchers.Main) {
        val x = (getWidth() * xPercent).toInt()
        val y = (getHeight() * yPercent).toInt()
        
        // Log the step with details
        val logMsg = if (stepName.isNotEmpty()) {
            "STEP: $stepName → tap(${String.format("%.2f", xPercent)}, ${String.format("%.2f", yPercent)}) pixel($x,$y)"
        } else {
            "TAP: (${String.format("%.2f", xPercent)}, ${String.format("%.2f", yPercent)}) → pixel($x, $y)"
        }
        
        Log.i(TAG, logMsg)
        
        // Show toast and callback in debug mode
        if (debugMode && stepName.isNotEmpty()) {
            showToast("📍 $stepName")
            onStepCallback?.invoke(stepName)
        }
        
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, DEFAULT_GESTURE_DURATION_MS))
            .build()
        
        val success = dispatchGestureSync(gesture)
        
        if (!success) {
            Log.e(TAG, "❌ FAILED: $stepName - Gesture dispatch failed")
        }
        
        if (delayAfterMs > 0) {
            Thread.sleep(delayAfterMs)
        }
        
        success
    }

    /**
     * Synchronous tap (non-suspend) for simpler use cases
     */
    fun tapSync(
        xPercent: Double, 
        yPercent: Double, 
        delayMs: Long = DEFAULT_TAP_DELAY_MS,
        stepName: String = ""
    ): Boolean {
        val x = (getWidth() * xPercent).toInt()
        val y = (getHeight() * yPercent).toInt()
        
        val logMsg = if (stepName.isNotEmpty()) {
            "STEP: $stepName → tap(${String.format("%.2f", xPercent)}, ${String.format("%.2f", yPercent)})"
        } else {
            "TAP: (${String.format("%.2f", xPercent)}, ${String.format("%.2f", yPercent)})"
        }
        
        Log.i(TAG, logMsg)
        
        if (debugMode && stepName.isNotEmpty()) {
            showToast("📍 $stepName")
            onStepCallback?.invoke(stepName)
        }
        
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, DEFAULT_GESTURE_DURATION_MS))
            .build()
        
        val success = dispatchGestureSyncBlocking(gesture)
        
        if (delayMs > 0) {
            Thread.sleep(delayMs)
        }
        
        return success
    }

    /**
     * Perform a swipe (scroll down - finger moves up)
     */
    suspend fun scrollDown(
        delayAfterMs: Long = DEFAULT_SWIPE_DELAY_MS,
        stepName: String = "Scroll down"
    ): Boolean = withContext(Dispatchers.Main) {
        val w = getWidth().toFloat()
        val h = getHeight().toFloat()
        
        val startX = (w * 0.5).toInt()
        val startY = (h * 0.78).toInt()
        val endY = (h * 0.25).toInt()
        
        Log.i(TAG, "STEP: $stepName → swipe($startX, $startY → $startX, $endY)")
        
        if (debugMode) {
            showToast("📜 $stepName")
            onStepCallback?.invoke(stepName)
        }
        
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(startX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, DEFAULT_SWIPE_DURATION_MS))
            .build()
        
        val success = dispatchGestureSync(gesture)
        
        if (delayAfterMs > 0) {
            Thread.sleep(delayAfterMs)
        }
        
        success
    }

    /**
     * Swipe from bottom to top (scrolls list down)
     */
    suspend fun swipeDownToUp(
        startYPercent: Double = 0.78,
        endYPercent: Double = 0.25,
        xPercent: Double = 0.5,
        delayAfterMs: Long = DEFAULT_SWIPE_DELAY_MS,
        stepName: String = "Swipe up"
    ): Boolean = withContext(Dispatchers.Main) {
        val w = getWidth().toFloat()
        val h = getHeight().toFloat()
        
        val startX = (w * xPercent).toInt()
        val startY = (h * startYPercent).toInt()
        val endY = (h * endYPercent).toInt()
        
        Log.i(TAG, "STEP: $stepName → (${startX}, $startY) → (${startX}, $endY)")
        
        if (debugMode) {
            showToast("📜 $stepName")
        }
        
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(startX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, DEFAULT_SWIPE_DURATION_MS))
            .build()
        
        val success = dispatchGestureSync(gesture)
        
        if (delayAfterMs > 0) {
            Thread.sleep(delayAfterMs)
        }
        
        success
    }

    /**
     * Set price directly using AccessibilityNodeInfo - NO KEYBOARD
     * Returns detailed error message if fails
     */
    suspend fun setPriceField(newPrice: String, stepName: String = "Set price"): SetPriceResult = withContext(Dispatchers.Main) {
        try {
            Log.i(TAG, "STEP: $stepName → Setting price to '$newPrice'")
            
            if (debugMode) {
                showToast("💰 $stepName: $newPrice")
                onStepCallback?.invoke("$stepName: $newPrice")
            }
            
            val rootNode = service.rootInActiveWindow
            if (rootNode == null) {
                return@withContext SetPriceResult(false, "No root window - is app in foreground?")
            }
            
            val targetNode = findPriceFieldNode(rootNode)
            
            if (targetNode != null) {
                val arguments = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        newPrice
                    )
                }
                
                val result = targetNode.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    arguments
                )
                
                Thread.sleep(800)
                
                if (result) {
                    Log.i(TAG, "✅ Price set to '$newPrice'")
                    SetPriceResult(true, "Price set to $newPrice")
                } else {
                    Log.e(TAG, "❌ performAction returned false")
                    SetPriceResult(false, "setText action failed - field may not be editable")
                }
            } else {
                Log.e(TAG, "❌ Could not find price field node")
                SetPriceResult(false, "Price field not found - try manual calibration")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ setPriceField error: ${e.message}", e)
            SetPriceResult(false, "Exception: ${e.message}")
        }
    }

    /**
     * Non-suspend version for simpler use
     */
    fun setPriceDirect(newPrice: String): Boolean {
        Log.i(TAG, "STEP: Set price directly to '$newPrice'")
        
        if (debugMode) {
            showToast("💰 Set price: $newPrice")
        }
        
        try {
            val nodes = service.rootInActiveWindow?.findAccessibilityNodeInfosByText("Price") ?: emptyList()
            nodes.firstOrNull()?.let { node ->
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newPrice)
                val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                Thread.sleep(800)
                return result
            }
            
            // Fallback: find any editable field
            val editable = findEditableInSubtree(service.rootInActiveWindow)
            if (editable != null) {
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newPrice)
                val result = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                Thread.sleep(800)
                return result
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "setPriceDirect error", e)
            return false
        }
    }

    private fun findPriceFieldNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Strategy 1: Find by text "Price"
        val textNodes = root.findAccessibilityNodeInfosByText("Price")
        if (textNodes.isNotEmpty()) {
            for (node in textNodes) {
                val editable = findEditableInSubtree(node.parent ?: node)
                if (editable != null) return editable
            }
        }
        
        // Strategy 2: Find by hint text
        val hintNodes = root.findAccessibilityNodeInfosByText("Enter price")
        if (hintNodes.isNotEmpty()) {
            for (node in hintNodes) {
                val editable = findEditableInSubtree(node)
                if (editable != null) return editable
            }
        }
        
        // Strategy 3: Find any editable EditText
        return findEditableInSubtree(root)
    }

    private fun findEditableInSubtree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className == "android.widget.EditText" && node.isEditable && node.isEnabled) {
            return node
        }
        
        if (node.isEditable && node.isEnabled && node.className?.contains("Edit") == true) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableInSubtree(child)
            if (result != null) return result
        }
        
        return null
    }

    private fun dispatchGestureSync(gesture: GestureDescription): Boolean {
        var success = false
        val latch = CountDownLatch(1)
        
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                success = true
                latch.countDown()
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture cancelled")
                latch.countDown()
            }
        }, null)
        
        latch.await(3, TimeUnit.SECONDS)
        return success
    }

    private fun dispatchGestureSyncBlocking(gesture: GestureDescription): Boolean {
        var success = false
        val latch = CountDownLatch(1)
        
        mainHandler.post {
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    latch.countDown()
                }
            }, null)
        }
        
        latch.await(3, TimeUnit.SECONDS)
        return success
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(service, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun getScreenInfo(): String = "Screen: ${getWidth()}×${getHeight()} pixels"
}

/**
 * Result of set price operation with detailed error message
 */
data class SetPriceResult(
    val success: Boolean,
    val errorMessage: String = ""
)
