package com.albion.marketassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_DELETE
import kotlinx.coroutines.delay

class UIInteractor(private val accessibilityService: AccessibilityService) {

    /**
     * Performs a tap at specified coordinates.
     */
    suspend fun performTap(x: Int, y: Int, durationMs: Long = 100) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                path,
                0,
                durationMs,
                false
            ))
            .build()
        
        accessibilityService.dispatchGesture(gesture, null, null)
        delay(durationMs + 50)
    }

    /**
     * Performs a swipe gesture from (startX, startY) to (endX, endY).
     */
    suspend fun performSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long
    ) {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                path,
                0,
                durationMs,
                false
            ))
            .build()
        
        accessibilityService.dispatchGesture(gesture, null, null)
        delay(durationMs + 50)
    }

    /**
     * Clears the currently focused text field using backspace simulation.
     */
    suspend fun clearTextField() {
        val rootNode = accessibilityService.rootInActiveWindow ?: return
        val focusedNode = findFocusedNode(rootNode) ?: return
        
        // Select all text
        focusedNode.performAction(AccessibilityNodeInfo.ACTION_SELECT_ALL)
        delay(100)
        
        // Delete selected text
        repeat(20) {
            focusedNode.performAction(ACTION_DELETE)
            delay(10)
        }
    }

    /**
     * Injects text into the currently focused text field.
     */
    suspend fun injectText(text: String) {
        val rootNode = accessibilityService.rootInActiveWindow ?: return
        val focusedNode = findFocusedNode(rootNode) ?: return
        
        // Use accessibility bundle to inject text
        val bundle = Bundle()
        bundle.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        delay(100)
    }

    /**
     * Finds the currently focused text input node.
     */
    private fun findFocusedNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isFocused && root.isEditable) {
            return root
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null) {
                val result = findFocusedNode(child)
                if (result != null) return result
                child.recycle()
            }
        }
        
        return null
    }

    /**
     * Finds a node by class name and text (case-insensitive).
     */
    fun findNodeByText(
        root: AccessibilityNodeInfo?,
        text: String,
        className: String? = null
    ): AccessibilityNodeInfo? {
        root ?: return null
        
        if (root.text != null && root.text.toString().contains(text, ignoreCase = true)) {
            if (className == null || root.className == className) {
                return root
            }
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null) {
                val result = findNodeByText(child, text, className)
                if (result != null) return result
                child.recycle()
            }
        }
        
        return null
    }

    /**
     * Scrolls the viewport using swipe gesture.
     */
    suspend fun scrollUp(distance: Int = 500) {
        performSwipe(500, 600, 500, 600 - distance, 400L)
    }

    suspend fun scrollDown(distance: Int = 500) {
        performSwipe(500, 200, 500, 200 + distance, 400L)
    }
}
