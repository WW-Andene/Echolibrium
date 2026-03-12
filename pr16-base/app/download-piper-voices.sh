#!/usr/bin/env bash
#
# Downloads 9 Piper TTS voices from k2-fsa/sherpa-onnx releases and
# prepares them as APK assets.
#
# Layout:  app/src/main/assets/piper_voices/{voiceId}/
#            ├── {voiceId}.onnx
#            ├── {voiceId}.onnx.json
#            ├── tokens.txt
#            └── espeak-ng-data/   (shared copy — symlinked after first extract)
#
# Optimization: espeak-ng-data/ is identical across all en_* voices.
# We keep ONE full copy and use hardlinks for the rest (saves ~50MB in APK).
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ASSETS_DIR="$SCRIPT_DIR/src/main/assets/piper_voices"
TEMP_DIR="$SCRIPT_DIR/build/piper-download-tmp"
BASE_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

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

echo "=== Downloading ${#VOICES[@]} Piper voices ==="
ESPEAK_REF=""

for VOICE_ID in "${VOICES[@]}"; do
    ARCHIVE_NAME="vits-piper-${VOICE_ID}"
    TAR_FILE="$TEMP_DIR/${ARCHIVE_NAME}.tar.bz2"
    VOICE_DIR="$ASSETS_DIR/$VOICE_ID"

    if [ -d "$VOICE_DIR" ] && [ -f "$VOICE_DIR/${VOICE_ID}.onnx" ]; then
        echo "  [skip] $VOICE_ID (already exists)"
        continue
    fi

    echo "  [download] $VOICE_ID"
    curl -L --retry 3 --retry-delay 2 --fail -o "$TAR_FILE" \
        "$BASE_URL/${ARCHIVE_NAME}.tar.bz2"

    # Verify the download isn't a tiny error page
    FILE_SIZE=$(wc -c < "$TAR_FILE")
    if [ "$FILE_SIZE" -lt 1000 ]; then
        echo "  [ERROR] $VOICE_ID download too small (${FILE_SIZE} bytes) — URL may be invalid"
        rm -f "$TAR_FILE"
        exit 1
    fi

    echo "  [extract]  $VOICE_ID"
    mkdir -p "$VOICE_DIR"

    # Extract model, tokens, json
    tar xjf "$TAR_FILE" -C "$TEMP_DIR"
    EXTRACTED="$TEMP_DIR/$ARCHIVE_NAME"

    cp "$EXTRACTED/${VOICE_ID}.onnx" "$VOICE_DIR/"
    [ -f "$EXTRACTED/${VOICE_ID}.onnx.json" ] && \
        cp "$EXTRACTED/${VOICE_ID}.onnx.json" "$VOICE_DIR/"
    cp "$EXTRACTED/tokens.txt" "$VOICE_DIR/"

    # espeak-ng-data: keep one full copy, reuse for others
    if [ -z "$ESPEAK_REF" ]; then
        echo "  [espeak]   Keeping reference espeak-ng-data from $VOICE_ID"
        cp -r "$EXTRACTED/espeak-ng-data" "$VOICE_DIR/"

        # Strip non-English dicts to save space
        echo "  [strip]    Removing non-English espeak dicts..."
        BEFORE_SIZE=$(du -sm "$VOICE_DIR/espeak-ng-data" | cut -f1)
        for DICT_FILE in "$VOICE_DIR/espeak-ng-data/"*_dict; do
            BASENAME=$(basename "$DICT_FILE")
            if ! echo "$KEEP_DICTS" | grep -qw "$BASENAME"; then
                rm -f "$DICT_FILE"
            fi
        done
        AFTER_SIZE=$(du -sm "$VOICE_DIR/espeak-ng-data" | cut -f1)
        echo "  [strip]    espeak-ng-data: ${BEFORE_SIZE}MB → ${AFTER_SIZE}MB"

        ESPEAK_REF="$VOICE_DIR/espeak-ng-data"
    else
        echo "  [espeak]   Copying shared espeak-ng-data for $VOICE_ID"
        cp -r "$ESPEAK_REF" "$VOICE_DIR/"
    fi

    # Clean up extracted archive
    rm -rf "$EXTRACTED" "$TAR_FILE"
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
