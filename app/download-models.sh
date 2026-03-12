#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Downloads all TTS dependencies needed to build Kyōkan.
# Everything is fetched from OUR OWN GitHub Release (tts-assets-v1) —
# no external dependencies on HuggingFace, k2-fsa, or other repos.
#
# Assets are uploaded by: .github/workflows/upload-voices.yml
#
# This script is idempotent — it skips files that already exist.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LIBS_DIR="libs"
KOKORO_DIR="src/main/assets/kokoro-model"
PIPER_DIR="src/main/assets/piper-models"

# All assets live in a single GitHub Release
RELEASE_BASE="https://github.com/WW-Andene/Echolibrium/releases/download/tts-assets-v1"

RETRY="--retry 3 --retry-delay 5"

# ── Utility ──────────────────────────────────────────────────────────────────

download() {
    local url="$1" dest="$2"
    if [ -f "$dest" ]; then
        echo "  ✓ $(basename "$dest") already exists — skipping"
        return 0
    fi
    echo "  ↓ Downloading $(basename "$dest")…"
    curl -fL $RETRY "$url" -o "$dest" || {
        echo "  ✗ FAILED: $url"
        rm -f "$dest"
        return 1
    }
}

# ── 1. Sherpa-onnx AAR ──────────────────────────────────────────────────────

echo ""
echo "═══ Step 1/3: sherpa-onnx AAR ═══"
mkdir -p "$LIBS_DIR"
download "$RELEASE_BASE/sherpa_onnx.aar" "$LIBS_DIR/sherpa_onnx.aar"

# ── 2. Kokoro model ─────────────────────────────────────────────────────────

echo ""
echo "═══ Step 2/3: Kokoro multi-lang model ═══"
mkdir -p "$KOKORO_DIR"

if [ -f "$KOKORO_DIR/model.onnx" ] && [ -f "$KOKORO_DIR/voices.bin" ] && [ -f "$KOKORO_DIR/tokens.txt" ] && [ -d "$KOKORO_DIR/espeak-ng-data" ]; then
    echo "  ✓ Kokoro FP32 model already present — skipping"
else
    echo "  ↓ Downloading Kokoro FP32 model (~120MB)…"
    curl -fL $RETRY "$RELEASE_BASE/kokoro-multi-lang-v1_0.tar.bz2" -o kokoro-model.tar.bz2
    echo "  ↓ Extracting…"
    tar -xjf kokoro-model.tar.bz2 -C "$KOKORO_DIR" --strip-components=1
    rm -f kokoro-model.tar.bz2
    echo "  ✓ Kokoro FP32 model ready"
fi

# INT8 quantized model (110MB vs 310MB — for low-RAM devices)
if [ -f "$KOKORO_DIR/model.int8.onnx" ]; then
    echo "  ✓ Kokoro INT8 model already present — skipping"
else
    echo "  ↓ Downloading Kokoro INT8 model (~126MB archive)…"
    curl -fL $RETRY "$RELEASE_BASE/kokoro-int8-multi-lang-v1_0.tar.bz2" -o kokoro-int8.tar.bz2
    echo "  ↓ Extracting model.int8.onnx…"
    # Only extract the INT8 model file — voices.bin/tokens.txt/espeak-ng-data are shared
    tar -xjf kokoro-int8.tar.bz2 --strip-components=1 -C "$KOKORO_DIR" \
        "kokoro-int8-multi-lang-v1_0/model.int8.onnx"
    rm -f kokoro-int8.tar.bz2
    echo "  ✓ Kokoro INT8 model ready"
fi

echo "  Files:"
ls -lh "$KOKORO_DIR"/*.onnx "$KOKORO_DIR"/*.bin "$KOKORO_DIR"/*.txt 2>/dev/null | while read -r line; do echo "    $line"; done

# ── 3. Core Piper voices (bundled in APK) ────────────────────────────────────

echo ""
echo "═══ Step 3/3: Core Piper voices (bundled in APK) ═══"
mkdir -p "$PIPER_DIR"

# Shared tokens.txt
download "$RELEASE_BASE/piper-tokens.txt" "$PIPER_DIR/tokens.txt"

# ── Bundled voices ───────────────────────────────────────────────────────────
# Keep in sync with PiperVoiceCatalog.kt BUNDLED_IDS
BUNDLED_VOICES=(
    "en_US-lessac-medium"
    "en_US-ryan-medium"
    "en_US-amy-medium"
    "en_US-joe-medium"
    "en_GB-alba-medium"
    "en_GB-alan-medium"
    "fr_FR-siwis-medium"
    "fr_FR-tom-medium"
)

TOTAL=${#BUNDLED_VOICES[@]}
DOWNLOADED=0
SKIPPED=0
FAILED=0

for VOICE_ID in "${BUNDLED_VOICES[@]}"; do
    if download "$RELEASE_BASE/${VOICE_ID}.onnx" "$PIPER_DIR/${VOICE_ID}.onnx"; then
        if [ -f "$PIPER_DIR/${VOICE_ID}.onnx" ]; then
            # Count new downloads vs skips based on download() output
            DOWNLOADED=$((DOWNLOADED + 1))
        fi
    else
        FAILED=$((FAILED + 1))
    fi
done

echo ""
echo "  Bundled voices: $TOTAL total, $FAILED failed"

# ── 4. Optimize models (ORT format) ─────────────────────────────────────────

echo ""
echo "═══ Step 4/4: Optimize bundled models (ORT format) ═══"

if command -v python3 &>/dev/null && python3 -c "import onnxruntime" 2>/dev/null; then
    python3 "$SCRIPT_DIR/optimize-models.py"
else
    echo "  ⚠ python3 + onnxruntime not available — skipping ORT optimization"
    echo "    Install with: pip install onnxruntime"
    echo "    The app will still work with .onnx files (slower first load)"
fi

# ── Summary ──────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════"
PIPER_ONNX=$(find "$PIPER_DIR" -maxdepth 1 -name '*.onnx' 2>/dev/null | wc -l)
PIPER_ORT=$(find "$PIPER_DIR" -maxdepth 1 -name '*.ort' 2>/dev/null | wc -l)
PIPER_COUNT=$((PIPER_ONNX + PIPER_ORT))
ORT_COUNT=$(find "$KOKORO_DIR" "$PIPER_DIR" -maxdepth 1 -name '*.ort' 2>/dev/null | wc -l)
echo "  AAR:    $(ls -lh "$LIBS_DIR/sherpa_onnx.aar" 2>/dev/null | awk '{print $5}' || echo 'MISSING')"
echo "  Kokoro: $(du -sh "$KOKORO_DIR" 2>/dev/null | cut -f1 || echo 'MISSING')"
echo "  Piper:  ${PIPER_COUNT} bundled voices ($(du -sh "$PIPER_DIR" 2>/dev/null | cut -f1 || echo '0'))"
if [ "$ORT_COUNT" -gt 0 ]; then
    echo "  ORT:    ${ORT_COUNT} pre-optimized models (5-10x faster load)"
fi
echo ""
echo "  All assets fetched from: $RELEASE_BASE"
echo "  Non-bundled voices are downloaded by the app on demand."
echo "  Ready to build: ./gradlew assembleRelease"
echo "═══════════════════════════════════════════════"
