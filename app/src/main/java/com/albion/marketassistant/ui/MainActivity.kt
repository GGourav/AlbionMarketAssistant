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
    private var selectedMode: OperationMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Create Buy Orders mode
        findViewById<Button>(R.id.btnNewOrderSweeper).setOnClickListener {
            selectedMode = OperationMode.NEW_ORDER_SWEEPER
            checkPermissionsAndStart()
        }

        // Edit Buy Orders mode
        findViewById<Button>(R.id.btnOrderEditor).setOnClickListener {
            selectedMode = OperationMode.ORDER_EDITOR
            checkPermissionsAndStart()
        }

        // Calibration Settings
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
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            showToast("Please allow 'Display over other apps' permission")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // Start Screen Capture Request
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
                    selectedMode?.let { mode ->
                        startAutomationService(mode, data)
                    }
                } else {
                    showToast("Screen capture permission denied")
                }
            }
        }
    }

    private fun startAutomationService(mode: OperationMode, screenCaptureData: Intent) {
        try {
            val serviceIntent = Intent(this, AutomationForegroundService::class.java).apply {
                action = AutomationForegroundService.ACTION_START_MODE
                putExtra(AutomationForegroundService.EXTRA_MODE, mode)
                putExtra("SCREEN_CAPTURE_INTENT", screenCaptureData)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            showToast("${mode.name} started - Enable Accessibility Service")
            finish()
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
