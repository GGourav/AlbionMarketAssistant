package com.albion.marketassistant.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.albion.marketassistant.data.CalibrationData

@Dao
interface CalibrationDao {
    @Query("SELECT * FROM calibration_data WHERE id = 1")
    suspend fun getCalibration(): CalibrationData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(data: CalibrationData)

    @Update
    suspend fun updateCalibration(data: CalibrationData)
}

@Database(entities = [CalibrationData::class], version = 1, exportSchema = false)
abstract class CalibrationDatabase : RoomDatabase() {
    abstract fun calibrationDao(): CalibrationDao

    companion object {
        @Volatile
        private var INSTANCE: CalibrationDatabase? = null

        fun getInstance(context: Context): CalibrationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CalibrationDatabase::class.java,
                    "calibration.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
