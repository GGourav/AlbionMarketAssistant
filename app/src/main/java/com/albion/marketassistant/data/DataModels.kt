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
    val confirmYesY: Int = 600,
    
    // Close/Cancel button for error popups
    val closeButtonX: Int = 1000,
    val closeButtonY: Int = 200
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
    // Maximum price change percentage (0.2 = 20% max increase)
    val maxPriceChangePercent: Float = 0.2f,
    
    // Hard cap on maximum price (in silver)
    val maxPriceCap: Int = 100000,
    
    // Minimum price to check against
    val minPriceCap: Int = 1,
    
    // Enable OCR sanity check
    val enableOcrSanityCheck: Boolean = true,
    
    // Max retries before skipping to next item
    val maxRetries: Int = 3,
    
    // Timeout for waiting for UI elements (ms)
    val uiTimeoutMs: Long = 3000,
    
    // Enable automatic error popup dismissal
    val autoDismissErrors: Boolean = true
)

/**
 * Anti-Detection settings to avoid pattern detection
 */
data class AntiDetectionSettings(
    // Enable randomization
    val enableRandomization: Boolean = true,
    
    // Random delay range (base delay ± this value)
    val randomDelayRangeMs: Long = 100,  // ±100ms
    
    // Randomize swipe distance (±percentage)
    val randomSwipeDistancePercent: Float = 0.1f,  // ±10%
    
    // Randomize gesture path (not perfectly straight)
    val randomizeGesturePath: Boolean = true,
    
    // Path randomization amount in pixels
    val pathRandomizationPixels: Int = 5,
    
    // Minimum random delay between actions
    val minRandomDelayMs: Long = 50,
    
    // Maximum random delay between actions
    val maxRandomDelayMs: Long = 200
)

/**
 * End of List Detection settings
 */
data class EndOfListSettings(
    // Enable end of list detection
    val enableEndOfListDetection: Boolean = true,
    
    // Number of identical page comparisons before stopping
    val identicalPageThreshold: Int = 3,
    
    // First line OCR region for comparison
    val firstLineOcrLeft: Int = 100,
    val firstLineOcrTop: Int = 300,
    val firstLineOcrRight: Int = 900,
    val firstLineOcrBottom: Int = 350,
    
    // Maximum cycles before auto-stop (safety limit)
    val maxCyclesBeforeStop: Int = 500,
    
    // Text similarity threshold (0.0-1.0, higher = more strict)
    val textSimilarityThreshold: Float = 0.9f
) {
    fun getFirstLineOcrRegion(): Rect = Rect(firstLineOcrLeft, firstLineOcrTop, firstLineOcrRight, firstLineOcrBottom)
}

/**
 * Immersive Mode / Interruption Trap settings
 */
data class ImmersiveModeSettings(
    // Enable game window verification
    val enableWindowVerification: Boolean = true,
    
    // Albion Online package name
    val gamePackageName: String = "com.albiononline.albiononline",
    
    // Action when game window is lost (pause/stop)
    val actionOnWindowLost: String = "pause",  // "pause" or "stop"
    
    // How often to check window (ms)
    val windowCheckIntervalMs: Long = 2000,
    
    // Number of consecutive failures before action
    val windowLostThreshold: Int = 2,
    
    // Auto-resume when game window returns
    val autoResumeOnReturn: Boolean = true
)

/**
 * Swipe Overlap Calibration settings
 */
data class SwipeOverlapSettings(
    // Enable swipe overlap prevention
    val enableSwipeOverlap: Boolean = true,
    
    // Overlap rows to ensure no items are skipped
    val overlapRowCount: Int = 1,
    
    // Wait time after swipe for scroll to settle (ms)
    val swipeSettleTimeMs: Long = 400,
    
    // Pixel tolerance for row detection
    val rowDetectionTolerance: Int = 10,
    
    // Enable scroll position tracking
    val enableScrollTracking: Boolean = true
)

/**
 * Battery Optimization settings
 */
data class BatterySettings(
    // Enable battery optimization
    val enableBatteryOptimization: Boolean = true,
    
    // Pause when battery below this percentage
    val pauseOnBatteryBelow: Int = 15,
    
    // Reduce OCR frequency when battery below this percentage
    val reduceOcrBelowPercent: Int = 30,
    
    // OCR frequency multiplier when battery is low
    val lowBatteryOcrMultiplier: Float = 1.5f,
    
    // Dim screen during automation
    val dimScreenDuringAutomation: Boolean = true,
    
    // Screen brightness level during automation (0-255)
    val dimBrightnessLevel: Int = 30
)

/**
 * Error Recovery settings
 */
