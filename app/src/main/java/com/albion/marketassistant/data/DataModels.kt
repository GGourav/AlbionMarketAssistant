// FILE: app/src/main/java/com/albion/marketassistant/data/DataModels.kt

package com.albion.marketassistant.data

import android.graphics.Rect
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded

/**
 * Configuration for Create Buy Order mode
 * MODE 1: FAST LOOP - Tap + button to outbid
 */
data class CreateModeConfig(
    // Row coordinates for tapping market rows
    val firstRowX: Int = 100,
    val firstRowY: Int = 300,
    val rowYOffset: Int = 80,
    val maxRowsPerScreen: Int = 5,

    // PLUS BUTTON coordinates (increment button next to price)
    val plusButtonX: Int = 350,
    val plusButtonY: Int = 450,

    // Create Order button coordinates
    val createButtonX: Int = 500,
    val createButtonY: Int = 550,

    // OCR scan region for price reading (safety check only)
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
    
    // Hard Price Cap - SAFETY KILL-SWITCH
    val hardPriceCap: Int = 100000,
    
    // Price increment (usually 1)
    val priceIncrement: Int = 1
) {
    fun getOCRRegion(): Rect = Rect(ocrRegionLeft, ocrRegionTop, ocrRegionRight, ocrRegionBottom)
}

/**
 * Configuration for Edit Buy Order mode
 * MODE 2: READ & TYPE LOOP - OCR price, inject new price
 */
data class EditModeConfig(
    // Edit button coordinates (ONLY ROW 1 - no iteration)
    val editButtonX: Int = 950,
    val editButtonY: Int = 300,

    // Price Input Box coordinates (for reference only)
    val priceInputX: Int = 300,
    val priceInputY: Int = 400,

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
    val closeButtonY: Int = 200,
    
    // Hard Price Cap - SAFETY KILL-SWITCH
    val hardPriceCap: Int = 100000,
    
    // Price increment (usually 1)
    val priceIncrement: Int = 1
) {
    fun getOCRRegion(): Rect = Rect(ocrRegionLeft, ocrRegionTop, ocrRegionRight, ocrRegionBottom)
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
    val tapDurationMs: Long = 200,
    val textInputDelayMs: Long = 200,
    val popupOpenWaitMs: Long = 300,
    val popupCloseWaitMs: Long = 400,
    val confirmationWaitMs: Long = 400,
    val ocrScanDelayMs: Long = 300,
    val keyboardDismissDelayMs: Long = 300,
    val networkLagMultiplier: Float = 1.0f,
    val cycleCooldownMs: Long = 200,
    val highlightedRowColorHex: String = "#E8E8E8",
    val colorToleranceRGB: Int = 30,
    val ocrConfidenceThreshold: Float = 0.7f,
    val ocrLanguage: String = "en"
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
 * Anti-Detection settings
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
 * Immersive Mode settings
 * CRITICAL OVERHAUL 3: DISABLED by default
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
    val stateEnterTime: Long = 0,
    val lastDetectedPrice: Int? = null
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

// ============================================
// ENUMS AND OTHER TYPES
// ============================================

enum class OperationMode { IDLE, NEW_ORDER_SWEEPER, ORDER_EDITOR }

enum class StateType {
    IDLE, PAUSED, WAIT_POPUP_OPEN, SCAN_HIGHLIGHTS, SCAN_OCR,
    VERIFY_UI_ELEMENT, VERIFY_GAME_WINDOW, EXECUTE_TAP, TAP_PLUS_BUTTON,
    EXECUTE_TEXT_INPUT, EXECUTE_BUTTON, HANDLE_CONFIRMATION, HANDLE_ERROR_POPUP,
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

// ============================================
// AUTOMATION CONFIG FOR CALIBRATION ACTIVITY
// Simple config classes for SharedPreferences JSON storage
// ============================================

data class AutomationConfig(
    val createMode: SimpleCreateModeConfig = SimpleCreateModeConfig(),
    val editMode: SimpleEditModeConfig = SimpleEditModeConfig(),
    val ocrRegionLeft: Int = 400,
    val ocrRegionTop: Int = 500,
    val ocrRegionRight: Int = 700,
    val ocrRegionBottom: Int = 700,
    val loopDelayMs: Long = 500L,
    val gestureDurationMs: Long = 200L
)

data class SimpleCreateModeConfig(
    val rowStartX: Int = 540,
    val rowStartY: Int = 400,
    val rowEndX: Int = 540,
    val rowEndY: Int = 1800,
    val rowHeight: Int = 120,
    val plusButtonX: Int = 800,
    val plusButtonY: Int = 600,
    val hardPriceCap: Long = 100000000L,
    val maxRows: Int = 8,
    val swipeX: Int = 540,
    val swipeY: Int = 1500,
    val swipeDistance: Int = 300
)

data class SimpleEditModeConfig(
    val row1X: Int = 540,
    val row1Y: Int = 400,
    val priceInputX: Int = 650,
    val priceInputY: Int = 600,
    val hardPriceCap: Long = 100000000L,
    val priceIncrement: Long = 1L,
    val createButtonX: Int = 900,
    val createButtonY: Int = 600,
    val confirmButtonX: Int = 540,
    val confirmButtonY: Int = 1200
)
