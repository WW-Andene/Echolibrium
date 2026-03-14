# Kyōkan TTS Engine — Architecture Breakdown & Reassembly Plan

## The Mission
Replace SherpaONNX + Kokoro/Piper with a multi-engine system built around Orpheus 3B, Chatterbox 0.5B, and Qwen3-TTS 1.7B — maximizing human-like voice fidelity while keeping everything free and open-source.

---

## PART 1: MODEL ANATOMY — What's Inside Each Engine

### Engine 1: Orpheus 3B (Primary — Maximum Fidelity)

```
Architecture:  Llama-3B backbone (full transformer)
Parameters:    3 billion (also available: 1B, 400M, 150M)
License:       Apache 2.0
Training Data: 100k+ hours of speech
Sample Rate:   24kHz
Approach:      Text → LLM generates speech tokens → Decode tokens to waveform

Pipeline:
┌──────────┐    ┌─────────────────┐    ┌──────────────┐    ┌───────────┐
│ Text +   │───▶│ Llama-3B        │───▶│ Speech Token │───▶│ WAV Audio │
│ Emotion  │    │ (predicts audio │    │ Decoder      │    │ Output    │
│ Tags     │    │  tokens)        │    │ (SNAC codec) │    │           │
└──────────┘    └─────────────────┘    └──────────────┘    └───────────┘
```

**Key Components:**
- **Tokenizer:** Extended Llama tokenizer with special audio tokens + emotion tags
- **Backbone:** Standard Llama transformer — predicts next audio token
- **Codec:** SNAC (multi-scale neural audio codec) decodes tokens → waveform
- **Emotion Tags:** `<laugh>`, `<sigh>`, `<gasp>`, `<cough>`, `<chuckle>`,
  `<sniffle>`, `<groan>`, `<yawn>` — treated as special tokens

**Runtime Options:**
- `vLLM` — fastest for GPU server inference, supports streaming
- `llama.cpp` — CPU/mobile friendly, GGUF quantized versions exist (Q4, Q8)
- `Transformers` — standard HuggingFace, easiest to fine-tune from

**What Makes It Special:**
- The ENTIRE model is one Llama — no separate encoder, vocoder, or alignment
- Fine-tuning = standard LLM fine-tuning (LoRA, QLoRA, full)
- Community fine-tunes drop in as weight swaps
- Emotion is learned IN the weights, not bolted on

**Quantized Variants Already Available:**
- `canopylabs/orpheus-3b-0.1-ft` — full precision fine-tuned
- `canopylabs/orpheus-3b-0.1-pretrained` — base for custom fine-tunes
- `QuantFactory/orpheus-3b-0.1-ft-GGUF` — GGUF for llama.cpp
- `lex-au/Orpheus-3b-FT-Q8_0.gguf` — Q8 quantized + FastAPI server

---

### Engine 2: Chatterbox 0.5B (Secondary — Speed + Emotion Control)

```
Architecture:  Two-stage — Llama-0.5B controller + Acoustic diffusion model
Parameters:    0.5B (controller) + acoustic model | Turbo variant: 350M
License:       MIT (original) / Apache 2.0 (multilingual)
Training Data: 500k+ hours of cleaned audio
Sample Rate:   24kHz
Approach:      Text → Controller generates speech tokens → Diffusion decodes to mel → Vocoder

Pipeline:
┌──────────┐   ┌────────────┐   ┌────────────────┐   ┌─────────┐   ┌───────┐
│ Text +   │──▶│ Llama-0.5B │──▶│ Audio Diffusion│──▶│ Vocoder │──▶│ WAV   │
│ Ref Audio│   │ Controller │   │ Decoder        │   │         │   │       │
│ + Params │   │ (semantic/ │   │ (mel-spec)     │   │         │   │       │
│          │   │  emotional)│   │ 10-step / 1-step│  │         │   │       │
└──────────┘   └────────────┘   └────────────────┘   └─────────┘   └───────┘
```

