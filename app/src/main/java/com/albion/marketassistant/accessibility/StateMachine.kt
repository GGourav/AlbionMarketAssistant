package com.albion.marketassistant.accessibility

import com.albion.marketassistant.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

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
    private var retryCount = AtomicInteger(0)
    private var lastKnownPrice: Int? = null
    
    var onStateChange: ((AutomationState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onPriceSanityError: ((String) -> Unit)? = null
    
    fun startMode(mode: OperationMode) {
        if (isRunning) {
            onError?.invoke("Already running")
            return
        }
        isRunning = true
        isPaused = false
        currentMode = mode
        currentRowIndex = 0
        retryCount.set(0)
        lastKnownPrice = null
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
    
    private fun Long.withLagMultiplier(): Long {
        return (this * calibration.global.networkLagMultiplier).toLong()
    }
    
    private suspend fun mainLoop() {
        try {
            while (isRunning) {
                while (isPaused) {
                    delay(100)
                }
                
                when (currentMode) {
                    OperationMode.NEW_ORDER_SWEEPER -> executeCreateOrderLoop()
                    OperationMode.ORDER_EDITOR -> executeEditOrderLoop()
                    OperationMode.IDLE -> return
                }
                
                currentRowIndex++
                
                updateState(StateType.COOLDOWN, "Cooldown")
                delay(calibration.global.cycleCooldownMs)
            }
        } catch (e: CancellationException) {
        } catch (e: Exception) {
            isRunning = false
            onError?.invoke("Error: ${e.message}")
        }
    }
    
    private suspend fun handleUnexpectedPopup() {
        val safety = calibration.safety
        
        if (!safety.autoDismissErrors) return
        
        updateState(StateType.HANDLE_ERROR_POPUP, "Handling error popup")
        
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
        
        uiInteractor.performTap(closeX, closeY, calibration.global.tapDurationMs)
        delay(300.withLagMultiplier())
        
        uiInteractor.dismissKeyboard()
        delay(300.withLagMultiplier())
    }
    
    private fun validatePrice(price: Int?): PriceValidationResult {
        if (price == null) {
            return PriceValidationResult(false, "Could not read price")
        }
        
        val safety = calibration.safety
        
        if (price < safety.minPriceCap) {
            return PriceValidationResult(false, "Price below minimum: $price")
        }
        
        if (price > safety.maxPriceCap) {
            return PriceValidationResult(false, "Price exceeds cap: $price > ${safety.maxPriceCap}")
        }
        
        if (safety.enableOcrSanityCheck && lastKnownPrice != null) {
            val changePercent = kotlin.math.abs(price - lastKnownPrice!!).toFloat() / lastKnownPrice!!
            if (changePercent > safety.maxPriceChangePercent) {
                return PriceValidationResult(
                    false, 
                    "Price change too large: ${(changePercent * 100).toInt()}%"
                )
            }
        }
        
        return PriceValidationResult(true, "OK")
    }
    
    data class PriceValidationResult(val isValid: Boolean, val message: String)
    
    private suspend fun executeWithRetry(maxRetries: Int, action: suspend () -> Boolean): Boolean {
        var attempts = 0
        
        while (attempts < maxRetries) {
            if (action()) {
                retryCount.set(0)
                return true
            }
            
            attempts++
            retryCount.set(attempts)
            
            if (attempts >= maxRetries) {
                handleUnexpectedPopup()
            }
            
            delay(500.withLagMultiplier())
        }
        
        return false
    }
    
    private suspend fun executeCreateOrderLoop() {
        try {
            val config = calibration.createMode
            val timing = calibration.global
            val safety = calibration.safety
            
            val rowX = config.firstRowX
            val rowY = config.firstRowY + (currentRowIndex * config.rowYOffset)
            
            updateState(StateType.EXECUTE_TAP, "Tapping row $currentRowIndex")
            if (!executeWithRetry(safety.maxRetries) {
                uiInteractor.performTap(rowX, rowY, timing.tapDurationMs)
            }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap row")
                handleUnexpectedPopup()
                return
            }
            
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup")
            delay(timing.popupOpenWaitMs.withLagMultiplier())
            
            updateState(StateType.EXECUTE_TAP, "Tapping price input")
            if (!executeWithRetry(safety.maxRetries) {
                uiInteractor.performTap(config.priceInputX, config.priceInputY, timing.tapDurationMs)
            }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap price box")
                handleUnexpectedPopup()
                return
            }
            
            delay(100.withLagMultiplier())
            
            val priceToInject = calculatePrice()
            val validation = validatePrice(priceToInject)
            
            if (!validation.isValid) {
                updateState(StateType.ERROR_PRICE_SANITY, validation.message)
                onPriceSanityError?.invoke(validation.message)
                handleUnexpectedPopup()
                return
            }
            
            updateState(StateType.EXECUTE_TEXT_INPUT, "Injecting price: $priceToInject")
            uiInteractor.clearTextField()
            delay(50.withLagMultiplier())
            
            if (!uiInteractor.injectText(priceToInject.toString())) {
                updateState(StateType.ERROR_RETRY, "Failed to inject text")
                handleUnexpectedPopup()
                return
            }
            
            lastKnownPrice = priceToInject
            
            delay(timing.textInputDelayMs.withLagMultiplier())
            
            updateState(StateType.DISMISS_KEYBOARD, "Dismissing keyboard")
            uiInteractor.dismissKeyboard()
            delay(timing.keyboardDismissDelayMs.withLagMultiplier())
            
            updateState(StateType.EXECUTE_BUTTON, "Creating order")
            if (!executeWithRetry(safety.maxRetries) {
                uiInteractor.performTap(config.createButtonX, config.createButtonY, timing.tapDurationMs)
            }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap create button")
                handleUnexpectedPopup()
                return
            }
            
            delay(timing.confirmationWaitMs.withLagMultiplier())
            
            updateState(StateType.HANDLE_CONFIRMATION, "Checking confirmation")
            uiInteractor.performTap(config.confirmYesX, config.confirmYesY, timing.tapDurationMs)
            
            delay(timing.popupCloseWaitMs.withLagMultiplier())
            
            handleRowIteration(config.maxRowsPerScreen, timing)
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
            handleUnexpectedPopup()
        }
    }
    
    private suspend fun executeEditOrderLoop() {
        try {
            val config = calibration.editMode
            val timing = calibration.global
            val safety = calibration.safety
            
            val editX = config.editButtonX
            val editY = config.editButtonY + (currentRowIndex * config.editButtonYOffset)
            
            updateState(StateType.EXECUTE_TAP, "Tapping edit button $currentRowIndex")
            if (!executeWithRetry(safety.maxRetries) {
                uiInteractor.performTap(editX, editY, timing.tapDurationMs)
            }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap edit button")
                handleUnexpectedPopup()
                return
            }
            
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup")
            delay(timing.popupOpenWaitMs.withLagMultiplier())
            
            updateState(StateType.EXECUTE_TAP, "Tapping price input")
            if (!executeWithRetry(safety.maxRetries) {
                uiInteractor.performTap(config.priceInputX, config.priceInputY, timing.tapDurationMs)
            }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap price box")
                handleUnexpectedPopup()
                return
            }
            
            delay(100.withLagMultiplier())
            
            val priceToInject = calculatePrice()
            val validation = validatePrice(priceToInject)
            
            if (!validation.isValid) {
                updateState(StateType.ERROR_PRICE_SANITY, validation.message)
                onPriceSanityError?.invoke(validation.message)
                handleUnexpectedPopup()
                return
            }
            
            updateState(StateType.EXECUTE_TEXT_INPUT, "Injecting price: $priceToInject")
            uiInteractor.clearTextField()
            delay(50.withLagMultiplier())
            
            if (!uiInteractor.injectText(priceToInject.toString())) {
                updateState(StateType.ERROR_RETRY, "Failed to inject text")
                handleUnexpectedPopup()
                return
            }
            
            lastKnownPrice = priceToInject
            
            delay(timing.textInputDelayMs.withLagMultiplier())
            
            updateState(StateType.DISMISS_KEYBOARD, "Dismissing keyboard")
            uiInteractor.dismissKeyboard()
            delay(timing.keyboardDismissDelayMs.withLagMultiplier())
            
            updateState(StateType.EXECUTE_BUTTON, "Updating order")
            if (!executeWithRetry(safety.maxRetries) {
                uiInteractor.performTap(config.updateButtonX, config.updateButtonY, timing.tapDurationMs)
            }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap update button")
                handleUnexpectedPopup()
                return
            }
            
            delay(timing.confirmationWaitMs.withLagMultiplier())
            
            updateState(StateType.HANDLE_CONFIRMATION, "Checking confirmation")
            uiInteractor.performTap(config.confirmYesX, config.confirmYesY, timing.tapDurationMs)
            
            delay(timing.popupCloseWaitMs.withLagMultiplier())
            
            handleRowIteration(5, timing)
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
            handleUnexpectedPopup()
        }
    }
    
    private fun calculatePrice(): Int {
        val basePrice = lastKnownPrice ?: 1000
        return basePrice + 1
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
            delay(300.withLagMultiplier())
        }
    }
    
    private fun updateState(stateType: StateType, message: String = "") {
        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            errorMessage = if (stateType.name.startsWith("ERROR")) message else null,
            isPaused = isPaused,
            retryCount = retryCount.get(),
            lastPrice = lastKnownPrice
        )
        _stateFlow.value = state
        onStateChange?.invoke(state)
    }
}
