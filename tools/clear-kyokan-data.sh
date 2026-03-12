#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# Clears all Kyōkan/Echolibrium data from an Android device.
# Run via Termux or adb shell.
#
# What it removes:
#   - App data & cache (SharedPreferences, crash state, init flags)
#   - Extracted models (kokoro-model, piper-models)
#   - Log files (Kyokan/Logs)
#   - Downloaded voices
#   - Notification listener binding state
#
# What it does NOT remove:
#   - The APK itself (uninstall separately if needed)
# ─────────────────────────────────────────────────────────────────

PKG="com.kokoro.reader"

echo "═══ Kyōkan Data Cleaner ═══"
echo ""

# Force stop the app first
echo "Stopping $PKG..."
am force-stop "$PKG" 2>/dev/null || su -c "am force-stop $PKG" 2>/dev/null || true

# Clear app data (SharedPreferences, databases, cache, extracted models)
echo "Clearing app data..."
pm clear "$PKG" 2>/dev/null || su -c "pm clear $PKG" 2>/dev/null || {
    echo "  ⚠ pm clear failed — trying manual cleanup"
    # Manual fallback if pm clear doesn't work without root
    rm -rf "/data/data/$PKG/shared_prefs" 2>/dev/null
    rm -rf "/data/data/$PKG/cache" 2>/dev/null
    rm -rf "/data/data/$PKG/files" 2>/dev/null
    rm -rf "/data/data/$PKG/databases" 2>/dev/null
}

# Remove app-private external storage
APP_EXT="/storage/emulated/0/Android/data/$PKG"
if [ -d "$APP_EXT" ]; then
    echo "Removing $APP_EXT..."
    rm -rf "$APP_EXT"
fi

# Remove shared storage logs
SHARED_LOGS="/storage/emulated/0/Kyokan"
if [ -d "$SHARED_LOGS" ]; then
    echo "Removing $SHARED_LOGS..."
    rm -rf "$SHARED_LOGS"
fi

echo ""
echo "═══ Done ═══"
echo "All Kyōkan data cleared."
echo "If you want to uninstall the app too: pm uninstall $PKG"
