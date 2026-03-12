# Shin Kyōkan — Cloud TTS + Self-Improving Architecture Plan

## Overview

Hybrid architecture: cloud Kokoro TTS on Oracle Cloud (free tier) for high-quality voices,
local Piper for offline fallback, and on-device self-improving intelligence via Groq.

---

## Phase 1 — Oracle Cloud TTS Server

**Goal:** Get Nicole and all 53 Kokoro voices working via cloud, zero device RAM.

### Server Stack
- **Host:** Oracle Cloud Always Free — 4 ARM Ampere cores, 24GB RAM
- **Runtime:** Python 3.11 + FastAPI + uvicorn
- **TTS:** sherpa-onnx (Python bindings) + Kokoro FP32 model
- **Audio format:** Opus (small, low latency) or WAV fallback

### API Endpoints
```
POST /tts
  Body: { "text": "Hello world", "speaker_id": 6, "speed": 1.0 }
  Headers: X-API-Key: <key>
  Response: audio/opus binary

GET /voices
  Response: { "voices": [{"id": 0, "name": "af", "lang": "en"}, ...] }

GET /health
  Response: { "status": "ok", "model": "kokoro-v1.0", "uptime": 3600 }
```

### Server Files
```
server/
├── main.py              — FastAPI app, /tts /voices /health endpoints
├── tts_engine.py        — sherpa-onnx wrapper, model loading, synthesis
├── auth.py              — API key validation middleware
├── requirements.txt     — fastapi, uvicorn, sherpa-onnx, numpy
├── Dockerfile           — for Oracle ARM deployment
├── setup-oracle.sh      — instance setup script (install deps, open ports, systemd)
└── kokoro-model/        — downloaded at setup time, not in git
```

### Security
- API key auth (generated at setup, stored in app settings)
- HTTPS via Let's Encrypt (free) or Cloudflare tunnel
- Rate limiting: 100 requests/minute per key
- No notification content stored server-side — stateless

---

## Phase 2 — Android Cloud TTS Client

**Goal:** App uses cloud Kokoro as primary engine, falls back to Piper offline.

### New File
```
app/src/main/java/com/kokoro/reader/CloudTtsEngine.kt
```

### Integration Point
In `SherpaEngine.warmUp()` init thread, before Piper fallback:
```
1. Try CloudTtsEngine.isReachable() (quick HEAD to /health, 2s timeout)
2. If reachable → set CloudTtsEngine as active, skip local init entirely
3. If unreachable → fall through to Piper fallback (existing code)
```

### CloudTtsEngine Behavior
- `synthesize(text, speakerId, speed)` → sends POST /tts, returns PCM audio
- Timeout: 5s per request, falls back to Piper for that sentence
- Caches server URL + API key in SharedPreferences
- Queues requests if multiple notifications arrive quickly
- Prefetches common phrases during idle (optional, Phase 5)

### Settings UI Addition
In ProfilesFragment or a new Settings section:
```
[Cloud TTS]
  Server URL:  [https://your-oracle-ip:8443  ]
  API Key:     [••••••••••••                 ]
  [Test Connection]
  Status: Connected — 53 voices available

  [ ] Use cloud TTS when available (fallback to local Piper)
```

### Engine Priority (updated)
```
1. Cloud Kokoro (if enabled + reachable)  → best quality, Nicole
2. Local Piper (always available)          → offline fallback
3. Android TTS (system)                    → last resort
```

---

## Phase 3 — Self-Improving Layer (EchoCore)

**Goal:** App learns user preferences and auto-optimizes voice behavior.

### On-Device Components
```
EchoCore.kt              — singleton orchestrator
BehaviorLogger.kt        — logs events to SQLite (Room)
BehaviorDatabase.kt      — Room schema: events, patterns, patches
PatternDetector.kt       — detects anomalies and triggers
ConfigManager.kt         — reads/writes echo_behavior.json
```

### What Gets Logged (on-device only)
- App package → voice used → mood detected → user reaction
- Replay events (user asked to hear notification again)
- Manual overrides (user changed voice/mood manually)
- TTS latency per engine (cloud vs local)
- Time of day, charging state, connectivity

### Trigger Conditions (when to call Groq)
- Same voice fails for same app 3+ times
- User replays notification 2+ times
- User manually overrides mood 3+ times in same context
- New app with no existing rule
- Daily idle summary (charging + screen off)

---

## Phase 4 — Cloud Brain (Groq)

**Goal:** Free cloud reasoning that generates behavior patches.

