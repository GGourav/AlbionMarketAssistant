// Path: app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // We are switching from 'kapt' to 'ksp' for better compatibility
}

// ... keep the rest of your 'android' block the same ...

dependencies {
    // Replace your Room dependencies with these:
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version") // Changed from 'kapt' to 'ksp'
    
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // ... keep your other implementation lines ...
}
