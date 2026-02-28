package com.albion.marketassistant.db

import com.albion.marketassistant.data.CalibrationData
import com.albion.marketassistant.data.SessionHistory
import com.albion.marketassistant.data.ItemCache
import android.content.Context
import androidx.room.*
import com.albion.marketassistant.data.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for calibration data
 */
@Dao
interface CalibrationDao {
    @Query("SELECT * FROM calibration_data LIMIT 1")
    suspend fun getCalibration(): CalibrationData?

    @Query("SELECT * FROM calibration_data LIMIT 1")
    fun getCalibrationFlow(): Flow<CalibrationData?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(calibration: CalibrationData)

    @Query("DELETE FROM calibration_data")
    suspend fun deleteAll()
}

/**
 * DAO for price history
 */
@Dao
interface PriceHistoryDao {
    @Query("SELECT * FROM price_history WHERE itemId = :itemId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getPriceHistory(itemId: String, limit: Int = 100): List<PriceHistoryEntry>

    @Query("SELECT * FROM price_history WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getSessionPriceHistory(sessionId: Long): List<PriceHistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(entry: PriceHistoryEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistoryBatch(entries: List<PriceHistoryEntry>)

    @Query("DELETE FROM price_history WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldEntries(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM price_history")
    suspend fun getCount(): Int
}

/**
 * DAO for session logs
 */
@Dao
interface SessionLogDao {
    @Query("SELECT * FROM session_logs ORDER BY sessionStart DESC")
    suspend fun getAllSessions(): List<SessionLogEntry>

    @Query("SELECT * FROM session_logs ORDER BY sessionStart DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int = 10): List<SessionLogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionLogEntry)

    @Update
    suspend fun updateSession(session: SessionLogEntry)

    @Query("DELETE FROM session_logs WHERE sessionStart < :beforeTimestamp")
    suspend fun deleteOldSessions(beforeTimestamp: Long)
}

/**
 * Main Room Database
 */
@Database(
    entities = [
        CalibrationData::class,
        PriceHistoryEntry::class,
        SessionLogEntry::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CalibrationDatabase : RoomDatabase() {
    abstract fun calibrationDao(): CalibrationDao
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun sessionLogDao(): SessionLogDao

    companion object {
        @Volatile
        private var INSTANCE: CalibrationDatabase? = null

        fun getInstance(context: Context): CalibrationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): CalibrationDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CalibrationDatabase::class.java,
                "albion_market_assistant_db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
