package com.albion.marketassistant.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class StateMachine(
    private val scope: CoroutineScope,
    private val calibration: CalibrationData,
    private val uiInteractor: UIInteractor,
    private val context: Context? = null
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

    // Cycle counter for end-of-list detection
    private var totalCycleCount = AtomicInteger(0)
    private var lastPageText = ""
    private var endOfListDetectionCount = AtomicInteger(0)

    // Window verification
    private var windowLostCount = AtomicInteger(0)
    private var lastWindowCheckTime = AtomicLong(0)

    // OCR and detection
    private val ocrEngine = OCREngine()
    private val colorDetector = ColorDetector()

    // Utilities
    private val randomizationHelper = RandomizationHelper(calibration.antiDetection)
    private val deviceUtils = context?.let { DeviceUtils(it) }
    private var statisticsManager: StatisticsManager? = null

    // Screen capture callback
    var onScreenCaptureRequest: (( (Bitmap?) -> Unit ) -> Unit)? = null

    // Callbacks
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
        lastPageText = ""
        endOfListDetectionCount.set(0)
        windowLostCount.set(0)
        totalCycleCount.set(0)

        statisticsManager?.resetSession()
        statisticsManager?.updateState(StateType.IDLE.name)

        loopJob = scope.launch { mainLoop() }
    }

    fun pause() {
        isPaused = true
        statisticsManager?.updateState(StateType.PAUSED.name)
        updateState(StateType.PAUSED, "Paused")
    }

    fun resume() {
        isPaused = false
        windowLostCount.set(0)
        statisticsManager?.updateState(StateType.IDLE.name)
        updateState(StateType.IDLE, "Resumed")
    }

    fun isPaused(): Boolean = isPaused

    fun stop() {
        isRunning = false
        isPaused = false
        loopJob?.cancel()
        currentMode = OperationMode.IDLE

        // Export session log before stopping
        scope.launch {
            statisticsManager?.exportSessionLog()
            statisticsManager?.flushPriceHistory()
        }

        _stateFlow.value = AutomationState(StateType.IDLE)
    }

    // Apply network lag multiplier with randomization
    private fun Long.withDelays(): Long {
        val withLag = (this * calibration.global.networkLagMultiplier).toLong()
        return randomizationHelper.getRandomizedDelay(withLag)
    }

    private suspend fun mainLoop() {
        try {
            while (isRunning) {
                // Check for pause
                while (isPaused) {
                    delay(100)
                }

                // Check battery optimization
                if (!checkBatteryOptimization()) {
                    pause()
                    updateState(StateType.ERROR_BATTERY_LOW, "Battery too low - paused")
                    continue
                }

                // Check game window verification
                if (!verifyGameWindow()) {
                    continue // Window check will handle pause/stop
                }

                // Check for stuck state
                if (checkStuckState()) {
                    handleStuckRecovery()
                    continue
                }

                when (currentMode) {
                    OperationMode.NEW_ORDER_SWEEPER -> executeCreateOrderLoop()
                    OperationMode.ORDER_EDITOR -> executeEditOrderLoop()
                    OperationMode.IDLE -> return
                }

                // Update cycle counter
                val cycle = totalCycleCount.incrementAndGet()
                statisticsManager?.recordCycle()
                currentRowIndex++

                // Check end of list
                if (checkEndOfList()) {
                    handleEndOfList()
                    continue
                }

                // Check max cycles safety limit
                if (cycle >= calibration.endOfList.maxCyclesBeforeStop) {
                    updateState(StateType.ERROR_END_OF_LIST, "Max cycles reached ($cycle)")
                    handleEndOfList()
                    continue
                }

                // Cooldown between cycles with randomization
                updateState(StateType.COOLDOWN, "Cooldown")
                val cooldownDelay = randomizationHelper.getRandomizedDelay(calibration.global.cycleCooldownMs)

                // Add human-like hesitation occasionally
                if (randomizationHelper.shouldAddHesitation(cycle % 20)) {
                    delay(randomizationHelper.getHesitationDelay())
                }

                delay(cooldownDelay)
            }
        } catch (e: CancellationException) {
            // Normal cancellation
        } catch (e: Exception) {
            isRunning = false
            statisticsManager?.recordFailure("Main loop error: ${e.message}")
            onError?.invoke("Error: ${e.message}")
        }
    }

    /**
     * Check battery optimization
     */
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

    /**
     * Verify game window is active
     */
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

    /**
     * Handle game window lost
     */
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

    /**
     * Check if stuck in same state too long
     */
    private fun checkStuckState(): Boolean {
        val errorSettings = calibration.errorRecovery
        if (!errorSettings.enableSmartRecovery) return false

        return statisticsManager?.isStuck(errorSettings.maxStateStuckTimeMs) ?: false
    }

    /**
     * Handle stuck state recovery
     */
    private suspend fun handleStuckRecovery() {
        val errorSettings = calibration.errorRecovery

        statisticsManager?.recordFailure("Stuck state detected")

        updateState(StateType.ERROR_STUCK_DETECTED, "Stuck detected - recovering")

        // Capture screenshot for debugging
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
            "pause" -> {
                pause()
            }
            "stop" -> {
                stop()
            }
        }
    }

    /**
     * Check for end of list (same page content)
     */
    private suspend fun checkEndOfList(): Boolean {
        val eolSettings = calibration.endOfList
        if (!eolSettings.enableEndOfListDetection) return false

        // Get current page text via OCR (placeholder for actual implementation)
        val currentText = captureAndOCRFirstLine() ?: return false

        if (lastPageText.isNotEmpty()) {
            val result = TextSimilarity.calculatePageMatchScore(
                lastPageText,
                currentText,
                eolSettings.textSimilarityThreshold
            )

            if (result.isLikelySamePage) {
                val count = endOfListDetectionCount.incrementAndGet()

                if (count >= eolSettings.identicalPageThreshold) {
                    return true // End of list confirmed
                }
            } else {
                endOfListDetectionCount.set(0)
            }
        }

        lastPageText = currentText
        return false
    }

    /**
     * Capture screen and OCR first line for comparison
     */
    private suspend fun captureAndOCRFirstLine(): String? {
        // This would use screen capture API
        // For now, return empty to indicate feature is available
        return null
    }

    /**
     * Handle end of list reached
     */
    private suspend fun handleEndOfList() {
        updateState(StateType.ERROR_END_OF_LIST, "End of list reached")
        onEndOfList?.invoke()

        // Export session statistics
        statisticsManager?.exportSessionLog()

        // Either stop or reset for next pass
        if (calibration.swipeOverlap.enableScrollTracking) {
            // Reset for next pass
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
     * Handle unexpected error popups
     */
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
        delay(300.withDelays())

        uiInteractor.dismissKeyboard()
        delay(300.withDelays())
    }

    /**
     * Perform tap with randomization
     */
    private fun performTapWithRandomization(x: Int, y: Int): Boolean {
        val duration = randomizationHelper.getRandomizedDuration(calibration.global.tapDurationMs)
        return uiInteractor.performTap(x, y, duration)
    }

    /**
     * Perform swipe with randomization and overlap handling
     */
    private fun performSwipeWithRandomization(
        startX: Int, startY: Int,
        endX: Int, endY: Int
    ): Boolean {
        val swipeSettings = calibration.swipeOverlap

        // Adjust swipe for overlap prevention
        val adjustedStartY = if (swipeSettings.enableSwipeOverlap) {
            startY + (swipeSettings.overlapRowCount * calibration.createMode.rowYOffset)
        } else {
            startY
        }

        // Randomize swipe distance
        val distance = kotlin.math.abs(endY - adjustedStartY)
        val randomizedDistance = randomizationHelper.getRandomizedSwipeDistance(distance)
        val actualEndY = adjustedStartY - randomizedDistance

        val duration = randomizationHelper.getRandomizedDuration(calibration.global.swipeDurationMs.toLong())

        return uiInteractor.performSwipe(startX, adjustedStartY, endX, actualEndY, duration)
    }

    /**
     * OCR Sanity Check
     */
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

        // Check for price anomaly
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

    /**
     * Execute with retry logic
     */
    private suspend fun executeWithRetry(
        maxRetries: Int,
        action: suspend () -> Boolean
    ): Boolean {
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

    /**
     * CREATE ORDER WORKFLOW
     */
    private suspend fun executeCreateOrderLoop() {
        try {
            val config = calibration.createMode
            val timing = calibration.global
            val safety = calibration.safety
            val errorSettings = calibration.errorRecovery

            statisticsManager?.updateState(StateType.EXECUTE_TAP.name)

            // Check consecutive errors for auto-restart
            if (statisticsManager?.getConsecutiveErrors() ?: 0 >= errorSettings.autoRestartAfterErrors) {
                updateState(StateType.RECOVERING, "Too many errors - auto-restart")
                delay(errorSettings.autoRestartDelayMs)
                currentRowIndex = 0
                statisticsManager?.resetConsecutiveErrors()
                return
            }

            // Step 1: Calculate row coordinates and tap
            val rowX = config.firstRowX
            val rowY = config.firstRowY + (currentRowIndex * config.rowYOffset)

            updateState(StateType.EXECUTE_TAP, "Tapping row $currentRowIndex")
            if (!executeWithRetry(safety.maxRetries) {
                performTapWithRandomization(rowX, rowY)
            }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap row after ${safety.maxRetries} retries")
                statisticsManager?.recordFailure("Tap row failed")
                handleUnexpectedPopup()
                return
            }

            // Wait for popup
            updateState(StateType.WAIT_POPUP_OPEN, "Waiting for popup")
            delay(timing.popupOpenWaitMs.withDelays())

            // Step 2: Tap price input box
            updateState(StateType.EXECUTE_TAP, "Tapping price input")
            if (!executeWithRetry(safety.maxRetries) {
                performTapWithRandomization(config.priceInputX, config.priceInputY)
            }) {
                updateState(StateType.ERROR_RETRY, "Failed to tap price box")
                statisticsManager?.recordFailure("Tap price box failed")
                handleUnexpectedPopup()
                return
            }

            delay(100.withDelays())

            // Inject price with sanity check
            val priceToInject = calculatePrice()
            val validation = validatePrice(priceToInject)

            if (!validation.isValid) {
                updateState(StateType.ERROR_PRICE_SANITY, validation.message)
                statisticsManager?.recordFailure("Price validation failed: ${validation.message}")
                onPriceSanityError?.invoke(validation.message)
                handleUnexpectedPopup()
                return
            }

            // Record price in history
            statisticsManager?.recordPrice("item_$currentRowIndex", priceToInject, "CREATE", true)

            updateState(StateType.EXECUTE_TEXT_INPUT, "Injecting price: $priceToInject")
            uiInteractor.clearTextField()
            delay(50.withDelays())

            if (!uiInteractor.injectText(priceToInject.toString())) {
                updateState(StateType.ERROR_RETRY, "Failed to inject text")
                statisticsManager?.recordFailure("Text injection failed")
                handleUnexpectedPopup()
                return
            }

            lastKnownPrice = priceToInject

            delay(timing.textInputDelayMs.withDelays())

            // Dismiss keyboard
            updateState(StateType.DISMISS_KEYBOARD, "Dismissing 
