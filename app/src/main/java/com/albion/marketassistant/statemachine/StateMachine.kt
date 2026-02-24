package com.albion.marketassistant.statemachine

import android.util.Log
import com.albion.marketassistant.accessibility.UIInteractor
import com.albion.marketassistant.data.*
import com.albion.marketassistant.ml.ColorDetector
import com.albion.marketassistant.ml.OCREngine
import com.albion.marketassistant.media.ScreenCaptureManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class StateMachine(
    private val uiInteractor: UIInteractor,
    private val ocrEngine: OCREngine,
    private val colorDetector: ColorDetector,
    private val screenCaptureManager: ScreenCaptureManager,
    private val calibration: CalibrationData
) {
    private val _state = MutableStateFlow(AutomationState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun start(mode: OperationMode) {
        _state.value = AutomationState(mode = mode, stateType = StateType.SCAN_HIGHLIGHTS)
        job = CoroutineScope(Dispatchers.Default).launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
        _state.value = AutomationState(stateType = StateType.IDLE)
    }

    private suspend fun runLoop() {
        while (job?.isActive == true) {
            try {
                when (_state.value.stateType) {
                    StateType.SCAN_HIGHLIGHTS -> handleScanning()
                    StateType.SCAN_OCR -> handleOCR()
                    StateType.EXECUTE_INPUT -> handleInput()
                    StateType.WAIT_POPUP_CLOSE -> handleClosing()
                    else -> delay(500)
                }
            } catch (e: Exception) {
                Log.e("StateMachine", "Error: ${e.message}")
                delay(2000)
            }
        }
    }

    private suspend fun handleScanning() {
        val bitmap = screenCaptureManager.captureScreen() ?: return
        val currentY = calibration.firstRowY + (_state.value.currentRowIndex * calibration.rowYOffset)
        val result = colorDetector.detectColor(bitmap, android.graphics.Rect(0, currentY, 200, currentY + 50), calibration.highlightedRowColorHex)

        if (result.isMatch) {
            moveToNextRow()
        } else {
            uiInteractor.performTap(calibration.firstRowX, currentY)
            delay(calibration.popupOpenWaitMs)
            _state.value = _state.value.copy(stateType = StateType.SCAN_OCR)
        }
        bitmap.recycle()
    }

    private suspend fun handleOCR() {
        val bitmap = screenCaptureManager.captureScreen() ?: return
        val results = ocrEngine.recognizeText(bitmap, calibration.getBuyOrdersRegion())
        if (results.isNotEmpty()) {
            val topPrice = results.first().numericValue ?: 0
            val myNewPrice = if (_state.value.mode == OperationMode.NEW_ORDER_SWEEPER) topPrice + 5 else topPrice + 1
            _state.value = _state.value.copy(stateType = StateType.EXECUTE_INPUT, ocrResult = results.first().copy(numericValue = myNewPrice))
        }
        bitmap.recycle()
    }

    private suspend fun handleInput() {
        val targetPrice = _state.value.ocrResult?.numericValue?.toString() ?: return
        uiInteractor.performTap(calibration.priceInputX, calibration.priceInputY)
        delay(300)
        val node = uiInteractor.findFocusedNode()
        uiInteractor.clearTextField(node)
        uiInteractor.injectText(node, targetPrice)
        delay(calibration.textInputDelayMs)
        uiInteractor.performTap(calibration.confirmButtonX, calibration.confirmButtonY)
        _state.value = _state.value.copy(stateType = StateType.WAIT_POPUP_CLOSE)
    }

    private suspend fun handleClosing() {
        uiInteractor.performTap(calibration.closeButtonX, calibration.closeButtonY)
        delay(calibration.popupCloseWaitMs)
        moveToNextRow()
    }

    private fun moveToNextRow() {
        var nextIndex = _state.value.currentRowIndex + 1
        if (nextIndex >= calibration.maxRowsPerScreen) {
            uiInteractor.performSwipe(500, 800, 500, 300)
            nextIndex = 0
        }
        _state.value = _state.value.copy(stateType = StateType.SCAN_HIGHLIGHTS, currentRowIndex = nextIndex)
    }
}
