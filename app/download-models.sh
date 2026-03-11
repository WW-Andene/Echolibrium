#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Downloads all TTS dependencies needed to build Kyōkan:
#   1. sherpa-onnx AAR (native TTS library)
#   2. Kokoro multi-lang model (30 voices, ~120MB)
#   3. All 44 Piper/VITS voice models (~15-25MB each)
#
# This script is idempotent — it skips files that already exist.
# Run manually or let Gradle call it automatically via the
# downloadTtsModels task in build.gradle.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SHERPA_VERSION="1.12.28"
LIBS_DIR="libs"
KOKORO_DIR="src/main/assets/kokoro-model"
PIPER_DIR="src/main/assets/piper-models"
HF_BASE="https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0"

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
echo "═══ Step 1/3: sherpa-onnx AAR (v${SHERPA_VERSION}) ═══"
mkdir -p "$LIBS_DIR"
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-${SHERPA_VERSION}.aar"
download "$AAR_URL" "$LIBS_DIR/sherpa_onnx.aar"

# ── 2. Kokoro model ─────────────────────────────────────────────────────────

echo ""
echo "═══ Step 2/3: Kokoro multi-lang model ═══"
mkdir -p "$KOKORO_DIR"

if [ -f "$KOKORO_DIR/model.onnx" ] && [ -f "$KOKORO_DIR/voices.bin" ] && [ -f "$KOKORO_DIR/tokens.txt" ] && [ -d "$KOKORO_DIR/espeak-ng-data" ]; then
    echo "  ✓ Kokoro model already present — skipping"
else
    MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"
    echo "  ↓ Downloading Kokoro model (~120MB)…"
    curl -fL $RETRY "$MODEL_URL" -o kokoro-model.tar.bz2
    echo "  ↓ Extracting…"
    tar -xjf kokoro-model.tar.bz2 -C "$KOKORO_DIR" --strip-components=1
    rm -f kokoro-model.tar.bz2
    echo "  ✓ Kokoro model ready"
fi

echo "  Files:"
ls -lh "$KOKORO_DIR"/*.onnx "$KOKORO_DIR"/*.bin "$KOKORO_DIR"/*.txt 2>/dev/null | while read -r line; do echo "    $line"; done

# ── 3. Piper voices ─────────────────────────────────────────────────────────

echo ""
echo "═══ Step 3/3: Piper/VITS voices (44 models) ═══"
mkdir -p "$PIPER_DIR"

# Shared tokens.txt (from lessac package)
if [ ! -f "$PIPER_DIR/tokens.txt" ]; then
    LESSAC_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2"
    echo "  ↓ Downloading shared tokens.txt…"
    curl -fL $RETRY "$LESSAC_URL" -o piper-pkg.tar.bz2
    mkdir -p tmp-piper
    tar -xjf piper-pkg.tar.bz2 -C tmp-piper
    find tmp-piper -name "tokens.txt" -exec cp {} "$PIPER_DIR/tokens.txt" \;
    # Also grab lessac model while we have it
    find tmp-piper -name "en_US-lessac-medium.onnx" -exec cp {} "$PIPER_DIR/en_US-lessac-medium.onnx" \;
    rm -rf tmp-piper piper-pkg.tar.bz2
    echo "  ✓ tokens.txt ready"
else
    echo "  ✓ tokens.txt already present"
fi

VOICES=(
    "en_US-amy-low"
    "en_US-amy-medium"
    "en_US-arctic-medium"
    "en_US-bryce-medium"
    "en_US-danny-low"
    "en_US-hfc_female-medium"
    "en_US-hfc_male-medium"
    "en_US-joe-medium"
    "en_US-john-medium"
    "en_US-kathleen-low"
    "en_US-kristin-medium"
    "en_US-kusal-medium"
    "en_US-l2arctic-medium"
    "en_US-lessac-low"
    "en_US-lessac-medium"
    "en_US-lessac-high"
    "en_US-libritts-high"
    "en_US-libritts_r-medium"
    "en_US-ljspeech-medium"
    "en_US-ljspeech-high"
    "en_US-norman-medium"
    "en_US-reza_ibrahim-medium"
    "en_US-ryan-low"
    "en_US-ryan-medium"
    "en_US-ryan-high"
    "en_US-sam-medium"
    "en_GB-alan-low"
    "en_GB-alan-medium"
    "en_GB-alba-medium"
    "en_GB-aru-medium"
    "en_GB-cori-medium"
    "en_GB-cori-high"
    "en_GB-jenny_dioco-medium"
    "en_GB-northern_english_male-medium"
    "en_GB-semaine-medium"
    "en_GB-southern_english_female-low"
    "en_GB-vctk-medium"
    "fr_FR-gilles-low"
    "fr_FR-mls-medium"
    "fr_FR-mls_1840-low"
    "fr_FR-siwis-low"
    "fr_FR-siwis-medium"
    "fr_FR-tom-medium"
    "fr_FR-upmc-medium"
)

TOTAL=${#VOICES[@]}
DOWNLOADED=0
SKIPPED=0
FAILED=0

for VOICE_ID in "${VOICES[@]}"; do
    if [ -f "$PIPER_DIR/${VOICE_ID}.onnx" ]; then
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    # Parse: en_US-lessac-medium → lang=en, locale=en_US, name=lessac, quality=medium
    LANG_CODE="${VOICE_ID%%_*}"
    COUNTRY=$(echo "$VOICE_ID" | sed 's/^[^_]*_//' | sed 's/-.*//')
    LOCALE="${LANG_CODE}_${COUNTRY}"
    REST=$(echo "$VOICE_ID" | sed "s/^${LOCALE}-//")
    QUALITY="${REST##*-}"
    NAME="${REST%-*}"

    URL="${HF_BASE}/${LANG_CODE}/${LOCALE}/${NAME}/${QUALITY}/${VOICE_ID}.onnx?download=true"
    if download "$URL" "$PIPER_DIR/${VOICE_ID}.onnx"; then
        DOWNLOADED=$((DOWNLOADED + 1))
    else
        FAILED=$((FAILED + 1))
    fi
