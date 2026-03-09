# Kokoro Reader v2

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
3. Download APK from Artifacts
4. Install → Grant notification permission

## Requirements
- Android 8.0+
- SherpaTTS installed and set as default TTS engine
