package com.albion.marketassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.albion.marketassistant.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MarketAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    companion object {
        private var instance: MarketAccessibilityService? = null
        fun getInstance(): MarketAccessibilityService? = instance
    }
    
    private var stateMachine: StateMachine? = null
    private var calibration: CalibrationData? = null
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    
    override fun onInterrupt() {}
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }
    
    fun setCalibration(data: CalibrationData) {
        calibration = data
    }
    
    fun startAutomation(mode: OperationMode) {
        val cal = calibration ?: return
        stateMachine?.stop()
        stateMachine = StateMachine(serviceScope, cal, UIInteractorImpl())
        stateMachine?.startMode(mode)
    }
    
    fun stopAutomation() {
        stateMachine?.stop()
        stateMachine = null
    }
    
    fun isRunning(): Boolean = stateMachine != null
    
    fun getUIInteractor(): UIInteractor = UIInteractorImpl()
    
    inner class UIInteractorImpl : UIInteractor {
        override fun performTap(x: Int, y: Int, durationMs: Long): Boolean {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs * 1000000))
                .build()
            return dispatchGesture(gesture, null, null)
        }
        
        override fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs * 1000000))
                .build()
            return dispatchGesture(gesture, null, null)
        }
        
        override fun injectText(text: String): Boolean {
            val rootNode = rootInActiveWindow ?: return false
            val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val result = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusNode.recycle()
            return result
        }
        
        override fun clearTextField(): Boolean = injectText("")
    }
}

interface UIInteractor {
    fun performTap(x: Int, y: Int, durationMs: Long): Boolean
    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean
    fun injectText(text: String): Boolean
    fun clearTextField(): Boolean
}
