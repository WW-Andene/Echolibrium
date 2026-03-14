# Kyōkan Architecture Guide

> Quick-reference for any Claude session working on this codebase.

---

## What Is This Project?

**Kyōkan** (共感, "empathy") / **Echolibrium** is an Android app that reads notifications aloud using fully offline TTS. It doesn't just read — it *reacts* to what it reads, modulating voice pitch, speed, breathiness, and stutter based on the emotional content of each notification.

- **Package:** `com.echolibrium.kyokan`
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34
- **ABI:** arm64-v8a only
- **Language:** Kotlin, single-module Android app
- **TTS backend:** [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (ONNX Runtime + custom JNI)

---

## Two-Process Architecture

This is the most important thing to understand. The app runs in **two separate OS processes**:

```
┌─────────────────────────────────┐    ┌──────────────────────────────────────┐
│  Main Process (default)         │    │  :tts Process (android:process=":tts") │
│                                 │    │                                        │
│  MainActivity                   │    │  NotificationReaderService             │
│  HomeFragment                   │    │  TtsCommandReceiver                    │
│  ProfilesFragment               │    │  SherpaEngine                          │
│  AppsFragment / RulesFragment   │    │  AudioPipeline                         │
│  ReaderApplication              │    │  VoiceCommandListener                  │
│    └─ TTS process watchdog      │    │  AudioDsp                              │
│  OemProtection                  │    │  PhonicAnalyzer                        │
│  GitHubReporter                 │    │                                        │
│                                 │    │                                        │
│  Reads tts_status.json ◄────────┼────┼── Writes tts_status.json               │
│  Sends broadcasts ──────────────┼────┼──► TtsCommandReceiver receives          │
│  Watchdog checks staleness ─────┼────┼── requestRebind() restarts if killed    │
└─────────────────────────────────┘    └──────────────────────────────────────┘
```

**Why two processes?** The sherpa-onnx native library (ONNX Runtime, ~50MB .so) conflicts with Android's HWUI RenderThread in the same process. Loading native TTS models in a process with a GPU-accelerated UI causes SIGSEGV on Xiaomi/MediaTek devices. The `:tts` process has no UI, no HWUI, no RenderThread — so native code runs safely.

**Cross-process IPC:**
- **Commands (UI → TTS):** Explicit broadcasts via `TtsBridge` → `TtsCommandReceiver`
- **Status (TTS → UI):** JSON file (`tts_status.json`) written atomically by TTS process, polled by UI every 1s

---

## Source File Map

All source is in `app/src/main/java/com/echolibrium/kyokan/`.

### Core Engine (runs in :tts process)

| File | Role |
|---|---|
| `SherpaEngine.kt` | **Heart of the app.** Manages Kokoro (311MB), Piper (~60MB), and Android system TTS fallback. Crash recovery, device profiling, config memory, SIGSEGV-surviving format fallback, `ApplicationExitInfo`-based crash classification. ~2000 lines. |
| `AudioPipeline.kt` | Single-threaded FIFO queue. Receives notification items, calls SherpaEngine for synthesis, applies DSP + pitch shift, plays via AudioTrack. Inter-clip crossfade (40ms) for smooth transitions between utterances. |
| `AudioDsp.kt` | PCM audio effects chain: volume → soft saturation → formant smoothing → breathiness → spectral tilt → jitter → vocal fry → trailing off → soft limiter. Also handles resampling-based pitch shifting with OLA time-stretch. 30ms dry→wet ramp-in prevents abrupt effect onset. |
| `PhonicAnalyzer.kt` | Analyzes synthesized PCM for context-aware DSP: voiced regions, phrase endings, energy contour, dynamic range, speech boundaries. |
| `NotificationReaderService.kt` | `NotificationListenerService` — captures notifications, filters (DnD, per-app rules, dedup), extracts signals, modulates voice, enqueues to AudioPipeline. Conversation grouping (1.5s buffer). Also provides `testSpeak()` for the UI talk feature. |
| `TtsCommandReceiver.kt` | `BroadcastReceiver` — receives commands from UI process and dispatches to SherpaEngine/AudioPipeline/VoiceCommandListener. |

### Voice & Personality System

| File | Role |
|---|---|
| `SignalExtractor.kt` | Pattern-matching engine — detects urgency, emotion, stakes, sarcasm, emotion blends from notification text. Computes flood tier from daily notification count. No LLM. |
| `SignalMap.kt` | Data class holding extracted signal strengths. Includes enums: `EmotionBlend` (NERVOUS_EXCITEMENT, SUPPRESSED_TENSION, etc.), `FloodTier` (CALM→OVERWHELMED), and all signal types (urgency, warmth, stakes, trajectory, register). |
| `VoiceModulator.kt` | Maps signal strengths × personality sensitivity → voice parameter adjustments. Features: time-of-day baseline shifts, flood tier modifiers, emotion blend overrides, sarcasm flattening, modulation budget cap, NaN guard. |
| `VoiceTransform.kt` | Text preprocessing pipeline: wording rules → commentary → gimmicks → intonation → stutter → breathiness. Uses quadratic `smooth()` curves for gradual slider response. |
| `VoiceProfile.kt` | User-configurable voice personality. Includes `PersonalitySensitivity` (per-profile signal reaction multipliers: distress, warmth, fake, mood velocity/decay, range/speed reactivity). JSON serialization for persistence. |
| `CommentaryPool.kt` | Bank of pre/post commentary lines injected based on notification signals. |
| `MoodState.kt` | Persistent emotional state that evolves based on notification stream. |
| `KokoroVoice.kt` | Kokoro voice ID mapping (speaker IDs for the multi-speaker model). |

### Voice Management

| File | Role |
|---|---|
| `PiperVoiceCatalog.kt` | Registry of all available Piper voices (bundled + downloadable). |
| `PiperVoiceManager.kt` | UI-facing voice management — list, download, delete Piper voices. |
| `VoiceDownloadManager.kt` | Downloads Piper voice models on demand from GitHub releases. |

### Voice Commands

| File | Role |
|---|---|
| `VoiceCommandListener.kt` | Always-on microphone listener for wake word detection. |
| `VoiceCommandHandler.kt` | Processes recognized voice commands (stop, repeat, skip, etc.). |

### UI (runs in main process)

| File | Role |
|---|---|
| `MainActivity.kt` | Single-activity host with bottom navigation (Home, Voices, Apps). |
| `HomeFragment.kt` | Permission setup, service status, master switch, read mode, DnD schedule, user talk (type & speak), battery/restricted settings. |
| `ProfilesFragment.kt` | Voice profile editor — sliders for pitch, speed, effects. Test playback. Commentary and gimmick configuration. |
| `AppsFragment.kt` | Per-app notification rules list. |
| `RulesFragment.kt` | Rule editor for individual apps. |
| `CrashReportActivity.kt` | Displays crash details when the app recovers from a crash. |

### Infrastructure

| File | Role |
|---|---|
| `ReaderApplication.kt` | `Application` subclass — installs global exception handler, crash recovery (main process only). Runs TTS process watchdog (15s polling, auto-restarts killed `:tts` via `requestRebind()`). |
| `TtsBridge.kt` | Cross-process bridge — command broadcasts, status file I/O, battery optimization helpers, staleness detection, AutoStart intent launcher. |
| `OemProtection.kt` | All-OEM background kill prevention. AutoStart intents for Xiaomi, Samsung, Huawei, OnePlus, Oppo, Vivo, Meizu, Asus, Lenovo, Nokia, Sony, and more. Three layers: battery exemption → OEM intent → user guidance. |
| `GitHubReporter.kt` | Automatic error reporting — after 30s with TTS error, creates GitHub Issue with full diagnostics. Rate-limited (1/hour). Also fetches remote config (force_retry, messages, min_version) every 6h. |
| `AppRule.kt` | Data class for per-app notification rules (block, priority, custom voice). |
| `BootReceiver.kt` | Starts the service on device boot. |

---

## Audio DSP Pipeline

The `AudioDsp` object applies effects to raw PCM from SherpaEngine. Effects are ordered to minimize artifacts:

```
Raw PCM from TTS
    ↓
1. Volume/gain — applies ModulatedVoice.volume
    ↓
2. Soft saturation — tanh(x * 1.05), very gentle, smooths peaks
    ↓
3. Formant smoothing — envelope-following blend (0.85 + 0.15 * ratio)
    ↓
4. Breathiness — filtered noise layer, intensity via quadratic curve
    ↓
5. Spectral tilt — low-pass filter proportional to breathiness (floor 3000Hz)
    ↓
6. Jitter — micro-amplitude variation, crossfaded 12ms windows (25% fade)
    ↓
7. Vocal fry — phrase-end amplitude modulation (uses PhonicLandmarks)
    ↓
8. Trailing off — gradual fade on collapsed trajectory
    ↓
9. Effect ramp-in — 30ms dry→wet crossfade at clip start
    ↓
10. Soft limiter — tanh-based above 0.9 threshold, prevents hard clipping
    ↓
Pitch shift — resampling + OLA time-stretch (25ms Hann windows, 50% overlap)
    ↓
Inter-clip crossfade — 40ms crossfade with previous clip's tail (AudioPipeline)
    ↓
AudioTrack playback at native sample rate
```

### Pitch Shifting

Pitch is shifted via resampling (linear interpolation), which changes both pitch and duration. Duration is then restored via overlap-add (OLA) time-stretching with 25ms Hann-windowed segments at 50% overlap. This replaces the old `AudioTrack.playbackRate` approach which shifted pitch AND speed together.

### Anti-Artifact Measures

- **No hard clipping** — all `coerceIn(-1, 1)` replaced with tanh soft limiter
- **Crossfaded jitter windows** — 25% fade at window edges prevents clicks
- **Effect ramp-in** — 30ms dry→wet crossfade at clip start prevents abrupt onset
- **Inter-clip crossfade** — 40ms crossfade between consecutive playbacks
- **Quadratic intensity curves** — `smoothCurve(v)` maps 0-100 through x² for gradual response

---

## Voice Modulation System

### Signal Flow

```
Notification text
    ↓
SignalExtractor.extract() → SignalMap
    ├─ sourceType, senderType, warmth, register
    ├─ stakesLevel/Type, urgencyType, intensityLevel
    ├─ trajectory, unknownFactor, emojiSad
    ├─ emotionBlend (detected from signal combinations)
    ├─ detectedSarcasm (pattern-based: starters + complaint words)
    ├─ floodTier (from daily notification count)
    └─ senderPressure, senderRepeat, senderRecency
    ↓
VoiceModulator.modulate(profile, signal) → ModulatedVoice
    ├─ Signal strengths × PersonalitySensitivity multipliers
    ├─ Time-of-day baseline (pitch/speed/breath/intonation shifts by hour)
    ├─ Flood tier effects (speed up, more breath, flatten intonation)
    ├─ Emotion blend overrides (e.g., nervous excitement → pitch+speed up)
    ├─ Sarcasm → flatten intonation by 30%
    ├─ Modulation budget cap (max ±0.25 pitch, ±0.35 speed delta)
    └─ Volume adjustment (urgency up, distress/night/overwhelmed down)
    ↓
ModulatedVoice (pitch, speed, breath, stutter, intonation, jitter, volume, trailOff)
```

### PersonalitySensitivity

Each VoiceProfile has a `PersonalitySensitivity` that scales how strongly it reacts to signals:

| Field | Default | Effect |
|---|---|---|
| `distressSensitivity` | 1.0 | Multiplier for distress/urgency signal strength |
| `warmthSensitivity` | 1.0 | Multiplier for emotional stakes signal strength |
| `fakeSensitivity` | 1.0 | Multiplier for fake/game stakes signal strength |
| `moodVelocity` | 1.0 | How fast mood shifts (for MoodState) |
| `moodDecayRate` | 1.0 | How fast mood returns to baseline |
| `rangeReactivity` | 1.0 | Multiplier for intonation variation |
| `speedReactivity` | 1.0 | Multiplier for speed changes |

### EmotionBlend

Complex mixed states detected from signal combinations:

| Blend | Pitch | Speed | Breath | Intonation |
|---|---|---|---|---|
| NERVOUS_EXCITEMENT | +0.06 | +0.08 | −3 | +10 |
| SUPPRESSED_TENSION | +0.03 | −0.04 | — | −8 |
| NOSTALGIC_WARMTH | −0.04 | −0.06 | +5 | +5 |
| RESIGNED_ACCEPTANCE | −0.02 | −0.08 | — | −12 |
| WORRIED_AFFECTION | +0.02 | −0.02 | +3 | +3 |

### FloodTier

Based on daily notification count:

| Tier | Count | Speed | Breath | Intonation |
|---|---|---|---|---|
| CALM | <20 | — | — | ×1.0 |
| ACTIVE | <50 | — | — | ×1.0 |
| BUSY | <100 | — | — | ×1.0 |
| FLOODED | <200 | +0.08 | +5 | ×0.85 |
| OVERWHELMED | 200+ | +0.05 | +10 | ×0.70 |

### Time-of-Day Modifiers

| Period | Pitch | Speed | Breath | Intonation |
|---|---|---|---|---|
| 0–4 (night) | −0.10 | −0.18 | +12 | −0.20 |
| 5–7 (early) | −0.05 | −0.08 | +3 | −0.10 |
| 8–11 (morning) | — | — | — | — |
| 12–14 (afternoon) | −0.02 | +0.03 | +1 | +0.05 |
| 15–18 (late afternoon) | — | +0.05 | — | +0.08 |
| 19–21 (evening) | −0.04 | −0.05 | +4 | −0.05 |
| 22–23 (late night) | −0.08 | −0.12 | +8 | −0.15 |

---

## SherpaEngine: Initialization Flow

This is the most complex and most-fixed part of the codebase. Understanding it is critical.

### Model Types

| Engine | Model Size | Quality | Reliability |
|---|---|---|---|
| **Kokoro** | 311MB (ONNX/ORT) | High — multi-speaker, natural | Crashes on some devices |
| **Piper** | ~60MB (VITS) | Good — single speaker | Very reliable fallback |
| **Android System TTS** | 0 (pre-installed) | Varies (Google TTS is decent) | Ultimate fallback — no native code |

### Initialization Stages (happy path)

```
warmUp(ctx)
  │
  ├─ Crash-loop check (SharedPreferences: init_in_progress flag)
  ├─ Set init_in_progress = true  ← BEFORE any native code
  │
  └─ Thread("SherpaEngine-warmup")
       │
       ├─ Stage 0: Native stack probe
       │    Load tiny Piper model to verify JNI/ORT works
       │
       ├─ Pre-flight: verify model.onnx, voices.bin, tokens.txt exist
       ├─ Disk space check (needs 400MB free)
       ├─ Extract model from APK assets → filesystem (atomic tmp+rename)
       ├─ Integrity check (model > 100MB, voices.bin > 1KB)
       │
       ├─ Stage 1: Device profiling + memory prep
       │    ├─ Profile: RAM, cores, SoC vendor, Xiaomi/MIUI/HyperOS detection
       │    ├─ Device tier: HIGH / MEDIUM / LOW
       │    ├─ GC (2-pass + finalization + 50ms sleep)
       │    ├─ RAM check (needs 350MB free)
       │    ├─ Heap reservation (alloc 25% max heap, touch pages, release)
       │    └─ Model pre-warming (sequential 256KB reads into page cache)
       │
       ├─ Stage 2: Config memory + auto-escalation
       │    ├─ Check SharedPreferences for last known-good config
       │    ├─ Check KEY_SKIP_ORT (set if previous .ort load caused SIGSEGV)
       │    ├─ Outer loop: model format (.ort first, .onnx fallback if both exist)
       │    │    ├─ Save KEY_INIT_FORMAT before loading (survives SIGSEGV)
       │    │    ├─ Escalation ladder: optimal → safe (1 thread, cpu) → diagnostic
       │    │    ├─ Run OfflineTts() on dedicated thread with URGENT_AUDIO priority
       │    │    ├─ Adaptive timeout (30s ORT / 90s ONNX × device tier multiplier)
       │    │    └─ If all configs fail → try next model format
       │    └─ On success: save config to memory, reset crash counter
       │
       └─ finally: clear init_in_progress flag
```

### Crash Recovery System

The native library (sherpa-onnx) can SIGSEGV — kill the process instantly. Android auto-restarts the NotificationListenerService, which triggers another init attempt. Without protection, this is an infinite crash loop.

**Detection:** `init_in_progress` flag in SharedPreferences. Set `true` (with `commit()`) before ANY native code. Cleared in `finally`. Only a SIGSEGV leaves it set.

**Crash classification (API 30+):** Uses `ApplicationExitInfo` to distinguish real native crashes from OS-initiated kills. Only `REASON_CRASH_NATIVE`, `REASON_CRASH`, and `REASON_ANR` increment the crash counter. HyperOS/MIUI process kills (`LOW_MEMORY`, `EXCESSIVE_RESOURCE_USAGE`, `OTHER`) are ignored — these are the OS being aggressive, not the app being broken.

**Recovery behavior by crash count:**

| Crash Count | Behavior |
|---|---|
| **0** | Normal Kokoro init |
| **1–7** | Set `init_in_progress=true` → try Piper fallback (tracked!) → if Piper works, user has TTS → exponential backoff retry Kokoro in background (5s, 10s, 20s, 40s, 80s, max 60s) |
| **8+** (`NATIVE_BROKEN_THRESHOLD`) | Skip ALL native code → try Android system TTS as ultimate fallback → if that fails too, show error with device diagnostics → retry after 1 min → user can force retry from UI |

**SIGSEGV-surviving format fallback:** The model format being loaded (`.ort` or `.onnx`) is persisted to `KEY_INIT_FORMAT` before the load attempt. If a SIGSEGV occurs during `.ort` loading, on restart `KEY_SKIP_ORT=true` is set, and the engine goes straight to `.onnx`. Without this, the engine would crash on `.ort` repeatedly until hitting the broken threshold.

**Synthesis cascade:** `synthesizeWithFallback()` tries engines in order: Kokoro → Piper → Android system TTS → null. System TTS uses `synthesizeToFile()` → reads WAV → returns PCM through the normal pipeline. DSP effects still work.

**Critical detail:** Piper uses the same native library as Kokoro. If `init_in_progress` isn't set before `initPiperFallback()`, a Piper SIGSEGV won't increment the crash counter → infinite loop at crashCount=1. This was a real bug (fixed March 2026).

**Config memory:** When Kokoro succeeds, the exact config (threads, provider, debug flag) is saved to SharedPreferences. Next launch skips the escalation ladder and uses the known-good config directly. Cleared on crash or app update.

**`forceRetry(ctx)`:** Resets crash counter to 0, clears `KEY_SKIP_ORT`, clears all error state, cleans up system TTS, re-attempts from scratch.

---

## Build System

```
build.gradle
├─ downloadTtsModels task (runs download-models.sh before build)
├─ aaptOptions: noCompress onnx, ort, bin, txt, dict, tab
├─ packagingOptions: useLegacyPackaging = false (default — loads .so from APK via mmap)
└─ ndk: arm64-v8a only

Dependencies:
├─ sherpa_onnx.aar (local, auto-downloaded)
├─ kotlin-stdlib 1.9.22
├─ androidx core-ktx, appcompat, constraintlayout, preference-ktx, fragment-ktx
├─ org.json (for GitHubReporter JSON handling)
```

**Native libraries** (inside sherpa_onnx.aar):
- `libonnxruntime.so` — ONNX Runtime (~50MB)
- `libsherpa-onnx-c-api.so` — sherpa-onnx C API
- `libsherpa-onnx-cxx-api.so` — sherpa-onnx C++ API
- `libsherpa-onnx-jni.so` — JNI bridge

**Note on `useLegacyPackaging`:** Set to `true` (extract .so to filesystem). The standalone sherpa-onnx APK works with `false` (mmap from APK), but it runs in the **main process**. Echolibrium loads native libs in a **separate `:tts` process** (`android:process=":tts"`), and on Xiaomi/HyperOS, background service processes fail to mmap native libs from the APK (EXIT_SELF status=255). Extracting to filesystem ensures any process can load reliably.

---

## Data Flow: Notification → Speech

```
1. Android OS delivers notification
        ↓
2. NotificationReaderService.onNotificationPosted()
        ↓
3. Filter: DnD check, per-app rules, dedup, self-notification skip
        ↓
4. Conversation grouping: buffer 1.5s, merge rapid messages from same sender
        ↓
5. SignalExtractor.extract(text) → SignalMap
   (urgency, emotion, stakes, sarcasm, emotion blend, flood tier)
        ↓
6. VoiceModulator.modulate(profile, signals) → ModulatedVoice
   (pitch, speed, breath, stutter, intonation, jitter, volume, trailOff)
        ↓
7. AudioPipeline.enqueue(Item(text, profile, modulatedVoice, signal, rules))
        ↓
8. Pipeline thread dequeues, applies VoiceTransform
   (commentary, gimmicks, intonation marks, stutter, breathiness markers)
        ↓
9. SherpaEngine.synthesize(text, speed) → FloatArray PCM + sampleRate
        ↓
10. PhonicAnalyzer.analyze(pcm) → PhonicLandmarks
        ↓
11. AudioDsp.apply(pcm, modulated, landmarks) → DSP effects chain
        ↓
12. AudioDsp.pitchShift(pcm, pitch) → resampling + OLA time-stretch
        ↓
13. AudioPipeline.applyCrossfade() → inter-clip smoothing
        ↓
14. AudioTrack.write(samples) → speaker output
```

---

## Key SharedPreferences

| Prefs Name | Process | Purpose |
|---|---|---|
| `sherpa_init_tracker` | :tts | Crash counter, init_in_progress flag, last crash time, KEY_INIT_FORMAT, KEY_SKIP_ORT |
| `sherpa_config_memory` | :tts | Last known-good engine config (threads, provider, debug, version) |
| Default prefs | main | Voice profiles (with PersonalitySensitivity), per-app rules, UI settings, active_profile_id, wording_rules, OEM protection prompt flag, GitHub reporter rate limits |

---

## Common Pitfalls for Future Sessions

1. **Never call `initializeKokoro()` directly** — always go through `warmUp()` which handles crash-loop detection. AudioPipeline's `ensureKokoroReady()` has a guard for this.

2. **Both Kokoro AND Piper use the same native library.** If native code is broken, BOTH will crash. The crash recovery must guard Piper calls with `init_in_progress` too. Android system TTS is the only fallback that doesn't use native sherpa-onnx code.

3. **`apply()` vs `commit()`** — use `commit()` for `init_in_progress` and `KEY_INIT_FORMAT` flags (must be flushed to disk before native code runs). Use `apply()` for everything else.

4. **Xiaomi/MIUI/HyperOS is hostile.** It kills background processes aggressively, throttles CPU, has modified ART runtime. HyperOS 2.0 dropped the MIUI system property — use `isXiaomiRom` (checks both `ro.miui.ui.version.name` and `ro.mi.os.version.incremental`). Battery exemption + OEM AutoStart intents help but aren't guaranteed. The TTS process watchdog in `ReaderApplication` auto-restarts after OS kills.

5. **The `tts_status.json` file is the only IPC channel.** If the :tts process crashes, the file retains whatever was last written. The UI polls it every 1s and shows stale data until the process restarts. The watchdog detects stale timestamps and calls `requestRebind()`.

6. **Model extraction is expensive.** First run extracts 311MB from APK to filesystem. Uses atomic tmp+rename to prevent corruption from power loss. Version marker (`.extracted_version`) forces re-extraction on app update.

7. **Config memory is invalidated on crash AND on app update.** Don't assume it's always valid.

8. **Never delete `.onnx` originals after ORT conversion.** The `.ort` format is version-specific — if the build-time ORT version doesn't match the on-device ORT, `.ort` files cause SIGSEGV. The engine falls back to `.onnx` at runtime if `.ort` fails. Both formats ship in the APK and are extracted to filesystem. The format fallback survives SIGSEGV via `KEY_INIT_FORMAT` / `KEY_SKIP_ORT` in SharedPreferences.

9. **Not all process deaths are native crashes.** HyperOS/MIUI kill the `:tts` process for battery management. Use `ApplicationExitInfo` (API 30+) to classify exit reasons. Only real crashes (`REASON_CRASH_NATIVE`, `REASON_CRASH`, `REASON_ANR`) should increment the crash counter.

10. **All OEMs kill background processes, not just Xiaomi.** `OemProtection.kt` handles AutoStart intents for Samsung, Huawei, OnePlus, Oppo, Vivo, and many more. `HomeFragment.promptOemProtections()` runs on all devices.

11. **`GitHubReporter` needs a token.** Set `github.issues.token=ghp_xxx` in `local.properties` for auto-reporting. Without it, the reporter is silently disabled. Remote config is fetched from `remote-config.json` in the repo root.

12. **DSP effects compound.** Each effect in the chain adds amplitude. The soft limiter at the end prevents clipping, but excessive parameters can still cause audible distortion. The modulation budget cap (±0.25 pitch, ±0.35 speed) prevents runaway compounding from VoiceModulator. AudioDsp uses quadratic intensity curves (`smoothCurve`) so low slider values produce subtle effects.

13. **Pitch shifting is separate from speed.** Pitch uses resampling + OLA time-stretch in `AudioDsp.pitchShift()`. Speed is set via the TTS engine's `speed` parameter during synthesis. They are independent — changing one does not affect the other.

14. **NaN guards are critical.** Float arithmetic in VoiceModulator can produce NaN from division-by-zero or infinite signal chains. All float outputs are guarded with `Float.guardNaN(default)` before being passed to DSP. Without this, a single NaN corrupts the entire PCM buffer.
