# Sherpa-onnx JNI — keep all native method bindings
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Keep all app classes — the voice pipeline uses object singletons, enums,
# data classes, and cross-class references that R8 can incorrectly strip
# or rename. This is a small app; aggressive minification provides no
# meaningful APK size benefit but causes runtime crashes.
-keep class com.kokoro.reader.** { *; }

# Keep enum entries (used by valueOf in deserialization)
-keepclassmembers enum com.kokoro.reader.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin metadata needed for reflection
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
