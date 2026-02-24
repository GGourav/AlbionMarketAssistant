package com.albion.marketassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.albion.marketassistant.data.*
import kotlinx.coroutines.*
import com.albion.marketassistant.service.AutomationForegroundService

class MarketAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    companion object {
        private var instance: MarketAccessibilityService? = null
        fun getInstance(): MarketAccessibilityService? = instance
        
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    private var stateMachine: StateMachine? = null
    private var calibration: CalibrationData? = null
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        // Notify that accessibility service is ready
        val intent = Intent(AutomationForegroundService.ACTION_ACCESSIBILITY_READY)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for gesture-based automation
    }
    
    override fun onInterrupt() {
        // Handle interruption
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }
    
    fun setCalibration(data: CalibrationData) {
        calibration = data
    }
    
    fun startAutomation(mode: OperationMode) {
        val cal = calibration ?: CalibrationData()
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
            return try {
                val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs * 1_000_000))
                    .build()
                dispatchGesture(gesture, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        
        override fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
            return try {
                val path = Path().apply {
                    moveTo(startX.toFloat(), startY.toFloat())
                    lineTo(endX.toFloat(), endY.toFloat())
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs * 1_000_000))
                    .build()
                dispatchGesture(gesture, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        
        override fun injectText(text: String): Boolean {
            return try {
                val rootNode = rootInActiveWindow ?: return false
                val focusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                val result = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                focusNode.recycle()
                result
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
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
