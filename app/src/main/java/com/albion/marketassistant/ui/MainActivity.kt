package com.albion.marketassistant.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.albion.marketassistant.R
import com.albion.marketassistant.accessibility.MarketAccessibilityService
import com.albion.marketassistant.data.OperationMode
import com.albion.marketassistant.service.AutomationForegroundService
import com.albion.marketassistant.ui.settings.CalibrationActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    private val MEDIA_PROJECTION_REQUEST_CODE = 1002
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1003

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchDebugMode: SwitchCompat
    private lateinit var tvDebugStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        setupButtons()
        setupDebugMode()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateDebugStatus()
    }

    private fun setupDebugMode() {
        switchDebugMode = findViewById(R.id.switchDebugMode)
        tvDebugStatus = findViewById(R.id.tvDebugStatus)
        
        val debugMode = sharedPreferences.getBoolean(AutomationForegroundService.PREF_DEBUG_MODE, false)
        switchDebugMode.isChecked = debugMode
        updateDebugStatus()
        
        switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit()
                .putBoolean(AutomationForegroundService.PREF_DEBUG_MODE, isChecked)
                .apply()
            updateDebugStatus()
            
            val msg = if (isChecked) {
                "🐛 DEBUG MODE ON - Will process 1 item with step toasts"
            } else {
                "🐛 DEBUG MODE OFF - Normal operation"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateDebugStatus() {
        val debugMode = sharedPreferences.getBoolean(AutomationForegroundService.PREF_DEBUG_MODE, false)
        switchDebugMode.isChecked = debugMode
        tvDebugStatus.text = if (debugMode) "ON" else "OFF"
        tvDebugStatus.setTextColor(
            if (debugMode) getColor(R.color.success) else getColor(R.color.error)
        )
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnStartCreate).setOnClickListener {
            if (checkAllPermissions()) {
                startAutomationMode(OperationMode.NEW_ORDER_SWEEPER)
            }
        }

        findViewById<Button>(R.id.btnStartEdit).setOnClickListener {
            if (checkAllPermissions()) {
                startAutomationMode(OperationMode.ORDER_EDITOR)
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopAutomation()
        }

        findViewById<Button>(R.id.btnOpenAccessibility).setOnClickListener {
            openAccessibilitySettings()
        }

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        findViewById<Button>(R.id.btnRequestScreenCapture)?.setOnClickListener {
            requestMediaProjection()
        }
    }

    private fun checkPermissions() {
        updatePermissionStatus()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun updatePermissionStatus() {
        val isAccessibilityEnabled = MarketAccessibilityService.isServiceEnabled()
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == 
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        findViewById<TextView>(R.id.tvAccessibilityStatus).apply {
            text = if (isAccessibilityEnabled) {
                "Accessibility: Enabled ✓"
            } else {
                "Accessibility: Disabled ✗"
            }
            setTextColor(if (isAccessibilityEnabled) {
                getColor(R.color.success)
            } else {
                getColor(R.color.error)
            })
        }

        findViewById<TextView>(R.id.tvOverlayStatus).apply {
            text = if (hasOverlayPermission) {
                "Overlay Permission: Granted ✓"
            } else {
                "Overlay Permission: Required ✗"
            }
            setTextColor(if (hasOverlayPermission) {
                getColor(R.color.success)
            } else {
                getColor(R.color.error)
            })
        }

        findViewById<TextView>(R.id.tvScreenCaptureStatus)?.apply {
            val (resultCode, _) = MarketAccessibilityService.getMediaProjectionData()
            text = if (resultCode != 0) {
                "Screen Capture: Granted ✓"
            } else {
                "Screen Capture: Required ✗"
            }
            setTextColor(if (resultCode != 0) {
                getColor(R.color.success)
            } else {
                getColor(R.color.error)
            })
        }
    }

    private fun checkAllPermissions(): Boolean {
        if (!MarketAccessibilityService.isServiceEnabled()) {
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return false
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant Overlay permission", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return false
        }

        val (resultCode, _) = MarketAccessibilityService.getMediaProjectionData()
        if (resultCode == 0) {
            Toast.makeText(this, "Please grant Screen Capture permission for OCR", Toast.LENGTH_LONG).show()
            requestMediaProjection()
            return false
        }

        return true
    }

    private fun requestMediaProjection() {
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQUEST_CODE
        )
    }

    private fun startAutomationMode(mode: OperationMode) {
        val debugMode = sharedPreferences.getBoolean(AutomationForegroundService.PREF_DEBUG_MODE, false)
        
        val intent = Intent(this, AutomationForegroundService::class.java).apply {
            action = when (mode) {
                OperationMode.NEW_ORDER_SWEEPER -> AutomationForegroundService.ACTION_CREATE_MODE
                OperationMode.ORDER_EDITOR -> AutomationForegroundService.ACTION_EDIT_MODE
                OperationMode.IDLE -> AutomationForegroundService.ACTION_STOP_MODE
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        val modeName = when (mode) {
            OperationMode.NEW_ORDER_SWEEPER -> "CREATE ORDERS"
            OperationMode.ORDER_EDITOR -> "EDIT ORDERS"
            OperationMode.IDLE -> "IDLE"
        }
        
        val debugText = if (debugMode) " [DEBUG - 1 item]" else ""
        Toast.makeText(this, "$modeName started$debugText", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutomation() {
        val intent = Intent(this, AutomationForegroundService::class.java).apply {
            action = AutomationForegroundService.ACTION_STOP_MODE
        }
        startService(intent)
        Toast.makeText(this, "Automation stopped", Toast.LENGTH_SHORT).show()
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
                    updatePermissionStatus()
                } else {
                    Toast.makeText(this, "Overlay permission is required for floating controls", Toast.LENGTH_LONG).show()
                }
            }
            
            MEDIA_PROJECTION_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    MarketAccessibilityService.setMediaProjectionData(resultCode, data)
                    Toast.makeText(this, "Screen capture permission granted", Toast.LENGTH_SHORT).show()
                    updatePermissionStatus()
                } else {
                    Toast.makeText(this, "Screen capture permission denied - OCR will not work", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied - you won't see status updates", Toast.LENGTH_LONG).show()
            }
        }
    }
}
