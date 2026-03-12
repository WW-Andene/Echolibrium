# ECHOLIBRIUM — FULL ARCHITECTURE DISCUSSION
### Compiled for Claude Max / Opus 4.6
### Context: continuation of deep technical session on self-improving TTS notification app

---

## WHO I AM / PROJECT CONTEXT

I am building **Echolibrium** — a native Android notification reader app with fully
customizable AI voices. The app reads notifications aloud using on-device neural TTS,
with mood detection, personality-driven text rewriting, and voice modulation.

**Current stack:**
- Language: Kotlin (native Android)
- TTS Backend: SherpaONNX (dual engine)
  - Kokoro-82M — 30 voices in one model, selected by speaker ID
  - Piper/VITS — one model per voice, lazy-loaded and cached
- Models bundled in APK assets (~650MB total project)
- NotificationListenerService for reading device notifications
- BootReceiver for persistence after reboot
- Full UI: Home, Apps, Rules, Profiles fragments

**Device:** Xiaomi 13T (8GB RAM, MediaTek Dimensity 8200-Ultra, 4nm)

**Current file structure (partial):**
```
app/src/main/java/com/kokoro/reader/
├── AppRule.kt
├── AppsFragment.kt
├── AudioDsp.kt
├── AudioPipeline.kt
├── BootReceiver.kt
├── CommentaryPool.kt
├── HomeFragment.kt
├── KokoroVoice.kt
├── MainActivity.kt
├── MoodState.kt
├── NotificationReaderService.kt
├── PhonicAnalyzer.kt
├── PiperVoiceCatalog.kt
├── PiperVoiceManager.kt
├── ProfilesFragment.kt
├── ReaderApplication.kt
├── RulesFragment.kt
├── SherpaEngine.kt
├── SignalExtractor.kt
├── SignalMap.kt
├── VoiceCommandHandler.kt
├── VoiceCommandListener.kt
├── VoiceDownloadManager.kt
├── VoiceModulator.kt
├── VoiceProfile.kt
├── VoiceTransform.kt
app/src/main/assets/
├── piper_voices/en_US/
├── piper_voices/fr_FR/
├── Sherpa_base1.zip
├── Sherpa_base2.zip
├── Sherpa_base3.zip
```

---

## THE VISION — WHAT WE ARE BUILDING

Echolibrium is not just a TTS app. It is a **living, self-improving audio intelligence
platform** with three major pillars:

### Pillar 1 — Self-Improving App (Option B Architecture)
The app improves its own behavior automatically without user intervention.
A lightweight on-device Custom Core observes everything. When it detects
a pattern or anomaly, it fires a free API call (Groq) to a cloud brain,
gets a JSON behavior patch back, and rewrites its own config live.
No reinstall. No update. No user action needed.

### Pillar 2 — Merged Engine
Instead of separate components passing data through a pipeline,
all engines (mood, voice, phonics, signal, DSP) merge into one
unified neural fabric where every component feels what every other
component knows in real time. Emergent behavior nobody programmed.

### Pillar 3 — Physiological Voice Model
Real voices are not DSP applied to finished audio. Real voices emerge
from coupled physiological systems. The new engine models this at the
mel-spectrogram level, before the vocoder renders — achieving things
that are physically impossible with waveform DSP.

---

## PROBLEM 1 — CURRENT DSP IS ADDITIVE AND FAKE

### What exists now:
```
Neural model generates clean voice
     ↓
Add breathiness (noise layer)
     ↓
Add pitch shift (STFT phase vocoder)
     ↓
Add speed change (resampling)
     ↓
Stack all → artifacts, degradation, robotic sound
```

Parameters don't interact. They are applied independently on top of
finished PCM audio. The result sounds artificial because in a real human
voice, all these parameters are coupled at the physiological source.

### The artifacts specifically:
- Pitch shift on PCM → phase coherence breaks → metallic artifacts
- Speed change on PCM → aliasing
- Stacking multiple transforms → cumulative degradation
- Breathiness as noise layer → sounds like noise, not breath

---

## PROBLEM 2 — FAKE ELONGATION / REPETITION

### What exists now:
To elongate a vowel, text is modified:
```
"hello" → "heeello"
```
The model sees extra letters and repeats phoneme tokens.
Sounds robotic because it is literal duplication — not real stretching.

### What should happen:
VITS and Kokoro both have an internal **Duration Predictor**.
It decides how many mel-spectrogram frames each phoneme gets.
```
Normal vowel    = 8 frames
Elongated vowel = 20 frames
```
Same audio content, genuinely stretched with proper formant continuity.
The fix is intercepting the duration predictor output before the decoder
runs — not modifying the text at all.

---

## PROBLEM 3 — VOICES DON'T BREATHE OR FEEL ALIVE

