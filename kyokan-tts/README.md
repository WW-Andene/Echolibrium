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

The router picks the engine based on:

1. **Voice instruction present?** → Qwen3-TTS (only one that supports it)
2. **Priority HIGH?** → Orpheus (maximum fidelity)
3. **Priority LOW?** → Chatterbox (cheapest, fastest)
4. **Orpheus emotion tags in text?** → Orpheus
5. **Chatterbox tags in text?** → Chatterbox
6. **Default** → Chatterbox Turbo (best cost/quality for notifications)

## Next Steps

- [ ] Phase 2: HTTP server wrapper (FastAPI) for Android app to call
- [ ] Phase 3: Kotlin TtsEngine interface in Kyōkan
- [ ] Phase 4: Custom Core observation + learning
- [ ] Phase 5: Fine-tune Orpheus for your preferred voice
