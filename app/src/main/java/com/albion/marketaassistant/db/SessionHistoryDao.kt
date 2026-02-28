package com.albion.marketaassistant.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.albion.marketaassistant.data.SessionHistory

@Dao
interface SessionHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionHistory)
    
    @Query("SELECT * FROM session_history ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<SessionHistory>
    
    @Query("SELECT * FROM session_history ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<SessionHistory>
    
    @Query("SELECT * FROM session_history WHERE id = :id")
    suspend fun getById(id: Long): SessionHistory?
    
    @Query("DELETE FROM session_history")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM session_history")
    suspend fun getCount(): Int
    
    @Query("SELECT SUM(itemsProcessed) FROM session_history")
    suspend fun getTotalItemsProcessed(): Int?
    
    @Query("SELECT SUM(totalProfit) FROM session_history")
    suspend fun getTotalProfit(): Long?
}
