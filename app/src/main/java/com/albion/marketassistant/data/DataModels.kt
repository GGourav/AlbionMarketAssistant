package com.albion.marketassistant.data

import android.graphics.Rect
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibration_data")
data class CalibrationData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val firstRowX: Int = 100,
    val firstRowY: Int = 300,
    val rowYOffset: Int = 80,
    val maxRowsPerScreen: Int = 5,
    val buyOrdersRegionLeft: Int = 600,
    val buyOrdersRegionTop: Int = 200,
    val buyOrdersRegionRight: Int = 1050,
    val buyOrdersRegionBottom: Int = 500,
    val priceInputX: Int = 300,
    val priceInputY: Int = 400,
    val priceInputRegionLeft: Int = 200,
    val priceInputRegionTop: Int = 380,
    val priceInputRegionRight: Int = 450,
    val priceInputRegionBottom: Int = 420,
    val confirmButtonX: Int = 500,
    val confirmButtonY: Int = 550,
    val closeButtonX: Int = 1000,
    val closeButtonY: Int = 200,
    val swipeStartX: Int = 500,
    val swipeStartY: Int = 600,
    val swipeEndX: Int = 500,
    val swipeEndY: Int = 300,
    val swipeDurationMs: Int = 500,
    val highlightedRowColorHex: String = "#E8E8E8",
    val colorToleranceRGB: Int = 30,
    val ocrConfidenceThreshold: Float = 0.7f,
    val ocrLanguage: String = "en",
    val tapDurationMs: Long = 100,
    val textInputDelayMs: Long = 200,
    val popupOpenWaitMs: Long = 800,
    val popupCloseWaitMs: Long = 600,
    val ocr_ScanDelayMs: Long = 500,
    val pixelPollingIntervalMs: Long = 300,
    val lastModified: Long = System.currentTimeMillis()
) {
    fun getBuyOrdersRegion(): Rect = Rect(buyOrdersRegionLeft, buyOrdersRegionTop, buyOrdersRegionRight, buyOrdersRegionBottom)
    fun getPriceInputRegion(): Rect = Rect(priceInputRegionLeft, priceInputRegionTop, priceInputRegionRight, priceInputRegionBottom)
}

enum class OperationMode { IDLE, NEW_ORDER_SWEEPER, ORDER_EDITOR }

enum class StateType { IDLE, WAIT_POPUP_OPEN, SCAN_HIGHLIGHTS, SCAN_OCR, EXECUTE_TAP, EXECUTE_TEXT_INPUT, EXECUTE_BUTTON, WAIT_POPUP_CLOSE, SCROLL_NEXT_ROW, COMPLETE_ITERATION, ERROR_RETRY }

data class AutomationState(
    val stateType: StateType = StateType.IDLE,
    val mode: OperationMode = OperationMode.IDLE,
    val currentRowIndex: Int = 0,
    val currentX: Int = 0,
    val currentY: Int = 0,
    val ocrResult: OCRResult? = null,
    val colorDetected: Boolean = false,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class OCRResult(
    val text: String = "",
    val confidence: Float = 0f,
    val boundingBox: Rect = Rect(0, 0, 0, 0),
    val isNumber: Boolean = false,
    val numericValue: Int? = null
)

data class ColorDetectionResult(
    val hexColor: String = "",
    val matchConfidence: Float = 0f,
    val isMatch: Boolean = false
)
