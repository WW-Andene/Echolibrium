# Kyōkan Rebuild Plan: From PR #16 to Working Piper-Only App

## Why PR #16 Worked But Current State Doesn't

PR #16 (commit `0070652`) was the **last time the app had a clean, working architecture**. After that, ~35 commits tried to fix problems that kept compounding:

| Phase | Commits | What happened |
|-------|---------|---------------|
| PR #16 | `0070652` | Dual Kokoro+Piper, clean UI, working |
| PR #17-19 | `c4db387`→`ad6b1d4` | Bundled Kokoro model in APK, added voice commands, foreground service. **APK grew to ~400MB** |
| PR #42-44 | `d4081dc`→`f65c7da` | Voice download system, ORT optimization, CI upload workflow. Tried to fix APK size |
| PR #45-48 | `e3c3d9f`→`365f09b` | Download script rewrites (3 attempts), import fixes, fallback URL juggling |
| PR #49 | `c5de8f5` | Device-adaptive engine, filesystem extraction, SIGSEGV fixes, crash loop detection. **~15 commits just for engine stability** |
| This branch | `b562548`→`13a747d` | Stripped Kokoro entirely. espeak-ng-data missing → Piper can't init |

**The core problem**: Each fix added complexity on top of the previous one. The engine went from ~200 lines to ~1500 lines of defensive code, but the fundamental asset pipeline (what files the APK needs and where they come from) was never properly validated end-to-end.

**Why PR #16 worked**: It had a simple architecture — Kokoro was the primary engine loaded from assets, Piper voices were there as catalog entries, and everything was self-contained. No cross-process TTS, no filesystem extraction, no crash loops to detect.

---

## Current Feature Inventory (30 Kotlin files)

### Core Engine (must work first)
1. **SherpaEngine** — TTS synthesis via sherpa-onnx (currently Piper-only)
2. **AudioPipeline** — Background synthesis queue + AudioTrack playback
3. **TtsBridge** — Cross-process UI↔TTS communication

### Signal Analysis & Modulation
4. **SignalExtractor** — Pattern-match notification content → 70+ signal dimensions
5. **SignalMap** — Data class for all signal dimensions
6. **VoiceModulator** — Applies signals + mood to voice parameters
7. **MoodState** — 3D mood (valence/arousal/stability) with decay
8. **VoiceTransform** — Text transforms (gimmicks, fillers, breathiness)
9. **CommentaryPool** — Contextual filler/commentary injection rules

### Voice & Profiles
10. **PiperVoiceCatalog** — 80+ voice catalog with bundled/downloadable tiers
11. **PiperVoiceManager** — Voice download delegation
12. **VoiceProfile** — 13+ parameter profiles with presets

### Notification Processing
13. **NotificationReaderService** — Foreground service in :tts process
14. **VoiceCommandListener** — Speech recognition + wake word
15. **VoiceCommandHandler** — Command processing (repeat, stop, time, mood)

### UI
16. **MainActivity** — 4-tab container
17. **HomeFragment** — Status dashboard + controls
18. **ProfilesFragment** — Voice/profile editor
19. **AppsFragment** — Per-app rules
20. **RulesFragment** — Text replacement rules

### System Integration
21. **ReaderApplication** — Crash handling, HWUI safe mode
22. **CrashReportActivity** — Crash log display
23. **GitHubReporter** — Auto-report to GitHub Issues
24. **OemProtection** — Battery whitelist for 15+ OEMs
25. **BootReceiver** — Service restart on boot
26. **TtsCommandReceiver** — Cross-process command receiver

### Data
27. **AppRule** — Per-app rule data class
28. **AudioDsp** — 7-stage DSP effects pipeline
29. **PhonicAnalyzer** — PCM landmark detection

### Build
30. **VoiceDownloadManager** (referenced but not listed — handles on-demand downloads)

---

## Rebuild Steps

The strategy: **start from the PR #16 merge commit, bring features forward one layer at a time, and TEST each layer before adding the next.**

### Phase 0: Clean Baseline (PR #16 state)
**Goal**: Get the app running with Piper voices, no Kokoro, simplest possible engine.

1. **Reset SherpaEngine to minimal Piper-only init**
   - Strip all device profiling, crash recovery, exponential backoff
   - Strip all Kokoro code paths
   - Simple init: extract assets → create OfflineTts → done
   - Single process (no :tts separation yet)
   - ~200 lines max

2. **Fix the asset pipeline**
   - Ensure `download-models.sh` delivers: sherpa_onnx.aar, tokens.txt, espeak-ng-data/, 8 bundled .onnx voices
   - Verify all assets end up in the APK (check `aaptOptions` noCompress list)
   - Test: `unzip -l app-debug.apk | grep piper-models` should show tokens.txt, espeak-ng-data/*, and .onnx files

3. **Simplify AudioPipeline**
   - Remove cross-process TtsBridge dependency
   - Direct engine calls in same process
   - Queue → synthesize → play

4. **Test on device**: App should launch, engine should init, test button should speak.

### Phase 1: Voice Selection & Profiles
**Goal**: Users can pick voices and configure parameters.

5. **PiperVoiceCatalog + VoiceProfile working**
   - Voice grid in ProfilesFragment displays bundled voices
   - Selecting a voice loads it in SherpaEngine
   - Pitch/speed sliders affect synthesis

6. **On-demand voice downloads**
   - VoiceDownloadManager / PiperVoiceManager for non-bundled voices
   - Download from GitHub release, store in app filesDir

7. **Test on device**: Can switch voices, adjust parameters, download new voices.

### Phase 2: Notification Reading
**Goal**: App reads notifications aloud.

8. **NotificationReaderService (single process first)**
   - Listen for notifications
   - Extract text → AudioPipeline
   - Basic read modes (full/title/text)

9. **Per-app rules (AppsFragment)**
   - Enable/disable per package
   - Read mode per app

10. **Test on device**: Send test notifications, app reads them.

### Phase 3: Signal Analysis & Voice Modulation
**Goal**: Notifications sound emotionally appropriate.

11. **SignalExtractor + SignalMap**
    - Analyze notification content for emotional signals

12. **VoiceModulator + MoodState**
    - Apply signals to voice parameters
    - Mood tracking with decay

13. **VoiceTransform + CommentaryPool**
    - Text gimmicks, fillers, breathiness injection

14. **AudioDsp + PhonicAnalyzer**
    - Post-synthesis audio effects

15. **Test on device**: Different notification types should sound different.

### Phase 4: Hardening
**Goal**: Robust on all devices.

16. **Separate :tts process**
    - Move engine to :tts process
    - TtsBridge for cross-process communication
    - TtsCommandReceiver for commands

17. **Device-adaptive config**
    - MediaTek single-threaded
    - Filesystem extraction (avoid AssetManager SIGSEGV)
    - SoC detection

18. **Crash recovery**
    - ReaderApplication crash handler
    - CrashReportActivity
    - GitHubReporter
    - Crash loop detection with backoff

19. **OEM protection**
    - Battery whitelist for Xiaomi etc.
    - BootReceiver for service restart

### Phase 5: Voice Commands
**Goal**: Hands-free control.

20. **VoiceCommandListener + VoiceCommandHandler**
    - Wake word detection
    - Repeat, stop, time, mood commands

---

## Key Principle

**Each phase must be tested on the Xiaomi device before moving to the next.** The previous approach of stacking 35 commits without device validation is what created this mess. One working feature is worth more than ten theoretical ones.