### What real exhaustion sounds like at a physiological level:
```
Subglottal air pressure drops
     ↓
Vocal fold tension changes
     ↓
Formants shift slightly
     ↓
Consonants weaken (breath support is low)
     ↓
Vowels reduce mid-sentence as air runs out
     ↓
Pitch becomes unstable predictably (not randomly)
     ↓
Breath intake sounds appear at phrase boundaries
     ↓
Next phrase starts stronger then fades again
```

This is one coherent physiological system. Not seven independent sliders.

No TTS system currently models this. Breath intake sounds are never
synthesized — silence is inserted. Breath support affecting tone
mid-phoneme is never modeled. The relationship between breath cycle
and prosodic contour is ignored entirely.

---

## THE CORE TECHNICAL FIX — MEL INTERCEPTION

### The split ONNX pipeline:

Currently SherpaONNX runs the full VITS/Kokoro model as one black box:
```
Text → [Black Box] → PCM Audio
```

The fix is splitting the model into two stages:
```
Stage 1: Acoustic Model
Text → Phonemes → Duration Predictor → Mel Spectrogram

                    ↑ INTERCEPTION POINT ↑
                    MelInterceptor lives here
                    All modifications happen here
                    Zero artifacts possible here

Stage 2: Vocoder (HiFi-GAN)
Modified Mel Spectrogram → Clean PCM Audio
```

### Why mel-space modifications are artifact-free:
- Pitch shift = shift frequency bins up/down (no phase issues)
- Speed = modify duration frame counts (from duration predictor)
- Breathiness = structured noise in specific frequency bands
- Warmth = boost lower mel bins
- All modifications applied once at source
- Vocoder renders once from clean modified features
- No stacking, no cumulative degradation

### MelInterceptor sketch:
```kotlin
class MelInterceptor {
    fun process(
        phonemes: List<Phoneme>,
        durations: IntArray,         // from duration predictor
        melFrames: FloatArray2D,     // pre-vocoder features
        state: PhysiologicalState
    ): FloatArray2D {

        // Real elongation — modify duration frames not text
        val modifiedDurations = DurationModifier.apply(
            durations, phonemes, state
        )

        // Real pitch — shift mel bins before render
        val pitchedMel = MelPitchShifter.apply(
            melFrames, state.pitchFactor
        )

        // Real breathiness — structured mel noise not PCM noise
        val breathyMel = BreathModel.apply(
            pitchedMel, state.subglottalPressure
        )

        // One clean render — zero artifacts
        return breathyMel
    }
}
```

### PhysiologicalState model sketch:
```kotlin
data class PhysiologicalState(
    val lungCapacity: Float,        // 0.0-1.0, depletes through phrase
    val subglottalPressure: Float,  // coupled to lung capacity
    val vocalFoldTension: Float,    // coupled to pressure + emotion
    val emotionalState: EmotionalState,
    val phrasePosition: Float,      // where in the phrase (0=start, 1=end)
    val breathPhase: BreathPhase    // inhale, hold, exhale, depleted
)
```

---

## HOW TO EXPOSE SHERPA ONNX INTERNALS

To intercept mel features, two options:

### Option A — Fork Sherpa ONNX Android library
Expose intermediate tensors from the VITS inference graph.
Hard but gives full control over the existing library.

### Option B — Custom split ONNX export (RECOMMENDED)
Export Kokoro as two separate ONNX graphs:
- `kokoro_acoustic.onnx` — text to mel
- `kokoro_vocoder.onnx` — mel to audio

Run them separately in Kotlin using ONNX Runtime.
MelInterceptor runs between the two inference calls.
Cleaner long-term. Full access to all intermediate tensors.

**This is the right architectural move for the merged engine.**

---

## OPTION B — SELF-IMPROVING ARCHITECTURE

### Concept:
Lightweight always-on Custom Core observes app behavior.
Detects patterns and anomalies.
Fires API call to Groq (free) when reasoning is needed.
Receives JSON behavior patch.
Rewrites echo_behavior.json live.
Next notification uses improved behavior.
Phone completely unaffected during normal use.

### Why Groq (not Anthropic API, not Firebase):
- Groq is completely free for low usage
- Response in under 1 second (500+ tokens/second)
- Llama 3.1 8B handles structured JSON patch generation perfectly
- At 10 calls/day well within free rate limits forever
- No Firebase needed — everything else stays on-device

### Data and privacy:
- Behavior data lives ONLY on the device (SQLite)
- Never leaves except as anonymous compressed context in API call
- No notification content ever stored or transmitted
- Only behavioral metadata: app package, interaction type, voice used
- GDPR clean — no identifiers leave the device
- Storage growth: ~1MB/month, negligible on 256GB device

