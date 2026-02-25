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
    
    // End-of-list detection
    private var previousFirstRowText: String? = null
    private var samePageCount = 0
    private var totalItemsProcessed = 0
    
    var onStateChange: ((AutomationState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onPriceSanityError: ((String) -> Unit)? = null
    var onEndOfList: ((String) -> Unit)? = null
    var onWrongApp: ((String) -> Unit)? = null
    
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
        previousFirstRowText = null
        samePageCount = 0
        totalItemsProcessed = 0
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
    
    /**
     * IMMERSIVE MODE PROTECTION
     * Verify we're in the correct app before every action
     */
    private fun verifyCorrectApp(): Boolean {
        if (!calibration.safety.enableAppVerification) {
            return true
        }
        
        val targetPackage = calibration.global.targetAppPackage
        val isCorrect = uiInteractor.isCorrectApp(targetPackage)
        
        if (!isCorrect) {
            val currentApp = uiInteractor.getCurrentPackage() ?: "unknown"
            updateStateWithApp(StateType.ERROR_WRONG_APP, "Wrong app: $currentApp", currentApp, false)
            onWrongApp?.invoke("Switched to: $currentApp")
            
            if (calibration.safety.pauseOnInterruption) {
                pause()
            }
            return false
        }
        
        return true
    }
    
    /**
     * END OF LIST DETECTION
     * Compare first row text before and after swipe
     */
    private fun checkEndOfList(): Boolean {
        if (!calibration.safety.enableEndOfListDetection) {
            return false
        }
        
        // In a full implementation, we would use OCR to read the first row
        // For now, we use a simplified approach based on row index
        
        // If we've processed items but row index keeps resetting after swipe
        // it means we're at the end
        if (samePageCount >= calibration.global.maxSamePageCount) {
            return true
        }
        
        return false
    }
    
    private suspend fun mainLoop() {
        try {
            while (isRunning) {
                // Check for pause
                while (isPaused) {
                    delay(100)
                }
                
                // IMMERSIVE MODE PROTECTION - Verify app before each iteration
                if (!verifyCorrectApp()) {
                    delay(1000)
                    continue
                }
                
                when (currentMode) {
                    OperationMode.NEW_ORDER_SWEEPER -> executeCreateOrderLoop()
                    OperationMode.ORDER_EDITOR -> executeEditOrderLoop()
                    OperationMode.IDLE -> return
                }
                
                currentRowIndex++
                totalItemsProcessed++
                
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
            // Verify app before each retry
            if (!verifyCorrectApp()) {
                return false
            }
            
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
            
            // Verify app is in foreground
            if (!verifyCorrectApp()) return
            
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
            
            // Verify app again
            if (!verifyCorrectApp()) return
            
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
            
            // Verify app again
            if (!verifyCorrectApp()) return
            
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
            
            // Handle row iteration with end-of-list detection
            handleRowIterationWithEndDetection(config.maxRowsPerScreen, timing)
            
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
            
            // Verify app is in foreground
            if (!verifyCorrectApp()) return
            
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
            
            // Verify app again
            if (!verifyCorrectApp()) return
            
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
            
            // Verify app again
            if (!verifyCorrectApp()) return
            
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
            
            // Handle row iteration with end-of-list detection
            handleRowIterationWithEndDetection(5, timing)
            
        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
            handleUnexpectedPopup()
        }
    }
    
    /**
     * SWIPE OVERLAP CALIBRATION
     * Handle scrolling with proper calibration
     */
    private suspend fun handleRowIterationWithEndDetection(maxRows: Int, timing: GlobalSettings) {
        if (currentRowIndex > 0 && currentRowIndex % maxRows == 0) {
            updateState(StateType.SCROLL_NEXT_ROW, "Scrolling...")
            
            // Calculate swipe distance
            // Formula: swipeDistance = maxRows * rowYOffset
            val swipeDistance = timing.swipeStartY - timing.swipeEndY
            
            // Perform the swipe
            uiInteractor.performSwipe(
                timing.swipeStartX,
                timing.swipeStartY,
                timing.swipeEndX,
                timing.swipeEndY,
                timing.swipeDurationMs.toLong()
            )
            
            delay(500.withLagMultiplier())
            
            // END OF LIST DETECTION
            // In full implementation, we would:
            // 1. Capture first row OCR text before swipe
            // 2. Capture first row OCR text after swipe
            // 3. Compare - if same, we're at end of list
            
            // Simplified detection: increment same page counter
            // In real implementation, this would use actual OCR comparison
            samePageCount++
            
            if (checkEndOfList()) {
                updateState(StateType.ERROR_END_OF_LIST, "End of list reached after $totalItemsProcessed items")
                onEndOfList?.invoke("Processed $totalItemsProcessed items total")
                stop()
                return
            }
            
            // Reset row index for new page
            currentRowIndex = 0
        }
    }
    
    private fun calculatePrice(): Int {
        val basePrice = lastKnownPrice ?: 1000
        return basePrice + 1
    }
    
    private fun updateState(stateType: StateType, message: String = "") {
        val currentApp = uiInteractor.getCurrentPackage() ?: "unknown"
        val isCorrect = uiInteractor.isCorrectApp(calibration.global.targetAppPackage)
        
        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            errorMessage = if (stateType.name.startsWith("ERROR")) message else null,
            isPaused = isPaused,
            retryCount = retryCount.get(),
            lastPrice = lastKnownPrice,
            currentAppName = currentApp,
            isCorrectApp = isCorrect
        )
        _stateFlow.value = state
        onStateChange?.invoke(state)
    }
    
    private fun updateStateWithApp(stateType: StateType, message: String, appName: String, isCorrect: Boolean) {
        val state = AutomationState(
            stateType = stateType,
            mode = currentMode,
            currentRowIndex = currentRowIndex,
            errorMessage = message,
            isPaused = isPaused,
            retryCount = retryCount.get(),
            lastPrice = lastKnownPrice,
            currentAppName = appName,
            isCorrectApp = isCorrect
        )
        _stateFlow.value = state
        onStateChange?.invoke(state)
    }
}
