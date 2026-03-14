# Echolibrium / Kyokan — Full App Audit Report

**Date:** 2026-03-14
**Scope:** All 25 Kotlin source files, 6 XML layouts, resources, manifest, build config
**App:** Android notification reader with TTS (Kokoro + Piper + Orpheus)

---

## Executive Summary

The app is functional with a cohesive dark-terminal aesthetic and well-structured TTS pipeline. The codebase is compact (25 files, ~4,000 LOC) and ships with three TTS engines. Key areas needing attention: **performance** (full UI rebuilds on every state change), **resource externalization** (100+ hardcoded strings/colors), and **dead code cleanup**.

**Critical fixes applied in this audit:** 5
**Severity breakdown:** 5 Critical, 12 Major, 18 Minor, 10 Informational

---

## A. Architecture & Project Structure

| # | Severity | Finding |
|---|----------|---------|
| A1 | Info | Single-activity + manual tab navigation (no Jetpack Navigation). Functional but loses backstack, deep linking, animations. |
| A2 | Info | Flat package — all 25 files in `com.echolibrium.kyokan`. Fine at this scale. |
| A3 | Minor | Fragment instances recreated on every tab click (`selectTab` → `loadFragment` → new instance). No caching or `FragmentTransaction.addToBackStack`. |
| A4 | Minor | Heavy singleton pattern (9 `object` declarations). Creates tight coupling but acceptable for this app size. |

---

## B. State Management & Data Flow

| # | Severity | Finding |
|---|----------|---------|
| B1 | **Critical** | `NotificationReaderService.dailyCount` and `lastCountDay` — declared but **never used**. Dead code. **FIXED** |
| B2 | **Major** | `swipedKeys` is populated in `onNotificationRemoved` but **never checked** in `onNotificationPosted`. The "skip swiped notifications" feature is incomplete — the pref `notif_skip_swiped` adds keys to the set but they're never consulted to skip future reads. **FIXED** |
| B3 | Major | `AudioPipeline.onSynthesisError` is a single callback slot overwritten in `ProfilesFragment.setupButtons()`. Only the last subscriber receives events. |
| B4 | Minor | Profiles stored as JSON string in SharedPreferences with no schema versioning or migration. Adding new fields could silently lose data. |
| B5 | Minor | `VoiceCommandListener.wakeWord` = profile name. Changing profile name changes the wake word with no notification to user. |

---

## C. Networking & API

| # | Severity | Finding |
|---|----------|---------|
| C1 | Major | `DownloadUtil` uses `HttpURLConnection` while OkHttp is already a dependency. Inconsistent HTTP stacks — DownloadUtil for model downloads, OkHttp for cloud TTS. |
| C2 | Major | No download integrity verification. Downloaded `.onnx` models are only validated by size > 1024 bytes. A corrupted or truncated download would pass validation and crash at runtime in native sherpa-onnx. |
| C3 | Minor | HuggingFace URLs (`huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/...`) are hardcoded. URL structure changes upstream would break all Piper downloads. |
| C4 | Info | Proxy URL still hardcoded in BuildConfig (`PROXY_BASE_URL`). Proxy removal plan exists but is not yet executed. |

---

## D. Security

| # | Severity | Finding |
|---|----------|---------|
| D1 | Good | `EncryptedSharedPreferences` via `SecureKeyStore` for API key storage with AES256-GCM. |
| D2 | Good | Network security config enforces HTTPS-only. |
| D3 | Good | Path traversal guard in `DownloadUtil.extractTarBz2` validates canonical paths. |
| D4 | Good | ProGuard enabled for release builds. |
| D5 | Major | `security-crypto:1.1.0-alpha06` is an **alpha** dependency. Alpha APIs may change or have undiscovered bugs. The stable `1.0.0` release exists. |
| D6 | Minor | `DEEPINFRA_API_KEY` in debug BuildConfig can be extracted from debug APKs. Release builds correctly set it to empty string. |

