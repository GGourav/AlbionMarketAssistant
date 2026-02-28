package com.albion.marketassistant.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.albion.marketassistant.data.CalibrationData

@Dao
interface CalibrationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calibrationData: CalibrationData)
    
    @Query("SELECT * FROM calibration_data ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): CalibrationData?
    
    @Query("SELECT * FROM calibration_data WHERE id = :id")
    suspend fun getById(id: Long): CalibrationData?
    
    @Query("DELETE FROM calibration_data")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM calibration_data")
    suspend fun getCount(): Int
}
