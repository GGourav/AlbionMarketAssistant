package com.albion.marketassistant.statistics

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.albion.marketassistant.data.LogEntry
import com.albion.marketassistant.data.LogLevel
import com.albion.marketassistant.data.SessionHistory
import com.albion.marketassistant.data.SessionStatistics
import com.albion.marketassistant.data.ItemCache
import com.albion.marketassistant.database.AppDatabase
import com.albion.marketassistant.database.SessionHistoryDao
import com.albion.marketassistant.database.ItemCacheDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

class StatisticsManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    private val sessionHistoryDao: SessionHistoryDao by lazy {
        AppDatabase.getInstance(context).sessionHistoryDao()
    }
    
    private val itemCacheDao: ItemCacheDao by lazy {
        AppDatabase.getInstance(context).itemCacheDao()
    }
    
    private val _currentSession = MutableStateFlow(SessionStatistics())
    val currentSession: StateFlow<SessionStatistics> = _currentSession
    
    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries
    
    private val _totalStats = MutableStateFlow(TotalStatistics())
    val totalStats: StateFlow<TotalStatistics> = _totalStats
    
    private var sessionStartTime: Long = 0
    private var lastCycleTime: Long = 0
    
    data class TotalStatistics(
        val totalSessions: Int = 0,
        val totalItemsProcessed: Int = 0,
        val totalProfitSilver: Long = 0,
        val totalTimeMs: Long = 0,
        val averageCycleTimeMs: Long = 0
    )
    
    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        lastCycleTime = sessionStartTime
        
        _currentSession.value = SessionStatistics(
            startTime = sessionStartTime,
            priceUpdates = 0,
            timeSavedMs = 0,
            estimatedProfitSilver = 0,
            sessionDurationMs = 0,
            averageCycleTimeMs = 0,
            lastCycleTime = sessionStartTime,
            lastState = "INITIALIZING",
            stateEnterTime = sessionStartTime
        )
        
        log(LogLevel.INFO, "StatisticsManager", "Session started")
    }
    
    fun recordPriceUpdate(profitSilver: Long = 0) {
        val currentTime = System.currentTimeMillis()
        val cycleTime = currentTime - lastCycleTime
        lastCycleTime = currentTime
        
        val current = _currentSession.value
        val totalUpdates = current.priceUpdates + 1
        val totalTime = current.sessionDurationMs + cycleTime
        
        _currentSession.value = current.copy(
            priceUpdates = totalUpdates,
            estimatedProfitSilver = current.estimatedProfitSilver + profitSilver,
            sessionDurationMs = currentTime - sessionStartTime,
            averageCycleTimeMs = if (totalUpdates > 0) totalTime / totalUpdates else 0,
            lastCycleTime = cycleTime,
            lastState = "PRICE_UPDATE",
            stateEnterTime = currentTime
        )
        
        log(LogLevel.INFO, "StatisticsManager", "Price update #${totalUpdates}, profit: +${profitSilver} silver")
    }
    
    fun updateState(stateName: String) {
        val current = _currentSession.value
        _currentSession.value = current.copy(
            lastState = stateName,
            stateEnterTime = System.currentTimeMillis()
        )
    }
    
    fun recordTimeSaved(timeMs: Long) {
        val current = _currentSession.value
        _currentSession.value = current.copy(
            timeSavedMs = current.timeSavedMs + timeMs
        )
    }
    
    fun endSession() {
        val session = _currentSession.value
        val endTime = System.currentTimeMillis()
        
        scope.launch {
            try {
                val history = SessionHistory(
                    startTime = session.startTime,
                    endTime = endTime,
                    mode = "AUTO",
                    itemsProcessed = session.priceUpdates,
                    totalProfit = session.estimatedProfitSilver,
                    averageCycleTime = session.averageCycleTimeMs
                )
                sessionHistoryDao.insert(history)
                updateTotalStatistics()
            } catch (e: Exception) {
                log(LogLevel.ERROR, "StatisticsManager", "Failed to save session: ${e.message}")
            }
        }
        
        log(LogLevel.INFO, "StatisticsManager", "Session ended. Items: ${session.priceUpdates}, Profit: ${session.estimatedProfitSilver}")
    }
    
    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        
        val currentLogs = _logEntries.value.toMutableList()
        currentLogs.add(entry)
        
        if (currentLogs.size > 500) {
            currentLogs.removeAt(0)
        }
        
        _logEntries.value = currentLogs
    }
    
    fun getSessionDuration(): String {
        return _currentSession.value.getSessionDurationFormatted()
    }
    
    fun formatDuration(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun updateItemCache(itemName: String, price: Int, category: String = "") {
        scope.launch {
            try {
                val cache = ItemCache(
                    itemName = itemName,
                    lastPrice = price,
                    lastUpdated = System.currentTimeMillis(),
                    category = category
                )
                itemCacheDao.insert(cache)
            } catch (e: Exception) {
                log(LogLevel.ERROR, "StatisticsManager", "Failed to update item cache: ${e.message}")
            }
        }
    }
    
    suspend fun getCachedItemPrice(itemName: String): Int? {
        return try {
            itemCacheDao.getPrice(itemName)
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun updateTotalStatistics() {
        try {
            val sessions = sessionHistoryDao.getAllSessions()
            
            val total = TotalStatistics(
                totalSessions = sessions.size,
                totalItemsProcessed = sessions.sumOf { it.itemsProcessed },
                totalProfitSilver = sessions.sumOf { it.totalProfit },
                totalTimeMs = sessions.sumOf { it.endTime - it.startTime },
                averageCycleTimeMs = if (sessions.isNotEmpty()) {
                    sessions.map { it.averageCycleTime }.average().toLong()
                } else 0
            )
            
            _totalStats.value = total
        } catch (e: Exception) {
            log(LogLevel.ERROR, "StatisticsManager", "Failed to load total stats: ${e.message}")
        }
    }
    
    init {
        scope.launch {
            updateTotalStatistics()
        }
    }
    
    fun clearHistory() {
        scope.launch {
            try {
                sessionHistoryDao.deleteAll()
                itemCacheDao.deleteAll()
                _logEntries.value = emptyList()
                updateTotalStatistics()
                log(LogLevel.INFO, "StatisticsManager", "History cleared")
            } catch (e: Exception) {
                log(LogLevel.ERROR, "StatisticsManager", "Failed to clear history: ${e.message}")
            }
        }
    }
    
    fun exportStatistics(): String {
        val session = _currentSession.value
        val total = _totalStats.value
        
        return buildString {
            appendLine("=== Current Session ===")
            appendLine("Duration: ${getSessionDuration()}")
            appendLine("Items processed: ${session.priceUpdates}")
            appendLine("Profit: ${session.estimatedProfitSilver} silver")
            appendLine("Average cycle: ${formatDuration(session.averageCycleTimeMs)}")
            appendLine()
            appendLine("=== Total Statistics ===")
            appendLine("Total sessions: ${total.totalSessions}")
            appendLine("Total items: ${total.totalItemsProcessed}")
            appendLine("Total profit: ${total.totalProfitSilver} silver")
            appendLine("Total time: ${formatDuration(total.totalTimeMs)}")
        }
    }
    
    fun cleanup() {
        scope.cancel()
    }
}
