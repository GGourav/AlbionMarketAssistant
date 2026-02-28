// FILE: app/src/main/java/com/albion/marketassistant/data/DataModels.kt
// UPDATED: v3 - Percentage-based coordinates

package com.albion.marketassistant.data

import android.graphics.Rect
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded

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

data class SafetySettings(
    val enablePriceCap: Boolean = true,
    val hardPriceCap: Int = 100000,
    val minPriceCap: Int = 1,
    val enableOcrSanityCheck: Boolean = true,
    val maxRetries: Int = 3,
    val autoDismissErrors: Boolean = true
)

data class AntiDetectionSettings(
    val enableRandomization: Boolean = true,
    val randomDelayRangeMs: Long = 200,
    val coordinateJitterPercent: Double = 0.02
)

data class EndOfListSettings(
    val enableEndOfListDetection: Boolean = true,
    val identicalPageThreshold: Int = 3,
    val maxCyclesBeforeStop: Int = 500
)

data class ErrorRecoverySettings(
    val enableSmartRecovery: Boolean = true,
    val maxConsecutiveErrors: Int = 5,
    val autoRestartAfterErrors: Int = 10,
    val autoRestartDelayMs: Long = 3000
)

data class SessionStatistics(
    val sessionStartTime: Long = System.currentTimeMillis(),
    val totalCycles: Int = 0,
    val successfulOperations: Int = 0,
    val failedOperations: Int = 0,
    val ordersCreated: Int = 0,
    val ordersEdited: Int = 0,
    val errorsEncountered: Int = 0,
    val consecutiveErrors: Int = 0,
    val lastPrice: Int? = null
) {
    fun getSuccessRate(): Float {
        val total = successfulOperations + failedOperations
        return if (total > 0) successfulOperations.toFloat() / total else 0f
    }
    
    fun getSessionDurationFormatted(): String {
        val durationMs = System.currentTimeMillis() - sessionStartTime
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = durationMs / (1000 * 60 * 60)
        return when {
            hours > 0 -> String.format("%dh %dm %ds", hours, minutes, seconds)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds)
            else -> String.format("%ds", seconds)
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
    
    @Embedded(prefix = "errorrecovery_")
    val errorRecovery: ErrorRecoverySettings = ErrorRecoverySettings(),

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

data class AutomationState(
    val stateType: StateType = StateType.IDLE,
    val mode: OperationMode = OperationMode.IDLE,
    val currentRowIndex: Int = 0,
    val itemsProcessed: Int = 0,
    val errorMessage: String? = null,
    val isPaused: Boolean = false,
    val lastPrice: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val statistics: SessionStatistics = SessionStatistics()
)

data class OCRResult(
    val text: String = "",
    val confidence: Float = 0f,
    val boundingBox: Rect = Rect(0, 0, 0, 0),
    val isNumber: Boolean = false,
    val numericValue: Int? = null
)
