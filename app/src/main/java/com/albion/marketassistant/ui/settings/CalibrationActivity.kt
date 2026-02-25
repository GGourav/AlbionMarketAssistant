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
        // FIXED: Added default price and increment fields
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

        // FIXED: Default package name is now "com.albiononline"
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
                ocrRegionBottom = getInt(R.id.createOcrBottom, 500),
                // FIXED: Added default price and increment
                defaultBuyPrice = getInt(R.id.createDefaultPrice, 10000),
                priceIncrement = getInt(R.id.createPriceIncrement, 1)
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

            val antiDetectionConfig = AntiDetectionSettings(
                enableRandomization = findViewById<CheckBox>(R.id.enableRandomization).isChecked,
                randomizeGesturePath = findViewById<CheckBox>(R.id.randomizeGesturePath).isChecked,
                randomDelayRangeMs = getLong(R.id.randomDelayRange, 100),
                pathRandomizationPixels = getInt(R.id.pathRandomizationPixels, 5)
            )

            val endOfListConfig = EndOfListSettings(
                enableEndOfListDetection = findViewById<CheckBox>(R.id.enableEndOfListDetection).isChecked,
                identicalPageThreshold = getInt(R.id.identicalPageThreshold, 3),
                maxCyclesBeforeStop = getInt(R.id.maxCyclesBeforeStop, 500)
            )

            val immersiveModeConfig = ImmersiveModeSettings(
                enableWindowVerification = findViewById<CheckBox>(R.id.enableWindowVerification).isChecked,
                gamePackageName = findViewById<EditText>(R.id.gamePackageName).text.toString(),
                windowLostThreshold = getInt(R.id.windowLostThreshold, 2),
                autoResumeOnReturn = findViewById<CheckBox>(R.id.autoResumeOnReturn).isChecked
            )

            val swipeOverlapConfig = SwipeOverlapSettings(
                enableSwipeOverlap = findViewById<CheckBox>(R.id.enableSwipeOverlap).isChecked,
                overlapRowCount = getInt(R.id.overlapRowCount, 1),
                swipeSettleTimeMs = getLong(R.id.swipeSettleTime, 400)
            )

            val batteryConfig = BatterySettings(
                enableBatteryOptimization = findViewById<CheckBox>(R.id.enableBatteryOptimization).isChecked,
                pauseOnBatteryBelow = getInt(R.id.pauseOnBatteryBelow, 15)
            )

            val errorRecoveryConfig = ErrorRecoverySettings(
                enableSmartRecovery = findViewById<CheckBox>(R.id.enableSmartRecovery).isChecked,
                maxConsecutiveErrors = getInt(R.id.maxConsecutiveErrors, 5),
                maxStateStuckTimeMs = getLong(R.id.maxStateStuckTime, 30000),
                screenshotOnError = findViewById<CheckBox>(R.id.screenshotOnError).isChecked
            )

            val priceHistoryConfig = PriceHistorySettings(
                enablePriceHistory = findViewById<CheckBox>(R.id.enablePriceHistory).isChecked,
                enableAnomalyDetection = findViewById<CheckBox>(R.id.enableAnomalyDetection).isChecked,
                maxHistoryEntries = getInt(R.id.maxHistoryEntries, 100)
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
