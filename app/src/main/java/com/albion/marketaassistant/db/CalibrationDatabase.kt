package com.albion.marketaassistant.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.albion.marketaassistant.data.CalibrationData
import com.albion.marketaassistant.data.SessionHistory
import com.albion.marketaassistant.data.ItemCache

@Database(
    entities = [
        CalibrationData::class,
        SessionHistory::class,
        ItemCache::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CalibrationDatabase : RoomDatabase() {
    
    abstract fun calibrationDao(): CalibrationDao
    abstract fun sessionHistoryDao(): SessionHistoryDao
    abstract fun itemCacheDao(): ItemCacheDao
    
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
                "albion_market_assistant.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
