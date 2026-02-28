package com.albion.marketassistant.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.albion.marketassistant.data.CalibrationData
import com.albion.marketassistant.data.SessionHistory
import com.albion.marketassistant.data.ItemCache

@Database(
    entities = [
        CalibrationData::class,
        SessionHistory::class,
        ItemCache::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun calibrationDao(): CalibrationDao
    abstract fun sessionHistoryDao(): SessionHistoryDao
    abstract fun itemCacheDao(): ItemCacheDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "albion_market_assistant.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
