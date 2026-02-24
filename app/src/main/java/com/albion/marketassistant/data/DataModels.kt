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

data class MarketOrder(
    val price: Int,
    val amount: Int
)

@Entity(tableName = "calibration_data")
data class CalibrationData(
    @PrimaryKey val id: Int = 1,
    
    // Row Tap Coordinates
    val firstRowX: Int = 100,
    val firstRowY: Int = 300,
    val rowYOffset: Int = 80,
    val maxRowsPerScreen: Int = 5,
    
    // Buy Orders Region (Right side of popup)
    val buyOrdersRegionLeft: Int = 600,
    val buyOrdersRegionTop: Int = 200,
    val buyOrdersRegionRight: Int = 1050,
    val buyOrdersRegionBottom: Int = 500,
    
    // Price Input Region (Left side of popup)
    val priceInputX: Int = 300,
    val priceInputY: Int = 400,
    val priceInputRegionLeft: Int = 200,
    val priceInputRegionTop: Int = 380,
    val priceInputRegionRight: Int = 450,
    val priceInputRegionBottom: Int = 420,
    
    // Buttons
    val confirmButtonX: Int = 500,
    val confirmButtonY: Int = 550,
    val closeButtonX: Int = 1000,
    val closeButtonY: Int = 200,
    
    // Colors & Timing
    val highlightedRowColorHex: String = "#E8E8E8",
    val colorToleranceRGB: Int = 30,
    
    val tapDurationMs: Long = 100,
    val textInputDelayMs: Long = 200,
    val popupOpenWaitMs: Long = 800,
    val popupCloseWaitMs: Long = 600,
    val pixelPollingIntervalMs: Long = 300
) {
    fun getBuyOrdersRegion(): Rect {
        return Rect(buyOrdersRegionLeft, buyOrdersRegionTop, buyOrdersRegionRight, buyOrdersRegionBottom)
    }
    