---

## E. Performance

| # | Severity | Finding |
|---|----------|---------|
| E1 | **Critical** | `ProfilesFragment.renderVoiceGrid()` rebuilds the **entire** voice grid (3 engine sections × ~50 voice cards) from scratch on every state change. During downloads, this runs every 1 second via `startDownloadRefresh()`. Each call creates dozens of `View`, `LinearLayout`, `TextView`, and `GradientDrawable` objects. **Not fixed** — requires RecyclerView migration (large scope). |
| E2 | Major | `SherpaEngine` — all methods `@Synchronized` on the same monitor. Kokoro synthesis blocks Piper initialization and vice versa. A slow Piper model load (~seconds) blocks all Kokoro TTS. |
| E3 | Major | `AppsFragment.renderRules()` rebuilds all rows on every search keystroke. With 100+ installed apps, this creates significant GC pressure. |
| E4 | Minor | `detectLanguage` in `NotificationReaderService` blocks the single executor thread up to 500ms. Combined with translation (up to 5s), a burst of notifications queues deeply. |
| E5 | Minor | `android:largeHeap="true"` in manifest — may mask real memory issues from E1/E3 view churn. |

---

## F. Error Handling

| # | Severity | Finding |
|---|----------|---------|
| F1 | Major | `VoiceProfile.loadAll` and `AppRule.loadAll` silently return empty list on JSON parse failure. A corrupted preference could wipe all user profiles/rules with no warning. |
| F2 | Major | Download failures only log to logcat. No user-visible error message when Piper/Kokoro downloads fail (network error, disk full, etc.). |
| F3 | Minor | Empty catch blocks in `SherpaEngine.release()` and `AudioTrack` cleanup — acceptable for teardown but could hide unexpected failures. |
| F4 | Minor | `VoiceDownloadManager.updateState(State.ERROR)` is called **before** `errorMessage` is set in the success-but-incomplete branch (lines 79-81). Race condition if `stateCallback` reads `errorMessage`. **FIXED** |

---

## G. UI/UX & Layouts

| # | Severity | Finding |
|---|----------|---------|
| G1 | **Critical** | `fragment_home.xml` line 30 displays **"v3.0 . Kokoro + Piper"** but `build.gradle` has `versionName "4.0"`. Stale version string. **FIXED** |
| G2 | **Critical** | Stop Speaking button (`btn_stop`) only calls `NotificationReaderService.instance?.stopSpeaking()`. If the service isn't running (user tested voices without notification access), the button silently does nothing. **FIXED** |
| G3 | Major | ~100+ hardcoded strings across all 6 layouts. App is not localizable. All tab labels, section headers, button text, toggle labels, hints should use `@string/` resources. |
| G4 | Major | ~15+ hardcoded hex color values not in `colors.xml`. A theme change requires editing every layout file. |
| G5 | Major | No accessibility on SeekBars (pitch, speed, DND time). No `contentDescription`, no `labelFor` attributes. |
| G6 | Minor | DayNight parent theme is pointless — app hardcodes dark colors everywhere. Should use `Theme.MaterialComponents.NoActionBar` directly. |
| G7 | Minor | `fragment_logcat.xml` uses a single `TextView` in `ScrollView` for log output — will lag with many lines. |
| G8 | Minor | `TtsAliveService` notification uses system icon `android.R.drawable.ic_btn_speak_now` — should use a custom app icon. |

---

## H. Code Quality & Consistency