**Key Components:**
- **Controller:** 0.5B Llama backbone — handles text understanding,
  emotion/reference encoding, and high-level speech planning
- **Acoustic Decoder:** Diffusion model converts speech tokens to mel-spectrograms
  - Original: 10-step diffusion
  - Turbo: 1-step distilled (much faster)
- **Vocoder:** Converts mel-spectrograms to raw audio
- **Reference Encoder:** Extracts speaker identity from 7-20s audio clip

**Runtime Options:**
- Python + PyTorch (native)
- FastAPI server (multiple community implementations ready to go)
- CUDA, ROCm, MPS (Apple), and CPU all supported

**Unique Features:**
- `exaggeration` parameter (0.0–1.0+) — dials emotion intensity up/down
- `cfg_weight` — controls how strongly reference voice influences output
- Zero-shot voice cloning from short audio clip
- Turbo variant with paralinguistic tags: `[laugh]`, `[cough]`, `[chuckle]`
- PerTh neural watermarking built in

**Variants:**
- `resemble-ai/chatterbox` — English, emotion control (MIT)
- `resemble-ai/chatterbox-multilingual` — 23 languages (Apache 2.0)
- `resemble-ai/chatterbox-turbo` — 350M, 1-step, fastest (MIT)
- Community: `Kartoffelbox` (German fine-tune), more emerging

---

### Engine 3: Qwen3-TTS 1.7B (Complementary — Instruction-Controlled Voice)

```
Architecture:  Qwen transformer + discrete multi-codebook LM + 12Hz tokenizer
Parameters:    1.7B (also: 0.6B)
License:       Apache 2.0
Training Data: Undisclosed (Alibaba scale)
Sample Rate:   24kHz
Approach:      Text + instructions → LM predicts multi-codebook tokens → Decode

Pipeline:
┌──────────────┐   ┌────────────┐   ┌──────────────────┐   ┌───────┐
│ Text +       │──▶│ Qwen 1.7B  │──▶│ Qwen3-TTS        │──▶│ WAV   │
│ Voice instru-│   │ Backbone   │   │ Tokenizer-12Hz   │   │       │
│ ctions +     │   │ (discrete  │   │ (acoustic decode) │   │       │
│ Speaker ref  │   │  codebook) │   │                  │   │       │
└──────────────┘   └────────────┘   └──────────────────┘   └───────┘
```

**Key Components:**
- **Backbone:** Qwen transformer with discrete multi-codebook LM
- **Tokenizer:** Qwen3-TTS-Tokenizer-12Hz — efficient acoustic compression
  that preserves paralinguistic info and environmental features
- **Decoder:** Lightweight non-DiT architecture for high-speed reconstruction

**Runtime Options:**
- Transformers (HuggingFace)
- vLLM
- Available on DeepInfra for free-tier hosted inference

**Unique Features:**
- Natural language voice control — "speak slowly and calmly", "excited tone"
- Dual-track streaming: single model supports both streaming (97ms) and non-streaming
- 9 preset voices across different genders, ages, accents
- Voice cloning from ~3 seconds of audio
- 10 languages: EN, ZH, JA, KO, DE, FR, RU, PT, ES, IT

**Sub-models (same backbone, different capabilities):**
- `Qwen3-TTS-Base` — voice cloning
- `Qwen3-TTS-CustomVoice` — style control via instructions, 9 premium voices
- `Qwen3-TTS-VoiceDesign` — describe any voice in natural language, it creates it

---

## PART 2: WHAT CARRIES FORWARD INTO KYŌKAN

