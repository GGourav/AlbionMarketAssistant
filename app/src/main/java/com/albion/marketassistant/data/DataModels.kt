package com.albion.marketassistant.data

import android.graphics.Rect
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.TypeConverters

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
    val confirmYesY: Int = 600,
    
    // Close/Cancel button for error popups
    val closeButtonX: Int = 1000,
    val closeButtonY: Int = 200,
    
    // Default buy price (in silver) - configurable per item type
    val defaultBuyPrice: Int = 10000,
    
    // Price increment per row (to undercut competitors)
    val priceIncrement: Int = 1
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
    val tapDurationMs: Long = 150,
    val textInputDelayMs: Long = 200,
    val popupOpenWaitMs: Long = 800,
    val popupCloseWaitMs: Long = 600,
    val confirmationWaitMs: Long = 500,
    val ocrScanDelayMs: Long = 300,
    val keyboardDismissDelayMs: Long = 300,
    
    // Network lag multiplier (1.0 = normal, 2.0 = double delays)
    val networkLagMultiplier: Float = 1.0f,
    
    // Cooldown between cycles to prevent memory issues
    val cycleCooldownMs: Long = 200,

    // Color detection
    val highlightedRowColorHex: String = "#E8E8E8",
    val colorToleranceRGB: Int = 30,

    // OCR settings
    val ocrConfidenceThreshold: Float = 0.7f,
    val ocrLanguage: String = "en"
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
    val autoDismissErrors: Boolean = true
)

/**
 * Anti-Detection settings to avoid pattern detection
 */
data class AntiDetectionSettings(
    val enableRandomization: Boolean = true,
    val randomDelayRangeMs: Long = 100,
    val randomSwipeDistancePercent: Float = 0.1f,
    val randomizeGesturePath: Boolean = true,
    val pathRandomizationPixels: Int = 5,
    val minRandomDelayMs: Long = 50,
    val maxRandomDelayMs: Long = 200
)

/**
 * End of List Detection settings
 */
data class EndOfListSettings(
    val enableEndOfListDetection: Boolean = true,
    val identicalPageThreshold: Int = 3,
    val firstLineOcrLeft: Int = 100,
    val firstLineOcrTop: Int = 300,
    val firstLineOcrRight: Int = 900,
    val firstLineOcrBottom: Int = 350,
    val maxCyclesBeforeStop: Int = 500,
    val textSimilarityThreshold: Float = 0.9f
) {
    fun getFirstLineOcrRegion(): Rect = Rect(firstLineOcrLeft, firstLineOcrTop, firstLineOcrRight, firstLineOcrBottom)
}

/**
 * Immersive Mode / Interruption Trap settings
 */
data class ImmersiveModeSettings(
    val enableWindowVerification: Boolean = true,
    // FIXED: Changed from "com.albiononline.albiononline" to "com.albiononline"
    val gamePackageName: String = "com.albiononline",
    val actionOnWindowLost: String = "pause",
    val windowCheckIntervalMs: Long = 2000,
    val windowLostThreshold: Int = 2,
    val autoResumeOnReturn: Boolean = true
)

/**
 * Swipe Overlap Calibration settings
 */
data class SwipeOverlapSettings(
    val enableSwipeOverlap: Boolean = true,
    val overlapRowCount: Int = 1,
    val swipeSettleTimeMs: Long = 400,
    val rowDetectionTolerance: Int = 10,
    val enableScrollTracking: Boolean = true
)

/**
 * Battery Optimization settings
 */
data class BatterySettings(
    val enableBatteryOptimization: Boolean = true,
    val pauseOnBatteryBelow: Int = 15,
    val reduceOcrBelowPercent: Int = 30,
    val lowBatteryOcrMultiplier: Float = 1.5f,
    val dimScreenDuringAutomation: Boolean = true,
    val dimBrightnessLevel: Int = 30
)

/**
 * Error Recovery settings
 */
data class ErrorRecoverySettings(
    val enableSmartRecovery: Boolean = true,
    val maxConsecutiveErrors: Int = 5,
    val maxStateStuckTimeMs: Long = 30000,
    val actionOnStuck: String = "restart",
    val screenshotOnError: Boolean = true,
    val autoRestartAfterErrors: Int = 10,
    val autoRestartDelayMs: Long = 3000
)

