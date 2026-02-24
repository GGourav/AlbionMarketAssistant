# Keep Kotlin classes
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }

# Keep data classes for Room
-keep class com.albion.marketassistant.data.** { *; }

# Keep all classes that are used by Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep accessibility service
-keep class com.albion.marketassistant.accessibility.** { *; }

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
