# Sherpa-onnx JNI — keep all native method bindings
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Keep components declared in AndroidManifest (Activity, Service, Receiver)
-keep class com.echolibrium.kyokan.MainActivity { *; }
-keep class com.echolibrium.kyokan.NotificationReaderService { *; }
-keep class com.echolibrium.kyokan.TtsAliveService { *; }
-keep class com.echolibrium.kyokan.BootReceiver { *; }

# Keep classes that are deserialized from SharedPreferences or JSON
-keep class com.echolibrium.kyokan.VoiceProfile { *; }
-keep class com.echolibrium.kyokan.AppRule { *; }
-keep class com.echolibrium.kyokan.GimmickConfig { *; }
-keep class com.echolibrium.kyokan.MoodState { *; }

# Keep Fragment classes (instantiated by FragmentManager via class name)
-keep class com.echolibrium.kyokan.*Fragment { *; }

# Keep enum entries (used by valueOf in deserialization)
-keepclassmembers enum com.echolibrium.kyokan.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin metadata needed for reflection
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ONNX Runtime (direct ORT for Yatagami pipeline)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ML Kit Translation
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
