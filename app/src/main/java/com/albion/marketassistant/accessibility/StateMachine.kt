// FILE: app/src/main/java/com/albion/marketassistant/accessibility/StateMachine.kt
// UPDATED: v3 - Fixed HANDLE_ERROR_POPUP with step logging + correct +1 outbid flow

package com.albion.marketassistant.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.albion.marketassistant.data.*
import com.albion.marketassistant.helper.GestureHelper
import com.albion.marketassistant.helper.SetPriceResult
import com.albion.marketassistant.ml.OCREngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StateMachine(
    private val scope: CoroutineScope,
    private val calibration: CalibrationData,
    private val accessibilityService: MarketAccessibilityService,
    private val context: Context? = null,
    private val debugMode: Boolean = false
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
    private var lastStepName = "Not started"

    private lateinit var gestureHelper: GestureHelper
    private val mainHandler = Handler(Looper.getMainLooper())

    var onStateChange: ((AutomationState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onPriceSanityError: ((String) -> Unit)? = null
    var onEndOfList: (() -> Unit)? = null
    var onProgressUpdate: ((Int, Int) -> Unit)? = null
    var onScreenshotRequest: (() -> Bitmap?)? = null
    var onStepUpdate: ((String) -> Unit)? = null

    fun startMode(mode: OperationMode) {
        if (isRunning.get()) {
            onError?.invoke("Already running")
            return
        }
        
        gestureHelper = GestureHelper(accessibilityService, debugMode) { step ->
            lastStepName = step
            onStepUpdate?.invoke(step)
        }
        
        Log.i(TAG, "========================================")
        Log.i(TAG, "STARTING MODE: $mode")
        Log.i(TAG, "Screen Info: ${gestureHelper.getScreenInfo()}")
        Log.i(TAG, "Debug Mode: $debugMode")
        Log.i(TAG, "========================================")
        
        showToast("Starting $mode")
        
        isRunning.set(true)
        isPaused.set(false)
        currentMode = mode
        currentRowIndex = 0
        itemsProcessed.set(0)
        consecutiveErrors.set(0)
        lastKnownPrice = null
        lastStepName = "Initializing"

        loopJob = scope.launch { mainLoop() }
    }

    fun pause() {
        isPaused.set(true)
        updateState(StateType.PAUSED, "Paused")
        showToast("Paused")
    }

    fun resume() {
        isPaused.set(false)
        updateState(StateType.IDLE, "Resumed")
        showToast("Resumed")
    }

    fun isPaused(): Boolean = isPaused.get()

    fun stop() {
        isRunning.set(false)
        isPaused.set(false)
        loopJob?.cancel()
        loopJob = null
        currentMode = OperationMode.IDLE
        _stateFlow.value = AutomationState(StateType.IDLE)
    }

    fun getLastStep(): String = lastStepName

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
            val errorMsg = buildErrorMessage("Main loop crashed", e)
            Log.e(TAG, errorMsg)
            onError?.invoke(errorMsg)
        }
    }

    private suspend fun executeCreateBuyOrderLoop() {
        val config = calibration.createMode
        val timing = calibration.global
        val maxItems = if (debugMode) 1 else config.maxItemsToProcess
        
        Log.i(TAG, "CREATE MODE: Starting outbid loop for max $maxItems items")
        
        for (i in 0 until maxItems) {
            if (!isRunning.get()) break
            while (isPaused.get()) delay(100)
            
            val itemNum = i + 1
            
            try {
                val result = createSingleBuyOrder(config, timing, itemNum)
                
                if (result.success) {
                    itemsProcessed.incrementAndGet()
                    consecutiveErrors.set(0)
                    
                    val processed = itemsProcessed.get()
                    onProgressUpdate?.invoke(processed, maxItems)
                    
                    updateState(StateType.COMPLETE_ITERATION, 
                        "Item $processed created (+1 silver outbid)")
                    showToast("Item $processed done")
                    
                    delay(timing.cycleCooldownMs)
                    scrollToNextItem(config, timing, itemNum)
                    
                } else {
                    val errorMsg = result.message
                    handleStepError("CREATE item $itemNum", errorMsg)
                    
                    val errors = consecutiveErrors.incrementAndGet()
                    if (errors >= calibration.errorRecovery.maxConsecutiveErrors) {
                        val fullError = "Too many errors ($errors). Last failure: $errorMsg"
                        updateState(StateType.ERROR_RETRY, fullError)
                        onError?.invoke(fullError)
                        break
                    }
                    delay(2000)
                }
                
            } catch (e: Exception) {
                val errorMsg = buildErrorMessage("CREATE item $itemNum crashed at step: $lastStepName", e)
                handleStepError("CREATE item $itemNum", errorMsg)
                consecutiveErrors.incrementAndGet()
                delay(2000)
            }
        }
        
        val total = itemsProcessed.get()
        Log.i(TAG, "CREATE MODE COMPLETE: $total orders created")
        showToast("Done! $total orders created")
        updateState(StateType.COMPLETE_ITERATION, "Complete: $total orders created")
        stop()
    }
    
    private suspend fun createSingleBuyOrder(
        config: CreateModeConfig, 
        timing: GlobalSettings,
        itemNum: Int
    ): CreateOrderResult {
        
        // STEP 1: Tap "Buy Order" button
        val rowY = config.firstRowYPercent + (currentRowIndex * config.rowYOffsetPercent)
        val step1Name = "1. Tap Buy Order button (row $currentRowIndex)"
        
        updateState(StateType.EXECUTE_TAP, step1Name)
        Log.i(TAG, "ITEM $itemNum - STEP 1: Tap Buy Order at (${String.format("%.2f", config.buyOrderButtonXPercent)}, ${String.format("%.2f", rowY)})")
        
        if (!gestureHelper.tap(config.buyOrderButtonXPercent, rowY, timing.delayAfterTapMs, step1Name)) {
            return CreateOrderResult(false, "FAILED at Step 1: Tap Buy Order button - Check X%=${String.format("%.2f", config.buyOrderButtonXPercent)} Y%=${String.format("%.2f", rowY)}")
        }
        
        // STEP 2: Tap "+" icon
        val step2Name = "2. Tap + icon (+1 silver)"
        
        updateState(StateType.TAP_PLUS_BUTTON, step2Name)
        Log.i(TAG, "ITEM $itemNum - STEP 2: Tap + icon at (${String.format("%.2f", config.plusButtonXPercent)}, ${String.format("%.2f", config.plusButtonYPercent)})")
        
        if (!gestureHelper.tap(config.plusButtonXPercent, config.plusButtonYPercent, timing.delayAfterTapMs, step2Name)) {
            return CreateOrderResult(false, "FAILED at Step 2: Tap + icon - Check X%=${String.format("%.2f", config.plusButtonXPercent)} Y%=${String.format("%.2f", config.plusButtonYPercent)}")
        }
        
        // STEP 3: Tap "Confirm" button
        val step3Name = "3. Tap Confirm button"
        
        updateState(StateType.TAP_CONFIRM_BUTTON, step3Name)
        Log.i(TAG, "ITEM $itemNum - STEP 3: Tap Confirm at (${String.format("%.2f", config.confirmButtonXPercent)}, ${String.format("%.2f", config.confirmButtonYPercent)})")
        
        if (!gestureHelper.tap(config.confirmButtonXPercent, config.confirmButtonYPercent, timing.delayAfterConfirmMs, step3Name)) {
            return CreateOrderResult(false, "FAILED at Step 3: Tap Confirm - Check X%=${String.format("%.2f", config.confirmButtonXPercent)} Y%=${String.format("%.2f", config.confirmButtonYPercent)}")
        }
        
        Log.i(TAG, "ITEM $itemNum - ALL STEPS SUCCESS")
        return CreateOrderResult(true, "Order created successfully")
    }
    
    private suspend fun scrollToNextItem(config: CreateModeConfig, timing: GlobalSettings, itemNum: Int) {
        currentRowIndex++
        
        if (currentRowIndex >= config.maxRowsPerScreen) {
            val stepName = "4. Scroll to next items"
            
            updateState(StateType.SCROLL_NEXT_ROW, stepName)
            Log.i(TAG, "ITEM $itemNum - STEP 4: Scroll down")
            
            gestureHelper.swipeDownToUp(
                startYPercent = config.scrollStartYPercent,
                endYPercent = config.scrollEndYPercent,
                xPercent = config.scrollXPercent,
                delayAfterMs = timing.delayAfterSwipeMs,
                stepName = stepName
            )
            
            currentRowIndex = 0
        }
    }

    private suspend fun executeEditBuyOrderLoop() {
        val config = calibration.editMode
        val timing = calibration.global
        val maxOrders = if (debugMode) 1 else config.maxOrdersToEdit
        
        Log.i(TAG, "EDIT MODE: Starting edit loop for max $maxOrders orders")
        
        // STEP 0: Navigate to "My Orders" tab
        val step0Name = "0. Go to My Orders tab"
        
        updateState(StateType.NAVIGATE_TO_MY_ORDERS, step0Name)
        Log.i(TAG, "STEP 0: Tap My Orders at (${String.format("%.2f", config.myOrdersTabXPercent)}, ${String.format("%.2f", config.myOrdersTabYPercent)})")
        
        if (!gestureHelper.tap(config.myOrdersTabXPercent, config.myOrdersTabYPercent, 1400, step0Name)) {
            val error = "FAILED at Step 0: Navigate to My Orders - Check X%=${String.format("%.2f", config.myOrdersTabXPercent)} Y%=${String.format("%.2f", config.myOrdersTabYPercent)}"
            handleStepError("EDIT", error)
            stop()
            return
        }
        
        for (i in 0 until maxOrders) {
            if (!isRunning.get()) break
            while (isPaused.get()) delay(100)
            
            val orderNum = i + 1
            
            try {
                val result = editSingleOrder(config, timing, orderNum)
                
                if (result.success) {
                    itemsProcessed.incrementAndGet()
                    consecutiveErrors.set(0)
                    
                    val processed = itemsProcessed.get()
                    onProgressUpdate?.invoke(processed, maxOrders)
                    
                    updateState(StateType.COMPLETE_ITERATION, 
                        "Order $processed updated to ${result.newPrice} silver")
                    showToast("Order $processed: ${result.newPrice}s")
                    
                    delay(timing.cycleCooldownMs)
                    
                } else if (result.isEndOfList) {
                    Log.i(TAG, "EDIT: End of orders list reached")
                    showToast("All orders processed!")
                    updateState(StateType.ERROR_END_OF_LIST, "All orders processed")
                    break
                    
                } else {
                    val errorMsg = result.message
                    handleStepError("EDIT order $orderNum", errorMsg)
                    
                    val errors = consecutiveErrors.incrementAndGet()
                    if (errors >= calibration.errorRecovery.maxConsecutiveErrors) {
                        val fullError = "Too many errors ($errors). Last: $errorMsg"
                        updateState(StateType.ERROR_RETRY, fullError)
                        onError?.invoke(fullError)
                        break
                    }
                    delay(2000)
                }
                
            } catch (e: Exception) {
                val errorMsg = buildErrorMessage("EDIT order $orderNum crashed at step: $lastStepName", e)
                handleStepError("EDIT order $orderNum", errorMsg)
                consecutiveErrors.incrementAndGet()
                delay(2000)
            }
        }
        
        val total = itemsProcessed.get()
        Log.i(TAG, "EDIT MODE COMPLETE: $total orders updated")
        showToast("Done! $total orders updated")
        updateState(StateType.COMPLETE_ITERATION, "Complete: $total orders updated")
        stop()
    }
    
    private suspend fun editSingleOrder(
        config: EditModeConfig,
        timing: GlobalSettings,
        orderNum: Int
    ): EditOrderResult {
        
        // STEP 1: Tap Edit (pencil) icon
        val step1Name = "1. Tap Edit pencil icon"
        
        updateState(StateType.TAP_EDIT_BUTTON, step1Name)
        Log.i(TAG, "ORDER $orderNum - STEP 1: Tap Edit at (${String.format("%.2f", config.editButtonXPercent)}, ${String.format("%.2f", config.editButtonYPercent)})")
        
        if (!gestureHelper.tap(config.editButtonXPercent, config.editButtonYPercent, timing.delayAfterTapMs, step1Name)) {
            return EditOrderResult(false, "FAILED at Step 1: Tap Edit pencil - Check X%=${String.format("%.2f", config.editButtonXPercent)} Y%=${String.format("%.2f", config.editButtonYPercent)}")
        }
        
        // STEP 2: Read current top order price via OCR
        val step2Name = "2. Read top price via OCR"
        
        updateState(StateType.COOLDOWN, step2Name)
        Log.i(TAG, "ORDER $orderNum - STEP 2: Reading price via OCR")
        
        val topPrice = readTopOrderPrice(config)
        
        if (topPrice == null) {
            Log.w(TAG, "EDIT: OCR returned null - treating as end of list")
            gestureHelper.tap(config.closeButtonXPercent, config.closeButtonYPercent, 500, "Close popup")
            return EditOrderResult(false, "OCR failed - no price detected", isEndOfList = true)
        }
        
        // STEP 3: Calculate new price = top price + 1
        val newPrice = topPrice + config.priceIncrement
        Log.i(TAG, "ORDER $orderNum - Top price=$topPrice -> New price=$newPrice")
        
        // Safety check
        if (newPrice > config.hardPriceCap) {
            val error = "SAFETY HALT: Price $newPrice > cap ${config.hardPriceCap}"
            updateState(StateType.ERROR_PRICE_SANITY, error)
            onPriceSanityError?.invoke(error)
            gestureHelper.tap(config.closeButtonXPercent, config.closeButtonYPercent, 500, "Close popup")
            return EditOrderResult(false, error)
        }
        
        // STEP 4: Set new price via AccessibilityNodeInfo
        val step4Name = "4. Set price to $newPrice"
        
        updateState(StateType.EXECUTE_TEXT_INPUT, step4Name)
        Log.i(TAG, "ORDER $orderNum - STEP 4: Set price to $newPrice")
        
        val priceResult = gestureHelper.setPriceField(newPrice.toString(), step4Name)
        
        if (!priceResult.success) {
            Log.w(TAG, "EDIT: Failed to set price, retrying...")
            delay(500)
            
            val retryResult = gestureHelper.setPriceField(newPrice.toString(), "$step4Name (retry)")
            if (!retryResult.success) {
                return EditOrderResult(false, "FAILED at Step 4: Set price - ${priceResult.errorMessage}")
            }
        }
        
        lastKnownPrice = newPrice
        delay(timing.delayAfterTapMs)
        
        // STEP 5: Tap "Update" button
        val step5Name = "5. Tap Update button"
        
        updateState(StateType.TAP_UPDATE_BUTTON, step5Name)
        Log.i(TAG, "ORDER $orderNum - STEP 5: Tap Update at (${String.format("%.2f", config.updateButtonXPercent)}, ${String.format("%.2f", config.updateButtonYPercent)})")
        
        if (!gestureHelper.tap(config.updateButtonXPercent, config.updateButtonYPercent, timing.delayAfterConfirmMs, step5Name)) {
            return EditOrderResult(false, "FAILED at Step 5: Tap Update - Check X%=${String.format("%.2f", config.updateButtonXPercent)} Y%=${String.format("%.2f", config.updateButtonYPercent)}")
        }
        
        Log.i(TAG, "ORDER $orderNum - ALL STEPS SUCCESS - Price: $newPrice")
        return EditOrderResult(true, "Order updated", newPrice)
    }
    
    private suspend fun readTopOrderPrice(config: EditModeConfig): Int? {
        val bitmap = onScreenshotRequest?.invoke()
        if (bitmap == null) {
            Log.w(TAG, "OCR: No screenshot available")
            return null
        }
        
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
            } else {
                Log.w(TAG, "OCR: No price detected in region")
            }
            price
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            null
        }
    }

    private fun handleStepError(context: String, message: String) {
        val fullError = buildErrorMessage(context, null, message)
        Log.e(TAG, fullError)
        updateState(StateType.ERROR_RETRY, fullError)
        
        if (debugMode) {
            showToast("ERROR: $message")
        }
    }
    
    private fun buildErrorMessage(context: String, exception: Exception? = null, customMessage: String? = null): String {
        val sb = StringBuilder()
        sb.append("HANDLE_ERROR_POPUP\n")
        sb.append("------------------\n")
        sb.append("Context: $context\n")
        sb.append("Last Step: $lastStepName\n")
        sb.append("Screen: ${if (::gestureHelper.isInitialized) gestureHelper.getScreenInfo() else "Unknown"}\n")
        
        if (customMessage != null) {
            sb.append("Error: $customMessage\n")
        }
        
        if (exception != null) {
            sb.append("Exception: ${exception.javaClass.simpleName}\n")
            sb.append("Message: ${exception.message}\n")
        }
        
        sb.append("------------------\n")
        sb.append("Fix: Adjust percent coordinates or delays in Calibration Settings")
        
        return sb.toString()
    }
    
    private fun updateState(stateType: StateType, message: String = "") {
        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            itemsProcessed = itemsProcessed.get(),
            errorMessage = if (stateType.name.contains("ERROR")) message else null,
            isPaused = isPaused.get(),
            lastPrice = lastKnownPrice,
            statistics = SessionStatistics(
                totalCycles = itemsProcessed.get(),
                successfulOperations = itemsProcessed.get(),
                consecutiveErrors = consecutiveErrors.get(),
                lastPrice = lastKnownPrice
            )
        )
        
        _stateFlow.value = state
        onStateChange?.invoke(state)
    }
    
    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(accessibilityService, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    fun getStatistics(): SessionStatistics {
        return SessionStatistics(
            totalCycles = itemsProcessed.get(),
            successfulOperations = itemsProcessed.get(),
            consecutiveErrors = consecutiveErrors.get(),
            lastPrice = lastKnownPrice
        )
    }
}

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