data class ErrorRecoverySettings(
    // Enable smart error recovery
    val enableSmartRecovery: Boolean = true,
    
    // Max consecutive errors before auto-restart
    val maxConsecutiveErrors: Int = 5,
    
    // Max time stuck in same state (ms) before action
    val maxStateStuckTimeMs: Long = 30000,  // 30 seconds
    
    // Action when stuck (restart/pause/stop)
    val actionOnStuck: String = "restart",  // "restart", "pause", or "stop"
    
    // Capture screenshot on critical errors
    val screenshotOnError: Boolean = true,
    
    // Auto-restart from beginning after X errors
    val autoRestartAfterErrors: Int = 10,
    
    // Delay before auto-restart (ms)
    val autoRestartDelayMs: Long = 3000
)

/**
 * Price History settings
 */
data class PriceHistorySettings(
    // Enable price history tracking
    val enablePriceHistory: Boolean = true,
    
    // Maximum history entries to keep per item
    val maxHistoryEntries: Int = 100,
    
    // Days to keep history before cleanup
    val historyRetentionDays: Int = 7,
    
    // Alert on price trend (up/down/none)
    val priceTrendAlert: String = "both",  // "up", "down", "both", "none"
    
    // Percentage change to trigger alert
    val trendAlertThreshold: Float = 0.1f,  // 10%
    
    // Enable price anomaly detection
    val enableAnomalyDetection: Boolean = true
)

/**
 * Session Statistics
 */
data class SessionStatistics(
    // Session start time
    val sessionStartTime: Long = System.currentTimeMillis(),
    
    // Total cycles completed
    val totalCycles: Int = 0,
    
    // Successful operations
    val successfulOperations: Int = 0,
    
    // Failed operations
    val failedOperations: Int = 0,
    
    // Price updates made
    val priceUpdates: Int = 0,
    
    // Orders created
    val ordersCreated: Int = 0,
    
    // Orders edited
    val ordersEdited: Int = 0,
    
    // Errors encountered
    val errorsEncountered: Int = 0,
    
    // Time saved (estimated ms)
    val timeSavedMs: Long = 0,
    
    // Silver profit (estimated)
    val estimatedProfitSilver: Long = 0,
    
    // Current session duration (ms)
    val sessionDurationMs: Long = 0,
    
    // Average cycle time (ms)
    val averageCycleTimeMs: Long = 0,
    
    // Last cycle timestamp
    val lastCycleTime: Long = 0,
    
    // Consecutive errors count
    val consecutiveErrors: Int = 0,
    
    // Last state for stuck detection
    val lastState: String = "",
    
    // Time entered current state
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
    
    // Item identifier (could be OCR text or custom ID)
    val itemId: String = "",
    
    // Price in silver
    val price: Int = 0,
    
    // Timestamp of the price record
    val timestamp: Long = System.currentTimeMillis(),
    
    // Source mode (CREATE or EDIT)
    val sourceMode: String = "",
    
    // Was this a successful update?
    val wasSuccessful: Boolean = true,
    
    // Session ID for grouping
    val sessionId: Long = 0
)

@Entity(tableName = "session_logs")
data class SessionLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // Session start time
    val sessionStart: Long = System.currentTimeMillis(),
    
    // Session end time
    val sessionEnd: Long = 0,
    
    // Mode used
    val mode: String = "",
    
    // Total cycles
    val totalCycles: Int = 0,
    
    // Successful operations
    val successfulOps: Int = 0,
    
    // Failed operations
    val failedOps: Int = 0,
    
    // Errors encountered
    val errors: Int = 0,
    
    // Export path if saved
    val exportPath: String = ""
)

enum class OperationMode { IDLE, NEW_ORDER_SWEEPER, ORDER_EDITOR }

enum class StateType {
    IDLE,
    PAUSED,
    WAIT_POPUP_OPEN,
    SCAN_HIGHLIGHTS,
    SCAN_OCR,
    VERIFY_UI_ELEMENT,
    VERIFY_GAME_WINDOW,
    EXECUTE_TAP,
    EXECUTE_TEXT_INPUT,
    EXECUTE_BUTTON,
    HANDLE_CONFIRMATION,
    HANDLE_ERROR_POPUP,
    DISMISS_KEYBOARD,
    WAIT_POPUP_CLOSE,
    SCROLL_NEXT_ROW,
    COMPLETE_ITERATION,
    ERROR_RETRY,
    ERROR_PRICE_SANITY,
    ERROR_TIMEOUT,
    ERROR_WINDOW_LOST,
    ERROR_STUCK_DETECTED,
    ERROR_END_OF_LIST,
    ERROR_BATTERY_LOW,
    COOLDOWN,
    RECOVERING
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
