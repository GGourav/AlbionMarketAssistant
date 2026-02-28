package com.albion.marketassistant.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.albion.marketassistant.R
import com.albion.marketassistant.data.SessionStatistics

class FloatingOverlayManager(
    private val context: Context,
    private val onAction: (String) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var tvStatus: TextView? = null
    private var tvStats: TextView? = null
    private var btnPause: Button? = null
    private var isPaused = false
    private var isExpanded = false

    fun show() {
        if (overlayView != null) return

        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Please allow 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 10
            y = 100
        }

        try {
            overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_controls, null)

            tvStatus = overlayView?.findViewById(R.id.tvStatus)
            tvStats = overlayView?.findViewById(R.id.tvStats)
            btnPause = overlayView?.findViewById(R.id.btnPause)

            overlayView?.findViewById<Button>(R.id.btnCreate)?.setOnClickListener {
                onAction("CREATE")
            }

            overlayView?.findViewById<Button>(R.id.btnEdit)?.setOnClickListener {
                onAction("EDIT")
            }

            overlayView?.findViewById<Button>(R.id.btnPause)?.setOnClickListener {
                togglePause()
            }

            overlayView?.findViewById<Button>(R.id.btnStop)?.setOnClickListener {
                onAction("STOP")
                hide()
            }

            overlayView?.findViewById<Button>(R.id.btnStats)?.setOnClickListener {
                toggleStats()
            }

            overlayView?.findViewById<Button>(R.id.btnExport)?.setOnClickListener {
                onAction("EXPORT")
            }

            setupDrag(params)
            windowManager.addView(overlayView, params)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Overlay error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDrag(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(overlayView, params)
                    } catch (e: Exception) { }
                    true
                }
                else -> false
            }
        }
    }

    private fun togglePause() {
        isPaused = !isPaused
        btnPause?.text = if (isPaused) ">" else "||"
        btnPause?.setBackgroundColor(
            if (isPaused) Color.parseColor("#4CAF50")
            else Color.parseColor("#FF9800")
        )
        onAction(if (isPaused) "PAUSE" else "RESUME")
        updateStatus(if (isPaused) "PAUSED" else "Running")
    }

    private fun toggleStats() {
        isExpanded = !isExpanded
        tvStats?.visibility = if (isExpanded) View.VISIBLE else View.GONE
    }

    fun updateStatus(status: String) {
        tvStatus?.text = status

        val color = when {
            status.contains("ERROR", ignoreCase = true) -> Color.parseColor("#F44336")
            status == "PAUSED" -> Color.parseColor("#FFC107")
            status == "COMPLETE" -> Color.parseColor("#4CAF50")
            status == "SAFETY HALT" -> Color.parseColor("#9C27B0")
            status == "LOW BATTERY" -> Color.parseColor("#FF5722")
            status == "RECOVERING" -> Color.parseColor("#03A9F4")
            else -> Color.parseColor("#4CAF50")
        }
        tvStatus?.setBackgroundColor(color)
    }

    fun updateStatistics(stats: SessionStatistics) {
        tvStats?.text = buildString {
            append("Session Stats\n")
            append("---------------\n")
            append("Cycles: ${stats.totalCycles}\n")
            append("Success: ${stats.successfulOperations}\n")
            append("Failed: ${stats.failedOperations}\n")
            append("Rate: ${(stats.getSuccessRate() * 100).toInt()}%\n")
            append("---------------\n")
            append("Created: ${stats.ordersCreated}\n")
            append("Edited: ${stats.ordersEdited}\n")
            append("---------------\n")
            append("Time: ${stats.getSessionDurationFormatted()}\n")
        }
    }

    fun updateProgress(processed: Int, total: Int) {
        tvStats?.text = buildString {
            append("Progress: $processed / $total\n")
            append("Success: $processed")
        }
    }

    fun isVisible(): Boolean = overlayView != null

    fun hide() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
                tvStatus = null
                tvStats = null
                btnPause = null
                isPaused = false
                isExpanded = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
