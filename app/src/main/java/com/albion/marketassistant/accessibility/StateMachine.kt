package com.albion.marketassistant.statemachine

import com.albion.marketassistant.accessibility.UIInteractor
import com.albion.marketassistant.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StateMachine(
    private val scope: CoroutineScope,
    private val calibration: CalibrationData,
    private val uiInteractor: UIInteractor
) {
    
    private val _stateFlow = MutableStateFlow<AutomationState>(AutomationState(StateType.IDLE))
    val stateFlow: StateFlow<AutomationState> = _stateFlow
    
    private var isRunning = false
    private var currentMode = OperationMode.IDLE
    private var currentRowIndex = 0
    private var loopJob: Job? = null
    
    var onStateChange: ((AutomationState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    fun startMode(mode: OperationMode) {
        if (isRunning) {
            onError?.invoke("Already running")
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
                    OperationMode.NEW_ORDER_SWEEPER -> executeSweeperLoop()
                    OperationMode.ORDER_EDITOR -> executeEditorLoop()
                    OperationMode.IDLE -> return
                }
                currentRowIndex++
            }
        } catch (e: Exception) {
            isRunning = false
            onError?.invoke("Error: ${e.message}")
        }
    }
    
    private suspend fun executeSweeperLoop() {
        try {
            val (rowX, rowY) = calculateRowCoordinates(currentRowIndex)
            updateState(StateType.EXECUTE_TAP, "Tapping row $currentRowIndex")
            uiInteractor.performTap(rowX, rowY, calibration.tapDurationMs)
            delay(calibration.popupOpenWaitMs)
            
            updateState(StateType.SCAN_OCR, "Scanning...")
            delay(500)
            
            updateState(StateType.EXECUTE_TEXT_INPUT, "Injecting text...")
            uiInteractor.injectText("1000")
            delay(calibration.textInputDelayMs)
            
            updateState(StateType.EXECUTE_BUTTON, "Creating order...")
            uiInteractor.performTap(calibration.confirmButtonX, calibration.confirmButtonY, calibration.tapDurationMs)
            delay(calibration.popupCloseWaitMs)
            
            handleRowIteration()
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
        }
    }
    
    private suspend fun executeEditorLoop() {
        try {
            val (editX, editY) = calculateRowCoordinates(currentRowIndex)
            updateState(StateType.EXECUTE_TAP, "Editing order $currentRowIndex")
            uiInteractor.performTap(editX, editY, calibration.tapDurationMs)
            delay(calibration.popupOpenWaitMs)
            
            updateState(StateType.SCAN_OCR, "Reading price...")
            delay(500)
            
            updateState(StateType.EXECUTE_TEXT_INPUT, "Updating price...")
            uiInteractor.clearTextField()
            delay(100)
            uiInteractor.injectText("1001")
            delay(calibration.textInputDelayMs)
            
            updateState(StateType.EXECUTE_BUTTON, "Updating order...")
            uiInteractor.performTap(calibration.confirmButtonX, calibration.confirmButtonY, calibration.tapDurationMs)
            delay(calibration.popupCloseWaitMs)
            
            handleRowIteration()
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
    
    private fun updateState(stateType: StateType, message: String = "") {
        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            errorMessage = if (stateType == StateType.ERROR_RETRY) message else null
        )
        _stateFlow.value = state
        onStateChange?.invoke(state)
    }
}

