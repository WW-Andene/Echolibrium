# Kyōkan v3.0 — Full APK Audit Report

**Package:** `com.echolibrium.kyokan`
**Version:** 3.0 (versionCode 3)
**Build SDK:** compileSdk 34, targetSdk 34, minSdk 26
**APK Size:** 18.6 MB
**Analysis Date:** 2026-03-13
**Analysis Method:** APK reverse engineering via androguard (DEX class extraction, manifest parsing, resource analysis, native lib inspection)

---

## 1. Build & Size Metrics

| Metric | Value |
|---|---|
| Total files in APK | 680 |
| Native libs (uncompressed) | 41.0 MB |
| DEX bytecode (uncompressed) | 2.2 MB |
| Resources (uncompressed) | 1.2 MB |
| Total DEX classes | 3,052 |
| App classes (`com.echolibrium`) | 195 |
| Library/framework classes | 2,857 |
| Named app classes | 165 |
| Obfuscated/generated classes | 30 |
| Architecture | arm64-v8a only |
| Signing | v2 only, debug certificate |

### Native Libraries

| Library | Size | Status |
|---|---|---|
| `libonnxruntime.so` | 16 MB | stripped |
| `libsherpa-onnx-c-api.so` | 4.4 MB | stripped |
| `libsherpa-onnx-jni.so` | 4.7 MB | stripped |
| `libsherpa-onnx-cxx-api.so` | 402 KB | stripped |
| `libonnxruntime4j_jni.so` | 743 KB | **NOT stripped (debug_info)** |
| `libtranslate_jni.so` | 16 MB | stripped |

---

## 2. Architecture Overview

### Components (Manifest)

| Type | Component | Notes |
|---|---|---|
| Activity | `MainActivity` | exported, LAUNCHER |
| Service | `NotificationReaderService` | NotificationListenerService, BIND_NOTIFICATION_LISTENER, foregroundServiceType=specialUse |
| Service | `TtsAliveService` | not exported, foregroundServiceType=specialUse |
| Receiver | `BootReceiver` | exported, BOOT_COMPLETED |
| Provider | MlKitInitProvider | ML Kit translation init |
| Provider | InitializationProvider | AndroidX Startup |

### App Class Architecture (inferred from DEX)

**Core Pipeline:**
- `NotificationReaderService` → notification capture
- `SignalExtractor` → context analysis (sender type, urgency, stakes)
- `NotificationTranslator` → ML Kit translation layer
- `MoodUpdater` / `MoodState` → emotional state tracking
- `StyleSculptor` → emotion-to-voice mapping via `VoicePalette` and `EmotionBlend`
- `YatagamiSynthesizer` → TTS orchestrator, routes to engines

**TTS Engines:**
- `DirectOrtEngine` → Kokoro-82M via ONNX Runtime direct inference
- `SherpaEngine` → Piper/VITS via SherpaONNX

**Voice Management:**
- `VoiceProfile` → per-profile settings
- `KokoroVoices` / `KokoroVoice` → Kokoro voice registry
- `PiperVoices` / `PiperVoice` → Piper voice registry
- `VoiceDownloadManager` / `PiperDownloadManager` → on-demand model downloads
- `VoiceInfo` / `ModulatedVoice` → voice metadata and wrapping

**Audio Processing:**
- `PhonemeTokenizer` / `PhonicAnalyzer` / `PhonicLandmarks` → phoneme-level text analysis
- `VoiceModulator` (with `Quad` processing) → pitch/formant modification
- `VoiceTransform` → gimmicks (breathiness, stuttering, fry), with `Gimmick` and `GimmickPosition`
- `AudioDsp` → fry region processing
- `AudioPipeline` → queue-and-play output
- `TimeModifier` / `Trajectory` → temporal modifications
- `ScaleMapper` → parameter scaling

**Personality/Emotion System:**
- `EmotionBlend` → multi-axis emotion representation
- `PersonalitySensitivity` → sensitivity thresholds
- `CommentaryPool` / `CommentaryCondition` → personality commentary injection
- `GimmickConfig` → gimmick parameter storage
- `FloodTier` → notification spam detection
- `WarmthLevel` / `StakesLevel` / `UrgencyType` / `SenderType` / `SourceType` / `Register` → signal taxonomy

**Voice Commands:**
- `VoiceCommandListener` / `CommandRecognitionListener` → always-on mic listener
- `VoiceCommandHandler` → command dispatch

**UI Fragments:**
- `HomeFragment` → main dashboard
- `ProfilesFragment` → voice profile editing (11+ lambda bindings in loadProfileToUI)
- `AppsFragment` → per-app rule configuration
- `RulesFragment` → text rules (find/replace, language profiles, translate routes)