```
KEEP (unchanged):
├── AndroidManifest.xml — permissions, service declarations
├── NotificationListenerService — intercepts notifications
├── Notification parsing/filtering logic
├── UI / Settings / Preferences screens
├── Mood analysis pipeline
├── Personality text rewriter
├── Custom Core — SQLite observation + Groq behavior patches
└── App-level architecture (modules, DI, etc.)

REPLACE:
├── SherpaONNX runtime → NEW: TTS Router + engine backends
├── Kokoro model loading → NEW: Orpheus/Chatterbox/Qwen3 backends
├── VITS/Piper model loading → (removed, or kept as legacy fallback)
└── Audio format handling → adapt to 24kHz PCM from new engines

ADAPT:
├── DSP pipeline — still processes audio, just from new source
├── Mel-spectrogram interception — different model, same concept
└── Audio playback — may need sample rate adjustment
```

---

## PART 3: THE TTS ROUTER — Central Nervous System

```kotlin
// Core interface — every engine implements this
interface TtsEngine {
    val engineId: String
    val isAvailable: Boolean          // can this engine run right now?
    val supportsStreaming: Boolean
    val supportedEmotionTags: List<String>
    val estimatedLatencyMs: Long

    suspend fun synthesize(request: TtsRequest): TtsResult
    suspend fun warmUp()              // preload model
    fun release()                     // free resources
}

data class TtsRequest(
    val text: String,
    val emotionTags: List<String> = emptyList(),
    val voiceInstruction: String? = null,  // for Qwen3 only
    val referenceAudioPath: String? = null, // for voice cloning
    val priority: Priority = Priority.NORMAL
)

sealed class TtsResult {
    data class Success(val audioData: ByteArray, val sampleRate: Int) : TtsResult()
    data class Streaming(val audioFlow: Flow<ByteArray>, val sampleRate: Int) : TtsResult()
    data class Error(val reason: String) : TtsResult()
}

// The Router decides which engine handles each request
class TtsRouter(
    private val engines: List<TtsEngine>,
    private val customCore: CustomCoreObserver  // learns user preferences
) {
    suspend fun synthesize(request: TtsRequest): TtsResult {
        val engine = selectEngine(request)
        val result = engine.synthesize(request)
        customCore.observe(engine.engineId, request, result) // learn
        return result
    }

    private fun selectEngine(request: TtsRequest): TtsEngine {
        // Decision logic:
        // 1. Has voice instruction? → Qwen3 (only engine that supports it)
        // 2. Needs emotion exaggeration control? → Chatterbox
        // 3. Maximum fidelity? → Orpheus
        // 4. Offline / no server? → smallest available engine
        // 5. Custom Core preference? → whatever user responds best to
    }
}
```

---

## PART 4: ENGINE BACKENDS — How Each One Connects

### Backend Option A: Local Server (recommended to start)

Run each model as a local HTTP/WebSocket server. The Android app calls them
via HTTP. This decouples the models from the app completely.

```
┌─────────────────────────────────────┐
│           Android App               │
│  ┌───────────┐  ┌───────────────┐   │
│  │ Notif     │  │ TTS Router    │   │
│  │ Listener  │─▶│ (Kotlin)      │   │
│  └───────────┘  └──────┬────────┘   │
│                        │ HTTP       │
└────────────────────────┼────────────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
    ┌────▼────┐    ┌─────▼─────┐   ┌─────▼──────┐
    │ Orpheus │    │Chatterbox │   │ Qwen3-TTS  │
    │ Server  │    │ Server    │   │ Server     │
    │(llama.cpp│   │(FastAPI)  │   │(vLLM or    │
    │ or vLLM)│    │           │   │ Transforms)│
    └─────────┘    └───────────┘   └────────────┘
    Port 8001      Port 8002       Port 8003
```

### Backend Option B: Free Cloud Inference (no local GPU needed)

- **DeepInfra** — hosts Orpheus, Qwen3-TTS, Chatterbox with free tier
- **HuggingFace Inference** — free serverless endpoints
- **Replicate** — Chatterbox hosted, pay-per-use

### Backend Option C: Hybrid

Chatterbox Turbo (350M) runs on-device or cheap VPS.
Orpheus/Qwen3 on DeepInfra free tier when network available.

