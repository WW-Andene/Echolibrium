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
-keep class com.echolibrium.kyokan.VoiceIdentity { *; }
-keep class com.echolibrium.kyokan.ExpressionMap { *; }
-keep class com.echolibrium.kyokan.GimmickSet { *; }
-keep class com.echolibrium.kyokan.AppRule { *; }
-keep class com.echolibrium.kyokan.GimmickConfig { *; }
-keep class com.echolibrium.kyokan.MoodState { *; }
-keep class com.echolibrium.kyokan.ExpressionCurve { *; }
-keep class com.echolibrium.kyokan.ExpressionCurve$CurvePoint { *; }
-keep class com.echolibrium.kyokan.ExpressionCurveSet { *; }
-keep class com.echolibrium.kyokan.VoiceConfig { *; }
-keep class com.echolibrium.kyokan.CommentaryPool { *; }
-keep class com.echolibrium.kyokan.CommentaryCondition { *; }
-keep class com.echolibrium.kyokan.PersonalitySensitivity { *; }

# Keep Fragment classes (instantiated by FragmentManager via class name)
-keep class com.echolibrium.kyokan.*Fragment { *; }

# Keep core pipeline classes — R8 must not strip these even though they're only
# called from other obfuscated code. Without these rules R8 reduced 195 classes
# to 23, removing the voice synthesis, DSP, and signal processing pipeline.
-keep class com.echolibrium.kyokan.AudioPipeline { *; }
-keep class com.echolibrium.kyokan.AudioDsp { *; }
-keep class com.echolibrium.kyokan.DspChain { *; }
-keep class com.echolibrium.kyokan.DspChain$* { *; }
-keep class com.echolibrium.kyokan.StyleSculptor { *; }
-keep class com.echolibrium.kyokan.StyleSculptor$* { *; }
-keep class com.echolibrium.kyokan.ScaleMapper { *; }
-keep class com.echolibrium.kyokan.DirectOrtEngine { *; }
-keep class com.echolibrium.kyokan.YatagamiSynthesizer { *; }
-keep class com.echolibrium.kyokan.YatagamiSynthesizer$* { *; }
-keep class com.echolibrium.kyokan.SherpaEngine { *; }
-keep class com.echolibrium.kyokan.VoiceModulator { *; }
-keep class com.echolibrium.kyokan.VoiceTransform { *; }
-keep class com.echolibrium.kyokan.VoiceTransform$* { *; }
-keep class com.echolibrium.kyokan.SignalExtractor { *; }
-keep class com.echolibrium.kyokan.SignalMap { *; }
-keep class com.echolibrium.kyokan.SourceContext { *; }
-keep class com.echolibrium.kyokan.EmotionalSignals { *; }
-keep class com.echolibrium.kyokan.IntensityMetrics { *; }
-keep class com.echolibrium.kyokan.StakesContext { *; }
-keep class com.echolibrium.kyokan.TemporalContext { *; }
-keep class com.echolibrium.kyokan.DownloadUtil { *; }
-keep class com.echolibrium.kyokan.CrashLogger { *; }
-keep class com.echolibrium.kyokan.CustomCoreEngine { *; }
-keep class com.echolibrium.kyokan.BehaviorPatch { *; }
-keep class com.echolibrium.kyokan.PhonicAnalyzer { *; }
-keep class com.echolibrium.kyokan.PhonemeTokenizer { *; }
-keep class com.echolibrium.kyokan.ParameterTrajectory { *; }
-keep class com.echolibrium.kyokan.ModulatedVoice { *; }
-keep class com.echolibrium.kyokan.ObservationDb { *; }
-keep class com.echolibrium.kyokan.VoiceRegistry { *; }
-keep class com.echolibrium.kyokan.KokoroVoice { *; }
-keep class com.echolibrium.kyokan.KokoroVoices { *; }
-keep class com.echolibrium.kyokan.PiperVoice { *; }
-keep class com.echolibrium.kyokan.PiperVoices { *; }
-keep class com.echolibrium.kyokan.VoiceDownloadManager { *; }
-keep class com.echolibrium.kyokan.PiperDownloadManager { *; }
-keep class com.echolibrium.kyokan.NotificationTranslator { *; }
-keep class com.echolibrium.kyokan.DurationMap { *; }
-keep class com.echolibrium.kyokan.VoiceCommandListener { *; }
-keep class com.echolibrium.kyokan.VoiceCommandHandler { *; }
-keep class com.echolibrium.kyokan.PersonalityEvent { *; }

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
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep any custom native methods in the app
-keepclasseswithmembers class com.echolibrium.kyokan.** {
    native <methods>;
}

# ML Kit Translation
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
