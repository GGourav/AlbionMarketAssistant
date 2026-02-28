// FILE: app/src/main/java/com/albion/marketassistant/ui/settings/CalibrationActivity.kt
// COMPLETE REWRITE - Save directly to Room Database

package com.albion.marketassistant.ui.settings

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.albion.marketassistant.R
import com.albion.marketassistant.data.*
import com.albion.marketassistant.db.CalibrationDatabase
import kotlinx.coroutines.launch

class CalibrationActivity : AppCompatActivity() {

    private lateinit var database: CalibrationDatabase
    
    // CREATE Mode inputs
    private lateinit var etCreateRowStartX: EditText
    private lateinit var etCreateRowStartY: EditText
    private lateinit var etCreateRowHeight: EditText
    private lateinit var etCreatePlusButtonX: EditText
    private lateinit var etCreatePlusButtonY: EditText
    private lateinit var etCreateButtonX: EditText
    private lateinit var etCreateButtonY: EditText
    private lateinit var etCreateConfirmYesX: EditText
    private lateinit var etCreateConfirmYesY: EditText
    private lateinit var etCreateCloseButtonX: EditText
    private lateinit var etCreateCloseButtonY: EditText
    private lateinit var etCreateHardPriceCap: EditText
    private lateinit var etCreateMaxRows: EditText
    private lateinit var etCreateOcrLeft: EditText
    private lateinit var etCreateOcrTop: EditText
    private lateinit var etCreateOcrRight: EditText
    private lateinit var etCreateOcrBottom: EditText
    private lateinit var etCreateSwipeX: EditText
    private lateinit var etCreateSwipeY: EditText
    private lateinit var etCreateSwipeDistance: EditText
    
    // EDIT Mode inputs
    private lateinit var etEditButtonX: EditText
    private lateinit var etEditButtonY: EditText
    private lateinit var etEditPriceInputX: EditText
    private lateinit var etEditPriceInputY: EditText
    private lateinit var etEditUpdateButtonX: EditText
    private lateinit var etEditUpdateButtonY: EditText
    private lateinit var etEditConfirmYesX: EditText
    private lateinit var etEditConfirmYesY: EditText
    private lateinit var etEditCloseButtonX: EditText
    private lateinit var etEditCloseButtonY: EditText
    private lateinit var etEditHardPriceCap: EditText
    private lateinit var etEditPriceIncrement: EditText
    private lateinit var etEditOcrLeft: EditText
    private lateinit var etEditOcrTop: EditText
    private lateinit var etEditOcrRight: EditText
    private lateinit var etEditOcrBottom: EditText
    
