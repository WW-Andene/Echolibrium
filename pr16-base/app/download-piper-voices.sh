#!/usr/bin/env bash
#
# Downloads 9 Piper TTS voices from this repo's tts-assets-v1 release
# and prepares them as APK assets.
#
# The release contains individual .onnx files (uploaded by upload-voices.yml)
# plus shared piper-tokens.txt and espeak-ng-data.tar.gz.
#
# Layout:  app/src/main/assets/piper_voices/{voiceId}/
#            ├── {voiceId}.onnx
#            ├── tokens.txt
#            └── espeak-ng-data/
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ASSETS_DIR="$SCRIPT_DIR/src/main/assets/piper_voices"
TEMP_DIR="$SCRIPT_DIR/build/piper-download-tmp"

# GitHub repo release URL
REPO_OWNER="${GITHUB_REPOSITORY:-WW-Andene/Echolibrium}"
RELEASE_TAG="tts-assets-v1"
BASE_URL="https://github.com/${REPO_OWNER}/releases/download/${RELEASE_TAG}"

# ── Voice list (9 voices: 6 en_US + 3 en_GB) ─────────────────────────────
VOICES=(
    "en_US-lessac-medium"
    "en_US-ljspeech-medium"
    "en_US-kristin-medium"
    "en_US-ryan-medium"
    "en_US-joe-medium"
    "en_US-bryce-medium"
    "en_GB-alba-medium"
    "en_GB-alan-medium"
    "en_GB-cori-medium"
)

# ── English-only espeak-ng dicts to keep (strip all others) ───────────────
KEEP_DICTS="en_dict intonations phondata phondata-extra phonindex phontab"

cleanup() { rm -rf "$TEMP_DIR"; }
trap cleanup EXIT

mkdir -p "$ASSETS_DIR" "$TEMP_DIR"

# ── Step 1: Download shared assets (tokens + espeak-ng-data) ─────────────
echo "=== Downloading shared Piper assets ==="

TOKENS_FILE="$TEMP_DIR/tokens.txt"
ESPEAK_ARCHIVE="$TEMP_DIR/espeak-ng-data.tar.gz"
ESPEAK_DIR="$TEMP_DIR/espeak-ng-data"

if [ ! -f "$TOKENS_FILE" ]; then
    echo "  [download] piper-tokens.txt"
    curl -fL --retry 3 --retry-delay 2 -o "$TOKENS_FILE" \
        "$BASE_URL/piper-tokens.txt"
    echo "  [ok]       tokens.txt ($(wc -c < "$TOKENS_FILE") bytes)"
fi

if [ ! -d "$ESPEAK_DIR" ]; then
    # Try repo release first, fall back to extracting from a k2-fsa Piper archive
    if curl -fL --retry 2 --retry-delay 2 -o "$ESPEAK_ARCHIVE" \
        "$BASE_URL/espeak-ng-data.tar.gz" 2>/dev/null; then
        echo "  [download] espeak-ng-data.tar.gz (from repo release)"
        tar xzf "$ESPEAK_ARCHIVE" -C "$TEMP_DIR"
        rm -f "$ESPEAK_ARCHIVE"
    else
        echo "  [fallback] espeak-ng-data not in release — extracting from k2-fsa lessac archive"
        FALLBACK_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2"
        FALLBACK_TAR="$TEMP_DIR/fallback-lessac.tar.bz2"
        curl -fL --retry 3 --retry-delay 2 -o "$FALLBACK_TAR" "$FALLBACK_URL"
        mkdir -p "$TEMP_DIR/fallback-extract"
        tar xjf "$FALLBACK_TAR" -C "$TEMP_DIR/fallback-extract"
        ESPEAK_SRC=$(find "$TEMP_DIR/fallback-extract" -type d -name "espeak-ng-data" | head -1)
        if [ -z "$ESPEAK_SRC" ]; then
            echo "  [ERROR] espeak-ng-data not found in fallback archive"
            exit 1
        fi
        cp -r "$ESPEAK_SRC" "$ESPEAK_DIR"
        # Also grab tokens.txt if we don't have it
        if [ ! -f "$TOKENS_FILE" ]; then
            TOKENS_SRC=$(find "$TEMP_DIR/fallback-extract" -name "tokens.txt" | head -1)
            [ -n "$TOKENS_SRC" ] && cp "$TOKENS_SRC" "$TOKENS_FILE"
        fi
        rm -rf "$TEMP_DIR/fallback-extract" "$FALLBACK_TAR"
    fi

    # Strip non-English dicts to save space
    echo "  [strip]    Removing non-English espeak dicts..."
    BEFORE_SIZE=$(du -sm "$ESPEAK_DIR" | cut -f1)
    for DICT_FILE in "$ESPEAK_DIR/"*_dict; do
        BASENAME=$(basename "$DICT_FILE")
        if ! echo "$KEEP_DICTS" | grep -qw "$BASENAME"; then
            rm -f "$DICT_FILE"
        fi
    done
    AFTER_SIZE=$(du -sm "$ESPEAK_DIR" | cut -f1)
    echo "  [strip]    espeak-ng-data: ${BEFORE_SIZE}MB → ${AFTER_SIZE}MB"
fi

# ── Step 2: Download each voice .onnx and assemble with shared assets ─────
echo ""
echo "=== Downloading ${#VOICES[@]} Piper voices ==="

for VOICE_ID in "${VOICES[@]}"; do
    VOICE_DIR="$ASSETS_DIR/$VOICE_ID"

    if [ -d "$VOICE_DIR" ] && [ -f "$VOICE_DIR/${VOICE_ID}.onnx" ]; then
        echo "  [skip] $VOICE_ID (already exists)"
        continue
    fi

    echo "  [download] $VOICE_ID"
    ONNX_FILE="$TEMP_DIR/${VOICE_ID}.onnx"
    curl -fL --retry 3 --retry-delay 2 -o "$ONNX_FILE" \
        "$BASE_URL/${VOICE_ID}.onnx"

    # Verify download
    FILE_SIZE=$(wc -c < "$ONNX_FILE")
    if [ "$FILE_SIZE" -lt 1000 ]; then
        echo "  [ERROR] $VOICE_ID too small (${FILE_SIZE} bytes) — asset may be missing from release"
        rm -f "$ONNX_FILE"
        exit 1
    fi
    echo "  [ok]       ${VOICE_ID}.onnx ($(du -h "$ONNX_FILE" | cut -f1))"

    # Assemble voice directory
    mkdir -p "$VOICE_DIR"
    mv "$ONNX_FILE" "$VOICE_DIR/"
    cp "$TOKENS_FILE" "$VOICE_DIR/tokens.txt"
    cp -r "$ESPEAK_DIR" "$VOICE_DIR/espeak-ng-data"
done

echo ""
echo "=== Summary ==="
TOTAL_SIZE=$(du -sm "$ASSETS_DIR" | cut -f1)
echo "  Voices:     ${#VOICES[@]}"
echo "  Assets dir: $ASSETS_DIR"
echo "  Total size: ${TOTAL_SIZE}MB"
echo ""
ls -la "$ASSETS_DIR"/
echo ""
echo "Done. Voices are ready as APK assets."
