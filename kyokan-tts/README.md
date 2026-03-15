> **Note:** This is a standalone server-side TTS routing module. The Android app (`app/`) does NOT
> use this module — `CloudTtsEngine.kt` talks directly to DeepInfra's API. This module is for
> future server-side TTS routing with multiple engine support.

# Kyōkan TTS Engine

Multi-engine TTS system for Kyōkan — routes notifications to the best voice engine.

## Engines

| Engine | Model | Fidelity | Cost/1M chars | Special |
|--------|-------|----------|---------------|---------|
| **Orpheus 3B** | Llama-based | Highest | $7.00 | `<laugh>` `<sigh>` emotion tags |
| **Chatterbox Turbo** | Llama-0.5B | High | $1.00 | `[laugh]` tags, 23 languages |
| **Qwen3-TTS 1.7B** | Qwen-based | High | $1.00 | "speak calmly" voice instructions |

## Setup

```bash
# 1. Install dependency
pip install aiohttp

# 2. Set your DeepInfra API key
export DEEPINFRA_API_KEY="your_key_here"

# 3. Run the test
python test_engines.py          # full comparison test
python test_engines.py --quick  # one request per engine
```

## Get a DeepInfra API Key

1. Go to https://deepinfra.com
2. Sign up (GitHub login works)
3. Add $5 credit (minimum top-up)
4. Copy your API key from https://deepinfra.com/auth/personal_details

$5 covers roughly 700,000 characters through Orpheus, or 5,000,000 through Chatterbox.
For a notification reader, that's months of usage.

## Project Structure

```
kyokan-tts/
├── engines/
│   ├── __init__.py        # package exports
│   ├── base.py            # TtsEngine interface + data models
│   ├── orpheus.py         # Orpheus 3B backend
│   ├── chatterbox.py      # Chatterbox Turbo backend
│   └── qwen3.py           # Qwen3-TTS backend
├── outputs/               # generated audio files go here
├── router.py              # smart routing logic
├── test_engines.py        # test harness
├── requirements.txt
└── README.md
```

## Smart Routing Logic

The Kotlin `CloudTtsEngine` (Android side) and Python `TtsRouter` both use this logic:

1. **Voice instruction present?** → Qwen3-TTS (only one that supports it)
2. **Priority HIGH?** → Orpheus (maximum fidelity)
3. **Non-English text?** → Qwen3 (10 langs) or Chatterbox (23 langs)
4. **Orpheus emotion tags in text?** → Orpheus
5. **Chatterbox tags in text?** → Chatterbox
6. **Default** → Chatterbox Turbo (best cost/quality for notifications)

### Emotion-Aware Enrichment

The Android `CloudTtsEngine` maps Kyōkan's `SignalMap` analysis to engine controls:

- **Orpheus**: `EmotionBlend` → `<gasp>`, `<sigh>` tags injected into text
- **Chatterbox**: `EmotionBlend` → `[laugh]`, `[sniffle]` tags injected
- **Qwen3-TTS**: `EmotionBlend` + `UrgencyType` + `WarmthLevel` → natural language
  voice instructions (e.g., "speak warmly and gently, with soft nostalgia")

## Android Integration

The Kotlin `CloudTtsEngine` in `app/src/main/java/.../CloudTtsEngine.kt` is the
Android-side counterpart of this Python system. It calls DeepInfra directly from
the app via OkHttp and integrates into `AudioPipeline` as the primary synthesis path:

```
Cloud TTS (DeepInfra) → primary
   ↓ (fallback if disabled or fails)
YatagamiSynthesizer (local ORT) → secondary
   ↓ (fallback)
SherpaEngine (Kokoro/Piper) → offline fallback
```

API key is stored in `EncryptedSharedPreferences` under `deepinfra_api_key`.

## Observation Layer

Every cloud TTS call is logged to `ObservationDb.cloud_tts_observations`:
- Engine used, text length, latency, success/failure, language
- `cloudEngineStats()` returns per-engine success rate + avg latency
- Data feeds Custom Core for learning engine preferences over time

## Fine-Tuning (Phase 6)

To create a custom voice for Orpheus:

1. **Collect samples**: Record or gather 15-30 minutes of target voice audio
2. **Prepare dataset**: Segment into 5-15 second clips with transcriptions
3. **LoRA fine-tune**: Use the Orpheus training scripts with QLoRA (4-bit)
   - Base model: `canopylabs/orpheus-3b-0.1-pretrained`
   - Training: ~2 hours on a single A100 / ~6 hours on RTX 4090
4. **Export**: Merge LoRA weights → upload to DeepInfra or self-host
5. **Swap in**: Change `Engine.ORPHEUS.modelId` to your fine-tuned model ID
   — zero app changes needed, the pipeline stays the same

For Chatterbox, voice cloning from a 7-20s reference clip is built in —
no fine-tuning needed. Pass a reference audio URL in the API call.

## Implementation Status

- [x] Phase 1: Python engine backends + test harness
- [x] Phase 2: Kotlin `CloudTtsEngine` wired into `AudioPipeline`
- [x] Phase 3: Chatterbox + emotion tag mapping
- [x] Phase 4: Qwen3-TTS + language routing + voice instructions
- [x] Phase 5: Observation layer for Custom Core learning
- [x] Phase 6: Fine-tuning documentation
