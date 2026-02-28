// FILE: app/src/main/java/com/albion/marketassistant/data/DataModels.kt
// UPDATED: Percentage-based coordinates for cross-device compatibility
// Target: iQOO Neo 6 (1080×2400) - but works on ANY screen size

package com.albion.marketassistant.data

import android.graphics.Rect
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded

/**
 * =====================================================
 * CREATE BUY ORDER MODE CONFIGURATION
 * =====================================================
 * 
 * Flow: User is on "Buy" tab with list of items
 * For each item:
 *   1. Tap "Buy Order" button (opens price panel)
 *   2. Albion auto-fills suggested price = current highest buy order
 *   3. Tap "+" icon to increment by 1 silver (outbids top order)
 *   4. Tap "Confirm" button
 *   5. Scroll to next item
 * 
 * ALL coordinates are PERCENTAGES (0.0 to 1.0)
 */
data class CreateModeConfig(
    // ==========================================
    // STEP 1: Tap "Buy Order" button on each row
    // Location: Right side of each item row in the Buy tab
    // iQOO Neo 6: ~82% from left, varies by row
    // ==========================================
    val buyOrderButtonXPercent: Double = 0.82,  // Right side of screen
    val firstRowYPercent: Double = 0.35,        // First item row
    val rowYOffsetPercent: Double = 0.10,       // Distance between rows
    val maxRowsPerScreen: Int = 6,              // How many rows before needing to scroll
    
    // ==========================================
    // STEP 2: Tap "+" icon (increment price by 1)
    // Location: Right side of price field in the popup
    // iQOO Neo 6: ~78% from left, ~62% from top
    // ==========================================
    val plusButtonXPercent: Double = 0.78,
    val plusButtonYPercent: Double = 0.62,
    
    // ==========================================
    // STEP 3: Tap "Confirm" button
    // Location: Bottom of the popup panel
    // iQOO Neo 6: ~75% from left, ~88% from top
    // ==========================================
    val confirmButtonXPercent: Double = 0.75,
    val confirmButtonYPercent: Double = 0.88,
    
    // ==========================================
    // STEP 4: Scroll to next item
    // Swipe from bottom to top to reveal more items
    // ==========================================
    val scrollStartYPercent: Double = 0.75,  // Start lower on screen
    val scrollEndYPercent: Double = 0.35,    // End higher on screen
    val scrollXPercent: Double = 0.50,       // Center horizontally
    
    // ==========================================
    // SAFETY SETTINGS
    // ==========================================
    val hardPriceCap: Int = 100000,           // Maximum price to prevent mistakes
    val priceIncrement: Int = 1,              // How much to add (+1 to outbid)
    val maxItemsToProcess: Int = 50,          // Maximum items to process in one run
    
    // OCR region for price reading (safety check)
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
 * =====================================================
 * EDIT BUY ORDER MODE CONFIGURATION
 * =====================================================
 * 
 * Flow: User is on "My Orders" tab
 * For each active buy order:
 *   1. Tap Edit (pencil) icon on the order
 *   2. Read the current top order price
 *   3. Calculate new price = top price + 1
 *   4. Input new price via AccessibilityNodeInfo (NO keyboard)
 *   5. Tap "Update" button (NOT "Confirm" - it's different in edit mode!)
 *   6. Return to list for next order
 * 
 * ALL coordinates are PERCENTAGES (0.0 to 1.0)
 */
data class EditModeConfig(
    // ==========================================
    // STEP 0: Navigate to "My Orders" tab
    // Location: Top-right area of market screen
    // iQOO Neo 6: ~90% from left, ~13% from top
    // ==========================================
    val myOrdersTabXPercent: Double = 0.90,
    val myOrdersTabYPercent: Double = 0.13,
    
    // ==========================================
    // STEP 1: Tap Edit (pencil) icon on first order
    // Location: Right side of the order row
    // iQOO Neo 6: ~85% from left, varies by row
    // CRITICAL: Always tap ROW 1 - editing pushes order to bottom
    // ==========================================
    val editButtonXPercent: Double = 0.85,
    val editButtonYPercent: Double = 0.55,    // First order row
    
    // ==========================================
    // STEP 2: Price field location (for reference)
    // Used by AccessibilityNodeInfo.setText() - no tap needed
    // ==========================================
    val priceFieldXPercent: Double = 0.50,
    val priceFieldYPercent: Double = 0.62,
    
    // ==========================================
    // STEP 4: Tap "Update" button
    // Location: Bottom of the edit panel
    // CRITICAL: This is "Update" NOT "Confirm" in edit mode!
    // iQOO Neo 6: ~75% from left, ~88% from top
    // ==========================================
    val updateButtonXPercent: Double = 0.75,
    val updateButtonYPercent: Double = 0.88,
    
    // ==========================================
    // Close button for error popups
    // ==========================================
    val closeButtonXPercent: Double = 0.95,
    val closeButtonYPercent: Double = 0.10,
    
    // ==========================================
    // SAFETY SETTINGS
    // ==========================================
    val hardPriceCap: Int = 100000,
    val priceIncrement: Int = 1,
    val maxOrdersToEdit: Int = 20,
    
    // OCR region for reading top order price
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
    // Delays after each action (in milliseconds)
    val delayAfterTapMs: Long = 900,          // Wait after tap for UI response
    val delayAfterSwipeMs: Long = 1200,       // Wait after scroll for list to settle
    val delayAfterConfirmMs: Long = 1100,     // Wait after confirming order
    val cycleCooldownMs: Long = 500,          // Extra delay between cycles
    
    // Gesture durations
    val tapDurationMs: Long = 80,             // How long tap lasts (80ms works for 3D games)
    val swipeDurationMs: Long = 400,          // How long swipe takes
    
    // Network lag compensation
    val networkLagMultiplier: Float = 1.0f,
    
    // OCR settings
    val ocrConfidenceThreshold: Float = 0.7f,
    val ocrLanguage: String = "en"
)

/**
 * Safety settings
 */
data class SafetySettings(
    val enablePriceCap: Boolean = true,
    val hardPriceCap: Int = 100000,
    val minPriceCap: Int = 1,
    val enableOcrSanityCheck: Boolean = true,
    val maxRetries: Int = 3,
    val autoDismissErrors: Boolean = true
)

/**
 * Anti-Detection settings
 */
data class AntiDetectionSettings(
    val enableRandomization: Boolean = true,
    val randomDelayRangeMs: Long = 200,
    val coordinateJitterPercent: Double = 0.02  // Add small random offset to coordinates
)

/**
 * End of List Detection settings
 */
data class EndOfListSettings(
    val enableEndOfListDetection: Boolean = true,
    val identicalPageThreshold: Int = 3,
    val maxCyclesBeforeStop: Int = 500
)

/**
 * Error Recovery settings
 */
data class ErrorRecoverySettings(
    val enableSmartRecovery: Boolean = true,
    val maxConsecutiveErrors: Int = 5,
    val autoRestartAfterErrors: Int = 10,
    val autoRestartDelayMs: Long = 3000
)

/**
 * Session Statistics
 */
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

// ============================================
// ENUMS AND OTHER TYPES
// ============================================

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
