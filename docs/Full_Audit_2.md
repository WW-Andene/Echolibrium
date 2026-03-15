# FULL AUDIT 2 — Kyōkan (Echolibrium)

**Branch:** `claude/phase0-fixes`  
**Date:** 2026-03-15  
**Auditor:** Claude Opus 4.6 — Full app-audit + design-aesthetic-audit + scope-context  
**Files read:** 88/88 (100% coverage — every Kotlin, XML, Gradle, Python, JS, shell, proguard file)

---

## §0 — APP CONTEXT

| Field | Value |
|-------|-------|
| **App name** | Kyōkan (repo: Echolibrium) |
| **Package** | `com.echolibrium.kyokan` |
| **Platform** | Android native (Kotlin) |
| **Min SDK / Target** | 26 (Android 8.0) / 35 |
| **Architecture** | Single Activity + 5 Fragments, manual DI (AppContainer), SharedPreferences persistence |
| **TTS engines** | Kokoro (local, SherpaONNX), Piper (local, SherpaONNX), Orpheus (cloud, DeepInfra) |
| **Key deps** | sherpa-onnx AAR, ORT 1.17.0, ML Kit (translate + langid), OkHttp 4.12, commons-compress 1.26.2 |
| **Lines of Kotlin** | ~4,800 across 38 files |
| **Lines of XML** | ~1,100 across 17 resource files |
| **External systems** | DeepInfra API, Cloudflare Worker proxy, GitHub Releases (model hosting) |

---

## SEVERITY LEGEND

| Level | Meaning |
|-------|---------|
| 🔴 **CRITICAL** | Crash, data loss, security hole, or fundamentally broken behavior |
| 🟠 **HIGH** | Significant bug, incorrect behavior visible to users, or major inconsistency |
| 🟡 **MEDIUM** | Quality issue, minor bug, inconsistency, or maintainability concern |
| 🟢 **LOW** | Nitpick, style issue, optimization opportunity, or future consideration |

---

## FINDINGS SUMMARY

| Category | 🔴 | 🟠 | 🟡 | 🟢 | Total |
|----------|-----|-----|-----|-----|-------|
| A — Domain Logic & Correctness | 0 | 2 | 2 | 1 | 5 |
| B — State & Data Integrity | 3 | 3 | 3 | 1 | 10 |
| C — Security & Trust | 0 | 2 | 2 | 2 | 6 |
| D — Performance | 0 | 2 | 4 | 2 | 8 |
| E — Visual Design & Tokens | 1 | 3 | 3 | 1 | 8 |
| F — UX & Information Architecture | 0 | 3 | 3 | 1 | 7 |
| G — Accessibility | 0 | 1 | 2 | 1 | 4 |
| I — Code Quality & Architecture | 0 | 3 | 6 | 3 | 12 |
| K — AI/LLM Integration | 0 | 1 | 1 | 1 | 3 |
| L — Standardization & Polish | 0 | 2 | 4 | 1 | 7 |
| N — Internationalization | 0 | 1 | 2 | 1 | 4 |
| O — Projections | 0 | 1 | 2 | 1 | 4 |
| **TOTAL** | **4** | **24** | **34** | **16** | **78** |

---

## CATEGORY A — DOMAIN LOGIC & CORRECTNESS

### A-01 🟠 CloudTtsEngine dead variable `url` never used
**File:** `CloudTtsEngine.kt:89`  
**Finding:** The variable `val url = proxy ?: DIRECT_URL` is assigned but never referenced. The code below it constructs `urlsToTry` independently using `proxy` and `DIRECT_URL` directly.  
**Impact:** Dead code that misleads readers about the control flow.  
**Fix:** Remove `val url = proxy ?: DIRECT_URL` (line 89).

### A-02 🟠 LanguageRoutingDelegate spinner fires on programmatic setSelection
**File:** `LanguageRoutingDelegate.kt:108-114`  
**Finding:** `setupProfileSpinner()` sets an `onItemSelectedListener` and then calls `spinner.setSelection(idx)`. The listener fires immediately, writing the current value back to SharedPreferences. This is harmless on first setup but during `refreshSpinner()` (called when profiles change), it can overwrite a legitimately saved profile ID with the default.  
**Impact:** Silent preference corruption during profile changes.  
**Fix:** Use the `SpinnerUtil.onItemSelectedSkipFirst()` extension that already exists in the codebase but is never used (see I-01).

### A-03 🟡 AudioPipeline crossfade stores tail BEFORE applying previous crossfade
**File:** `AudioPipeline.kt:218-224`  
**Finding:** `applyCrossfade()` saves `prevTail` from the current audio samples before the crossfade from the previous tail is applied. This means stored tails include crossfade artifacts, which compound over consecutive plays.  
**Impact:** Subtle audio artifacts over long playback sessions.  
**Fix:** Move the `prevTail` assignment to after the crossfade application block.

### A-04 🟡 VoiceCommandListener scheduleRestart increments errors THEN reads count for backoff
**File:** `VoiceCommandListener.kt:114-125`  
**Finding:** `consecutiveErrors.incrementAndGet()` is called at the top of the error branch, then `consecutiveErrors.get()` is called again later to compute backoff delay. Between these two calls, the value could theoretically change from another thread. In practice, `scheduleRestart` is only called from the main thread via handler, so this is safe but the pattern is fragile.  
**Impact:** Theoretical race. Negligible in practice.  
**Fix:** Store the incremented value: `val errCount = consecutiveErrors.incrementAndGet()` and use it consistently.

### A-05 🟢 DND time display uses 24-hour format regardless of locale
**File:** `HomeFragment.kt` (via `getString(R.string.silence_from, h)`)  
**Finding:** `silence_from` string uses `%02d:00` format, always showing 24-hour time. US locale users expect 12-hour AM/PM format.  
**Impact:** Minor UX confusion for US users.  
**Fix:** Use `DateFormat.getTimeInstance(DateFormat.SHORT)` or format based on locale.

---

## CATEGORY B — STATE MANAGEMENT & DATA INTEGRITY

### B-01 🔴 ~~Dual source of truth for current profile (Fragment vs ViewModel)~~ ✅ FIXED
**File:** `ProfilesFragment.kt:28,33` + `ProfilesViewModel.kt`  
**Finding:** `ProfilesFragment` maintains `var currentProfile = VoiceProfile()` as a local mutable variable AND observes `viewModel.currentProfile` via LiveData. Voice card clicks modify only the fragment's local `currentProfile` (e.g., `currentProfile = currentProfile.copy(voiceName = v.id)`), but this change is NOT propagated to the ViewModel until the user explicitly clicks "Save". If the fragment is recreated (rotation, process death), all unsaved voice/pitch/speed changes are silently lost. The ViewModel observer in `onViewCreated` then overwrites the fragment's `currentProfile` with the ViewModel's stale version.  
**Impact:** User loses unsaved profile edits on any configuration change. The two states can diverge silently.  
**Fix applied:** Replaced fragment-local `var currentProfile` with a read-only `val` getter delegating to `viewModel.currentProfile.value`. All 4 mutation sites (Kokoro voice click, Piper voice click, cloud voice select, loadProfileToUI) now route through `viewModel.updateCurrentProfile()`. ViewModel survives rotation natively. Commit `e50bba2`.

### B-02 🔴 ~~MainActivity does not restore selected tab after rotation~~ ✅ FIXED
**File:** `MainActivity.kt:39-40`  
**Finding:** `if (savedInstanceState == null) selectTab(R.id.nav_home)` — on rotation, `savedInstanceState` is non-null, so `selectTab` is never called. The `selectedTabId` field resets to `R.id.nav_home` (the default). FragmentManager restores the last visible fragment, but the bottom nav indicators are never updated — all tabs show inactive color, and `selectedTabId` is wrong (says Home when user may be on Rules tab).  
**Impact:** After rotation: bottom nav shows no active tab (all inactive color), or shows Home as active while displaying a different fragment. Tab state is broken.  
**Fix applied:** Added `onSaveInstanceState()` that persists `selectedTabId` to Bundle. `onCreate` now always calls `selectTab(restoredTabId)` — both on fresh start (defaults to Home) and on rotation (restores saved tab). Bottom nav indicators and fragment are always in sync. Commit `e50bba2`.

