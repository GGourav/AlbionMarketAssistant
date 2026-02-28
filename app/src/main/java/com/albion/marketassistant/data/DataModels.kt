package com.albion.marketassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import android.graphics.Rect

/**
 * Configuration for Create Buy Order mode
 */
data class CreateModeConfig(
    val buyOrderButtonXPercent: Double = 0.82,
    val firstRowYPercent: Double = 0.35,
    val rowYOffsetPercent: Double = 0.10,
    val maxRowsPerScreen: Int = 6,
    
    val plusButtonXPercent: Double = 0.78,
    val plusButtonYPercent: Double = 0.62,
    
    val confirmButtonXPercent: Double = 0.75,
    val confirmButtonYPercent: Double = 0.88,
    
    val scrollStartYPercent: Double = 0.75,
    val scrollEndYPercent: Double = 0.35,
    val scrollXPercent: Double = 0.50,
    
    val hardPriceCap: Int = 100000,
    val priceIncrement: Int = 1,
    val maxItemsToProcess: Int = 50,
    
    val ocrRegionLeftPercent: Double = 0.30,
    val ocrRegionTopPercent: Double = 0.40,
    val ocrRegionRightPercent: Double = 0.70,
    val ocrRegionBottomPercent: Double = 0.55
) {
    fun getOCRRegion(screenWidth: Int, screenHeight: Int): Rect = Rect(
        (screenWidth * ocrRegionLeftPercent).toInt(),
        (screenHeight * ocrRegionTopPercent).toInt(),
        (screenWidth * ocrRegionRightPercent).toInt(),
        (screenHeight * ocrRegionBottomPercent).toInt()
    )
}

/**
 * Configuration for Edit Buy Order mode
 */
data class EditModeConfig(
    val myOrdersTabXPercent: Double = 0.90,
    val myOrdersTabYPercent: Double = 0.13,
    
    val editButtonXPercent: Double = 0.85,
    val editButtonYPercent: Double = 0.55,
    
    val priceFieldXPercent: Double = 0.50,
    val priceFieldYPercent: Double = 0.62,
    
    val updateButtonXPercent: Double = 0.75,
    val updateButtonYPercent: Double = 0.88,
    
    val closeButtonXPercent: Double = 0.95,
    val closeButtonYPercent: Double = 0.10,
    
    val hardPriceCap: Int = 100000,
    val priceIncrement: Int = 1,
    val maxOrdersToEdit: Int = 20,
    
    val ocrRegionLeftPercent: Double = 0.30,
    val ocrRegionTopPercent: Double = 0.40,
    val ocrRegionRightPercent: Double = 0.70,
    val ocrRegionBottomPercent: Double = 0.55
) {
    fun getOCRRegion(screenWidth: Int, screenHeight: Int): Rect = Rect(
        (screenWidth * ocrRegionLeftPercent).toInt(),
        (screenHeight * ocrRegionTopPercent).toInt(),
        (screenWidth * ocrRegionRightPercent).toInt(),
        (screenHeight * ocrRegionBottomPercent).toInt()
    )
}

/**
 * Global timing settings
 */
data class GlobalSettings(
    val delayAfterTapMs: Long = 900,
    val delayAfterSwipeMs: Long = 1200,
    val delayAfterConfirmMs: Long = 1100,
    val cycleCooldownMs: Long = 500,
    
    val tapDurationMs: Long = 80,
    val swipeDurationMs: Long = 400,
    
    val networkLagMultiplier: Float = 1.0f,
    
    val ocrConfidenceThreshold: Float = 0.7f,
    val ocrLanguage: String = "en"
)

/**
 * Safety settings
 */
data class SafetySettings(
    val maxPriceCap: Int = 100000,
    val minPriceCap: Int = 1,
    val enableOcrSanityCheck: Boolean = true,
    val maxRetries: Int = 3,
    val uiTimeoutMs: Long = 5000,
    val autoDismissErrors: Boolean = true
)

/**
 * Anti-detection settings
 */
data class AntiDetectionSettings(
    val enableRandomization: Boolean = true,
    val randomDelayRangeMs: Long = 200,
    val coordinateJitterPercent: Double = 0.02
)

