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
    private lateinit var etBuyOrderBtnX: EditText
    private lateinit var etBuyOrderBtnY: EditText
    private lateinit var etRowOffsetY: EditText
    private lateinit var etMaxRows: EditText
    private lateinit var etPlusBtnX: EditText
    private lateinit var etPlusBtnY: EditText
    private lateinit var etConfirmBtnX: EditText
    private lateinit var etConfirmBtnY: EditText
    private lateinit var etScrollStartY: EditText
    private lateinit var etScrollEndY: EditText
    private lateinit var etMaxItemsCreate: EditText
    private lateinit var etHardPriceCapCreate: EditText
    
    // EDIT Mode inputs
    private lateinit var etMyOrdersTabX: EditText
    private lateinit var etMyOrdersTabY: EditText
    private lateinit var etEditBtnX: EditText
    private lateinit var etEditBtnY: EditText
    private lateinit var etPriceFieldX: EditText
    private lateinit var etPriceFieldY: EditText
    private lateinit var etUpdateBtnX: EditText
    private lateinit var etUpdateBtnY: EditText
    private lateinit var etCloseBtnX: EditText
    private lateinit var etCloseBtnY: EditText
    private lateinit var etMaxOrdersEdit: EditText
    private lateinit var etHardPriceCapEdit: EditText
    private lateinit var etPriceIncrement: EditText
    
    // Timing inputs
    private lateinit var etDelayAfterTap: EditText
    private lateinit var etDelayAfterSwipe: EditText
    private lateinit var etDelayAfterConfirm: EditText
    private lateinit var etCycleCooldown: EditText

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
        etBuyOrderBtnX = findViewById(R.id.et_buy_order_btn_x)
        etBuyOrderBtnY = findViewById(R.id.et_buy_order_btn_y)
        etRowOffsetY = findViewById(R.id.et_row_offset_y)
        etMaxRows = findViewById(R.id.et_max_rows)
        etPlusBtnX = findViewById(R.id.et_plus_btn_x)
        etPlusBtnY = findViewById(R.id.et_plus_btn_y)
        etConfirmBtnX = findViewById(R.id.et_confirm_btn_x)
        etConfirmBtnY = findViewById(R.id.et_confirm_btn_y)
        etScrollStartY = findViewById(R.id.et_scroll_start_y)
        etScrollEndY = findViewById(R.id.et_scroll_end_y)
        etMaxItemsCreate = findViewById(R.id.et_max_items_create)
        etHardPriceCapCreate = findViewById(R.id.et_hard_price_cap_create)
        
        // EDIT Mode
        etMyOrdersTabX = findViewById(R.id.et_my_orders_tab_x)
        etMyOrdersTabY = findViewById(R.id.et_my_orders_tab_y)
        etEditBtnX = findViewById(R.id.et_edit_btn_x)
        etEditBtnY = findViewById(R.id.et_edit_btn_y)
        etPriceFieldX = findViewById(R.id.et_price_field_x)
        etPriceFieldY = findViewById(R.id.et_price_field_y)
        etUpdateBtnX = findViewById(R.id.et_update_btn_x)
        etUpdateBtnY = findViewById(R.id.et_update_btn_y)
        etCloseBtnX = findViewById(R.id.et_close_btn_x)
        etCloseBtnY = findViewById(R.id.et_close_btn_y)
        etMaxOrdersEdit = findViewById(R.id.et_max_orders_edit)
        etHardPriceCapEdit = findViewById(R.id.et_hard_price_cap_edit)
        etPriceIncrement = findViewById(R.id.et_price_increment)
        
        // Timing
        etDelayAfterTap = findViewById(R.id.et_delay_after_tap)
        etDelayAfterSwipe = findViewById(R.id.et_delay_after_swipe)
        etDelayAfterConfirm = findViewById(R.id.et_delay_after_confirm)
        etCycleCooldown = findViewById(R.id.et_cycle_cooldown)
    }

    private fun loadConfig() {
        lifecycleScope.launch {
            val calibration = database.calibrationDao().getCalibration() ?: CalibrationData()
            
            // CREATE Mode
            etBuyOrderBtnX.setText("%.2f".format(calibration.createMode.buyOrderButtonXPercent))
            etBuyOrderBtnY.setText("%.2f".format(calibration.createMode.firstRowYPercent))
            etRowOffsetY.setText("%.2f".format(calibration.createMode.rowYOffsetPercent))
            etMaxRows.setText(calibration.createMode.maxRowsPerScreen.toString())
            etPlusBtnX.setText("%.2f".format(calibration.createMode.plusButtonXPercent))
            etPlusBtnY.setText("%.2f".format(calibration.createMode.plusButtonYPercent))
            etConfirmBtnX.setText("%.2f".format(calibration.createMode.confirmButtonXPercent))
            etConfirmBtnY.setText("%.2f".format(calibration.createMode.confirmButtonYPercent))
            etScrollStartY.setText("%.2f".format(calibration.createMode.scrollStartYPercent))
            etScrollEndY.setText("%.2f".format(calibration.createMode.scrollEndYPercent))
            etMaxItemsCreate.setText(calibration.createMode.maxItemsToProcess.toString())
            etHardPriceCapCreate.setText(calibration.createMode.hardPriceCap.toString())
            
            // EDIT Mode
            etMyOrdersTabX.setText("%.2f".format(calibration.editMode.myOrdersTabXPercent))
            etMyOrdersTabY.setText("%.2f".format(calibration.editMode.myOrdersTabYPercent))
            etEditBtnX.setText("%.2f".format(calibration.editMode.editButtonXPercent))
            etEditBtnY.setText("%.2f".format(calibration.editMode.editButtonYPercent))
            etPriceFieldX.setText("%.2f".format(calibration.editMode.priceFieldXPercent))
            etPriceFieldY.setText("%.2f".format(calibration.editMode.priceFieldYPercent))
            etUpdateBtnX.setText("%.2f".format(calibration.editMode.updateButtonXPercent))
            etUpdateBtnY.setText("%.2f".format(calibration.editMode.updateButtonYPercent))
            etCloseBtnX.setText("%.2f".format(calibration.editMode.closeButtonXPercent))
            etCloseBtnY.setText("%.2f".format(calibration.editMode.closeButtonYPercent))
            etMaxOrdersEdit.setText(calibration.editMode.maxOrdersToEdit.toString())
            etHardPriceCapEdit.setText(calibration.editMode.hardPriceCap.toString())
            etPriceIncrement.setText(calibration.editMode.priceIncrement.toString())
            
            // Timing
            etDelayAfterTap.setText(calibration.global.delayAfterTapMs.toString())
            etDelayAfterSwipe.setText(calibration.global.delayAfterSwipeMs.toString())
            etDelayAfterConfirm.setText(calibration.global.delayAfterConfirmMs.toString())
            etCycleCooldown.setText(calibration.global.cycleCooldownMs.toString())
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
                val calibration = CalibrationData(
                    createMode = CreateModeConfig(
                        buyOrderButtonXPercent = etBuyOrderBtnX.text.toString().toDoubleOrNull() ?: 0.82,
                        firstRowYPercent = etBuyOrderBtnY.text.toString().toDoubleOrNull() ?: 0.35,
                        rowYOffsetPercent = etRowOffsetY.text.toString().toDoubleOrNull() ?: 0.10,
                        maxRowsPerScreen = etMaxRows.text.toString().toIntOrNull() ?: 6,
                        plusButtonXPercent = etPlusBtnX.text.toString().toDoubleOrNull() ?: 0.78,
                        plusButtonYPercent = etPlusBtnY.text.toString().toDoubleOrNull() ?: 0.62,
                        confirmButtonXPercent = etConfirmBtnX.text.toString().toDoubleOrNull() ?: 0.75,
                        confirmButtonYPercent = etConfirmBtnY.text.toString().toDoubleOrNull() ?: 0.88,
                        scrollStartYPercent = etScrollStartY.text.toString().toDoubleOrNull() ?: 0.75,
                        scrollEndYPercent = etScrollEndY.text.toString().toDoubleOrNull() ?: 0.35,
                        maxItemsToProcess = etMaxItemsCreate.text.toString().toIntOrNull() ?: 50,
                        hardPriceCap = etHardPriceCapCreate.text.toString().toIntOrNull() ?: 100000
                    ),
                    editMode = EditModeConfig(
                        myOrdersTabXPercent = etMyOrdersTabX.text.toString().toDoubleOrNull() ?: 0.90,
                        myOrdersTabYPercent = etMyOrdersTabY.text.toString().toDoubleOrNull() ?: 0.13,
                        editButtonXPercent = etEditBtnX.text.toString().toDoubleOrNull() ?: 0.85,
                        editButtonYPercent = etEditBtnY.text.toString().toDoubleOrNull() ?: 0.55,
                        priceFieldXPercent = etPriceFieldX.text.toString().toDoubleOrNull() ?: 0.50,
                        priceFieldYPercent = etPriceFieldY.text.toString().toDoubleOrNull() ?: 0.62,
                        updateButtonXPercent = etUpdateBtnX.text.toString().toDoubleOrNull() ?: 0.75,
                        updateButtonYPercent = etUpdateBtnY.text.toString().toDoubleOrNull() ?: 0.88,
                        closeButtonXPercent = etCloseBtnX.text.toString().toDoubleOrNull() ?: 0.95,
                        closeButtonYPercent = etCloseBtnY.text.toString().toDoubleOrNull() ?: 0.10,
                        maxOrdersToEdit = etMaxOrdersEdit.text.toString().toIntOrNull() ?: 20,
                        hardPriceCap = etHardPriceCapEdit.text.toString().toIntOrNull() ?: 100000,
                        priceIncrement = etPriceIncrement.text.toString().toIntOrNull() ?: 1
                    ),
                    global = GlobalSettings(
                        delayAfterTapMs = etDelayAfterTap.text.toString().toLongOrNull() ?: 900,
                        delayAfterSwipeMs = etDelayAfterSwipe.text.toString().toLongOrNull() ?: 1200,
                        delayAfterConfirmMs = etDelayAfterConfirm.text.toString().toLongOrNull() ?: 1100,
                        cycleCooldownMs = etCycleCooldown.text.toString().toLongOrNull() ?: 500
                    )
                )
                
                database.calibrationDao().insertCalibration(calibration)
                
                Toast.makeText(this@CalibrationActivity, "✓ Configuration saved!", Toast.LENGTH_SHORT).show()
                finish()
                
            } catch (e: Exception) {
                Toast.makeText(this@CalibrationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetToDefaults() {
        etBuyOrderBtnX.setText("0.82")
        etBuyOrderBtnY.setText("0.35")
        etRowOffsetY.setText("0.10")
        etMaxRows.setText("6")
        etPlusBtnX.setText("0.78")
        etPlusBtnY.setText("0.62")
        etConfirmBtnX.setText("0.75")
        etConfirmBtnY.setText("0.88")
        etScrollStartY.setText("0.75")
        etScrollEndY.setText("0.35")
        etMaxItemsCreate.setText("50")
        etHardPriceCapCreate.setText("100000")
        
        etMyOrdersTabX.setText("0.90")
        etMyOrdersTabY.setText("0.13")
        etEditBtnX.setText("0.85")
        etEditBtnY.setText("0.55")
        etPriceFieldX.setText("0.50")
        etPriceFieldY.setText("0.62")
        etUpdateBtnX.setText("0.75")
        etUpdateBtnY.setText("0.88")
        etCloseBtnX.setText("0.95")
        etCloseBtnY.setText("0.10")
        etMaxOrdersEdit.setText("20")
        etHardPriceCapEdit.setText("100000")
        etPriceIncrement.setText("1")
        
        etDelayAfterTap.setText("900")
        etDelayAfterSwipe.setText("1200")
        etDelayAfterConfirm.setText("1100")
        etCycleCooldown.setText("500")
        
        Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
    }
}
