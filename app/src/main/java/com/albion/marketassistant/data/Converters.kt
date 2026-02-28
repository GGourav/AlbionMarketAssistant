package com.albion.marketassistant.data

import androidx.room.TypeConverter

/**
 * Room Type Converters for complex data types
 */
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
    
    @TypeConverter
    fun fromLongList(value: List<Long>): String {
        return value.joinToString(separator = ",")
    }
    
    @TypeConverter
    fun toLongList(value: String): List<Long> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split(",").map { it.toLongOrNull() ?: 0L }
        }
    }
    
    @TypeConverter
    fun fromFloatList(value: List<Float>): String {
        return value.joinToString(separator = ",")
    }
    
    @TypeConverter
    fun toFloatList(value: String): List<Float> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split(",").map { it.toFloatOrNull() ?: 0f }
        }
    }
    
    @TypeConverter
    fun fromMapStringInt(value: Map<String, Int>): String {
        return value.entries.joinToString(separator = "|||") { "${it.key}::${it.value}" }
    }
    
    @TypeConverter
    fun toMapStringInt(value: String): Map<String, Int> {
        return if (value.isEmpty()) {
            emptyMap()
        } else {
            value.split("|||").associate {
                val (key, v) = it.split("::")
                key to (v.toIntOrNull() ?: 0)
            }
        }
    }
    
    @TypeConverter
    fun fromMapStringLong(value: Map<String, Long>): String {
        return value.entries.joinToString(separator = "|||") { "${it.key}::${it.value}" }
    }
    
    @TypeConverter
    fun toMapStringLong(value: String): Map<String, Long> {
        return if (value.isEmpty()) {
            emptyMap()
        } else {
            value.split("|||").associate {
                val (key, v) = it.split("::")
                key to (v.toLongOrNull() ?: 0L)
            }
        }
    }
}
