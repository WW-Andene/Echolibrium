# Piper Voice Catalog

Voice definitions sourced from [OHF-Voice/piper1-gpl](https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/VOICES.md).

## Included Languages

- **en_US** — English (United States): 20 voices
- **fr_FR** — French (France): 6 voices

## Voice Model Files

Each voice requires two files downloaded from HuggingFace:
1. A `.onnx` model file (e.g., `en_US-lessac-medium.onnx`)
2. A `.onnx.json` config file (e.g., `en_US-lessac-medium.onnx.json`)

Download from: https://huggingface.co/rhasspy/piper-voices/tree/main

## Quality Levels

- **x_low** — Fastest, smallest, lowest quality
- **low** — Good balance for mobile
- **medium** — Recommended for most use cases
- **high** — Best quality, largest model size

## License

Piper is intended for personal use and text to speech research.
Some voices may have restrictive licenses — review the MODEL_CARD for each voice.
