package com.albion.marketassistant.statemachine

import com.albion.marketassistant.accessibility.UIInteractor
import com.albion.marketassistant.data.*
import com.albion.marketassistant.media.ScreenCaptureManager
import com.albion.marketassistant.ml.ColorDetector
import com.albion.marketassistant.ml.OCREngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    
    private suspend fun executeNewOrderSweeperLoop() {
        try {
            val (rowX, rowY) = calculateRowCoordinates(currentRowIndex)
            updateState(StateType.EXECUTE_TAP, rowX, rowY, "Tapping row $currentRowIndex")
            
            uiInteractor.performTap(rowX, rowY, calibration.tapDurationMs)
            
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup...")
            delay(calibration.popupOpenWaitMs)
            
            val screenshot = screenCaptureManager.captureScreen()
            val highlightDetected = if (screenshot != null) {
                detectHighlightedRow(screenshot)
            } else {
                false
            }
            
            updateState(StateType.SCAN_HIGHLIGHTS, "Highlight: $highlightDetected")
            
            if (highlightDetected) {
                updateState(StateType.EXECUTE_TAP, "Closing (already ordered)")
                uiInteractor.performTap(
                    calibration.closeButtonX,
                    calibration.closeButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
            } else {
                updateState(StateType.SCAN_OCR, "Extracting price...")
                val topPrice = extractTopMarketPrice(screenshot)
                
                if (topPrice != null) {
                    val newPrice = topPrice + 5
                    updateState(StateType.EXECUTE_TEXT_INPUT, "Setting price: $newPrice")
                    
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
                    
                    updateState(StateType.EXECUTE_BUTTON, "Creating order")
                    uiInteractor.performTap(
                        calibration.confirmButtonX,
                        calibration.confirmButtonY,
                        calibration.tapDurationMs
                    )
                    delay(calibration.popupCloseWaitMs)
                } else {
                    updateState(StateType.ERROR_RETRY, "OCR failed")
                }
            }
            
            handleRowIteration()
            delay(calibration.pixelPollingIntervalMs)
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
        }
    }
    
    private suspend fun executeOrderEditorLoop() {
        try {
            val (editButtonX, editButtonY) = calculateRowCoordinates(currentRowIndex)
            updateState(StateType.EXECUTE_TAP, editButtonX, editButtonY, "Editing order $currentRowIndex")
            
            uiInteractor.performTap(editButtonX, editButtonY, calibration.tapDurationMs)
            
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting...")
            delay(calibration.popupOpenWaitMs)
            
            val screenshot = screenCaptureManager.captureScreen()
            updateState(StateType.SCAN_OCR, "Reading prices...")
            
            val topMarketPrice = extractTopMarketPrice(screenshot)
            val myCurrentPrice = extractPriceFromInputField(screenshot)
            
            if (topMarketPrice == null || myCurrentPrice == null) {
                updateState(StateType.ERROR_RETRY, "OCR failed")
                uiInteractor.performTap(
                    calibration.closeButtonX,
                    calibration.closeButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
                handleRowIteration()
                return
            }
            
            updateState(StateType.SCAN_OCR, "Market: $topMarketPrice, Mine: $myCurrentPrice")
            
            if (myCurrentPrice >= topMarketPrice) {
                updateState(StateType.EXECUTE_TAP, "Already highest")
                uiInteractor.performTap(
                    calibration.closeButtonX,
                    calibration.closeButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
            } else {
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
                
                updateState(StateType.EXECUTE_BUTTON, "Updating order")
                uiInteractor.performTap(
                    calibration.confirmButtonX,
                    calibration.confirmButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
            }
            
            handleRowIteration()
            delay(calibration.pixelPollingIntervalMs)
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
        }
    }
    
    private fun calculateRowCoordinates(rowIndex: Int): Pair<Int, Int> {
        val x = calibration.firstRowX
        val y = calibration.firstRowY + (rowIndex * calibration.rowYOffset)
        return Pair(x, y)
    }
    
    private suspend fun handleRowIteration() {
        if (currentRowIndex % calibration.maxRowsPerScreen == 0) {
            updateState(StateType.SCROLL_NEXT_ROW, "Swiping...")
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
    }
}
