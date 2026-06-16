# Android Standard ProGuard rules for compilation and optimization integration.

# Preserve Coroutines, ViewModel, and life-cycle elements
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class androidx.lifecycle.** { *; }

# Keep Room database and its generated structural components
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep OkHttp & Retrofit from minification stripping
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Preserve Moshi JSON serialization adapters and maps
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}

# Keep all local models, DTO models, and network data structures intact
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }

# Keep WorkManager classes intact
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
