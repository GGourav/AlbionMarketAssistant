package com.albion.marketassistant.statistics

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import com.albion.marketassistant.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages session statistics, price history, and logging
 */
class StatisticsManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _statistics = MutableStateFlow(SessionStatistics())
    val statistics: StateFlow<SessionStatistics> = _statistics

    private val sessionId = System.currentTimeMillis()

    // Atomic counters for thread-safe updates
    private val totalCycles = AtomicInteger(0)
    private val successfulOps = AtomicInteger(0)
    private val failedOps = AtomicInteger(0)
    private val priceUpdates = AtomicInteger(0)
    private val ordersCreated = AtomicInteger(0)
    private val ordersEdited = AtomicInteger(0)
    private val errorsEncountered = AtomicInteger(0)
    private val consecutiveErrors = AtomicInteger(0)
    private val timeSavedMs = AtomicLong(0)
    private val estimatedProfit = AtomicLong(0)

    // State tracking for stuck detection
    private var currentState: String = ""
    private var stateEnterTime: Long = 0
    private var lastCycleTime: Long = System.currentTimeMillis()
    private val sessionStartTime = System.currentTimeMillis()

    // Price history buffer
    private val priceHistoryBuffer = mutableListOf<PriceHistoryEntry>()

    // Screenshot directory
    private val screenshotDir: File by lazy {
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots").apply {
            mkdirs()
        }
    }

    // Log directory
    private val logDir: File by lazy {
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "logs").apply {
            mkdirs()
        }
    }

    /**
     * Record a successful operation
     */
    fun recordSuccess(operationType: String, price: Int? = null) {
        successfulOps.incrementAndGet()
        consecutiveErrors.set(0)

        when (operationType) {
            "CREATE" -> ordersCreated.incrementAndGet()
            "EDIT" -> ordersEdited.incrementAndGet()
            "PRICE_UPDATE" -> priceUpdates.incrementAndGet()
        }

        price?.let {
            estimatedProfit.addAndGet(calculateEstimateProfit(it))
            timeSavedMs.addAndGet(5000) // Estimate 5 seconds saved per operation
        }

        updateStatistics()
    }

    /**
     * Record a failed operation
     */
    fun recordFailure(errorType: String) {
        failedOps.incrementAndGet()
        errorsEncountered.incrementAndGet()
        consecutiveErrors.incrementAndGet()

        logError(errorType)
        updateStatistics()
    }

    /**
     * Record a completed cycle
     */
    fun recordCycle() {
        totalCycles.incrementAndGet()
        lastCycleTime = System.currentTimeMillis()
        updateStatistics()
    }

    /**
     * Record price history entry
     */
    fun recordPrice(itemId: String, price: Int, mode: String, successful: Boolean) {
        val entry = PriceHistoryEntry(
            itemId = itemId,
            price = price,
            timestamp = System.currentTimeMillis(),
            sourceMode = mode,
            wasSuccessful = successful,
            sessionId = sessionId
        )

        synchronized(priceHistoryBuffer) {
            priceHistoryBuffer.add(entry)

            // Keep buffer size manageable
            if (priceHistoryBuffer.size > 1000) {
                flushPriceHistory()
            }
        }
    }

    /**
     * Update state for stuck detection
     */
    fun updateState(state: String) {
        if (currentState != state) {
            currentState = state
            stateEnterTime = System.currentTimeMillis()
        }
    }

    /**
     * Check if stuck in current state
     */
    fun isStuck(maxStuckTimeMs: Long): Boolean {
        if (currentState.isEmpty() || stateEnterTime == 0L) return false
        return System.currentTimeMillis() - stateEnterTime > maxStuckTimeMs
    }

    /**
     * Get consecutive error count
     */
    fun getConsecutiveErrors(): Int = consecutiveErrors.get()

    /**
     * Reset consecutive errors
     */
    fun resetConsecutiveErrors() {
        consecutiveErrors.set(0)
    }

    /**
     * Save screenshot for debugging
     */
    fun saveScreenshot(bitmap: Bitmap, prefix: String = "error"): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(screenshotDir, "${prefix}_${timestamp}.png")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            logEvent("Screenshot saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            logError("Failed to save screenshot: ${e.message}")
            null
        }
    }

    /**
     * Export session statistics to file
     */
    suspend fun exportSessionLog(): File? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(logDir, "session_${timestamp}.csv")

            val stats = _statistics.value

            file.writeText(buildString {
                appendLine("Albion Market Assistant - Session Log")
                appendLine("Session ID,$sessionId")
                appendLine("Start Time,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sessionStartTime))}")
                appendLine("End Time,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine()
                appendLine("Statistics")
                appendLine("Total Cycles,${stats.totalCycles}")
                appendLine("Successful Operations,${stats.successfulOperations}")
                appendLine("Failed Operations,${stats.failedOperations}")
                appendLine("Success Rate,${(stats.getSuccessRate() * 100).toInt()}%")
                appendLine("Price Updates,${stats.priceUpdates}")
                appendLine("Orders Created,${stats.ordersCreated}")
                appendLine("Orders Edited,${stats.ordersEdited}")
                appendLine("Errors Encountered,${stats.errorsEncountered}")
                appendLine("Session Duration,${stats.getSessionDurationFormatted()}")
                appendLine("Estimated Time Saved,${stats.timeSavedMs / 1000}s")
                appendLine("Estimated Profit,${stats.estimatedProfitSilver} silver")
                appendLine()
                appendLine("Price History (last 50 entries)")
                appendLine("Item,Price,Timestamp,Mode,Success")

                synchronized(priceHistoryBuffer) {
                    priceHistoryBuffer.takeLast(50).forEach { entry ->
                        appendLine("${entry.itemId},${entry.price},${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))},${entry.sourceMode},${entry.wasSuccessful}")
                    }
                }
            })

            logEvent("Session log exported: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            logError("Failed to export session log: ${e.message}")
            null
        }
    }

    /**
     * Flush price history to persistent storage
     */
    suspend fun flushPriceHistory() = withContext(Dispatchers.IO) {
        synchronized(priceHistoryBuffer) {
            if (priceHistoryBuffer.isEmpty()) return@withContext

            // In a full implementation, this would write to Room database
            // For now, we'll write to a CSV file
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                val file = File(logDir, "price_history_${timestamp}.csv")

                val exists = file.exists()
                file.appendText(buildString {
                    if (!exists) {
                        appendLine("Item,Price,Timestamp,Mode,Success,SessionId")
                    }
                    priceHistoryBuffer.forEach { entry ->
                        appendLine("${entry.itemId},${entry.price},${entry.timestamp},${entry.sourceMode},${entry.wasSuccessful},${entry.sessionId}")
                    }
                })

                priceHistoryBuffer.clear()
            } catch (e: Exception) {
                logError("Failed to flush price history: ${e.message}")
            }
        }
    }

    /**
     * Get price history for an item
     */
    fun getPriceHistory(itemId: String, limit: Int = 100): List<PriceHistoryEntry> {
        synchronized(priceHistoryBuffer) {
            return priceHistoryBuffer
                .filter { it.itemId == itemId }
                .sortedByDescending { it.timestamp }
                .take(limit)
        }
    }

    /**
     * Detect price anomaly (unusual price change)
     */
    fun detectPriceAnomaly(itemId: String, currentPrice: Int, threshold: Float = 0.2f): Boolean {
        val history = getPriceHistory(itemId, 10)
        if (history.size < 3) return false

        val avgPrice = history.map { it.price }.average()
        val deviation = kotlin.math.abs(currentPrice - avgPrice) / avgPrice

        return deviation > threshold
    }

    /**
     * Get price trend (1 = up, -1 = down, 0 = stable)
     */
    fun getPriceTrend(itemId: String): Int {
        val history = getPriceHistory(itemId, 5)
        if (history.size < 2) return 0

        val recent = history.take(2)
        val older = history.drop(2)

        val recentAvg = recent.map { it.price }.average()
        val olderAvg = older.map { it.price }.average()

        val diff = (recentAvg - olderAvg) / olderAvg

        return when {
            diff > 0.05 -> 1   // Trending up
            diff < -0.05 -> -1 // Trending down
            else -> 0          // Stable
        }
    }

    /**
     * Reset statistics for new session
     */
    fun resetSession() {
        totalCycles.set(0)
        successfulOps.set(0)
        failedOps.set(0)
        priceUpdates.set(0)
        ordersCreated.set(0)
        ordersEdited.set(0)
        errorsEncountered.set(0)
        consecutiveErrors.set(0)
        timeSavedMs.set(0)
        estimatedProfit.set(0)
        currentState = ""
        stateEnterTime = 0

        updateStatistics()
    }

    /**
     * Get current statistics snapshot
     */
    fun getStatistics(): SessionStatistics = _statistics.value

    private fun updateStatistics() {
        val duration = System.currentTimeMillis() - sessionStartTime
        val cycles = totalCycles.get()
        val avgCycleTime = if (cycles > 0) duration / cycles else 0

        _statistics.value = SessionStatistics(
            sessionStartTime = sessionStartTime,
            totalCycles = cycles,
            successfulOperations = successfulOps.get(),
            failedOperations = failedOps.get(),
            priceUpdates = priceUpdates.get(),
            ordersCreated = ordersCreated.get(),
            ordersEdited = ordersEdited.get(),
            errorsEncountered = errorsEncountered.get(),
            timeSavedMs = timeSavedMs.get(),
            estimatedProfitSilver = estimatedProfit.get(),
            sessionDurationMs = duration,
            averageCycleTimeMs = avgCycleTime,
            lastCycleTime = lastCycleTime,
            consecutiveErrors = consecutiveErrors.get(),
            lastState = currentState,
            stateEnterTime = stateEnterTime
        )
    }

    private fun calculateEstimateProfit(price: Int): Long {
        // Estimate profit based on price improvement
        // This is a rough estimate; actual profit depends on market conditions
        return (price * 0.01).toLong() // Assume 1% profit margin
    }

    private fun logEvent(message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val file = File(logDir, "events.log")
                file.appendText("[$timestamp] $message\n")
            } catch (e: Exception) {
                // Ignore logging errors
            }
        }
    }

    private fun logError(message: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val file = File(logDir, "errors.log")
                file.appendText("[$timestamp] ERROR: $message\n")
            } catch (e: Exception) {
                // Ignore logging errors
            }
        }
    }
}
