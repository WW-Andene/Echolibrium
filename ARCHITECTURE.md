# Kokoro Reader — Architecture & Systems Documentation

> Kokoro Reader (Echolibrium) is an Android notification reader that speaks
> incoming notifications aloud using fully offline text-to-speech.  It wraps
> every notification through a personality system, so the voice doesn't just read
> — it **reacts** to what it reads.

---

## Table of Contents

1. [High-Level Overview](#1-high-level-overview)
2. [Application Lifecycle](#2-application-lifecycle)
3. [Notification Capture System](#3-notification-capture-system)
4. [Signal Extraction Engine](#4-signal-extraction-engine)
5. [Voice Profile System](#5-voice-profile-system)
6. [Voice Modulation System](#6-voice-modulation-system)
7. [Text Transform Pipeline](#7-text-transform-pipeline)
8. [TTS Engine Layer (SherpaEngine)](#8-tts-engine-layer-sherpaengine)
9. [Audio DSP Layer](#9-audio-dsp-layer)
10. [Audio Pipeline](#10-audio-pipeline)
11. [Voice Catalog & Management](#11-voice-catalog--management)
12. [Voice Command System](#12-voice-command-system)
13. [Per-App Rules](#13-per-app-rules)
14. [UI Architecture](#14-ui-architecture)
15. [Build & CI System](#15-build--ci-system)
16. [Thread Model & Concurrency](#16-thread-model--concurrency)
17. [Data Flow End-to-End](#17-data-flow-end-to-end)
18. [Error Handling Strategy](#18-error-handling-strategy)

---

## 1. High-Level Overview

```
┌───────────────────────── Android OS ──────────────────────────────┐
│                                                                   │
│  Notification   ──►  NotificationReaderService                    │
│   arrives             │                                           │
│                       ├─ filters (DnD, per-app rules, dedup)      │
│                       ├─ SignalExtractor  → SignalMap              │
│                       ├─ VoiceModulator   → ModulatedVoice        │
│                       └─ enqueue(AudioPipeline.Item)              │
│                              │                                    │
│                      ┌───────▼────────┐                           │
│                      │ AudioPipeline   │  (single background      │
│                      │   loop thread)  │   thread, FIFO queue)    │
│                      └───────┬────────┘                           │
│                              │                                    │
│              ┌───────────────┼───────────────┐                    │
│              ▼               ▼               ▼                    │
│       VoiceTransform   SherpaEngine     AudioDsp                  │
│       (text effects)   (TTS synthesis)  (PCM effects)             │
│              │               │               │                    │
│              └───────────────┼───────────────┘                    │
│                              ▼                                    │
│                         AudioTrack                                │
│                        (speaker out)                              │
└───────────────────────────────────────────────────────────────────┘
```

**Key principles:**

| Principle | Implementation |
|---|---|
| Fully offline | Kokoro & Piper models are bundled in the APK; no network needed at runtime |
| Single pipeline thread | Notifications are spoken strictly in FIFO order on one thread |
| Reactive personality | The voice adapts pitch, speed, stutter, breathiness based on notification *content* |
| No LLM | Signal extraction is pure pattern-matching; honest about its limits |

---

## 2. Application Lifecycle

### ReaderApplication

`ReaderApplication` extends `Application` and is declared in the manifest as `android:name=".ReaderApplication"`.  Its only job is to install a **global uncaught exception handler** so that crashes from background threads (especially native sherpa-onnx JNI errors) are always logged before the default handler shows the crash dialog.

```
Application.onCreate()
  └─► Thread.setDefaultUncaughtExceptionHandler
        ├─ Log.e(TAG, throwable)
        └─ delegate to system default handler
```

### Service Startup Flow

1. **User grants notification access** → Android binds `NotificationReaderService`.
2. `onCreate()`:
   - Sets the static `instance` reference for UI access.
   - Calls `AudioPipeline.start(this)` — starts the background synthesis loop.
   - Calls `SherpaEngine.warmUp(this)` — extracts models and initializes the TTS engine on a daemon thread.
   - Calls `startForegroundNotification()` — promotes to foreground so Android doesn't kill the service.
3. `onListenerConnected()` — records `listener_connected = true` in SharedPreferences; the UI checks this.
4. `onListenerDisconnected()` — records `listener_connected = false` and calls `requestRebind()` to attempt reconnection.

### Boot Receiver

`BootReceiver` listens for `ACTION_BOOT_COMPLETED`.  If notification access is still granted (checked by reading `Settings.Secure.enabled_notification_listeners`), it calls `NotificationListenerService.requestRebind()` so the service restarts automatically after a reboot without requiring the user to re-open the app.

---

## 3. Notification Capture System

**File:** `NotificationReaderService.kt`

### Entry Point

`onNotificationPosted(sbn: StatusBarNotification)` is called by the Android framework every time a new notification appears or an existing one is updated.

### Filtering Pipeline

The service applies these filters **in order** before any notification is spoken:

| Step | Check | Result if failed |
|---|---|---|
| 1 | `service_enabled` pref is `true` | silent return |
| 2 | Notification is not from our own package | silent return |
| 3 | DnD quiet hours are not active | silent return |
| 4 | Per-app rule doesn't say `skip` or `enabled = false` | silent return |
| 5 | Title and text are not both blank | silent return |
| 6 | Content deduplication (see below) | silent return |

### Deduplication

A `LinkedHashMap<String, String>` (access-ordered, LRU eviction at 100 entries) tracks the last notification content per `packageName:notificationId`.  If an app updates an existing notification with the **same** title and text (e.g., WhatsApp appending to the same notification ID), the duplicate is silently skipped.

```kotlin
val contentKey = "${sbn.packageName}:${sbn.id}"
val contentValue = "$title|$text"
synchronized(dedupLock) {
    if (lastNotificationContent[contentKey] == contentValue) return
    lastNotificationContent[contentKey] = contentValue
    // evict LRU entries above 100
}
```

### Daily Flood Counter

A thread-safe counter (`dailyCount` protected by `countLock` and `@Volatile`) tracks how many notifications have been received today.  It resets automatically when the day-of-year changes.  The flood count feeds into `SignalExtractor` and influences modulation — many notifications in a day signal "flooding".

### Message Building

The `buildMessage()` method assembles the text that will be spoken, depending on the user's `read_mode` preference:

| Mode | Output |
|---|---|
| `full` | "AppName. Title. Text" |
| `title_only` | "AppName. Title" |
| `app_only` | "AppName" |
| `text_only` | "Text" |

### Do Not Disturb

`isDndActive()` checks a start/end hour pair stored in SharedPreferences.  Supports wrap-around (e.g., 22:00–08:00).

### Foreground Notification

A persistent, low-importance notification ("Listening for notifications") keeps the service alive.  It uses `FOREGROUND_SERVICE_SPECIAL_USE` type with a description explaining the use case.

---

## 4. Signal Extraction Engine

**File:** `SignalExtractor.kt` and `SignalMap.kt`

The signal extractor reads raw notification data and produces a `SignalMap` — a structured representation of the notification's **meaning** and **emotional context**.  It uses **no AI/LLM** — only pattern matching against curated word/emoji/package lists.

### Signal Dimensions

```kotlin
data class SignalMap(
    // Who sent it
    sourceType:     SourceType,    // GAME | PERSONAL | SERVICE | PLATFORM | FINANCIAL | SYSTEM
    senderType:     SenderType,    // HUMAN | BOT | SYSTEM | UNKNOWN

    // What they want
    intents:        Set<Intent>,   // INFORM | REQUEST | ALERT | INVITE | ACTION_REQUIRED | DENIAL | PLEA | GREETING

    // What's at stake
    stakesLevel:    StakesLevel,   // NONE | LOW | MEDIUM | HIGH
    stakesType:     StakesType,    // NONE | FAKE | FINANCIAL | EMOTIONAL | TECHNICAL | PHYSICAL

    // How urgent
    urgencyType:    UrgencyType,   // NONE | SOFT | REAL | EXPIRING | BLOCKING

    // Emotional tone
    warmth:         WarmthLevel,   // NONE | LOW | MEDIUM | HIGH | DISTRESSED
    register:       Register,      // MINIMAL | CASUAL | FORMAL | DRAMATIC | RAW | TECHNICAL

    // Emoji sentiment
    emojiHappy, emojiSad, emojiAngry, emojiLove, emojiShock: Boolean,

    // Message structure
    fragmented:     Boolean,       // multi-line / ellipsis
    capsRatio:      Float,         // 0.0–1.0 shouting
    unknownFactor:  Boolean,       // unknown sender
    actionNeeded:   Boolean,

    // Intensity arc
    intensityLevel: Float,         // 0.0–1.0
    trajectory:     Trajectory,    // FLAT | BUILDING | PEAKED | COLLAPSED

    // Context
    hourOfDay:      Int,
    floodCount:     Int
)
```

### Extraction Logic

**Source type** — determined by matching the package name and app name against curated lists:
- Game packages: `game`, `clash`, `supercell`, `roblox`, `minecraft`, etc.
- Financial: `bank`, `paypal`, `stripe`, `revolut`, etc.
- Service: `amazon`, `fedex`, `ups`, `dhl`, etc.
- Platform: `twitch`, `youtube`, `spotify`, `discord`, etc.
- System: `android`, `system`, `settings`
- Default: `PERSONAL`

**Sender type** — if source is PERSONAL and the title looks like a name (2–30 chars, no `@` or `http`), it's classified as HUMAN; all other sources default to BOT.

**Intent detection** — scans the text body for keyword lists:
- Request words: "could you", "can you", "please", "help me", etc.
- Apology words: "sorry", "apologize", "my bad", etc.
- Denial: "I swear", "I didn't", "believe me", etc.
- Plea: "please come back", "don't leave", "miss you", etc.
- Greeting: "hello", "hi", "good morning", etc.
- Urgent: "urgent", "asap", "emergency", "sos", etc.
- Financial: "payment", "transaction", "$", "€", etc.

**Emoji detection** — checks the full text for emoji from categorized sets (happy, sad, angry, love, shock).

**Intensity scoring** — each line is scored using a weighted word map:
```
"fuck" → 0.4, "emergency" → 0.4, "urgent" → 0.35
"!!!" → 0.3, "!!" → 0.2, "???" → 0.25
Plus caps ratio * 0.3 and punctuation counts * 0.1
```

**Trajectory** — compares per-line intensity scores to determine the message's emotional arc:
- All rising → `BUILDING`
- Ends at max → `PEAKED`
- Peak then drop > 50% → `COLLAPSED`
- Otherwise → `FLAT`

---

## 5. Voice Profile System

**File:** `VoiceProfile.kt`

A voice profile is a complete set of parameters that define how the voice sounds and behaves.  Profiles are user-configurable and stored as JSON in SharedPreferences.

### Profile Parameters

```kotlin
data class VoiceProfile(
    id:    String,     // UUID
    name:  String,     // Display name (also used as wake word for voice commands)
    emoji: String,     // Profile icon

    // Base TTS
    voiceName:  String,   // Kokoro voice ID (e.g. "af_heart") or Piper ID
    voiceAlias: String,   // Per-profile nickname
    pitch:      Float,    // 0.5–2.0 (1.0 = natural)
    speed:      Float,    // 0.5–3.0 (1.0 = natural)

    // Breathiness (whispery quality)
    breathIntensity:     Int,    // 0–100
    breathCurvePosition: Float,  // 0.0–1.0 (where the "h" sounds appear in a word)
    breathPause:         Int,    // 0–100

    // Stuttering
    stutterIntensity: Int,    // 0–100
    stutterPosition:  Float,  // 0.0–1.0 (where in the word the stutter occurs)
    stutterFrequency: Int,    // 0–100 (% of words affected)
    stutterPause:     Int,    // 0–100 (dash length between stutters)

    // Intonation (emphasis/drama)
    intonationIntensity: Int,    // 0–100
    intonationVariation: Float,  // 0.0–1.0

    // Gimmicks (reactive sound effects)
    gimmicks: List<GimmickConfig>,

    // Commentary pools (contextual lines before/after the notification)
    commentaryPools: List<CommentaryPool>
)
```

### Personality Presets

The app ships with **15 personality presets**, each a carefully tuned `VoiceProfile`:

| Preset | Emoji | Key Characteristics |
|---|---|---|
| Natural | 😐 | Flat, neutral baseline |
| Excited | 🎉 | High pitch (1.45), fast speed (1.4), high intonation, "woah" + "laugh" gimmicks |
| Bored | 😒 | Low pitch (0.85), slow speed (0.75), flat intonation, "yawn" + "hmm" gimmicks |
| Depressed | 😔 | Very low pitch (0.72), very slow (0.65), breathy, frequent "sigh" |
| Flirty | 😏 | Slightly high pitch (1.25), slow (0.88), breathy, "giggle" + "sigh" + "mmm" |
| Gentle | 🌸 | Slightly high pitch (1.12), slow (0.82), moderate breathiness |
| Happy | 😄 | High pitch (1.35), fast (1.15), high intonation, "laugh" + "woah" + "aww" |
| Hangry | 😤 | Neutral pitch (1.05), fast (1.25), "ugh" + "tsk" + "huh" |
| Nervous | 😰 | Slightly high pitch (1.2), notable stutter (45/40), "huh" + "hmm" |
| Whispery | 🤫 | Slightly high pitch (1.1), slow (0.72), high breathiness (65) |
| Robot | 🤖 | Very low pitch (0.5), slow (0.78), zero intonation |
| Drunk | 🥴 | Low pitch (0.88), slow (0.82), moderate stutter (35/45), high intonation variation (0.95) |
| Elder | 🧓 | Low pitch (0.78), slow (0.7), moderate breathiness, "hmm" + "yawn" |
| Child | 🧒 | Very high pitch (1.85), fast (1.2), high intonation, "woah" + "giggle" |
| Dramatic | 🎭 | Normal pitch, slow (0.82), very high intonation (90, 0.95), "gasp" + "sigh" |
| Sarcastic | 🙄 | Slightly high pitch (1.15), moderate speed (0.9), high intonation, "hmm" + "tsk" + "huh" |

Each preset includes **context-aware commentary pools** — lines the voice says before or after a notification, gated by conditions like `time_night`, `flooded`, `intent_request`, etc.

### Gimmick System

Gimmicks are reactive sound effects that fire probabilistically based on signal conditions:

```kotlin
data class GimmickConfig(
    type:      String,  // giggle | sigh | huh | mmm | woah | ugh | aww | gasp | yawn | hmm | laugh | tsk
    frequency: Int,     // 0–100 (probability cap)
    position:  String   // START | MID | END | RANDOM
)
```

Each gimmick type maps to multiple text variants:
- **giggle** → "heh heh", "ha ha ha", "heh heh heh"
- **sigh** → "haah...", "haaah...", "haahm..."
- **gasp** → "*gasp*", "oh!", "oh-"
- **yawn** → "haaah...", "aaah...", "haahm."
- etc.

### Commentary System

Commentary pools are sets of lines spoken before (`pre`) or after (`post`) the notification text, gated by a `CommentaryCondition`:

```kotlin
data class CommentaryPool(
    position:  String,              // "pre" or "post"
    condition: CommentaryCondition, // what triggers this pool
    lines:     List<String>,        // possible lines to speak
    frequency: Int                  // 0–100 probability
)
```

`CommentaryCondition` supports **38 condition types** including:
- Source checks: `source_game`, `source_personal`, `source_financial`, `source_service`, `source_platform`, `source_system`
- Sender checks: `sender_human`, `sender_unknown`
- Intent checks: `intent_request`, `intent_plea`, `intent_denial`, `intent_alert`, `intent_greeting`, `intent_invite`, `intent_action`
- Stakes: `stakes_fake`, `stakes_financial`, `stakes_emotional`, `stakes_high`, `stakes_low`
- Urgency: `urgency_none`, `urgency_real`, `urgency_expiring`
- Warmth: `warmth_high`, `warmth_distressed`
- Emoji: `emoji_sad`, `emoji_happy`, `emoji_angry`, `emoji_love`
- Intensity: `intensity_low`, `intensity_high`
- Trajectory: `traj_building`, `traj_peaked`, `traj_collapsed`
- Time: `time_night`, `time_morning`
- Flood: `flooded`
- Always: `always`

---

## 6. Voice Modulation System

**File:** `VoiceModulator.kt`

The modulator takes a **voice profile** (baseline personality) and a **signal map** (notification context) and produces a `ModulatedVoice` — the actual parameters sent to the TTS and audio pipeline.

### Modulation Philosophy

Two rules govern how baseline and signal combine:

1. **Addition** — signal has intensity, profile baseline doesn't →
   `result = baseline + (signal × sensitivity)`

2. **Multiplication** — both signal and profile have the quality →
   `result = baseline × (1 + signal × sensitivity)`
   (two things resonating = exponential, not linear)

### Trajectory Scaling

The message trajectory acts as a global multiplier on all signal-driven adjustments:

| Trajectory | Multiplier | Meaning |
|---|---|---|
| FLAT | 1.0× | Neutral, no arc |
| BUILDING | 1.3× | Ramping up |
| PEAKED | 1.6× | Full force |
| COLLAPSED | 0.7× | Exhausted, broke — less energy |

### Modulated Parameters

**Pitch:**
```
pitchDelta = lerp(0, 0.25, (urgencyStrength × 0.5 + distressStrength × 0.5) × intensity × trajectory)
pitch = (profile.pitch + pitchDelta - fakeStrength × 0.05).coerceIn(0.5, 2.0)
```
- Urgent/distressed → pitch goes up
- Fake stakes (games) → pitch goes slightly down

**Speed:**
```
speedUp   = urgencyStrength × 0.35 × intensity × trajectory
speedDown = 0.18 if trajectory == COLLAPSED else 0
speed = (profile.speed + speedUp - speedDown - fakeStrength × 0.12).coerceIn(0.5, 3.0)
```
- Urgent → faster speech
- Collapsed → slower speech
- Games → slightly slower

**Breathiness:**
```
msgBreath = lerp(0, 0.8, distressStrength × intensity × trajectory) + lerp(0, 0.3, if emojiSad: intensity else 0)
breathIntensity = blendAdd(profile.breathIntensity, ..., msgBreath × 35, trajectory)
```
- Distressed or sad emoji → more breathiness

**Stutter:**
```
msgStutter = urgencyStrength × intensity × trajectory × 0.9
           + distressStrength × intensity × trajectory × 0.6
           + RAW register × intensity × 0.3
stutterIntensity = blendAdd(profile.stutterIntensity, ..., msgStutter × 30, trajectory)
```
- Urgent + distressed → more stutter

**Intonation:**
```
msgInton = (emotionalStrength × 0.7 + intensity × 0.3) × trajectory × 0.8
         - fakeStrength × 0.4   // flatten fake stakes
```
- Emotional stakes → more dramatic intonation
- Game notifications → flattened

---

## 7. Text Transform Pipeline

**File:** `VoiceTransform.kt`

Before text reaches the TTS engine, it passes through a series of text transformations.  These are applied in the `process()` method in this order:

### Step 1: Wording Rules
User-defined find-and-replace rules.  Case-insensitive.  Common defaults:
- "WhatsApp" → "Message"
- "lol" → "laugh out loud"
- "https://" → "link"
- "brb" → "be right back"

### Step 2: Commentary
Inserts `pre` commentary lines before and `post` commentary lines after the notification text, based on the signal context and probability rolls.

### Step 3: Gimmicks
Inserts sound-effect text (giggle, sigh, gasp, etc.) at the configured position.  Each gimmick:
1. Checks its signal condition against the SignalMap
2. Rolls against its frequency cap
3. Picks a random variant from the sound bank
4. Inserts at START, MID, END, or RANDOM position

### Step 4: Intonation
Applies vowel-stretching and ellipsis to stressed words at intervals:
```
"Hello world" → "Heeello world..." (with high intensity)
```
- Dead zone: intensity < 5 → no effect
- Quadratic intensity curve so low values are subtle

### Step 5: Stuttering
Repeats syllable fragments at random word positions:
```
"something" → "so-so-something" (stutter at position 0.0)
"terrible"  → "terr-terr-terrible" (stutter at position 0.4)
```
- Quadratic frequency curve: low values stutter very rarely
- Dead zone: intensity < 5 or frequency < 5

### Step 6: Breathiness
Inserts "h" sounds and pauses around/within words:
```
"hello" → "hh hello"      (curvePos < 0.2, prefix mode)
"hello" → "hhe llo"       (curvePos < 0.4, initial split)
"hello" → "hhheelloo"     (curvePos < 0.6, vowel stretch)
"hello" → "hel hh lo"     (curvePos < 0.8, mid-word)
"hello" → "hellohh"       (curvePos >= 0.8, suffix mode)
```
- Dead zone: intensity < 5
- Quadratic curve: low values produce minimal "h" sounds

### Smoothing Curve

All slider values (0–100) are mapped through a quadratic curve before use:
```
smooth(v) = (v / 100)²
```
This makes the first 30% of the slider range barely audible, the middle range natural, and the upper range increasingly dramatic.

---

## 8. TTS Engine Layer (SherpaEngine)

**File:** `SherpaEngine.kt`

A singleton wrapper around the sherpa-onnx `OfflineTts` native library that supports two synthesis backends:

### Kokoro Engine (Primary)

- **Model:** `kokoro-multi-lang-v1_0` (~120 MB ONNX model)
- **Speakers:** 30 voices in a single model, selected by speaker ID (0–29)
- **Languages:** English (US), English (UK), Spanish
- **Files:** `model.onnx`, `voices.bin`, `tokens.txt`, `espeak-ng-data/`
- **Sample rate:** 22050 Hz

```kotlin
val audio = engine.generate(text = text, sid = speakerId, speed = speed)
// Returns: FloatArray (PCM samples) + sampleRate
```

### Piper/VITS Engine (Secondary)

- **Model:** One `.onnx` file per voice (each ~15–60 MB)
- **Voices:** 44 voices across en_US (26), en_GB (11), fr_FR (7)
- **Shared files:** `tokens.txt`, `espeak-ng-data/` (reused from Kokoro model)
- **Caching:** Only one Piper model loaded at a time; cached for reuse if same voice

```kotlin
val audio = engine.generate(text = text, sid = 0, speed = speed)
// Piper voices always use sid=0 (single speaker per model)
```

### Warm-up Flow

```
SherpaEngine.warmUp(ctx)
  └─► Background thread ("SherpaEngine-warmup", daemon)
       ├─ VoiceDownloadManager.ensureModelSync()   // Extract Kokoro model from assets
       ├─ PiperVoiceManager.extractBundledVoicesSync()  // Extract bundled Piper voices
       └─ initializeKokoro()                        // Load ONNX model into memory
           ├─ OfflineTtsKokoroModelConfig(model, voices, tokens, dataDir)
           ├─ OfflineTtsModelConfig(kokoro, numThreads=2, provider="cpu")
           └─ OfflineTts(config)
```

### Fallback Chain

When synthesizing, the AudioPipeline routes to:
1. **Kokoro engine** if the voice ID matches a `KokoroVoice`
2. **Piper engine** if the voice ID matches a `PiperVoice` and the model is downloaded
3. **Default Kokoro** (af_heart, SID 3) as fallback if voice is unavailable

If a Piper voice crashes during synthesis (native library error), the pipeline catches the `Throwable` and falls back to the default Kokoro voice.

---

## 9. Audio DSP Layer

**File:** `AudioDsp.kt`

Applies three DSP effects to the raw PCM output from the TTS engine, in order:

### Effect 1: Soft Saturation

Uses `tanh` waveshaping with a drive of 1.2 to gently compress peaks:

```kotlin
tanh(sample * 1.2)
```

**Purpose:** Voices that speak non-native phonemes sometimes produce transient spikes that sound like breathing or clicking artifacts.  Soft-clipping rounds these off while keeping clean speech intact.

### Effect 2: Formant Smoothing

A single-pole IIR low-pass on the amplitude envelope, then re-applies the smoothed envelope to the original signal's phase:

1. Extract instantaneous amplitude envelope (5ms window → ~110 samples at 22050 Hz)
2. Low-pass filter the envelope with IIR coefficient `alpha = 1/windowSamples`
3. Blend: 70% original + 30% envelope-shaped
4. Ratio clamped to [0.5, 2.0] to avoid crushing peaks or amplifying silence

**Purpose:** Makes pitch transitions between syllables feel like curves rather than abrupt steps — particularly helpful for Piper/VITS voices that produce robotic transitions.

### Effect 3: Breathiness (Conditional)

Only applied when `ModulatedVoice.breathIntensity >= 5`:

1. Generate random noise
2. IIR low-pass filter at ~3 kHz (α = 0.55) to create "breathy air" quality
3. Compute 20ms RMS speech envelope
4. Shape noise by envelope: louder where speech is, quieter during silence
5. Mix: `output = speech + noise × (0.3 + envelope × 0.7)`
6. Maximum noise level: 18% (quadratic curve from intensity slider)

**Purpose:** Creates a whispery, breathy quality that's shaped by the speech signal — breath is louder during voiced segments and quiet during pauses, making it sound natural.

---

## 10. Audio Pipeline

**File:** `AudioPipeline.kt`

A singleton that manages a producer-consumer queue for TTS synthesis and playback.

### Architecture

```
Producers:                                     Consumer:
  NotificationReaderService.onNotificationPosted   ──►  ┌──────────────────┐
  AudioPipeline.testSpeak()                        ──►  │ LinkedBlockingQueue │
  VoiceCommandHandler.speak()                      ──►  │   (Item objects)   │
                                                        └────────┬─────────┘
                                                                 │
                                                     AudioPipeline-loop thread
                                                                 │
                                                    ┌────────────▼───────────┐
                                                    │ processItem()          │
                                                    │  1. VoiceTransform     │
                                                    │  2. SherpaEngine       │
                                                    │  3. AudioDsp           │
                                                    │  4. playPcm()          │
                                                    └────────────────────────┘
```

### Queue Item

```kotlin
data class Item(
    rawText:   String,              // Original notification text
    profile:   VoiceProfile,        // Active voice profile
    modulated: ModulatedVoice,      // Signal-adjusted parameters
    signal:    SignalMap,            // Extracted signal context
    rules:     List<Pair<String, String>>,  // Word replacement rules
    priority:  Boolean = false      // true = interrupt (phone calls)
)
```

### Priority Interruption

When `priority = true` (e.g., phone call notification), the pipeline:
1. Clears the queue
2. Stops current playback immediately
3. Enqueues the priority item

### Playback

PCM audio is played through `AudioTrack` (Android audio API):
- Mode: `MODE_STATIC` — writes all PCM data before starting playback
- Encoding: `ENCODING_PCM_FLOAT` (32-bit float)
- Channel: `CHANNEL_OUT_MONO`
- Usage: `USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`
- **Pitch shifting** is done by adjusting `playbackRate` — higher rate = higher pitch
  ```kotlin
  val shiftedRate = (sampleRate * pitch).toInt().coerceIn(4000, 192000)
  track.playbackRate = shiftedRate
  ```
- Playback completion is detected via `setNotificationMarkerPosition` + `CountDownLatch` (60s timeout)

---

## 11. Voice Catalog & Management

### Kokoro Voice Catalog

**File:** `KokoroVoice.kt`

30 voices bundled in the Kokoro multi-lang model:

| Prefix | Count | Language | Nationality |
|---|---|---|---|
| af_ (American Female) | 11 | English (US) | American |
| am_ (American Male) | 9 | English (US) | American |
| bf_ (British Female) | 4 | English (UK) | British |
| bm_ (British Male) | 4 | English (UK) | British |
| ef_ (Spanish Female) | 1 | Spanish | Spanish |
| em_ (Spanish Male) | 1 | Spanish | Spanish |

Each voice has: `id` (e.g. "af_heart"), `sid` (speaker index 0–29), `displayName`, `gender`, `language`, `nationality`.  The default voice is `af_heart` (SID 3).

### Piper Voice Catalog

**File:** `PiperVoiceCatalog.kt`

44 voices across three locales, available for download from HuggingFace:
- **en_US:** 26 voices (amy, arctic, bryce, danny, hfc_female, hfc_male, joe, john, kathleen, kristin, kusal, l2arctic, lessac, libritts, libritts_r, ljspeech, norman, reza_ibrahim, ryan, sam — in low/medium/high quality variants)
- **en_GB:** 11 voices (alan, alba, aru, cori, jenny_dioco, northern_english_male, semaine, southern_english_female, vctk)
- **fr_FR:** 7 voices (gilles, mls, mls_1840, siwis, tom, upmc)

### Voice Classification

**File:** `VoiceProfile.kt` (VoiceInfo class)

`VoiceInfo.from(voiceName)` classifies any voice name into gender, language, and nationality:
- **Kokoro format:** `af_heart` → prefix `af` → American Female
- **Piper format:** `en_US-lessac-medium` → parse locale → English (US), then check name against known male/female name lists

### Bundled Voices

Three Piper voices are bundled in the APK assets (no download needed):
1. `en_US-lessac-medium` (Female, American)
2. `en_US-ryan-medium` (Male, American)
3. `fr_FR-siwis-medium` (Female, French)

### Kokoro Model Management

**File:** `VoiceDownloadManager.kt`

Manages extraction of the Kokoro model from APK assets to internal storage:
- **Asset path:** `assets/kokoro-model/`
- **Storage path:** `filesDir/sherpa/kokoro-multi-lang-v1_0/`
- **Required files:** `model.onnx`, `voices.bin`, `tokens.txt`, `espeak-ng-data/`
- Extraction is recursive (`copyAssetsRecursive`) and idempotent — skips if already extracted
- Thread-safe via `@Synchronized` on `ensureModelSync()`

### Piper Voice Management

**File:** `PiperVoiceManager.kt`

Manages both bundled and downloaded Piper voice models:

- **Storage path:** `filesDir/piper/`
- **Bundled voices** are extracted from `assets/piper-models/` on first launch
- **Downloaded voices** are fetched from HuggingFace URLs
- **Atomic downloads:** Uses temp-file-then-rename pattern (`.tmp` suffix) to prevent corrupted partial downloads from appearing ready
- **Redirect handling:** Manually follows HTTP redirects (up to 5 hops) for HuggingFace/GitHub URLs
- **Shared resources:** `tokens.txt` is shared across all Piper voices; `espeak-ng-data/` is reused from the Kokoro model directory

---

## 12. Voice Command System

**Files:** `VoiceCommandListener.kt`, `VoiceCommandHandler.kt`

### Overview

An optional feature that uses Android's `SpeechRecognizer` to listen for voice commands.  Requires `RECORD_AUDIO` permission.

### Wake Word

To prevent the constant beep sound from SpeechRecognizer restarts, the listener requires a **wake word** to be spoken before processing any command.  The wake word is the **active profile name** (e.g., if the active profile is "Sarcastic", the user says "Sarcastic, repeat that").

```kotlin
val heardWakeWord = matches.any { it.lowercase().contains(wakeWord) }
if (!heardWakeWord) return  // ignore, restart listening
```

### Supported Commands

| Command | Triggers | Response |
|---|---|---|
| Repeat | "can you repeat", "repeat that", "say that again", "what did you say" | Speaks the last notification text |
| Time ago | "how long ago", "when was that" | Speaks elapsed time since last notification |
| Stop | "stop", "shut up", "be quiet", "silence" | Stops current speech |
| Current time | "what time is it", "what's the time" | Speaks the current time |

### Listener Lifecycle

1. `start(ctx)` — creates `SpeechRecognizer`, sets up `RecognitionListener`, begins listening
2. Speech recognized → check for wake word → match command → handle
3. On error or end of speech → schedule restart after 800ms delay
4. `NO_MATCH` and `SPEECH_TIMEOUT` are normal and restart silently
5. Real errors (AUDIO, CLIENT, PERMISSIONS, etc.) restart after 3 seconds
6. `stop()` — cancels, destroys recognizer

### Configuration

```
RecognizerIntent:
  LANGUAGE_MODEL_FREE_FORM
  PARTIAL_RESULTS = false
  MAX_RESULTS = 3
  COMPLETE_SILENCE_LENGTH = 3000ms
  POSSIBLY_COMPLETE_SILENCE = 2000ms
  MINIMUM_LENGTH = 2000ms
```

Longer silence windows reduce how often the recognizer restarts, which reduces the beep frequency.

---

## 13. Per-App Rules

**File:** `AppRule.kt`

Users can configure per-app behavior:

```kotlin
data class AppRule(
    packageName: String,  // e.g. "com.whatsapp"
    appLabel:    String,  // e.g. "WhatsApp"
    enabled:     Boolean, // true = read, false = skip
    readMode:    String,  // "full" | "title_only" | "app_only" | "text_only" | "skip"
    profileId:   String   // override the active profile for this app
)
```

Rules are stored as a JSON array in SharedPreferences under key `"app_rules"`.

When a notification arrives, the service:
1. Loads all rules
2. Finds the rule matching `sbn.packageName`
3. Checks `enabled` and `readMode` for skip conditions
4. Uses `profileId` (if set) to override the active profile for this notification

### Word Replacement Rules

**File:** `RulesFragment.kt`

Separate from per-app rules, these are global find-and-replace rules applied to all text:
- Stored as JSON array in SharedPreferences under key `"wording_rules"`
- Each rule is a `(find, replace)` pair
- Applied case-insensitively before all other text transforms
- Default rules convert abbreviations (lol, tbh, idk) and URLs to spoken forms

---

## 14. UI Architecture

### Activity & Navigation

**File:** `MainActivity.kt`

The app uses a single `AppCompatActivity` with a custom bottom navigation bar.  Four tabs manually swap `Fragment` instances:

| Tab | Fragment | Purpose |
|---|---|---|
| Home | `HomeFragment` | Service status, permissions, settings, DnD |
| Profiles | `ProfilesFragment` | Voice selection, profile editing, presets, gimmicks, commentary |
| Apps | `AppsFragment` | Per-app rules, app loading |
| Rules | `RulesFragment` | Word replacement rules |

Navigation is implemented with simple `Fragment.replace()` — no Navigation Component or ViewPager.

### HomeFragment

- Shows notification access status (green ✓ / red ✗)
- Shows service status (running / starting / not active)
- Toggles: service enabled, voice commands, read app name
- Read mode spinner (full / title only / app only / text only)
- DnD settings (start/end hour seekbars)
- Stop speaking button
- Eagerly calls `SherpaEngine.warmUp()` on view creation

### ProfilesFragment

The most complex UI — a scrollable editor for voice profiles:
- Profile selector (Spinner)
- Preset buttons (15 personality presets)
- Voice grid (filtered by gender and language, showing Kokoro + Piper voices)
- Voice alias renaming
- TTS parameter sliders (pitch, speed, breathiness, stutter, intonation)
- Gimmick configuration (12 types × frequency × position)
- Commentary pool editor (add/edit/remove pools with condition selection)
- Test speak button (synthesizes preview text with current settings)
- Engine status indicator (ready / loading / error)

### AppsFragment

- "Load installed apps" button scans device for non-system apps
- Each app shows: enabled toggle, read mode dropdown, profile override dropdown
- Rules are saved to SharedPreferences on change

### RulesFragment

- List of find/replace text field pairs
- "Add Rule" button
- Changes auto-save after 500ms debounce

---

## 15. Build & CI System

### Build Configuration

**File:** `app/build.gradle`

| Property | Value |
|---|---|
| compileSdk / targetSdk | 34 |
| minSdk | 26 (Android 8.0) |
| ABI filter | arm64-v8a only |
| Java version | 17 |
| Kotlin version | 1.9.22 |
| Gradle Plugin | 8.2.2 |

**Dependencies:**
- `sherpa-onnx` — pre-built AAR in `app/libs/` (downloaded by CI)
- `androidx.core:core-ktx:1.12.0`
- `androidx.appcompat:appcompat:1.6.1`
- `androidx.constraintlayout:constraintlayout:2.1.4`
- `androidx.preference:preference-ktx:1.2.1`
- `androidx.fragment:fragment-ktx:1.6.2`

**No-compress assets:** `onnx` and `bin` files are exempt from AAPT compression since they're already compressed or benefit from direct memory mapping.

### CI/CD Pipeline

**File:** `.github/workflows/build.yml`

The GitHub Actions workflow runs on push to `main` or manual dispatch:

```
1. Checkout repository
2. Setup Java 17 (Temurin)
3. Setup Gradle
4. Download sherpa-onnx AAR (v1.12.28) → app/libs/sherpa_onnx.aar
5. Download Kokoro multi-lang model → app/src/main/assets/kokoro-model/
   (kokoro-multi-lang-v1_0.tar.bz2 → extract → model.onnx, voices.bin, tokens.txt, espeak-ng-data/)
6. Download bundled Piper voices → app/src/main/assets/piper-models/
   (en_US-lessac-medium.onnx, en_US-ryan-medium.onnx, fr_FR-siwis-medium.onnx, tokens.txt)
7. ./gradlew assembleRelease
8. Upload APK artifact (KokoroReader-v4, 30-day retention)
```

**Critical:** The project cannot be built locally without first downloading the sherpa-onnx AAR and TTS models.  The CI pipeline handles this automatically.

---

## 16. Thread Model & Concurrency

### Named Daemon Threads

All background threads are named and set as daemons:

| Thread Name | Purpose | Lifecycle |
|---|---|---|
| `SherpaEngine-warmup` | Extract models, initialize TTS engine | Created on warmUp(), ends after initialization |
| `AudioPipeline-loop` | Consume queue, synthesize, play | Created on start(), runs until shutdown() |
| `ModelExtractor` | Extract Kokoro model from assets | Created on ensureModel(), ends after extraction |
| `PiperDownload-{voiceId}` | Download Piper voice from HuggingFace | Created on downloadVoice(), ends after download |

### Synchronization

| Resource | Protection | Pattern |
|---|---|---|
| `SherpaEngine.warmUp()` | `warmUpLock` (synchronized block) | Check-and-set boolean prevents duplicate warm-ups |
| `SherpaEngine.synthesize()` | `@Synchronized` (instance) | All synthesis calls are serialized |
| `AudioPipeline.start()` | `@Synchronized` | Prevents duplicate pipeline threads |
| `AudioPipeline.currentTrack` | `trackLock` (synchronized block) | Protects AudioTrack stop/release from race conditions |
| `VoiceDownloadManager.ensureModelSync()` | `@Synchronized` | Prevents concurrent model extraction |
| `NotificationReaderService.dailyCount` | `countLock` + `@Volatile` | Atomic daily counter updates |
| `NotificationReaderService.lastNotificationContent` | `dedupLock` | Thread-safe LRU dedup map |
| `NotificationReaderService.lastSpokenText` | `@Volatile` + `@Synchronized recordSpoken()` | Safe cross-thread reads |

### Thread Safety Pattern

The app consistently uses `catch(Throwable)` instead of `catch(Exception)` in TTS-related code to also catch native library errors like `UnsatisfiedLinkError` and `OutOfMemoryError` from sherpa-onnx JNI calls.

---

## 17. Data Flow End-to-End

Here is the complete path a notification takes from arrival to speaker:

```
1. Android OS delivers StatusBarNotification
   │
2. NotificationReaderService.onNotificationPosted(sbn)
   ├── Check: service enabled? → no → return
   ├── Check: own package? → yes → return
   ├── Check: DnD active? → yes → return
   ├── Check: per-app rule skip? → yes → return
   ├── Extract: title + text from notification extras
   ├── Check: both blank? → yes → return
   ├── Dedup: same content for same notification ID? → yes → return
   ├── Increment daily flood counter
   │
3. SignalExtractor.extract(packageName, appName, title, text, hour, floodCount)
   ├── Detect emojis (happy, sad, angry, love, shock)
   ├── Classify source type (game, financial, service, platform, system, personal)
   ├── Classify sender type (human, bot, unknown)
   ├── Detect intents (request, plea, denial, alert, greeting, invite, action_required)
   ├── Determine stakes (type + level)
   ├── Determine urgency (none, soft, real, expiring, blocking)
   ├── Determine warmth (none, low, medium, high, distressed)
   ├── Determine register (minimal, casual, formal, dramatic, raw, technical)
   ├── Score intensity per line → compute trajectory (flat, building, peaked, collapsed)
   └── Return SignalMap
   │
4. Load active VoiceProfile (from per-app rule override or global active profile)
   │
5. VoiceModulator.modulate(profile, signal)
   ├── Apply trajectory multiplier
   ├── Compute strength values (distress, urgency, emotional, fake)
   ├── Modulate: pitch, speed, breathiness, stutter, intonation
   └── Return ModulatedVoice
   │
6. Build message text (app name + title + text, based on read mode)
   │
7. AudioPipeline.enqueue(Item)
   ├── If priority: clear queue, stop current playback
   └── Add to LinkedBlockingQueue
   │
   ▼ (AudioPipeline-loop thread picks up item)
   │
8. VoiceTransform.process(text, profile, modulated, signal, rules)
   ├── Apply wording rules (find/replace)
   ├── Apply commentary (pre/post contextual lines)
   ├── Apply gimmicks (sound effects)
   ├── Apply intonation (vowel stretching)
   ├── Apply stuttering (syllable repetition)
   └── Apply breathiness (h-sound insertion)
   │
9. SherpaEngine.synthesize() or synthesizePiper()
   ├── Route to Kokoro (speaker ID) or Piper (voice model) or default fallback
   └── Return (FloatArray PCM samples, sampleRate)
   │
10. AudioDsp.apply(samples, sampleRate, modulated)
    ├── Soft saturation (tanh waveshaping)
    ├── Formant smoothing (IIR envelope)
    └── Breathiness (noise mixing, if enabled)
    │
11. AudioPipeline.playPcm(samples, sampleRate, pitch)
    ├── Create AudioTrack (MODE_STATIC, FLOAT, MONO)
    ├── Write PCM data
    ├── Set playbackRate (pitch shift)
    ├── Set marker position for completion detection
    ├── Play → await marker (CountDownLatch, 60s timeout)
    └── Release AudioTrack
```

---

## 18. Error Handling Strategy

### Global Exception Handler

`ReaderApplication` installs a global uncaught exception handler that logs all crashes before delegating to the default handler.  This catches background thread crashes that would otherwise be silent.

### TTS Engine Errors

- `SherpaEngine.errorMessage` — a `@Volatile String?` that tracks the latest initialization failure.  Set in `warmUp()` catch and `initializeKokoro()` catch, cleared on success.  Displayed in red on the Profiles tab.
- Piper synthesis failures are caught and fall back to the default Kokoro voice.
- All TTS-related catch blocks use `Throwable` (not `Exception`) to catch JNI errors.

### Download Errors

- `PiperVoiceManager.downloadFile()` uses the temp-file-then-rename pattern — if a download is interrupted, the `.tmp` file is cleaned up and the voice doesn't appear as "ready".
- `VoiceDownloadManager` tracks state (NOT_EXTRACTED, EXTRACTING, READY, ERROR) with callbacks for UI updates.

### Service Resilience

- `onListenerDisconnected()` automatically calls `requestRebind()` to recover from permission revocation.
- `BootReceiver` re-binds the service after device restart.
- Foreground notification prevents the OS from killing the service.
- The pipeline loop catches `Throwable` per item, so a single bad notification doesn't crash the loop.

### Fragment Safety

- `HomeFragment` uses `context ?: return` instead of `requireContext()` in callback methods that may fire after fragment detach.
- UI updates from background threads use `activity?.runOnUiThread {}` with null-safe calls.
