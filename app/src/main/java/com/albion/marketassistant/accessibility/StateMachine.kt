package com.albion.marketassistant.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.albion.marketassistant.data.*
import com.albion.marketassistant.ml.ColorDetector
import com.albion.marketassistant.ml.OCREngine
import com.albion.marketassistant.statistics.StatisticsManager
import com.albion.marketassistant.util.DeviceUtils
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

    private val windowLostCount = AtomicInteger(0)
    private val lastWindowCheckTime = AtomicLong(0)

    // Fixed: ColorDetector created but methods can be used if needed
    private val colorDetector = ColorDetector()

    private val randomizationHelper = RandomizationHelper(calibration.antiDetection)
    private val deviceUtils = context?.let { DeviceUtils(it) }
    private var statisticsManager: StatisticsManager? = null

    var onStateChange: ((AutomationState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onPriceSanityError: ((String) -> Unit)? = null
    var onEndOfList: (() -> Unit)? = null
    var onWindowLost: (() -> Unit)? = null
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
        windowLostCount.set(0)
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
        windowLostCount.set(0)
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

    private suspend fun mainLoop() {
        try {
            while (isRunning.get()) {
                while (isPaused.get()) {
                    delay(100)
                }

                if (!checkBatteryOptimization()) {
                    pause()
                    updateState(StateType.ERROR_BATTERY_LOW, "Battery too low - paused")
                    continue
                }

                if (!verifyGameWindow()) {
                    continue
                }

                if (checkStuckState()) {
                    handleStuckRecovery()
                    continue
                }

                when (currentMode) {
                    OperationMode.NEW_ORDER_SWEEPER -> executeCreateOrderLoop()
                    OperationMode.ORDER_EDITOR -> executeEditOrderLoop()
                    OperationMode.IDLE -> return
                }

                val cycle = totalCycleCount.incrementAndGet()
                statisticsManager?.recordCycle()
                currentRowIndex++

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
                val cooldownDelay = randomizationHelper.getRandomizedDelay(calibration.global.cycleCooldownMs)

                if (randomizationHelper.shouldAddHesitation(cycle % 20)) {
                    delay(randomizationHelper.getHesitationDelay())
                }

                delay(cooldownDelay)
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

    private suspend fun checkBatteryOptimization(): Boolean {
        val batterySettings = calibration.battery
        if (!batterySettings.enableBatteryOptimization) return true

        deviceUtils?.let { utils ->
            if (utils.isBatteryLow(batterySettings.pauseOnBatteryBelow) && !utils.isCharging()) {
                return false
            }
        }
        return true
    }

    private suspend fun verifyGameWindow(): Boolean {
        val immersiveSettings = calibration.immersiveMode
        if (!immersiveSettings.enableWindowVerification) return true

        val now = System.currentTimeMillis()
        if (now - lastWindowCheckTime.get() < immersiveSettings.windowCheckIntervalMs) {
            return true
        }
        lastWindowCheckTime.set(now)

        deviceUtils?.let { utils ->
            val isGameActive = utils.isAppInForeground(immersiveSettings.gamePackageName)

            if (!isGameActive) {
                val lostCount = windowLostCount.incrementAndGet()
                updateState(StateType.VERIFY_GAME_WINDOW, "Game window check failed ($lostCount/${immersiveSettings.windowLostThreshold})")

                if (lostCount >= immersiveSettings.windowLostThreshold) {
                    handleWindowLost(immersiveSettings)
                    return false
                }
            } else {
                windowLostCount.set(0)
            }
        }
        return true
    }

    private suspend fun handleWindowLost(settings: ImmersiveModeSettings) {
        statisticsManager?.recordFailure("Game window lost")

        when (settings.actionOnWindowLost) {
            "pause" -> {
                updateState(StateType.ERROR_WINDOW_LOST, "Game window lost - paused")
                onWindowLost?.invoke()
                pause()
            }
            "stop" -> {
                updateState(StateType.ERROR_WINDOW_LOST, "Game window lost - stopped")
                onWindowLost?.invoke()
                stop()
            }
        }
    }

    private fun checkStuckState(): Boolean {
        val errorSettings = calibration.errorRecovery
        if (!errorSettings.enableSmartRecovery) return false
        return statisticsManager?.isStuck(errorSettings.maxStateStuckTimeMs) ?: false
    }

    private suspend fun handleStuckRecovery() {
        val errorSettings = calibration.errorRecovery
        statisticsManager?.recordFailure("Stuck state detected")
        updateState(StateType.ERROR_STUCK_DETECTED, "Stuck detected - recovering")

        if (errorSettings.screenshotOnError) {
            onScreenshotRequest?.invoke()?.let { bitmap ->
                statisticsManager?.saveScreenshot(bitmap, "stuck")
            }
        }

        when (errorSettings.actionOnStuck) {
            "restart" -> {
                updateState(StateType.RECOVERING, "Auto-restarting...")
                delay(errorSettings.autoRestartDelayMs)
                currentRowIndex = 0
                lastPageText = ""
                endOfListDetectionCount.set(0)
                statisticsManager?.resetConsecutiveErrors()
            }
            "pause" -> pause()
            "stop" -> stop()
        }
    }

    private suspend fun checkEndOfList(): Boolean {
        val eolSettings = calibration.endOfList
        if (!eolSettings.enableEndOfListDetection) return false

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

        if (calibration.swipeOverlap.enableScrollTracking) {
            currentRowIndex = 0
            lastPageText = ""
            endOfListDetectionCount.set(0)
            updateState(StateType.COOLDOWN, "Restarting from top...")
            delay(2000)
        } else {
            stop()
        }
    }

    /**
     * Fixed: Actually verify UI element by checking screenshot
     */
    private suspend fun verifyUIElement(
        x: Int, 
        y: Int, 
        expectedColorHex: String, 
        timeoutMs: Long
    ): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isPaused.get()) {
                delay(100)
                continue
            }
            
            // Take screenshot and check color
            val bitmap = onScreenshotRequest?.invoke()
            if (bitmap != null && !bitmap.isRecycled) {
                try {
                    val isMatch = colorDetector.isColorMatch(
                        bitmap, x, y, expectedColorHex, 
                        calibration.global.colorToleranceRGB
                    )
                    if (isMatch) {
                        return true
                    }
                } finally {
                    // Don't recycle - bitmap is cached
                }
            }
            
            delay(100)
        }
        
        return false
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

        performTapWithRandomization(closeX, closeY)
        delay(300L.withDelays())
        uiInteractor.dismissKeyboard()
        delay(300L.withDelays())
    }

    private fun performTapWithRandomization(x: Int, y: Int): Boolean {
        val duration = randomizationHelper.getRandomizedDuration(calibration.global.tapDurationMs)
        return uiInteractor.performTap(x, y, duration)
    }

    private fun performSwipeWithRandomization(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
        val swipeSettings = calibration.swipeOverlap
        val adjustedStartY = if (swipeSettings.enableSwipeOverlap) {
            startY + (swipeSettings.overlapRowCount * calibration.createMode.rowYOffset)
        } else {
            startY
        }

        val distance = abs(endY - adjustedStartY)
        val randomizedDistance = randomizationHelper.getRandomizedSwipeDistance(distance)
        val actualEndY = adjustedStartY - randomizedDistance
        val duration = randomizationHelper.getRandomizedDuration(calibration.global.swipeDurationMs.toLong())

        return uiInteractor.performSwipe(startX, adjustedStartY, endX, actualEndY, duration)
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
            val changePercent = abs(price - lastKnownPrice!!).toFloat() / lastKnownPrice!!
            if (changePercent > safety.maxPriceChangePercent) {
                val percentInt = (changePercent * 100).toInt()
                return PriceValidationResult(false, "Price change too large: $percentInt%")
            }
        }

        statisticsManager?.let { stats ->
            if (calibration.priceHistory.enableAnomalyDetection) {
                if (stats.detectPriceAnomaly("current", price)) {
                    return PriceValidationResult(false, "Price anomaly detected")
                }
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
            statisticsManager?.recordFailure("Retry attempt $attempts")

            if (attempts >= maxRetries) {
                handleUnexpectedPopup()
            }

            delay(randomizationHelper.getRandomDelay(300, 700))
        }
        return false
    }

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

            val rowX = config.firstRowX
            val rowY = config.firstRowY + (currentRowIndex * config.rowYOffset)

            updateState(StateType.EXECUTE_TAP, "Tapping row $currentRowIndex")
            if (!executeWithRetry(safety.maxRetries) { performTapWithRandomization(rowX, rowY) }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap row after ${safety.maxRetries} retries")
                statisticsManager?.recordFailure("Tap row failed")
                handleUnexpectedPopup()
                return
            }

            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup")
            delay(timing.popupOpenWaitMs.withDelays())

            updateState(StateType.EXECUTE_TAP, "Tapping price input")
            if (!executeWithRetry(safety.maxRetries) { performTapWithRandomization(config.priceInputX, config.priceInputY) }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap price box")
                statisticsManager?.recordFailure("Tap price box failed")
                handleUnexpectedPopup()
                return
            }

            delay(100L.withDelays())

            val priceToInject = calculatePrice()
            val validation = validatePrice(priceToInject)

            if (!validation.isValid) {
                updateState(StateType.ERROR_PRICE_SANITY, validation.message)
                statisticsManager?.recordFailure("Price validation failed: ${validation.message}")
                onPriceSanityError?.invoke(validation.message)
                handleUnexpectedPopup()
                return
            }

            statisticsManager?.recordPrice("item_$currentRowIndex", priceToInject, "CREATE", true)

            updateState(StateType.EXECUTE_TEXT_INPUT, "Injecting price: $priceToInject")
            uiInteractor.clearTextField()
            delay(50L.withDelays())

            if (!uiInteractor.injectText(priceToInject.toString())) {
                updateState(StateType.ERROR_RETRY, "Failed to inject text")
                statisticsManager?.recordFailure("Text injection failed")
                handleUnexpectedPopup()
                return
            }

            lastKnownPrice = priceToInject
            delay(timing.textInputDelayMs.withDelays())

            updateState(StateType.DISMISS_KEYBOARD, "Dismissing keyboard")
            uiInteractor.dismissKeyboard()
            delay(timing.keyboardDismissDelayMs.withDelays())

            updateState(StateType.EXECUTE_BUTTON, "Creating order")
            if (!executeWithRetry(safety.maxRetries) { performTapWithRandomization(config.createButtonX, config.createButtonY) }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap create button")
                statisticsManager?.recordFailure("Tap create button failed")
                handleUnexpectedPopup()
                return
            }

            delay(timing.confirmationWaitMs.withDelays())

            updateState(StateType.HANDLE_CONFIRMATION, "Checking confirmation")
            performTapWithRandomization(config.confirmYesX, config.confirmYesY)
            delay(timing.popupCloseWaitMs.withDelays())

            statisticsManager?.recordSuccess("CREATE", priceToInject)
            handleRowIteration(config.maxRowsPerScreen, timing)

        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
            statisticsManager?.recordFailure("Exception: ${e.message}")
            Log.e(TAG, "Create order loop error", e)
            handleUnexpectedPopup()
        }
    }

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
                currentRowIndex = 0
                statisticsManager?.resetConsecutiveErrors()
                return
            }

            val editX = config.editButtonX
            val editY = config.editButtonY + (currentRowIndex * config.editButtonYOffset)

            updateState(StateType.EXECUTE_TAP, "Tapping edit button $currentRowIndex")
            if (!executeWithRetry(safety.maxRetries) { performTapWithRandomization(editX, editY) }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap edit button")
                statisticsManager?.recordFailure("Tap edit button failed")
                handleUnexpectedPopup()
                return
            }

            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup")
            delay(timing.popupOpenWaitMs.withDelays())

            updateState(StateType.EXECUTE_TAP, "Tapping price input")
            if (!executeWithRetry(safety.maxRetries) { performTapWithRandomization(config.priceInputX, config.priceInputY) }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap price box")
                statisticsManager?.recordFailure("Tap price box failed")
                handleUnexpectedPopup()
                return
            }

            delay(100L.withDelays())

            val priceToInject = calculatePrice()
            val validation = validatePrice(priceToInject)

            if (!validation.isValid) {
                updateState(StateType.ERROR_PRICE_SANITY, validation.message)
                statisticsManager?.recordFailure("Price validation failed: ${validation.message}")
                onPriceSanityError?.invoke(validation.message)
                handleUnexpectedPopup()
                return
            }

            statisticsManager?.recordPrice("item_$currentRowIndex", priceToInject, "EDIT", true)

            updateState(StateType.EXECUTE_TEXT_INPUT, "Injecting price: $priceToInject")
            uiInteractor.clearTextField()
            delay(50L.withDelays())

            if (!uiInteractor.injectText(priceToInject.toString())) {
                updateState(StateType.ERROR_RETRY, "Failed to inject text")
                statisticsManager?.recordFailure("Text injection failed")
                handleUnexpectedPopup()
                return
            }

            lastKnownPrice = priceToInject
            delay(timing.textInputDelayMs.withDelays())

            updateState(StateType.DISMISS_KEYBOARD, "Dismissing keyboard")
            uiInteractor.dismissKeyboard()
            delay(timing.keyboardDismissDelayMs.withDelays())

            updateState(StateType.EXECUTE_BUTTON, "Updating order")
            if (!executeWithRetry(safety.maxRetries) { performTapWithRandomization(config.updateButtonX, config.updateButtonY) }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap update button")
                statisticsManager?.recordFailure("Tap update button failed")
                handleUnexpectedPopup()
                return
            }

            delay(timing.confirmationWaitMs.withDelays())

            updateState(StateType.HANDLE_CONFIRMATION, "Checking confirmation")
            performTapWithRandomization(config.confirmYesX, config.confirmYesY)
            delay(timing.popupCloseWaitMs.withDelays())

            statisticsManager?.recordSuccess("EDIT", priceToInject)
            handleRowIteration(5, timing)

        } catch (e: Exception) {
            updateState(StateType.ERROR_RETRY, "Error: ${e.message}")
            statisticsManager?.recordFailure("Exception: ${e.message}")
            Log.e(TAG, "Edit order loop error", e)
            handleUnexpectedPopup()
        }
    }

    private fun calculatePrice(): Int {
        val basePrice = lastKnownPrice ?: calibration.createMode.defaultBuyPrice
        val trend = statisticsManager?.getPriceTrend("current") ?: 0
        val increment = calibration.createMode.priceIncrement

        return when {
            trend > 0 -> basePrice + (increment * 2)
            trend < 0 -> basePrice + increment
            else -> basePrice + increment
        }
    }

    private suspend fun handleRowIteration(maxRows: Int, timing: GlobalSettings) {
        if (currentRowIndex > 0 && currentRowIndex % maxRows == 0) {
            updateState(StateType.SCROLL_NEXT_ROW, "Scrolling...")
            performSwipeWithRandomization(
                timing.swipeStartX, timing.swipeStartY,
                timing.swipeEndX, timing.swipeEndY
            )

            val settleTime = if (calibration.swipeOverlap.enableSwipeOverlap) {
                calibration.swipeOverlap.swipeSettleTimeMs
            } else {
                300L
            }
            delay(randomizationHelper.getRandomizedDelay(settleTime))
        }
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
            endOfListCount = endOfListDetectionCount.get(),
            windowLostCount = windowLostCount.get()
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
