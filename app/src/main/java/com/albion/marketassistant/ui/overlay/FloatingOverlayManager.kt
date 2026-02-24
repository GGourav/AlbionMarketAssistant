package com.albion.marketassistant.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.albion.marketassistant.R

class FloatingOverlayManager(
    private val context: Context, 
    private val onAction: (String) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var tvStatus: TextView? = null

    fun show(initialStatus: String = "Ready") {
        if (overlayView != null) {
            updateStatus(initialStatus)
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Please allow 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        try {
            overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_controls, null)
            
            tvStatus = overlayView?.findViewById(R.id.tvStatus)
            tvStatus?.text = initialStatus
            
            overlayView?.findViewById<Button>(R.id.btnCreateOrder)?.setOnClickListener {
                onAction("CREATE_ORDER")
            }
            
            overlayView?.findViewById<Button>(R.id.btnEditOrder)?.setOnClickListener {
                onAction("EDIT_ORDER")
            }
            
            overlayView?.findViewById<Button>(R.id.btnStop)?.setOnClickListener {
                onAction("STOP")
                hide()
            }
            
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            
            overlayView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(overlayView, params)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Overlay error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateStatus(status: String) {
        tvStatus?.text = status
    }

    fun hide() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
                tvStatus = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
