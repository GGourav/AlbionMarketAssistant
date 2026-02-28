package com.albion.marketassistant.ui.settings

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.albion.marketassistant.R
import com.albion.marketassistant.data.CalibrationData
import com.albion.marketassistant.database.AppDatabase
import com.albion.marketassistant.database.CalibrationDao
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class CalibrationActivity : AppCompatActivity() {

    private lateinit var calibrationDao: CalibrationDao
    
    private lateinit var tabLayout: TabLayout
    private lateinit var createModeLayout: LinearLayout
    private lateinit var editModeLayout: LinearLayout
    private lateinit var commonLayout: LinearLayout
    
    private lateinit var etFirstRowX: EditText
    private lateinit var etFirstRowY: EditText
    private lateinit var etRowYOffset: EditText
    private lateinit var etPlusButtonX: EditText
    private lateinit var etPlusButtonY: EditText
    private lateinit var etCreateButtonX: EditText
    private lateinit var etCreateButtonY: EditText
    private lateinit var etConfirmYesX: EditText
    private lateinit var etConfirmYesY: EditText
    private lateinit var etCloseButtonX: EditText
    private lateinit var etCloseButtonY: EditText
    
    private lateinit var etOcrLeft: EditText
    private lateinit var etOcrTop: EditText
    private lateinit var etOcrRight: EditText
    private lateinit var etOcrBottom: EditText
    
    private lateinit var etSwipeX: EditText
    private lateinit var etSwipeY: EditText
    private lateinit var etSwipeDistance: EditText
    
    private lateinit var etEditButtonX: EditText
    private lateinit var etEditButtonY: EditText
    private lateinit var etPriceInputX: EditText
    private lateinit var etPriceInputY: EditText
    private lateinit var etUpdateButtonX: EditText
    private lateinit var etUpdateButtonY: EditText
    private lateinit var etEditConfirmYesX: EditText
    private lateinit var etEditConfirmYesY: EditText
    private lateinit var etEditCloseButtonX: EditText
    private lateinit var etEditCloseButtonY: EditText
    
    private lateinit var etEditOcrLeft: EditText
    private lateinit var etEditOcrTop: EditText
    private lateinit var etEditOcrRight: EditText
    private lateinit var etEditOcrBottom: EditText
    
    private lateinit var etHardPriceCap: EditText
    private lateinit var etMaxRows: EditText
    private lateinit var etPriceIncrement: EditText
    private lateinit var etLoopDelay: EditText
    private lateinit var etGestureDuration: EditText
    
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var btnBack: Button
    
    private var currentData: CalibrationData = CalibrationData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)
        
        calibrationDao = AppDatabase.getInstance(this).calibrationDao()
        
        initViews()
        setupTabs()
        setupClickListeners()
        loadCalibrationData()
    }
    
    private fun initViews() {
        tabLayout = findViewById(R.id.tab_layout)
        createModeLayout = findViewById(R.id.layout_create_mode)
        editModeLayout = findViewById(R.id.layout_edit_mode)
        commonLayout = findViewById(R.id.layout_common)
        
        etFirstRowX = findViewById(R.id.et_first_row_x)
        etFirstRowY = findViewById(R.id.et_first_row_y)
        etRowYOffset = findViewById(R.id.et_row_y_offset)
        etPlusButtonX = findViewById(R.id.et_plus_button_x)
        etPlusButtonY = findViewById(R.id.et_plus_button_y)
        etCreateButtonX = findViewById(R.id.et_create_button_x)
        etCreateButtonY = findViewById(R.id.et_create_button_y)
        etConfirmYesX = findViewById(R.id.et_confirm_yes_x)
        etConfirmYesY = findViewById(R.id.et_confirm_yes_y)
        etCloseButtonX = findViewById(R.id.et_close_button_x)
        etCloseButtonY = findViewById(R.id.et_close_button_y)
        
        etOcrLeft = findViewById(R.id.et_ocr_left)
        etOcrTop = findViewById(R.id.et_ocr_top)
        etOcrRight = findViewById(R.id.et_ocr_right)
        etOcrBottom = findViewById(R.id.et_ocr_bottom)
        
        etSwipeX = findViewById(R.id.et_swipe_x)
        etSwipeY = findViewById(R.id.et_swipe_y)
        etSwipeDistance = findViewById(R.id.et_swipe_distance)
        
        etEditButtonX = findViewById(R.id.et_edit_button_x)
        etEditButtonY = findViewById(R.id.et_edit_button_y)
        etPriceInputX = findViewById(R.id.et_price_input_x)
        etPriceInputY = findViewById(R.id.et_price_input_y)
        etUpdateButtonX = findViewById(R.id.et_update_button_x)
        etUpdateButtonY = findViewById(R.id.et_update_button_y)
        etEditConfirmYesX = findViewById(R.id.et_edit_confirm_yes_x)
        etEditConfirmYesY = findViewById(R.id.et_edit_confirm_yes_y)
        etEditCloseButtonX = findViewById(R.id.et_edit_close_button_x)
        etEditCloseButtonY = findViewById(R.id.et_edit_close_button_y)
        
        etEditOcrLeft = findViewById(R.id.et_edit_ocr_left)
        etEditOcrTop = findViewById(R.id.et_edit_ocr_top)
        etEditOcrRight = findViewById(R.id.et_edit_ocr_right)
        etEditOcrBottom = findViewById(R.id.et_edit_ocr_bottom)
        
        etHardPriceCap = findViewById(R.id.et_hard_price_cap)
        etMaxRows = findViewById(R.id.et_max_rows)
        etPriceIncrement = findViewById(R.id.et_price_increment)
        etLoopDelay = findViewById(R.id.et_loop_delay)
        etGestureDuration = findViewById(R.id.et_gesture_duration)
        
        btnSave = findViewById(R.id.btn_save)
        btnReset = findViewById(R.id.btn_reset)
        btnBack = findViewById(R.id.btn_back)
    }
    
    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        createModeLayout.visibility = android.view.View.VISIBLE
                        editModeLayout.visibility = android.view.View.GONE
                    }
                    1 -> {
                        createModeLayout.visibility = android.view.View.GONE
                        editModeLayout.visibility = android.view.View.VISIBLE
                    }
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun setupClickListeners() {
        btnSave.setOnClickListener { saveCalibrationData() }
        btnReset.setOnClickListener { resetToDefaults() }
        btnBack.setOnClickListener { finish() }
    }
    
    private fun loadCalibrationData() {
        lifecycleScope.launch {
            try {
                val data = calibrationDao.getLatest()
                if (data != null) {
                    currentData = data
                    populateFields(data)
                } else {
                    populateFields(CalibrationData())
                }
            } catch (e: Exception) {
                Toast.makeText(this@CalibrationActivity, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                populateFields(CalibrationData())
            }
        }
    }
    
    private fun populateFields(data: CalibrationData) {
        etFirstRowX.setText(String.format("%.3f", data.firstRowX))
        etFirstRowY.setText(String.format("%.3f", data.firstRowY))
        etRowYOffset.setText(String.format("%.3f", data.rowYOffset))
        etPlusButtonX.setText(String.format("%.3f", data.plusButtonX))
        etPlusButtonY.setText(String.format("%.3f", data.plusButtonY))
        etCreateButtonX.setText(String.format("%.3f", data.createButtonX))
        etCreateButtonY.setText(String.format("%.3f", data.createButtonY))
        etConfirmYesX.setText(String.format("%.3f", data.confirmYesX))
        etConfirmYesY.setText(String.format("%.3f", data.confirmYesY))
        etCloseButtonX.setText(String.format("%.3f", data.closeButtonX))
        etCloseButtonY.setText(String.format("%.3f", data.closeButtonY))
        
        etOcrLeft.setText(String.format("%.3f", data.ocrRegionLeft))
        etOcrTop.setText(String.format("%.3f", data.ocrRegionTop))
        etOcrRight.setText(String.format("%.3f", data.ocrRegionRight))
        etOcrBottom.setText(String.format("%.3f", data.ocrRegionBottom))
        
        etSwipeX.setText(String.format("%.3f", data.swipeStartX))
        etSwipeY.setText(String.format("%.3f", data.swipeStartY))
        etSwipeDistance.setText(String.format("%.3f", data.swipeStartY - data.swipeEndY))
        
        etEditButtonX.setText(String.format("%.3f", data.editButtonX))
        etEditButtonY.setText(String.format("%.3f", data.editButtonY))
        etPriceInputX.setText(String.format("%.3f", data.priceInputX))
        etPriceInputY.setText(String.format("%.3f", data.priceInputY))
        etUpdateButtonX.setText(String.format("%.3f", data.updateButtonX))
        etUpdateButtonY.setText(String.format("%.3f", data.updateButtonY))
        etEditConfirmYesX.setText(String.format("%.3f", data.confirmYesX))
        etEditConfirmYesY.setText(String.format("%.3f", data.confirmYesY))
        etEditCloseButtonX.setText(String.format("%.3f", data.closeButtonX))
        etEditCloseButtonY.setText(String.format("%.3f", data.closeButtonY))
        
        etEditOcrLeft.setText(String.format("%.3f", data.editOcrRegionLeft))
        etEditOcrTop.setText(String.format("%.3f", data.editOcrRegionTop))
        etEditOcrRight.setText(String.format("%.3f", data.editOcrRegionRight))
        etEditOcrBottom.setText(String.format("%.3f", data.editOcrRegionBottom))
        
        etHardPriceCap.setText(data.hardPriceCap.toString())
        etMaxRows.setText(data.maxRows.toString())
        etPriceIncrement.setText(data.priceIncrement.toString())
        etLoopDelay.setText(data.loopDelayMs.toString())
        etGestureDuration.setText(data.gestureDurationMs.toString())
    }
    
    private fun saveCalibrationData() {
        try {
            val swipeDistance = etSwipeDistance.text.toString().toFloatOrNull() ?: 0.35f
            
            val data = CalibrationData(
                id = currentData.id,
                firstRowX = etFirstRowX.text.toString().toFloatOrNull() ?: 0.5f,
                firstRowY = etFirstRowY.text.toString().toFloatOrNull() ?: 0.25f,
                rowYOffset = etRowYOffset.text.toString().toFloatOrNull() ?: 0.08f,
                plusButtonX = etPlusButtonX.text.toString().toFloatOrNull() ?: 0.85f,
                plusButtonY = etPlusButtonY.text.toString().toFloatOrNull() ?: 0.25f,
                createButtonX = etCreateButtonX.text.toString().toFloatOrNull() ?: 0.5f,
                createButtonY = etCreateButtonY.text.toString().toFloatOrNull() ?: 0.9f,
                confirmYesX = etConfirmYesX.text.toString().toFloatOrNull() ?: 0.35f,
                confirmYesY = etConfirmYesY.text.toString().toFloatOrNull() ?: 0.55f,
                closeButtonX = etCloseButtonX.text.toString().toFloatOrNull() ?: 0.85f,
                closeButtonY = etCloseButtonY.text.toString().toFloatOrNull() ?: 0.15f,
                ocrRegionLeft = etOcrLeft.text.toString().toFloatOrNull() ?: 0.55f,
                ocrRegionTop = etOcrTop.text.toString().toFloatOrNull() ?: 0.22f,
                ocrRegionRight = etOcrRight.text.toString().toFloatOrNull() ?: 0.75f,
                ocrRegionBottom = etOcrBottom.text.toString().toFloatOrNull() ?: 0.28f,
                swipeStartX = etSwipeX.text.toString().toFloatOrNull() ?: 0.5f,
                swipeStartY = etSwipeY.text.toString().toFloatOrNull() ?: 0.7f,
                swipeEndX = etSwipeX.text.toString().toFloatOrNull() ?: 0.5f,
                swipeEndY = (etSwipeY.text.toString().toFloatOrNull() ?: 0.7f) - swipeDistance,
                editButtonX = etEditButtonX.text.toString().toFloatOrNull() ?: 0.85f,
                editButtonY = etEditButtonY.text.toString().toFloatOrNull() ?: 0.25f,
                priceInputX = etPriceInputX.text.toString().toFloatOrNull() ?: 0.5f,
                priceInputY = etPriceInputY.text.toString().toFloatOrNull() ?: 0.45f,
                updateButtonX = etUpdateButtonX.text.toString().toFloatOrNull() ?: 0.75f,
                updateButtonY = etUpdateButtonY.text.toString().toFloatOrNull() ?: 0.55f,
                editOcrRegionLeft = etEditOcrLeft.text.toString().toFloatOrNull() ?: 0.55f,
                editOcrRegionTop = etEditOcrTop.text.toString().toFloatOrNull() ?: 0.22f,
                editOcrRegionRight = etEditOcrRight.text.toString().toFloatOrNull() ?: 0.75f,
                editOcrRegionBottom = etEditOcrBottom.text.toString().toFloatOrNull() ?: 0.28f,
                hardPriceCap = etHardPriceCap.text.toString().toIntOrNull() ?: 50000,
                maxRows = etMaxRows.text.toString().toIntOrNull() ?: 5,
                priceIncrement = etPriceIncrement.text.toString().toIntOrNull() ?: 100,
                loopDelayMs = etLoopDelay.text.toString().toLongOrNull() ?: 500,
                gestureDurationMs = etGestureDuration.text.toString().toLongOrNull() ?: 200,
                lastUpdated = System.currentTimeMillis()
            )
            
            lifecycleScope.launch {
                try {
                    calibrationDao.insert(data)
                    currentData = data
                    Toast.makeText(this@CalibrationActivity, "Calibration saved!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@CalibrationActivity, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid input: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun resetToDefaults() {
        populateFields(CalibrationData())
        Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
    }
}
