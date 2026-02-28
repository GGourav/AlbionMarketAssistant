// FILE: app/src/main/java/com/albion/marketassistant/data/DataModels.kt
// UPDATED: Percentage-based coordinates for iQOO Neo 6 (1080×2400) - works on any screen
// ALL coordinates are PERCENTAGES (0.0 to 1.0)

package com.albion.marketassistant.data

import android.graphics.Rect
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded

/**
 * Configuration for Create Buy Order mode
 * MODE 1: FAST LOOP - Tap + button to outbid
 * 
 * All coordinates are PERCENTAGES (0.0-1.0):
 * - 0.0 = leftmost/topmost edge
 * - 0.5 = center
 * - 1.0 = rightmost/bottommost edge
 */
data class CreateModeConfig(
    // Buy Order button position
    val buyOrderButtonXPercent: Double = 0.82,
    val firstRowYPercent: Double = 0.35,
    val rowYOffsetPercent: Double = 0.10,
    val maxRowsPerScreen: Int = 6,
    
    // Plus button position (increment price)
    val plusButtonXPercent: Double = 0.78,
    val plusButtonYPercent: Double = 0.62,
    
    // Confirm button position
    val confirmButtonXPercent: Double = 0.75,
    val confirmButtonYPercent: Double = 0.88,
    
    // Scroll settings
    val scrollStartYPercent: Double = 0.75,
    val scrollEndYPercent: Double = 0.35,
    val scrollXPercent: Double = 0.50,
    
    // Limits
    val maxItemsToProcess: Int = 50,
    val hardPriceCap: Int = 100000
)

/**
 * Configuration for Edit Buy Order mode
 * MODE 2: READ & TYPE LOOP - OCR price, inject new price
 */
data class EditModeConfig(
    // My Orders tab position
    val myOrdersTabXPercent: Double = 0.90,
    val myOrdersTabYPercent: Double = 0.13,
    
    // Edit button position (ONLY ROW 1 - no iteration)
    val editButtonXPercent: Double = 0.85,
    val editButtonYPercent: Double = 0.55,
    
    // Price field position
    val priceFieldXPercent: Double = 0.50,
    val priceFieldYPercent: Double = 0.62,
    
    // Update button position
    val updateButtonXPercent: Double = 0.75,
    val updateButtonYPercent: Double = 0.88,
    
    // Close button position
    val closeButtonXPercent: Double = 0.95,
    val closeButtonYPercent: Double = 0.10,
    
    // OCR region (percentage of screen)
    val ocrRegionLeftPercent: Double = 0.35,
    val ocrRegionTopPercent: Double = 0.25,
    val ocrRegionRightPercent: Double = 0.65,
    val ocrRegionBottomPercent: Double = 0.45,
    
    // Limits
    val maxOrdersToEdit: Int = 20,
    val hardPriceCap: Int = 100000,
    val priceIncrement: Int = 1
)

/**
 * Global settings shared between modes
 */
data class GlobalSettings(
    val delayAfterTapMs: Long = 900,
    val delayAfterSwipeMs: Long = 1200,
    val delayAfterConfirmMs: Long = 1100,
    val cycleCooldownMs: Long = 500,
    val tapDurationMs: Long = 100,
    val swipeDurationMs: Long = 500
)

/**
 * Safety settings
 */
data class SafetySettings(
    val maxPriceChangePercent: Float = 0.5f,
    val maxPriceCap: Int = 100000,
    val minPriceCap: Int = 1,
    val enableOcrSanityCheck: Boolean = true,
    val maxRetries: Int = 3,
    val uiTimeoutMs: Long = 5000,
    val autoDismissErrors: Boolean = true
)

/**
 * Anti-Detection settings (used by RandomizationHelper)
 */
data class RandomizationSettings(
    val enableRandomization: Boolean = true,
    val minRandomDelayMs: Long = 50,
    val maxRandomDelayMs: Long = 200,
    val randomSwipeDistancePercent: Float = 0.1f,
    val randomizeGesturePath: Boolean = true,
    val pathRandomizationPixels: Int = 5
)

/**
 * Error Recovery settings
 */
data class ErrorRecoverySettings(
    val enableSmartRecovery: Boolean = true,
    val maxConsecutiveErrors: Int = 5,
    val maxStateStuckTimeMs: Long = 60000,
    val actionOnStuck: String = "restart",
    val screenshotOnError: Boolean = true,
    val autoRestartAfterErrors: Int = 10,
    val autoRestartDelayMs: Long = 3000
)

/**
 * End of List Detection settings
 */
data class EndOfListSettings(
    val enableEndOfListDetection: Boolean = true,
    val identicalPageThreshold: Int = 3,
    val maxCyclesBeforeStop: Int = 500,
    val textSimilarityThreshold: Float = 0.9f
)