### How It Works
```
PatternDetector fires trigger
     ↓
ContextCompressor.kt reduces behavior log to ~200 tokens
     ↓
CloudBrain.kt sends to Groq (llama-3.1-8b-instant, free tier)
     ↓
PatchParser.kt validates JSON response
     ↓
PatchApplicator.kt writes to echo_behavior.json
     ↓
Next notification uses improved config
```

### Groq Call (~10/day, well within free limits)
```json
{
  "model": "llama-3.1-8b-instant",
  "max_tokens": 500,
  "messages": [
    {"role": "system", "content": "You are Echolibrium's improvement engine..."},
    {"role": "user", "content": "<compressed behavior snapshot>"}
  ]
}
```

### Patch Format
```json
{
  "app_rules": {
    "com.whatsapp": {
      "preferred_voice": "af_nicole",
      "speed": 1.1,
      "mood_override": null
    }
  },
  "time_rules": {
    "23:00-07:00": { "speed": 0.9, "volume": 0.6 }
  }
}
```

---

## Phase 5 — Mel Interception (Server-Side)

**Goal:** Physiological voice model — breath, emotion, real elongation.

### Server-Side Split Model
```
kokoro_acoustic.onnx   — text → mel spectrogram
MelInterceptor         — modify mel frames (Python, on server)
kokoro_vocoder.onnx    — mel → PCM audio
```

### What MelInterceptor Does
- **Real elongation:** modify duration predictor output (not text)
- **Artifact-free pitch:** shift mel frequency bins before vocoder
- **Breath modeling:** structured noise in specific frequency bands
- **Warmth/cold:** boost/cut lower mel bins
- **Phrase-level dynamics:** lung capacity depletes through sentence

### Updated API
```
POST /tts
  Body: {
    "text": "Hello world",
    "speaker_id": 6,
    "speed": 1.0,
    "physiology": {
      "lung_capacity": 0.8,
      "breath_phase": "exhale",
      "emotion": "calm",
      "warmth": 0.6
    }
  }
```

### PhysiologicalState (computed on-device, sent with request)
```kotlin
data class PhysiologicalState(
    val lungCapacity: Float,        // 0-1, depletes through phrase
    val subglottalPressure: Float,  // coupled to lung capacity
    val vocalFoldTension: Float,    // coupled to pressure + emotion
    val emotionalState: String,     // from MoodState
    val phrasePosition: Float,      // 0=start, 1=end
    val breathPhase: String         // inhale, hold, exhale, depleted
)
```

---

## Phase 6 — Merged Engine (Long Term)

EchoCore learns relationships between components over months.
Groq patches start crossing component boundaries:
- Mood → voice → pitch → speed → breath become one coupled system
- Behavior is emergent, not programmed
- The app becomes uniquely tuned to the user

---

## Dependencies

### Server (Oracle)
```
fastapi
uvicorn[standard]
sherpa-onnx
numpy
```

### Android (new)
```gradle
// Already have OkHttp via sherpa-onnx transitive deps
// Room for behavior database
implementation 'androidx.room:room-runtime:2.6.1'
kapt 'androidx.room:room-compiler:2.6.1'
```

---

## Build Order

| Step | What | Where | Depends On |
|------|------|-------|------------|
| 1 | FastAPI TTS server | server/ | Nothing |
| 2 | Deploy to Oracle ARM | Oracle Cloud | Step 1 |
| 3 | CloudTtsEngine.kt | Android app | Step 2 |
| 4 | Settings UI for server URL | Android app | Step 3 |
| 5 | Test end-to-end | Both | Steps 1-4 |
| 6 | BehaviorDatabase + Logger | Android app | Nothing |
| 7 | EchoCore + PatternDetector | Android app | Step 6 |
| 8 | CloudBrain (Groq) | Android app | Step 7 |
| 9 | Split Kokoro ONNX model | Server | Step 2 |
| 10 | MelInterceptor | Server | Step 9 |
| 11 | PhysiologicalState API | Both | Step 10 |

Steps 1-5 get you Nicole + 53 voices immediately.
Steps 6-8 can run in parallel — no dependency on cloud TTS.
Steps 9-11 are the advanced audio work.

---

## What This Solves

- **RAM crashes:** gone — Kokoro runs on server with 24GB RAM
- **Voice quality:** FP32 Kokoro, all 53 voices including Nicole
- **Offline:** Piper always works without internet
- **Self-hosted:** users can point to their own server
- **Future-proof:** mel interception and self-improving layer plug in cleanly
- **APK size:** drops significantly without bundled Kokoro model (~300MB smaller)
