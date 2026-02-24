package com.albion.marketassistant.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.albion.marketassistant.R
import com.albion.marketassistant.data.OperationMode
import com.albion.marketassistant.service.AutomationForegroundService
import com.albion.marketassistant.ui.settings.CalibrationActivity

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private var screenCaptureIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        findViewById<Button>(R.id.btnStartOverlay).setOnClickListener {
            checkPermissionsAndStart()
        }

        findViewById<Button>(R.id.btnCalibration).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            // Overlay permission granted
        }
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            showToast("Please allow 'Display over other apps' permission")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        try {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
        } catch (e: Exception) {
            showToast("Error starting screen capture: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CODE_SCREEN_CAPTURE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    screenCaptureIntent = data
                    startAutomationService(data)
                } else {
                    showToast("Screen capture permission denied")
                }
            }
        }
    }

    private fun startAutomationService(screenCaptureData: Intent) {
        try {
            val serviceIntent = Intent(this, AutomationForegroundService::class.java).apply {
                action = AutomationForegroundService.ACTION_START_MODE
                putExtra(AutomationForegroundService.EXTRA_MODE, OperationMode.NEW_ORDER_SWEEPER)
                putExtra("SCREEN_CAPTURE_INTENT", screenCaptureData)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            showToast("Assistant started - Enable Accessibility Service in Settings")
        } catch (e: Exception) {
            showToast("Error starting service: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQUEST_CODE_SCREEN_CAPTURE = 1000
    }
}
