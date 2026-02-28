package com.albion.marketassistant.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.lifecycle.lifecycleScope
import com.albion.marketassistant.R
import com.albion.marketassistant.data.AutomationMode
import com.albion.marketassistant.service.AutomationForegroundService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val MEDIA_PROJECTION_REQUEST_CODE = 1001
        private const val PREF_DEBUG_MODE = "debug_mode"
    }

    private lateinit var sharedPreferences: SharedPreferences
    
    private lateinit var btnCreateBuyOrder: Button
    private lateinit var btnEditBuyOrder: Button
    private lateinit var btnStop: Button
    private lateinit var btnCalibration: Button
    
    private lateinit var tvStatus: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvStatistics: TextView
    
    private lateinit var cardStatus: CardView
    private lateinit var switchDebugMode: Switch
    
    private var pendingMode: AutomationMode = AutomationMode.CREATE_BUY_ORDER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        
        initViews()
        setupClickListeners()
        observeState()
        updateUI()
    }
    
    private fun initViews() {
        btnCreateBuyOrder = findViewById(R.id.btn_create_buy_order)
        btnEditBuyOrder = findViewById(R.id.btn_edit_buy_order)
        btnStop = findViewById(R.id.btn_stop)
        btnCalibration = findViewById(R.id.btn_calibration)
        
        tvStatus = findViewById(R.id.tv_status)
        tvProgress = findViewById(R.id.tv_progress)
        tvStatistics = findViewById(R.id.tv_statistics)
        
        cardStatus = findViewById(R.id.card_status)
        switchDebugMode = findViewById(R.id.switch_debug_mode)
        
        switchDebugMode.isChecked = sharedPreferences.getBoolean(PREF_DEBUG_MODE, false)
    }
    
    private fun setupClickListeners() {
        btnCreateBuyOrder.setOnClickListener {
            pendingMode = AutomationMode.CREATE_BUY_ORDER
            requestMediaProjection()
        }
        
        btnEditBuyOrder.setOnClickListener {
            pendingMode = AutomationMode.EDIT_BUY_ORDER
            requestMediaProjection()
        }
        
        btnStop.setOnClickListener {
            stopService(Intent(this, AutomationForegroundService::class.java))
            updateUI()
        }
        
        btnCalibration.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        
        switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(PREF_DEBUG_MODE, isChecked).apply()
            Toast.makeText(
                this,
                if (isChecked) "Debug mode enabled - Will stop after 1 item" else "Debug mode disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun observeState() {
        lifecycleScope.launch {
            AutomationForegroundService.isRunning.collectLatest { isRunning ->
                updateButtonStates(isRunning)
            }
        }
        
        lifecycleScope.launch {
            AutomationForegroundService.currentState.collectLatest { state ->
                tvStatus.text = "State: $state"
            }
        }
        
        lifecycleScope.launch {
            AutomationForegroundService.progress.collectLatest { progress ->
                tvProgress.text = progress
            }
        }
        
        lifecycleScope.launch {
            AutomationForegroundService.statistics.collectLatest { stats ->
                tvStatistics.text = buildString {
                    appendLine("Items processed: ${stats.priceUpdates}")
                    appendLine("Session: ${stats.getSessionDurationFormatted()}")
                    appendLine("Profit: ${stats.estimatedProfitSilver} silver")
                }
            }
        }
        
        lifecycleScope.launch {
            AutomationForegroundService.lastError.collectLatest { error ->
                error?.let {
                    Toast.makeText(this@MainActivity, "Error: $it", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun requestMediaProjection() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQUEST_CODE
        )
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startAutomationService(resultCode, data)
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startAutomationService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, AutomationForegroundService::class.java).apply {
            action = AutomationForegroundService.ACTION_START
            putExtra(AutomationForegroundService.EXTRA_MODE, pendingMode)
            putExtra(AutomationForegroundService.EXTRA_MEDIA_RESULT_CODE, resultCode)
            putExtra(AutomationForegroundService.EXTRA_MEDIA_RESULT_DATA, data)
        }
        
        ContextCompat.startForegroundService(this, serviceIntent)
        updateUI()
    }
    
    private fun updateUI() {
        val isRunning = AutomationForegroundService.isRunning.value
        updateButtonStates(isRunning)
        
        if (!isRunning) {
            tvStatus.text = "State: IDLE"
            tvProgress.text = "Ready to start"
            tvStatistics.text = "No statistics yet"
        }
    }
    
    private fun updateButtonStates(isRunning: Boolean) {
        btnCreateBuyOrder.isEnabled = !isRunning
        btnEditBuyOrder.isEnabled = !isRunning
        btnStop.isEnabled = isRunning
        
        btnCreateBuyOrder.alpha = if (isRunning) 0.5f else 1.0f
        btnEditBuyOrder.alpha = if (isRunning) 0.5f else 1.0f
        btnStop.alpha = if (isRunning) 1.0f else 0.5f
        
        if (isRunning) {
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_running))
        } else {
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_idle))
        }
    }
}
