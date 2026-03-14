# Kyōkan v4.0 — Deep Audit Report

**Package:** `com.echolibrium.kyokan`
**Version:** 4.0 (code 4) | **Target SDK:** 35 | **Min SDK:** 26
**Build toolchain:** AGP 8.2.2 · Kotlin 1.9.22 · Gradle 8.4
**APK size:** 19 MB (42 MB uncompressed native)

---

## 1. Foreground service type — critical compliance risk

### The problem

Both `NotificationReaderService` and `TtsAliveService` declare `foregroundServiceType="specialUse"` (hex `0x40000000`). Per Android 14 documentation, `specialUse` is explicitly **reserved for system applications, VPN apps, and apps holding `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`**. Kyōkan meets none of these criteria.

On devices running Android 14+, attempting to call `startForeground()` with this type will throw `ForegroundServiceTypeNotAllowedException` — unless Google grants a Play Console exemption. Even if it currently works during sideloading (because the OS enforcement may be lenient outside Play Protect), this will block Play Store distribution.

### The fix

**`NotificationReaderService`** performs two functions: listening to notifications (which doesn't require FGS — `NotificationListenerService` is a system-bound service that runs independently) and TTS audio output. The TTS audio output is the part that needs the FGS, and the correct type is `mediaPlayback`. This is exactly how other TTS reader apps (Wallabag, Voice, etc.) handle it. The required changes:

```xml
<!-- Permission -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<!-- Remove: FOREGROUND_SERVICE_SPECIAL_USE -->

<!-- Service declaration -->
<service
    android:name=".NotificationReaderService"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService"/>
    </intent-filter>
</service>
```

In the Kotlin code, the `startForeground()` call needs to pass the type explicitly:

```kotlin
ServiceCompat.startForeground(
    this,
    NOTIFICATION_ID,
    notification,
    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
)
```

**`TtsAliveService`** — same treatment. If this service exists solely to keep the TTS engine warm in memory while audio is being produced, `mediaPlayback` is the right type. If it's a keepalive that doesn't produce audio itself, consider whether it can be merged into the main service or replaced with `WorkManager`.

### Play Console declaration

After changing the FGS type, you'll need to declare the `mediaPlayback` foreground service on the Play Console App Content page. This requires a short description ("Reads device notifications aloud using on-device TTS") and a video demonstrating the feature. `mediaPlayback` is a well-understood category and should pass review without issue — it's what podcast and audiobook apps use.

### RECORD_AUDIO interaction

There's a subtlety: if `NotificationReaderService` also uses `RECORD_AUDIO` (for voice commands), and you want the mic active while the service is foregrounded, you'd need to declare `mediaPlayback|microphone` as a combined type. However, `microphone` type has a **while-in-use restriction** — on Android 14+, you can't start a `microphone` FGS from the background. This means the mic component should only activate when the user initiates it from the foreground (which aligns with how voice commands typically work). Consider using a combined type only during active voice-command sessions, starting with just `mediaPlayback` and upgrading via a second `startForeground()` call when the user triggers listening.

---

## 2. ABI support — arm64-v8a only

### Impact

The APK ships exclusively with `arm64-v8a` native libraries. This means:

- **No 32-bit ARM support.** Devices running 32-bit only Android (mostly pre-2018 budget phones) can't install.
- **No x86/x86_64 support.** Android emulators without ARM translation layer (standard Android Studio emulators) won't run the app natively. This makes development testing harder, especially from Codespaces.
- **Play Store filtering.** Google Play will automatically filter the app from devices that don't support arm64-v8a.

### Recommendation

For SherpaONNX, check whether pre-built libraries exist for `armeabi-v7a`. The main ONNX Runtime does publish 32-bit ARM builds, so the bottleneck would be the SherpaONNX JNI layer. The `libtranslate_jni.so` (ML Kit) is delivered by Google and would need its own 32-bit variant.

If 32-bit support is infeasible (because the models are too large for 32-bit address space anyway), document this explicitly in the Play Store listing and consider adding a `<uses-feature>` declaration or an ABI filter note.

For emulator testing, an alternative is to use ARM-capable cloud emulators (Firebase Test Lab, AWS Device Farm) or test on physical hardware exclusively.

### App Bundle consideration

If distributing via Play Store, use Android App Bundle (`.aab`) instead of APK. Play will generate per-ABI APKs automatically, so users only download the native libraries for their device. This would shave the download size from ~19MB to ~15MB for arm64 users (since the APK currently contains only one ABI anyway, but the .aab format also enables better compression).

---

## 3. BootReceiver — exported without permission guard

### Current state

```xml
<receiver android:name=".BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
    </intent-filter>
</receiver>
```

### Risk

While `BOOT_COMPLETED` is a protected broadcast (only the system can send it), the receiver is technically reachable by any app that sends a matching intent via `sendBroadcast()`. On most devices this would fail because of the protected broadcast restriction, but on rooted devices or custom ROMs, another app could trigger `BootReceiver` arbitrarily.

### Fix

```xml
<receiver
    android:name=".BootReceiver"
    android:exported="true"
    android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
    </intent-filter>
</receiver>
```

Additionally, there are **restrictions on BOOT_COMPLETED receivers launching FGS on Android 14+**. If `BootReceiver` tries to start `NotificationReaderService` or `TtsAliveService` as a foreground service after boot, it may fail on Android 14 for certain FGS types. With `mediaPlayback`, background FGS start from BOOT_COMPLETED is allowed — but verify this in your code path.

---

## 4. RECORD_AUDIO permission

### Current state

`RECORD_AUDIO` is declared as a static manifest permission. The `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value on `NotificationReaderService` says "Notification reading and voice command listening", confirming this is for voice commands.

### Issues

1. **Play Store data safety form**: `RECORD_AUDIO` triggers a mandatory "Microphone" disclosure. You'll need a privacy policy explaining what audio data is collected, stored, and whether it's transmitted.

2. **Runtime permission**: On Android 6+, `RECORD_AUDIO` is a runtime permission. Declaring it in the manifest alone doesn't grant it — you must request it at runtime. Make sure the app handles the denial case gracefully (voice commands disabled, TTS still works).

3. **While-in-use restriction (Android 14+)**: If using `microphone` FGS type, the service can't access the mic when started from the background. Ensure voice command activation requires user interaction from the foreground.

### Recommendation

If voice commands aren't implemented yet, consider removing `RECORD_AUDIO` from the manifest until the feature ships. Every declared permission increases the Play Store install friction (users see "This app needs access to your microphone") and requires privacy documentation. Add it when the feature is ready.

---

## 5. REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

### Play Store policy

Google Play's Device and Network Abuse policy restricts `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to apps whose "core function" requires it. The documented acceptable use cases are: VoIP, messaging, device management, sync apps, and safety/emergency apps. A notification reader with TTS is a borderline case.

### Alternative approach

Rather than requesting full battery optimization exemption, consider:

1. **Use `MediaSession` with `mediaPlayback` FGS**: This is inherently battery-exempted while audio is actively playing. The system won't kill a media-playing FGS.

2. **Notification listener binding**: `NotificationListenerService` is already system-bound. It persists as long as the user has granted notification access in Settings. It doesn't need battery exemption.

3. **Targeted wakelock**: Use `WAKE_LOCK` (which you already declare) for the brief TTS synthesis period, then release.

If you keep the permission, be prepared for a Play Store review request. Have a clear explanation ready: "Kyōkan must remain active to read incoming notifications aloud in real-time, which requires exemption from battery optimization to prevent the system from killing the notification listener."

---

## 6. Signing — V2 only, no V3

### Current state

The APK is signed with APK Signature Scheme v2 only (no v1/v3).

### Why V3 matters

V3 signing (introduced in Android 9) enables **key rotation** — you can change your signing key without losing the ability to update existing installs. Without V3, if your signing key is ever compromised or you need to migrate to a stronger key, you'd have to publish under a new package name, losing all existing users.

### Fix

In your `build.gradle`:

```groovy
android {
    signingConfigs {
        release {
            // ... existing config
            enableV3Signing = true
            enableV4Signing = true  // V4 adds incremental install support
        }
    }
}
```

This is backward compatible — V2 is still included for older devices.

---

## 7. Dependency optimization — Firebase/GMS bloat

### Current state

The manifest shows these Firebase/GMS components:

- `MlKitComponentDiscoveryService` (direct boot aware)
- `MlKitInitProvider` (ContentProvider, init order 99)
- `GoogleApiActivity`
- `TransportBackendDiscovery` + `JobInfoSchedulerService` + `AlarmManagerSchedulerBroadcastReceiver`
- `com.google.android.gms.version` metadata

The `NaturalLanguageTranslateRegistrar` and `CommonComponentRegistrar` are Firebase component registrars. The `datatransport` services handle Google telemetry/analytics transport.

### Analysis

ML Kit Translate pulls in Firebase as a transitive dependency even though Kyōkan doesn't appear to use Firebase Analytics, Crashlytics, or any other Firebase product. The `datatransport` stack (3 components: a service, a job scheduler service, and an alarm receiver) exists solely to batch and send ML Kit usage analytics to Google.

### Optimization options

1. **Disable datatransport at build time**: If you don't want Google collecting ML Kit usage telemetry:

```xml
<!-- In AndroidManifest.xml, merge rule to remove -->
<service android:name="com.google.android.datatransport.runtime.backends.TransportBackendDiscovery"
    tools:node="remove" />
<service android:name="com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService"
    tools:node="remove" />
<receiver android:name="com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver"
    tools:node="remove" />
```

2. **Lazy-load ML Kit Translate**: If translation is a secondary feature (not used every session), make it a dynamic feature module. This moves the 16MB `libtranslate_jni.so` out of the base APK.

3. **Replace with on-device alternative**: If you're already running SherpaONNX, consider using a lightweight ONNX translation model instead of ML Kit. This would eliminate the entire GMS/Firebase dependency chain.

---

## 8. Build toolchain upgrades

### Kotlin 1.9.22 → 2.0+

Kotlin 2.0 introduced the K2 compiler as stable. Benefits for Kyōkan: 30-50% faster compilation, better Kotlin/JVM interop, and improved type inference. The migration is straightforward for most codebases — run `kotlin.experimental.tryK2=true` in `gradle.properties` first to test compatibility.

### AGP 8.2.2 → 8.7+

Newer AGP versions bring better R8 optimizations (important for your single-DEX app), improved baseline profile generation (you already have `ProfileInstallerInitializer` set up), and Android 15 SDK support.

### Gradle 8.4 → 8.10+

Primarily for configuration cache support and faster incremental builds.

### Migration path

Upgrade in order: Gradle first, then AGP, then Kotlin. Test each step on a branch before merging. The Kotlin 2.0 migration may require updating some library dependencies that use `kapt` to `ksp`.

---

## 9. Dead code — AudioDsp.kt and trajectory_config.json

### AudioDsp.kt — delete

`AudioDsp.kt` is a post-PCM pitch shifter (resample + overlap-add time-stretch) called from `AudioPipeline.kt` line 153:

```kotlin
val pcm = if (item.pitch != 1.0f) {
    AudioDsp.pitchShift(rawPcm, item.pitch, sampleRate)
} else { rawPcm }
```

This applies pitch modification to the finished vocoder output — exactly the approach that was identified as architecturally wrong. Resampling breaks phase coherence and OLA introduces windowing artifacts. The correct path is mel-space bin shifting before the vocoder renders, via the planned MelInterceptor (split ONNX graph: acoustic model → intercept → vocoder).

**Action:** Delete `AudioDsp.kt`. In `AudioPipeline.kt`, remove lines 152–155 and pass `rawPcm` directly to `playPcm`. Remove the `AudioDsp` keep rule from `proguard-rules.pro`. Keep the `pitch` field on `VoiceProfile` and `AudioPipeline.Item` — it becomes the input to the MelInterceptor when that's built.

### trajectory_config.json — dead asset

`trajectory_config.json` has zero references anywhere in the Kotlin source. `grep -rn "trajectory" --include="*.kt"` returns nothing. The file defines prosody envelopes (building/peaked/collapsed arcs for volume, breathiness, jitter, pitch, speed) that were designed for the mel interception layer, but nothing currently loads or consumes it.

**Action:** Either delete it now and recreate when the ONNX graph split happens, or leave it as a dormant spec. Either way, it's inert.

---

## 10. TtsAliveService — permanent wake lock

### The problem

`TtsAliveService` acquires a `PARTIAL_WAKE_LOCK` for 10 minutes and renews it every 9 minutes in a `Handler` loop:

```kotlin
wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kyokan::TtsAlive")
    .apply { acquire(10 * 60 * 1000L) }
handler.postDelayed(wakeLockRenewer, 9 * 60 * 1000L)
```

The renewer re-acquires for another 10 minutes before the previous one expires, creating an infinite chain. This keeps the CPU awake permanently — even when no notifications are arriving and no TTS is happening. On battery-conscious OEMs (Samsung, Xiaomi, OnePlus), this will show up as a top battery drain in Settings.

### The fix

The wake lock should only be held during active TTS synthesis, not 24/7. Two approaches:

**Option A — Scoped wake lock in AudioPipeline:** Acquire the wake lock in `processItem()` before synthesis starts, release it after `playPcm()` finishes. This means the CPU is only kept awake for the ~0.5–2s it takes to synthesize and play each notification.

**Option B — Remove TtsAliveService entirely.** The `NotificationReaderService` is already a system-bound `NotificationListenerService` — it doesn't get killed by OEMs the same way regular services do. The `mediaPlayback` FGS (from the fix in section 1) also provides process priority during active audio. The keepalive service may be solving a problem that the correct FGS type already solves.

---

## 11. TtsAliveService — missing FGS type in startForeground()

`TtsAliveService.onCreate()` calls:

```kotlin
startForeground(NOTIFICATION_ID, buildNotification())
```

This overload doesn't pass a `foregroundServiceType`. On Android 14+ (target SDK 34+), calling `startForeground()` without specifying the type that matches the manifest declaration throws `MissingForegroundServiceTypeException`. Even after you switch from `specialUse` to `mediaPlayback`, you need:

```kotlin
ServiceCompat.startForeground(
    this,
    NOTIFICATION_ID,
    buildNotification(),
    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
)
```

This is a crash on Android 14+ devices — same severity as the FGS type issue in section 1.

---

## 12. ProGuard rules — R8 effectively disabled

The `proguard-rules.pro` file keeps almost every class in the app with `{ *; }` (all members preserved):

```
-keep class com.echolibrium.kyokan.AudioPipeline { *; }
-keep class com.echolibrium.kyokan.AudioDsp { *; }
-keep class com.echolibrium.kyokan.SherpaEngine { *; }
-keep class com.echolibrium.kyokan.CloudTtsEngine { *; }
-keep class com.echolibrium.kyokan.VoiceRegistry { *; }
...  (20+ more classes)
```

This tells R8 not to rename, remove, or optimize any of these classes. Since this covers essentially every file in the app, R8's minification/obfuscation does almost nothing — the code in the DEX is effectively unobfuscated. Anyone running `jadx` on the APK sees readable class names, method names, and string constants.

**Fix:** Most of these keep rules are unnecessary. R8 already preserves classes referenced from the manifest (activities, services, receivers, providers). Only keep rules needed for JNI (`com.k2fsa.sherpa.onnx.**`), reflection-based deserialization (`VoiceProfile`, `AppRule`), and fragment instantiation (`*Fragment`). Remove the blanket keeps on `AudioPipeline`, `SherpaEngine`, `CloudTtsEngine`, etc. — they're referenced from normal Kotlin code and R8 traces them automatically.

---

## 13. API key exposure via BuildConfig

`AudioPipeline.initCloudTts()` falls back to `BuildConfig.DEEPINFRA_API_KEY` if no user-entered key is found in EncryptedSharedPreferences:

```kotlin
val key = userKey.ifBlank { BuildConfig.DEEPINFRA_API_KEY }
```

`BuildConfig` fields are compiled as static string constants in the DEX. R8 cannot obfuscate string literals. Anyone running `strings` on the APK or decompiling with jadx can extract the DeepInfra API key in seconds — especially since ProGuard keeps `AudioPipeline { *; }`.

**Fix:** Remove the `BuildConfig` fallback entirely. If you need a default key for development, inject it only in debug builds and strip it from release:

```groovy
buildTypes {
    debug {
        buildConfigField "String", "DEEPINFRA_API_KEY",
            "\"${localProps.getProperty('DEEPINFRA_API_KEY', '')}\""
    }
    release {
        buildConfigField "String", "DEEPINFRA_API_KEY", "\"\""
    }
}
```

In release, the only key source should be EncryptedSharedPreferences (user-entered).

---

## 14. security-crypto alpha in production

The dependency `androidx.security:security-crypto:1.1.0-alpha06` is an alpha-channel library:

```groovy
implementation 'androidx.security:security-crypto:1.1.0-alpha06'
```

Alpha APIs can change or break between versions without deprecation. The stable release is `1.0.0` — it lacks some convenience methods but is production-safe. Alternatively, `1.1.0-alpha06` has been sitting at alpha for years and is unlikely to break, but it's still technically unsupported for production use. If Play Store reviewers flag it, switch to `1.0.0`.

---

## 15. Language detection is regex-only

`NotificationReaderService.detectLanguage()` uses hardcoded French accent counting and word-pattern regex matching, defaulting to English for everything else:

```kotlin
val frenchAccents = lower.count { it in "éèêëàâùûôîïç" }
if (frenchAccents >= 2) return "fr"
```

This will misidentify Spanish, Portuguese, and Turkish text containing accented characters as French. It also has no support for the other 12 languages in `NotificationTranslator.LANGUAGES` — German, Japanese, Korean, Arabic, etc. are all treated as English.

**Fix:** ML Kit already provides a language identification API (`LanguageIdentification.getClient()`) that works on-device. Since ML Kit Translate is already a dependency, adding language ID is minimal overhead and would correctly handle all supported languages.

---

## 16. Voice command responses hardcoded in English

`VoiceCommandHandler` speaks responses like "No notification has been read yet" and "The last notification was X ago" always in English, regardless of the user's language, active profile, or translation settings. If the app is being used by a French-speaking user with French notifications being translated, the voice command responses still come back in English.

**Action:** Either route command responses through the same translation pipeline as notifications, or at minimum pull response strings from `strings.xml` so the 55+ locale translations apply.

---

## 17. Additional observations

### largeHeap

The manifest declares `android:largeHeap="true"`. This is expected given the ONNX Runtime and TTS model memory requirements, but be aware that `largeHeap` increases GC pause times. Profile heap usage — if peak memory is under 256MB, you may not need it, and disabling it would improve GC responsiveness.

### extractNativeLibs

`extractNativeLibs="true"` means native `.so` files are extracted from the APK at install time. On Android 6+ with `extractNativeLibs="false"`, the system loads them directly from the APK via memory mapping, saving ~42MB of disk space. However, this requires the `.so` files to be stored uncompressed in the APK (which increases download size). Since the native libs are the bulk of your app, test both approaches to find the better tradeoff for your users.

### Queries tag

The `<queries>` block with `MAIN/LAUNCHER` intent lets `AppsFragment` list installed apps for the notification filter UI — this is appropriate and correctly scoped.

### Localization completeness

55+ locales is excellent. Verify that SherpaONNX/Kokoro TTS engine names, error messages, and technical settings labels are excluded from translation. Common issue: auto-translated strings like "Kokoro-82M" becoming garbled.

---

## Summary — prioritized action list

| # | Item | Severity | Effort |
|---|------|----------|--------|
| 1 | Change FGS type from `specialUse` to `mediaPlayback` | **Blocker** | Low |
| 2 | Add `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission, remove `FOREGROUND_SERVICE_SPECIAL_USE` | **Blocker** | Low |
| 3 | Update `startForeground()` calls to pass `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` (both services) | **Blocker** | Low |
| 4 | Fix `TtsAliveService.startForeground()` missing type parameter — crashes Android 14+ | **Blocker** | Low |
| 5 | Add Play Console FGS type declaration | **Blocker** | Low |
| 6 | Delete `AudioDsp.kt`, remove pitch shift call in `AudioPipeline.kt` | High | Low |
| 7 | Remove `RECORD_AUDIO` if voice commands not yet shipped | High | Low |
| 8 | Add permission guard to BootReceiver | High | Low |
| 9 | Enable V3 + V4 signing | High | Low |
| 10 | Remove BuildConfig API key fallback from release builds | High | Low |
| 11 | Fix permanent wake lock — scope to active synthesis only or remove TtsAliveService | High | Medium |
| 12 | Trim ProGuard keep rules — R8 is effectively disabled | Medium | Low |
| 13 | Evaluate removing `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Medium | Medium |
| 14 | Remove datatransport components via manifest merge rules | Medium | Low |
| 15 | Switch to App Bundle (.aab) for distribution | Medium | Low |
| 16 | Replace regex language detection with ML Kit LanguageIdentification | Medium | Medium |
| 17 | Localize voice command responses | Low | Low |
| 18 | Upgrade `security-crypto` from alpha to stable 1.0.0 | Low | Low |
| 19 | Upgrade Kotlin to 2.0+ | Low | Medium |
| 20 | Upgrade AGP to 8.7+ | Low | Medium |
| 21 | Evaluate `extractNativeLibs="false"` | Low | Low |
| 22 | Consider armeabi-v7a support | Low | High |
