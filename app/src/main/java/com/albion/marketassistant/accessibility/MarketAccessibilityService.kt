package com.albion.marketassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.albion.marketassistant.service.AutomationForegroundService
import kotlinx.coroutines.*

class MarketAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "MarketAccessibility"
        var instance: MarketAccessibilityService? = null
            private set
    }
    
    private lateinit var uiInteractor: UIInteractor
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onServiceConnected() {
        instance = this
        
        uiInteractor = UIInteractor(this)
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_TREE
            notificationTimeout = 0
        }
        
        setServiceInfo(info)
        
        try {
            sendBroadcast(Intent(AutomationForegroundService.ACTION_ACCESSIBILITY_READY))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        showToast("Accessibility Connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Handle window changes
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Handle content changes
                }
            }
        }
    }
    
    override fun onInterrupt() {
        serviceScope.cancel()
    }
    
    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }
    
    fun getUIInteractor(): UIInteractor = uiInteractor
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
