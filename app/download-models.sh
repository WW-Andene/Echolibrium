#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Downloads all TTS dependencies needed to build Kyōkan:
#   1. sherpa-onnx AAR (native TTS library)
#   2. Kokoro multi-lang model (30 voices, ~120MB)
#   3. Core Piper voices bundled in the APK (8 voices, ~480MB)
#
# Non-bundled Piper voices are downloaded on-demand by the app from
# GitHub Releases — see VoiceDownloadManager.kt and PiperVoiceCatalog.kt.
#
# This script is idempotent — it skips files that already exist.
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

# ── 3. Core Piper voices (bundled in APK) ────────────────────────────────────

echo ""
echo "═══ Step 3/3: Core Piper voices (bundled in APK) ═══"
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
echo "  Bundled voices: $SKIPPED already present, $DOWNLOADED downloaded, $FAILED failed (of $TOTAL total)"

# ── Summary ──────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════"
PIPER_COUNT=$(ls -1 "$PIPER_DIR"/*.onnx 2>/dev/null | wc -l)
echo "  AAR:    $(ls -lh "$LIBS_DIR/sherpa_onnx.aar" 2>/dev/null | awk '{print $5}' || echo 'MISSING')"
echo "  Kokoro: $(du -sh "$KOKORO_DIR" 2>/dev/null | cut -f1 || echo 'MISSING')"
echo "  Piper:  ${PIPER_COUNT} bundled voices ($(du -sh "$PIPER_DIR" 2>/dev/null | cut -f1 || echo '0'))"
echo ""
echo "  Non-bundled voices are downloaded by the app on demand."
echo "  Ready to build: ./gradlew assembleRelease"
echo "═══════════════════════════════════════════════"