---

## 3. What's Good

**Architecture is coherent and ambitious.** The separation between notification capture, signal extraction, mood tracking, style sculpting, TTS synthesis, voice modulation, and audio output forms a genuine pipeline. Each stage has a clear responsibility.

**Dual TTS backend** with Kokoro-82M (DirectOrtEngine, direct ONNX inference) and Piper/VITS (SherpaEngine) gives flexibility. Kokoro for quality, Piper for breadth of voices.

**Deep audio processing.** `AudioDsp` with fry regions, `VoiceModulator` with quad processing, `VoiceTransform` with breathiness/stuttering, `StyleSculptor` with 5-axis emotion blending and voice palettes — this goes well beyond typical TTS wrapper apps.

**Phoneme-level analysis.** `PhonemeTokenizer` + `PhonicAnalyzer` + `PhonicLandmarks` suggest pre-synthesis text analysis that informs where gimmicks and modifications land, not just blind global DSP.

**Translation via ML Kit** (`NotificationTranslator` + `libtranslate_jni.so`) with on-device models is a practical differentiator.

**Good manifest practices:**
- `usesCleartextTraffic=false` — forces HTTPS
- `FOREGROUND_SERVICE_SPECIAL_USE` with proper `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` declarations
- `BIND_NOTIFICATION_LISTENER_SERVICE` permission correctly on the service
- `BootReceiver` for auto-restart after reboot
- `WRITE_EXTERNAL_STORAGE` has `maxSdkVersion=29`, `READ_EXTERNAL_STORAGE` has `maxSdkVersion=32`

**Flood protection.** `FloodTier` class indicates notification spam handling — a common oversight.

**Distinctive naming.** YatagamiSynthesizer, StyleSculptor, Kyōkan — the project has identity.

---

## 4. What's Wrong

### CRITICAL: Debug Signing
The APK is signed with an **"Android Debug" certificate** (serial: 1, subject: "Android Debug"). This cannot be published to the Play Store, and sideload updates will fail if the debug keystore changes. Generate a proper release keystore immediately.

### CRITICAL: `QUERY_ALL_PACKAGES`
Google Play rejects apps with this permission unless an approved use case is declared. If used for `AppsFragment` to list installed apps, replace with targeted `<queries>` blocks for specific package intents.

### CRITICAL: `MANAGE_EXTERNAL_STORAGE` without `maxSdkVersion`
Play Store blocker — requires a special declaration form. Evaluate whether scoped storage via `MediaStore` or app-specific directories would work for voice model storage.

### HIGH: `allowBackup=true`
All SharedPreferences (voice profiles, rules, mood state, per-app configs) get backed up to Google Drive unencrypted. For an app that reads all notifications, this is a privacy concern. Set to `false` or implement `BackupAgent` with encryption.

### HIGH: `libonnxruntime4j_jni.so` not stripped
743 KB of debug symbols shipping in release. Add `strip` to the native build or ensure `android.buildTypes.release.isDebuggable = false` in Gradle.

### MEDIUM: arm64-only
No `armeabi-v7a` ABI present. Excludes older 32-bit ARM devices and all x86 emulators. Fine for personal use, problematic for distribution.

### MEDIUM: No v3 APK signing
Only v2 signing detected. v3 signing enables key rotation — painful to not have once the app is distributed.

### MEDIUM: No `networkSecurityConfig`
While `usesCleartextTraffic=false` is set, a proper network security config XML enables certificate pinning for voice model download servers and granular control.

### LOW: Partial R8/ProGuard
165 named app classes vs 30 single-letter classes suggests either R8 is off entirely (and the short names are actual source classes) or keep rules are overly broad. If R8 is enabled, the keep rules need tightening. If disabled, enable it.

### LOW: No `AccessibilityService`
Purely notification-listener based — won't catch in-app toasts, snackbars, or heads-up content that doesn't produce `StatusBarNotification`. Likely intentional but worth noting.

---

## 5. What Needs Change

### Target SDK 35
Currently at 34. Google requires SDK 35 targeting by August 2025 for Play Store updates. The `FOREGROUND_SERVICE_SPECIAL_USE` type (`0x40000000 = specialUse`) may need re-justification under SDK 35 foreground service rules.

### APK → AAB
19 MB APK is mostly native libs (41 MB uncompressed). Android App Bundles (AAB) would let Play Store serve only the needed ABI, cutting download size roughly in half for most users.

### Permission Scoping
Replace `QUERY_ALL_PACKAGES` with targeted `<queries>`:
```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
    </intent>
</queries>
```
Replace `MANAGE_EXTERNAL_STORAGE` with scoped storage or `getExternalFilesDir()` for model storage.