/**
 * Price History settings
 */
data class PriceHistorySettings(
    val enablePriceHistory: Boolean = true,
    val maxHistoryEntries: Int = 100,
    val historyRetentionDays: Int = 7,
    val priceTrendAlert: String = "both",
    val trendAlertThreshold: Float = 0.1f,
    val enableAnomalyDetection: Boolean = true
)

/**
 * Session Statistics
 */
data class SessionStatistics(
    val sessionStartTime: Long = System.currentTimeMillis(),
    val totalCycles: Int = 0,
    val successfulOperations: Int = 0,
    val failedOperations: Int = 0,
    val priceUpdates: Int = 0,
    val ordersCreated: Int = 0,
    val ordersEdited: Int = 0,
    val errorsEncountered: Int = 0,
    val timeSavedMs: Long = 0,
    val estimatedProfitSilver: Long = 0,
    val sessionDurationMs: Long = 0,
    val averageCycleTimeMs: Long = 0,
    val lastCycleTime: Long = 0,
    val consecutiveErrors: Int = 0,
    val lastState: String = "",
    val stateEnterTime: Long = 0
) {
    fun getSuccessRate(): Float {
        val total = successfulOperations + failedOperations
        return if (total > 0) successfulOperations.toFloat() / total else 0f
    }
    
    fun getSessionDurationFormatted(): String {
        val seconds = sessionDurationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}

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
    
    @Embedded(prefix = "antidetection_")
    val antiDetection: AntiDetectionSettings = AntiDetectionSettings(),
    
    @Embedded(prefix = "endoflist_")
    val endOfList: EndOfListSettings = EndOfListSettings(),
    
    @Embedded(prefix = "immersive_")
    val immersiveMode: ImmersiveModeSettings = ImmersiveModeSettings(),
    
    @Embedded(prefix = "swipeoverlap_")
    val swipeOverlap: SwipeOverlapSettings = SwipeOverlapSettings(),
    
    @Embedded(prefix = "battery_")
    val battery: BatterySettings = BatterySettings(),
    
    @Embedded(prefix = "errorrecovery_")
    val errorRecovery: ErrorRecoverySettings = ErrorRecoverySettings(),
    
    @Embedded(prefix = "pricehistory_")
    val priceHistory: PriceHistorySettings = PriceHistorySettings(),

    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "price_history")
data class PriceHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val itemId: String = "",
    val price: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceMode: String = "",
    val wasSuccessful: Boolean = true,
    val sessionId: Long = 0
)

@Entity(tableName = "session_logs")
data class SessionLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sessionStart: Long = System.currentTimeMillis(),
    val sessionEnd: Long = 0,
    val mode: String = "",
    val totalCycles: Int = 0,
    val successfulOps: Int = 0,
    val failedOps: Int = 0,
    val errors: Int = 0,
    val exportPath: String = ""
)

enum class OperationMode { IDLE, NEW_ORDER_SWEEPER, ORDER_EDITOR }

enum class StateType {
    IDLE, PAUSED, WAIT_POPUP_OPEN, SCAN_HIGHLIGHTS, SCAN_OCR,
    VERIFY_UI_ELEMENT, VERIFY_GAME_WINDOW, EXECUTE_TAP, EXECUTE_TEXT_INPUT,
    EXECUTE_BUTTON, HANDLE_CONFIRMATION, HANDLE_ERROR_POPUP, DISMISS_KEYBOARD,
    WAIT_POPUP_CLOSE, SCROLL_NEXT_ROW, COMPLETE_ITERATION, ERROR_RETRY,
    ERROR_PRICE_SANITY, ERROR_TIMEOUT, ERROR_WINDOW_LOST, ERROR_STUCK_DETECTED,
    ERROR_END_OF_LIST, ERROR_BATTERY_LOW, COOLDOWN, RECOVERING
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
    val timestamp: Long = System.currentTimeMillis(),
    val statistics: SessionStatistics = SessionStatistics(),
    val lastPageText: String = "",
    val endOfListCount: Int = 0,
    val windowLostCount: Int = 0,
    val isGameWindowActive: Boolean = true
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