    // Global timing settings
    private lateinit var etLoopDelay: EditText
    private lateinit var etGestureDuration: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)
        
        database = CalibrationDatabase.getInstance(applicationContext)
        
        initViews()
        loadConfig()
        setupButtons()
    }

    private fun initViews() {
        // CREATE Mode
        etCreateRowStartX = findViewById(R.id.et_create_row_start_x)
        etCreateRowStartY = findViewById(R.id.et_create_row_start_y)
        etCreateRowHeight = findViewById(R.id.et_create_row_height)
        etCreatePlusButtonX = findViewById(R.id.et_create_plus_button_x)
        etCreatePlusButtonY = findViewById(R.id.et_create_plus_button_y)
        etCreateButtonX = findViewById(R.id.et_create_button_x)
        etCreateButtonY = findViewById(R.id.et_create_button_y)
        etCreateConfirmYesX = findViewById(R.id.et_create_confirm_yes_x)
        etCreateConfirmYesY = findViewById(R.id.et_create_confirm_yes_y)
        etCreateCloseButtonX = findViewById(R.id.et_create_close_button_x)
        etCreateCloseButtonY = findViewById(R.id.et_create_close_button_y)
        etCreateHardPriceCap = findViewById(R.id.et_create_hard_price_cap)
        etCreateMaxRows = findViewById(R.id.et_create_max_rows)
        etCreateOcrLeft = findViewById(R.id.et_create_ocr_left)
        etCreateOcrTop = findViewById(R.id.et_create_ocr_top)
        etCreateOcrRight = findViewById(R.id.et_create_ocr_right)
        etCreateOcrBottom = findViewById(R.id.et_create_ocr_bottom)
        etCreateSwipeX = findViewById(R.id.et_create_swipe_x)
        etCreateSwipeY = findViewById(R.id.et_create_swipe_y)
        etCreateSwipeDistance = findViewById(R.id.et_create_swipe_distance)
        
        // EDIT Mode
        etEditButtonX = findViewById(R.id.et_edit_button_x)
        etEditButtonY = findViewById(R.id.et_edit_button_y)
        etEditPriceInputX = findViewById(R.id.et_edit_price_input_x)
        etEditPriceInputY = findViewById(R.id.et_edit_price_input_y)
        etEditUpdateButtonX = findViewById(R.id.et_edit_update_button_x)
        etEditUpdateButtonY = findViewById(R.id.et_edit_update_button_y)
        etEditConfirmYesX = findViewById(R.id.et_edit_confirm_yes_x)
        etEditConfirmYesY = findViewById(R.id.et_edit_confirm_yes_y)
        etEditCloseButtonX = findViewById(R.id.et_edit_close_button_x)
        etEditCloseButtonY = findViewById(R.id.et_edit_close_button_y)
        etEditHardPriceCap = findViewById(R.id.et_edit_hard_price_cap)
        etEditPriceIncrement = findViewById(R.id.et_edit_price_increment)
        etEditOcrLeft = findViewById(R.id.et_edit_ocr_left)
        etEditOcrTop = findViewById(R.id.et_edit_ocr_top)
        etEditOcrRight = findViewById(R.id.et_edit_ocr_right)
        etEditOcrBottom = findViewById(R.id.et_edit_ocr_bottom)
        
        // Global
        etLoopDelay = findViewById(R.id.et_loop_delay)
        etGestureDuration = findViewById(R.id.et_gesture_duration)
    }

    private fun loadConfig() {
        lifecycleScope.launch {
            val calibration = database.calibrationDao().getCalibration() ?: CalibrationData()
            
            // CREATE Mode
            etCreateRowStartX.setText(calibration.createMode.firstRowX.toString())
            etCreateRowStartY.setText(calibration.createMode.firstRowY.toString())
            etCreateRowHeight.setText(calibration.createMode.rowYOffset.toString())
            etCreatePlusButtonX.setText(calibration.createMode.plusButtonX.toString())
            etCreatePlusButtonY.setText(calibration.createMode.plusButtonY.toString())
            etCreateButtonX.setText(calibration.createMode.createButtonX.toString())
            etCreateButtonY.setText(calibration.createMode.createButtonY.toString())
            etCreateConfirmYesX.setText(calibration.createMode.confirmYesX.toString())
            etCreateConfirmYesY.setText(calibration.createMode.confirmYesY.toString())
            etCreateCloseButtonX.setText(calibration.createMode.closeButtonX.toString())
            etCreateCloseButtonY.setText(calibration.createMode.closeButtonY.toString())
            etCreateHardPriceCap.setText(calibration.createMode.hardPriceCap.toString())
            etCreateMaxRows.setText(calibration.createMode.maxRowsPerScreen.toString())
            etCreateOcrLeft.setText(calibration.createMode.ocrRegionLeft.toString())
            etCreateOcrTop.setText(calibration.createMode.ocrRegionTop.toString())
            etCreateOcrRight.setText(calibration.createMode.ocrRegionRight.toString())
            etCreateOcrBottom.setText(calibration.createMode.ocrRegionBottom.toString())
            etCreateSwipeX.setText(calibration.global.swipeStartX.toString())
            etCreateSwipeY.setText(calibration.global.swipeStartY.toString())
            etCreateSwipeDistance.setText((calibration.global.swipeStartY - calibration.global.swipeEndY).toString())
            
            // EDIT Mode
            etEditButtonX.setText(calibration.editMode.editButtonX.toString())
            etEditButtonY.setText(calibration.editMode.editButtonY.toString())
            etEditPriceInputX.setText(calibration.editMode.priceInputX.toString())
            etEditPriceInputY.setText(calibration.editMode.priceInputY.toString())
            etEditUpdateButtonX.setText(calibration.editMode.updateButtonX.toString())
            etEditUpdateButtonY.setText(calibration.editMode.updateButtonY.toString())
            etEditConfirmYesX.setText(calibration.editMode.confirmYesX.toString())
            etEditConfirmYesY.setText(calibration.editMode.confirmYesY.toString())
            etEditCloseButtonX.setText(calibration.editMode.closeButtonX.toString())
            etEditCloseButtonY.setText(calibration.editMode.closeButtonY.toString())
            etEditHardPriceCap.setText(calibration.editMode.hardPriceCap.toString())
            etEditPriceIncrement.setText(calibration.editMode.priceIncrement.toString())
            etEditOcrLeft.setText(calibration.editMode.ocrRegionLeft.toString())
            etEditOcrTop.setText(calibration.editMode.ocrRegionTop.toString())
            etEditOcrRight.setText(calibration.editMode.ocrRegionRight.toString())
            etEditOcrBottom.setText(calibration.editMode.ocrRegionBottom.toString())
            
            // Global
            etLoopDelay.setText(calibration.global.cycleCooldownMs.toString())
            etGestureDuration.setText(calibration.global.tapDurationMs.toString())
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
        lifecycleScope.launch {
            try {
                val swipeX = etCreateSwipeX.text.toString().toIntOrNull() ?: 540
                val swipeY = etCreateSwipeY.text.toString().toIntOrNull() ?: 1500
                val swipeDistance = etCreateSwipeDistance.text.toString().toIntOrNull() ?: 300
                
                val calibration = CalibrationData(
                    createMode = CreateModeConfig(
                        firstRowX = etCreateRowStartX.text.toString().toIntOrNull() ?: 100,
                        firstRowY = etCreateRowStartY.text.toString().toIntOrNull() ?: 300,
                        rowYOffset = etCreateRowHeight.text.toString().toIntOrNull() ?: 80,
                        plusButtonX = etCreatePlusButtonX.text.toString().toIntOrNull() ?: 350,
                        plusButtonY = etCreatePlusButtonY.text.toString().toIntOrNull() ?: 450,
                        createButtonX = etCreateButtonX.text.toString().toIntOrNull() ?: 500,
                        createButtonY = etCreateButtonY.text.toString().toIntOrNull() ?: 550,
                        confirmYesX = etCreateConfirmYesX.text.toString().toIntOrNull() ?: 500,
                        confirmYesY = etCreateConfirmYesY.text.toString().toIntOrNull() ?: 600,
                        closeButtonX = etCreateCloseButtonX.text.toString().toIntOrNull() ?: 1000,
                        closeButtonY = etCreateCloseButtonY.text.toString().toIntOrNull() ?: 200,
                        hardPriceCap = etCreateHardPriceCap.text.toString().toIntOrNull() ?: 100000,
                        maxRowsPerScreen = etCreateMaxRows.text.toString().toIntOrNull() ?: 5,
                        ocrRegionLeft = etCreateOcrLeft.text.toString().toIntOrNull() ?: 600,
                        ocrRegionTop = etCreateOcrTop.text.toString().toIntOrNull() ?: 200,
                        ocrRegionRight = etCreateOcrRight.text.toString().toIntOrNull() ?: 1050,
                        ocrRegionBottom = etCreateOcrBottom.text.toString().toIntOrNull() ?: 500
                    ),
                    editMode = EditModeConfig(
                        editButtonX = etEditButtonX.text.toString().toIntOrNull() ?: 950,
                        editButtonY = etEditButtonY.text.toString().toIntOrNull() ?: 300,
                        priceInputX = etEditPriceInputX.text.toString().toIntOrNull() ?: 300,
                        priceInputY = etEditPriceInputY.text.toString().toIntOrNull() ?: 400,
                        updateButtonX = etEditUpdateButtonX.text.toString().toIntOrNull() ?: 500,
                        updateButtonY = etEditUpdateButtonY.text.toString().toIntOrNull() ?: 550,
                        confirmYesX = etEditConfirmYesX.text.toString().toIntOrNull() ?: 500,
                        confirmYesY = etEditConfirmYesY.text.toString().toIntOrNull() ?: 600,
                        closeButtonX = etEditCloseButtonX.text.toString().toIntOrNull() ?: 1000,
                        closeButtonY = etEditCloseButtonY.text.toString().toIntOrNull() ?: 200,
                        hardPriceCap = etEditHardPriceCap.text.toString().toIntOrNull() ?: 100000,
                        priceIncrement = etEditPriceIncrement.text.toString().toIntOrNull() ?: 1,
                        ocrRegionLeft = etEditOcrLeft.text.toString().toIntOrNull() ?: 600,
                        ocrRegionTop = etEditOcrTop.text.toString().toIntOrNull() ?: 200,
                        ocrRegionRight = etEditOcrRight.text.toString().toIntOrNull() ?: 1050,
                        ocrRegionBottom = etEditOcrBottom.text.toString().toIntOrNull() ?: 500
                    ),
                    global = GlobalSettings(
                        swipeStartX = swipeX,
                        swipeStartY = swipeY,
                        swipeEndX = swipeX,
                        swipeEndY = swipeY - swipeDistance,
                        cycleCooldownMs = etLoopDelay.text.toString().toLongOrNull() ?: 200,
                        tapDurationMs = etGestureDuration.text.toString().toLongOrNull() ?: 200
                    )
                )
                
                database.calibrationDao().insertCalibration(calibration)
                
                Toast.makeText(this@CalibrationActivity, "✓ Configuration saved to database!", Toast.LENGTH_SHORT).show()
                finish()
                
            } catch (e: Exception) {
                Toast.makeText(this@CalibrationActivity, "Error saving config: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetToDefaults() {
        // CREATE Mode defaults
        etCreateRowStartX.setText("100")
        etCreateRowStartY.setText("300")
        etCreateRowHeight.setText("80")
        etCreatePlusButtonX.setText("350")
        etCreatePlusButtonY.setText("450")
        etCreateButtonX.setText("500")
        etCreateButtonY.setText("550")
        etCreateConfirmYesX.setText("500")
        etCreateConfirmYesY.setText("600")
        etCreateCloseButtonX.setText("1000")
        etCreateCloseButtonY.setText("200")
        etCreateHardPriceCap.setText("100000")
        etCreateMaxRows.setText("5")
        etCreateOcrLeft.setText("600")
        etCreateOcrTop.setText("200")
        etCreateOcrRight.setText("1050")
        etCreateOcrBottom.setText("500")
        etCreateSwipeX.setText("540")
        etCreateSwipeY.setText("1500")
        etCreateSwipeDistance.setText("300")
        
        // EDIT Mode defaults
        etEditButtonX.setText("950")
        etEditButtonY.setText("300")
        etEditPriceInputX.setText("300")
        etEditPriceInputY.setText("400")
        etEditUpdateButtonX.setText("500")
        etEditUpdateButtonY.setText("550")
        etEditConfirmYesX.setText("500")
        etEditConfirmYesY.setText("600")
        etEditCloseButtonX.setText("1000")
        etEditCloseButtonY.setText("200")
        etEditHardPriceCap.setText("100000")
        etEditPriceIncrement.setText("1")
        etEditOcrLeft.setText("600")
        etEditOcrTop.setText("200")
        etEditOcrRight.setText("1050")
        etEditOcrBottom.setText("500")
        
        // Global defaults
        etLoopDelay.setText("200")
        etGestureDuration.setText("200")
        
        Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
    }
}
