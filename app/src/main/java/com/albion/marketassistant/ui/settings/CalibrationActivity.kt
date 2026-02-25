package com.albion.marketassistant.ui.settings

import android.os.Bundle
import android.util.Log
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

    companion object {
        private const val TAG = "CalibrationActivity"
    }

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
        tabLayout.addTab(tabLayout.newTab().setText("Advanced"))

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
        findViewById<LinearLayout>(R.id.advancedLayout).visibility =
            if (tab == 4) LinearLayout.VISIBLE else LinearLayout.GONE
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
                Log.e(TAG, "Error loading calibration", e)
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
        findViewById<EditText>(R.id.createDefaultPrice).setText(data.createMode.defaultBuyPrice.toString())
        findViewById<EditText>(R.id.createPriceIncrement).setText(data.createMode.priceIncrement.toString())

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

        // Advanced fields
        findViewById<CheckBox>(R.id.enableRandomization).isChecked = data.antiDetection.enableRandomization
        findViewById<CheckBox>(R.id.randomizeGesturePath).isChecked = data.antiDetection.randomizeGesturePath
        findViewById<EditText>(R.id.randomDelayRange).setText(data.antiDetection.randomDelayRangeMs.toString())
        findViewById<EditText>(R.id.pathRandomizationPixels).setText(data.antiDetection.pathRandomizationPixels.toString())

        findViewById<CheckBox>(R.id.enableEndOfListDetection).isChecked = data.endOfList.enableEndOfListDetection
        findViewById<EditText>(R.id.identicalPageThreshold).setText(data.endOfList.identicalPageThreshold.toString())
        findViewById<EditText>(R.id.maxCyclesBeforeStop).setText(data.endOfList.maxCyclesBeforeStop.toString())
        
        // End of List OCR region fields
        findViewById<EditText>(R.id.firstLineOcrLeft).setText(data.endOfList.firstLineOcrLeft.toString())
        findViewById<EditText>(R.id.firstLineOcrTop).setText(data.endOfList.firstLineOcrTop.toString())
        findViewById<EditText>(R.id.firstLineOcrRight).setText(data.endOfList.firstLineOcrRight.toString())
        findViewById<EditText>(R.id.firstLineOcrBottom).setText(data.endOfList.firstLineOcrBottom.toString())

        findViewById<CheckBox>(R.id.enableWindowVerification).isChecked = data.immersiveMode.enableWindowVerification
        findViewById<EditText>(R.id.gamePackageName).setText(data.immersiveMode.gamePackageName)
        findViewById<EditText>(R.id.windowLostThreshold).setText(data.immersiveMode.windowLostThreshold.toString())
        findViewById<CheckBox>(R.id.autoResumeOnReturn).isChecked = data.immersiveMode.autoResumeOnReturn

        findViewById<CheckBox>(R.id.enableSwipeOverlap).isChecked = data.swipeOverlap.enableSwipeOverlap
        findViewById<EditText>(R.id.overlapRowCount).setText(data.swipeOverlap.overlapRowCount.toString())
        findViewById<EditText>(R.id.swipeSettleTime).setText(data.swipeOverlap.swipeSettleTimeMs.toString())

        findViewById<CheckBox>(R.id.enableBatteryOptimization).isChecked = data.battery.enableBatteryOptimization
        findViewById<EditText>(R.id.pauseOnBatteryBelow).setText(data.battery.pauseOnBatteryBelow.toString())

        findViewById<CheckBox>(R.id.enableSmartRecovery).isChecked = data.errorRecovery.enableSmartRecovery
        findViewById<EditText>(R.id.maxConsecutiveErrors).setText(data.errorRecovery.maxConsecutiveErrors.toString())
        findViewById<EditText>(R.id.maxStateStuckTime).setText(data.errorRecovery.maxStateStuckTimeMs.toString())
        findViewById<CheckBox>(R.id.screenshotOnError).isChecked = data.errorRecovery.screenshotOnError

        findViewById<CheckBox>(R.id.enablePriceHistory).isChecked = data.priceHistory.enablePriceHistory
        findViewById<CheckBox>(R.id.enableAnomalyDetection).isChecked = data.priceHistory.enableAnomalyDetection
        findViewById<EditText>(R.id.maxHistoryEntries).setText(data.priceHistory.maxHistoryEntries.toString())
    }

    private fun saveCalibration() {
        try {
            val createConfig = CreateModeConfig(
                firstRowX = getInt(R.id.createFirstRowX, 100, 0, 2000),
                firstRowY = getInt(R.id.createFirstRowY, 300, 0, 4000),
                rowYOffset = getInt(R.id.createRowYOffset, 80, 10, 500),
                maxRowsPerScreen = getInt(R.id.createMaxRows, 5, 1, 20),
                priceInputX = getInt(R.id.createPriceX, 300, 0, 2000),
                priceInputY = getInt(R.id.createPriceY, 400, 0, 4000),
                createButtonX = getInt(R.id.createButtonX, 500, 0, 2000),
                createButtonY = getInt(R.id.createButtonY, 550, 0, 4000),
                confirmYesX = getInt(R.id.createConfirmYesX, 500, 0, 2000),
                confirmYesY = getInt(R.id.createConfirmYesY, 600, 0, 4000),
                closeButtonX = getInt(R.id.createCloseX, 1000, 0, 2000),
                closeButtonY = getInt(R.id.createCloseY, 200, 0, 4000),
                ocrRegionLeft = getInt(R.id.createOcrLeft, 600, 0, 2000),
                ocrRegionTop = getInt(R.id.createOcrTop, 200, 0, 4000),
                ocrRegionRight = getInt(R.id.createOcrRight, 1050, 0, 2000),
                ocrRegionBottom = getInt(R.id.createOcrBottom, 500, 0, 4000),
                defaultBuyPrice = getInt(R.id.createDefaultPrice, 10000, 1, 10000000),
                priceIncrement = getInt(R.id.createPriceIncrement, 1, 0, 10000)
            )

            val editConfig = EditModeConfig(
                editButtonX = getInt(R.id.editButtonX, 950, 0, 2000),
                editButtonY = getInt(R.id.editButtonY, 300, 0, 4000),
                editButtonYOffset = getInt(R.id.editYOffset, 80, 10, 500),
                priceInputX = getInt(R.id.editPriceX, 300, 0, 2000),
                priceInputY = getInt(R.id.editPriceY, 400, 0, 4000),
                updateButtonX = getInt(R.id.updateButtonX, 500, 0, 2000),
                updateButtonY = getInt(R.id.updateButtonY, 550, 0, 4000),
                confirmYesX = getInt(R.id.editConfirmYesX, 500, 0, 2000),
                confirmYesY = getInt(R.id.editConfirmYesY, 600, 0, 4000),
                closeButtonX = getInt(R.id.editCloseX, 1000, 0, 2000),
                closeButtonY = getInt(R.id.editCloseY, 200, 0, 4000),
                ocrRegionLeft = getInt(R.id.editOcrLeft, 600, 0, 2000),
                ocrRegionTop = getInt(R.id.editOcrTop, 200, 0, 4000),
                ocrRegionRight = getInt(R.id.editOcrRight, 1050, 0, 2000),
                ocrRegionBottom = getInt(R.id.editOcrBottom, 500, 0, 4000)
            )

            val globalConfig = GlobalSettings(
                networkLagMultiplier = getFloat(R.id.networkLagMultiplier, 1.0f, 0.1f, 10.0f),
                swipeStartX = getInt(R.id.swipeStartX, 500, 0, 2000),
                swipeStartY = getInt(R.id.swipeStartY, 600, 0, 4000),
                swipeEndX = getInt(R.id.swipeEndX, 500, 0, 2000),
                swipeEndY = getInt(R.id.swipeEndY, 300, 0, 4000),
                swipeDurationMs = getInt(R.id.swipeDuration, 300, 50, 5000),
                tapDurationMs = getLong(R.id.tapDuration, 150, 50, 1000),
                textInputDelayMs = getLong(R.id.textInputDelay, 200, 0, 10000),
                popupOpenWaitMs = getLong(R.id.popupOpenWait, 800, 100, 10000),
                popupCloseWaitMs = getLong(R.id.popupCloseWait, 600, 100, 10000),
                confirmationWaitMs = getLong(R.id.confirmationWait, 500, 100, 10000),
                cycleCooldownMs = getLong(R.id.cycleCooldown, 200, 0, 10000)
            )

            val safetyConfig = SafetySettings(
                enableOcrSanityCheck = findViewById<CheckBox>(R.id.enableOcrSanityCheck).isChecked,
                maxPriceChangePercent = getFloat(R.id.maxPriceChangePercent, 0.2f, 0.01f, 1.0f),
                maxPriceCap = getInt(R.id.maxPriceCap, 100000, 1, 100000000),
                minPriceCap = getInt(R.id.minPriceCap, 1, 1, 100000),
                autoDismissErrors = findViewById<CheckBox>(R.id.autoDismissErrors).isChecked,
                maxRetries = getInt(R.id.maxRetries, 3, 1, 20),
                uiTimeoutMs = getLong(R.id.uiTimeout, 3000, 100, 60000)
            )

            val antiDetectionConfig = AntiDetectionSettings(
                enableRandomization = findViewById<CheckBox>(R.id.enableRandomization).isChecked,
                randomizeGesturePath = findViewById<CheckBox>(R.id.randomizeGesturePath).isChecked,
                randomDelayRangeMs = getLong(R.id.randomDelayRange, 100, 0, 5000),
                pathRandomizationPixels = getInt(R.id.pathRandomizationPixels, 5, 0, 50)
            )

            val endOfListConfig = EndOfListSettings(
                enableEndOfListDetection = findViewById<CheckBox>(R.id.enableEndOfListDetection).isChecked,
                identicalPageThreshold = getInt(R.id.identicalPageThreshold, 3, 1, 20),
                maxCyclesBeforeStop = getInt(R.id.maxCyclesBeforeStop, 500, 10, 10000),
                firstLineOcrLeft = getInt(R.id.firstLineOcrLeft, 100, 0, 2000),
                firstLineOcrTop = getInt(R.id.firstLineOcrTop, 300, 0, 4000),
                firstLineOcrRight = getInt(R.id.firstLineOcrRight, 900, 0, 2000),
                firstLineOcrBottom = getInt(R.id.firstLineOcrBottom, 350, 0, 4000)
            )

            val immersiveModeConfig = ImmersiveModeSettings(
                enableWindowVerification = findViewById<CheckBox>(R.id.enableWindowVerification).isChecked,
                gamePackageName = findViewById<EditText>(R.id.gamePackageName).text.toString().trim(),
                windowLostThreshold = getInt(R.id.windowLostThreshold, 2, 1, 10),
                autoResumeOnReturn = findViewById<CheckBox>(R.id.autoResumeOnReturn).isChecked
            )

            val swipeOverlapConfig = SwipeOverlapSettings(
                enableSwipeOverlap = findViewById<CheckBox>(R.id.enableSwipeOverlap).isChecked,
                overlapRowCount = getInt(R.id.overlapRowCount, 1, 0, 10),
                swipeSettleTimeMs = getLong(R.id.swipeSettleTime, 400, 100, 5000)
            )

            val batteryConfig = BatterySettings(
                enableBatteryOptimization = findViewById<CheckBox>(R.id.enableBatteryOptimization).isChecked,
                pauseOnBatteryBelow = getInt(R.id.pauseOnBatteryBelow, 15, 5, 50)
            )

            val errorRecoveryConfig = ErrorRecoverySettings(
                enableSmartRecovery = findViewById<CheckBox>(R.id.enableSmartRecovery).isChecked,
                maxConsecutiveErrors = getInt(R.id.maxConsecutiveErrors, 5, 1, 50),
                maxStateStuckTimeMs = getLong(R.id.maxStateStuckTime, 30000, 1000, 300000),
                screenshotOnError = findViewById<CheckBox>(R.id.screenshotOnError).isChecked
            )

            val priceHistoryConfig = PriceHistorySettings(
                enablePriceHistory = findViewById<CheckBox>(R.id.enablePriceHistory).isChecked,
                enableAnomalyDetection = findViewById<CheckBox>(R.id.enableAnomalyDetection).isChecked,
                maxHistoryEntries = getInt(R.id.maxHistoryEntries, 100, 10, 10000)
            )

            val updatedData = CalibrationData(
                id = currentCalibration?.id ?: 0,
                createMode = createConfig,
                editMode = editConfig,
                global = globalConfig,
                safety = safetyConfig,
                antiDetection = antiDetectionConfig,
                endOfList = endOfListConfig,
                immersiveMode = immersiveModeConfig,
                swipeOverlap = swipeOverlapConfig,
                battery = batteryConfig,
                errorRecovery = errorRecoveryConfig,
                priceHistory = priceHistoryConfig
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.calibrationDao().insertCalibration(updatedData)
                    currentCalibration = updatedData
                    withContext(Dispatchers.Main) {
                        showToast("Settings saved!")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving calibration", e)
                    withContext(Dispatchers.Main) {
                        showToast("Error saving: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid input", e)
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
                Log.e(TAG, "Error resetting", e)
                withContext(Dispatchers.Main) {
                    showToast("Error resetting: ${e.message}")
                }
            }
        }
    }

    // Fixed: Added validation with min/max
    private fun getInt(id: Int, default: Int, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int {
        val text = findViewById<EditText>(id).text.toString()
        val value = text.toIntOrNull() ?: default
        return value.coerceIn(min, max)
    }

    private fun getLong(id: Int, default: Long, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): Long {
        val text = findViewById<EditText>(id).text.toString()
        val value = text.toLongOrNull() ?: default
        return value.coerceIn(min, max)
    }

    private fun getFloat(id: Int, default: Float, min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE): Float {
        val text = findViewById<EditText>(id).text.toString()
        val value = text.toFloatOrNull() ?: default
        return value.coerceIn(min, max)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
