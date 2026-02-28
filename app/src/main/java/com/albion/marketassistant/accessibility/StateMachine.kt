// FILE: app/src/main/java/com/albion/marketassistant/accessibility/StateMachine.kt
// UPDATED: Correct +1 outbid flow using percentage-based GestureHelper
// Target: iQOO Neo 6 (1080×2400) - works on any screen

package com.albion.marketassistant.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.albion.marketassistant.data.*
import com.albion.marketassistant.helper.GestureHelper
import com.albion.marketassistant.ml.OCREngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * =====================================================
 * STATE MACHINE - Albion Online Market Assistant
 * =====================================================
 * 
 * CREATE BUY ORDER MODE:
 *   User is on "Buy" tab with list of items
 *   For each item:
 *     1. Tap "Buy Order" button (opens price panel)
 *     2. Albion auto-fills suggested price
 *     3. Tap "+" icon to increment by 1 silver
 *     4. Tap "Confirm" button
 *     5. Scroll to next item
 * 
 * EDIT BUY ORDER MODE:
 *   User starts on "Buy" tab
 *   For each active order:
 *     1. Navigate to "My Orders" tab
 *     2. Tap Edit (pencil) icon on first order
 *     3. Read top order price via OCR
 *     4. Calculate new price = top price + 1
 *     5. Set price via AccessibilityNodeInfo (NO keyboard)
 *     6. Tap "Update" button
 *     7. Return to list for next order
 */
