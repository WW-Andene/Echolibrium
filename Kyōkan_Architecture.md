# Kyōkan Architecture Guide

> Quick-reference for any Claude session working on this codebase.
> For the full 1000-line deep-dive, see `ARCHITECTURE.md`.

---

## What Is This Project?

**Kyōkan** (共感, "empathy") / **Echolibrium** / **Kokoro Reader** is an Android app that reads notifications aloud using fully offline TTS. It doesn't just read — it *reacts* to what it reads, modulating voice pitch, speed, breathiness, and stutter based on the emotional content of each notification.

- **Package:** `com.kokoro.reader`
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
│  ReaderApplication (UI only)    │    │  VoiceCommandListener                  │
│                                 │    │  AudioDsp                              │
│  Reads tts_status.json ◄────────┼────┼── Writes tts_status.json               │
│  Sends broadcasts ──────────────┼────┼──► TtsCommandReceiver receives          │
└─────────────────────────────────┘    └──────────────────────────────────────┘
```

**Why two processes?** The sherpa-onnx native library (ONNX Runtime, ~50MB .so) conflicts with Android's HWUI RenderThread in the same process. Loading native TTS models in a process with a GPU-accelerated UI causes SIGSEGV on Xiaomi/MediaTek devices. The `:tts` process has no UI, no HWUI, no RenderThread — so native code runs safely.

**Cross-process IPC:**
- **Commands (UI → TTS):** Explicit broadcasts via `TtsBridge` → `TtsCommandReceiver`
- **Status (TTS → UI):** JSON file (`tts_status.json`) written atomically by TTS process, polled by UI every 1s

---

## Source File Map

All source is in `app/src/main/java/com/kokoro/reader/`.

### Core Engine (runs in :tts process)

| File | Role |
|---|---|
| `SherpaEngine.kt` | **Heart of the app.** Manages Kokoro (311MB) and Piper (~60MB) model loading, crash recovery, device profiling, config memory, synthesis. ~1600 lines. |
| `AudioPipeline.kt` | Single-threaded FIFO queue. Receives notification items, calls SherpaEngine for synthesis, applies DSP, plays via AudioTrack. Chunked synthesis for long texts. |
| `AudioDsp.kt` | PCM audio effects (gain, speed adjustment) applied after synthesis. |
| `NotificationReaderService.kt` | `NotificationListenerService` — captures notifications, filters (DnD, per-app rules, dedup), extracts signals, modulates voice, enqueues to AudioPipeline. Conversation grouping (1.5s buffer). |
| `TtsCommandReceiver.kt` | `BroadcastReceiver` — receives commands from UI process and dispatches to SherpaEngine/AudioPipeline/VoiceCommandListener. |

### Voice & Personality System

| File | Role |
|---|---|
| `SignalExtractor.kt` | Pattern-matching engine — detects urgency, emotion, stakes from notification text. No LLM. |
| `SignalMap.kt` | Data class holding extracted signal strengths (urgency, distress, emotional, fake, raw). |
| `VoiceModulator.kt` | Maps signal strengths → voice parameter adjustments (pitch, speed, breathiness, stutter, intonation). |
| `VoiceTransform.kt` | Text preprocessing pipeline: wording rules → commentary → gimmicks → intonation → stutter → breathiness. |
| `VoiceProfile.kt` | User-configurable voice personality (base pitch, speed, effects intensities). |
| `CommentaryPool.kt` | Bank of pre/post commentary lines injected based on notification signals. |
| `MoodState.kt` | Persistent emotional state that evolves based on notification stream. |
| `KokoroVoice.kt` | Kokoro voice ID mapping (speaker IDs for the multi-speaker model). |
| `PhonicAnalyzer.kt` | Analyzes phonetic properties of text for stutter/breathiness placement. |

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
| `HomeFragment.kt` | Permission setup, service status, engine status display with polling. |
| `ProfilesFragment.kt` | Voice profile editor — sliders for pitch, speed, effects. Test playback. |
| `AppsFragment.kt` | Per-app notification rules list. |
| `RulesFragment.kt` | Rule editor for individual apps. |
| `CrashReportActivity.kt` | Displays crash details when the app recovers from a crash. |

### Infrastructure

| File | Role |
|---|---|
| `ReaderApplication.kt` | `Application` subclass — installs global exception handler, crash recovery (main process only). |
| `TtsBridge.kt` | Cross-process bridge — command broadcasts, status file I/O, battery optimization helpers, staleness detection. |
| `AppRule.kt` | Data class for per-app notification rules (block, priority, custom voice). |
| `BootReceiver.kt` | Starts the service on device boot. |

---

## SherpaEngine: Initialization Flow

This is the most complex and most-fixed part of the codebase. Understanding it is critical.

### Model Types

| Engine | Model Size | Quality | Reliability |
|---|---|---|---|
| **Kokoro** | 311MB (ONNX/ORT) | High — multi-speaker, natural | Crashes on some devices |
| **Piper** | ~60MB (VITS) | Good — single speaker | Very reliable fallback |

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
       │    ├─ Profile: RAM, cores, SoC vendor, Xiaomi/MIUI detection
       │    ├─ Device tier: HIGH / MEDIUM / LOW
       │    ├─ GC (2-pass + finalization + 50ms sleep)
       │    ├─ RAM check (needs 350MB free)
       │    ├─ Heap reservation (alloc 25% max heap, touch pages, release)
       │    └─ Model pre-warming (sequential 256KB reads into page cache)
       │
       ├─ Stage 2: Config memory + auto-escalation
       │    ├─ Check SharedPreferences for last known-good config
       │    ├─ Escalation ladder: optimal → safe (1 thread, cpu) → diagnostic
       │    ├─ Run OfflineTts() on dedicated thread with URGENT_AUDIO priority
       │    ├─ Adaptive timeout (30s ORT / 90s ONNX × device tier multiplier)
       │    └─ On success: save config to memory, reset crash counter
       │
       └─ finally: clear init_in_progress flag
```

