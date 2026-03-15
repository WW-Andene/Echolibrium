# Kyōkan v4

Notification reader for Android with multi-engine TTS and adaptive voice profiles.

## Naming
- **Echolibrium** — project/repository name
- **Kyōkan (共感)** — product name shown to users (means "empathy/resonance" in Japanese)
- **Package:** `com.echolibrium.kyokan` — bridges both names

## Features
- Reads notification text aloud (app name + title + body, configurable per app)
- **Three TTS engines:**
  - **Kokoro** — offline, 11 voices (American/British, Male/Female), ~120MB shared model
  - **Piper** — offline, 33 voices (English US/UK, French), per-voice download
  - **Orpheus** — cloud via DeepInfra API, 8 voices, lowest latency
- Voice profiles with pitch and speed controls
- Per-app rules: read mode + voice profile override per app
- Word replacement rules (find → replace before speaking)
- Do Not Disturb quiet hours
- On-device language detection (ML Kit)
- On-device translation (ML Kit, 13 languages)
- Language-based voice routing (route by detected language)
- Voice commands via wake word (repeat, stop, time, how long ago)
- Live logcat viewer with level/tag filtering
- Foreground service for background reliability
- Auto-start on boot
- Local crash logging
- Cloudflare Worker proxy for API key protection

## Build via GitHub Actions
1. Push this repo to GitHub
2. Actions → Build APK → Run workflow
3. Download the APK from Artifacts (`Kyokan-v4` artifact name)
4. Install on device and grant permissions

## Install
1. Install the APK (sideload — no Play Store yet)
2. Open Kyōkan → Follow the 3-step setup (restricted settings → battery → notification access)
3. Go to **Voices** tab → Download the Kokoro voice model (~120MB, one-time)
4. Optionally download Piper voices or enter a DeepInfra API key for Orpheus cloud voices

## Requirements
- Android 8.0+ (API 26+)
- arm64 device (arm64-v8a)
- Internet for initial model downloads and cloud TTS (Orpheus)

## Architecture
- Single-Activity + 5 Fragments (Home, Profiles, Apps, Rules, Logcat)
- sherpa-onnx for local TTS (Kokoro + Piper via ONNX Runtime)
- OkHttp for cloud TTS (DeepInfra Orpheus API)
- ML Kit for on-device language detection and translation
- EncryptedSharedPreferences for API key storage
- SharedPreferences (JSON) for profiles, rules, and settings
