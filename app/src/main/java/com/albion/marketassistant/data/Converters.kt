package com.albion.marketassistant.data

import androidx.room.TypeConverter

class Converters {
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(separator = "|||")
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split("|||")
        }
    }
    
    @TypeConverter
    fun fromIntList(value: List<Int>): String {
        return value.joinToString(separator = ",")
    }
    
    @TypeConverter
    fun toIntList(value: String): List<Int> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split(",").map { it.toIntOrNull() ?: 0 }
        }
    }
}