### ProfilesFragment Complexity
`loadProfileToUI` has 11+ lambda bindings — symptom of `VoiceProfile` being a flat god-object. Decompose (see Voice System Improvements below).

---

## 6. Voice System: Improvements & Restructure

### 6.1 Decompose VoiceProfile

Current: `VoiceProfile` bundles engine choice, model, DSP settings, gimmick config, and emotion mapping in one flat object.

Proposed layered composition:
- **VoiceIdentity** — engine (Kokoro/Piper), model file, base pitch/speed. The "who."
- **ExpressionMap** — emotion→DSP parameter mappings with `ExpressionCurve` (8-point lookup tables, not linear). The "how they react."
- **GimmickSet** — transform chain config (breathiness curve, stutter probability, fry regions, commentary pool bindings). The "personality texture."
- **VoiceProfile** — named container holding one VoiceIdentity + one ExpressionMap + one GimmickSet + per-app overrides.

This enables mix-and-match: same Kokoro voice with different expression maps, same gimmick set on different base voices.

### 6.2 Explicit DSP Pipeline Graph

Current: `VoiceModulator` → `VoiceTransform` → `AudioDsp` → `AudioPipeline` (hardcoded sequence).

Proposed `DspNode` interface + `DspChain`:
```kotlin
interface DspNode {
    fun process(samples: FloatArray, sampleRate: Int, context: UtteranceContext): FloatArray
    val name: String
    var enabled: Boolean
}
```
`UtteranceContext` carries `PhonicLandmarks`, `EmotionBlend`, `SignalMap`.

Benefits:
- Reorder nodes per-profile
- Disable individual stages
- Split chain at vocoder boundary for mel-spectrogram interception (pre-vocoder mel DSP vs post-vocoder PCM DSP)
- Log per-node timing/parameters to SQLite for Custom Core observation layer

### 6.3 Non-Linear Expression Curves

`StyleSculptor.applyEmotionBlend` has 5 lambda bindings suggesting 5 emotion axes mapped independently — likely linear. Human vocal expression has thresholds and plateaus (whisper→normal→shout are distinct registers, not a gradient).

Introduce `ExpressionCurve` — 8-point lookup table mapping emotion intensity [0..1] to parameter value with interpolation:
- Anger 0.0–0.3: minimal pitch change. 0.4+: jumps to higher register
- Sadness: slow roll-off on speed with a floor
- Excitement: compresses dynamic range at high intensity

`ScaleMapper` already hints at this — make it the universal mechanism.

### 6.4 Phoneme-Level Duration Control

`PhonemeTokenizer` → `PhonicLandmarks` currently informs gimmick placement. Extend to output `DurationMap` — per-phoneme duration ratios based on emotion context:
- Stretch vowels by 1.3x in sadness
- Compress plosives by 0.8x in excitement
- Insert breath segments with their own mel-space representation

When mel-spectrogram interception lands, `DurationMap` feeds directly into mel-space frame duplication/removal. `Trajectory` class suggests time-varying parameter evolution is already conceived — make it first-class.

### 6.5 Decouple Voice Selection from Modification

Current: `KokoroVoices`/`PiperVoices` are static registries, `ModulatedVoice` wraps with modifications, download managers handle fetching. Voice selection and modulation are tangled at the profile level.

Proposed clean separation:
- **VoiceRegistry** — singleton, all available voices across engines, download state, capabilities (sample rates, languages, native emotion tokens)
- **VoiceSynthesizer** — stateless: `VoiceIdentity` + text + `EmotionBlend` → raw PCM. Routes to correct engine internally.
- **VoiceProcessor** — raw PCM + `DspChain` + `UtteranceContext` → processed PCM
- **AudioPipeline** — unchanged, queue and play

Enables engine swaps without touching the processing chain, new engine additions with zero modulation impact, and DSP chain testing with synthetic PCM.

### 6.6 Event-Driven Commentary System

`CommentaryPool` + `CommentaryCondition` should emit from an event bus rather than being evaluated per-notification:
```kotlin
sealed class PersonalityEvent {
    data class MoodShift(val from: MoodState, val to: MoodState)
    data class FloodDetected(val tier: FloodTier, val app: String)
    data class SilenceBroken(val durationMs: Long)
    data class NewSenderType(val type: SenderType)
}
```
Commentary triggers subscribe to events. Observation layer logs events to SQLite. Groq behavior patches add/remove/modify event subscriptions.

### 6.7 Data-Driven Voice System

