// FILE: app/src/main/java/com/albion/marketassistant/helper/GestureHelper.kt
// Percentage-based gesture helper for Albion Online mobile
// Target: iQOO Neo 6 (1080×2400) - works on any screen size

package com.albion.marketassistant.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Resources
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GestureHelper - Percentage-based gesture automation
 * 
 * CRITICAL: All coordinates are percentages (0.0 to 1.0)
 * - xPercent=0.5 means center of screen horizontally
 * - yPercent=0.5 means center of screen vertically
 * 
 * This makes the automation work on ANY screen size.
 * Tested on iQOO Neo 6 (1080×2400, 20:9 aspect ratio)
 */
class GestureHelper(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "GestureHelper"
        
        // Default delays (can be overridden via parameters)
        const val DEFAULT_TAP_DELAY_MS: Long = 800
        const val DEFAULT_SWIPE_DELAY_MS: Long = 1200
        const val DEFAULT_GESTURE_DURATION_MS: Long = 80
        const val DEFAULT_SWIPE_DURATION_MS: Long = 400
    }

    private fun getWidth(): Int = Resources.getSystem().displayMetrics.widthPixels
    private fun getHeight(): Int = Resources.getSystem().displayMetrics.heightPixels

    /**
     * Perform a tap at percentage coordinates
     */
    suspend fun tap(
        xPercent: Double, 
        yPercent: Double, 
        delayAfterMs: Long = DEFAULT_TAP_DELAY_MS
    ): Boolean = withContext(Dispatchers.Main) {
        val x = (getWidth() * xPercent).toInt()
        val y = (getHeight() * yPercent).toInt()
        
        Log.d(TAG, "TAP: (${"%.2f".format(xPercent)}, ${"%.2f".format(yPercent)}) → pixel($x, $y)")
        
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, DEFAULT_GESTURE_DURATION_MS))
            .build()
        
        val success = dispatchGestureSync(gesture)
        
        if (delayAfterMs > 0) {
            Thread.sleep(delayAfterMs)
        }
        
        success
    }

    /**
     * Perform a swipe from bottom to top (scrolls list down)
     */
    suspend fun swipeDownToUp(
        startYPercent: Double = 0.75,
        endYPercent: Double = 0.35,
        xPercent: Double = 0.5,
        delayAfterMs: Long = DEFAULT_SWIPE_DELAY_MS
    ): Boolean = withContext(Dispatchers.Main) {
        val w = getWidth().toFloat()
        val h = getHeight().toFloat()
        
        val startX = (w * xPercent).toInt()
        val startY = (h * startYPercent).toInt()
        val endY = (h * endYPercent).toInt()
        
        Log.d(TAG, "SWIPE UP: ($startX, $startY) → ($startX, $endY)")
        
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
     * Perform a swipe from top to bottom (scrolls list up)
     */
    suspend fun swipeUpToDown(
        startYPercent: Double = 0.35,
        endYPercent: Double = 0.75,
        xPercent: Double = 0.5,
        delayAfterMs: Long = DEFAULT_SWIPE_DELAY_MS
    ): Boolean = withContext(Dispatchers.Main) {
        val w = getWidth().toFloat()
        val h = getHeight().toFloat()
        
        val startX = (w * xPercent).toInt()
        val startY = (h * startYPercent).toInt()
        val endY = (h * endYPercent).toInt()
        
        Log.d(TAG, "SWIPE DOWN: ($startX, $startY) → ($startX, $endY)")
        
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
     * Set text in a field using AccessibilityNodeInfo.ACTION_SET_TEXT
     */
    suspend fun setPriceField(newPrice: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val rootNode = service.rootInActiveWindow ?: run {
                Log.w(TAG, "setPriceField: No root window")
                return@withContext false
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
                
                Log.d(TAG, "setPriceField: Set price to '$newPrice' → $result")
                Thread.sleep(600)
                result
            } else {
                Log.w(TAG, "setPriceField: Could not find price field node")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "setPriceField error", e)
            false
        }
    }

    private fun findPriceFieldNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Strategy 1: Find by text hint
        val textNodes = root.findAccessibilityNodeInfosByText("Price")
        if (textNodes.isNotEmpty()) {
            for (node in textNodes) {
                val editable = findEditableInSubtree(node.parent ?: node)
                if (editable != null) return editable
            }
        }
        
        // Strategy 2: Find any editable field
        return findEditableInSubtree(root)
    }

    private fun findEditableInSubtree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) {
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

    fun getScreenInfo(): String = "Screen: ${getWidth()}×${getHeight()} pixels"
}
