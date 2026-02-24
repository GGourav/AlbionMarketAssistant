package com.albion.marketassistant.statemachine

import android.graphics.Rect
import com.albion.marketassistant.accessibility.UIInteractor
import com.albion.marketassistant.data.*
import com.albion.marketassistant.media.ScreenCaptureManager
import com.albion.marketassistant.ml.ColorDetector
import com.albion.marketassistant.ml.OCREngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Orchestrates the automation state machine for both operational modes.
 */
class StateMachine(
    private val scope: CoroutineScope,
    private val calibration: CalibrationData,
    private val colorDetector: ColorDetector,
    private val ocrEngine: OCREngine,
    private val uiInteractor: UIInteractor,
    private val screenCaptureManager: ScreenCaptureManager
) {
    
    private val _stateFlow = MutableStateFlow<AutomationState>(
        AutomationState(StateType.IDLE)
    )
    val stateFlow: StateFlow<AutomationState> = _stateFlow
    
    private var isRunning = false
    private var currentMode = OperationMode.IDLE
    private var currentRowIndex = 0
    private var loopJob: Job? = null
    
    var onStateChange: ((AutomationState) -> Unit)? = null
    var onIterationComplete: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    fun startMode(mode: OperationMode) {
        if (isRunning) {
            onError?.invoke("Automation already running")
            return
        }
        isRunning = true
        currentMode = mode
        currentRowIndex = 0
        loopJob = scope.launch {
            mainLoop()
        }
    }
    
    fun stop() {
        isRunning = false
        loopJob?.cancel()
        currentMode = OperationMode.IDLE
        _stateFlow.value = AutomationState(StateType.IDLE)
    }
    
    private suspend fun mainLoop() {
        try {
            while (isRunning) {
                when (currentMode) {
                    OperationMode.NEW_ORDER_SWEEPER -> executeNewOrderSweeperLoop()
                    OperationMode.ORDER_EDITOR -> executeOrderEditorLoop()
                    OperationMode.IDLE -> return
                }
                currentRowIndex++
            }
        } catch (e: Exception) {
            isRunning = false
            onError?.invoke("State Machine Error: ${e.message}")
        }
    }
    
    // ==================== MODE 1: NEW ORDER SWEEPER ====================
    
    private suspend fun executeNewOrderSweeperLoop() {
        try {
            // STEP 1: Calculate row coordinates
            val (rowX, rowY) = calculateRowCoordinates(currentRowIndex)
            updateState(StateType.EXECUTE_TAP, rowX, rowY, "Tapping row $currentRowIndex")
            
            // STEP 2: Tap the item row
            uiInteractor.performTap(rowX, rowY, calibration.tapDurationMs)
            
            // STEP 3: Wait for popup to open
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup to open...")
            delay(calibration.popupOpenWaitMs)
            
            // STEP 4: Capture screen and check for highlight
            val screenshot = screenCaptureManager.captureScreen()
            val highlightDetected = if (screenshot != null) {
                detectHighlightedRow(screenshot)
            } else {
                false
            }
            
            updateState(StateType.SCAN_HIGHLIGHTS, "Highlight detected: $highlightDetected")
            
            // STEP 5: Branch logic
            if (highlightDetected) {
                // Already have order - close and next
                updateState(StateType.EXECUTE_TAP, "Closing popup (already ordered)")
                uiInteractor.performTap(
                    calibration.closeButtonX,
                    calibration.closeButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
            } else {
                // New order - extract price and create
                updateState(StateType.SCAN_OCR, "Scanning market price via OCR...")
                val topPrice = extractTopMarketPrice(screenshot)
                
                if (topPrice != null) {
                    val newPrice = topPrice + 5
                    updateState(StateType.EXECUTE_TEXT_INPUT, "Setting price to $newPrice")
                    
                    // Tap price input
                    uiInteractor.performTap(
                        calibration.priceInputX,
                        calibration.priceInputY,
                        calibration.tapDurationMs
                    )
                    delay(calibration.textInputDelayMs)
                    
                    // Clear and inject text
                    uiInteractor.clearTextField()
                    delay(100)
                    uiInteractor.injectText(newPrice.toString())
                    delay(calibration.textInputDelayMs)
                    
                    // Tap confirm button
                    updateState(StateType.EXECUTE_BUTTON, "Clicking Create Buy Order")
                    uiInteractor.performTap(
                        calibration.confirmButtonX,
                        calibration.confirmButtonY,
                        calibration.tapDurationMs
                    )
                    delay(calibration.popupCloseWaitMs)
                } else {
                    updateState(StateType.ERROR_RETRY, "Failed to extract market price")
                }
            }
            
            // STEP 6: Scroll to next row
            updateState(StateType.SCROLL_NEXT_ROW, "Scrolling to next row...")
            handleRowIteration()
            delay(calibration.pixelPollingIntervalMs)
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error in sweeper loop: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // ==================== MODE 2: ORDER EDITOR ====================
    
    private suspend fun executeOrderEditorLoop() {
        try {
            // STEP 1: Calculate edit button coordinates
            val (editButtonX, editButtonY) = calculateRowCoordinates(currentRowIndex)
            updateState(StateType.EXECUTE_TAP, editButtonX, editButtonY, "Tapping Edit for order $currentRowIndex")
            
            // STEP 2: Tap edit button
            uiInteractor.performTap(editButtonX, editButtonY, calibration.tapDurationMs)
            
            // STEP 3: Wait for popup
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup...")
            delay(calibration.popupOpenWaitMs)
            
            // STEP 4: Dual OCR
            val screenshot = screenCaptureManager.captureScreen()
            updateState(StateType.SCAN_OCR, "Scanning prices...")
            
            val topMarketPrice = extractTopMarketPrice(screenshot)
            val myCurrentPrice = extractPriceFromInputField(screenshot)
            
            if (topMarketPrice == null || myCurrentPrice == null) {
                updateState(StateType.ERROR_RETRY, "Failed to extract prices")
                uiInteractor.performTap(
                    calibration.closeButtonX,
                    calibration.closeButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
                handleRowIteration()
                return
            }
            
            updateState(StateType.SCAN_OCR, "Top: $topMarketPrice, Mine: $myCurrentPrice")
            
            // STEP 5: Comparison
            if (myCurrentPrice >= topMarketPrice) {
                // Already highest
                updateState(StateType.EXECUTE_TAP, "Already highest bidder")
                uiInteractor.performTap(
                    calibration.closeButtonX,
                    calibration.closeButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
            } else {
                // Outbid - update
                val newPrice = topMarketPrice + 1
                updateState(StateType.EXECUTE_TEXT_INPUT, "Updating to $newPrice")
                
                uiInteractor.performTap(
                    calibration.priceInputX,
                    calibration.priceInputY,
                    calibration.tapDurationMs
                )
                delay(calibration.textInputDelayMs)
                
                uiInteractor.clearTextField()
                delay(100)
                uiInteractor.injectText(newPrice.toString())
                delay(calibration.textInputDelayMs)
                
                updateState(StateType.EXECUTE_BUTTON, "Clicking Update Order")
                uiInteractor.performTap(
                    calibration.confirmButtonX,
                    calibration.confirmButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
            }
            
            // STEP 6: Next order
            updateState(StateType.SCROLL_NEXT_ROW, "Moving to next order...")
            handleRowIteration()
            delay(calibration.pixelPollingIntervalMs)
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error in editor loop: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private fun calculateRowCoordinates(rowIndex: Int): Pair<Int, Int> {
        val x = calibration.firstRowX
        val y = calibration.firstRowY + (rowIndex * calibration.rowYOffset)
        return Pair(x, y)
    }
    
    private suspend fun handleRowIteration() {
        if (currentRowIndex % calibration.maxRowsPerScreen == 0) {
            updateState(StateType.SCROLL_NEXT_ROW, "Swiping up...")
            uiInteractor.performSwipe(
                calibration.swipeStartX,
                calibration.swipeStartY,
                calibration.swipeEndX,
                calibration.swipeEndY,
                calibration.swipeDurationMs.toLong()
            )
            delay(300)
        }
    }
    
    private suspend fun detectHighlightedRow(screenshot: android.graphics.Bitmap): Boolean {
        return try {
            val result = colorDetector.detectColor(
                screenshot,
                calibration.getBuyOrdersRegion(),
                calibration.highlightedRowColorHex,
                calibration.colorToleranceRGB
            )
            result.isMatch
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun extractTopMarketPrice(screenshot: android.graphics.Bitmap?): Int? {
        if (screenshot == null) return null
        
        return try {
            val ocrResults = ocrEngine.recognizeText(
                screenshot,
                calibration.getBuyOrdersRegion(),
                calibration.ocrLanguage
            )
            ocrResults.firstOrNull { it.isNumber }?.numericValue
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun extractPriceFromInputField(screenshot: android.graphics.Bitmap?): Int? {
        if (screenshot == null) return null
        
        return try {
            val ocrResults = ocrEngine.recognizeText(
                screenshot,
                calibration.getPriceInputRegion(),
                calibration.ocrLanguage
            )
            ocrResults.firstOrNull { it.isNumber }?.numericValue
        } catch (e: Exception) {
            null
        }
    }
    
    private fun updateState(
        stateType: StateType,
        x: Int = 0,
        y: Int = 0,
        message: String = ""
    ) {
        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            currentX = x,
            currentY = y,
            errorMessage = if (stateType == StateType.ERROR_RETRY) message else null
        )
        _stateFlow.value = state
        onStateChange?.invoke(state)
    }
    
    private fun updateState(stateType: StateType, message: String) {
        updateState(stateType, 0, 0, message)
