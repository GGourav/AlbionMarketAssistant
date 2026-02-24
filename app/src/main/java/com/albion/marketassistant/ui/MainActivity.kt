package com.albion.marketassistant.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.albion.marketassistant.R
import com.albion.marketassistant.data.OperationMode
import com.albion.marketassistant.service.AutomationForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        findViewById<Button>(R.id.btnStartOverlay).setOnClickListener {
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        // Start Screen Capture Request
        startActivityForResult(projectionManager.createScreenCaptureIntent(), 1000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1000 && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, AutomationForegroundService::class.java).apply {
                putExtra("MODE", OperationMode.NEW_ORDER_SWEEPER)
                putExtra("SCREEN_CAPTURE_INTENT", data)
            }
            startForegroundService(serviceIntent)
            finish() // Close main UI to go to game
        }
    }
}
