# Kokoro Reader v3

Notification reader for Android with full voice personality system.

## Features
- Reads full notification text (app + title + body)
- Voice profiles: pitch, speed, breathiness curve, stuttering, intonation
- 15 personality presets: Excited, Bored, Depressed, Flirty, Gentle, Happy, Hangry, Nervous, Whispery, Robot, Drunk, Elder, Child, Dramatic, Sarcastic
- Gimmicks: giggle · sigh · huh · mmm · woah · ugh · aww · gasp · yawn · hmm · laugh · tsk — each with frequency + position (start/mid/end/random)
- Voice picker filtered by gender (♀ Female / ♂ Male) and nationality (American / British)
- Button to open SherpaTTS directly for downloading new voices
- Per-app rules: mode + profile override per app
- Word replacement rules
- Do Not Disturb quiet hours

## Build via GitHub Actions
1. Push this repo to GitHub
2. Actions → Build APK → Run workflow
3. Download **both** APKs from Artifacts:
   - `KokoroReader-v3` — the main app
   - `SherpaTTS-arm64-v8a` — the TTS engine (required)
4. Install SherpaTTS first, then Kokoro Reader
5. Grant notification permission when prompted

## Install
1. Install **SherpaTTS** (`SherpaTTS-arm64-v8a.apk`) — this is the TTS engine that Kokoro Reader uses to speak
2. Install **Kokoro Reader** (`KokoroReader-v3.apk`)
3. Open Kokoro Reader → Grant notification listener permission
4. Go to **Profiles** tab → Download the Kokoro voice model (~120 MB, one-time)

## Requirements
- Android 8.0+ (arm64 device)
- SherpaTTS installed (bundled in build artifacts — see above)
