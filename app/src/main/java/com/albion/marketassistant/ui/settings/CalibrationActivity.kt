package com.albion.marketassistant.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.albion.marketassistant.R
import com.albion.marketassistant.data.CalibrationData
import com.albion.marketassistant.db.CalibrationDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalibrationActivity : AppCompatActivity() {

    private val db by lazy { CalibrationDatabase.getInstance(this) }
    private var currentCalibration: CalibrationData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

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

    private fun loadCurrentCalibration() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val calibration = db.calibrationDao().getCalibration() ?: CalibrationData()
                currentCalibration = calibration
                withContext(Dispatchers.Main) {
                    populateFields(calibration)
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
        findViewById<EditText>(R.id.etFirstRowX).setText(data.firstRowX.toString())
        findViewById<EditText>(R.id.etFirstRowY).setText(data.firstRowY.toString())
        findViewById<EditText>(R.id.etRowYOffset).setText(data.rowYOffset.toString())
        findViewById<EditText>(R.id.etMaxRows).setText(data.maxRowsPerScreen.toString())
        findViewById<EditText>(R.id.etConfirmX).setText(data.confirmButtonX.toString())
        findViewById<EditText>(R.id.etConfirmY).setText(data.confirmButtonY.toString())
        findViewById<EditText>(R.id.etCloseX).setText(data.closeButtonX.toString())
        findViewById<EditText>(R.id.etCloseY).setText(data.closeButtonY.toString())
        findViewById<EditText>(R.id.etSwipeStartX).setText(data.swipeStartX.toString())
        findViewById<EditText>(R.id.etSwipeStartY).setText(data.swipeStartY.toString())
        findViewById<EditText>(R.id.etSwipeEndX).setText(data.swipeEndX.toString())
        findViewById<EditText>(R.id.etSwipeEndY).setText(data.swipeEndY.toString())
        findViewById<EditText>(R.id.etSwipeDuration).setText(data.swipeDurationMs.toString())
        findViewById<EditText>(R.id.etTapDuration).setText(data.tapDurationMs.toString())
        findViewById<EditText>(R.id.etTextInputDelay).setText(data.textInputDelayMs.toString())
        findViewById<EditText>(R.id.etPopupOpenWait).setText(data.popupOpenWaitMs.toString())
        findViewById<EditText>(R.id.etPopupCloseWait).setText(data.popupCloseWaitMs.toString())
        findViewById<EditText>(R.id.etHighlightColor).setText(data.highlightedRowColorHex)
        findViewById<EditText>(R.id.etColorTolerance).setText(data.colorToleranceRGB.toString())
    }

    private fun saveCalibration() {
        try {
            val updatedData = CalibrationData(
                id = currentCalibration?.id ?: 0,
                firstRowX = findViewById<EditText>(R.id.etFirstRowX).text.toString().toIntOrNull() ?: 100,
                firstRowY = findViewById<EditText>(R.id.etFirstRowY).text.toString().toIntOrNull() ?: 300,
                rowYOffset = findViewById<EditText>(R.id.etRowYOffset).text.toString().toIntOrNull() ?: 80,
                maxRowsPerScreen = findViewById<EditText>(R.id.etMaxRows).text.toString().toIntOrNull() ?: 5,
                confirmButtonX = findViewById<EditText>(R.id.etConfirmX).text.toString().toIntOrNull() ?: 500,
                confirmButtonY = findViewById<EditText>(R.id.etConfirmY).text.toString().toIntOrNull() ?: 550,
                closeButtonX = findViewById<EditText>(R.id.etCloseX).text.toString().toIntOrNull() ?: 1000,
                closeButtonY = findViewById<EditText>(R.id.etCloseY).text.toString().toIntOrNull() ?: 200,
                swipeStartX = findViewById<EditText>(R.id.etSwipeStartX).text.toString().toIntOrNull() ?: 500,
                swipeStartY = findViewById<EditText>(R.id.etSwipeStartY).text.toString().toIntOrNull() ?: 600,
                swipeEndX = findViewById<EditText>(R.id.etSwipeEndX).text.toString().toIntOrNull() ?: 500,
                swipeEndY = findViewById<EditText>(R.id.etSwipeEndY).text.toString().toIntOrNull() ?: 300,
                swipeDurationMs = findViewById<EditText>(R.id.etSwipeDuration).text.toString().toIntOrNull() ?: 500,
                tapDurationMs = findViewById<EditText>(R.id.etTapDuration).text.toString().toLongOrNull() ?: 100,
                textInputDelayMs = findViewById<EditText>(R.id.etTextInputDelay).text.toString().toLongOrNull() ?: 200,
                popupOpenWaitMs = findViewById<EditText>(R.id.etPopupOpenWait).text.toString().toLongOrNull() ?: 800,
                popupCloseWaitMs = findViewById<EditText>(R.id.etPopupCloseWait).text.toString().toLongOrNull() ?: 600,
                highlightedRowColorHex = findViewById<EditText>(R.id.etHighlightColor).text.toString().ifEmpty { "#E8E8E8" },
                colorToleranceRGB = findViewById<EditText>(R.id.etColorTolerance).text.toString().toIntOrNull() ?: 30,
                buyOrdersRegionLeft = currentCalibration?.buyOrdersRegionLeft ?: 600,
                buyOrdersRegionTop = currentCalibration?.buyOrdersRegionTop ?: 200,
                buyOrdersRegionRight = currentCalibration?.buyOrdersRegionRight ?: 1050,
                buyOrdersRegionBottom = currentCalibration?.buyOrdersRegionBottom ?: 500,
                priceInputX = currentCalibration?.priceInputX ?: 300,
                priceInputY = currentCalibration?.priceInputY ?: 400,
                priceInputRegionLeft = currentCalibration?.priceInputRegionLeft ?: 200,
                priceInputRegionTop = currentCalibration?.priceInputRegionTop ?: 380,
                priceInputRegionRight = currentCalibration?.priceInputRegionRight ?: 450,
                priceInputRegionBottom = currentCalibration?.priceInputRegionBottom ?: 420,
                ocrConfidenceThreshold = currentCalibration?.ocrConfidenceThreshold ?: 0.7f,
                ocrLanguage = currentCalibration?.ocrLanguage ?: "en",
                ocr_ScanDelayMs = currentCalibration?.ocr_ScanDelayMs ?: 500,
                pixelPollingIntervalMs = currentCalibration?.pixelPollingIntervalMs ?: 300
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.calibrationDao().insertCalibration(updatedData)
                    currentCalibration = updatedData
                    withContext(Dispatchers.Main) {
                        showToast("Calibration saved successfully!")
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

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
