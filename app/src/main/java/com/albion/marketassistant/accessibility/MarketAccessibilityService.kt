package com.albion.marketassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.albion.marketassistant.data.*
import com.albion.marketassistant.service.AutomationForegroundService
import kotlinx.coroutines.*
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
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        val intent = Intent(AutomationForegroundService.ACTION_ACCESSIBILITY_READY)
        intent.setPackage(packageName)
        sendBroadcast(intent)
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
        val cal = calibration ?: CalibrationData()
        stateMachine?.stop()
        stateMachine = StateMachine(serviceScope, cal, UIInteractorImpl())
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
    
    inner class UIInteractorImpl : UIInteractor {
        
        override fun performTap(x: Int, y: Int, durationMs: Long): Boolean {
            return try {
                var success = false
                val path = Path().apply { 
                    moveTo(x.toFloat(), y.toFloat()) 
                }
                
                val duration = min(durationMs * 1_000_000L, 300_000_000L)
                
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
                
                val latch = java.util.concurrent.CountDownLatch(1)
                
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
                
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        
        override fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
            return try {
                var success = false
                val path = Path().apply {
                    moveTo(startX.toFloat(), startY.toFloat())
                    lineTo(endX.toFloat(), endY.toFloat())
                }
                
                val duration = min(durationMs * 1_000_000L, 500_000_000L)
                
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                    .build()
                
                val latch = java.util.concurrent.CountDownLatch(1)
                
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
                
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        
        override fun injectText(text: String): Boolean {
            return try {
                val rootNode = rootInActiveWindow ?: return false
                
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
                    return result
                }
                
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
    }
}

interface UIInteractor {
    fun performTap(x: Int, y: Int, durationMs: Long): Boolean
    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean
    fun injectText(text: String): Boolean
    fun clearTextField(): Boolean
    fun dismissKeyboard(): Boolean
}
