package com.albion.marketassistant.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.albion.marketassistant.data.*
import com.albion.marketassistant.ml.OCREngine
import com.albion.marketassistant.statistics.StatisticsManager
import com.albion.marketassistant.util.RandomizationHelper
import com.albion.marketassistant.util.TextSimilarity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class StateMachine(
    private val scope: CoroutineScope,
    private val calibration: CalibrationData,
    private val uiInteractor: UIInteractor,
    private val context: Context? = null
) {

    companion object {
        private const val TAG = "StateMachine"
        
        // CRITICAL: Minimum tap duration to prevent ghost taps on 3D game engine
        private const val MIN_TAP_DURATION_MS = 150L
        private const val MAX_TAP_DURATION_MS = 300L
    }

    private val _stateFlow = MutableStateFlow<AutomationState>(AutomationState(StateType.IDLE))
    val stateFlow: StateFlow<AutomationState> = _stateFlow

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var currentMode = OperationMode.IDLE
    private var currentRowIndex = 0
    private var loopJob: Job? = null
    private val retryCount = AtomicInteger(0)
    private var lastKnownPrice: Int? = null

    private val totalCycleCount = AtomicInteger(0)
    private var lastPageText = ""
    private val endOfListDetectionCount = AtomicInteger(0)

    private val randomizationHelper = RandomizationHelper(calibration.antiDetection)
    private var statisticsManager: StatisticsManager? = null

    var onStateChange: ((AutomationState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onPriceSanityError: ((String) -> Unit)? = null
    var onEndOfList: (() -> Unit)? = null
    var onStatisticsUpdate: ((SessionStatistics) -> Unit)? = null
    var onScreenshotRequest: (() -> Bitmap?)? = null

    init {
        context?.let {
            statisticsManager = StatisticsManager(it, scope)
        }
    }

    fun startMode(mode: OperationMode) {
        if (isRunning.get()) {
            onError?.invoke("Already running")
            return
        }
        
        isRunning.set(true)
        isPaused.set(false)
        currentMode = mode
        currentRowIndex = 0
        retryCount.set(0)
        lastKnownPrice = null
        lastPageText = ""
        endOfListDetectionCount.set(0)
        totalCycleCount.set(0)

        statisticsManager?.resetSession()
        statisticsManager?.updateState(StateType.IDLE.name)

        loopJob = scope.launch { mainLoop() }
        Log.d(TAG, "Started mode: $mode")
    }

    fun pause() {
        isPaused.set(true)
        statisticsManager?.updateState(StateType.PAUSED.name)
        updateState(StateType.PAUSED, "Paused")
        Log.d(TAG, "Paused")
    }

    fun resume() {
        isPaused.set(false)
        statisticsManager?.updateState(StateType.IDLE.name)
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

        scope.launch {
            statisticsManager?.exportSessionLog()
            statisticsManager?.flushPriceHistory()
        }

        _stateFlow.value = AutomationState(StateType.IDLE)
    }

    private fun Long.withDelays(): Long {
        val withLag = (this * calibration.global.networkLagMultiplier).toLong()
        return randomizationHelper.getRandomizedDelay(withLag)
    }

    /**
     * CRITICAL: Get safe tap duration (150-300ms) to prevent ghost taps
     */
    private fun getSafeTapDuration(): Long {
        return randomizationHelper.getRandomizedDuration(
            (MIN_TAP_DURATION_MS + MAX_TAP_DURATION_MS) / 2
        ).coerceIn(MIN_TAP_DURATION_MS, MAX_TAP_DURATION_MS)
    }

    private suspend fun mainLoop() {
        try {
            while (isRunning.get()) {
                while (isPaused.get()) {
                    delay(100)
                }

                // CRITICAL OVERHAUL 3: REMOVED immersive mode check
                // The bot now executes regardless of what Android reports as active window
                // No more "Game window lost!" false positives

                when (currentMode) {
                    OperationMode.NEW_ORDER_SWEEPER -> executeCreateOrderLoop()
                    OperationMode.ORDER_EDITOR -> executeEditOrderLoop()
                    OperationMode.IDLE -> return
                }

                val cycle = totalCycleCount.incrementAndGet()
                statisticsManager?.recordCycle()

                if (checkEndOfList()) {
                    handleEndOfList()
                    continue
                }

                if (cycle >= calibration.endOfList.maxCyclesBeforeStop) {
                    updateState(StateType.ERROR_END_OF_LIST, "Max cycles reached ($cycle)")
                    handleEndOfList()
                    continue
                }

                updateState(StateType.COOLDOWN, "Cooldown")
                delay(randomizationHelper.getRandomizedDelay(calibration.global.cycleCooldownMs))
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Main loop cancelled")
        } catch (e: Exception) {
            isRunning.set(false)
            statisticsManager?.recordFailure("Main loop error: ${e.message}")
            onError?.invoke("Error: ${e.message}")
            Log.e(TAG, "Main loop error", e)
        }
    }

    // =====================================================
    // MODE 1: CREATE ORDERS (FAST LOOP)
    // =====================================================
    // Logic: Opening new buy order auto-fills market average
    // Action: Tap + button once to outbid
    // NO text injection, NO price input box tapping
    // Safety: OCR runs silently, halts if price > Hard Price Cap
    // =====================================================
    
    private suspend fun executeCreateOrderLoop() {
        try {
            val config = calibration.createMode
            val timing = calibration.global
            val safety = calibration.safety
            val errorSettings = calibration.errorRecovery

            statisticsManager?.updateState(StateType.EXECUTE_TAP.name)

            if ((statisticsManager?.getConsecutiveErrors() ?: 0) >= errorSettings.autoRestartAfterErrors) {
                updateState(StateType.RECOVERING, "Too many errors - auto-restart")
                delay(errorSettings.autoRestartDelayMs)
                currentRowIndex = 0
                statisticsManager?.resetConsecutiveErrors()
                return
            }

            // STEP 1: Calculate row position
            val rowX = config.firstRowX
            val rowY = config.firstRowY + (currentRowIndex * config.rowYOffset)

            // STEP 2: Tap the market row
            updateState(StateType.EXECUTE_TAP, "CREATE: Tapping row $currentRowIndex")
            Log.d(TAG, "CREATE: Tapping row $currentRowIndex at ($rowX, $rowY)")
            
            if (!performSafeTap(rowX, rowY)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap row")
                statisticsManager?.recordFailure("Tap row failed")
                handleUnexpectedPopup()
                return
            }

            // STEP 3: Wait for popup to open (300ms minimum)
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup")
            delay(300L.withDelays())

            // STEP 4: OCR Safety Check (runs silently in background)
            // This is a SAFETY NET only - if price exceeds Hard Price Cap, HALT
            val detectedPrice = performBackgroundOCRSafetyCheck()
            if (detectedPrice != null && detectedPrice > config.hardPriceCap) {
                updateState(StateType.ERROR_PRICE_SANITY, 
                    "SAFETY HALT: Price $detectedPrice > Hard Cap ${config.hardPriceCap}")
                statisticsManager?.recordFailure("Price cap exceeded: $detectedPrice > ${config.hardPriceCap}")
                onPriceSanityError?.invoke("Price $detectedPrice exceeds hard cap ${config.hardPriceCap}")
                handleUnexpectedPopup()
                return
            }

            // STEP 5: Tap the PLUS BUTTON (increment price by 1)
            updateState(StateType.TAP_PLUS_BUTTON, "CREATE: Tapping + button to outbid")
            Log.d(TAG, "CREATE: Tapping + button at (${config.plusButtonX}, ${config.plusButtonY})")
            
            if (!performSafeTap(config.plusButtonX, config.plusButtonY)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap + button")
                statisticsManager?.recordFailure("Tap + button failed")
                handleUnexpectedPopup()
                return
            }

            // Small delay after plus button
            delay(150L.withDelays())

            // STEP 6: Tap CREATE button
            updateState(StateType.EXECUTE_BUTTON, "CREATE: Tapping Create button")
            Log.d(TAG, "CREATE: Tapping Create button at (${config.createButtonX}, ${config.createButtonY})")
            
            if (!performSafeTap(config.createButtonX, config.createButtonY)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap Create button")
                statisticsManager?.recordFailure("Tap Create button failed")
                handleUnexpectedPopup()
                return
            }

            delay(timing.confirmationWaitMs.withDelays())

            // STEP 7: Check for Confirmation popup and tap YES
            updateState(StateType.HANDLE_CONFIRMATION, "CREATE: Confirming order")
            Log.d(TAG, "CREATE: Tapping Confirm Yes at (${config.confirmYesX}, ${config.confirmYesY})")
            
            performSafeTap(config.confirmYesX, config.confirmYesY)
            delay(timing.popupCloseWaitMs.withDelays())

            // SUCCESS!
            statisticsManager?.recordSuccess("CREATE", detectedPrice)
            if (detectedPrice != null) {
                statisticsManager?.recordPrice("item_$currentRowIndex", detectedPrice + 1, "CREATE", true)
            }
            
            updateState(StateType.COMPLETE_ITERATION, "Order created successfully")
            lastKnownPrice = detectedPrice
            
            // STEP 8: Handle row iteration (swipe after max rows)
            handleCreateModeRowIteration(config.maxRowsPerScreen, timing)

        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
            statisticsManager?.recordFailure("Exception: ${e.message}")
            Log.e(TAG, "Create order loop error", e)
            handleUnexpectedPopup()
        }
    }

    /**
     * CREATE MODE: After Max Rows, execute Swipe
     */
    private suspend fun handleCreateModeRowIteration(maxRows: Int, timing: GlobalSettings) {
        currentRowIndex++
        
        if (currentRowIndex >= maxRows) {
            // Swipe to next page
            updateState(StateType.SCROLL_NEXT_ROW, "CREATE: Swiping to next page...")
            Log.d(TAG, "CREATE: Swiping after $maxRows rows")
            
            performSafeSwipe(
                timing.swipeStartX, timing.swipeStartY,
                timing.swipeEndX, timing.swipeEndY
            )
            
            val settleTime = if (calibration.swipeOverlap.enableSwipeOverlap) {
                calibration.swipeOverlap.swipeSettleTimeMs
            } else {
                400L
            }
            delay(randomizationHelper.getRandomizedDelay(settleTime))
            
            // Reset row index after swipe
            currentRowIndex = 0
        }
    }

    // =====================================================
    // MODE 2: EDIT ORDERS (READ & TYPE LOOP)
    // =====================================================
    // Logic: Editing pushes order to bottom of list
    // Action: Always tap Row 1, read price, inject new price
    // NO swiping, NO row iteration
    // MUST use ACTION_SET_TEXT (no keyboard popup)
    // =====================================================
    
    private suspend fun executeEditOrderLoop() {
        try {
            val config = calibration.editMode
            val timing = calibration.global
            val safety = calibration.safety
            val errorSettings = calibration.errorRecovery

            statisticsManager?.updateState(StateType.EXECUTE_TAP.name)

            if ((statisticsManager?.getConsecutiveErrors() ?: 0) >= errorSettings.autoRestartAfterErrors) {
                updateState(StateType.RECOVERING, "Too many errors - auto-restart")
                delay(errorSettings.autoRestartDelayMs)
                statisticsManager?.resetConsecutiveErrors()
                return
            }

            // CRITICAL: EDIT MODE ALWAYS TAPS ROW 1
            // Editing pushes order to bottom, so next order is always at Row 1
            val editX = config.editButtonX
            val editY = config.editButtonY  // Always Row 1 - NO iteration

            // STEP 1: Tap Edit button on Row 1
            updateState(StateType.EXECUTE_TAP, "EDIT: Tapping Edit button (Row 1)")
            Log.d(TAG, "EDIT: Tapping Edit button at ($editX, $editY)")
            
            if (!performSafeTap(editX, editY)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap Edit button")
                statisticsManager?.recordFailure("Tap Edit button failed")
                handleUnexpectedPopup()
                return
            }

            // STEP 2: Wait for popup to open
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup")
            delay(timing.popupOpenWaitMs.withDelays())

            // STEP 3: Read OCR Top Price
            updateState(StateType.SCAN_OCR, "EDIT: Reading current price via OCR")
            val detectedPrice = performPriceOCR()
            
            if (detectedPrice == null) {
                // OCR failed - could be end of list
                updateState(StateType.ERROR_END_OF_LIST, "EDIT: OCR failed - possibly end of list")
                Log.w(TAG, "EDIT: OCR returned null - treating as end of list")
                handleUnexpectedPopup()
                statisticsManager?.recordFailure("OCR failed - end of list")
                return
            }

            // STEP 4: Calculate new price (Top Price + 1)
            val newPrice = detectedPrice + config.priceIncrement
            Log.d(TAG, "EDIT: Detected price $detectedPrice, new price: $newPrice")

            // STEP 5: Safety check against Hard Price Cap
            if (newPrice > config.hardPriceCap) {
                updateState(StateType.ERROR_PRICE_SANITY, 
                    "SAFETY HALT: New price $newPrice > Hard Cap ${config.hardPriceCap}")
                statisticsManager?.recordFailure("Price cap exceeded: $newPrice > ${config.hardPriceCap}")
                onPriceSanityError?.invoke("New price $newPrice exceeds hard cap ${config.hardPriceCap}")
                handleUnexpectedPopup()
                return
            }

            // STEP 6: INJECT TEXT using ACTION_SET_TEXT (NO keyboard popup)
            // CRITICAL: Do NOT use dispatchGesture to tap the text box!
            updateState(StateType.EXECUTE_TEXT_INPUT, "EDIT: Injecting price $newPrice via ACTION_SET_TEXT")
            Log.d(TAG, "EDIT: Injecting price $newPrice via AccessibilityNodeInfo")
            
            // First clear the field, then inject new price
            if (!uiInteractor.clearTextField()) {
                Log.w(TAG, "EDIT: Failed to clear text field, attempting inject anyway")
            }
            
            delay(50L)
            
            if (!uiInteractor.injectText(newPrice.toString())) {
                updateState(StateType.ERROR_RETRY, "Failed to inject text")
                statisticsManager?.recordFailure("Text injection failed")
                handleUnexpectedPopup()
                return
            }

            lastKnownPrice = newPrice
            delay(timing.textInputDelayMs.withDelays())

            // STEP 7: Tap UPDATE button
            updateState(StateType.EXECUTE_BUTTON, "EDIT: Tapping Update button")
            Log.d(TAG, "EDIT: Tapping Update button at (${config.updateButtonX}, ${config.updateButtonY})")
            
            if (!performSafeTap(config.updateButtonX, config.updateButtonY)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap Update button")
                statisticsManager?.recordFailure("Tap Update button failed")
                handleUnexpectedPopup()
                return
            }

            delay(timing.confirmationWaitMs.withDelays())

            // STEP 8: Check for Confirmation popup and tap YES
            updateState(StateType.HANDLE_CONFIRMATION, "EDIT: Confirming update")
            Log.d(TAG, "EDIT: Tapping Confirm Yes at (${config.confirmYesX}, ${config.confirmYesY})")
            
            performSafeTap(config.confirmYesX, config.confirmYesY)
            delay(timing.popupCloseWaitMs.withDelays())

            // SUCCESS!
            statisticsManager?.recordSuccess("EDIT", newPrice)
            statisticsManager?.recordPrice("edit_order", newPrice, "EDIT", true)
            
            updateState(StateType.COMPLETE_ITERATION, "Order updated successfully")

            // CRITICAL: EDIT MODE DOES NOT ITERATE ROWS
            // DO NOT SWIPE. DO NOT INCREMENT currentRowIndex.
            // Always return to tap Row 1 on next iteration
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
            statisticsManager?.recordFailure("Exception: ${e.message}")
            Log.e(TAG, "Edit order loop error", e)
            handleUnexpectedPopup()
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * CRITICAL: Perform a SAFE tap with proper duration (150-300ms)
     * This prevents ghost taps on the 3D game engine
     */
    private suspend fun performSafeTap(x: Int, y: Int): Boolean {
        val duration = getSafeTapDuration()
        Log.d(TAG, "Safe tap at ($x, $y) with duration ${duration}ms")
        return uiInteractor.performTap(x, y, duration)
    }

    /**
     * Perform a safe swipe
     */
    private fun performSafeSwipe(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
        val distance = abs(endY - startY)
        val randomizedDistance = randomizationHelper.getRandomizedSwipeDistance(distance)
        val actualEndY = startY - randomizedDistance
        val duration = randomizationHelper.getRandomizedDuration(calibration.global.swipeDurationMs.toLong())
            .coerceIn(200L, 500L)  // Minimum 200ms for swipe

        return uiInteractor.performSwipe(startX, startY, endX, actualEndY, duration)
    }

    /**
     * OCR Safety Check for CREATE mode (runs silently in background)
     * Only returns price if OCR succeeds; null means OCR failed (continue anyway)
     */
    private suspend fun performBackgroundOCRSafetyCheck(): Int? {
        if (!calibration.safety.enableOcrSanityCheck) return null

        val bitmap = onScreenshotRequest?.invoke() ?: return null
        val ocrRegion = calibration.createMode.getOCRRegion()
        
        return try {
            val results = OCREngine.recognizeText(bitmap, ocrRegion)
            val price = results
                .filter { it.isNumber && it.numericValue != null }
                .maxByOrNull { it.confidence }
                ?.numericValue
            
            if (price != null) {
                Log.d(TAG, "OCR Safety Check - Detected price: $price")
            }
            price
        } catch (e: Exception) {
            Log.e(TAG, "OCR Safety Check failed", e)
            null
        }
    }

    /**
     * Read price via OCR for EDIT mode
     */
    private suspend fun performPriceOCR(): Int? {
        val bitmap = onScreenshotRequest?.invoke() ?: return null
        val ocrRegion = calibration.editMode.getOCRRegion()
        
        return try {
            val results = OCREngine.recognizeText(bitmap, ocrRegion)
            val price = results
                .filter { it.isNumber && it.numericValue != null }
                .maxByOrNull { it.confidence }
                ?.numericValue
            
            if (price != null) {
                Log.d(TAG, "Price OCR - Detected: $price")
                lastKnownPrice = price
            }
            price
        } catch (e: Exception) {
            Log.e(TAG, "Price OCR failed", e)
            null
        }
    }

    private suspend fun checkEndOfList(): Boolean {
        val eolSettings = calibration.endOfList
        if (!eolSettings.enableEndOfListDetection) return false

        // For EDIT mode, we detect end of list differently (OCR failure)
        if (currentMode == OperationMode.ORDER_EDITOR) {
            return false  // Handled in executeEditOrderLoop
        }

        val currentText = captureAndOCRFirstLine() ?: return false

        if (lastPageText.isNotEmpty()) {
            val result = TextSimilarity.calculatePageMatchScore(
                lastPageText, currentText, eolSettings.textSimilarityThreshold
            )

            if (result.isLikelySamePage) {
                val count = endOfListDetectionCount.incrementAndGet()
                if (count >= eolSettings.identicalPageThreshold) {
                    return true
                }
            } else {
                endOfListDetectionCount.set(0)
            }
        }

        lastPageText = currentText
        return false
    }

    private suspend fun captureAndOCRFirstLine(): String? {
        val bitmap = onScreenshotRequest?.invoke() ?: return null
        val region = calibration.endOfList.getFirstLineOcrRegion()
        val results = OCREngine.recognizeText(bitmap, region)
        return results.firstOrNull()?.text
    }

    private suspend fun handleEndOfList() {
        updateState(StateType.ERROR_END_OF_LIST, "End of list reached")
        onEndOfList?.invoke()
        statisticsManager?.exportSessionLog()

        if (calibration.swipeOverlap.enableScrollTracking && currentMode == OperationMode.NEW_ORDER_SWEEPER) {
            currentRowIndex = 0
            lastPageText = ""
            endOfListDetectionCount.set(0)
            updateState(StateType.COOLDOWN, "Restarting from top...")
            delay(2000)
        } else {
            stop()
        }
    }

    private suspend fun handleUnexpectedPopup() {
        val safety = calibration.safety
        if (!safety.autoDismissErrors) return

        updateState(StateType.HANDLE_ERROR_POPUP, "Handling error popup")
        statisticsManager?.updateState(StateType.HANDLE_ERROR_POPUP.name)

        val closeX = if (currentMode == OperationMode.NEW_ORDER_SWEEPER) {
            calibration.createMode.closeButtonX
        } else {
            calibration.editMode.closeButtonX
        }
        val closeY = if (currentMode == OperationMode.NEW_ORDER_SWEEPER) {
            calibration.createMode.closeButtonY
        } else {
            calibration.editMode.closeButtonY
        }

        performSafeTap(closeX, closeY)
        delay(300L.withDelays())
    }

    private fun updateState(stateType: StateType, message: String = "") {
        val stats = statisticsManager?.getStatistics() ?: SessionStatistics()
        statisticsManager?.updateState(stateType.name)

        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            errorMessage = if (stateType.name.startsWith("ERROR")) message else null,
            isPaused = isPaused.get(),
            retryCount = retryCount.get(),
            lastPrice = lastKnownPrice,
            statistics = stats,
            lastPageText = lastPageText,
            endOfListCount = endOfListDetectionCount.get()
        )

        _stateFlow.value = state
        onStateChange?.invoke(state)
        onStatisticsUpdate?.invoke(stats)
    }

    fun getStatistics(): SessionStatistics {
        return statisticsManager?.getStatistics() ?: SessionStatistics()
    }

    suspend fun exportSessionLog() {
        statisticsManager?.exportSessionLog()
    }
}