### Files to create:
```
EchoCore.kt             — main singleton orchestrator
BehaviorLogger.kt       — logs every app event to SQLite
PatternDetector.kt      — detects anomalies and triggers
BehaviorDatabase.kt     — Room/SQLite schema
ImprovementTrigger.kt   — defines trigger conditions
ContextCompressor.kt    — compresses BehaviorLog into minimal prompt
CloudBrain.kt           — OkHttp call to Groq API
PatchParser.kt          — parses JSON response into config changes
PatchApplicator.kt      — applies changes to echo_behavior.json
ConfigManager.kt        — reads/writes/reloads behavior config
ApiKeyManager.kt        — secure key storage via Android Keystore
```

### Trigger conditions (when to call Groq):
- Same voice/mood combo fails 3+ times for same app package
- User replays a notification 2+ times
- User manually overrides mood detection 3+ times in same context
- New app package appears with no existing rule
- Daily idle summary (device charging + screen off)

### Groq API call:
```kotlin
POST https://api.groq.com/openai/v1/chat/completions

{
  "model": "llama-3.1-8b-instant",
  "max_tokens": 500,
  "messages": [
    {
      "role": "system",
      "content": "You are Echolibrium's improvement engine. You receive
                  behavior logs from a TTS notification app and return
                  a JSON patch to improve voice behavior.
                  Return only valid JSON. No explanation."
    },
    {
      "role": "user",
      "content": "[compressed behavior snapshot]"
    }
  ]
}
```

### Dependencies to add (nothing else):
```gradle
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.google.code.gson:gson:2.10.1'
implementation 'androidx.room:room-runtime:2.6.1'
kapt 'androidx.room:room-compiler:2.6.1'
```

---

## THE MERGED ENGINE — LONG TERM VISION

### Current state (pipeline):
```
NotificationReader  →  separate
MoodAnalyzer        →  separate
SherpaEngine        →  separate
AudioPipeline       →  separate
PhonicAnalyzer      →  separate
SignalExtractor      →  separate
```

### Target state (merged fabric):
```
One unified neural fabric
where mood shapes phonemes
phonemes inform signal extraction
signal feeds back into mood
breath cycle drives prosodic contour
everything is one continuous loop
```

### How it emerges:
The self-improving layer starts by patching individual components.
Over months it learns the relationships BETWEEN components.
It discovers combinations no preset ever had:
- Specific Signal from specific app at specific time of day
  = unique combination of Piper voice + Kokoro warmth +
    phoneme emphasis that nobody programmed
- Emergent behavior from learned coupling

The merged engine is not built. It grows.

### The Tamagotchi growth curve:
```
Year 1 — learns fast
  Every new app, new context is fresh data
  Behavior improves noticeably week by week

Year 2 — slows down
  Most patterns already learned
  Refinements become smaller
  Still improving but less noticeable

Year 3+ — plateau
  Config is perfect for this specific user
  New learning only on new apps or changed habits
  Not smarter — more YOURS
```

Ceiling can be broken later by:
- Upgrading the reasoning model
- Adding collective learning across users (requires consent + backend)
- Fine-tuning on accumulated behavior data

---

## IMPLEMENTATION PRIORITY ORDER

### Phase 1 — Fix the audio engine (most impactful immediately)
1. Split Kokoro ONNX into acoustic + vocoder stages
2. Implement MelInterceptor
3. Implement DurationModifier (real elongation)
4. Implement MelPitchShifter (artifact-free pitch)
5. Implement BreathModel (physiological breath)
6. Implement PhysiologicalState model

### Phase 2 — Self-improving layer foundation
7. BehaviorDatabase + BehaviorLogger
8. Wire logger into NotificationReaderService + AudioPipeline
9. ConfigManager + echo_behavior.json
10. Refactor SherpaEngine to read from ConfigManager

### Phase 3 — Core intelligence
11. PatternDetector + ImprovementTrigger
12. EchoCore orchestrator
13. Test trigger firing on simulated failures

### Phase 4 — Cloud connection
14. ApiKeyManager
15. ContextCompressor
16. CloudBrain (Groq)
17. PatchParser + PatchApplicator
18. End-to-end test

### Phase 5 — Hardening
19. Offline fallback (Core acts alone without internet)
20. Patch validation (reject malformed responses)
21. Patch history log (rollback capability)
22. Battery/network awareness

---

## KEY FILES TO READ FIRST (ask user to share these)

Before writing any integration code, read:
- `NotificationReaderService.kt` — notification flow entry point
- `AudioPipeline.kt` — audio processing chain
- `SherpaEngine.kt` — TTS synthesis (already partially seen)
- `PhonicAnalyzer.kt` — phoneme analysis (unknown internals)
- `SignalExtractor.kt` + `SignalMap.kt` — mood signal system

---

## WHAT MAKES THIS GENUINELY NEW

No TTS app does any of this:
- Mel-space modification before vocoder rendering
- Duration predictor interception for real elongation
- Physiological breath modeling coupled across a full phrase
- On-device self-improving behavior via cloud brain patches
- Engines that merge through learned coupling over time

This is not a notification reader with nice voices.
This is a new category of software.

---
*End of compiled discussion*
*Continue from here with full technical implementation*