---

## PART 5: BUILD ORDER — Phase by Phase

### Phase 1: Proof of Concept (get sound out)
- [ ] Set up Orpheus 3B via llama.cpp or DeepInfra API
- [ ] Generate first audio from text
- [ ] Confirm quality meets expectations
- [ ] Test emotion tags: `<laugh>`, `<sigh>`, etc.
- [ ] Compare side-by-side with current Kokoro output

### Phase 2: TTS Router Interface
- [ ] Define `TtsEngine` interface in Kotlin
- [ ] Implement `OrpheusEngine` backend (HTTP client to server)
- [ ] Implement basic `TtsRouter` with single-engine dispatch
- [ ] Wire into existing notification pipeline (replace SherpaONNX calls)
- [ ] Verify notifications now speak through Orpheus

### Phase 3: Add Chatterbox
- [ ] Set up Chatterbox server (FastAPI, use community server)
- [ ] Implement `ChatterboxEngine` backend
- [ ] Add emotion exaggeration control mapping from mood analysis
- [ ] Add to router selection logic
- [ ] Test multilingual if needed

### Phase 4: Add Qwen3-TTS
- [ ] Set up Qwen3-TTS server
- [ ] Implement `Qwen3Engine` backend
- [ ] Map mood analysis output → voice instructions
- [ ] Add to router selection logic

### Phase 5: Smart Routing + Custom Core
- [ ] Implement Custom Core observation for each engine's output
- [ ] Track user response patterns (skip rate, volume changes, etc.)
- [ ] Auto-adjust engine selection based on learned preferences
- [ ] A/B test engines on similar notifications

### Phase 6: Fine-Tuning (the growth path)
- [ ] Collect preferred voice samples
- [ ] LoRA fine-tune Orpheus for your ideal voice/style
- [ ] Fine-tune Chatterbox for notification-specific delivery
- [ ] Swap fine-tuned models into existing pipeline (zero app changes)

---

## PART 6: RUNTIME REQUIREMENTS

### Orpheus 3B
| Format   | VRAM/RAM   | Speed          | Quality |
|----------|------------|----------------|---------|
| FP16     | ~6GB VRAM  | Real-time+     | Best    |
| Q8 GGUF  | ~3.5GB     | Real-time      | Great   |
| Q4 GGUF  | ~2GB       | Faster         | Good    |
| DeepInfra | Cloud     | ~200ms latency | Best    |

### Chatterbox 0.5B
| Variant      | VRAM/RAM  | Speed           | Quality |
|--------------|-----------|-----------------|---------|
| Original     | ~2GB VRAM | Sub-200ms       | Best    |
| Turbo (350M) | ~1.5GB   | Much faster     | Great   |
| CPU          | ~4GB RAM  | Slower but works| Great   |

### Qwen3-TTS 1.7B
| Format    | VRAM/RAM  | Speed          | Quality |
|-----------|-----------|----------------|---------|
| FP16      | ~4GB VRAM | 97ms streaming | Best    |
| DeepInfra | Cloud     | ~100ms latency | Best    |

---

## PART 7: KEY DECISIONS TO MAKE NOW

1. **Where will models run?**
   - Local machine/server you own?
   - Free cloud APIs (DeepInfra, HuggingFace)?
   - Future: on-device Android (only Chatterbox Turbo might fit)?

2. **Start with which engine?**
   - Recommended: Orpheus via DeepInfra free tier (zero setup)
   - Then add Chatterbox locally (lighter, easier to self-host)
   - Then Qwen3-TTS via DeepInfra

3. **Keep Kokoro as offline fallback?**
   - Recommended: Yes, for now. Remove later when you're confident
     in the new system. SherpaONNX stays for this one model.

4. **Audio format standardization?**
   - All three engines output 24kHz — standardize on this
   - DSP pipeline adapts once, works for all engines
