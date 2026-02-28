// FILE: app/src/main/java/com/albion/marketassistant/helper/GestureHelper.kt
// UPDATED: v3 - Fixed HANDLE_ERROR_POPUP with step logging + correct +1 outbid flow
// Target: iQOO Neo 6 (1080×2400) - works on any screen size

package com.albion.marketassistant.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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

class GestureHelper(
    private val service: AccessibilityService,
    private val debugMode: Boolean = false,
    private val onStepCallback: ((String) -> Unit)? = null
) {

    companion object {
        private const val TAG = "GestureHelper"
        const val DEFAULT_TAP_DELAY_MS: Long = 1400
        const val DEFAULT_SWIPE_DELAY_MS: Long = 1500
        const val DEFAULT_GESTURE_DURATION_MS: Long = 100
        const val DEFAULT_SWIPE_DURATION_MS: Long = 500
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getWidth(): Int = Resources.getSystem().displayMetrics.widthPixels
    private fun getHeight(): Int = Resources.getSystem().displayMetrics.heightPixels

    suspend fun tap(
        xPercent: Double, 
        yPercent: Double, 
        delayAfterMs: Long = DEFAULT_TAP_DELAY_MS,
        stepName: String = ""
    ): Boolean = withContext(Dispatchers.Main) {
        val x = (getWidth() * xPercent).toInt()
        val y = (getHeight() * yPercent).toInt()
        
        val logMsg = if (stepName.isNotEmpty()) {
            "STEP: $stepName -> tap(${String.format("%.2f", xPercent)}, ${String.format("%.2f", yPercent)}) pixel($x,$y)"
        } else {
            "TAP: (${String.format("%.2f", xPercent)}, ${String.format("%.2f", yPercent)}) -> pixel($x, $y)"
        }
        
        Log.i(TAG, logMsg)
        
        if (debugMode && stepName.isNotEmpty()) {
            showToast("Step: $stepName")
            onStepCallback?.invoke(stepName)
        }
        
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, DEFAULT_GESTURE_DURATION_MS))
            .build()
        
        val success = dispatchGestureSync(gesture)
        
        if (!success) {
            Log.e(TAG, "FAILED: $stepName - Gesture dispatch failed")
        }
        
        if (delayAfterMs > 0) {
            Thread.sleep(delayAfterMs)
        }
        
        success
    }

    suspend fun swipeDownToUp(
        startYPercent: Double = 0.78,
        endYPercent: Double = 0.25,
        xPercent: Double = 0.5,
        delayAfterMs: Long = DEFAULT_SWIPE_DELAY_MS,
        stepName: String = "Scroll"
    ): Boolean = withContext(Dispatchers.Main) {
        val w = getWidth().toFloat()
        val h = getHeight().toFloat()
        
        val startX = (w * xPercent).toInt()
        val startY = (h * startYPercent).toInt()
        val endY = (h * endYPercent).toInt()
        
        Log.i(TAG, "STEP: $stepName -> ($startX, $startY) -> ($startX, $endY)")
        
        if (debugMode) {
            showToast("Scroll")
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

    suspend fun setPriceField(newPrice: String, stepName: String = "Set price"): SetPriceResult = withContext(Dispatchers.Main) {
        try {
            Log.i(TAG, "STEP: $stepName -> Setting price to '$newPrice'")
            
            if (debugMode) {
                showToast("Set price: $newPrice")
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
                    Log.i(TAG, "Price set to '$newPrice'")
                    SetPriceResult(true, "Price set to $newPrice")
                } else {
                    Log.e(TAG, "performAction returned false")
                    SetPriceResult(false, "setText action failed - field may not be editable")
                }
            } else {
                Log.e(TAG, "Could not find price field node")
                SetPriceResult(false, "Price field not found - try manual calibration")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setPriceField error: ${e.message}", e)
            SetPriceResult(false, "Exception: ${e.message}")
        }
    }

    private fun findPriceFieldNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val textNodes = root.findAccessibilityNodeInfosByText("Price")
        if (textNodes.isNotEmpty()) {
            for (node in textNodes) {
                val editable = findEditableInSubtree(node.parent ?: node)
                if (editable != null) return editable
            }
        }
        
        val hintNodes = root.findAccessibilityNodeInfosByText("Enter price")
        if (hintNodes.isNotEmpty()) {
            for (node in hintNodes) {
                val editable = findEditableInSubtree(node)
                if (editable != null) return editable
            }
        }
        
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

    private fun showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(service, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun getScreenInfo(): String = "Screen: ${getWidth()}x${getHeight()} pixels"
}

data class SetPriceResult(
    val success: Boolean,
    val errorMessage: String = ""
)
