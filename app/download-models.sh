#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Downloads all TTS dependencies needed to build Kyōkan.
# Tries OUR OWN GitHub Release (tts-assets-v1) first, then falls back
# to the original upstream sources (k2-fsa, HuggingFace).
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

# Primary: our own GitHub Release
RELEASE_BASE="https://github.com/WW-Andene/Echolibrium/releases/download/tts-assets-v1"

# Fallback: upstream sources
SHERPA_VERSION="1.12.28"
UPSTREAM_AAR="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-${SHERPA_VERSION}.aar"
UPSTREAM_KOKORO="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"
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

# Try primary URL, then fallback URL
download_with_fallback() {
    local primary="$1" fallback="$2" dest="$3"
    if [ -f "$dest" ]; then
        echo "  ✓ $(basename "$dest") already exists — skipping"
        return 0
    fi
    echo "  ↓ Downloading $(basename "$dest")…"
    if curl -fL $RETRY "$primary" -o "$dest" 2>/dev/null; then
        return 0
    fi
    echo "  ⚠ Release asset not found, trying upstream…"
    curl -fL $RETRY "$fallback" -o "$dest" || {
        echo "  ✗ FAILED: $(basename "$dest")"
        rm -f "$dest"
        return 1
    }
}

# Build the upstream HuggingFace URL for a Piper voice
piper_hf_url() {
    local voice_id="$1"
    local lang_code="${voice_id%%_*}"
    local locale
    locale=$(echo "$voice_id" | sed 's/-.*//')
    local rest="${voice_id#"${locale}"-}"
    local quality="${rest##*-}"
    local name="${rest%-*}"
    echo "${HF_BASE}/${lang_code}/${locale}/${name}/${quality}/${voice_id}.onnx?download=true"
}

# ── 1. Sherpa-onnx AAR ──────────────────────────────────────────────────────

echo ""
echo "═══ Step 1/3: sherpa-onnx AAR ═══"
mkdir -p "$LIBS_DIR"
download_with_fallback \
    "$RELEASE_BASE/sherpa_onnx.aar" \
    "$UPSTREAM_AAR" \
    "$LIBS_DIR/sherpa_onnx.aar"

# ── 2. Kokoro model ─────────────────────────────────────────────────────────

echo ""
echo "═══ Step 2/3: Kokoro multi-lang model ═══"
mkdir -p "$KOKORO_DIR"

if [ -f "$KOKORO_DIR/model.onnx" ] && [ -f "$KOKORO_DIR/voices.bin" ] && [ -f "$KOKORO_DIR/tokens.txt" ] && [ -d "$KOKORO_DIR/espeak-ng-data" ]; then
    echo "  ✓ Kokoro model already present — skipping"
else
    echo "  ↓ Downloading Kokoro model (~120MB)…"
    if ! curl -fL $RETRY "$RELEASE_BASE/kokoro-multi-lang-v1_0.tar.bz2" -o kokoro-model.tar.bz2 2>/dev/null; then
        echo "  ⚠ Release asset not found, trying upstream…"
        curl -fL $RETRY "$UPSTREAM_KOKORO" -o kokoro-model.tar.bz2
    fi
    echo "  ↓ Extracting…"
    tar -xjf kokoro-model.tar.bz2 -C "$KOKORO_DIR" --strip-components=1
    rm -f kokoro-model.tar.bz2
    echo "  ✓ Kokoro model ready"
fi

echo "  Files:"
ls -lh "$KOKORO_DIR"/*.onnx "$KOKORO_DIR"/*.bin "$KOKORO_DIR"/*.txt 2>/dev/null | while read -r line; do echo "    $line"; done

# ── 3. Core Piper voices (bundled in APK) ────────────────────────────────────

echo ""
echo "═══ Step 3/3: Core Piper voices (bundled in APK) ═══"
mkdir -p "$PIPER_DIR"

# Shared tokens.txt — fallback: extract from upstream lessac package
if [ ! -f "$PIPER_DIR/tokens.txt" ]; then
    if ! download "$RELEASE_BASE/piper-tokens.txt" "$PIPER_DIR/tokens.txt" 2>/dev/null; then
        echo "  ⚠ Release asset not found, extracting tokens.txt from upstream…"
        curl -fL $RETRY \
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2" \
            -o piper-pkg.tar.bz2
        mkdir -p tmp-piper
        tar -xjf piper-pkg.tar.bz2 -C tmp-piper
        find tmp-piper -name "tokens.txt" -exec cp {} "$PIPER_DIR/tokens.txt" \;
        rm -rf tmp-piper piper-pkg.tar.bz2
    fi
else
    echo "  ✓ tokens.txt already exists — skipping"
fi

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
    DEST="$PIPER_DIR/${VOICE_ID}.onnx"
    FALLBACK_URL=$(piper_hf_url "$VOICE_ID")
    if download_with_fallback "$RELEASE_BASE/${VOICE_ID}.onnx" "$FALLBACK_URL" "$DEST"; then
        if [ -f "$DEST" ]; then
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
echo "  Non-bundled voices are downloaded by the app on demand."
echo "  Ready to build: ./gradlew assembleRelease"
echo "═══════════════════════════════════════════════"
