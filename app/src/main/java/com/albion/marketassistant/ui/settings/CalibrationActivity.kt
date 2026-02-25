package com.albion.marketassistant.ui.settings

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.albion.marketassistant.R
import com.albion.marketassistant.data.*
import com.albion.marketassistant.db.CalibrationDatabase
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalibrationActivity : AppCompatActivity() {

    private val db by lazy { CalibrationDatabase.getInstance(this) }
    private var currentCalibration: CalibrationData? = null
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        setupTabs()
        loadCurrentCalibration()

        findViewById<Button>(R.id.btnSaveCalibration).setOnClickListener {
            saveCalibration()
        }

        findViewById<Button>(R.id.btnResetCalibration).setOnClickListener {
            resetToDefaults()
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.addTab(tabLayout.newTab().setText("Create"))
        tabLayout.addTab(tabLayout.newTab().setText("Edit"))
        tabLayout.addTab(tabLayout.newTab().setText("Global"))
        tabLayout.addTab(tabLayout.newTab().setText("Safety"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                showTabContent(currentTab)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showTabContent(tab: Int) {
        findViewById<LinearLayout>(R.id.createModeLayout).visibility = 
            if (tab == 0) LinearLayout.VISIBLE else LinearLayout.GONE
        findViewById<LinearLayout>(R.id.editModeLayout).visibility = 
            if (tab == 1) LinearLayout.VISIBLE else LinearLayout.GONE
        findViewById<LinearLayout>(R.id.globalLayout).visibility = 
            if (tab == 2) LinearLayout.VISIBLE else LinearLayout.GONE
        findViewById<LinearLayout>(R.id.safetyLayout).visibility = 
            if (tab == 3) LinearLayout.VISIBLE else LinearLayout.GONE
    }

    private fun loadCurrentCalibration() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val calibration = db.calibrationDao().getCalibration() ?: CalibrationData()
                currentCalibration = calibration
                withContext(Dispatchers.Main) {
                    populateFields(calibration)
                    showTabContent(0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    populateFields(CalibrationData())
                    showToast("Error loading calibration: ${e.message}")
                }
            }
        }
    }

    private fun populateFields(data: CalibrationData) {
        // Create Mode fields
        findViewById<EditText>(R.id.createFirstRowX).setText(data.createMode.firstRowX.toString())
        findViewById<EditText>(R.id.createFirstRowY).setText(data.createMode.firstRowY.toString())
        findViewById<EditText>(R.id.createRowYOffset).setText(data.createMode.rowYOffset.toString())
        findViewById<EditText>(R.id.createMaxRows).setText(data.createMode.maxRowsPerScreen.toString())
        findViewById<EditText>(R.id.createPriceX).setText(data.createMode.priceInputX.toString())
        findViewById<EditText>(R.id.createPriceY).setText(data.createMode.priceInputY.toString())
        findViewById<EditText>(R.id.createButtonX).setText(data.createMode.createButtonX.toString())
        findViewById<EditText>(R.id.createButtonY).setText(data.createMode.createButtonY.toString())
        findViewById<EditText>(R.id.createConfirmYesX).setText(data.createMode.confirmYesX.toString())
        findViewById<EditText>(R.id.createConfirmYesY).setText(data.createMode.confirmYesY.toString())
        findViewById<EditText>(R.id.createCloseX).setText(data.createMode.closeButtonX.toString())
        findViewById<EditText>(R.id.createCloseY).setText(data.createMode.closeButtonY.toString())
        findViewById<EditText>(R.id.createOcrLeft).setText(data.createMode.ocrRegionLeft.toString())
        findViewById<EditText>(R.id.createOcrTop).setText(data.createMode.ocrRegionTop.toString())
        findViewById<EditText>(R.id.createOcrRight).setText(data.createMode.ocrRegionRight.toString())
        findViewById<EditText>(R.id.createOcrBottom).setText(data.createMode.ocrRegionBottom.toString())

        // Edit Mode fields
        findViewById<EditText>(R.id.editButtonX).setText(data.editMode.editButtonX.toString())
        findViewById<EditText>(R.id.editButtonY).setText(data.editMode.editButtonY.toString())
        findViewById<EditText>(R.id.editYOffset).setText(data.editMode.editButtonYOffset.toString())
        findViewById<EditText>(R.id.editPriceX).setText(data.editMode.priceInputX.toString())
        findViewById<EditText>(R.id.editPriceY).setText(data.editMode.priceInputY.toString())
        findViewById<EditText>(R.id.updateButtonX).setText(data.editMode.updateButtonX.toString())
        findViewById<EditText>(R.id.updateButtonY).setText(data.editMode.updateButtonY.toString())
        findViewById<EditText>(R.id.editConfirmYesX).setText(data.editMode.confirmYesX.toString())
        findViewById<EditText>(R.id.editConfirmYesY).setText(data.editMode.confirmYesY.toString())
        findViewById<EditText>(R.id.editCloseX).setText(data.editMode.closeButtonX.toString())
        findViewById<EditText>(R.id.editCloseY).setText(data.editMode.closeButtonY.toString())
        findViewById<EditText>(R.id.editOcrLeft).setText(data.editMode.ocrRegionLeft.toString())
        findViewById<EditText>(R.id.editOcrTop).setText(data.editMode.ocrRegionTop.toString())
        findViewById<EditText>(R.id.editOcrRight).setText(data.editMode.ocrRegionRight.toString())
        findViewById<EditText>(R.id.editOcrBottom).setText(data.editMode.ocrRegionBottom.toString())

        // Global fields
        findViewById<EditText>(R.id.networkLagMultiplier).setText(data.global.networkLagMultiplier.toString())
        findViewById<EditText>(R.id.swipeStartX).setText(data.global.swipeStartX.toString())
        findViewById<EditText>(R.id.swipeStartY).setText(data.global.swipeStartY.toString())
        findViewById<EditText>(R.id.swipeEndX).setText(data.global.swipeEndX.toString())
        findViewById<EditText>(R.id.swipeEndY).setText(data.global.swipeEndY.toString())
        findViewById<EditText>(R.id.swipeDuration).setText(data.global.swipeDurationMs.toString())
        findViewById<EditText>(R.id.tapDuration).setText(data.global.tapDurationMs.toString())
        findViewById<EditText>(R.id.textInputDelay).setText(data.global.textInputDelayMs.toString())
        findViewById<EditText>(R.id.popupOpenWait).setText(data.global.popupOpenWaitMs.toString())
        findViewById<EditText>(R.id.popupCloseWait).setText(data.global.popupCloseWaitMs.toString())
        findViewById<EditText>(R.id.confirmationWait).setText(data.global.confirmationWaitMs.toString())
        findViewById<EditText>(R.id.cycleCooldown).setText(data.global.cycleCooldownMs.toString())

        // Safety fields
        findViewById<CheckBox>(R.id.enableOcrSanityCheck).isChecked = data.safety.enableOcrSanityCheck
        findViewById<EditText>(R.id.maxPriceChangePercent).setText(data.safety.maxPriceChangePercent.toString())
        findViewById<EditText>(R.id.maxPriceCap).setText(data.safety.maxPriceCap.toString())
        findViewById<EditText>(R.id.minPriceCap).setText(data.safety.minPriceCap.toString())
        findViewById<CheckBox>(R.id.autoDismissErrors).isChecked = data.safety.autoDismissErrors
        findViewById<EditText>(R.id.maxRetries).setText(data.safety.maxRetries.toString())
        findViewById<EditText>(R.id.uiTimeout).setText(data.safety.uiTimeoutMs.toString())
    }

    private fun saveCalibration() {
        try {
            val createConfig = CreateModeConfig(
                firstRowX = getInt(R.id.createFirstRowX, 100),
                firstRowY = getInt(R.id.createFirstRowY, 300),
                rowYOffset = getInt(R.id.createRowYOffset, 80),
                maxRowsPerScreen = getInt(R.id.createMaxRows, 5),
                priceInputX = getInt(R.id.createPriceX, 300),
                priceInputY = getInt(R.id.createPriceY, 400),
                createButtonX = getInt(R.id.createButtonX, 500),
                createButtonY = getInt(R.id.createButtonY, 550),
                confirmYesX = getInt(R.id.createConfirmYesX, 500),
                confirmYesY = getInt(R.id.createConfirmYesY, 600),
                closeButtonX = getInt(R.id.createCloseX, 1000),
                closeButtonY = getInt(R.id.createCloseY, 200),
                ocrRegionLeft = getInt(R.id.createOcrLeft, 600),
                ocrRegionTop = getInt(R.id.createOcrTop, 200),
                ocrRegionRight = getInt(R.id.createOcrRight, 1050),
                ocrRegionBottom = getInt(R.id.createOcrBottom, 500)
            )

            val editConfig = EditModeConfig(
                editButtonX = getInt(R.id.editButtonX, 950),
                editButtonY = getInt(R.id.editButtonY, 300),
                editButtonYOffset = getInt(R.id.editYOffset, 80),
                priceInputX = getInt(R.id.editPriceX, 300),
                priceInputY = getInt(R.id.editPriceY, 400),
                updateButtonX = getInt(R.id.updateButtonX, 500),
                updateButtonY = getInt(R.id.updateButtonY, 550),
                confirmYesX = getInt(R.id.editConfirmYesX, 500),
                confirmYesY = getInt(R.id.editConfirmYesY, 600),
                closeButtonX = getInt(R.id.editCloseX, 1000),
                closeButtonY = getInt(R.id.editCloseY, 200),
                ocrRegionLeft = getInt(R.id.editOcrLeft, 600),
                ocrRegionTop = getInt(R.id.editOcrTop, 200),
                ocrRegionRight = getInt(R.id.editOcrRight, 1050),
                ocrRegionBottom = getInt(R.id.editOcrBottom, 500)
            )

            val globalConfig = GlobalSettings(
                networkLagMultiplier = getFloat(R.id.networkLagMultiplier, 1.0f),
                swipeStartX = getInt(R.id.swipeStartX, 500),
                swipeStartY = getInt(R.id.swipeStartY, 600),
                swipeEndX = getInt(R.id.swipeEndX, 500),
                swipeEndY = getInt(R.id.swipeEndY, 300),
                swipeDurationMs = getInt(R.id.swipeDuration, 300),
                tapDurationMs = getLong(R.id.tapDuration, 150),
                textInputDelayMs = getLong(R.id.textInputDelay, 200),
                popupOpenWaitMs = getLong(R.id.popupOpenWait, 800),
                popupCloseWaitMs = getLong(R.id.popupCloseWait, 600),
                confirmationWaitMs = getLong(R.id.confirmationWait, 500),
                cycleCooldownMs = getLong(R.id.cycleCooldown, 200)
            )

            val safetyConfig = SafetySettings(
                enableOcrSanityCheck = findViewById<CheckBox>(R.id.enableOcrSanityCheck).isChecked,
                maxPriceChangePercent = getFloat(R.id.maxPriceChangePercent, 0.2f),
                maxPriceCap = getInt(R.id.maxPriceCap, 100000),
                minPriceCap = getInt(R.id.minPriceCap, 1),
                autoDismissErrors = findViewById<CheckBox>(R.id.autoDismissErrors).isChecked,
                maxRetries = getInt(R.id.maxRetries, 3),
                uiTimeoutMs = getLong(R.id.uiTimeout, 3000)
            )

            val updatedData = CalibrationData(
                id = currentCalibration?.id ?: 0,
                createMode = createConfig,
                editMode = editConfig,
                global = globalConfig,
                safety = safetyConfig
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.calibrationDao().insertCalibration(updatedData)
                    currentCalibration = updatedData
                    withContext(Dispatchers.Main) {
                        showToast("Settings saved!")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        showToast("Error saving: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            showToast("Invalid input: ${e.message}")
        }
    }

    private fun resetToDefaults() {
        val defaultData = CalibrationData()
        populateFields(defaultData)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.calibrationDao().insertCalibration(defaultData)
                currentCalibration = defaultData
                withContext(Dispatchers.Main) {
                    showToast("Reset to defaults!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showToast("Error resetting: ${e.message}")
                }
            }
        }
    }

    private fun getInt(id: Int, default: Int): Int {
        return findViewById<EditText>(id).text.toString().toIntOrNull() ?: default
    }

    private fun getLong(id: Int, default: Long): Long {
        return findViewById<EditText>(id).text.toString().toLongOrNull() ?: default
    }

    private fun getFloat(id: Int, default: Float): Float {
        return findViewById<EditText>(id).text.toString().toFloatOrNull() ?: default
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
