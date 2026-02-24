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
    var onError: ((String) -> Unit)? = null
    
    fun startMode(mode: OperationMode) {
        if (isRunning) {
            onError?.invoke("Automation already running")
            return
        }
        isRunning = true
        currentMode = mode
        currentRowIndex = 0
        loopJob = scope.launch { mainLoop() }
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
            onError?.invoke("Error: ${e.message}")
        }
    }
    
    private suspend fun executeNewOrderSweeperLoop() {
        try {
            val (rowX, rowY) = calculateRowCoordinates(currentRowIndex)
            updateState(StateType.EXECUTE_TAP, "Tapping row")
            
            uiInteractor.performTap(rowX, rowY, calibration.tapDurationMs)
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting...")
            delay(calibration.popupOpenWaitMs)
            
            val screenshot = screenCaptureManager.captureScreen()
            val highlightDetected = if (screenshot != null) {
                detectHighlightedRow(screenshot)
            } else {
                false
            }
            
            if (highlightDetected) {
                updateState(StateType.EXECUTE_TAP, "Closing")
                uiInteractor.performTap(
                    calibration.closeButtonX,
                    calibration.closeButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
            } else {
                updateState(StateType.SCAN_OCR, "Reading price")
                val topPrice = extractTopMarketPrice(screenshot)
                
                if (topPrice != null) {
                    val newPrice = topPrice + 5
                    updateState(StateType.EXECUTE_TEXT_INPUT, "Setting price")
                    
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
                    
                    updateState(StateType.EXECUTE_BUTTON, "Creating")
                    uiInteractor.performTap(
                        calibration.confirmButtonX,
                        calibration.confirmButtonY,
                        calibration.tapDurationMs
                    )
                    delay(calibration.popupCloseWaitMs)
                }
            }
            
            handleRowIteration()
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error")
        }
    }
    
    private suspend fun executeOrderEditorLoop() {
        try {
            val (editX, editY) = calculateRowCoordinates(currentRowIndex)
            updateState(StateType.EXECUTE_TAP, "Editing")
            
            uiInteractor.performTap(editX, editY, calibration.tapDurationMs)
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting")
            delay(calibration.popupOpenWaitMs)
            
            val screenshot = screenCaptureManager.captureScreen()
            val topPrice = extractTopMarketPrice(screenshot)
            val myPrice = extractPriceFromInputField(screenshot)
            
            if (topPrice == null || myPrice == null) {
                uiInteractor.performTap(
                    calibration.closeButtonX,
                    calibration.closeButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
                handleRowIteration()
                return
            }
            
            if (myPrice >= topPrice) {
                updateState(StateType.EXECUTE_TAP, "Already high")
                uiInteractor.performTap(
                    calibration.closeButtonX,
                    calibration.closeButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
            } else {
                val newPrice = topPrice + 1
                updateState(StateType.EXECUTE_TEXT_INPUT, "Updating")
                
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
                
                updateState(StateType.EXECUTE_BUTTON, "Updating")
                uiInteractor.performTap(
                    calibration.confirmButtonX,
                    calibration.confirmButtonY,
                    calibration.tapDurationMs
                )
                delay(calibration.popupCloseWaitMs)
            }
            
            handleRowIteration()
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error")
        }
    }
    
    private fun calculateRowCoordinates(rowIndex: Int): Pair<Int, Int> {
        val x = calibration.firstRowX
        val y = calibration.firstRowY + (rowIndex * calibration.rowYOffset)
        return Pair(x, y)
    }
    
    private suspend fun handleRowIteration() {
        if (currentRowIndex % calibration.maxRowsPerScreen == 0) {
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
            colorDetector.detectColor(
                screenshot,
                calibration.getBuyOrdersRegion(),
                calibration.highlightedRowColorHex,
                calibration.colorToleranceRGB
            ).isMatch
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun extractTopMarketPrice(screenshot: android.graphics.Bitmap?): Int? {
        if (screenshot == null) return null
        return try {
            ocrEngine.recognizeText(
                screenshot,
                calibration.getBuyOrdersRegion(),
                calibration.ocrLanguage
            ).firstOrNull { it.isNumber }?.numericValue
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun extractPriceFromInputField(screenshot: android.graphics.Bitmap?): Int? {
        if (screenshot == null) return null
        return try {
            ocrEngine.recognizeText(
                screenshot,
                calibration.getPriceInputRegion(),
                calibration.ocrLanguage
            ).firstOrNull { it.isNumber }?.numericValue
        } catch (e: Exception) {
            null
        }
    }
    
    private fun updateState(stateType: StateType, message: String = "") {
        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            currentX = 0,
            currentY = 0,
            errorMessage = if (stateType == StateType.ERROR_RETRY) message else null
        )
        _stateFlow.value = state
        onStateChange?.invoke(state)
    }
}