class StateMachine(
    private val scope: CoroutineScope,
    private val calibration: CalibrationData,
    private val accessibilityService: MarketAccessibilityService,
    private val context: Context? = null
) {

    companion object {
        private const val TAG = "StateMachine"
    }

    private val _stateFlow = MutableStateFlow<AutomationState>(AutomationState(StateType.IDLE))
    val stateFlow: StateFlow<AutomationState> = _stateFlow

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var currentMode = OperationMode.IDLE
    private var currentRowIndex = 0
    private var itemsProcessed = AtomicInteger(0)
    private var consecutiveErrors = AtomicInteger(0)
    private var lastKnownPrice: Int? = null
    private var loopJob: Job? = null

    private lateinit var gestureHelper: GestureHelper

    var onStateChange: ((AutomationState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onPriceSanityError: ((String) -> Unit)? = null
    var onEndOfList: (() -> Unit)? = null
    var onProgressUpdate: ((Int, Int) -> Unit)? = null  // (processed, total)
    var onScreenshotRequest: (() -> Bitmap?)? = null

    fun startMode(mode: OperationMode) {
        if (isRunning.get()) {
            onError?.invoke("Already running")
            return
        }
        
        // Initialize gesture helper
        gestureHelper = GestureHelper(accessibilityService)
        
        Log.i(TAG, "========================================")
        Log.i(TAG, "STARTING MODE: $mode")
        Log.i(TAG, "Screen Info: ${gestureHelper.getScreenInfo()}")
        Log.i(TAG, "========================================")
        
        isRunning.set(true)
        isPaused.set(false)
        currentMode = mode
        currentRowIndex = 0
        itemsProcessed.set(0)
        consecutiveErrors.set(0)
        lastKnownPrice = null

        loopJob = scope.launch { mainLoop() }
    }

    fun pause() {
        isPaused.set(true)
        updateState(StateType.PAUSED, "Paused")
        Log.d(TAG, "Paused")
    }

    fun resume() {
        isPaused.set(false)
        updateState(StateType.IDLE, "Resumed")
        Log.d(TAG, "Resumed")
    }

    fun isPaused(): Boolean = isPaused.get()

    fun stop() {
        Log.d(TAG, "Stopping state machine")
        isRunning.set(false)
        isPaused.set(false)
        loopJob?.cancel()
        loopJob = null
        currentMode = OperationMode.IDLE
        _stateFlow.value = AutomationState(StateType.IDLE)
    }

    private suspend fun mainLoop() {
        try {
            when (currentMode) {
                OperationMode.NEW_ORDER_SWEEPER -> executeCreateBuyOrderLoop()
                OperationMode.ORDER_EDITOR -> executeEditBuyOrderLoop()
                OperationMode.IDLE -> return
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Main loop cancelled")
        } catch (e: Exception) {
            isRunning.set(false)
            onError?.invoke("Error: ${e.message}")
            Log.e(TAG, "Main loop error", e)
        }
    }

    // =====================================================
    // MODE 1: CREATE BUY ORDER (OUTBID +1 SILVER LOOP)
    // =====================================================
    
    private suspend fun executeCreateBuyOrderLoop() {
        val config = calibration.createMode
        val timing = calibration.global
        val maxItems = config.maxItemsToProcess
        
        Log.i(TAG, "CREATE MODE: Starting outbid loop for max $maxItems items")
        
        for (i in 0 until maxItems) {
            if (!isRunning.get()) break
            while (isPaused.get()) delay(100)
            
            try {
                val result = createSingleBuyOrder(config, timing, i)
                
                if (result.success) {
                    itemsProcessed.incrementAndGet()
                    consecutiveErrors.set(0)
                    
                    val processed = itemsProcessed.get()
                    onProgressUpdate?.invoke(processed, maxItems)
                    
                    updateState(StateType.COMPLETE_ITERATION, 
                        "Item $processed created (price +1 silver)")
                    
                    // Scroll to next item
                    delay(timing.cycleCooldownMs)
                    scrollToNextItem(config, timing)
                    
                } else {
                    val errors = consecutiveErrors.incrementAndGet()
                    if (errors >= calibration.errorRecovery.maxConsecutiveErrors) {
                        updateState(StateType.ERROR_RETRY, "Too many errors, stopping")
                        break
                    }
                    Log.w(TAG, "CREATE: Failed on item $i, error count: $errors")
                    delay(2000) // Wait before retry
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "CREATE: Error on item $i", e)
                consecutiveErrors.incrementAndGet()
                delay(2000)
            }
        }
        
        val total = itemsProcessed.get()
        Log.i(TAG, "CREATE MODE COMPLETE: $total orders created")
        updateState(StateType.COMPLETE_ITERATION, "Complete: $total orders created")
        stop()
    }
    
    private suspend fun createSingleBuyOrder(
        config: CreateModeConfig, 
        timing: GlobalSettings,
        itemIndex: Int
    ): CreateOrderResult {
        
        // ==========================================
        // STEP 1: Calculate row position and tap "Buy Order" button
        // ==========================================
        val rowY = config.firstRowYPercent + (currentRowIndex * config.rowYOffsetPercent)
        
        updateState(StateType.EXECUTE_TAP, "CREATE: Tapping Buy Order button (row $currentRowIndex)")
        Log.d(TAG, "CREATE: Tap Buy Order at (${config.buyOrderButtonXPercent}, $rowY)")
        
        if (!gestureHelper.tap(config.buyOrderButtonXPercent, rowY, timing.delayAfterTapMs)) {
            return CreateOrderResult(false, "Failed to tap Buy Order button")
        }
        
        // ==========================================
        // STEP 2: Tap "+" icon to increment price by 1
        // Location: Right side of price field in popup
        // ==========================================
        updateState(StateType.TAP_PLUS_BUTTON, "CREATE: Tapping + icon to outbid by 1 silver")
        Log.d(TAG, "CREATE: Tap + icon at (${config.plusButtonXPercent}, ${config.plusButtonYPercent})")
        
        if (!gestureHelper.tap(config.plusButtonXPercent, config.plusButtonYPercent, timing.delayAfterTapMs)) {
            return CreateOrderResult(false, "Failed to tap + button")
        }
        
        // ==========================================
        // STEP 3: Tap "Confirm" button
        // Location: Bottom of popup panel
        // ==========================================
        updateState(StateType.TAP_CONFIRM_BUTTON, "CREATE: Tapping Confirm button")
        Log.d(TAG, "CREATE: Tap Confirm at (${config.confirmButtonXPercent}, ${config.confirmButtonYPercent})")
        
        if (!gestureHelper.tap(config.confirmButtonXPercent, config.confirmButtonYPercent, timing.delayAfterConfirmMs)) {
            return CreateOrderResult(false, "Failed to tap Confirm button")
        }
        
        return CreateOrderResult(true, "Order created successfully")
    }
    
    private suspend fun scrollToNextItem(config: CreateModeConfig, timing: GlobalSettings) {
        currentRowIndex++
        
        if (currentRowIndex >= config.maxRowsPerScreen) {
            updateState(StateType.SCROLL_NEXT_ROW, "CREATE: Scrolling to next items")
            Log.d(TAG, "CREATE: Scroll from ${config.scrollStartYPercent} to ${config.scrollEndYPercent}")
            
            gestureHelper.swipeDownToUp(
                startYPercent = config.scrollStartYPercent,
                endYPercent = config.scrollEndYPercent,
                xPercent = config.scrollXPercent,
                delayAfterMs = timing.delayAfterSwipeMs
            )
            
            currentRowIndex = 0
        }
    }

    // =====================================================
    // MODE 2: EDIT BUY ORDER (READ PRICE + UPDATE LOOP)
    // =====================================================
    
    private suspend fun executeEditBuyOrderLoop() {
        val config = calibration.editMode
        val timing = calibration.global
        val maxOrders = config.maxOrdersToEdit
        
        Log.i(TAG, "EDIT MODE: Starting edit loop for max $maxOrders orders")
        
        // ==========================================
        // STEP 0: Navigate to "My Orders" tab
        // ==========================================
        updateState(StateType.NAVIGATE_TO_MY_ORDERS, "EDIT: Navigating to My Orders tab")
        Log.d(TAG, "EDIT: Tap My Orders at (${config.myOrdersTabXPercent}, ${config.myOrdersTabYPercent})")
        
        if (!gestureHelper.tap(config.myOrdersTabXPercent, config.myOrdersTabYPercent, 1000)) {
            updateState(StateType.ERROR_RETRY, "EDIT: Failed to navigate to My Orders tab")
            stop()
            return
        }
        
        for (i in 0 until maxOrders) {
            if (!isRunning.get()) break
            while (isPaused.get()) delay(100)
            
            try {
                val result = editSingleOrder(config, timing, i)
                
                if (result.success) {
                    itemsProcessed.incrementAndGet()
                    consecutiveErrors.set(0)
                    
                    val processed = itemsProcessed.get()
                    onProgressUpdate?.invoke(processed, maxOrders)
                    
                    updateState(StateType.COMPLETE_ITERATION, 
                        "Order $processed updated to ${result.newPrice} silver")
                    
                    delay(timing.cycleCooldownMs)
                    
                } else if (result.isEndOfList) {
                    Log.i(TAG, "EDIT: End of orders list reached")
                    updateState(StateType.ERROR_END_OF_LIST, "All orders processed")
                    break
                    
                } else {
                    val errors = consecutiveErrors.incrementAndGet()
                    if (errors >= calibration.errorRecovery.maxConsecutiveErrors) {
                        updateState(StateType.ERROR_RETRY, "Too many errors, stopping")
                        break
                    }
                    Log.w(TAG, "EDIT: Failed on order $i, error count: $errors")
                    delay(2000)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "EDIT: Error on order $i", e)
                consecutiveErrors.incrementAndGet()
                delay(2000)
            }
        }
        
        val total = itemsProcessed.get()
        Log.i(TAG, "EDIT MODE COMPLETE: $total orders updated")
        updateState(StateType.COMPLETE_ITERATION, "Complete: $total orders updated")
        stop()
    }
    
    private suspend fun editSingleOrder(
        config: EditModeConfig,
        timing: GlobalSettings,
        orderIndex: Int
    ): EditOrderResult {
        
        // ==========================================
        // STEP 1: Tap Edit (pencil) icon on first order
        // CRITICAL: Always tap ROW 1 - editing pushes order to bottom
        // ==========================================
        updateState(StateType.TAP_EDIT_BUTTON, "EDIT: Tapping Edit button on first order")
        Log.d(TAG, "EDIT: Tap Edit at (${config.editButtonXPercent}, ${config.editButtonYPercent})")
        
        if (!gestureHelper.tap(config.editButtonXPercent, config.editButtonYPercent, timing.delayAfterTapMs)) {
            return EditOrderResult(false, "Failed to tap Edit button")
        }
        
        // ==========================================
        // STEP 2: Read current top order price via OCR
        // ==========================================
        updateState(StateType.COOLDOWN, "EDIT: Reading top price via OCR")
        val topPrice = readTopOrderPrice(config)
        
        if (topPrice == null) {
            // OCR failed - might be end of list or OCR error
            Log.w(TAG, "EDIT: OCR returned null - treating as end of list")
            
            // Close any popup
            gestureHelper.tap(config.closeButtonXPercent, config.closeButtonYPercent, 500)
            
            return EditOrderResult(false, "OCR failed", isEndOfList = true)
        }
        
        // ==========================================
        // STEP 3: Calculate new price = top price + 1
        // ==========================================
        val newPrice = topPrice + config.priceIncrement
        Log.i(TAG, "EDIT: Top price=$topPrice, New price=$newPrice")
        
        // Safety check
        if (newPrice > config.hardPriceCap) {
            updateState(StateType.ERROR_PRICE_SANITY, 
                "SAFETY HALT: Price $newPrice > cap ${config.hardPriceCap}")
            onPriceSanityError?.invoke("Price $newPrice exceeds cap ${config.hardPriceCap}")
            
            // Close popup
            gestureHelper.tap(config.closeButtonXPercent, config.closeButtonYPercent, 500)
            
            return EditOrderResult(false, "Price exceeds cap")
        }
        
        // ==========================================
        // STEP 4: Set new price via AccessibilityNodeInfo
        // CRITICAL: Uses setText() - NO on-screen keyboard!
        // ==========================================
        updateState(StateType.EXECUTE_TEXT_INPUT, "EDIT: Setting price to $newPrice")
        Log.d(TAG, "EDIT: Set price field to $newPrice")
        
        if (!gestureHelper.setPriceField(newPrice.toString())) {
            Log.w(TAG, "EDIT: Failed to set price field, retrying...")
            delay(500)
            
            // Retry once
            if (!gestureHelper.setPriceField(newPrice.toString())) {
                return EditOrderResult(false, "Failed to set price field")
            }
        }
        
        lastKnownPrice = newPrice
        delay(timing.delayAfterTapMs)
        
        // ==========================================
        // STEP 5: Tap "Update" button
        // CRITICAL: This is "Update" NOT "Confirm" in edit mode!
        // ==========================================
        updateState(StateType.TAP_UPDATE_BUTTON, "EDIT: Tapping Update button")
        Log.d(TAG, "EDIT: Tap Update at (${config.updateButtonXPercent}, ${config.updateButtonYPercent})")
        
        if (!gestureHelper.tap(config.updateButtonXPercent, config.updateButtonYPercent, timing.delayAfterConfirmMs)) {
            return EditOrderResult(false, "Failed to tap Update button")
        }
        
        return EditOrderResult(true, "Order updated", newPrice)
    }
    
    /**
     * Read the top order price from the edit panel via OCR
     */
    private suspend fun readTopOrderPrice(config: EditModeConfig): Int? {
        val bitmap = onScreenshotRequest?.invoke() ?: return null
        
        // Get screen dimensions for percentage-based region
        val screenWidth = bitmap.width
        val screenHeight = bitmap.height
        
        val ocrRegion = Rect(
            (screenWidth * config.ocrRegionLeftPercent).toInt(),
            (screenHeight * config.ocrRegionTopPercent).toInt(),
            (screenWidth * config.ocrRegionRightPercent).toInt(),
            (screenHeight * config.ocrRegionBottomPercent).toInt()
        )
        
        return try {
            val results = OCREngine.recognizeText(bitmap, ocrRegion)
            val price = results
                .filter { it.isNumber && it.numericValue != null }
                .maxByOrNull { it.confidence }
                ?.numericValue
            
            if (price != null) {
                Log.d(TAG, "OCR: Detected price $price")
            }
            price
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            null
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================
    
    private fun updateState(stateType: StateType, message: String = "") {
        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            itemsProcessed = itemsProcessed.get(),
            errorMessage = if (stateType.name.startsWith("ERROR")) message else null,
            isPaused = isPaused.get(),
            lastPrice = lastKnownPrice,
            statistics = SessionStatistics(
                successfulOperations = itemsProcessed.get(),
                consecutiveErrors = consecutiveErrors.get(),
                lastPrice = lastKnownPrice
            )
        )
        
        _stateFlow.value = state
        onStateChange?.invoke(state)
    }
    
    fun getStatistics(): SessionStatistics {
        return SessionStatistics(
            successfulOperations = itemsProcessed.get(),
            consecutiveErrors = consecutiveErrors.get(),
            lastPrice = lastKnownPrice
        )
    }
}

// =====================================================
// RESULT DATA CLASSES
// =====================================================

data class CreateOrderResult(
    val success: Boolean,
    val message: String
)

data class EditOrderResult(
    val success: Boolean,
    val message: String,
    val newPrice: Int? = null,
    val isEndOfList: Boolean = false
)
