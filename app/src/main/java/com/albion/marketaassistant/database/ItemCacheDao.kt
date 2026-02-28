
package com.albion.marketassistant.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.albion.marketassistant.data.ItemCache

@Dao
interface ItemCacheDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemCache)
    
    @Query("SELECT * FROM item_cache WHERE itemName = :itemName")
    suspend fun getByName(itemName: String): ItemCache?
    
    @Query("SELECT lastPrice FROM item_cache WHERE itemName = :itemName")
    suspend fun getPrice(itemName: String): Int?
    
    @Query("SELECT * FROM item_cache ORDER BY lastUpdated DESC")
    suspend fun getAll(): List<ItemCache>
    
    @Query("SELECT * FROM item_cache WHERE category = :category ORDER BY lastUpdated DESC")
    suspend fun getByCategory(category: String): List<ItemCache>
    
    @Query("DELETE FROM item_cache")
    suspend fun deleteAll()
    
    @Query("DELETE FROM item_cache WHERE lastUpdated < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM item_cache")
    suspend fun getCount(): Int
}
