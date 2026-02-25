package com.albion.marketassistant.data

import android.graphics.Rect
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded

/**
 * Configuration for Create Buy Order mode
 */
data class CreateModeConfig(
    val firstRowX: Int = 100,
    val firstRowY: Int = 300,
    val rowYOffset: Int = 80,
    val maxRowsPerScreen: Int = 5,
    val priceInputX: Int = 300,
    val priceInputY: Int = 400,
    val priceInputRegionLeft: Int = 200,
    val priceInputRegionTop: Int = 380,
    val priceInputRegionRight: Int = 450,
    val priceInputRegionBottom: Int = 420,
    val createButtonX: Int = 500,
    val createButtonY: Int = 550,
    val ocrRegionLeft: Int = 600,
    val ocrRegionTop: Int = 200,
    val ocrRegionRight: Int = 1050,
    val ocrRegionBottom: Int = 500,
    val confirmYesX: Int = 500,
    val confirmYesY: Int = 600,
    val closeButtonX: Int = 1000,
    val closeButtonY: Int = 200,
    // First row OCR region for end-of-list detection
    val firstRowOcrLeft: Int = 50,
    val firstRowOcrTop: Int = 280,
    val firstRowOcrRight: Int = 400,
    val firstRowOcrBottom: Int = 320
) {
    fun getOCRRegion(): Rect = Rect(ocrRegionLeft, ocrRegionTop, ocrRegionRight, ocrRegionBottom)
    fun getPriceInputRegion(): Rect = Rect(priceInputRegionLeft, priceInputRegionTop, priceInputRegionRight, priceInputRegionBottom)
    fun getFirstRowOCRRegion(): Rect = Rect(firstRowOcrLeft, firstRowOcrTop, firstRowOcrRight, firstRowOcrBottom)
}

/**
 * Configuration for Edit Buy Order mode
 */
data class EditModeConfig(
    val editButtonX: Int = 950,
    val editButtonY: Int = 300,
    val editButtonYOffset: Int = 80,
    val priceInputX: Int = 300,
    val priceInputY: Int = 400,
    val priceInputRegionLeft: Int = 200,
    val priceInputRegionTop: Int = 380,
    val priceInputRegionRight: Int = 450,
    val priceInputRegionBottom: Int = 420,
    val updateButtonX: Int = 500,
    val updateButtonY: Int = 550,
    val ocrRegionLeft: Int = 600,
    val ocrRegionTop: Int = 200,
    val ocrRegionRight: Int = 1050,
    val ocrRegionBottom: Int = 500,
    val confirmYesX: Int = 500,
    val confirmYesY: Int = 600,
    val closeButtonX: Int = 1000,
    val closeButtonY: Int = 200,
    // First row OCR region for end-of-list detection
    val firstRowOcrLeft: Int = 50,
    val firstRowOcrTop: Int = 280,
    val firstRowOcrRight: Int = 400,
    val firstRowOcrBottom: Int = 320
) {
    fun getOCRRegion(): Rect = Rect(ocrRegionLeft, ocrRegionTop, ocrRegionRight, ocrRegionBottom)
    fun getPriceInputRegion(): Rect = Rect(priceInputRegionLeft, priceInputRegionTop, priceInputRegionRight, priceInputRegionBottom)
    fun getFirstRowOCRRegion(): Rect = Rect(firstRowOcrLeft, firstRowOcrTop, firstRowOcrRight, firstRowOcrBottom)
}

/**
 * Global settings shared between modes
 */
data class GlobalSettings(
    val swipeStartX: Int = 500,
    val swipeStartY: Int = 600,
    val swipeEndX: Int = 500,
    val swipeEndY: Int = 300,
    val swipeDurationMs: Int = 300,
    val tapDurationMs: Long = 150,
    val textInputDelayMs: Long = 200,
    val popupOpenWaitMs: Long = 800,
    val popupCloseWaitMs: Long = 600,
    val confirmationWaitMs: Long = 500,
    val ocrScanDelayMs: Long = 300,
    val keyboardDismissDelayMs: Long = 300,
    val networkLagMultiplier: Float = 1.0f,
    val cycleCooldownMs: Long = 200,
    val highlightedRowColorHex: String = "#E8E8E8",
    val colorToleranceRGB: Int = 30,
    val ocrConfidenceThreshold: Float = 0.7f,
    val ocrLanguage: String = "en",
    // Albion Online package name for app verification
    val targetAppPackage: String = "com.albiononline",
    // Max consecutive same-page detections before stopping
    val maxSamePageCount: Int = 2
)

/**
 * Safety settings to prevent financial loss
 */
data class SafetySettings(
    val maxPriceChangePercent: Float = 0.2f,
    val maxPriceCap: Int = 100000,
    val minPriceCap: Int = 1,
    val enableOcrSanityCheck: Boolean = true,
    val maxRetries: Int = 3,
    val uiTimeoutMs: Long = 3000,
    val autoDismissErrors: Boolean = true,
    // Enable end-of-list detection
    val enableEndOfListDetection: Boolean = true,
    // Enable app package verification (immersive mode protection)
    val enableAppVerification: Boolean = true,
    // Pause on interruption (calls, notifications)
    val pauseOnInterruption: Boolean = true
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
    @Embedded(prefix = "safety_")
    val safety: SafetySettings = SafetySettings(),
    val lastModified: Long = System.currentTimeMillis()
)

enum class OperationMode { IDLE, NEW_ORDER_SWEEPER, ORDER_EDITOR }

enum class StateType {
    IDLE, PAUSED, WAIT_POPUP_OPEN, SCAN_HIGHLIGHTS, SCAN_OCR, VERIFY_UI_ELEMENT,
    EXECUTE_TAP, EXECUTE_TEXT_INPUT, EXECUTE_BUTTON, HANDLE_CONFIRMATION,
    HANDLE_ERROR_POPUP, DISMISS_KEYBOARD, WAIT_POPUP_CLOSE, SCROLL_NEXT_ROW,
    COMPLETE_ITERATION, ERROR_RETRY, ERROR_PRICE_SANITY, ERROR_TIMEOUT, 
    ERROR_END_OF_LIST, ERROR_WRONG_APP, COOLDOWN
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
    val retryCount: Int = 0,
    val lastPrice: Int? = null,
    val currentAppName: String? = null,
    val isCorrectApp: Boolean = true,
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
