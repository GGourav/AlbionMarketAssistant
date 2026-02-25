package com.albion.marketassistant.accessibility

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
    private var isPaused = false
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
        isPaused = false
        currentMode = mode
        currentRowIndex = 0
        loopJob = scope.launch { mainLoop() }
    }
    
    fun pause() {
        isPaused = true
        updateState(StateType.PAUSED, "Paused")
    }
    
    fun resume() {
        isPaused = false
        updateState(StateType.IDLE, "Resumed")
    }
    
    fun isPaused(): Boolean = isPaused
    
    fun stop() {
        isRunning = false
        isPaused = false
        loopJob?.cancel()
        currentMode = OperationMode.IDLE
        _stateFlow.value = AutomationState(StateType.IDLE)
    }
    
    private suspend fun mainLoop() {
        try {
            while (isRunning) {
                // Check for pause
                while (isPaused) {
                    delay(100)
                }
                
                when (currentMode) {
                    OperationMode.NEW_ORDER_SWEEPER -> executeCreateOrderLoop()
                    OperationMode.ORDER_EDITOR -> executeEditOrderLoop()
                    OperationMode.IDLE -> return
                }
                
                currentRowIndex++
                
                // Delay between iterations
                delay(300)
            }
        } catch (e: CancellationException) {
            // Normal cancellation
        } catch (e: Exception) {
            isRunning = false
            onError?.invoke("Error: ${e.message}")
        }
    }
    
    /**
     * CREATE ORDER WORKFLOW:
     * 1. Tap Row -> Wait for popup
     * 2. Tap Price Box -> Clear Text -> Inject Price
     * 3. Tap Create Button -> Wait
     * 4. Handle Confirmation Popup (tap Yes if appears)
     */
    private suspend fun executeCreateOrderLoop() {
        try {
            val config = calibration.createMode
            val timing = calibration.global
            
            // Step 1: Calculate row coordinates and tap
            val rowX = config.firstRowX
            val rowY = config.firstRowY + (currentRowIndex * config.rowYOffset)
            
            updateState(StateType.EXECUTE_TAP, "Tapping row $currentRowIndex")
            if (!uiInteractor.performTap(rowX, rowY, timing.tapDurationMs)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap row")
                return
            }
            
            delay(timing.popupOpenWaitMs)
            
            // Step 2: Tap price input box
            updateState(StateType.EXECUTE_TAP, "Tapping price input")
            if (!uiInteractor.performTap(config.priceInputX, config.priceInputY, timing.tapDurationMs)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap price box")
                return
            }
            
            delay(100)
            
            // Clear and inject text (without keyboard)
            updateState(StateType.EXECUTE_TEXT_INPUT, "Injecting price")
            uiInteractor.clearTextField()
            delay(50)
            if (!uiInteractor.injectText("1000")) {
                updateState(StateType.ERROR_RETRY, "Failed to inject text")
                return
            }
            
            delay(timing.textInputDelayMs)
            
            // Dismiss keyboard if it appeared
            uiInteractor.dismissKeyboard()
            delay(timing.keyboardDismissDelayMs)
            
            // Step 3: Tap Create button
            updateState(StateType.EXECUTE_BUTTON, "Creating order")
            if (!uiInteractor.performTap(config.createButtonX, config.createButtonY, timing.tapDurationMs)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap create button")
                return
            }
            
            delay(timing.confirmationWaitMs)
            
            // Step 4: Handle confirmation popup
            updateState(StateType.HANDLE_CONFIRMATION, "Checking confirmation")
            uiInteractor.performTap(config.confirmYesX, config.confirmYesY, timing.tapDurationMs)
            
            delay(timing.popupCloseWaitMs)
            
            // Step 5: Handle scrolling if needed
            handleRowIteration(config.maxRowsPerScreen, timing)
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
        }
    }
    
    /**
     * EDIT ORDER WORKFLOW:
     * 1. Tap Edit Button -> Wait for popup
     * 2. Tap Price Box -> Clear Text -> Inject New Price
     * 3. Tap Update Button -> Wait
     * 4. Handle Confirmation Popup (tap Yes if appears)
     */
    private suspend fun executeEditOrderLoop() {
        try {
            val config = calibration.editMode
            val timing = calibration.global
            
            // Step 1: Calculate edit button coordinates and tap
            val editX = config.editButtonX
            val editY = config.editButtonY + (currentRowIndex * config.editButtonYOffset)
            
            updateState(StateType.EXECUTE_TAP, "Tapping edit button $currentRowIndex")
            if (!uiInteractor.performTap(editX, editY, timing.tapDurationMs)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap edit button")
                return
            }
            
            delay(timing.popupOpenWaitMs)
            
            // Step 2: Tap price input box
            updateState(StateType.EXECUTE_TAP, "Tapping price input")
            if (!uiInteractor.performTap(config.priceInputX, config.priceInputY, timing.tapDurationMs)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap price box")
                return
            }
            
            delay(100)
            
            // Clear and inject text (without keyboard)
            updateState(StateType.EXECUTE_TEXT_INPUT, "Injecting price")
            uiInteractor.clearTextField()
            delay(50)
            if (!uiInteractor.injectText("1001")) {
                updateState(StateType.ERROR_RETRY, "Failed to inject text")
                return
            }
            
            delay(timing.textInputDelayMs)
            
            // Dismiss keyboard if it appeared
            uiInteractor.dismissKeyboard()
            delay(timing.keyboardDismissDelayMs)
            
            // Step 3: Tap Update button
            updateState(StateType.EXECUTE_BUTTON, "Updating order")
            if (!uiInteractor.performTap(config.updateButtonX, config.updateButtonY, timing.tapDurationMs)) {
                updateState(StateType.ERROR_RETRY, "Failed to tap update button")
                return
            }
            
            delay(timing.confirmationWaitMs)
            
            // Step 4: Handle confirmation popup
            updateState(StateType.HANDLE_CONFIRMATION, "Checking confirmation")
            uiInteractor.performTap(config.confirmYesX, config.confirmYesY, timing.tapDurationMs)
            
            delay(timing.popupCloseWaitMs)
            
            // Step 5: Handle scrolling if needed
            handleRowIteration(5, timing) // Default max rows for edit mode
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
        }
    }
    
    private suspend fun handleRowIteration(maxRows: Int, timing: GlobalSettings) {
        if (currentRowIndex > 0 && currentRowIndex % maxRows == 0) {
            updateState(StateType.SCROLL_NEXT_ROW, "Scrolling...")
            uiInteractor.performSwipe(
                timing.swipeStartX,
                timing.swipeStartY,
                timing.swipeEndX,
                timing.swipeEndY,
                timing.swipeDurationMs.toLong()
            )
            delay(300)
        }
    }
    
    private fun updateState(stateType: StateType, message: String = "") {
        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            errorMessage = if (stateType == StateType.ERROR_RETRY) message else null,
            isPaused = isPaused
        )
        _stateFlow.value = state
        onStateChange?.invoke(state)
    }
}