Move emotion→DSP mappings, gimmick parameters, and voice selection logic from Kotlin `WhenMappings` and hardcoded logic into JSON/proto structures owned by `VoiceProfile`. Custom Core behavior patches become profile patches — swap a JSON blob, get different behavior, no code changes.

---

## 7. Permissions Audit

| Permission | Justification | Risk | Action |
|---|---|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Core feature | Expected | OK |
| `RECORD_AUDIO` | Voice commands | High — always-on mic + notification reading | Document privacy story |
| `FOREGROUND_SERVICE` | Service persistence | Expected | OK |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Notification reader TTS | Expected | OK, has PROPERTY_SPECIAL_USE_FGS_SUBTYPE |
| `POST_NOTIFICATIONS` | Own status notifications | Expected | OK |
| `RECEIVE_BOOT_COMPLETED` | Auto-restart | Expected | OK |
| `WAKE_LOCK` | TTS during doze | Expected | OK |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Background persistence | Medium | OK for this use case |
| `INTERNET` | Model downloads, translation | Expected | OK |
| `ACCESS_NETWORK_STATE` | Download state checks | Expected | OK |
| `QUERY_ALL_PACKAGES` | Per-app rules | **Play Store blocker** | Replace with `<queries>` |
| `MANAGE_EXTERNAL_STORAGE` | Model storage | **Play Store blocker** | Replace with scoped storage |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 29) | Legacy compat | OK | Properly scoped |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Legacy compat | OK | Properly scoped |

---

## 8. Implementation Priorities

### P0 — Release Blockers
1. Generate release keystore, re-sign with v2+v3
2. Strip `libonnxruntime4j_jni.so` debug symbols
3. Set `allowBackup=false` or implement encrypted BackupAgent
4. Replace `QUERY_ALL_PACKAGES` with `<queries>`
5. Replace `MANAGE_EXTERNAL_STORAGE` with scoped storage

### P1 — Before Distribution
6. Enable R8 full mode, audit keep rules
7. Add `networkSecurityConfig` with certificate pinning
8. Target SDK 35
9. Consider AAB format for distribution
10. Add `armeabi-v7a` support or document arm64 requirement

### P2 — Voice System Restructure
11. Decompose `VoiceProfile` into VoiceIdentity/ExpressionMap/GimmickSet
12. Implement `DspNode`/`DspChain` pipeline graph
13. Introduce `ExpressionCurve` for non-linear emotion mapping
14. Decouple VoiceRegistry from VoiceSynthesizer from VoiceProcessor

### P3 — Custom Core Preparation
15. Add SQLite observation layer (per-node timing, event logging)
16. Implement `PersonalityEvent` bus for commentary system
17. Move WhenMappings to JSON-based expression/gimmick configs
18. Extend `PhonicLandmarks` to output `DurationMap` for mel-spectrogram interception
19. Implement `Trajectory` as first-class per-utterance parameter curves

---

## 9. Full App Class Listing

### Core
`MainActivity`, `NotificationReaderService`, `TtsAliveService`, `BootReceiver`

### Synthesis
`YatagamiSynthesizer` (with `EngineType`, `SynthResult`, `Companion`), `DirectOrtEngine`, `SherpaEngine`

### Voice Management
`VoiceProfile`, `VoiceInfo`, `ModulatedVoice`, `KokoroVoice`, `KokoroVoices`, `PiperVoice`, `PiperVoices`, `VoiceDownloadManager`, `PiperDownloadManager`

### Audio Processing
`AudioPipeline` (with `Item`), `AudioDsp`, `VoiceModulator` (with `Quad`), `VoiceTransform` (with `Gimmick`, `GimmickPosition`), `TimeModifier`, `Trajectory`

### Text Analysis
`PhonemeTokenizer`, `PhonicAnalyzer`, `PhonicLandmarks`, `NotificationTranslator`

### Emotion & Personality
`StyleSculptor` (with `VoicePalette`), `EmotionBlend`, `MoodState`, `MoodUpdater`, `PersonalitySensitivity`, `CommentaryPool`, `CommentaryCondition`, `GimmickConfig`

### Signal Analysis
`SignalExtractor`, `SignalMap`, `ScaleMapper`, `FloodTier`, `SenderType`, `SourceType`, `UrgencyType`, `StakesLevel`, `StakesType`, `WarmthLevel`, `Register`

### Voice Commands
`VoiceCommandListener` (with `CommandRecognitionListener`), `VoiceCommandHandler`

### Rules
`AppRule`, `RulesFragment`, `AppsFragment`

### UI
`HomeFragment`, `ProfilesFragment`, `RulesFragment`, `AppsFragment`
