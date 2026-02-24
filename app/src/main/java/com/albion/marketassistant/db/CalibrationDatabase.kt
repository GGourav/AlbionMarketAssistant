package com.albion.marketassistant.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.albion.marketassistant.data.CalibrationData

@Dao
interface CalibrationDao {
    @Query("SELECT * FROM calibration_data LIMIT 1")
    suspend fun getCalibration(): CalibrationData?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(calibration: CalibrationData)
    
    @Update
    suspend fun updateCalibration(calibration: CalibrationData)
}

@Database(entities = [CalibrationData::class], version = 1, exportSchema = false)
abstract class CalibrationDatabase : RoomDatabase() {
    abstract fun calibrationDao(): CalibrationDao
}