done

echo ""
echo "  Piper voices: $SKIPPED already present, $DOWNLOADED downloaded, $FAILED failed (of $TOTAL total)"

# ── 4. Optimize models (ORT format + strip languages) ────────────────────────

echo ""
echo "═══ Step 4/4: Optimize models (ORT format) ═══"

# Check if ORT conversion has already been done (idempotent)
if [ -f "$KOKORO_DIR/model.ort" ]; then
    echo "  ✓ Models already optimized — skipping"
else
    if command -v python3 &>/dev/null && python3 -c "import onnxruntime" 2>/dev/null; then
        python3 "$SCRIPT_DIR/optimize-models.py"
    else
        echo "  ⚠ python3 + onnxruntime not available — skipping ORT optimization"
        echo "    Install with: pip install onnxruntime"
        echo "    The app will still work with .onnx files (slower first load)"
    fi
fi

# ── Summary ──────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════"
PIPER_ONNX=$(ls -1 "$PIPER_DIR"/*.onnx 2>/dev/null | wc -l)
PIPER_ORT=$(ls -1 "$PIPER_DIR"/*.ort 2>/dev/null | wc -l)
PIPER_COUNT=$((PIPER_ONNX + PIPER_ORT))
ORT_COUNT=$(ls -1 "$KOKORO_DIR"/*.ort "$PIPER_DIR"/*.ort 2>/dev/null | wc -l)
echo "  AAR:    $(ls -lh "$LIBS_DIR/sherpa_onnx.aar" 2>/dev/null | awk '{print $5}' || echo 'MISSING')"
echo "  Kokoro: $(du -sh "$KOKORO_DIR" 2>/dev/null | cut -f1 || echo 'MISSING')"
echo "  Piper:  ${PIPER_COUNT} voices ($(du -sh "$PIPER_DIR" 2>/dev/null | cut -f1 || echo '0'))"
if [ "$ORT_COUNT" -gt 0 ]; then
    echo "  ORT:    ${ORT_COUNT} pre-optimized models (faster load)"
fi
echo ""
echo "  Ready to build: ./gradlew assembleDebug"
echo "═══════════════════════════════════════════════"
