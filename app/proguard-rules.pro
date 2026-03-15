# Sherpa-onnx JNI — keep all native method bindings and class members
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }

# Room entities and data classes (reflection + Room annotation processing)
-keep class com.echolibrium.kyokan.VoiceProfile { *; }
-keep class com.echolibrium.kyokan.AppRule { *; }
-keep class com.echolibrium.kyokan.WordRule { *; }

# Room database
-keep class com.echolibrium.kyokan.KyokanDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# Fragment classes (instantiated by FragmentManager via class name)
-keep class com.echolibrium.kyokan.*Fragment { *; }

# Keep enum entries (used by valueOf in deserialization)
-keepclassmembers enum com.echolibrium.kyokan.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin metadata needed for reflection
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep any custom native methods in the app
-keepclasseswithmembers class com.echolibrium.kyokan.** {
    native <methods>;
}

# ML Kit Translation
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