/**
 * Immersive Mode settings
 */
data class ImmersiveModeSettings(
    val enableWindowVerification: Boolean = false,
    val gamePackageName: String = "com.albiononline",
    val actionOnWindowLost: String = "ignore",
    val windowCheckIntervalMs: Long = 999999999,
    val windowLostThreshold: Int = 999,
    val autoResumeOnReturn: Boolean = false
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
    val dimScreenDuringAutomation: Boolean = false,
    val dimBrightnessLevel: Int = 30
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

// ============================================
// SESSION STATISTICS
// ============================================

/**
 * Session Statistics - tracks automation progress
 */
data class SessionStatistics(
    val startTime: Long = System.currentTimeMillis(),
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
    val stateEnterTime: Long = 0,
    val lastPrice: Int? = null
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

// ============================================
// LOGGING
// ============================================

/**
 * Log level enum
 */
enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

/**
 * Log entry for in-memory logging
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "",
    val message: String = ""
)

// ============================================
// ROOM DATABASE ENTITIES
// ============================================

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
    val antiDetection: RandomizationSettings = RandomizationSettings(),
    
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
    val itemName: String = "",
    val price: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceMode: String = "",
    val wasSuccessful: Boolean = true,
    val sessionId: Long = 0
)

@Entity(tableName = "session_logs")
data class SessionLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionStart: Long = System.currentTimeMillis(),
    val sessionEnd: Long = 0,
    val mode: String = "",
    val totalCycles: Int = 0,
    val successfulOps: Int = 0,
    val failedOps: Int = 0,
    val errors: Int = 0,
    val exportPath: String = "",
    // Additional fields for StatisticsManager
    val itemsProcessed: Int = 0,
    val totalProfit: Long = 0,
    val averageCycleTime: Long = 0,
    val errorCount: Int = 0,
    val status: String = ""
)

// ============================================
// ENUMS AND OTHER TYPES
// ============================================

enum class OperationMode { IDLE, NEW_ORDER_SWEEPER, ORDER_EDITOR }

enum class StateType {
    IDLE, PAUSED, WAIT_POPUP_OPEN, SCAN_HIGHLIGHTS, SCAN_OCR,
    VERIFY_UI_ELEMENT, VERIFY_GAME_WINDOW, EXECUTE_TAP, TAP_PLUS_BUTTON,
    TAP_CONFIRM_BUTTON, TAP_EDIT_BUTTON, TAP_UPDATE_BUTTON,
    EXECUTE_TEXT_INPUT, EXECUTE_BUTTON, HANDLE_CONFIRMATION, HANDLE_ERROR_POPUP,
    WAIT_POPUP_CLOSE, SCROLL_NEXT_ROW, COMPLETE_ITERATION, ERROR_RETRY,
    ERROR_PRICE_SANITY, ERROR_TIMEOUT, ERROR_WINDOW_LOST, ERROR_STUCK_DETECTED,
    ERROR_END_OF_LIST, ERROR_BATTERY_LOW, COOLDOWN, RECOVERING, NAVIGATE_TO_MY_ORDERS
}

/**
 * Automation State - current state of the automation
 */
data class AutomationState(
    val stateType: StateType = StateType.IDLE,
    val mode: OperationMode = OperationMode.IDLE,
    val currentRowIndex: Int = 0,
    val currentX: Int = 0,
    val currentY: Int = 0,
    val itemsProcessed: Int = 0,
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

/**
 * OCR Result - text recognition result
 */
data class OCRResult(
    val text: String = "",
    val confidence: Float = 0f,
    val boundingBox: Rect = Rect(0, 0, 0, 0),
    val isNumber: Boolean = false,
    val numericValue: Int? = null
)

/**
 * Color Detection Result - color analysis result
 */
data class ColorDetectionResult(
    val isValid: Boolean = false,
    val detectedColor: String = "#000000",
    val confidence: Float = 0f,
    val regionX: Int = 0,
    val regionY: Int = 0,
    val regionWidth: Int = 0,
    val regionHeight: Int = 0,
    // Legacy fields for compatibility
    val hexColor: String = detectedColor,
    val matchConfidence: Float = confidence,
    val isMatch: Boolean = isValid
)

// ============================================
// RESULT DATA CLASSES
// ============================================

/**
 * Result of Create Order operation
 */
data class CreateOrderResult(
    val success: Boolean,
    val message: String
)

/**
 * Result of Edit Order operation
 */
data class EditOrderResult(
    val success: Boolean,
    val message: String,
    val newPrice: Int? = null,
    val isEndOfList: Boolean = false
)
