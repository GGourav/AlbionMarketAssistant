package com.albion.marketassistant.data

import android.graphics.Rect
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded

/**
 * Configuration for Create Buy Order mode
 */
data class CreateModeConfig(
    // Row coordinates for tapping market rows
    val firstRowX: Int = 100,
    val firstRowY: Int = 300,
    val rowYOffset: Int = 80,
    val maxRowsPerScreen: Int = 5,

    // Price input box coordinates
    val priceInputX: Int = 300,
    val priceInputY: Int = 400,
    val priceInputRegionLeft: Int = 200,
    val priceInputRegionTop: Int = 380,
    val priceInputRegionRight: Int = 450,
    val priceInputRegionBottom: Int = 420,

    // Create Order button coordinates
    val createButtonX: Int = 500,
    val createButtonY: Int = 550,

    // OCR scan region for price reading
    val ocrRegionLeft: Int = 600,
    val ocrRegionTop: Int = 200,
    val ocrRegionRight: Int = 1050,
    val ocrRegionBottom: Int = 500,

    // Confirmation popup "Yes" button
    val confirmYesX: Int = 500,
    val confirmYesY: Int = 600
) {
    fun getOCRRegion(): Rect = Rect(ocrRegionLeft, ocrRegionTop, ocrRegionRight, ocrRegionBottom)
    fun getPriceInputRegion(): Rect = Rect(priceInputRegionLeft, priceInputRegionTop, priceInputRegionRight, priceInputRegionBottom)
}

/**
 * Configuration for Edit Buy Order mode
 */
data class EditModeConfig(
    // Edit button coordinates (per row)
    val editButtonX: Int = 950,
    val editButtonY: Int = 300,
    val editButtonYOffset: Int = 80,

    // Price input box coordinates
    val priceInputX: Int = 300,
    val priceInputY: Int = 400,
    val priceInputRegionLeft: Int = 200,
    val priceInputRegionTop: Int = 380,
    val priceInputRegionRight: Int = 450,
    val priceInputRegionBottom: Int = 420,

    // Update Order button coordinates
    val updateButtonX: Int = 500,
    val updateButtonY: Int = 550,

    // OCR scan region for price reading
    val ocrRegionLeft: Int = 600,
    val ocrRegionTop: Int = 200,
    val ocrRegionRight: Int = 1050,
    val ocrRegionBottom: Int = 500,

    // Confirmation popup "Yes" button
    val confirmYesX: Int = 500,
    val confirmYesY: Int = 600,

    // Close button coordinates
    val closeButtonX: Int = 1000,
    val closeButtonY: Int = 200
) {
    fun getOCRRegion(): Rect = Rect(ocrRegionLeft, ocrRegionTop, ocrRegionRight, ocrRegionBottom)
    fun getPriceInputRegion(): Rect = Rect(priceInputRegionLeft, priceInputRegionTop, priceInputRegionRight, priceInputRegionBottom)
}

/**
 * Global settings shared between modes
 */
data class GlobalSettings(
    // Swipe settings for scrolling
    val swipeStartX: Int = 500,
    val swipeStartY: Int = 600,
    val swipeEndX: Int = 500,
    val swipeEndY: Int = 300,
    val swipeDurationMs: Int = 300,

    // Timing settings (milliseconds)
    val tapDurationMs: Long = 150,  // 150ms for heavy 3D game
    val textInputDelayMs: Long = 200,
    val popupOpenWaitMs: Long = 800,
    val popupCloseWaitMs: Long = 600,
    val confirmationWaitMs: Long = 500,
    val ocrScanDelayMs: Long = 300,
    val keyboardDismissDelayMs: Long = 300,

    // Color detection
    val highlightedRowColorHex: String = "#E8E8E8",
    val colorToleranceRGB: Int = 30,

    // OCR settings
    val ocrConfidenceThreshold: Float = 0.7f,
    val ocrLanguage: String = "en"
)

@Entity(tableName = "calibration_data")
data class CalibrationData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @Embedded(prefix = "create_")
    val createMode: CreateModeConfig = CreateModeConfig(),

    @Embedded(prefix = "edit_")
    val editMode: EditModeConfig = EditModeConfig(),

    @Embedded(prefix = "global_")
    val global: GlobalSettings = GlobalSettings(),

    val lastModified: Long = System.currentTimeMillis()
)

enum class OperationMode { IDLE, NEW_ORDER_SWEEPER, ORDER_EDITOR }

enum class StateType {
    IDLE,
    PAUSED,
    WAIT_POPUP_OPEN,
    SCAN_HIGHLIGHTS,
    SCAN_OCR,
    EXECUTE_TAP,
    EXECUTE_TEXT_INPUT,
    EXECUTE_BUTTON,
    HANDLE_CONFIRMATION,
    DISMISS_KEYBOARD,
    WAIT_POPUP_CLOSE,
    SCROLL_NEXT_ROW,
    COMPLETE_ITERATION,
    ERROR_RETRY
}

data class AutomationState(
    val stateType: StateType = StateType.IDLE,
    val mode: OperationMode = OperationMode.IDLE,
    val currentRowIndex: Int = 0,
    val currentX: Int = 0,
    val currentY: Int = 0,
    val ocrResult: OCRResult? = null,
    val colorDetected: Boolean = false,
    val errorMessage: String? = null,
    val isPaused: Boolean = false,
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
