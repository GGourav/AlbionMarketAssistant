package com.albion.marketassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_DELETE
import kotlinx.coroutines.delay

class UIInteractor(private val accessibilityService: AccessibilityService) {

    fun performTap(x: Int, y: Int, durationMs: Long = 100) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        accessibilityService.dispatchGesture(gesture, null, null)
    }

    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        accessibilityService.dispatchGesture(gesture, null, null)
    }

    suspend fun clearTextField(node: AccessibilityNodeInfo?) {
        if (node == null) return
        repeat(15) {
            node.performAction(ACTION_DELETE)
            delay(10)
        }
    }

    suspend fun injectText(node: AccessibilityNodeInfo?, text: String) {
        if (node == null) return
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun findFocusedNode(): AccessibilityNodeInfo? {
        val rootInActiveWindow = accessibilityService.rootInActiveWindow ?: return null
        return findFocusRecursive(rootInActiveWindow)
    }

    private fun findFocusRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val focused = findFocusRecursive(child)
            if (focused != null) return focused
        }
        return null
    }
}