### B-03 🔴 ~~Fragment cache in MainActivity holds stale references after recreation~~ ✅ FIXED
**File:** `MainActivity.kt:18`  
**Finding:** `private val fragmentCache = mutableMapOf<Int, Fragment>()` — this map is a property of the Activity instance. After rotation, a new Activity is created with an empty cache. The old cache (and its fragment references) are lost. However, FragmentManager independently restores fragments. The cache and FragmentManager are out of sync. When the user taps a tab, a NEW fragment is created (cache miss), but the FragmentManager may still have the OLD restored fragment attached. The `replace()` call will work but the old restored fragment is now leaked (still in the FragmentManager's back state).  
**Impact:** Fragment duplication, potential memory leak, inconsistent state.  
**Fix applied:** Replaced `fragmentCache` map with `FragmentManager.findFragmentByTag()`. Each tab now has a stable string tag (e.g., `"frag_home"`, `"frag_profiles"`). `selectTab()` calls `findFragmentByTag(tag)` first — if the FM has a restored instance from rotation, it reuses it. Only creates a new fragment on cache miss. `loadFragment()` now passes the tag to `replace(containerId, fragment, tag)`. Eliminates stale references, duplication, and leaks. Commit `e50bba2`.

### B-04 🟠 VoiceProfile.saveAll and AppRule.saveAll use commit() blocking UI thread
**File:** `VoiceProfile.kt:38`, `AppRule.kt:14`  
**Finding:** Both use `prefs.edit()...commit()` which performs synchronous disk I/O on the calling thread (always the UI thread). With many profiles or app rules, this can cause visible jank or ANR.  
**Impact:** UI freeze during save operations, especially on slower devices.  
**Fix:** Replace `commit()` with `apply()` for async writes. Both methods are called from UI contexts where the immediate return value of `commit()` is never checked.

### B-05 🟠 ~~ProfilesFragment.onSaveInstanceState does not save currentProfile edits~~ ✅ FIXED
**File:** `ProfilesFragment.kt:85-89`  
**Finding:** `onSaveInstanceState` saves `genderFilter`, `languageFilter`, and `activeProfileId`, but NOT the in-progress `currentProfile` (which may have unsaved voice, pitch, speed changes). After rotation, the profile reverts to the last saved state.  
**Impact:** Unsaved edits silently lost on rotation.  
**Fix applied:** `onSaveInstanceState` now serializes `currentProfile.toJson().toString()` to the Bundle under key `"currentProfileJson"`. `onViewCreated` restores it via `VoiceProfile.fromJson()` and feeds it back to the ViewModel with `viewModel.updateCurrentProfile(restored)`. Survives both rotation and process death. Commit `e50bba2`.

### B-06 🟠 Silent data loss on JSON parse failure
**File:** `VoiceProfile.kt:50-55`, `AppRule.kt:21-27`  
**Finding:** If `voice_profiles` or `app_rules` JSON is corrupted, both `loadAll()` methods log an error and return `mutableListOf()`. All user profiles and rules silently disappear. No user notification, no recovery attempt, no backup.  
**Impact:** One corrupted byte in SharedPreferences silently wipes all user configuration.  
**Fix:** Show a user-visible error (Toast/Snackbar), attempt to salvage valid entries from partially corrupted JSON, and/or keep a backup of the last known-good JSON before overwriting.

### B-07 🟡 NotificationReaderService.instance static access without synchronization
**File:** `NotificationReaderService.kt:47`  
**Finding:** `companion object { var instance: NotificationReaderService? = null }` is read from fragment UI threads and written from the service binder thread. No `@Volatile` annotation and no synchronization. The JVM memory model doesn't guarantee visibility across threads without volatile or synchronized.  
**Impact:** Fragments could see a stale `null` value for `instance` and fall back to creating their own AudioPipeline instead of using the service.  
**Fix:** Add `@Volatile` to `instance`.

### B-08 🟡 LanguageRoutingDelegate translateCodes/translateNames filtering inconsistency
**File:** `LanguageRoutingDelegate.kt:15-16`  
**Finding:** `translateCodes` filters by `{ it.isNotEmpty() }` (removing the "" entry). `translateNames` filters by `{ it != "Off (no translation)" }`. These two lists are expected to be parallel (same indices). If the LANGUAGES map changes and a non-empty code has the display name "Off (no translation)", the lists would be misaligned.  
**Impact:** Fragile coupling. Currently correct but breaks if LANGUAGES map changes.  
**Fix:** Filter both lists from the same entries: `LANGUAGES.entries.filter { it.key.isNotEmpty() }.let { entries -> codes = entries.map { it.key }; names = entries.map { it.value } }`.

### B-09 🟡 Word rules saved on every keystroke
**File:** `WordRulesDelegate.kt:39`  
**Finding:** `editText` attaches a `TextWatcher` that calls `saveRules()` in `afterTextChanged` — on every single character typed. This triggers a SharedPreferences write per keystroke per EditText.  
**Impact:** Excessive disk I/O. With 10 rules visible, typing causes 10+ write operations per second.  
**Fix:** Debounce saves (e.g., 500ms after last keystroke), or save only on focus loss / fragment pause.

### B-10 🟢 No data export/backup mechanism
**Finding:** All user data (profiles, app rules, word rules, preferences) is stored in SharedPreferences with no export, import, or backup capability. Uninstalling the app or clearing data destroys everything permanently.  
**Impact:** Users cannot migrate data to a new device or recover from data loss.  
**Fix:** Add export/import functionality (JSON file in user-accessible storage or share intent).

---

## CATEGORY C — SECURITY & TRUST

### C-01 🟠 No input length limit on direct DeepInfra API calls
**File:** `CloudTtsEngine.kt`  
**Finding:** The Cloudflare Worker proxy enforces `MAX_INPUT_LENGTH = 2000` characters. But when the app falls back to direct DeepInfra API calls (with user's own key), there is no character limit. A very long notification (e.g., a full email body) could generate excessive API costs.  
**Impact:** Unexpected API charges for users with their own DeepInfra key.  
**Fix:** Add `text.take(2000)` or a configurable max length before sending to any endpoint.

### C-02 🟠 Proxy worker rate limiting is per-instance, ineffective at scale
**File:** `proxy/worker.js:3-7`  
**Finding:** The rate limiter uses an in-memory `Map` that resets when the worker instance restarts (which Cloudflare does frequently). Multiple worker instances don't share state. Under load, each instance tracks its own counts independently.  
**Impact:** Rate limiting is essentially decorative. A determined attacker can exhaust API quota.  
**Fix:** Use Cloudflare Workers KV or Cloudflare Rate Limiting rules for durable cross-instance rate limiting.

### C-03 🟡 BuildConfig.DEEPINFRA_API_KEY in debug builds
**File:** `app/build.gradle:23`  
**Finding:** Debug builds read `DEEPINFRA_API_KEY` from `local.properties` and compile it into `BuildConfig`. If a debug APK is distributed (e.g., via testing), the key is extractable via APK decompilation.  
**Impact:** Potential API key exposure if debug builds leak.  
**Fix:** Consider using the proxy exclusively in non-development contexts. Document that debug APKs should never be distributed.

### C-04 🟡 No notification content sanitization before cloud TTS
**File:** `NotificationReaderService.kt:109`  
**Finding:** Notification text is sent verbatim to DeepInfra for cloud TTS synthesis. No sanitization or filtering of potentially sensitive content (passwords, 2FA codes, financial data).  
**Impact:** Sensitive notification content is transmitted to a third-party server. The privacy dialog warns about this, but users may not fully understand the implications.  
**Fix:** Consider filtering known sensitive patterns (OTP codes, credit card numbers) before cloud TTS, or add a per-app "local only" override.

### C-05 🟢 BootReceiver SDK 31+ foreground service restriction handled
**File:** `BootReceiver.kt:13-18`  
**Finding:** Correctly catches `ForegroundServiceStartNotAllowedException` on SDK 31+. Good.  
**Impact:** None — correctly implemented.

### C-06 🟢 ProGuard rules comprehensive but could add OkHttp
**File:** `app/proguard-rules.pro`  
**Finding:** Keep rules cover sherpa-onnx, VoiceProfile, AppRule, fragments, enums, ONNX Runtime, ML Kit. OkHttp is not explicitly kept, but OkHttp 4.12 ships with its own consumer ProGuard rules that handle this automatically.  
**Impact:** None currently — works correctly.

---

## CATEGORY D — PERFORMANCE

### D-01 🟠 No AudioFocus handling — TTS overlaps with calls, music, navigation
**File:** `AudioPipeline.kt`  
**Finding:** `playPcm()` creates an AudioTrack and plays immediately without requesting audio focus. Android's audio focus system ensures only one app plays prominently at a time. Without requesting focus, Kyōkan's TTS speech will overlap with phone calls, music, turn-by-turn navigation, and other apps.  
**Impact:** Notification speech plays over phone calls and other audio, disrupting the user.  
**Fix:** Request `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` before playing, release focus after. Abandon playback if focus is denied.

### D-02 🟠 LanguageRoutingDelegate parses all profiles 26+ times on setup
**File:** `LanguageRoutingDelegate.kt:66-84`  
**Finding:** `setup()` creates ~13 voice routing rows + ~13 translation rows. Each routing row's `setupProfileSpinner()` calls `VoiceProfile.loadAll(prefs)` which parses the full `voice_profiles` JSON string. With 13 languages, that's 13+ JSON parse operations of the same data, on the UI thread, during fragment creation.  
**Impact:** Visible lag when opening the Rules tab, especially with many profiles.  
**Fix:** Parse profiles once at the top of `setup()` and pass the list to all rows.

### D-03 🟡 WordRulesDelegate rebuilds all views on every change
**File:** `WordRulesDelegate.kt:44`  
**Finding:** `renderRules()` calls `container.removeAllViews()` and rebuilds ALL rule rows from scratch, including creating new EditText instances. Every text change (via TextWatcher) → `saveRules()` → could trigger re-render if not careful. Currently re-renders only on add/delete, but the approach doesn't scale.  
**Impact:** Jank with many word rules (20+). EditText focus loss on re-render.  
**Fix:** Use RecyclerView with DiffUtil (same pattern as VoiceGridAdapter).

### D-04 🟡 LogcatFragment rebuilds full SpannableStringBuilder on every refresh
**File:** `LogcatFragment.kt:222-253`  
**Finding:** `refreshDisplay()` creates a new `SpannableStringBuilder`, iterates up to 500 LogLines, appends text with color spans, then sets `tvLog.text = ssb`. This creates a new Spannable object every 200ms during active logging.  
**Impact:** UI jank during heavy logging, especially with text selection enabled (`android:textIsSelectable="true"`).  
**Fix:** Use RecyclerView for log display, or at minimum reuse the SpannableStringBuilder.

### D-05 🟡 VoiceGridItem lambdas defeat DiffUtil optimization
**File:** `VoiceGridAdapter.kt:70-84`  
**Finding:** `VoiceGridItem.Card` and `VoiceGridItem.Header` store `@Transient` lambda fields (`onClick`, `onAction`). These are excluded from serialization but ARE included in `data class equals()`. Since lambdas are compared by reference (and new lambdas are created on every `renderVoiceGrid()` call), `areContentsTheSame()` always returns `false` for every item, causing full rebind of every visible card on every render.  
**Impact:** DiffUtil provides correct item-level stability (no flicker) but no content-level optimization. Every card is rebound on every update.  
**Fix:** Override `equals()`/`hashCode()` to exclude lambda fields, or use a stable callback interface.

### D-06 🟡 SherpaEngine.lastSampleRate written under different locks, read with none
**File:** `SherpaEngine.kt:22`  
**Finding:** `lastSampleRate` is written inside `synchronized(kokoroLock)` during Kokoro synthesis and inside `synchronized(piperLock)` during Piper synthesis. It's read from AudioPipeline without any synchronization.  
**Impact:** Theoretical data race on Int write/read. Practically harmless on ARM64 (atomic int writes) but technically undefined behavior per JMM.  
**Fix:** Add `@Volatile` annotation to `lastSampleRate`.

### D-07 🟢 MODE_STATIC vs MODE_STREAM threshold is well-calibrated
**File:** `AudioPipeline.kt:11`  
**Finding:** `STREAM_MODE_THRESHOLD_BYTES = 256 * 1024` — audio larger than 256KB uses streaming mode. This is a good threshold: typical short notifications are under 256KB, long ones stream. Good.

### D-08 🟢 Download resume support is well-implemented
**File:** `DownloadUtil.kt:30-60`  
**Finding:** HTTP Range header resume, redirect following, retry with backoff, path traversal guard in tar extraction. Solid implementation.

---

## CATEGORY E — VISUAL DESIGN & TOKENS

### E-01 🔴 ~~item_app_rule.xml uses hardcoded dark-only colors — completely broken in light mode~~ ✅ FIXED
**File:** `app/src/main/res/layout/item_app_rule.xml`  
**Finding:** The layout hardcodes `android:background="#181222"` (dark purple), `android:textColor="#d4cce0"` (light purple text), and `android:background="#110d18"` (darker purple). These are dark theme colors baked into the layout. In light mode (`bg=#f5f0eb`), these items will appear as dark rectangles with light text on a cream background — visually broken.  
**Impact:** Apps tab is completely broken in light mode. Every app rule row has wrong colors.  
**Fix applied:** Replaced all 3 hardcoded colors with theme-aware resources: `#181222` → `@color/surface`, `#d4cce0` → `@color/text_primary`, `#110d18` → `@color/row_bg`. These all have proper light (`values/colors.xml`) and dark (`values-night/colors.xml`) variants already defined. Apps tab now renders correctly in both themes. Commit `e50bba2`.

### E-02 🟠 Six accent colors missing from dark theme (values-night/colors.xml)
**File:** `res/values/colors.xml` vs `res/values-night/colors.xml`  
**Finding:** These colors are defined in light theme only:
- `accent_orange` (#e08800) — no dark equivalent
- `accent_cyan` (#0099cc) — no dark equivalent
- `accent_blue` (#4488cc) — no dark equivalent
- `accent_dnd` (#cc8800) — used by DND UI elements
- `accent_save` (#5555cc) — used by save button
- `accent_rose` (#a05868) — used by active profile indicators

Android falls back to the light values on dark backgrounds. `accent_dnd` (#cc8800 on #140f1e) may have insufficient contrast. `accent_save` (#5555cc) will look muted. `accent_rose` (#a05868) will appear dull.  
**Impact:** Several UI accent colors look wrong/muted in dark mode.  
**Fix:** Add all six colors to `values-night/colors.xml` with appropriate dark-mode values (brighter/more saturated variants).

### E-03 🟠 LogcatFragment hardcodes all colors — broken in light mode
**File:** `LogcatFragment.kt:43-49`  
**Finding:** `COLOR_VERBOSE = Color.parseColor("#7e6e98")`, `COLOR_DEBUG = "#b0a4c0"`, etc. — all 7 log-level colors are hardcoded for dark backgrounds. In light mode, these will be nearly invisible against the light background.  
**Also:** `fragment_logcat.xml` has `android:textColor="#b0a4c0"` and `android:popupBackground="#201830"` hardcoded.  
**Impact:** Logcat tab has poor readability in light mode.  
**Fix:** Move colors to `colors.xml`/`colors.xml (night)` and reference via `@color/`. Or use `AppColors` methods.

### E-04 🟠 Drawable icons hardcode fillColor="#00ff88" (neon green)
**File:** All 5 `drawable-v24/ic_*.xml` files  
**Finding:** Every nav icon has `android:fillColor="#00ff88"` — a neon green that doesn't match either theme's color scheme. These ARE tinted programmatically in `MainActivity.selectTab()`, but the raw green color is visible during the initial render frame before tinting kicks in.  
**Impact:** Brief green flash on app launch; incorrect color if icons are used anywhere else without tinting.  
**Fix:** Change `fillColor` to a neutral color like `#FFFFFF` or a theme attribute.

### E-05 🟡 Dimens system defined but widely unused
**File:** `dimens.xml` vs all XML layouts  
**Finding:** `dimens.xml` defines a complete type scale (`text_title` through `text_mini`) and spacing system (`spacing_xs` through `spacing_xl`). But layouts overwhelmingly use inline values: `textSize="12sp"`, `padding="20dp"`, `layout_marginBottom="14dp"`. The dimens resources are referenced almost nowhere.  
**Impact:** Inconsistent spacing/sizing, harder to maintain global changes.  
**Fix:** Systematic pass to replace inline values with dimen references.

### E-06 🟡 KyokanButton style defined but not applied to most buttons
**File:** `styles.xml` vs all layouts  
**Finding:** `KyokanButton`, `KyokanButton.Danger`, and `KyokanButton.Accent` styles exist but most buttons in XML use inline `backgroundTint`/`textColor` attributes directly.  
**Impact:** Style changes require editing every button individually instead of one style.  
**Fix:** Apply `style="@style/KyokanButton"` (or variants) to all buttons.

### E-07 🟡 Version string hardcoded in layout XML
**File:** `fragment_home.xml`  
**Finding:** `android:text="v4.0  ·  Kokoro + Piper + Orpheus"` — version and engine list hardcoded in the layout.  
**Impact:** Must manually update the layout file on every version bump.  
**Fix:** Use a string resource with `BuildConfig.VERSION_NAME`, or set dynamically in `HomeFragment.onViewCreated`.

### E-08 🟢 Color system is well-structured overall
**Finding:** 40+ semantic color names organized into clear categories (core brand, text hierarchy, engine accents, gender, status, backgrounds, inputs). Both light and dark variants exist for most colors. `AppColors` object provides type-safe access.  
**Impact:** Positive — good foundation despite the gaps noted above.

---

## CATEGORY F — UX & INFORMATION ARCHITECTURE

### F-01 🟠 No unsaved changes warning when leaving ProfilesFragment
**File:** `ProfilesFragment.kt`  
**Finding:** User can modify voice selection, pitch, speed without saving. Tapping any other tab silently discards all changes. No "unsaved changes" dialog, no visual indicator that changes are pending.  
**Impact:** Users lose work without knowing. Especially frustrating after careful voice tuning.  
**Fix:** Track dirty state (compare current UI state vs last saved), show confirmation dialog on tab switch if dirty.

### F-02 🟠 Tab label "Voices" vs internal name "Profiles" — conceptual confusion
**File:** `strings.xml:7` vs `ProfilesFragment.kt`, `ProfilesViewModel.kt`  
**Finding:** Bottom nav says "Voices" (`nav_voices`), but the fragment is `ProfilesFragment`, the title says "VOICE PROFILES", and the section is called "Profiles". The tab manages two distinct concepts: voice profiles (named containers with voice + pitch + speed) AND the voice picker (engine-grouped voice catalog). These are conflated.  
**Impact:** New users don't understand the profile/voice distinction. "Voices" tab actually manages profiles.  
**Fix:** Either rename to "Profiles" consistently, or split into two tabs (Voices catalog + Profile management).

### F-03 🟠 Preview button shows "synthesizing" for fixed 2s regardless of actual synthesis time
**File:** `VoiceCardBuilder.kt:147-152`  
**Finding:** Preview button changes text to "⏳ synthesizing…" and resets after 2000ms via `postDelayed`. But cloud voice synthesis via DeepInfra can take 3-10+ seconds. After 2 seconds, the button shows "▶ preview" again while audio is still loading. User may click again, queuing duplicates.  
**Impact:** Misleading feedback; double-tap queuing.  
**Fix:** Use the AudioPipeline's synthesis error listener to detect actual completion/failure, or disable the button until synthesis finishes.

### F-04 🟡 Onboarding text is a wall of text with no visual hierarchy
**File:** `strings.xml:onboarding_welcome`  
**Finding:** The onboarding text is a single paragraph with numbered steps embedded as plain text. No formatting, no visual hierarchy, no progressive disclosure.  
**Impact:** Users skip it because it looks like a text dump.  
**Fix:** Break into a step-by-step onboarding flow, or at minimum style with custom spans.

### F-05 🟡 Post-setup guidance disappears forever once all three steps are done
**File:** `HomeFragment.kt:160-173`  
**Finding:** `showGuidance()` checks for voice model, profile, and app rules. Once all three exist, guidance hides. But it also hides permanently — no way to see it again. Users who set up months ago and forgot the app rules section have no way to rediscover it.  
**Impact:** Discoverability loss for returning users.  
**Fix:** Consider a help/tips section that's always accessible, rather than one-time guidance.

### F-06 🟡 No feedback when cloud voice synthesis fails
**File:** `AudioPipeline.kt:139`  
**Finding:** When cloud synthesis fails, `notifySynthesisError` fires, and ProfilesFragment shows a Toast. But if the user is not on the Profiles tab (e.g., notification triggered cloud synthesis from the background), the error is silently swallowed.  
**Impact:** Users don't know why their notifications suddenly go silent.  
**Fix:** Show a notification or update the foreground service notification text with error status.

### F-07 🟢 Setup wizard flow is well-designed
**Finding:** Three-step progressive setup (restricted settings → battery → notifications) with per-step status, clear button labels, and automatic progression. Good.

---

## CATEGORY G — ACCESSIBILITY

### G-01 🟠 No respect for system animation scale setting
**File:** Multiple — `MainActivity.kt`, `VoiceCardBuilder.kt`, `HomeFragment.kt`  
**Finding:** Animations (tab color transitions, voice card scale pulse, listening breath animation, collapsible section transitions) run regardless of the system `ANIMATOR_DURATION_SCALE` setting. Users who set animations to 0x (off) still see all animations.  
**Impact:** Accessibility violation for users with motion sensitivities or vestibular disorders.  
**Fix:** Check `Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)` and disable animations when set to 0.

### G-02 🟡 Word rule rows have no content descriptions
**File:** `WordRulesDelegate.kt:28-46`  
**Finding:** EditTexts for find/replace and the delete button have no `contentDescription` or `labelFor`. Screen reader users cannot distinguish between "Find" and "Replace" fields for different rules.  
**Impact:** Rules tab is difficult to use with TalkBack.  
**Fix:** Add content descriptions: "Find text for rule {N}", "Replace text for rule {N}", "Delete rule {N}".

### G-03 🟡 Delete button ("✕") in word rules may not meet 48dp touch target
**File:** `WordRulesDelegate.kt:45`  
**Finding:** Delete button has `setPadding(16, 8, 16, 8)` but no explicit `minHeight`/`minWidth`. The button's size depends on text content ("✕"), which may render smaller than 48dp on some devices.  
**Impact:** Small touch target for an important destructive action.  
**Fix:** Add `minHeight = (48 * dp).toInt(); minimumHeight = (48 * dp).toInt()`.

### G-04 🟢 Voice cards and profile cards have good accessibility labels
**Finding:** `VoiceCardBuilder.buildVoiceCard()` and `ProfileGridBuilder.buildCard()` both set comprehensive `contentDescription` including voice name, gender, status, and selection state. Good.

---

## CATEGORY I — CODE QUALITY & ARCHITECTURE

### I-01 🟠 SpinnerUtil.kt defined but NEVER used — dead code
**File:** `SpinnerUtil.kt`  
**Finding:** `onItemSelectedSkipFirst()` extension function exists specifically to solve the "spinner fires on programmatic setSelection" problem. But every spinner in the codebase uses the manual `var initialized = false` pattern instead. The utility is completely unused.  
**Impact:** Dead code. Worse: the problem it solves (A-02) still exists in some places.  
**Fix:** Either use `onItemSelectedSkipFirst()` everywhere spinners exist, or delete the file.

### I-02 🟠 VoiceCardBuilder.addVoiceRows() is dead code
**File:** `VoiceCardBuilder.kt:123-143`  
**Finding:** `addVoiceRows()` was the old pattern for adding voice cards to a LinearLayout. Since the migration to RecyclerView (VoiceGridAdapter), this method is never called.  
**Impact:** 20 lines of dead code.  
**Fix:** Remove `addVoiceRows()`.

### I-03 🟠 PiperVoices.isPiperVoice() duplicates VoiceRegistry.isPiper()
**File:** `PiperVoice.kt:78`, `VoiceRegistry.kt:51`  
**Finding:** Two separate methods check if a voice ID belongs to Piper:
- `PiperVoices.isPiperVoice(voiceId)` — checks PiperVoices.ALL
- `VoiceRegistry.isPiper(voiceId)` — checks VoiceRegistry.ALL via engine type

Both are used in different places: `AudioPipeline` uses `PiperVoices.isPiperVoice()`, while `VoiceRegistry.isPiper()` exists but is unused in production code.  
**Impact:** Conceptual duplication. If the voice catalog changes, both need updating.  
**Fix:** Consolidate to `VoiceRegistry.isPiper()` as the single authority.

### I-04 🟡 @Deprecated genderColor properties still exist in three voice classes
**File:** `KokoroVoice.kt:13`, `PiperVoice.kt:30-33`, `VoiceRegistry.kt:41`  
**Finding:** All three voice data classes have `@Deprecated("Unused — views use AppColors.genderColor() instead") val genderColor`. These are annotated as unused but not removed.  
**Impact:** Dead code that clutters the data model.  
**Fix:** Remove all three `genderColor` properties.

### I-05 🟡 Delegate constructor inconsistency
**File:** `WordRulesDelegate.kt`, `NotificationRulesDelegate.kt`, `LanguageRoutingDelegate.kt`  
**Finding:** Three delegates, three different constructor signatures:
- `WordRulesDelegate(Context, SharedPreferences, LinearLayout)`
- `NotificationRulesDelegate(SharedPreferences)`
- `LanguageRoutingDelegate(Context, SharedPreferences, AppContainer)`

No shared interface or base class. Inconsistent dependency injection.  
**Impact:** Hard to reason about what each delegate needs.  
**Fix:** Standardize on a common constructor pattern or introduce a base delegate class.

### I-06 🟡 Voice preview logic duplicated in ProfilesFragment
**File:** `ProfilesFragment.kt:256-268` and `ProfilesFragment.kt:297-308`  
**Finding:** `previewVoice()` and `btnTest.setOnClickListener` contain nearly identical code: check for `NotificationReaderService.instance`, create a temp profile, start AudioPipeline if needed, enqueue an Item.  
**Impact:** Logic duplication; fix in one place may miss the other.  
**Fix:** Extract into a single `playPreview(text, profile)` method.

### I-07 🟡 No repository/data layer — SharedPreferences accessed directly everywhere
**Finding:** `PreferenceManager.getDefaultSharedPreferences()` is called in: fragments (HomeFragment, ProfilesFragment, AppsFragment, RulesFragment), ViewModels (HomeViewModel, ProfilesViewModel), delegates (3), services (NotificationReaderService), utilities (VoiceCommandHandler, VoiceCommandListener). At least 11 different classes directly access the same SharedPreferences.  
**Impact:** No single point to add caching, migration, validation, or backend swap.  
**Fix:** Extract a `SettingsRepository` or `DataStore` wrapper that all classes access through `AppContainer`.

### I-08 🟡 `HomeFragment.onCreateView` parameter `c` shadows class property `c`
**File:** `HomeFragment.kt:38`  
**Finding:** `override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?)` — parameter `c` shadows the class-level `val c by lazy { requireContext().container }`. Inside `onCreateView`, `c` refers to the ViewGroup parameter, not the container.  
**Impact:** Confusion for developers; potential bugs if someone accidentally uses `c` expecting the container.  
**Fix:** Rename parameters to standard Android names: `inflater`, `container`, `savedInstanceState`.

### I-09 🟡 Unused string resources
**File:** `strings.xml`  
**Finding:** These string resources are defined but never referenced in code or layouts:
- `cooldown_label` / `max_queue_label` (superseded by hardcoded strings in NotificationRulesDelegate)
- `find_hint` / `replace_hint` (superseded by hardcoded "Find…" / "Replace…" in WordRulesDelegate)
- `loading` / `reload_apps` / `no_user_apps` (superseded by hardcoded strings in AppsFragment)
- `lang_route_label` (superseded by inline string construction in LanguageRoutingDelegate)
- `global` (never referenced)

**Impact:** Dead resources bloat the app and mislead developers.  
**Fix:** Either use these resources (preferred — they were created for a reason) or remove them.

### I-10 🟢 kyokan-tts/ Python module is disconnected from the Android app
**File:** `kyokan-tts/`  
**Finding:** The Python TTS router (Orpheus + Chatterbox + Qwen3 via DeepInfra) is a standalone module in the repo. The Android app does NOT use it — CloudTtsEngine talks directly to DeepInfra's OpenAI-compatible API. The Python module appears to be for future server-side TTS routing.  
**Impact:** Potentially confusing for contributors. Not a bug.  
**Fix:** Add a README note clarifying this is server-side future work, not part of the Android build.

### I-11 🟢 VoiceRegistry.engines() returns list never used in UI
**File:** `VoiceRegistry.kt:57`  
**Finding:** `fun engines(): List<String>` returns `["All", "Kokoro", "Piper", "Orpheus"]` but no engine filter exists in the UI. Only gender and language filters are implemented.  
**Impact:** Unused API. Might be planned for future use.

### I-12 🟢 App name split: Echolibrium vs Kyōkan
**Finding:** Repository is "Echolibrium", package is `com.echolibrium.kyokan`, UI displays "Kyōkan". This appears intentional (Echolibrium = project, Kyōkan = product name) but should be documented.

---

## CATEGORY K — AI/LLM INTEGRATION

### K-01 🟠 No cost tracking or usage limits for DeepInfra API
**File:** `CloudTtsEngine.kt`  
**Finding:** Every cloud voice notification sends a request to DeepInfra with no tracking of character count, request count, or accumulated cost. A burst of notifications could generate significant unexpected charges ($7/million chars for Orpheus).  
**Impact:** Users with their own API key have no visibility into or control over costs.  
**Fix:** Add a daily/monthly character counter persisted in SharedPreferences, with configurable limits and user warnings.

### K-02 🟡 No fallback when cloud voice is selected but connectivity is lost
**File:** `AudioPipeline.kt:133-138`  
**Finding:** When a cloud voice fails, `notifySynthesisError` fires and the notification is silently dropped. No automatic fallback to a local voice (Kokoro/Piper). The notification simply goes unread.  
**Impact:** Users who rely on cloud voices lose all notification reading when offline.  
**Fix:** Add a configurable "fallback to local voice" option when cloud synthesis fails.

### K-03 🟢 Proxy architecture properly protects API key
**Finding:** The Cloudflare Worker proxy holds the DeepInfra key server-side. The Android app sends requests to the proxy without any API key. The proxy adds the key and forwards. This correctly prevents key exposure on-device.

---

## CATEGORY L — STANDARDIZATION & POLISH

### L-01 🟠 Hardcoded strings in Kotlin override existing string resources
**File:** `NotificationRulesDelegate.kt`, `WordRulesDelegate.kt`, `AppsFragment.kt`  
**Finding:** String resources were created (`cooldown_label`, `max_queue_label`, `find_hint`, etc.) but the code builds strings manually instead of using them:
- `"Cooldown per app: ${cooldown}s"` instead of `getString(R.string.cooldown_label, cooldown)`
- `"Max queue size: $maxQueue"` instead of `getString(R.string.max_queue_label, maxQueue)`
- `"LOADING..."` instead of `getString(R.string.loading)`
- `"RELOAD APPS"` instead of `getString(R.string.reload_apps)`

**Impact:** Inconsistency between defined resources and actual usage. Makes i18n impossible for these strings.  
**Fix:** Replace all hardcoded strings with their existing string resource references.

### L-02 🟠 Programmatic view building uses raw dp calculations instead of dimen resources
**File:** `VoiceCardBuilder.kt`, `ProfileGridBuilder.kt`, `WordRulesDelegate.kt`, `LanguageRoutingDelegate.kt`  
**Finding:** All four files compute dp values inline: `val dp = ctx.resources.displayMetrics.density; setPadding((8 * dp).toInt(), ...)`. The `dimens.xml` file defines `spacing_xs` (4dp), `spacing_sm` (8dp), `spacing_md` (12dp), `spacing_lg` (16dp), `spacing_xl` (20dp) — but these are never used in programmatic code.  
**Impact:** Spacing values are arbitrary and inconsistent across programmatically-built views.  
**Fix:** Create a utility `fun Context.dimenPx(@DimenRes id: Int) = resources.getDimensionPixelSize(id)` and use `dimenPx(R.dimen.spacing_sm)` instead of raw calculations.

### L-03 🟡 Default word rules are hardcoded English
**File:** `WordRulesDelegate.kt:17-26`  
**Finding:** Default word rules ("WhatsApp" → "Message", "lol" → "laugh out loud", etc.) are hardcoded in Kotlin. Not localizable, not configurable.  
**Impact:** Non-English users get irrelevant English abbreviation expansions by default.  
**Fix:** Move defaults to a string-array resource or load from a language-specific config.

### L-04 🟡 Inconsistent collapsible section implementation
**File:** `RulesFragment.kt:54-63` vs `ProfilesFragment.kt:92-104`  
**Finding:** Both fragments implement collapsible sections with nearly identical code (toggle visibility, animate, update arrow), but with slightly different implementations. RulesFragment uses the delegate `setupCollapsible()`, ProfilesFragment has `setupCollapsibleSection()`.  
**Impact:** Two implementations of the same pattern with subtle differences.  
**Fix:** Extract a shared `CollapsibleSectionHelper` utility.

### L-05 🟡 Filter button creation is a raw programmatic pattern
**File:** `ProfilesFragment.kt:115-128`  
**Finding:** `filterBtn()` creates buttons programmatically with inline dp calculations, colors, padding, and layout params. This should be a reusable component or at minimum use the `KyokanButton` style.  
**Impact:** Filter button appearance is defined in code, not in the design system.  
**Fix:** Create an XML chip/button style and inflate it.

### L-06 🟡 DownloadDelegate constructor takes a Fragment reference
**File:** `DownloadDelegate.kt:13`  
**Finding:** `class DownloadDelegate(private val fragment: Fragment, ...)` — storing a Fragment reference is a common source of leaks. The delegate uses `fragment.activity?.runOnUiThread` and `fragment.isAdded` checks, which is correct for safety, but the reference itself could outlive the fragment if a callback retains it.  
**Impact:** Currently safe (listeners are unregistered in onDestroyView), but fragile pattern.  
**Fix:** Pass a `WeakReference<Fragment>` or use lifecycle-aware callbacks.

### L-07 🟢 Good use of sealed classes for VoiceGridItem
**Finding:** `VoiceGridItem` as a sealed class with `Header`, `Card`, `Empty` subtypes is a clean pattern for RecyclerView multi-type adapters. Well-structured.

---

## CATEGORY N — INTERNATIONALIZATION

### N-01 🟠 Significant hardcoded user-facing strings scattered in Kotlin
**Finding:** Comprehensive inventory of hardcoded strings not in `strings.xml`:
- `NotificationRulesDelegate.kt`: "Cooldown per app: ${value}s", "Max queue size: $value"
- `AppsFragment.kt`: "LOADING...", "RELOAD APPS", "No user apps found."
- `WordRulesDelegate.kt`: "Find…", "Replace…", "→"
- `LogcatFragment.kt`: "Failed to start logcat:"
- `HomeFragment.kt`: Setup status strings ("Restricted ✓", "Battery ✓", etc.), guidance tips ("→ Head to Voices...")
- `ProfilesFragment.kt`: "▸ Pitch & speed" (though this is also in the XML)

**Impact:** App cannot be fully translated without code changes. Internationalization blocked.  
**Fix:** Move all user-facing strings to `strings.xml` with format placeholders.

### N-02 🟡 No locale-aware time formatting for DND hours
**File:** `strings.xml:26-27`  
**Finding:** `silence_from` = "Silence from: %02d:00" — always 24-hour format. No AM/PM variant.  
**Impact:** US users see "Silence from: 22:00" instead of "10:00 PM".  
**Fix:** Use `DateFormat` or `SimpleDateFormat` with locale-appropriate patterns.

### N-03 🟡 No RTL layout consideration
**Finding:** No `android:supportsRtl="true"` is set (wait — it IS in the manifest). Good. But programmatic layouts (VoiceCardBuilder, ProfileGridBuilder, WordRulesDelegate) use `setPadding(left, top, right, bottom)` instead of `setPaddingRelative(start, top, end, bottom)`. These won't mirror in RTL locales.  
**Impact:** RTL languages (Arabic, Hebrew) will have incorrect padding direction in programmatic views.  
**Fix:** Use `setPaddingRelative()` and `MarginLayoutParams.marginStart/marginEnd` instead of left/right.

### N-04 🟢 String resources use proper format placeholders
**Finding:** Most strings use `%s`, `%d`, `%1$s` format specifiers correctly. Good for translation.

---

## CATEGORY O — PROJECTIONS

### O-01 🟠 SharedPreferences as database will not scale
**Finding:** All persistent data — voice profiles, app rules (potentially 200+ installed apps), word rules, per-language routing preferences, translation settings — is stored as JSON strings in SharedPreferences. At 200 app rules with profile IDs and modes, the `app_rules` JSON alone approaches 50-100KB. Every save rewrites the entire string. Every read parses the entire string.  
**Impact:** Performance degrades with data volume. No query capability. No partial updates. No migration path to a real database without data loss.  
**Fix:** Migrate to Room (SQLite) for structured data, keeping SharedPreferences for simple key-value settings only.

### O-02 🟡 Kotlin 1.9.22 / AGP 8.2.2 are dated
**File:** `build.gradle` (root)  
**Finding:** Kotlin 1.9.22 (current: 2.0+), AGP 8.2.2 (current: 8.5+). Not critically outdated but missing K2 compiler improvements and newer AGP optimizations.  
**Impact:** Missing performance improvements and new language features.  
**Fix:** Upgrade when convenient. Test thoroughly — K2 compiler can surface new warnings.

### O-03 🟡 No automated testing
**Finding:** `testImplementation 'junit:junit:4.13.2'` and `androidTestImplementation` are declared but no test files exist in the project. Zero test coverage.  
**Impact:** Every change is a regression risk. Especially dangerous for the audio pipeline and notification processing logic.  
**Fix:** Add at least unit tests for: VoiceProfile serialization round-trip, AppRule serialization, DND time logic, word rule application, and buildMessage formatting.

### O-04 🟢 APK size optimization is well-considered
**Finding:** arm64-only ABI filter for APK, AAB for Play Store with ABI split, native symbol stripping, ProGuard minification, ML Kit telemetry removal. Good size management.

---

## CROSS-CUTTING FINDINGS

### X-01 🟠 Light mode is systematically broken
**Scope:** E-01, E-02, E-03, E-04  
**Finding:** While a light theme exists in `values/colors.xml` and the dark mode toggle works, at least three major UI areas are broken in light mode:
1. `item_app_rule.xml` — hardcoded dark colors
2. LogcatFragment — hardcoded dark colors
3. Six accent colors missing from dark theme (inverse problem — light values on dark bg)
4. Nav icons flash neon green before tinting

**Root cause:** The app was built dark-mode-first, and light mode was added later without fully auditing all hardcoded values.  
**Fix:** Systematic pass through all XML layouts and programmatic views to ensure all colors use `@color/` resources with both light and dark variants.

### X-02 🟠 String externalization is half-done
**Scope:** F-04, I-09, L-01, N-01  
**Finding:** strings.xml has 95+ entries covering most of the app, BUT there are two distinct problems:
1. Some string resources exist but are IGNORED (code hardcodes the same string differently)
2. Some user-facing strings were never externalized at all

**Root cause:** Incomplete migration pass. Some files were updated, some were missed.  
**Fix:** Complete the migration: search all `.kt` files for string literals that appear in UI.

### X-03 🟡 Programmatic view building pattern needs standardization
**Scope:** L-02, L-04, L-05, E-05, E-06  
**Finding:** Four delegate/builder files construct views entirely in Kotlin code with inline dp calculations, colors, and layout params. The XML design system (dimens, styles, colors) exists but is bypassed by this programmatic code.  
**Root cause:** Views were built programmatically to avoid XML inflation complexity, but the design system wasn't extended to cover the programmatic path.  
**Fix:** Create Kotlin-side design token accessors that reference the XML resources, and use them consistently.

---

## ROOT CAUSE ANALYSIS

The majority of issues trace to **three root causes**:

1. **Incomplete migration passes** — String externalization, theme adaptation, and utility adoption (SpinnerUtil) were started but not completed across all files. Some files were updated, others were left with the old patterns.

2. **Dark-mode-first development without light-mode verification** — The app was built and tested primarily in dark mode. Light mode colors exist but hardcoded dark values persist in XML layouts and Kotlin code, making light mode visually broken in several areas.

3. **Fragment state management gaps** — The Activity/Fragment lifecycle is only partially handled. Tab state, unsaved profile edits, and fragment caching all have issues that manifest on configuration changes (rotation).

---

## QUICK WINS (High impact, low effort)

| # | Fix | Findings Resolved | Effort |
|---|-----|-------------------|--------|
| 1 | Add `@Volatile` to `NotificationReaderService.instance` | B-07 | 1 line |
| 2 | Replace `commit()` with `apply()` in VoiceProfile.saveAll + AppRule.saveAll | B-04 | 2 lines |
| 3 | Delete dead `url` variable in CloudTtsEngine.synthesize | A-01 | 1 line |
| 4 | Remove `@Deprecated genderColor` from 3 voice classes | I-04 | 3 deletions |
| 5 | Delete unused `addVoiceRows()` in VoiceCardBuilder | I-02 | 20 lines |
| 6 | ~~Replace hardcoded colors in item_app_rule.xml with @color refs~~ ✅ | E-01 | 6 lines |
| 7 | Add missing accent colors to values-night/colors.xml | E-02 | 6 lines |
| 8 | Use existing string resources instead of hardcoded strings in NotificationRulesDelegate | L-01 partial | 4 lines |
| 9 | ~~Save/restore selectedTabId in MainActivity~~ ✅ | B-02 | 10 lines |
| 10 | Add `@Volatile` to SherpaEngine.lastSampleRate | D-06 | 1 line |

---

## PRIORITY ROADMAP

### Phase 0 — Critical Fixes (do immediately) ✅ COMPLETE
- ~~B-01: Fix dual source of truth for currentProfile~~ ✅ `e50bba2`
- ~~B-02: Restore selected tab after rotation~~ ✅ `e50bba2`
- ~~B-03: Fix fragment cache lifecycle~~ ✅ `e50bba2`
- ~~E-01: Fix hardcoded colors in item_app_rule.xml~~ ✅ `e50bba2`
- ~~B-05: Save in-progress profile edits~~ ✅ `e50bba2` (bonus — resolved alongside B-01)

### Phase 1 — High-Priority Bug Fixes
- E-02: Add missing dark theme accent colors
- E-03: Fix LogcatFragment hardcoded colors
- D-01: Implement AudioFocus handling
- B-04: Replace commit() with apply()
- B-06: Add user feedback on data corruption
- L-01: Use existing string resources
- A-02: Use SpinnerUtil.onItemSelectedSkipFirst or fix spinner patterns

### Phase 2 — Quality & Polish
- F-01: Add unsaved changes warning
- X-01: Systematic light mode audit
- X-02: Complete string externalization
- D-02: Cache profile list in LanguageRoutingDelegate
- G-01: Respect system animation scale
- I-01/I-02/I-03/I-04: Remove all dead code

### Phase 3 — Architecture & Future
- I-07: Extract SettingsRepository
- O-01: Evaluate Room migration for structured data
- O-03: Add unit tests for core logic
- K-01: Add API usage tracking
- K-02: Add cloud-to-local voice fallback
- B-10: Add data export/backup

---

## AUDIT COMPLETENESS CERTIFICATION

```
Files read:        88/88 (100%)
Kotlin files:      38/38 checked
XML resources:     17/17 checked
Build config:      4/4 checked (build.gradle, settings.gradle, gradle.properties, proguard)
Manifest:          1/1 checked
Python files:      7/7 checked
JS files:          1/1 checked
Shell scripts:     2/2 checked

Categories audited: A, B, C, D, E, F, G, I, K, L, N, O (12/15 applicable)
Categories skipped: H (compatibility — N/A for native), J (data presentation — N/A), M (deployment — minimal scope)

Skills applied:
  ✓ app-audit — Full 15-category framework
  ✓ design-aesthetic-audit — Color, tokens, component character
  ✓ scope-context — Cross-file pattern inventory for hardcoded strings, colors, dead code

Total findings: 78 (4 critical, 24 high, 34 medium, 16 low)
Cross-cutting patterns: 3
Root causes identified: 3
Quick wins identified: 10
```

---

## IMPLEMENTATION LOG

### Phase 0 — Critical Fixes ✅ COMPLETE

**Date:** 2026-03-15  
**Commit:** `e50bba2`  
**Branch:** `claude/phase0-fixes`  
**Files modified:** 3 (`MainActivity.kt`, `ProfilesFragment.kt`, `item_app_rule.xml`)  
**Net change:** +58 lines, −28 lines  

#### B-01 🔴 → ✅ Dual source of truth for currentProfile

**Problem:** `ProfilesFragment` had a local `var currentProfile` AND observed `viewModel.currentProfile` via LiveData. Voice card clicks mutated the local copy only. On rotation, the ViewModel's stale version overwrote unsaved edits.

**Solution chosen:** Option (a) from the audit — route all mutations through ViewModel.

**Changes in `ProfilesFragment.kt`:**
1. Replaced `private var currentProfile = VoiceProfile()` with a read-only computed property:
   ```kotlin
   private val currentProfile: VoiceProfile
       get() = viewModel.currentProfile.value ?: VoiceProfile()
   ```
2. Removed the `viewModel.currentProfile.observe()` block (redundant — getter reads live).
3. Replaced all 4 direct assignment sites with `viewModel.updateCurrentProfile(...)`:
   - `loadProfileToUI()` — was `currentProfile = p`
   - Kokoro voice card click lambda — was `currentProfile = currentProfile.copy(voiceName = v.id)`
   - Piper voice card click lambda — same pattern
   - `selectCloudVoice()` — two paths (consent given / consent dialog accept)

**Why this approach:** The ViewModel already existed with `updateCurrentProfile()`. Making it the single owner required zero new infrastructure — only redirecting writes. The read-only getter ensures the fragment can never diverge from the ViewModel.

#### B-02 🔴 → ✅ Tab state lost on rotation

**Problem:** `selectTab()` was only called when `savedInstanceState == null` (fresh launch). After rotation, `selectedTabId` reset to default `R.id.nav_home`, and bottom nav showed all tabs inactive.

**Changes in `MainActivity.kt`:**
1. Added `companion object { private const val KEY_SELECTED_TAB = "selected_tab_id" }`
2. Added `onSaveInstanceState()` persisting `selectedTabId`
3. Changed `onCreate` logic from `if (savedInstanceState == null) selectTab(R.id.nav_home)` to always calling:
   ```kotlin
   val restoredTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB, R.id.nav_home) ?: R.id.nav_home
   selectTab(restoredTabId)
   ```

**Why this approach:** Minimal change, maximum correctness. The `selectTab()` method already handles both nav indicator tinting and fragment loading, so calling it on every `onCreate` synchronizes both.

#### B-03 🔴 → ✅ Fragment cache holds stale references

**Problem:** `fragmentCache` was a `MutableMap<Int, Fragment>` on the Activity. After rotation, new Activity = empty map. FragmentManager restores old fragments independently. Cache misses create duplicates; old restored fragments leak.

**Changes in `MainActivity.kt`:**
1. Removed `private val fragmentCache = mutableMapOf<Int, Fragment>()`
2. Added `tagForTab(id: Int): String` mapping tab IDs to stable string tags (`"frag_home"`, `"frag_profiles"`, etc.)
3. Changed `selectTab()` fragment lookup from `fragmentCache.getOrPut(id) { ... }` to:
   ```kotlin
   val tag = tagForTab(id)
   val fragment = supportFragmentManager.findFragmentByTag(tag) ?: when (id) { ... }
   loadFragment(fragment, tag)
   ```
4. Changed `loadFragment()` signature to accept a tag, passes it to `.replace(containerId, fragment, tag)`

**Why this approach:** `findFragmentByTag()` is the standard Android pattern for surviving rotation. It finds the FragmentManager-restored instance if one exists, preventing duplication. Tags are stable across Activity recreation.

#### E-01 🔴 → ✅ Hardcoded dark colors in item_app_rule.xml

**Problem:** Three hardcoded hex colors (`#181222`, `#d4cce0`, `#110d18`) made the Apps tab's rule rows dark-themed regardless of system theme. In light mode: dark purple rectangles with light text on cream background.

**Changes in `item_app_rule.xml`:**
1. `android:background="#181222"` → `android:background="@color/surface"`
2. `android:textColor="#d4cce0"` → `android:textColor="@color/text_primary"`
3. `android:background="#110d18"` → `android:background="@color/row_bg"`

**Why these specific resources:** All three already had correct light/dark variants:
- `surface`: light `#ede5dc` / dark `#1a1428`
- `text_primary`: light `#1a1520` / dark `#d4cce0`
- `row_bg`: light `#ede5dc` / dark `#201830`

The dark values are visually close to the original hardcoded colors, so dark mode appearance is preserved while light mode is now correct.

#### B-05 🟠 → ✅ Save in-progress profile edits (bonus)

**Problem:** `onSaveInstanceState` saved filters and activeProfileId but not the working `currentProfile`. Process death silently reverted unsaved voice/pitch/speed edits.

**Changes in `ProfilesFragment.kt`:**
1. Added to `onSaveInstanceState`:
   ```kotlin
   outState.putString("currentProfileJson", currentProfile.toJson().toString())
   ```
2. Added to `onViewCreated` (inside `if (s != null)` block):
   ```kotlin
   val savedProfileJson = s.getString("currentProfileJson", null)
   if (savedProfileJson != null) {
       val restored = VoiceProfile.fromJson(org.json.JSONObject(savedProfileJson))
       viewModel.updateCurrentProfile(restored)
   }
   ```

**Why JSON serialization:** `VoiceProfile` already has `toJson()`/`fromJson()` methods. Using them avoids Parcelable boilerplate. The JSON string is small (~200 bytes) and well within Bundle size limits.

---

### Remaining Critical/High Findings After Phase 0

| Severity | Before | Resolved | Remaining | Solutions Documented |
|----------|--------|----------|-----------|---------------------|
| 🔴 CRITICAL | 4 | 4 (B-01, B-02, B-03, E-01) | **0** | N/A |
| 🟠 HIGH | 24 | 1 (B-05) | **23** | ✅ 23/23 |
| 🟡 MEDIUM | 34 | 0 | 34 | ✅ 34/34 (includes 3 cross-cutting) |
| 🟢 LOW | 16 | 0 | 16 | ✅ 11/16 (5 positive findings need no fix) |
| **TOTAL** | **78** | **5** | **73** | ✅ **62 actionable + 14 positive/no-action = 76 accounted** |

> **Phase 1–3 Fix Solutions** documented below — every remaining actionable finding has a precise, file-specific solution with exact code changes.

---

## PHASE 1–3 FIX SOLUTIONS — Every Remaining Finding

**Date:** 2026-03-15  
**Status:** Solutions documented. No code modified yet.  
**Scope:** All 59 actionable findings (73 remaining minus 14 positive/informational findings that need no fix).

> Each solution describes the exact file(s), line(s), and code changes needed. Organized by category to match the audit structure. Positive findings (C-05, C-06, D-07, D-08, E-08, F-07, G-04, K-03, L-07, N-04, O-04) are omitted as they require no action. Informational findings (I-10, I-11, I-12) are addressed with documentation-only solutions.

---

### CATEGORY A — DOMAIN LOGIC & CORRECTNESS

#### A-01 🟠 FIX: Remove dead variable `url` in CloudTtsEngine

**File:** `CloudTtsEngine.kt`  
**Line:** 97

**Change:** Delete the line:
```kotlin
val url = proxy ?: DIRECT_URL
```

**Rationale:** This variable is assigned but never read. The `urlsToTry` list constructed immediately below it builds from `proxy` and `DIRECT_URL` independently. The dead variable misleads readers about which URL selection logic is active.

**Effort:** Trivial (1 line deletion)  
**Risk:** None — variable is provably unused (grep confirms zero reads).

---

#### A-02 🟠 FIX: Use SpinnerUtil.onItemSelectedSkipFirst in LanguageRoutingDelegate

**File:** `LanguageRoutingDelegate.kt`  
**Lines:** 108–121 (`setupProfileSpinner`), 127–138 (`refreshSpinner`)

**Change in `setupProfileSpinner()` (line 116):** Replace the manual `onItemSelectedListener` with:
```kotlin
spinner.onItemSelectedSkipFirst { position ->
    prefs.edit().putString(prefKey, ids[position]).apply()
}
```
Remove the entire `object : AdapterView.OnItemSelectedListener` anonymous class (lines 116–122).

**Change in `refreshSpinner()` (line ~138):** The refresh method sets `spinner.setSelection(idx)` after swapping the adapter. Since `onItemSelectedSkipFirst` already skips the first programmatic fire, the refresh scenario is safe. However, because `refreshSpinner` replaces the adapter (which resets the skip flag in the listener's closure), we need to also re-attach the listener. Refactor to:
```kotlin
private fun refreshSpinner(spinner: Spinner, prefKey: String) {
    val profiles = VoiceProfile.loadAll(prefs)
    val activeId = prefs.getString("active_profile_id", "") ?: ""
    val activeName = profiles.find { it.id == activeId }?.let { "${it.emoji} ${it.name}" } ?: "Active profile"
    val names = listOf("($activeName)") + profiles.map { "${it.emoji} ${it.name}" }
    val ids = listOf("") + profiles.map { it.id }

    spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, names)
    val savedId = prefs.getString(prefKey, "") ?: ""
    val idx = ids.indexOf(savedId).coerceAtLeast(0)
    spinner.setSelection(idx)
    spinner.onItemSelectedSkipFirst { position ->
        prefs.edit().putString(prefKey, ids[position]).apply()
    }
}
```

**Also apply in:** `addTranslationRow()` (line 236) — the translation target spinner also has this problem. Replace its `onItemSelectedListener` with `onItemSelectedSkipFirst`.

**Rationale:** The `SpinnerUtil.onItemSelectedSkipFirst()` extension already exists in the codebase (I-01), was built for exactly this purpose, but is never used. This fix both resolves the spinner preference corruption AND eliminates dead code (I-01) by putting the utility to use.

**Effort:** Small (~30 min)  
**Risk:** Low — spinner behavior is more correct after fix, not less. Test by: changing profiles while on Rules tab, rotate device, verify language routing spinners retain their selection.

---

#### A-03 🟡 FIX: Move prevTail assignment after crossfade application

**File:** `AudioPipeline.kt`  
**Lines:** 295–322 (`applyCrossfade` method)

**Change:** Move the `prevTail` / `prevSampleRate` assignment block (lines 301–306) to AFTER the crossfade application block (after line 319), and save from `result` instead of `samples`:

```kotlin
private fun applyCrossfade(samples: FloatArray, sampleRate: Int): FloatArray {
    val tail = prevTail
    val tailRate = prevSampleRate
    val result = samples.copyOf()

    val fadeSamples = (sampleRate * CROSSFADE_MS / 1000).coerceAtMost(samples.size / 4)

    // Apply crossfade from previous tail FIRST
    if (tail != null && tailRate == sampleRate && tail.isNotEmpty()) {
        val crossLen = minOf(tail.size, fadeSamples, result.size)
        for (i in 0 until crossLen) {
            val t = i.toFloat() / crossLen
            result[i] = tail[tail.size - crossLen + i] * (1f - t) + result[i] * t
        }
    } else if (tail == null) {
        val fadeIn = (sampleRate * 0.005f).toInt().coerceAtMost(result.size)
        for (i in 0 until fadeIn) {
            result[i] *= i.toFloat() / fadeIn
        }
    }

    // THEN save tail from the crossfaded result (not raw input)
    if (fadeSamples > 0) {
        val start = (result.size - fadeSamples).coerceAtLeast(0)
        val tailSize = (result.size - start).coerceAtMost(MAX_PREV_TAIL_SAMPLES)
        prevTail = result.sliceArray((result.size - tailSize) until result.size)
        prevSampleRate = sampleRate
    }

    return result
}
```

**Rationale:** The original code saves `prevTail` from raw `samples` before the crossfade is applied to `result`. This means stored tails contain the original un-crossfaded head of the audio, and over many consecutive plays, crossfade artifacts compound because each tail feeds into the next crossfade without itself being crossfaded.

**Effort:** Small (~15 min)  
**Risk:** Low — affects audio quality positively. Test by: playing 10+ consecutive notifications and listening for smooth transitions between them.

---

#### A-04 🟡 FIX: Store incrementAndGet result and reuse in backoff calculation

**File:** `VoiceCommandListener.kt`  
**Lines:** 120–143 (`scheduleRestart` method)

**Change:** Restructure to lift `errCount` out of the inner `if` block:

```kotlin
private fun scheduleRestart(isError: Boolean = false) {
    if (!isListening) return
    val errCount: Int
    if (isError) {
        errCount = consecutiveErrors.incrementAndGet()
        if (errCount > MAX_CONSECUTIVE_ERRORS) {
            Log.w(TAG, "Too many consecutive errors ($errCount), stopping listener")
            isListening = false
            onStatusChanged?.invoke(false)
            return
        }
    } else {
        errCount = 0
        consecutiveErrors.set(0)
    }
    val delay = if (isError) {
        (RESTART_DELAY_MS * (1L shl (errCount - 1).coerceAtMost(6)))
            .coerceAtMost(MAX_BACKOFF_MS)
    } else {
        NORMAL_RESTART_DELAY_MS
    }
    mainHandler.postDelayed({
        if (isListening) startListeningInternal()
    }, delay)
}
```

**Rationale:** Eliminates the second `consecutiveErrors.get()` call at line 135. The value from `incrementAndGet()` is reused directly, closing the theoretical race window and making the logic clearer.

**Effort:** Trivial (~5 min)  
**Risk:** None.

---

#### A-05 🟢 FIX: Use locale-aware time formatting for DND hours

**File:** `HomeFragment.kt` — DND seek bar change listeners  
**File:** `strings.xml` — `silence_from` and `until_time` format strings

**Change in `HomeFragment.kt`:** Replace the format string approach with a locale-aware formatter. Create a helper:
```kotlin
private fun formatHour(hour: Int): String {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, 0)
    }
    return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(cal.time)
}
```

Then change the DND label updates from:
```kotlin
txtDndStart.text = getString(R.string.silence_from, dndStart)
```
to:
```kotlin
txtDndStart.text = "Silence from: ${formatHour(dndStart)}"
```

**Change in `strings.xml`:** Update `silence_from` and `until_time` to use `%s` instead of `%02d:00`:
```xml
<string name="silence_from">Silence from: %s</string>
<string name="until_time">Until: %s</string>
```

Then use: `getString(R.string.silence_from, formatHour(h))`.

**Rationale:** US locale users see "10:00 PM" instead of "22:00". Other locales see their native format.

**Effort:** Small (~15 min)  
**Risk:** None.

---

### CATEGORY B — STATE MANAGEMENT & DATA INTEGRITY

#### B-04 🟠 FIX: Replace commit() with apply() in VoiceProfile and AppRule

**File:** `VoiceProfile.kt` line 54  
**File:** `AppRule.kt` line 28

**Change in `VoiceProfile.kt:54`:**
```kotlin
// Before:
prefs.edit().putString("voice_profiles", json).commit()
// After:
prefs.edit().putString("voice_profiles", json).apply()
```

**Change in `AppRule.kt:28`:**
```kotlin
// Before:
prefs.edit().putString("app_rules", arr.toString()).commit()
// After:
prefs.edit().putString("app_rules", arr.toString()).apply()
```

**Rationale:** `commit()` blocks the UI thread for synchronous disk I/O. `apply()` writes asynchronously. Neither call site checks the return value of `commit()`, so there is no loss of correctness. Both are called from UI thread contexts where blocking is harmful.

**Effort:** Trivial (2 lines)  
**Risk:** Extremely low. `apply()` guarantees in-memory consistency immediately and disk persistence before the process exits. Data cannot be lost under normal operation.

---

#### B-06 🟠 FIX: Add user notification and backup on JSON parse failure

**File:** `VoiceProfile.kt` lines 50–55 (`loadAll`)  
**File:** `AppRule.kt` lines 21–27 (`loadAll`)

**Change in `VoiceProfile.kt` `loadAll()`:**
```kotlin
fun loadAll(prefs: android.content.SharedPreferences): MutableList<VoiceProfile> {
    val json = prefs.getString("voice_profiles", null) ?: return mutableListOf()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            try {
                fromJson(arr.getJSONObject(i))
            } catch (e: Exception) {
                Log.e("VoiceProfile", "Skipping corrupted profile at index $i", e)
                null // Salvage valid entries, skip corrupted ones
            }
        }.toMutableList()
    } catch (e: Exception) {
        Log.e("VoiceProfile", "voice_profiles JSON fully corrupted, backing up and returning empty", e)
        // Backup corrupted data for potential manual recovery
        prefs.edit().putString("voice_profiles_backup_corrupted", json).apply()
        mutableListOf()
    }
}
```

**Same pattern in `AppRule.kt` `loadAll()`** — add per-entry try/catch with `mapNotNull`, and backup the corrupted JSON string to a separate key before returning empty.

**Additionally:** In `ProfilesViewModel.loadProfiles()` and `AppsFragment.onViewCreated()`, after calling `loadAll()`, if the result is empty AND the raw JSON string was non-null, show a Toast:
```kotlin
val profiles = VoiceProfile.loadAll(prefs).toMutableList()
if (profiles.isEmpty() && prefs.getString("voice_profiles", null) != null) {
    // Data existed but could not be parsed — inform the user
    android.widget.Toast.makeText(
        getApplication(), "Profile data was corrupted. Defaults restored.",
        android.widget.Toast.LENGTH_LONG
    ).show()
}
```

**Rationale:** The current code silently drops ALL profiles on any parse error. The fix salvages individually valid entries (per-entry try/catch), backs up the corrupted data for manual recovery, and notifies the user so silent data loss becomes visible.

**Effort:** Medium (~1 hour)  
**Risk:** Low — the fallback behavior (empty list) is unchanged for fully corrupted data. The improvement is that partially corrupted data now recovers valid entries.

---

#### B-07 🟡 FIX: Add @Volatile to NotificationReaderService.instance

**File:** `NotificationReaderService.kt` line 53

**Change:**
```kotlin
// Before:
var instance: NotificationReaderService? = null
// After:
@Volatile var instance: NotificationReaderService? = null
```

**Rationale:** `instance` is written from the service binder thread (`onCreate`/`onDestroy`) and read from fragment UI threads. Without `@Volatile`, the JVM memory model doesn't guarantee cross-thread visibility.

**Effort:** Trivial (1 line)  
**Risk:** None.

---

#### B-08 🟡 FIX: Filter translateCodes and translateNames from same entry set

**File:** `LanguageRoutingDelegate.kt` lines 15–16

**Change:**
```kotlin
// Before:
private val translateCodes = NotificationTranslator.LANGUAGES.keys.toList().filter { it.isNotEmpty() }
private val translateNames = NotificationTranslator.LANGUAGES.values.toList().filter { it != "Off (no translation)" }

// After:
private val translateEntries = NotificationTranslator.LANGUAGES.entries.filter { it.key.isNotEmpty() }
private val translateCodes = translateEntries.map { it.key }
private val translateNames = translateEntries.map { it.value }
```

**Rationale:** Both lists are now derived from the same filtered entry set, guaranteeing parallel alignment. If the LANGUAGES map changes, both lists stay in sync.

**Effort:** Trivial (~5 min)  
**Risk:** None.

---

#### B-09 🟡 FIX: Debounce word rule saves

**File:** `WordRulesDelegate.kt` — inside the `editText()` helper function (approx. line 39)

**Change:** Add a debounce handler to the class and delay saves:
```kotlin
class WordRulesDelegate(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val container: LinearLayout
) {
    private val rules = mutableListOf<Pair<String, String>>()
    private val saveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var saveRunnable: Runnable? = null

    private fun debounceSave() {
        saveRunnable?.let { saveHandler.removeCallbacks(it) }
        saveRunnable = Runnable { saveRules() }
        saveHandler.postDelayed(saveRunnable!!, 500)
    }
```

Then in the `editText()` TextWatcher, replace `saveRules()` with `debounceSave()`:
```kotlin
override fun afterTextChanged(s: Editable?) {
    onChanged(s.toString())
    debounceSave()  // was: saveRules()
}
```

**Rationale:** Reduces disk I/O from one write per keystroke to one write per 500ms pause. With 10 rules visible, this reduces writes from 10+/second to 1/second.

**Effort:** Small (~15 min)  
**Risk:** None — data is still saved; just slightly delayed.

---

#### B-10 🟢 FIX: Add data export/import via share intent

**File:** New method in a new `DataExportHelper.kt` or added to an existing utility

**Change:** Create a simple export/import mechanism:
```kotlin
object DataExportHelper {
    fun exportAll(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val export = JSONObject().apply {
            put("_version", 1)
            put("_exported", System.currentTimeMillis())
            put("voice_profiles", prefs.getString("voice_profiles", "[]"))
            put("app_rules", prefs.getString("app_rules", "[]"))
            put("wording_rules", prefs.getString("wording_rules", "[]"))
            put("active_profile_id", prefs.getString("active_profile_id", ""))
            // Add other important prefs as needed
        }
        return export.toString(2)
    }

    fun importAll(ctx: Context, json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val version = obj.optInt("_version", 0)
            if (version < 1) return false
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            prefs.edit()
                .putString("voice_profiles", obj.optString("voice_profiles", "[]"))
                .putString("app_rules", obj.optString("app_rules", "[]"))
                .putString("wording_rules", obj.optString("wording_rules", "[]"))
                .putString("active_profile_id", obj.optString("active_profile_id", ""))
                .apply()
            true
        } catch (e: Exception) { false }
    }
}
```

Add an "Export Data" button in HomeFragment (settings area) that creates a share intent with the JSON file, and an "Import Data" button that opens a file picker.

**Rationale:** Users currently have zero recovery path for data loss. Export/import enables device migration and backup.

**Effort:** Medium (~2-3 hours)  
**Risk:** Low — additive feature, doesn't modify existing behavior.

---

### CATEGORY C — SECURITY & TRUST

#### C-01 🟠 FIX: Add text length limit before any API call

**File:** `CloudTtsEngine.kt` — `synthesize()` method, near line 80

**Change:** Add a character limit constant and trim input text:
```kotlin
companion object {
    // ... existing constants ...
    private const val MAX_INPUT_LENGTH = 2000
}

fun synthesize(text: String, voice: String? = null, language: String? = null): Pair<FloatArray, Int>? {
    // ... existing null checks ...
    
    val trimmedText = text.take(MAX_INPUT_LENGTH)
    
    val payload = JSONObject().apply {
        put("model", MODEL_ID)
        put("input", trimmedText)  // was: text
        put("response_format", "pcm")
        put("voice", v)
    }
    // ... rest unchanged ...
}
```

**Rationale:** The proxy enforces 2000-char limit, but direct API calls with user's own key have no limit. A long notification (full email body) could generate unexpected charges.

**Effort:** Trivial (~5 min)  
**Risk:** None — notifications longer than 2000 chars are truncated silently, which is preferable to uncapped API costs.

---

#### C-02 🟠 FIX: Document Cloudflare Rate Limiting alternative in proxy worker

**File:** `proxy/worker.js` — comment block at top

**Change:** Add a documentation comment and TODO:
```javascript
// KNOWN LIMITATION: In-memory rate limiter resets when worker instance restarts
// (Cloudflare Workers restart frequently) and doesn't share state across instances.
// For production use with real traffic, replace with one of:
//   1. Cloudflare Rate Limiting rules (dashboard → Security → WAF → Rate limiting rules)
//   2. Cloudflare Workers KV for durable per-IP counters
//   3. Cloudflare D1 (SQLite) for more sophisticated rate tracking
// The current in-memory approach is adequate for single-user/low-traffic deployment.
// TODO: Migrate to Cloudflare Rate Limiting rules before public release.
```

**Rationale:** The per-instance rate limiter is a known architectural limitation. The fix at this stage is documentation and a clear migration path — implementing Cloudflare KV is a deployment concern, not a code change in the Android app.

**Effort:** Trivial (documentation only)  
**Risk:** None.

---

#### C-03 🟡 FIX: Document debug APK key exposure risk

**File:** `app/build.gradle` — near the debug buildConfigField

**Change:** Add a comment:
```groovy
debug {
    // WARNING: DEEPINFRA_API_KEY is compiled into debug APKs and extractable via decompilation.
    // NEVER distribute debug APKs externally. Use the proxy for non-development contexts.
    buildConfigField "String", "DEEPINFRA_API_KEY", "\"${localProps.getProperty('DEEPINFRA_API_KEY', '')}\""
    buildConfigField "String", "PROXY_BASE_URL", "\"${localProps.getProperty('PROXY_BASE_URL', 'https://kyokan-tts.whisperingwishes-app.workers.dev')}\""
}
```

**Rationale:** This is a documentation/awareness fix. The proxy architecture already eliminates the key from release builds. The risk is only if debug APKs are distributed.

**Effort:** Trivial (1 comment)  
**Risk:** None.

---

#### C-04 🟡 FIX: Add configurable per-app "local voice only" override

**File:** `AppRule.kt` — add a `forceLocal` field  
**File:** `NotificationReaderService.kt` — check the field before cloud routing

**Change in `AppRule.kt`:** Add a new field:
```kotlin
data class AppRule(
    val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val readMode: String = "full",
    val profileId: String = "",
    val forceLocal: Boolean = false  // NEW: force local TTS, never send to cloud
)
```
Update `toJson()`/`fromJson()` to include the new field.

**Change in `NotificationReaderService.kt`** inside `onNotificationPosted`, in the `processingExecutor.execute` block: after resolving the profile, check if the app rule forces local:
```kotlin
val profile = profiles.find { it.id == profileId } ?: VoiceProfile()
val effectiveVoiceId = if (rule?.forceLocal == true && VoiceRegistry.isCloud(profile.voiceName)) {
    // Fall back to default Kokoro voice when app is marked local-only
    KokoroVoices.default().id
} else {
    profile.voiceName
}
```

**Also:** Add a toggle in the `AppRuleAdapter` row UI for "Local only" so users can mark sensitive apps (banking, messaging) as never-send-to-cloud.

**Rationale:** Users may want cloud voices for most apps but not for banking notifications that contain account numbers. Per-app local-only override gives granular control.

**Effort:** Medium (~2 hours)  
**Risk:** Low — additive feature. Default `forceLocal = false` means no behavior change for existing users.

---

### CATEGORY D — PERFORMANCE

#### D-01 🟠 FIX: Implement AudioFocus handling

**File:** `AudioPipeline.kt` — `playPcm()` method and class-level additions

**Change:** Add AudioFocus request/release around playback:

Add to class level:
```kotlin
private var audioManager: AudioManager? = null
private var focusRequest: android.media.AudioFocusRequest? = null
```

In `start(ctx)`:
```kotlin
audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
```

Create a focus request helper:
```kotlin
private fun requestAudioFocus(): Boolean {
    val am = audioManager ?: return true // No manager = don't block
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        focusRequest = request
        return am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else {
        @Suppress("DEPRECATION")
        return am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
}

private fun abandonAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
    } else {
        @Suppress("DEPRECATION")
        am.abandonAudioFocus(null)
    }
}
```

In `playPcm()`, wrap the playback:
```kotlin
private fun playPcm(samples: FloatArray, sampleRate: Int) {
    if (!requestAudioFocus()) {
        Log.w(TAG, "Audio focus denied — skipping playback")
        return
    }
    // ... existing playback code ...
    // In the finally block, after track.release():
    abandonAudioFocus()
}
```

**Rationale:** Without AudioFocus, TTS overlaps with phone calls, music, and navigation. `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` ducks (lowers volume of) other audio rather than pausing it — appropriate for brief notification speech.

**Effort:** Medium (~1 hour)  
**Risk:** Low — audio focus denial simply skips the notification read, which is the correct behavior (user is on a phone call). Test by: playing music, triggering a notification, verifying music ducks during speech.

---

#### D-02 🟠 FIX: Cache profile list in LanguageRoutingDelegate.setup()

**File:** `LanguageRoutingDelegate.kt` — `setup()` method

**Change:** Parse profiles once at the top and pass to all spinners:

```kotlin
fun setup(v: View) {
    // Parse once, reuse everywhere
    val profiles = VoiceProfile.loadAll(prefs)
    
    val switchLangRouting = v.findViewById<SwitchCompat>(R.id.switch_lang_routing)
    // ... existing switch code ...

    val routingContainer = v.findViewById<LinearLayout>(R.id.voice_routing_container)
    routingContainer.removeAllViews()
    profileSpinners.clear()
    for (langCode in translateCodes) {
        val langName = NotificationTranslator.LANGUAGES[langCode] ?: langCode
        val flag = langFlags[langCode] ?: ""
        addVoiceRoutingRow(routingContainer, langCode, "$flag  $langName notifications \u2192 Voice:", profiles)
    }
    // ... rest unchanged but pass profiles to any method that needs them ...
}
```

Update `setupProfileSpinner()` and `addVoiceRoutingRow()` signatures to accept `profiles: List<VoiceProfile>` instead of calling `VoiceProfile.loadAll(prefs)` internally.

Similarly update `refreshProfileSpinners()` to parse once and pass the list.

**Rationale:** Eliminates 26+ redundant JSON parse operations of the same data on the UI thread during fragment creation.

**Effort:** Small (~30 min)  
**Risk:** None — same data, just parsed once.

---

#### D-03 🟡 FIX: Save word rules on focus loss instead of every keystroke

**File:** `WordRulesDelegate.kt`

**Change:** Combined with B-09's debounce fix. Additionally, the `renderRules()` method's full-rebuild approach should add `setOnFocusChangeListener` to EditTexts:
```kotlin
etFind.setOnFocusChangeListener { _, hasFocus ->
    if (!hasFocus) saveRules()
}
```

For a full fix, migrate to RecyclerView with DiffUtil (same pattern as `VoiceGridAdapter`), but the debounce + focus-loss save is the pragmatic minimal fix.

**Effort:** Small (debounce) or Medium (RecyclerView migration)  
**Risk:** Low.

---

#### D-04 🟡 FIX: Optimize LogcatFragment display refresh

**File:** `LogcatFragment.kt` — `refreshDisplay()` method

**Change:** Rather than migrating to RecyclerView (which would be a larger refactor), apply these targeted optimizations:

1. Reuse `SpannableStringBuilder` by clearing instead of recreating:
```kotlin
private val ssb = SpannableStringBuilder()

private fun refreshDisplay() {
    lastRefresh = System.currentTimeMillis()
    val filtered: List<LogLine>
    synchronized(logBuffer) {
        filtered = logBuffer.filter { passesFilter(it) }
    }
    lineCount = filtered.size
    tvLineCount.text = getString(R.string.lines_count, lineCount)

    ssb.clear()
    ssb.clearSpans()
    val displayLines = if (filtered.size > 500) filtered.takeLast(500) else filtered
    // ... existing span building code using ssb ...
    tvLog.text = ssb
}
```

2. Consider setting `android:textIsSelectable="false"` in `fragment_logcat.xml` to avoid expensive selection tracking during rapid updates. Add a "Copy" button instead for when users need to copy log content.

**Rationale:** Reusing the SpannableStringBuilder avoids allocating a new object every 200ms. Disabling text selectability removes a major source of layout overhead during rapid updates.

**Effort:** Small (~20 min)  
**Risk:** Low — functionally identical output.

---

#### D-05 🟡 FIX: Override equals/hashCode in VoiceGridItem to exclude lambdas

**File:** `VoiceGridAdapter.kt` — `VoiceGridItem.Card` and `VoiceGridItem.Header` data classes

**Change:** Override `equals` and `hashCode` in both data classes to compare only the non-lambda fields:

```kotlin
data class Card(
    val voiceId: String,
    val name: String,
    val icon: String,
    val iconColor: Int,
    val status: String,
    val statusColor: Int,
    val active: Boolean,
    val accent: Int,
    val enabled: Boolean,
    @Transient val onClick: (() -> Unit)? = null
) : VoiceGridItem() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Card) return false
        return voiceId == other.voiceId && name == other.name && icon == other.icon
            && iconColor == other.iconColor && status == other.status
            && statusColor == other.statusColor && active == other.active
            && accent == other.accent && enabled == other.enabled
    }
    override fun hashCode(): Int = voiceId.hashCode()
}
```

Same pattern for `Header` — exclude `onAction` from equality.

**Rationale:** Data class `equals()` includes all properties by default, including `@Transient` lambdas. Since lambdas are created fresh on every render, `areContentsTheSame()` always returns false, causing full rebind of every visible card. Excluding lambdas enables DiffUtil to skip unchanged items.

**Effort:** Small (~20 min)  
**Risk:** None — DiffUtil becomes more efficient, not less correct.

---

#### D-06 🟡 FIX: Add @Volatile to SherpaEngine.lastSampleRate

**File:** `SherpaEngine.kt` line 51

**Change:**
```kotlin
// Before:
var lastSampleRate: Int = 22050
// After:
@Volatile var lastSampleRate: Int = 22050
```

**Rationale:** Written under `kokoroLock` and `piperLock` (different monitors), read with no synchronization. `@Volatile` guarantees cross-thread visibility.

**Effort:** Trivial (1 line)  
**Risk:** None.

---

### CATEGORY E — VISUAL DESIGN & TOKENS

#### E-02 🟠 FIX: Add missing accent colors to values-night/colors.xml

**File:** `app/src/main/res/values-night/colors.xml`

**Change:** Add these six entries inside the `<resources>` tag:
```xml
<!-- ═══ Functional accent colors — brighter for dark backgrounds ═══ -->
<color name="accent_orange">#ffaa44</color>
<color name="accent_cyan">#33ccff</color>
<color name="accent_blue">#88bbff</color>
<color name="accent_dnd">#ffbb33</color>
<color name="accent_save">#8888ff</color>
<color name="accent_rose">#d4788a</color>
```

**Rationale:** These six colors exist only in `values/colors.xml` (light theme). Without dark variants, Android falls back to the light values, which look muted/dull on dark backgrounds. The dark variants are brighter/more saturated to maintain visual punch on dark surfaces — matching the pattern already used by `accent_red` (#cc2222 light → #ff4444 dark) and engine colors.

**Effort:** Trivial (~5 min)  
**Risk:** None — additive, no existing behavior changes.

---

#### E-03 🟠 FIX: Move LogcatFragment colors to resources

**File:** `LogcatFragment.kt` lines 43–49  
**File:** `app/src/main/res/values/colors.xml`  
**File:** `app/src/main/res/values-night/colors.xml`

**Change step 1 — Add to `values/colors.xml` (light theme):**
```xml
<!-- ═══ Logcat level colors ═══ -->
<color name="log_verbose">#6a5e80</color>
<color name="log_debug">#5a4e70</color>
<color name="log_info">#7844a0</color>
<color name="log_warn">#cc9900</color>
<color name="log_error">#cc2222</color>
<color name="log_fatal">#cc0000</color>
<color name="log_tag">#a05868</color>
```

**Change step 2 — Add to `values-night/colors.xml` (dark theme):**
```xml
<!-- ═══ Logcat level colors ═══ -->
<color name="log_verbose">#7e6e98</color>
<color name="log_debug">#b0a4c0</color>
<color name="log_info">#9b7eb8</color>
<color name="log_warn">#ffcc00</color>
<color name="log_error">#ff4444</color>
<color name="log_fatal">#ff0000</color>
<color name="log_tag">#c48da0</color>
```

**Change step 3 — In `LogcatFragment.kt`**, replace hardcoded colors with resource lookups:
```kotlin
// Replace:
private val COLOR_VERBOSE = Color.parseColor("#7e6e98")
// ... etc ...

// With:
private var COLOR_VERBOSE = 0
private var COLOR_DEBUG = 0
// ... etc ...

// Initialize in onViewCreated:
val ctx = requireContext()
COLOR_VERBOSE = ContextCompat.getColor(ctx, R.color.log_verbose)
COLOR_DEBUG = ContextCompat.getColor(ctx, R.color.log_debug)
COLOR_INFO = ContextCompat.getColor(ctx, R.color.log_info)
COLOR_WARN = ContextCompat.getColor(ctx, R.color.log_warn)
COLOR_ERROR = ContextCompat.getColor(ctx, R.color.log_error)
COLOR_FATAL = ContextCompat.getColor(ctx, R.color.log_fatal)
COLOR_TAG = ContextCompat.getColor(ctx, R.color.log_tag)
```

**Change step 4 — In `fragment_logcat.xml`:** Replace `android:textColor="#b0a4c0"` with `android:textColor="@color/log_debug"` and `android:popupBackground="#201830"` with `android:popupBackground="@color/surface_elevated"`.

**Rationale:** All logcat colors are dark-theme-only constants. Light mode renders them nearly invisible on cream backgrounds.

**Effort:** Small (~30 min)  
**Risk:** None — visual improvement only.

---

#### E-04 🟠 FIX: Change icon fillColor from neon green to neutral white

**Files:** All 5 files in `app/src/main/res/drawable-v24/`:
- `ic_home.xml`
- `ic_voice.xml`
- `ic_apps.xml`
- `ic_rules.xml`
- `ic_logcat.xml`

**Change:** In each file, replace:
```xml
android:fillColor="#00ff88"
```
with:
```xml
android:fillColor="#FFFFFF"
```

**Rationale:** Icons are tinted programmatically in `MainActivity.selectTab()`, so the base fillColor only matters during the initial render frame before tinting. White is neutral and invisible against the initial theme load, eliminating the brief green flash. If icons are reused elsewhere without tinting, white is a safer default than neon green.

**Effort:** Trivial (~5 min, 5 files)  
**Risk:** None — tinting overrides fillColor immediately.

---

#### E-05 🟡 FIX: Systematic dimen resource adoption in XML layouts

**Files:** All 7 XML layout files

**Change:** Replace inline dp/sp values with `@dimen/` references. Priority replacements (highest impact):

| Inline value | Replace with | Occurrences |
|-------------|-------------|-------------|
| `android:padding="20dp"` | `@dimen/spacing_xl` | 5 layouts |
| `android:textSize="12sp"` | `@dimen/text_caption` | 15+ elements |
| `android:textSize="14sp"` | `@dimen/text_body` | 10+ elements |
| `android:textSize="13sp"` | `@dimen/text_body_sm` | 8+ elements |
| `android:textSize="11sp"` | `@dimen/text_label` | 10+ elements |
| `android:textSize="10sp"` | `@dimen/text_micro` | 5+ elements |
| `android:layout_marginBottom="14dp"` | New `@dimen/spacing_md_lg` (14dp) or nearest (`@dimen/spacing_lg`) | Multiple |

**Note:** Some values like 14dp and 6dp don't have exact dimen equivalents. Add these to `dimens.xml`:
```xml
<dimen name="spacing_md_lg">14dp</dimen>
<dimen name="spacing_content_pad">20dp</dimen>
```

**Rationale:** The dimen system exists but is unused. Adopting it enables global spacing/sizing changes from one file.

**Effort:** Medium (~2 hours — many small changes across 7 files)  
**Risk:** Low — purely visual consistency. Verify each layout after change.

---

#### E-06 🟡 FIX: Apply KyokanButton styles to all buttons

**Files:** `fragment_home.xml`, `fragment_profiles.xml`, `fragment_apps.xml`, `fragment_rules.xml`

**Change:** For each button that uses inline `backgroundTint`/`textColor`:
```xml
<!-- Before: -->
<Button android:id="@+id/btn_load_apps"
    android:backgroundTint="@color/btn_primary_bg"
    android:textColor="@color/primary"
    android:fontFamily="sans-serif" ... />

<!-- After: -->
<Button android:id="@+id/btn_load_apps"
    style="@style/KyokanButton" ... />
```

Map existing inline styles to the correct variant:
- `btn_primary_bg` + `primary` text → `@style/KyokanButton`
- `btn_red_bg` + `accent_red` text → `@style/KyokanButton.Danger`
- `primary` bg + `text_on_accent` text → `@style/KyokanButton.Accent`
- `btn_blue_bg` + `accent_save` text → New `@style/KyokanButton.Save`:
```xml
<style name="KyokanButton.Save">
    <item name="android:backgroundTint">@color/btn_blue_bg</item>
    <item name="android:textColor">@color/accent_save</item>
</style>
```

**Rationale:** The style system exists but is bypassed. Using it means button appearance changes require editing one style instead of every layout.

**Effort:** Small (~45 min)  
**Risk:** Low — verify visual appearance matches after switch.

---

#### E-07 🟡 FIX: Set version string dynamically from BuildConfig

**File:** `HomeFragment.kt` — `onViewCreated()`  
**File:** `fragment_home.xml` — the version TextView

**Change in `fragment_home.xml`:** Remove the hardcoded text:
```xml
<!-- Before: -->
android:text="v4.0  ·  Kokoro + Piper + Orpheus"
<!-- After: -->
android:text=""
android:id="@+id/txt_version"
```

**Change in `HomeFragment.kt` `onViewCreated()`:**
```kotlin
v.findViewById<TextView>(R.id.txt_version).text =
    "v${BuildConfig.VERSION_NAME}  ·  Kokoro + Piper + Orpheus"
```

**Rationale:** Version is now always in sync with `build.gradle` `versionName`. No manual layout updates needed on version bumps.

**Effort:** Trivial (~5 min)  
**Risk:** None.

---

### CATEGORY F — UX & INFORMATION ARCHITECTURE

#### F-01 🟠 FIX: Track dirty state and warn on tab switch

**File:** `ProfilesFragment.kt`  
**File:** `MainActivity.kt`

**Change in `ProfilesFragment.kt`:** Add a dirty-state tracker:
```kotlin
private var lastSavedProfile: VoiceProfile? = null

private fun loadProfileToUI(p: VoiceProfile) {
    lastSavedProfile = p
    viewModel.updateCurrentProfile(p)
    // ... existing UI setup ...
}

fun hasUnsavedChanges(): Boolean {
    val saved = lastSavedProfile ?: return false
    val current = readProfileFromUI()
    return current.voiceName != saved.voiceName
        || current.pitch != saved.pitch
        || current.speed != saved.speed
}
```

**Change in `MainActivity.kt`:** Before switching tabs, check if the current fragment has unsaved changes:
```kotlin
private fun selectTab(id: Int) {
    if (id == selectedTabId) return
    val currentFragment = supportFragmentManager.findFragmentByTag(tagForTab(selectedTabId))
    if (currentFragment is ProfilesFragment && currentFragment.hasUnsavedChanges()) {
        AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("You have unsaved voice profile changes. Discard them?")
            .setPositiveButton("Discard") { _, _ -> doSelectTab(id) }
            .setNegativeButton("Cancel", null)
            .show()
        return
    }
    doSelectTab(id)
}

private fun doSelectTab(id: Int) {
    // ... existing selectTab() body ...
}
```

**Rationale:** Users lose careful voice tuning work without any warning when switching tabs.

**Effort:** Medium (~1 hour)  
**Risk:** Low — worst case, the dialog is slightly annoying. Users can always dismiss it.

---

#### F-02 🟠 FIX: Rename tab and title to "Profiles" consistently

**File:** `strings.xml` — change `nav_voices`  
**File:** `activity_main.xml` — the nav tab label

**Change in `strings.xml`:**
```xml
<!-- Before: -->
<string name="nav_voices">Voices</string>
<!-- After: -->
<string name="nav_voices">Profiles</string>
```

The existing layout references `@string/nav_voices`, so this one-line change updates the bottom nav label. The fragment title already says "VOICE PROFILES".

**Rationale:** The tab manages profiles (named containers with voice + pitch + speed). "Voices" misleads users into thinking it's just a voice picker. The simplest fix is aligning the tab label with the existing fragment title.

**Effort:** Trivial (1 line)  
**Risk:** None — existing users will see the tab label change; this is an improvement, not a regression.

---

#### F-03 🟠 FIX: Use actual synthesis completion for preview button state

**File:** `VoiceCardBuilder.kt` lines 147–152

**Change:** Replace the fixed `postDelayed(2000)` with a callback-based approach. The simplest pragmatic fix is to increase the timeout to match cloud voice worst-case, and use the `synthesisErrorListener` to reset early on failure:

```kotlin
setOnClickListener {
    text = "⏳ synthesizing…"
    isEnabled = false
    onPreview(voiceId, name)
    // Reset after generous timeout (covers cloud latency)
    postDelayed({ text = originalText; isEnabled = true }, 10_000)
}
```

A better fix (requires plumbing): have `AudioPipeline` emit a "playback started" event, and reset the button on that event rather than a fixed timer.

**Rationale:** 2s timeout is too short for cloud voices (3-10s). 10s covers realistic worst-case while still recovering the button if something goes wrong silently.

**Effort:** Trivial (change one number) or Medium (callback-based approach)  
**Risk:** None for timeout increase. Button stays in "synthesizing" state slightly longer than ideal but always recovers.

---

#### F-04 🟡 FIX: Break onboarding text into styled steps

**File:** `strings.xml` — `onboarding_welcome`

**Change:** Restructure the onboarding text with clearer visual hierarchy:
```xml
<string name="onboarding_welcome">Welcome to Kyōkan!\n\nThree quick steps to start:\n\n① Complete the setup below to grant permissions\n② Go to Profiles to download a voice model\n③ Configure per-app rules in the Apps tab\n\nTap the setup button to get started.</string>
```

**Alternatively** (better UX): Replace the static TextView with a simple vertical stepper that highlights the current step dynamically based on `isNotifGranted()`, voice model download state, and app rules count. This is a larger change but dramatically improves onboarding.

**Effort:** Trivial (string rewrite) or Medium (dynamic stepper)  
**Risk:** None.

---

#### F-05 🟡 FIX: Make guidance re-accessible via a help section

**File:** `HomeFragment.kt` — `showGuidance()` method

**Change:** Instead of hiding guidance permanently, always show it but change its content based on state:
```kotlin
private fun showGuidance() {
    val guidance = view?.findViewById<TextView>(R.id.txt_guidance) ?: return
    val hasVoice = viewModel.isVoiceReady()
    val hasProfile = VoiceProfile.loadAll(prefs).any { it.voiceName.isNotBlank() }
    val hasRules = AppRule.loadAll(prefs).isNotEmpty()

    if (!hasVoice || !hasProfile || !hasRules) {
        val tips = mutableListOf<String>()
        if (!hasVoice) tips.add("→ Head to Profiles to download a voice model")
        if (!hasProfile) tips.add("→ Set up a voice profile in the Profiles tab")
        if (!hasRules) tips.add("→ Configure per-app rules in the Apps tab")
        guidance.text = "What's next?\n${tips.joinToString("\n")}"
        guidance.visibility = View.VISIBLE
    } else {
        guidance.text = "✓ All set! Kyōkan is reading your notifications.\nTip: Long-press a profile card to rename it."
        guidance.visibility = View.VISIBLE
    }
}
```

**Rationale:** Instead of disappearing forever, guidance transforms into contextual tips for returning users.

**Effort:** Small (~15 min)  
**Risk:** None.

---

#### F-06 🟡 FIX: Show notification on background cloud TTS failure

**File:** `AudioPipeline.kt` — `notifySynthesisError()` method

**Change:** In addition to firing the listener (which only reaches ProfilesFragment), update the foreground service notification:
```kotlin
private fun notifySynthesisError(voiceId: String, reason: String) {
    val snapshot = synchronized(listenersLock) { synthesisErrorListeners.toList() }
    mainHandler.post { snapshot.forEach { it(voiceId, reason) } }
    // Also log for background visibility
    Log.w(TAG, "Synthesis error for $voiceId: $reason")
}
```

The fuller fix is to update `TtsAliveService`'s notification text to include the error status. This requires passing a Context or having the service observe errors. A pragmatic approach:

In `NotificationReaderService.processItem()`, after `notifySynthesisError` returns, update a static error field:
```kotlin
companion object {
    // ... existing fields ...
    @Volatile var lastError: String = ""
}
```

And have `TtsAliveService` periodically check and update its notification text if an error is present.

**Effort:** Medium (~1 hour)  
**Risk:** Low — additive notification update.

---

### CATEGORY G — ACCESSIBILITY

#### G-01 🟠 FIX: Respect system animation duration scale

**File:** `MainActivity.kt`, `VoiceCardBuilder.kt`, `HomeFragment.kt`, `ProfilesFragment.kt`, `RulesFragment.kt`

**Change:** Create a shared utility:
```kotlin
// In a new AnimationUtil.kt or in an existing utility file:
object AnimationUtil {
    fun areAnimationsEnabled(context: Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale > 0f
    }
}
```

Then guard all animations:

**In `MainActivity.kt` (`selectTab`):** Wrap the `ValueAnimator` in:
```kotlin
if (AnimationUtil.areAnimationsEnabled(this)) {
    ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply { ... }
} else {
    // Apply final color immediately without animation
    for (i in 0 until tab.childCount) { ... set toColor directly ... }
}
```

**In `VoiceCardBuilder.kt`:** Wrap the scale pulse animation:
```kotlin
if (AnimationUtil.areAnimationsEnabled(ctx)) {
    AnimatorSet().apply { ... }
}
onClick()
```

**In `HomeFragment.kt`:** Wrap the breathing animation:
```kotlin
if (AnimationUtil.areAnimationsEnabled(requireContext())) {
    breathAnimator = ObjectAnimator.ofFloat(tv, "alpha", 1f, 0.4f).apply { ... }
}
```

**In collapsible sections (`RulesFragment`, `ProfilesFragment`):** Skip `TransitionManager.beginDelayedTransition()` when animations are disabled:
```kotlin
if (AnimationUtil.areAnimationsEnabled(context)) {
    TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 250 })
}
section.visibility = if (expanded) View.GONE else View.VISIBLE
```

**Rationale:** Users who set system animations to 0x have motion sensitivities. All non-essential animations must respect this setting.

**Effort:** Small (~45 min — one utility + guards in 5 files)  
**Risk:** None — animations skip gracefully, functionality preserved.

---

#### G-02 🟡 FIX: Add content descriptions to word rule rows

**File:** `WordRulesDelegate.kt` — `renderRules()` method

**Change:** Add content descriptions to each element in the rule row:
```kotlin
val etFind = editText("Find…", find) { rules[idx] = it to rules[idx].second }
etFind.contentDescription = "Find text for rule ${idx + 1}"

val etReplace = editText("Replace…", replace) { rules[idx] = rules[idx].first to it }
etReplace.contentDescription = "Replacement text for rule ${idx + 1}"

val btnDel = Button(context).apply {
    text = "✕"
    contentDescription = "Delete rule ${idx + 1}: ${find.ifBlank { "empty" }} → ${replace.ifBlank { "empty" }}"
    // ... existing styling ...
}
```

**Rationale:** Screen reader users cannot distinguish between multiple identical "Find…" / "Replace…" fields without indexed descriptions.

**Effort:** Trivial (~10 min)  
**Risk:** None.

---

#### G-03 🟡 FIX: Ensure delete button meets 48dp touch target

**File:** `WordRulesDelegate.kt` — delete button creation (approx. line 45)

**Change:** Add minimum height/width:
```kotlin
val btnDel = Button(context).apply {
    text = "✕"; textSize = 12f; setTextColor(AppColors.accentRed(context))
    setBackgroundColor(AppColors.inputBg(context)); setPadding(16, 8, 16, 8)
    val dp = context.resources.displayMetrics.density
    minHeight = (48 * dp).toInt()
    minimumHeight = (48 * dp).toInt()
    minWidth = (48 * dp).toInt()
    minimumWidth = (48 * dp).toInt()
    setOnClickListener { rules.removeAt(idx); saveRules(); renderRules() }
}
```

**Rationale:** WCAG / Material guidelines require 48dp minimum touch targets.

**Effort:** Trivial (~5 min)  
**Risk:** None.

---

### CATEGORY I — CODE QUALITY & ARCHITECTURE

#### I-01 🟠 FIX: Use SpinnerUtil everywhere, or delete it

**Resolution:** USE IT. SpinnerUtil solves a real problem (A-02). Apply `onItemSelectedSkipFirst()` in:
1. `LanguageRoutingDelegate.kt` — both `setupProfileSpinner()` and `addTranslationRow()` spinners (covered by A-02 fix above)
2. `AppRuleAdapter.kt` — mode and profile spinners (lines 74, 86) — these already use the manual `modeInitDone`/`profileInitDone` flag pattern. Replace with `onItemSelectedSkipFirst()` for cleaner code.
3. `HomeFragment.kt` — read mode spinner (already uses the manual pattern)
4. `ProfilesFragment.kt` — profile spinner (already uses `spinnerInitDone`)

The `onItemSelectedSkipFirst()` extension replaces the manual boolean flag pattern used in 4+ locations with a single clean call.

**Effort:** Small (~30 min across all files)  
**Risk:** Low — replacement is functionally equivalent to the manual pattern.

---

#### I-02 🟠 FIX: Delete VoiceCardBuilder.addVoiceRows()

**File:** `VoiceCardBuilder.kt` lines 220–238

**Change:** Delete the entire `addVoiceRows()` function (lines 220–238). Grep confirms zero callers.

**Effort:** Trivial (1 deletion)  
**Risk:** None — dead code.

---

#### I-03 🟠 FIX: Consolidate isPiperVoice to VoiceRegistry.isPiper()

**File:** `AudioPipeline.kt` line 166  
**File:** `PiperVoice.kt` line 127

**Change in `AudioPipeline.kt:166`:**
```kotlin
// Before:
} else if (PiperVoices.isPiperVoice(voiceId)) {
// After:
} else if (VoiceRegistry.isPiper(voiceId)) {
```

**Change in `PiperVoice.kt`:** Delete `isPiperVoice()` (line 127):
```kotlin
// Delete this line:
fun isPiperVoice(voiceId: String): Boolean = byId(voiceId) != null
```

**Rationale:** `VoiceRegistry` is the unified voice authority. Having a second check function in `PiperVoices` creates conceptual duplication.

**Effort:** Trivial (~5 min)  
**Risk:** None — functionally identical.

---

#### I-04 🟡 FIX: Remove deprecated genderColor from 3 voice classes

**File:** `KokoroVoice.kt` line 18 — delete `@Deprecated... val genderColor get() = ...`  
**File:** `PiperVoice.kt` lines 29–33 — delete the `@Deprecated` annotation and `val genderColor` getter  
**File:** `VoiceRegistry.kt` line 49 — delete `@Deprecated... val genderColor get() = ...`

**Effort:** Trivial (3 deletions)  
**Risk:** None — annotated as unused, grep confirms zero callers.

---

#### I-05 🟡 FIX: Document delegate constructor patterns (not refactor)

**Rationale for documentation over refactor:** The three delegates have genuinely different dependency needs. `NotificationRulesDelegate` needs only prefs. `WordRulesDelegate` needs a context and a container. `LanguageRoutingDelegate` needs context, prefs, and the AppContainer. Forcing a common base class would either over-inject dependencies or require making some optional. 

**Change:** Add a comment block in `RulesFragment.kt`:
```kotlin
// Delegates have intentionally different constructor signatures:
// - WordRulesDelegate(Context, SharedPreferences, LinearLayout) — needs direct container access for view building
// - NotificationRulesDelegate(SharedPreferences) — pure preference management, no view building
// - LanguageRoutingDelegate(Context, SharedPreferences, AppContainer) — needs container for NotificationTranslator
```

**Effort:** Trivial (documentation)  
**Risk:** None.

---

#### I-06 🟡 FIX: Extract shared preview playback method

**File:** `ProfilesFragment.kt`

**Change:** Extract the duplicated logic into a single method:
```kotlin
private fun playPreview(text: String, profile: VoiceProfile) {
    val ctx = requireContext()
    val service = NotificationReaderService.instance
    if (service != null) {
        service.speakDirect(text, profile)
    } else {
        c.audioPipeline.start(ctx)
        c.audioPipeline.enqueue(AudioPipeline.Item(
            text = text,
            voiceId = profile.voiceName,
            pitch = profile.pitch,
            speed = profile.speed
        ))
    }
}
```

Then replace `previewVoice()` body with:
```kotlin
private fun previewVoice(voiceId: String, name: String) {
    val previewText = txtPreview.text.toString().ifBlank { getString(R.string.preview_default, name) }
    playPreview(previewText, currentProfile.copy(voiceName = voiceId))
}
```

And `btnTest.setOnClickListener` body with:
```kotlin
val p = readProfileFromUI()
val text = txtPreview.text.toString().ifBlank { getString(R.string.preview_text) }
playPreview(text, p)
```

**Effort:** Small (~15 min)  
**Risk:** None — logic consolidation.

---

#### I-07 🟡 FIX: Plan SettingsRepository extraction (architectural — document path)

**Change:** This is an architectural improvement, not a quick fix. Document the extraction plan:

1. Create `SettingsRepository.kt` in the `kyokan` package
2. Move all SharedPreferences reads/writes behind its interface
3. Inject via `AppContainer`
4. Phase migration: start with `VoiceProfile` and `AppRule` persistence, then preferences

The immediate fix (before full extraction): add `AppContainer` methods that wrap common preference patterns:
```kotlin
class AppContainer(private val appContext: Context) {
    val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(appContext)
    }
    // ... existing fields ...
}
```

This at least provides a single access point without the full repository refactor.

**Effort:** Large (full extraction: 1-2 days)  
**Risk:** Medium — touches 11+ files. Must be done incrementally.

---

#### I-08 🟡 FIX: Rename onCreateView parameters to standard names

**File:** `HomeFragment.kt` line 38  
**Also:** `ProfilesFragment.kt`, `AppsFragment.kt`, `RulesFragment.kt`, `LogcatFragment.kt` — all use the same shorthand pattern

**Change in each fragment's `onCreateView`:**
```kotlin
// Before:
override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
    i.inflate(R.layout.fragment_home, c, false)

// After:
override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
    inflater.inflate(R.layout.fragment_home, container, false)
```

**Rationale:** Parameter `c` shadows the class property `val c by lazy { requireContext().container }`. Standard Android parameter names eliminate the confusion.

**Effort:** Small (~20 min, 5 files)  
**Risk:** None — rename only.

---

#### I-09 🟡 FIX: Wire up existing string resources (preferred over deletion)

**File:** `NotificationRulesDelegate.kt`, `WordRulesDelegate.kt`, `AppsFragment.kt`, `LanguageRoutingDelegate.kt`

**Changes (use the already-defined resources):**

In `NotificationRulesDelegate.kt`:
```kotlin
// Before:
txtCooldown.text = "Cooldown per app: ${cooldown}s"
// After:
txtCooldown.text = v.context.getString(R.string.cooldown_label, cooldown)
```
```kotlin
// Before:
txtMaxQueue.text = "Max queue size: $maxQueue"
// After:
txtMaxQueue.text = v.context.getString(R.string.max_queue_label, maxQueue)
```

In `AppsFragment.kt`:
```kotlin
// Before:
btn.text = "LOADING..."
// After:
btn.text = getString(R.string.loading)
```
```kotlin
// Before:
btn.text = "RELOAD APPS"
// After:
btn.text = getString(R.string.reload_apps)
```

The `find_hint` / `replace_hint` / `global` resources should be used in `WordRulesDelegate` and `AppRuleAdapter` respectively.

Delete `lang_route_label` — it's genuinely unused (the LanguageRoutingDelegate constructs the label inline with flag emojis).

**Effort:** Small (~30 min)  
**Risk:** None — using resources that were created for this purpose.

---

#### I-10 🟢 FIX: Add README note for kyokan-tts module

**File:** `kyokan-tts/README.md`

**Change:** Add a note at the top:
```markdown
> **Note:** This is a standalone server-side TTS routing module. The Android app (`app/`) does NOT 
> use this module — `CloudTtsEngine.kt` talks directly to DeepInfra's API. This module is for 
> future server-side TTS routing with multiple engine support.
```

**Effort:** Trivial  
**Risk:** None.

---

#### I-11 🟢 FIX: Keep VoiceRegistry.engines() with a TODO comment

**File:** `VoiceRegistry.kt` line 57

**Change:** Add a comment:
```kotlin
/** Returns engine filter options. Currently unused in UI — planned for future engine filter. */
fun engines(): List<String> = listOf("All", "Kokoro", "Piper", "Orpheus")
```

**Effort:** Trivial  
**Risk:** None.

---

#### I-12 🟢 FIX: Document Echolibrium vs Kyōkan naming

**File:** `README.md`

**Change:** Add under the heading:
```markdown
## Naming
- **Echolibrium** — project/repository name
- **Kyōkan (共感)** — product name shown to users (means "empathy/resonance" in Japanese)
- **Package:** `com.echolibrium.kyokan` — bridges both names
```

**Effort:** Trivial  
**Risk:** None.

---

### CATEGORY K — AI/LLM INTEGRATION

#### K-01 🟠 FIX: Add daily character counter for cloud TTS usage

**File:** `CloudTtsEngine.kt`

**Change:** Add usage tracking:
```kotlin
companion object {
    // ... existing constants ...
    private const val PREF_DAILY_CHARS = "cloud_tts_daily_chars"
    private const val PREF_DAILY_CHARS_DATE = "cloud_tts_daily_date"
    private const val DEFAULT_DAILY_LIMIT = 50_000  // ~$0.35/day at $7/million chars
}

private var dailyChars = 0
private var dailyDate = ""

fun trackUsage(prefs: android.content.SharedPreferences, charCount: Int): Boolean {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date())
    if (today != dailyDate) {
        dailyChars = 0
        dailyDate = today
    }
    dailyChars += charCount
    prefs.edit()
        .putInt(PREF_DAILY_CHARS, dailyChars)
        .putString(PREF_DAILY_CHARS_DATE, today)
        .apply()
    return dailyChars <= DEFAULT_DAILY_LIMIT
}
```

Call `trackUsage()` in `synthesize()` before making the API call. If the limit is exceeded, return `null` and fire `notifySynthesisError("Daily cloud TTS limit reached")`.

Add a "Daily usage: X/50K chars" display in the Profiles fragment API key dialog.

**Effort:** Medium (~1-2 hours)  
**Risk:** Low — additive feature. Default limit is generous (50K chars ≈ 25+ pages of text).

---

#### K-02 🟡 FIX: Add configurable cloud-to-local voice fallback

**File:** `AudioPipeline.kt` — `processItem()` method, cloud voice failure path

**Change:** After cloud synthesis fails, attempt local fallback:
```kotlin
// In processItem(), replace the cloud failure path:
val result: Pair<FloatArray, Int> = if (VoiceRegistry.isCloud(voiceId)) {
    synthesizeWithCloud(item.text, item)
        ?: run {
            Log.w(TAG, "Cloud voice $voiceId unavailable, trying local fallback")
            notifySynthesisError(voiceId, "Cloud voice failed — falling back to local")
            // Attempt Kokoro fallback
            val fallbackVoice = KokoroVoices.default()
            synthesizeWithKokoro(ctx, fallbackVoice.id, item.text, item.speed)
                ?: run {
                    notifySynthesisError(voiceId, "Cloud and local fallback both failed")
                    return
                }
        }
} else if ...
```

Add a SharedPreferences toggle `cloud_fallback_enabled` (default true) so users can disable this if they prefer silence over a different voice.

**Rationale:** Users who rely on cloud voices currently get silent notification drops when offline.

**Effort:** Small (~30 min)  
**Risk:** Low — fallback produces a different voice, but reading a notification in the "wrong" voice is better than not reading it at all.

---

### CATEGORY L — STANDARDIZATION & POLISH

#### L-01 🟠 FIX: Use existing string resources

Covered by I-09 above. Same fix — use the already-created `R.string.*` resources instead of hardcoded strings.

---

#### L-02 🟠 FIX: Create Kotlin-side dimen accessor and use it

**File:** New utility (e.g., add to a `DimenUtil.kt` or as an extension)

**Change:** Create a helper extension:
```kotlin
fun Context.dimenPx(@androidx.annotation.DimenRes id: Int): Int =
    resources.getDimensionPixelSize(id)
```

Then in programmatic view builders (`VoiceCardBuilder.kt`, `ProfileGridBuilder.kt`, `WordRulesDelegate.kt`, `LanguageRoutingDelegate.kt`), replace:
```kotlin
val dp = ctx.resources.displayMetrics.density
setPadding((8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
```
with:
```kotlin
setPadding(
    ctx.dimenPx(R.dimen.spacing_sm),   // 8dp
    ctx.dimenPx(R.dimen.spacing_md_lg), // 14dp
    ctx.dimenPx(R.dimen.spacing_sm),   // 8dp
    ctx.dimenPx(R.dimen.spacing_md)    // 12dp (closest)
)
```

**Rationale:** Bridges the gap between the XML dimen system and programmatic view building. Changes to spacing values propagate from `dimens.xml` to both XML and programmatic views.

**Effort:** Medium (~2 hours across 4 files)  
**Risk:** Low — verify visual appearance after each file.

---

#### L-03 🟡 FIX: Move default word rules to a resource

**File:** `WordRulesDelegate.kt` lines 17–26  
**File:** `app/src/main/res/values/strings.xml`

**Change:** Add default rules as string arrays:
```xml
<string-array name="default_word_rules_find">
    <item>WhatsApp</item>
    <item>Gmail</item>
    <item>lol</item>
    <item>tbh</item>
    <item>idk</item>
    <item>omg</item>
    <item>brb</item>
    <item>ngl</item>
    <item>https://</item>
    <item>http://</item>
</string-array>
<string-array name="default_word_rules_replace">
    <item>Message</item>
    <item>Email</item>
    <item>laugh out loud</item>
    <item>to be honest</item>
    <item>I don\'t know</item>
    <item>oh my god</item>
    <item>be right back</item>
    <item>not gonna lie</item>
    <item>link</item>
    <item>link</item>
</string-array>
```

Then in `WordRulesDelegate.setup()`:
```kotlin
if (rules.isEmpty()) {
    val finds = context.resources.getStringArray(R.array.default_word_rules_find)
    val replaces = context.resources.getStringArray(R.array.default_word_rules_replace)
    rules.addAll(finds.zip(replaces))
    saveRules()
}
```

**Rationale:** Default rules become localizable. French users could get French abbreviation expansions via `values-fr/strings.xml`.

**Effort:** Small (~20 min)  
**Risk:** None.

---

#### L-04 🟡 FIX: Extract shared CollapsibleSectionHelper

**File:** New `CollapsibleSectionHelper.kt`

**Change:**
```kotlin
object CollapsibleSectionHelper {
    fun setup(
        root: View,
        labelId: Int,
        sectionId: Int,
        title: String,
        context: Context
    ) {
        val label = root.findViewById<TextView>(labelId)
        val section = root.findViewById<View>(sectionId)
        label.contentDescription = "$title, collapsed. Tap to expand."
        label.setOnClickListener {
            val expanded = section.visibility == View.VISIBLE
            val parent = section.parent as? ViewGroup
            if (parent != null && AnimationUtil.areAnimationsEnabled(context)) {
                TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 250 })
            }
            section.visibility = if (expanded) View.GONE else View.VISIBLE
            label.text = "${if (expanded) "▸" else "▾"} $title"
            label.contentDescription = if (expanded) "$title, collapsed. Tap to expand."
                else "$title, expanded. Tap to collapse."
        }
    }
}
```

Replace the two duplicate implementations in `RulesFragment.setupCollapsible()` and `ProfilesFragment.setupCollapsibleSection()` with calls to `CollapsibleSectionHelper.setup(...)`.

**Rationale:** Eliminates code duplication and integrates the G-01 animation scale check.

**Effort:** Small (~20 min)  
**Risk:** None.

---

#### L-05 🟡 FIX: Use XML chip style for filter buttons

**File:** `ProfilesFragment.kt` — `filterBtn()` method  
**File:** `app/src/main/res/values/styles.xml`

**Change:** Add a filter chip style:
```xml
<style name="KyokanFilterChip">
    <item name="android:textSize">@dimen/text_label</item>
    <item name="android:minHeight">@dimen/touch_target_min</item>
    <item name="android:paddingStart">16dp</item>
    <item name="android:paddingEnd">16dp</item>
    <item name="android:fontFamily">sans-serif</item>
</style>
```

Then simplify `filterBtn()` to apply the style and only set the dynamic parts (active/inactive colors):
```kotlin
private fun filterBtn(label: String, active: Boolean, onClick: () -> Unit): Button {
    return Button(requireContext()).apply {
        // Core styling from style resource is applied via theme or constructor
        text = label
        setBackgroundColor(if (active) AppColors.filterActiveBg(requireContext()) else AppColors.surface(requireContext()))
        setTextColor(if (active) AppColors.primary(requireContext()) else AppColors.textDisabled(requireContext()))
        textSize = 11f
        val dp = requireContext().resources.displayMetrics.density
        setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        contentDescription = "Filter: $label${if (active) ", selected" else ""}"
        setOnClickListener { onClick() }
    }
}
```

**Effort:** Small (~20 min)  
**Risk:** None.

---

#### L-06 🟡 FIX: Use WeakReference for Fragment in DownloadDelegate

**File:** `DownloadDelegate.kt` line 13

**Change:**
```kotlin
class DownloadDelegate(
    fragment: Fragment,  // Not stored directly
    private val container: AppContainer,
    private val viewModel: ProfilesViewModel,
    private val onVoiceGridChanged: () -> Unit
) {
    private val fragmentRef = java.lang.ref.WeakReference(fragment)
    
    // Replace all `fragment.` accesses with safe unwrap:
    private inline fun withFragment(block: (Fragment) -> Unit) {
        val f = fragmentRef.get() ?: return
        if (f.isAdded) block(f)
    }
```

Then replace all `fragment.activity?.runOnUiThread` calls with `withFragment { it.activity?.runOnUiThread { ... } }`.

**Rationale:** Prevents potential Fragment leak if a callback retains the delegate past the fragment's lifecycle.

**Effort:** Small (~20 min)  
**Risk:** Low — functionally equivalent with added safety.

---

### CATEGORY N — INTERNATIONALIZATION

#### N-01 🟠 FIX: Externalize all remaining hardcoded strings

**Comprehensive list of changes needed:**

**File: `NotificationRulesDelegate.kt`:**
- `"Cooldown per app: ${value}s"` → `context.getString(R.string.cooldown_label, value)` (resource already exists)
- `"Max queue size: $value"` → `context.getString(R.string.max_queue_label, value)` (resource already exists)

**File: `AppsFragment.kt`:**
- `"LOADING..."` → `getString(R.string.loading)` (resource exists)
- `"RELOAD APPS"` → `getString(R.string.reload_apps)` (resource exists)
- `"No user apps found."` → `getString(R.string.no_user_apps)` (resource exists but unused)

**File: `WordRulesDelegate.kt`:**
- `"Find…"` → `context.getString(R.string.find_hint)` (resource exists)
- `"Replace…"` → `context.getString(R.string.replace_hint)` (resource exists)

**File: `HomeFragment.kt`:**
- `"Restricted ✓"` / `"✗ Restricted"` / `"✓ Battery"` / `"✗ Battery"` / `"✓ Notifications"` / `"✗ Notifications"` → Add new string resources:
```xml
<string name="status_restricted_ok">✓ Restricted</string>
<string name="status_restricted_fail">✗ Restricted</string>
<string name="status_battery_ok">✓ Battery</string>
<string name="status_battery_fail">✗ Battery</string>
<string name="status_notif_ok">✓ Notifications</string>
<string name="status_notif_fail">✗ Notifications</string>
```
- `"→ Head to Voices to download a voice model"` → New `R.string.guidance_download_voice`
- `"→ Set up a voice profile in the Profiles tab"` → New `R.string.guidance_setup_profile`
- `"→ Configure per-app rules in the Apps tab"` → New `R.string.guidance_configure_apps`
- `"What's next?"` → New `R.string.guidance_title`

**File: `LogcatFragment.kt`:**
- `"Failed to start logcat: ${e.message}"` → New `R.string.logcat_failed`

**Note:** This overlaps with I-09 and L-01. Resolving N-01 automatically resolves both of those.

**Effort:** Medium (~1-2 hours — many small changes)  
**Risk:** Low — string content is identical, just moved to resources.

---

#### N-02 🟡 FIX: Locale-aware DND time formatting

Covered by A-05 above (same fix).

---

#### N-03 🟡 FIX: Use setPaddingRelative in programmatic views

**Files:** `VoiceCardBuilder.kt`, `ProfileGridBuilder.kt`, `WordRulesDelegate.kt`, `LanguageRoutingDelegate.kt`

**Change:** Global search-and-replace within these files:
```kotlin
// Before:
setPadding(left, top, right, bottom)
// After:
setPaddingRelative(start, top, end, bottom)
```

Also replace margin assignments:
```kotlin
// Before:
it.setMargins(left, top, right, bottom)
// After:
it.marginStart = start; it.topMargin = top; it.marginEnd = end; it.bottomMargin = bottom
```

Or use `MarginLayoutParamsCompat.setMarginStart()` / `setMarginEnd()`.

**Rationale:** `setPadding(left, ...)` and `setMargins(left, ...)` don't mirror for RTL locales. `setPaddingRelative(start, ...)` does.

**Effort:** Medium (~1-2 hours — many small changes across 4 files)  
**Risk:** Low — verify layout in both LTR and RTL configurations.

---

### CATEGORY O — PROJECTIONS

#### O-01 🟠 FIX: Plan Room migration path (architectural — document strategy)

**This is not a quick fix but an architectural plan.** Document the migration strategy:

1. **Phase 1 (immediate, no Room):** Add a `DataVersion` integer to SharedPreferences. On every save, increment it. This enables future migration detection.

2. **Phase 2 (short-term):** Create Room entities for `VoiceProfile` and `AppRule`. These are the most complex data models and benefit most from structured queries.

3. **Phase 3 (medium-term):** Create a `KyokanDatabase` with migration from SharedPreferences:
```kotlin
@Database(entities = [VoiceProfileEntity::class, AppRuleEntity::class], version = 1)
abstract class KyokanDatabase : RoomDatabase() {
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun appRuleDao(): AppRuleDao
}
```

4. **Migration strategy:** On first launch after Room introduction, read from SharedPreferences, write to Room, then clear the SP keys. Keep a `migration_completed` flag.

5. **Keep SharedPreferences for:** Simple key-value settings (booleans, ints, theme preference). These don't benefit from Room's structured queries.

**Effort:** Large (1-2 days for Room setup + migration)  
**Risk:** Medium — data migration must be tested thoroughly.

---

#### O-02 🟡 FIX: Document upgrade path for Kotlin/AGP

**File:** `build.gradle` (root)

**Change:** Add a comment:
```groovy
plugins {
    // Upgrade path: Kotlin 2.0+ (K2 compiler) + AGP 8.5+
    // Prerequisites before upgrading:
    //   1. Test K2 compiler with `kotlin.experimental.tryK2=true` in gradle.properties
    //   2. Update deprecated API usage flagged by K2
    //   3. Verify sherpa-onnx AAR compatibility with newer AGP
    //   4. Run full regression test after upgrade
    id 'com.android.application' version '8.2.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
```

**Effort:** Trivial (documentation)  
**Risk:** None.

---

#### O-03 🟡 FIX: Document minimum test suite and priorities

**File:** New `docs/TESTING_PLAN.md` or added to README

**Change:** Document the priority test targets:

```markdown
## Minimum Test Suite (Priority Order)

### Unit Tests (JUnit)
1. **VoiceProfile serialization round-trip** — `toJson()` → `fromJson()` produces identical object
2. **AppRule serialization round-trip** — same pattern
3. **DND time logic** — `isDndActive()` for: same-hour (disabled), wrap-around midnight, normal range
4. **Word rule application** — case-insensitive matching, empty rules, overlapping rules
5. **buildMessage formatting** — all 4 read modes: full, title_only, app_only, text_only

### Integration Tests (Android Instrumented)
6. **Profile save/load cycle** — create profile, modify, save, reload, verify
7. **AudioPipeline crossfade** — verify no NaN/Infinity in output samples
8. **VoiceRegistry consistency** — all Kokoro + Piper IDs are unique, all have valid sample rates

### Manual Test Checklist
9. **Rotation on every screen** — tab state, profile edits, logcat scroll position
10. **Light mode on every screen** — no invisible text, no broken colors
```

**Effort:** Small (documentation now, implementation is separate effort)  
**Risk:** None.

---

### CROSS-CUTTING FINDINGS

#### X-01 🟠 FIX: Systematic light mode audit

**Resolution:** Resolved by the combination of:
- E-01 ✅ (already fixed — item_app_rule.xml)
- E-02 (add dark theme accent colors — fix documented above)
- E-03 (fix LogcatFragment hardcoded colors — fix documented above)
- E-04 (fix icon fillColor — fix documented above)

After applying E-02, E-03, and E-04, perform a visual verification pass on every screen in light mode:
1. Home tab — verify all text visible, setup button colors correct
2. Profiles tab — verify voice cards, filter buttons, profile grid
3. Apps tab — verify rule rows (already fixed), search bar, buttons
4. Rules tab — verify all collapsible sections, word rule rows, spinners
5. Logcat tab — verify all log level colors readable

**Effort:** Cumulative with E-02 + E-03 + E-04 fixes  
**Risk:** Low.

---

#### X-02 🟠 FIX: Complete string externalization

**Resolution:** Resolved by N-01 fix (which also resolves I-09 and L-01). The comprehensive string externalization documented under N-01 covers all known hardcoded strings.

**Effort:** Cumulative with N-01  
**Risk:** Low.

---

#### X-03 🟡 FIX: Standardize programmatic view building

**Resolution:** Resolved by the combination of:
- L-02 (Kotlin-side dimen accessor — fix documented above)
- L-04 (shared CollapsibleSectionHelper — fix documented above)
- L-05 (filter button style — fix documented above)
- E-05 (systematic dimen adoption — fix documented above)
- E-06 (KyokanButton style adoption — fix documented above)

The key enabler is L-02's `Context.dimenPx()` extension which bridges the XML design system to programmatic code.

**Effort:** Cumulative with the above fixes  
**Risk:** Low.

---

## FIX SOLUTIONS SUMMARY

| Category | Actionable Findings | Solutions Written | Positive/No-Action |
|----------|-------------------|-------------------|-------------------|
| A — Domain Logic | 5 | 5 | 0 |
| B — State & Data | 6 (after Phase 0) | 6 | 0 |
| C — Security | 4 | 4 | 2 (C-05, C-06) |
| D — Performance | 6 | 6 | 2 (D-07, D-08) |
| E — Visual Design | 6 (after Phase 0) | 6 | 1 (E-08) |
| F — UX & IA | 6 | 6 | 1 (F-07) |
| G — Accessibility | 3 | 3 | 1 (G-04) |
| I — Code Quality | 9 | 9 | 3 (I-10, I-11, I-12 — docs only) |
| K — AI/LLM | 2 | 2 | 1 (K-03) |
| L — Standardization | 6 | 6 | 1 (L-07) |
| N — i18n | 3 | 3 | 1 (N-04) |
| O — Projections | 3 | 3 | 1 (O-04) |
| Cross-cutting | 3 | 3 | 0 |
| **TOTAL** | **62** | **62** | **14** |

### Effort Breakdown

| Effort Level | Count | Examples |
|-------------|-------|---------|
| **Trivial** (<5 min each) | 18 | B-07, D-06, E-04, I-02, I-04, A-01 |
| **Small** (<1 hour each) | 25 | A-02, A-04, B-04, B-09, D-02, E-02, G-01, I-06 |
| **Medium** (<1 day each) | 14 | B-06, C-04, D-01, F-01, K-01, L-02, N-01, N-03 |
| **Large** (>1 day each) | 5 | I-07, O-01, B-10, E-05, O-03 |

### Recommended Implementation Order

**Batch 1 — Trivials (can do all in one sitting, ~1 hour total):**
A-01, B-04, B-07, D-06, E-04, I-02, I-04, E-07, F-02, I-08, I-10, I-11, I-12, C-03, O-02

**Batch 2 — Small wins (high impact, ~4-5 hours total):**
E-02, E-03, A-02+I-01, A-04, B-08, D-02, D-05, I-03, I-06, I-09+L-01, G-02, G-03, L-04

**Batch 3 — Medium structural (spread across sessions, ~2-3 days):**
D-01, G-01, B-06, B-09+D-03, A-03, C-01, N-01+X-02, K-02, F-01, F-03, L-02+X-03, E-05+E-06, N-03, L-03, L-05, L-06, F-05, F-06, C-04

**Batch 4 — Architectural (plan first, implement incrementally):**
I-07, O-01, B-10, K-01, O-03, C-02

---

*End of Full Audit 2*