/**
 * End of list detection settings
 */
data class EndOfListSettings(
    val enableEndOfListDetection: Boolean = true,
    val identicalPageThreshold: Int = 3,
    val maxCyclesBeforeStop: Int = 500
)

/**
 * Error recovery settings
 */
data class ErrorRecoverySettings(
    val enableSmartRecovery: Boolean = true,
    val maxConsecutiveErrors: Int = 5,
    val maxStateStuckTimeMs: Long = 60000,
    val screenshotOnError: Boolean = true,
    val autoRestartAfterErrors: Int = 10,
    val autoRestartDelayMs: Long = 3000
)

/**
 * Session Statistics
 */
data class SessionStatistics(
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

/**
 * Room Entity for calibration data
 */
@Entity(tableName = "calibration_data")
data class CalibrationData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
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
    
    @Embedded(prefix = "errorrecovery_")
    val errorRecovery: ErrorRecoverySettings = ErrorRecoverySettings(),
    
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Room Entity for session logs
 */
@Entity(tableName = "session_logs")
data class SessionLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionStart: Long = System.currentTimeMillis(),
    val sessionEnd: Long = 0,
    val mode: String = "",
    val itemsProcessed: Int = 0,
    val totalProfit: Long = 0,
    val averageCycleTime: Long = 0,
    val errorCount: Int = 0,
    val status: String = "RUNNING"
)

/**
 * Room Entity for price history
 */
@Entity(tableName = "price_history")
data class PriceHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: String,
    val itemName: String = "",
    val price: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: Long = 0
)

/**
 * Operation mode enum
 */
enum class OperationMode { 
    IDLE, 
    NEW_ORDER_SWEEPER, 
    ORDER_EDITOR 
}

/**
 * State type enum
 */
enum class StateType {
    IDLE, PAUSED, 
    EXECUTE_TAP, 
    TAP_PLUS_BUTTON, 
    TAP_CONFIRM_BUTTON,
    TAP_UPDATE_BUTTON,
    TAP_EDIT_BUTTON,
    EXECUTE_TEXT_INPUT, 
    SCROLL_NEXT_ROW, 
    COMPLETE_ITERATION, 
    ERROR_RETRY,
    ERROR_PRICE_SANITY, 
    ERROR_END_OF_LIST, 
    ERROR_OCR_FAILED,
    COOLDOWN, 
    RECOVERING,
    NAVIGATE_TO_MY_ORDERS
}

/**
 * Automation state data class
 */
data class AutomationState(
    val stateType: StateType = StateType.IDLE,
    val mode: OperationMode = OperationMode.IDLE,
    val currentRowIndex: Int = 0,
    val itemsProcessed: Int = 0,
    val errorMessage: String? = null,
    val isPaused: Boolean = false,
    val lastPrice: Int? = null,
    val statistics: SessionStatistics = SessionStatistics()
)

/**
 * OCR Result
 */
data class OCRResult(
    val text: String = "",
    val confidence: Float = 0f,
    val boundingBox: Rect? = null,
    val isNumber: Boolean = false,
    val numericValue: Int? = null
)

/**
 * Color detection result
 */
data class ColorDetectionResult(
    val hexColor: String = "",
    val matchConfidence: Float = 0f,
    val isMatch: Boolean = false
)

/**
 * Log entry for debugging
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * Randomization settings for anti-detection
 */
data class RandomizationSettings(
    val minRandomDelayMs: Long = 50,
    val maxRandomDelayMs: Long = 200,
    val randomSwipeDistancePercent: Float = 0.05f,
    val pathRandomizationPixels: Int = 5,
    val randomizeGesturePath: Boolean = true
)

/**
 * Automation mode enum (for service)
 */
enum class AutomationMode {
    CREATE_BUY_ORDER,
    EDIT_BUY_ORDER
}

/**
 * Step result for state machine
 */
data class StepResult(
    val success: Boolean,
    val state: AutomationState,
    val message: String = "",
    val error: Throwable? = null,
    val delay: Long = 0
)

/**
 * Converters for Room
 */
class Converters {
    @androidx.room.TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString("|||")
    
    @androidx.room.TypeConverter
    fun toStringList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("|||")
}
