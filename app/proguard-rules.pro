# Sherpa-onnx JNI — keep all native method bindings and class members
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }

# Keep components declared in AndroidManifest (Activity, Service, Receiver)
-keep class com.echolibrium.kyokan.MainActivity { *; }
-keep class com.echolibrium.kyokan.NotificationReaderService { *; }
-keep class com.echolibrium.kyokan.TtsAliveService { *; }
-keep class com.echolibrium.kyokan.BootReceiver { *; }

# Keep classes that are deserialized from SharedPreferences or JSON
-keep class com.echolibrium.kyokan.VoiceProfile { *; }
-keep class com.echolibrium.kyokan.AppRule { *; }

# Keep Fragment classes (instantiated by FragmentManager via class name)
-keep class com.echolibrium.kyokan.*Fragment { *; }

# Keep core pipeline classes — R8 must not strip these even though they're only
# called from other obfuscated code.
-keep class com.echolibrium.kyokan.AudioPipeline { *; }
-keep class com.echolibrium.kyokan.AudioDsp { *; }
-keep class com.echolibrium.kyokan.SherpaEngine { *; }
-keep class com.echolibrium.kyokan.CloudTtsEngine { *; }
-keep class com.echolibrium.kyokan.VoiceRegistry { *; }
-keep class com.echolibrium.kyokan.KokoroVoice { *; }
-keep class com.echolibrium.kyokan.KokoroVoices { *; }
-keep class com.echolibrium.kyokan.PiperVoice { *; }
-keep class com.echolibrium.kyokan.PiperVoices { *; }
-keep class com.echolibrium.kyokan.VoiceDownloadManager { *; }
-keep class com.echolibrium.kyokan.PiperDownloadManager { *; }
-keep class com.echolibrium.kyokan.DownloadUtil { *; }
-keep class com.echolibrium.kyokan.CrashLogger { *; }
-keep class com.echolibrium.kyokan.NotificationTranslator { *; }
-keep class com.echolibrium.kyokan.VoiceCommandListener { *; }
-keep class com.echolibrium.kyokan.VoiceCommandHandler { *; }

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
