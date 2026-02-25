# Add project specific ProGuard rules here.

# Keep data models for Room
-keep class com.albion.marketassistant.data.** { *; }

# Keep Accessibility Service
-keep class com.albion.marketassistant.accessibility.** { *; }

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }

# Keep Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Room generated classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
