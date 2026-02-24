package com.albion.marketassistant.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.albion.marketassistant.R

class FloatingOverlayManager(private val context: Context, private val onAction: (String) -> Unit) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun show() {
        // Prevent drawing multiple overlays
        if (overlayView != null) return

        // Safety Check 1: Ensure permission is actually granted by the OS
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
            x = 100
            y = 100
        }

        try {
            overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_controls, null)
            
            overlayView?.findViewById<Button>(R.id.btnStop)?.setOnClickListener {
                onAction("STOP")
                hide()
            }

            // Safety Check 2: The Try-Catch net to prevent instant app death
            windowManager.addView(overlayView, params)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Overlay blocked by phone UI", Toast.LENGTH_SHORT).show()
        }
    }

    fun hide() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