### Crash Recovery System

The native library (sherpa-onnx) can SIGSEGV — kill the process instantly. Android auto-restarts the NotificationListenerService, which triggers another init attempt. Without protection, this is an infinite crash loop.

**Detection:** `init_in_progress` flag in SharedPreferences. Set `true` (with `commit()`) before ANY native code. Cleared in `finally`. Only a SIGSEGV leaves it set.

**Recovery behavior by crash count:**

| Crash Count | Behavior |
|---|---|
| **0** | Normal Kokoro init |
| **1–4** | Set `init_in_progress=true` → try Piper fallback (tracked!) → if Piper works, user has TTS → exponential backoff retry Kokoro in background (5s, 10s, 20s, 40s, 80s) |
| **5+** (`NATIVE_BROKEN_THRESHOLD`) | Skip ALL native code → show error "native engine incompatible" → retry after 2.5 min → user can force retry from UI |

**Critical detail:** Piper uses the same native library as Kokoro. If `init_in_progress` isn't set before `initPiperFallback()`, a Piper SIGSEGV won't increment the crash counter → infinite loop at crashCount=1. This was a real bug (fixed March 2026).

**Config memory:** When Kokoro succeeds, the exact config (threads, provider, debug flag) is saved to SharedPreferences. Next launch skips the escalation ladder and uses the known-good config directly. Cleared on crash or app update.

**`forceRetry(ctx)`:** Resets crash counter to 0, clears all error state, re-attempts from scratch.

---

## Build System

```
build.gradle
├─ downloadTtsModels task (runs download-models.sh before build)
├─ aaptOptions: noCompress onnx, ort, bin, txt, dict, tab
├─ packagingOptions: useLegacyPackaging = true (extract .so from APK)
└─ ndk: arm64-v8a only

Dependencies:
├─ sherpa_onnx.aar (local, auto-downloaded)
├─ kotlin-stdlib 1.9.22
├─ androidx core-ktx, appcompat, constraintlayout, preference-ktx, fragment-ktx
```

**Native libraries** (inside sherpa_onnx.aar):
- `libonnxruntime.so` — ONNX Runtime (~50MB)
- `libsherpa-onnx-c-api.so` — sherpa-onnx C API
- `libsherpa-onnx-cxx-api.so` — sherpa-onnx C++ API
- `libsherpa-onnx-jni.so` — JNI bridge

**Why `useLegacyPackaging = true`?** On API 23+, Android loads .so directly from APK via mmap. On MediaTek/Xiaomi, mmapping the ~50MB ONNX Runtime .so from the APK triggers SIGSEGV during dlopen. Extracting to filesystem first avoids this.

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
5. SignalExtractor.extract(text) → SignalMap (urgency, emotion, stakes)
        ↓
6. VoiceModulator.modulate(profile, signals) → ModulatedVoice (pitch, speed, etc.)
        ↓
7. AudioPipeline.enqueue(Item(text, modulatedVoice))
        ↓
8. Pipeline thread dequeues, applies VoiceTransform (commentary, gimmicks, stutter)
        ↓
9. Text chunking: split at sentence boundaries for streaming synthesis
        ↓
10. SherpaEngine.synthesizeWithFallback(chunk) → FloatArray PCM
        ↓
11. AudioDsp.process(samples) → gain, speed adjustment
        ↓
12. AudioTrack.write(samples) → speaker output
```

---

## Key SharedPreferences

| Prefs Name | Process | Purpose |
|---|---|---|
| `sherpa_init_tracker` | :tts | Crash counter, init_in_progress flag, last crash time |
| `sherpa_config_memory` | :tts | Last known-good engine config (threads, provider, debug, version) |
| Default prefs | main | Voice profiles, per-app rules, UI settings |

---

## Common Pitfalls for Future Sessions

1. **Never call `initializeKokoro()` directly** — always go through `warmUp()` which handles crash-loop detection. AudioPipeline's `ensureKokoroReady()` has a guard for this.

2. **Both Kokoro AND Piper use the same native library.** If native code is broken, BOTH will crash. The crash recovery must guard Piper calls with `init_in_progress` too.

3. **`apply()` vs `commit()`** — use `commit()` for `init_in_progress` flag (must be flushed to disk before native code runs). Use `apply()` for everything else.

4. **Xiaomi/MIUI is hostile.** It kills background processes aggressively, throttles CPU, has modified ART runtime with different mmap behavior. The battery optimization exemption (`TtsBridge.requestBatteryExemption()`) helps but isn't guaranteed.

5. **The `tts_status.json` file is the only IPC channel.** If the :tts process crashes, the file retains whatever was last written. The UI polls it every 1s and shows stale data until the process restarts.

6. **Model extraction is expensive.** First run extracts 311MB from APK to filesystem. Uses atomic tmp+rename to prevent corruption from power loss. Version marker (`.extracted_version`) forces re-extraction on app update.

7. **Config memory is invalidated on crash AND on app update.** Don't assume it's always valid.
