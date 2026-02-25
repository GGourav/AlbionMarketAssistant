package com.albion.marketassistant.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.albion.marketassistant.R
import com.albion.marketassistant.accessibility.MarketAccessibilityService
import com.albion.marketassistant.data.OperationMode
import com.albion.marketassistant.service.AutomationForegroundService
import com.albion.marketassistant.ui.settings.CalibrationActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupButtons()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
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
    }

    private fun checkPermissions() {
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val isAccessibilityEnabled = MarketAccessibilityService.isServiceEnabled()
        val hasOverlayPermission = Settings.canDrawOverlays(this)

        findViewById<TextView>(R.id.tvAccessibilityStatus).apply {
            text = if (isAccessibilityEnabled) {
                "Accessibility: Enabled"
            } else {
                "Accessibility: Disabled"
            }
            setTextColor(if (isAccessibilityEnabled) {
                getColor(R.color.success)
            } else {
                getColor(R.color.error)
            })
        }

        findViewById<TextView>(R.id.tvOverlayStatus).apply {
            text = if (hasOverlayPermission) {
                "Overlay Permission: Granted"
            } else {
                "Overlay Permission: Required"
            }
            setTextColor(if (hasOverlayPermission) {
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

        return true
    }

    private fun startAutomationMode(mode: OperationMode) {
        val intent = Intent(this, AutomationForegroundService::class.java).apply {
            action = when (mode) {
                OperationMode.NEW_ORDER_SWEEPER -> AutomationForegroundService.ACTION_CREATE_MODE
                OperationMode.ORDER_EDITOR -> AutomationForegroundService.ACTION_EDIT_MODE
                OperationMode.IDLE -> AutomationForegroundService.ACTION_STOP_MODE
            }
        }
        startService(intent)
        Toast.makeText(this, "${mode.name} started", Toast.LENGTH_SHORT).show()
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
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
                updatePermissionStatus()
            } else {
                Toast.makeText(this, "Overlay permission is required for floating controls", Toast.LENGTH_LONG).show()
            }
        }
    }
}
