# Sherpa-onnx JNI — keep all native method bindings
-keep class com.k2fsa.sherpa.onnx.** { *; }

# App data classes serialized via JSONObject — keep field names
-keep class com.kokoro.reader.VoiceProfile { *; }
-keep class com.kokoro.reader.VoiceProfile$* { *; }
-keep class com.kokoro.reader.AppRule { *; }
-keep class com.kokoro.reader.SignalMap { *; }
-keep class com.kokoro.reader.MoodState { *; }
-keep class com.kokoro.reader.CommentaryPool { *; }
-keep class com.kokoro.reader.GimmickConfig { *; }
-keep class com.kokoro.reader.PersonalitySensitivity { *; }
-keep class com.kokoro.reader.CommentaryCondition { *; }
-keep class com.kokoro.reader.ModulatedVoice { *; }

# Keep enum entries (used by valueOf in deserialization)
-keepclassmembers enum com.kokoro.reader.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# NotificationListenerService must be discoverable by the system
-keep class com.kokoro.reader.NotificationReaderService { *; }

# Kotlin metadata needed for reflection
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
