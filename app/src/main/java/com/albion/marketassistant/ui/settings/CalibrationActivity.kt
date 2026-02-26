// FILE: app/src/main/java/com/albion/marketassistant/ui/settings/CalibrationActivity.kt
// ============================================
// CalibrationActivity.kt - Fixed (Removed enableWindowVerification)
// ============================================

package com.albion.marketassistant.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.albion.marketassistant.R
import com.albion.marketassistant.data.AutomationConfig
import com.google.gson.Gson

class CalibrationActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    
    // CREATE Mode inputs
    private lateinit var etCreateRowStartX: EditText
    private lateinit var etCreateRowStartY: EditText
    private lateinit var etCreateRowEndX: EditText
    private lateinit var etCreateRowEndY: EditText
    private lateinit var etCreateRowHeight: EditText
    private lateinit var etCreatePlusButtonX: EditText
    private lateinit var etCreatePlusButtonY: EditText
    private lateinit var etCreateHardPriceCap: EditText
    private lateinit var etCreateMaxRows: EditText
    private lateinit var etCreateSwipeX: EditText
    private lateinit var etCreateSwipeY: EditText
    private lateinit var etCreateSwipeDistance: EditText
    
    // EDIT Mode inputs
    private lateinit var etEditRow1X: EditText
    private lateinit var etEditRow1Y: EditText
    private lateinit var etEditPriceInputX: EditText
    private lateinit var etEditPriceInputY: EditText
    private lateinit var etEditHardPriceCap: EditText
    private lateinit var etEditPriceIncrement: EditText
    private lateinit var etEditCreateButtonX: EditText
    private lateinit var etEditCreateButtonY: EditText
    private lateinit var etEditConfirmButtonX: EditText
    private lateinit var etEditConfirmButtonY: EditText
    
    // Common inputs
    private lateinit var etOcrRegionLeft: EditText
    private lateinit var etOcrRegionTop: EditText
    private lateinit var etOcrRegionRight: EditText
    private lateinit var etOcrRegionBottom: EditText
    private lateinit var etLoopDelay: EditText
    private lateinit var etGestureDuration: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)
        
        sharedPrefs = getSharedPreferences("AlbionMarketAssistant", Context.MODE_PRIVATE)
        
        initViews()
        loadConfig()
        setupButtons()
    }

    private fun initViews() {
        // CREATE Mode
        etCreateRowStartX = findViewById(R.id.et_create_row_start_x)
        etCreateRowStartY = findViewById(R.id.et_create_row_start_y)
        etCreateRowEndX = findViewById(R.id.et_create_row_end_x)
        etCreateRowEndY = findViewById(R.id.et_create_row_end_y)
        etCreateRowHeight = findViewById(R.id.et_create_row_height)
        etCreatePlusButtonX = findViewById(R.id.et_create_plus_button_x)
        etCreatePlusButtonY = findViewById(R.id.et_create_plus_button_y)
        etCreateHardPriceCap = findViewById(R.id.et_create_hard_price_cap)
        etCreateMaxRows = findViewById(R.id.et_create_max_rows)
        etCreateSwipeX = findViewById(R.id.et_create_swipe_x)
        etCreateSwipeY = findViewById(R.id.et_create_swipe_y)
        etCreateSwipeDistance = findViewById(R.id.et_create_swipe_distance)
        
        // EDIT Mode
        etEditRow1X = findViewById(R.id.et_edit_row1_x)
        etEditRow1Y = findViewById(R.id.et_edit_row1_y)
        etEditPriceInputX = findViewById(R.id.et_edit_price_input_x)
        etEditPriceInputY = findViewById(R.id.et_edit_price_input_y)
        etEditHardPriceCap = findViewById(R.id.et_edit_hard_price_cap)
        etEditPriceIncrement = findViewById(R.id.et_edit_price_increment)
        etEditCreateButtonX = findViewById(R.id.et_edit_create_button_x)
        etEditCreateButtonY = findViewById(R.id.et_edit_create_button_y)
        etEditConfirmButtonX = findViewById(R.id.et_edit_confirm_button_x)
        etEditConfirmButtonY = findViewById(R.id.et_edit_confirm_button_y)
        
        // Common
        etOcrRegionLeft = findViewById(R.id.et_ocr_region_left)
        etOcrRegionTop = findViewById(R.id.et_ocr_region_top)
        etOcrRegionRight = findViewById(R.id.et_ocr_region_right)
        etOcrRegionBottom = findViewById(R.id.et_ocr_region_bottom)
        etLoopDelay = findViewById(R.id.et_loop_delay)
        etGestureDuration = findViewById(R.id.et_gesture_duration)
    }

    private fun loadConfig() {
        val configJson = sharedPrefs.getString("automation_config", null)
        if (configJson != null) {
            try {
                val config = Gson().fromJson(configJson, AutomationConfig::class.java)
                
                // CREATE Mode
                etCreateRowStartX.setText(config.createMode.rowStartX.toString())
                etCreateRowStartY.setText(config.createMode.rowStartY.toString())
                etCreateRowEndX.setText(config.createMode.rowEndX.toString())
                etCreateRowEndY.setText(config.createMode.rowEndY.toString())
                etCreateRowHeight.setText(config.createMode.rowHeight.toString())
                etCreatePlusButtonX.setText(config.createMode.plusButtonX.toString())
                etCreatePlusButtonY.setText(config.createMode.plusButtonY.toString())
                etCreateHardPriceCap.setText(config.createMode.hardPriceCap.toString())
                etCreateMaxRows.setText(config.createMode.maxRows.toString())
                etCreateSwipeX.setText(config.createMode.swipeX.toString())
                etCreateSwipeY.setText(config.createMode.swipeY.toString())
                etCreateSwipeDistance.setText(config.createMode.swipeDistance.toString())
                
                // EDIT Mode
                etEditRow1X.setText(config.editMode.row1X.toString())
                etEditRow1Y.setText(config.editMode.row1Y.toString())
                etEditPriceInputX.setText(config.editMode.priceInputX.toString())
                etEditPriceInputY.setText(config.editMode.priceInputY.toString())
                etEditHardPriceCap.setText(config.editMode.hardPriceCap.toString())
                etEditPriceIncrement.setText(config.editMode.priceIncrement.toString())
                etEditCreateButtonX.setText(config.editMode.createButtonX.toString())
                etEditCreateButtonY.setText(config.editMode.createButtonY.toString())
                etEditConfirmButtonX.setText(config.editMode.confirmButtonX.toString())
                etEditConfirmButtonY.setText(config.editMode.confirmButtonY.toString())
                
                // Common
                etOcrRegionLeft.setText(config.ocrRegionLeft.toString())
                etOcrRegionTop.setText(config.ocrRegionTop.toString())
                etOcrRegionRight.setText(config.ocrRegionRight.toString())
                etOcrRegionBottom.setText(config.ocrRegionBottom.toString())
                etLoopDelay.setText(config.loopDelayMs.toString())
                etGestureDuration.setText(config.gestureDurationMs.toString())
                
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading config: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_save_config).setOnClickListener {
            saveConfig()
        }
        
        findViewById<Button>(R.id.btn_reset_config).setOnClickListener {
            resetToDefaults()
        }
    }

    private fun saveConfig() {
        try {
            val config = AutomationConfig(
                createMode = CreateModeConfig(
                    rowStartX = etCreateRowStartX.text.toString().toIntOrNull() ?: 540,
                    rowStartY = etCreateRowStartY.text.toString().toIntOrNull() ?: 400,
                    rowEndX = etCreateRowEndX.text.toString().toIntOrNull() ?: 540,
                    rowEndY = etCreateRowEndY.text.toString().toIntOrNull() ?: 1800,
                    rowHeight = etCreateRowHeight.text.toString().toIntOrNull() ?: 120,
                    plusButtonX = etCreatePlusButtonX.text.toString().toIntOrNull() ?: 800,
                    plusButtonY = etCreatePlusButtonY.text.toString().toIntOrNull() ?: 600,
                    hardPriceCap = etCreateHardPriceCap.text.toString().toLongOrNull() ?: 100000000L,
                    maxRows = etCreateMaxRows.text.toString().toIntOrNull() ?: 8,
                    swipeX = etCreateSwipeX.text.toString().toIntOrNull() ?: 540,
                    swipeY = etCreateSwipeY.text.toString().toIntOrNull() ?: 1500,
                    swipeDistance = etCreateSwipeDistance.text.toString().toIntOrNull() ?: 300
                ),
                editMode = EditModeConfig(
                    row1X = etEditRow1X.text.toString().toIntOrNull() ?: 540,
                    row1Y = etEditRow1Y.text.toString().toIntOrNull() ?: 400,
                    priceInputX = etEditPriceInputX.text.toString().toIntOrNull() ?: 650,
                    priceInputY = etEditPriceInputY.text.toString().toIntOrNull() ?: 600,
                    hardPriceCap = etEditHardPriceCap.text.toString().toLongOrNull() ?: 100000000L,
                    priceIncrement = etEditPriceIncrement.text.toString().toLongOrNull() ?: 1L,
                    createButtonX = etEditCreateButtonX.text.toString().toIntOrNull() ?: 900,
                    createButtonY = etEditCreateButtonY.text.toString().toIntOrNull() ?: 600,
                    confirmButtonX = etEditConfirmButtonX.text.toString().toIntOrNull() ?: 540,
                    confirmButtonY = etEditConfirmButtonY.text.toString().toIntOrNull() ?: 1200
                ),
                ocrRegionLeft = etOcrRegionLeft.text.toString().toIntOrNull() ?: 400,
                ocrRegionTop = etOcrRegionTop.text.toString().toIntOrNull() ?: 500,
                ocrRegionRight = etOcrRegionRight.text.toString().toIntOrNull() ?: 700,
                ocrRegionBottom = etOcrRegionBottom.text.toString().toIntOrNull() ?: 700,
                loopDelayMs = etLoopDelay.text.toString().toLongOrNull() ?: 500L,
                gestureDurationMs = etGestureDuration.text.toString().toLongOrNull() ?: 200L
            )
            
            val configJson = Gson().toJson(config)
            sharedPrefs.edit().putString("automation_config", configJson).apply()
            
            Toast.makeText(this, "Configuration saved!", Toast.LENGTH_SHORT).show()
            finish()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving config: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetToDefaults() {
        // CREATE Mode defaults
        etCreateRowStartX.setText("540")
        etCreateRowStartY.setText("400")
        etCreateRowEndX.setText("540")
        etCreateRowEndY.setText("1800")
        etCreateRowHeight.setText("120")
        etCreatePlusButtonX.setText("800")
        etCreatePlusButtonY.setText("600")
        etCreateHardPriceCap.setText("100000000")
        etCreateMaxRows.setText("8")
        etCreateSwipeX.setText("540")
        etCreateSwipeY.setText("1500")
        etCreateSwipeDistance.setText("300")
        
        // EDIT Mode defaults
        etEditRow1X.setText("540")
        etEditRow1Y.setText("400")
        etEditPriceInputX.setText("650")
        etEditPriceInputY.setText("600")
        etEditHardPriceCap.setText("100000000")
        etEditPriceIncrement.setText("1")
        etEditCreateButtonX.setText("900")
        etEditCreateButtonY.setText("600")
        etEditConfirmButtonX.setText("540")
        etEditConfirmButtonY.setText("1200")
        
        // Common defaults
        etOcrRegionLeft.setText("400")
        etOcrRegionTop.setText("500")
        etOcrRegionRight.setText("700")
        etOcrRegionBottom.setText("700")
        etLoopDelay.setText("500")
        etGestureDuration.setText("200")
        
        Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
    }
}

// Data classes for config
data class AutomationConfig(
    val createMode: CreateModeConfig,
    val editMode: EditModeConfig,
    val ocrRegionLeft: Int,
    val ocrRegionTop: Int,
    val ocrRegionRight: Int,
    val ocrRegionBottom: Int,
    val loopDelayMs: Long,
    val gestureDurationMs: Long
)

data class CreateModeConfig(
    val rowStartX: Int,
    val rowStartY: Int,
    val rowEndX: Int,
    val rowEndY: Int,
    val rowHeight: Int,
    val plusButtonX: Int,
    val plusButtonY: Int,
    val hardPriceCap: Long,
    val maxRows: Int,
    val swipeX: Int,
    val swipeY: Int,
    val swipeDistance: Int
)

data class EditModeConfig(
    val row1X: Int,
    val row1Y: Int,
    val priceInputX: Int,
    val priceInputY: Int,
    val hardPriceCap: Long,
    val priceIncrement: Long,
    val createButtonX: Int,
    val createButtonY: Int,
    val confirmButtonX: Int,
    val confirmButtonY: Int
)
