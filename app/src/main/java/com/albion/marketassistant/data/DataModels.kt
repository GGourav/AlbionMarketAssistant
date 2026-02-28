package com.albion.marketassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Room Entity for storing calibration data
 */
@Entity(tableName = "calibration_data")
@TypeConverters(Converters::class)
data class CalibrationData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // CREATE mode coordinates (percentage-based 0.0-1.0)
    val firstRowX: Float = 0.5f,
    val firstRowY: Float = 0.25f,
    val rowYOffset: Float = 0.08f,
    val plusButtonX: Float = 0.85f,
    val plusButtonY: Float = 0.25f,
    val createButtonX: Float = 0.5f,
    val createButtonY: Float = 0.9f,
    val confirmYesX: Float = 0.35f,
    val confirmYesY: Float = 0.55f,
    val closeButtonX: Float = 0.85f,
    val closeButtonY: Float = 0.15f,
    
    // OCR Region for CREATE mode
    val ocrRegionLeft: Float = 0.55f,
    val ocrRegionTop: Float = 0.22f,
    val ocrRegionRight: Float = 0.75f,
    val ocrRegionBottom: Float = 0.28f,
    
    // Swipe settings for CREATE mode
    val swipeStartX: Float = 0.5f,
    val swipeStartY: Float = 0.7f,
    val swipeEndX: Float = 0.5f,
    val swipeEndY: Float = 0.35f,
    
    // EDIT mode coordinates
    val editButtonX: Float = 0.85f,
    val editButtonY: Float = 0.25f,
    val priceInputX: Float = 0.5f,
    val priceInputY: Float = 0.45f,
    val updateButtonX: Float = 0.75f,
    val updateButtonY: Float = 0.55f,
    
    // OCR Region for EDIT mode
    val editOcrRegionLeft: Float = 0.55f,
    val editOcrRegionTop: Float = 0.22f,
    val editOcrRegionRight: Float = 0.75f,
    val editOcrRegionBottom: Float = 0.28f,
    
    // Pricing settings
    val hardPriceCap: Int = 50000,
    val maxRows: Int = 5,
    val priceIncrement: Int = 100,
    
    // Timing settings
    val loopDelayMs: Long = 500,
    val gestureDurationMs: Long = 200,
    
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Price history entry for tracking price changes
 */
@Entity(tableName = "price_history")
data class PriceHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: String,
    val price: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: Long = 0,
    val cityName: String = ""
)

/**
 * Session log entry for tracking automation sessions
 */
@Entity(tableName = "session_logs")
data class SessionLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionStart: Long,
    var sessionEnd: Long = 0,
    var itemsProcessed: Int = 0,
    var profitSilver: Long = 0,
    var status: String = "RUNNING",
    var errorMessage: String = ""
)

/**
 * Randomization settings for anti-detection
 */
data class RandomizationSettings(
    val minRandomDelayMs: Long = 50,
    val maxRandomDelayMs: Long = 150,
    val randomSwipeDistancePercent: Float = 0.05f,
    val pathRandomizationPixels: Int = 5,
    val randomizeGesturePath: Boolean = true
)

/**
 * Session statistics for tracking automation performance
 */
data class SessionStatistics(
    val startTime: Long = System.currentTimeMillis(),
    var priceUpdates: Int = 0,
    var timeSavedMs: Long = 0,
    var estimatedProfitSilver: Long = 0,
    var sessionDurationMs: Long = 0,
    var averageCycleTimeMs: Long = 0,
    var lastCycleTime: Long = 0,
    var lastState: String = "IDLE",
    var stateEnterTime: Long = System.currentTimeMillis()
) {
    fun getSessionDurationFormatted(): String {
        val duration = System.currentTimeMillis() - startTime
        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}

data class ColorDetectionResult(
    val isValid: Boolean,
    val detectedColor: String,
    val confidence: Float,
    val regionX: Int,
    val regionY: Int,
    val regionWidth: Int,
    val regionHeight: Int
)

data class OcrResult(
    val text: String,
    val confidence: Float,
    val boundingBox: BoundingBox? = null
)

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

enum class AutomationState {
    IDLE,
    INITIALIZING,
    SCANNING,
    PROCESSING_ROW,
    TAPPING_PLUS,
    CONFIRMING,
    SCROLLING,
    EDITING_ORDER,
    UPDATING_PRICE,
    HANDLING_ERROR,
    COMPLETED,
    STOPPED
}

enum class AutomationMode {
    CREATE_BUY_ORDER,
    EDIT_BUY_ORDER
}

data class StepResult(
    val success: Boolean,
    val state: AutomationState,
    val message: String = "",
    val error: Throwable? = null,
    val delay: Long = 0
)

data class PriceResult(
    val originalPrice: Int,
    val newPrice: Int,
    val profitEstimate: Long = 0
)

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class AppSettings(
    val debugMode: Boolean = false,
    val autoStart: Boolean = false,
    val notificationEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false
)

data class CoordinatePoint(
    val x: Float,
    val y: Float
) {
    fun toAbsolute(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        return (x * screenWidth).toInt() to (y * screenHeight).toInt()
    }
}

data class GestureResult(
    val success: Boolean,
    val gestureType: GestureType,
    val duration: Long,
    val stepName: String = ""
)

enum class GestureType {
    TAP,
    LONG_TAP,
    SWIPE,
    SCROLL,
    DRAG
}

data class SetPriceResult(
    val success: Boolean,
    val errorMessage: String = "",
    val failedStep: String = ""
)
