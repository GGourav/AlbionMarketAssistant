package com.albion.marketassistant.data

import android.graphics.Rect
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OperationMode {
    IDLE,
    NEW_ORDER_SWEEPER,
    ORDER_EDITOR
}

enum class StateType {
    IDLE,
    WAIT_POPUP_OPEN,
    SCAN_HIGHLIGHTS,
    SCAN_OCR,
    EXECUTE_INPUT,
    WAIT_POPUP_CLOSE,
    SCROLLING,
    ERROR_RETRY
}

data class AutomationState(
    val stateType: StateType = StateType.IDLE,
    val mode: OperationMode = OperationMode.IDLE,
    val currentRowIndex: Int = 0,
    val ocrResult: OCRResult? = null,
    val colorDetected: Boolean = false,
    val errorMessage: String? = null
)

data class OCRResult(
    val text: String,
    val numericValue: Int?,
    val confidence: Float,
    val boundingBox: Rect?
)

data class ColorDetectionResult(
    val isMatch: Boolean,
    val confidence: Float,
    val matchedHex: String?
)

@Entity(tableName = "calibration_data")
data class CalibrationData(
    @PrimaryKey val id: Int = 1,
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
    val highlightedRowColorHex: String = "#E8E8E8",
    val colorToleranceRGB: Int = 30,
    val tapDurationMs: Long = 100,
    val textInputDelayMs: Long = 200,
    val popupOpenWaitMs: Long = 800,
    val popupCloseWaitMs: Long = 600,
    val pixelPollingIntervalMs: Long = 300
) {
    fun getBuyOrdersRegion(): Rect = Rect(buyOrdersRegionLeft, buyOrdersRegionTop, buyOrdersRegionRight, buyOrdersRegionBottom)
    fun getPriceInputRegion(): Rect = Rect(priceInputRegionLeft, priceInputRegionTop, priceInputRegionRight, priceInputRegionBottom)
}