| # | Severity | Finding |
|---|----------|---------|
| H1 | Major | Raw `Thread` usage for all background work (downloads, app loading). Should use coroutines or WorkManager for proper lifecycle awareness and cancellation. |
| H2 | Minor | Two separate `State` enums (`VoiceDownloadManager.State` and `PiperDownloadManager.State`) are identical — should be shared. |
| H3 | Minor | Inconsistent callback patterns: `VoiceDownloadManager` uses `onProgress(cb)` / `onStateChange(cb)` setter functions; `PiperDownloadManager` uses `var onProgress` / `var onStateChange` public properties. |
| H4 | Minor | Semicolons joining statements on one line (ProfilesFragment:28-29, 81-82) — Kotlin anti-pattern, reduces readability. |
| H5 | Minor | Import wildcards (`android.view.*`, `android.widget.*`) — minor, but explicit imports are preferred. |
| H6 | Info | `NotificationTranslator.translators` is a plain `mutableMapOf` with `synchronized` access while `readyModels` is a plain `mutableSetOf` without synchronization — potential concurrent access issue. |

---

## I. Build Config & Dependencies

| # | Severity | Finding |
|---|----------|---------|
| I1 | Major | No test dependencies declared. No JUnit, no Espresso, no testing infrastructure. |
| I2 | Minor | `kotlin-stdlib:1.9.22` pinned explicitly — usually managed by Kotlin Gradle plugin. Can cause version conflicts. |
| I3 | Minor | `pickFirst '**/libonnxruntime.so'` — if sherpa-onnx and onnxruntime-android ship different ORT versions, this silently picks one and may crash at runtime. |
| I4 | Info | Dependency versions are dated but functional (`core-ktx:1.12.0`, `appcompat:1.6.1`, `fragment-ktx:1.6.2`). |

---

## J. Design & Aesthetics

| # | Severity | Finding |
|---|----------|---------|
| J1 | Good | Cohesive dark-terminal aesthetic. Black background (#0d0d0d), dark surface (#111111), green accent (#00ff88). Consistent across all screens. |
| J2 | Good | Color-coded engine sections: orange (Orpheus), cyan (Kokoro), light blue (Piper). Clear visual hierarchy. |
| J3 | Good | Gender-coded voice icons (♀ pink, ♂ blue, ◆ neutral). Consistent across all three engines. |
| J4 | Good | Monospace typography with letter-spacing on section headers. Terminal/hacker aesthetic is intentional and well-executed. |
| J5 | Good | Collapsible sections with ▸/▾ indicators and proper accessibility announcements. |
| J6 | Minor | Profile cards use `setBackgroundColor` (no corner radius or elevation) while voice cards use `GradientDrawable` with rounded corners. Inconsistent card treatment. |
| J7 | Minor | "STOP SPEAKING" button emoji (⏹) may not render on all devices. |
| J8 | Info | System notification icon (`ic_btn_speak_now`) breaks the visual identity. Custom icon recommended. |

---

## Fixes Applied

### Fix 1: Version string mismatch (G1 — Critical)
`fragment_home.xml` → Updated "v3.0" to "v4.0"

### Fix 2: Dead code removal (B1 — Critical)
`NotificationReaderService.kt` → Removed unused `dailyCount` and `lastCountDay` fields

### Fix 3: swipedKeys feature completion (B2 — Major)
`NotificationReaderService.kt` → Added `swipedKeys` check in `onNotificationPosted` to actually skip swiped notifications

### Fix 4: Stop button fix (G2 — Critical)
`ProfilesFragment.kt` → Stop button now also calls `AudioPipeline.stop()` directly

### Fix 5: errorMessage race fix (F4 — Minor)
`VoiceDownloadManager.kt` → Set `errorMessage` before `updateState(State.ERROR)`

---

## Recommended Follow-ups (Not in Scope)

1. **RecyclerView migration** for voice grid and app list — highest performance impact
2. **String resource externalization** — required for localization
3. **Color resource consolidation** — define all colors in `colors.xml`
4. **Upgrade `security-crypto`** to stable `1.0.0` (or accept alpha risk)
5. **Add download integrity checks** — SHA256 verification for model files
6. **Migrate to coroutines** from raw Threads for proper cancellation
7. **Add basic unit tests** — at minimum for VoiceProfile/AppRule serialization
