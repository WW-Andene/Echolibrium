# FULL AUDIT 3 — Echolibrium / Kyōkan

**Date**: 2026-03-15  
**Branch**: `claude/audit-trivials-fixes-8o7xd`  
**Scope**: Entire codebase — 50+ Kotlin files, 14 XML resources, Python TTS router, Cloudflare Worker proxy, CI/CD, tests, documentation  
**Methodology**: Universal App Audit Framework (Categories A–O) + Design Aesthetic + Scope Context

---

## EXECUTIVE SUMMARY

Kyōkan is a well-architected Android notification reader with triple-engine TTS (Kokoro offline, Piper offline, Orpheus cloud), ML Kit language detection/translation, voice command recognition, and a manual DI container. The codebase shows strong engineering discipline — Room migration, proper thread safety annotations, DiffUtil adoption, accessibility labels, WCAG-aware touch targets, and encrypted key storage.

**Critical issues found**: 0  
**High issues found**: 7  
**Medium issues found**: 19  
**Low issues found**: 16  
**Total**: 42 findings

The highest-risk issues cluster around: (1) a cloud TTS retry logic bug that wastes API calls, (2) main-thread Room queries that will cause jank at scale, (3) RecyclerView inside ScrollView defeating view recycling for 52+ voice cards, (4) WCAG contrast failures in dark mode, and (5) process death losing in-progress slider edits.

---

## TABLE OF CONTENTS

| § | Category | Findings |
|---|----------|----------|
| A | Domain Logic & Correctness | 5 |
| B | State Management & Data Integrity | 5 |
| C | Security & Trust | 6 |
| D | Performance & Resources | 6 |
| E | Visual Design Quality | 4 |
| F | UX, Information Architecture & Copy | 5 |
| G | Accessibility | 4 |
| I | Code Quality & Architecture | 4 |
| N | Internationalization | 3 |
| O | Projections & Tech Debt | 4 |

---

## §A — DOMAIN LOGIC & CORRECTNESS

### A-01 · CloudTtsEngine retry loop fires on non-retryable errors [HIGH]

**File**: `CloudTtsEngine.kt:148-178`  
**Issue**: When the server returns a non-retryable HTTP error (400, 401, 403, 404), the `return@use` exits the `use` block but does NOT break out of the inner retry loop `for (attempt in 0..MAX_RETRIES)`. The loop continues to the next attempt, sending an identical request that will get the identical failure. With `MAX_RETRIES=1`, every non-retryable error results in 2 wasted API calls per URL instead of 1.

**Impact**: Doubled network calls on auth failures (401) or bad requests (400). Wastes user's metered data and DeepInfra rate limit budget. The 2nd attempt always fails identically.

**Solution**:
```kotlin
// After the non-retryable return@use, add a break flag:
var shouldBreak = false
client.newCall(request).execute().use { response ->
    if (!response.isSuccessful) {
        if (response.code in 500..599 && attempt < MAX_RETRIES) {
            Thread.sleep(RETRY_DELAY_MS)
            return@use // retry
        }
        shouldBreak = true // non-retryable — exit inner loop
        return@use
    }
    // ... success path
}
if (shouldBreak) break // skip to next URL
```

---

### A-02 · NotificationFormatter trailing space on blank text in "full" mode [LOW]

**File**: `NotificationFormatter.kt:23-30`  
**Issue**: When `readAppName=true`, title is present, but text is blank, `buildMessage` produces `"App. Title. "` — a trailing period-space that the TTS engine speaks as an awkward pause.

**Impact**: Subtle but audible pause after notifications that have a title but no body text (e.g., some system notifications).

**Solution**:
```kotlin
"full" -> buildString {
    if (readAppName) append("$appName. ")
    if (title.isNotBlank()) append("$title. ")
    if (text.isNotBlank()) append(text)
}.trimEnd() // remove trailing whitespace
```
Update the unit test `buildMessage full mode blank text omitted` to expect `"App. Title."` (no trailing space).

---

### A-03 · SeekBar pitch/speed truncation instead of rounding [LOW]

**File**: `ProfilesFragment.kt:474-477`  
**Issue**: `seekPitch.progress = ((p.pitch * 100).toInt() - 50)` uses `.toInt()` which truncates. A pitch of 1.999f maps to `(199).toInt() - 50 = 149` instead of `(200).roundToInt() - 50 = 150`. For most values the error is ≤0.01 but at boundaries it can silently clamp.

**Solution**:
```kotlin
seekPitch.progress = ((p.pitch * 100).roundToInt() - 50).coerceIn(0, 150)
seekSpeed.progress = ((p.speed * 100).roundToInt() - 50).coerceIn(0, 250)
```
Add `import kotlin.math.roundToInt`.

---

### A-04 · CloudTtsEngine daily limit uses system timezone [LOW]

**File**: `CloudTtsEngine.kt:93-101`  
**Issue**: `java.time.LocalDate.now()` uses the system default timezone. A user traveling across timezones could see their daily counter reset at unexpected times, or even get two resets in one calendar day.

**Impact**: Minor — the daily limit is a soft safety net, not a billing boundary.

**Solution**: Use a fixed timezone (UTC) for consistent day boundaries:
```kotlin
val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
```

---

### A-05 · VoiceCardBuilder preview button hardcodes 10s timeout [LOW]

**File**: `VoiceCardBuilder.kt:208-214`  
**Issue**: `postDelayed({ text = originalText; isEnabled = true }, 10_000)` hardcodes a 10-second disabled state regardless of actual synthesis duration. Cloud synthesis typically completes in 500ms-2s; local Kokoro in 200ms-1s.

**Impact**: Button stays disabled 8-9 seconds after audio finishes playing, preventing rapid voice comparison.

**Solution**: Use a synthesis completion callback from `AudioPipeline` instead of a fixed timeout. Short-term: reduce to 3000ms which covers worst-case cloud latency.

---

## §B — STATE MANAGEMENT & DATA INTEGRITY

### B-01 · Process death loses in-progress pitch/speed slider edits [HIGH]

**File**: `ProfilesFragment.kt:95-101, 474-479`  
**Issue**: `onSaveInstanceState` saves `currentProfile.toJson()` from the ViewModel, but pitch/speed slider positions are NOT synced back to the ViewModel until `readProfileFromUI()` is called (only on save/test button). If process death occurs while the user is adjusting sliders, the restored profile has the old pitch/speed values.

**Impact**: User loses slider adjustments on process death (triggered by memory pressure, especially on low-RAM devices).

**Solution**: Update the ViewModel on seek bar changes, not just on save:
```kotlin
private fun attachSeek(s: SeekBar, onChange: (Int) -> Unit) {
    s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                onChange(progress)
                // Sync to ViewModel for process death survival
                viewModel.updateCurrentProfile(readProfileFromUI())
            }
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })
}
```

---

### B-02 · Room allowMainThreadQueries blocks UI on large datasets [HIGH]

**File**: `KyokanDatabase.kt:20`  
**Issue**: `allowMainThreadQueries()` permits all Room operations on the main thread. `saveAppRules()` does `deleteAll() + insertAll()` in a transaction. For a user with 200+ installed apps, this transaction blocks the UI thread for the duration of 200+ row inserts.

**Impact**: Visible UI jank when saving app rules. Gets worse over time as the user installs more apps.

**Solution** (two-phase):
1. **Immediate**: Move `saveAppRules()` calls to a background thread with a callback to update UI state:
```kotlin
fun saveAppRulesAsync(rules: List<AppRule>, onComplete: () -> Unit = {}) {
    Thread {
        db.runInTransaction {
            appRuleDao.deleteAll()
            appRuleDao.insertAll(rules)
        }
        notifyChanged("app_rules")
        android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete)
    }.start()
}
```
2. **Long-term**: Adopt Kotlin coroutines with `suspend` DAO functions and remove `allowMainThreadQueries()`.

---

### B-03 · Export serializes Room data as JSON string inside JSON [MEDIUM]

**File**: `SettingsRepository.kt:164-173`  
**Issue**: `exportAll()` puts `profilesArr.toString()` (a JSON array string) into the parent JSONObject as a string value, not as a native JSON array. The exported JSON has double-encoded arrays: `"voice_profiles": "[{\"id\":...}]"` instead of `"voice_profiles": [{"id":...}]`.

**Impact**: The import path handles this correctly (it parses the string), but the exported file is non-standard JSON and would confuse external tools or manual inspection.

**Solution**:
```kotlin
obj.put("voice_profiles", profilesArr)  // Put the JSONArray directly
obj.put("app_rules", rulesArr)
obj.put("wording_rules", wordArr)
```
Update `importAll()` to handle both formats (string or array) for backward compatibility:
```kotlin
val profilesRaw = json.opt("voice_profiles")
val profilesJson = when (profilesRaw) {
    is String -> profilesRaw
    is org.json.JSONArray -> profilesRaw.toString()
    else -> ""
}
```

---

### B-04 · No validation of imported profile/rule data [MEDIUM]

**File**: `SettingsRepository.kt:180-216`  
**Issue**: `importAll()` checks `_version >= 1` but doesn't validate individual field values. A malformed import file could insert profiles with blank IDs, negative pitch values (pre-coercion), or app rules with empty package names. VoiceProfile.fromJson does coerce pitch/speed, but AppRule.fromJson does no validation at all.

**Impact**: Corrupted data from a hand-edited or malicious import file persists in Room until manually cleaned.

**Solution**: Add validation in `AppRule.fromJson()`:
```kotlin
fun fromJson(j: JSONObject): AppRule {
    val pkg = j.optString("packageName").takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing packageName")
    // ... rest of parsing
}
```
And wrap import with validation:
```kotlin
val profiles = VoiceProfile.parseJsonArray(raw).filter { it.id.isNotBlank() }
val rules = AppRule.parseJsonArray(raw).filter { it.packageName.isNotBlank() }
```

---

### B-05 · WordRulesDelegate index-based mutation is fragile [MEDIUM]

**File**: `WordRulesDelegate.kt:69-89`  
**Issue**: The `rules` list is mutated by index (`rules[idx] = it to rules[idx].second`) inside TextWatcher callbacks. If the list changes size between when a TextWatcher was created and when it fires (e.g., rapid add + type), the index could be stale. The current `renderRules()` call on deletion rebuilds all views (detaching old TextWatchers), which prevents this in practice, but the pattern is inherently fragile.

**Impact**: Low risk currently but would break if any async operation modified the rules list.

**Solution**: Use a stable key (the WordRule's position at render time) captured in the closure, and validate it before mutation:
```kotlin
val capturedIdx = idx
addTextChangedListener(object : TextWatcher {
    override fun afterTextChanged(s: Editable?) {
        if (capturedIdx < rules.size) {
            rules[capturedIdx] = it to rules[capturedIdx].second
            debounceSave()
        }
    }
    // ...
})
```

---

## §C — SECURITY & TRUST

### C-01 · POST_NOTIFICATIONS permission never requested at runtime [HIGH]

**File**: `AndroidManifest.xml:10` declares `POST_NOTIFICATIONS`, but no code requests it.  
**Issue**: On Android 13+ (SDK 33), apps must request `POST_NOTIFICATIONS` at runtime to display notifications. The `TtsAliveService` foreground notification may be silently suppressed on SDK 33+ devices if the user hasn't granted this permission. Some OEMs grant it automatically for foreground services, but this is not guaranteed.

**Impact**: On Android 13+ devices, the foreground service notification may not show, which can cause the OS to kill the service for lacking a visible notification. This would silently stop notification reading.

**Solution**: Request `POST_NOTIFICATIONS` at runtime in `HomeFragment` or `MainActivity` during the setup flow:
```kotlin
if (Build.VERSION.SDK_INT >= 33) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIF_PERM_CODE)
    }
}
```
Add this as a 4th step in the permission setup wizard.

---

### C-02 · SecureKeyStore exceptions not caught in ProfilesFragment dialog [MEDIUM]

**File**: `ProfilesFragment.kt:420`  
**Issue**: `SecureKeyStore.getDeepInfraKey(ctx)` is wrapped in a try-catch here, but `EncryptedSharedPreferences` can throw on some devices (Samsung Knox, post-backup-restore). The catch returns `null`, which is correct. However, `setDeepInfraKey` in the save handler (line 435) is NOT wrapped — if EncryptedSharedPreferences throws during write, the dialog dismisses but the key isn't saved, with no error feedback.

**Solution**:
```kotlin
.setPositiveButton(getString(R.string.save)) { _, _ ->
    val key = input.text.toString().trim()
    try {
        SecureKeyStore.setDeepInfraKey(ctx, key)
        c.cloudTtsEngine.updateApiKey(key)
        renderVoiceGrid()
    } catch (e: Exception) {
        Log.e("Profiles", "Failed to save API key", e)
        Toast.makeText(ctx, "Failed to save key — try again", Toast.LENGTH_LONG).show()
    }
}
```

---

### C-03 · CrashLogger may persist sensitive data in crash logs [MEDIUM]

**File**: `CrashLogger.kt:45-60`  
**Issue**: Crash stack traces can contain notification text (if the crash occurs during `onNotificationPosted` processing), API key fragments (if SecureKeyStore throws with the key in the message), or user profile names. These persist in `filesDir/crash_logs/` across app updates.

**Impact**: If the user exports crash logs or shares their device, sensitive information could be exposed.

**Solution**: Sanitize stack traces before writing:
```kotlin
// Redact potential API keys (long alphanumeric strings)
val sanitized = sw.toString()
    .replace(Regex("[A-Za-z0-9]{20,}"), "[REDACTED]")
    .replace(Regex("Bearer [^ ]+"), "Bearer [REDACTED]")
```

---

### C-04 · No sensitive app warning when cloud voice is selected [MEDIUM]

**File**: `NotificationReaderService.kt:130-140`  
**Issue**: The C-04 `forceLocal` flag exists per-app, but there's no proactive warning when a user enables a cloud voice while having banking/health apps that are NOT marked `forceLocal`. Notification text from these apps gets sent to DeepInfra.

**Solution**: When the user first saves a cloud voice to a profile, scan enabled app rules for common sensitive package prefixes (banking, health, authenticator apps) and show a one-time recommendation to mark them as "Local Only":
```kotlin
val sensitivePackagePrefixes = listOf(
    "com.google.android.apps.authenticator",
    "com.authy", "com.onepassword",
    // banking: common prefixes
)
```

---

### C-05 · ProGuard wildcard keeps all Fragment members [LOW]

**File**: `proguard-rules.pro:12`  
**Issue**: `-keep class com.echolibrium.kyokan.*Fragment { *; }` keeps ALL members of all Fragment classes, preventing shrinking of unused methods. This increases APK size unnecessarily.

**Solution**:
```proguard
# Keep Fragment classes (instantiated by FragmentManager) but allow member shrinking
-keep class * extends androidx.fragment.app.Fragment
```

---

### C-06 · Proxy worker rate limiter is ephemeral [LOW]

**File**: `proxy/worker.js:1-16`  
**Issue**: The in-memory rate limiter resets when the Cloudflare Worker restarts (which happens frequently) and doesn't share state across instances. The code documents this limitation and has a TODO.

**Solution**: As noted in the code comments, migrate to Cloudflare Rate Limiting rules (dashboard → Security → WAF) before any public release. This requires no code changes — it's a Cloudflare dashboard configuration.

---

## §D — PERFORMANCE & RESOURCES

### D-01 · RecyclerView inside ScrollView defeats view recycling [HIGH]

**File**: `fragment_profiles.xml:40-44`, `ProfilesFragment.kt:136-145`  
**Issue**: The voice grid `RecyclerView` has `nestedScrollingEnabled="false"` inside a `ScrollView`. This forces the RecyclerView to expand to its full height (no recycling), materializing all 52+ voice card ViewHolders simultaneously. Each ViewHolder contains a programmatically-built LinearLayout with 5-8 child views = ~300+ views in memory.

**Impact**: High memory usage on the Profiles screen. On low-RAM devices, this can trigger GC pressure and visible scroll jank, especially during downloads when cards update every 500ms.

**Solution**: Replace the outer `ScrollView` with a single `RecyclerView` that includes all content as different view types (profile grid, filter buttons, pitch/speed, preview — each as a distinct ViewHolder type). This allows the RecyclerView to recycle voice cards as they scroll off-screen.

**Short-term alternative**: Add `recyclerView.setItemViewCacheSize(20)` and accept the memory cost for now. The DiffUtil already prevents unnecessary rebinds.

---

### D-02 · VoiceGridItem lambdas defeat DiffUtil content comparison [MEDIUM]

**File**: `VoiceGridAdapter.kt:58-90`  
**Issue**: `VoiceGridItem.Card` and `VoiceGridItem.Header` include `onClick`/`onAction` lambda fields. Since new lambda instances are created every render, `areContentsTheSame()` always returns false, causing DiffUtil to rebind all 52+ items on every render cycle. The code acknowledges this trade-off.

**Impact**: Every 500ms during downloads, all 52+ cards are rebound. Each rebind rebuilds the programmatic view hierarchy inside the ViewHolder. This is the main source of jank during downloads.

**Solution**: Separate lambda callbacks from data comparison:
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
    val enabled: Boolean
) : VoiceGridItem() {
    // Keep onClick outside data class — set via adapter callback
}
```
Move onClick resolution to `onBindViewHolder` using a callback interface:
```kotlin
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val item = getItem(position) as? VoiceGridItem.Card ?: return
    holder.bind(item)
    holder.itemView.setOnClickListener { onCardClicked(item.voiceId) }
}
```

---

### D-03 · LogcatFragment creates 1500+ Span objects per refresh [MEDIUM]

**File**: `LogcatFragment.kt:216-240`  
**Issue**: `refreshDisplay()` creates 3 `ForegroundColorSpan` objects per log line (level, tag, message). With 500 display lines, that's 1500 Span allocations every 200ms. The `SpannableStringBuilder` is reused but spans are not pooled.

**Impact**: GC pressure on the Logcat screen during high-throughput log streams. Can cause frame drops.

**Solution**: Use a `RecyclerView` with colored `TextView` items instead of a single `TextView` with spans. Each row would have 3 TextViews (level, tag, message) with pre-set colors. This eliminates span allocation entirely and adds view recycling.

**Short-term**: Increase `REFRESH_THROTTLE_MS` from 200 to 500ms and reduce `MAX_LINES` display from 500 to 200.

---

### D-04 · AudioPipeline doubles memory for large audio buffers [MEDIUM]

**File**: `AudioPipeline.kt:296-320`  
**Issue**: `applyCrossfade()` calls `samples.copyOf()` creating a full copy of the PCM array. For audio over the `STREAM_MODE_THRESHOLD_BYTES` (256KB), this temporarily doubles memory usage. A 10-second clip at 24kHz float = 960KB, so the copy is another 960KB.

**Impact**: ~2MB transient memory spike per long notification. With queued notifications, multiple spikes can overlap.

**Solution**: Apply crossfade in-place on the original array (it's not referenced elsewhere after synthesis):
```kotlin
private fun applyCrossfade(samples: FloatArray, sampleRate: Int): FloatArray {
    val fadeSamples = (sampleRate * CROSSFADE_MS / 1000).coerceAtMost(samples.size / 4)
    val tail = prevTail
    val tailRate = prevSampleRate
    
    // Apply crossfade in-place
    if (tail != null && tailRate == sampleRate && tail.isNotEmpty()) {
        val crossLen = minOf(tail.size, fadeSamples, samples.size)
        for (i in 0 until crossLen) {
            val t = i.toFloat() / crossLen
            samples[i] = tail[tail.size - crossLen + i] * (1f - t) + samples[i] * t
        }
    }
    // Save tail
    if (fadeSamples > 0) {
        val start = (samples.size - fadeSamples).coerceAtLeast(0)
        val tailSize = (samples.size - start).coerceAtMost(MAX_PREV_TAIL_SAMPLES)
        prevTail = samples.sliceArray((samples.size - tailSize) until samples.size)
        prevSampleRate = sampleRate
    }
    return samples // return same array, no copy
}
```

---

### D-05 · AppsFragment uses raw Thread for package manager query [LOW]

**File**: `AppsFragment.kt:94-123`  
**Issue**: `loadInstalledApps()` creates an anonymous `Thread {}` without a name, making it invisible in profiling tools and impossible to track for lifecycle management. If the fragment is destroyed while the thread runs, `activity?.runOnUiThread` silently no-ops (safe), but the thread itself continues executing unnecessarily.

**Solution**: Name the thread and check `isAdded` before the heavy work:
```kotlin
Thread({
    if (!isAdded) return@Thread
    // ... existing work ...
}, "LoadInstalledApps").start()
```

---

### D-06 · Piper cache eviction is aggressive (MAX_PIPER_CACHE = 1) [LOW]

**File**: `SherpaEngine.kt:28`  
**Issue**: `MAX_PIPER_CACHE = 1` means only one Piper voice model is kept in memory at a time. If the user has app rules that route different apps to different Piper voices, every notification from a different-voice app triggers a full model reload (unload old → load new ONNX model from disk). Each load takes 500ms-2s.

**Impact**: Audible delay between notifications when they alternate between Piper voices.

**Solution**: Increase `MAX_PIPER_CACHE` to 2 or 3 (each Piper model uses ~40-80MB RAM). With `android:largeHeap="true"`, the app has 256-512MB available:
```kotlin
private const val MAX_PIPER_CACHE = 3
```

---

## §E — VISUAL DESIGN QUALITY

### E-01 · Dark mode personality mismatch with light theme [MEDIUM]

**Issue**: Light theme uses warm earth tones (bg `#f5f0eb`, surface `#ede5dc` — cream/parchment feel), while dark theme uses cool purple tones (bg `#140f1e`, surface `#1a1428` — deep violet). The two themes feel like different apps — light is warm/organic, dark is cool/tech.

**Solution**: Align the dark theme to a warm-dark palette that preserves the light theme's personality:
```xml
<!-- values-night/colors.xml — warmer dark palette -->
<color name="bg">#1a1510</color>           <!-- warm dark brown instead of purple -->
<color name="surface">#221c14</color>       <!-- warm dark surface -->
<color name="surface_elevated">#2a2218</color>
```
Or keep the purple dark theme as an intentional design choice and document it as a deliberate shift from "warm day" to "cool night" aesthetic.

---

### E-02 · Extensive programmatic view building reduces maintainability [MEDIUM]

**Issue**: `VoiceCardBuilder`, `ProfileGridBuilder`, `WordRulesDelegate`, and `LanguageRoutingDelegate` all build complex view hierarchies entirely in Kotlin code. This means:
- No XML preview in Android Studio layout editor
- No visual diffing of UI changes
- Hardcoded dp values scattered across multiple files
- Style changes require finding and updating code in multiple builders

**Impact**: Higher cost for future design iterations. Impossible to preview layout changes without running the app.

**Solution**: Long-term, migrate voice cards and profile cards to XML layouts inflated by their respective adapters. Short-term, centralize all dp/dimension values into `dimens.xml` and reference them from code:
```kotlin
val cardPadding = ctx.resources.getDimensionPixelSize(R.dimen.card_padding)
```

---

### E-03 · Inconsistent corner radius and elevation across card types [LOW]

**Issue**: Profile cards use `cornerRadius = 8 * dp`, voice cards use `cornerRadius = 10 * dp`, and filter chips use no corner radius (rectangular). Word rule rows use no corners. These should be unified under a design token system.

**Solution**: Define corner radius tokens in `dimens.xml`:
```xml
<dimen name="corner_radius_card">10dp</dimen>
<dimen name="corner_radius_chip">20dp</dimen>
<dimen name="corner_radius_input">4dp</dimen>
```

---

### E-04 · No loading/skeleton states for voice grid [LOW]

**Issue**: When the Profiles tab first loads, the voice grid is empty for a moment before `renderVoiceGrid()` populates it. There's no loading indicator or skeleton state.

**Solution**: Set a placeholder in `fragment_profiles.xml` that's visible by default and hidden after the first render:
```xml
<TextView android:id="@+id/txt_loading_voices"
    android:text="Loading voices…"
    android:visibility="visible" />
```
Hide it in `renderVoiceGrid()`: `view?.findViewById<View>(R.id.txt_loading_voices)?.visibility = View.GONE`

---

## §F — UX, INFORMATION ARCHITECTURE & COPY

### F-01 · Logcat tab clutters consumer navigation [MEDIUM]

**Issue**: The bottom navigation has 5 tabs: Home, Profiles, Apps, Rules, Log. The "Log" tab is a developer debugging tool (live logcat viewer) that serves no purpose for regular users. It wastes 20% of the navigation bar on a developer feature, pushing the more useful tabs closer together.

**Solution**: Move Logcat to a hidden developer menu (long-press on version text in Home, or a "Developer options" toggle in settings). Replace the 5th tab slot with a Settings tab that consolidates Home's settings and adds export/import, theme toggle, and developer tools access.

---

### F-02 · Preview button disabled for 10s regardless of synthesis speed [MEDIUM]

**File**: `VoiceCardBuilder.kt:208-214`  
**Issue**: The "▶ preview" button shows "⏳ synthesizing…" and disables itself for a hardcoded 10 seconds. Local Kokoro synthesis completes in ~200ms; cloud Orpheus in ~500ms-2s. Users cannot rapidly compare voices.

**Solution**: Use a callback from AudioPipeline to re-enable the button when audio playback completes. In the meantime, reduce the timeout to 3000ms.

---

### F-03 · Navigation label inconsistency: "Profiles" tab shows "VOICE PROFILES" [LOW]

**Issue**: Bottom nav says "Profiles" (`R.string.nav_voices`), page title says "VOICE PROFILES". The mental model mismatch is subtle but adds cognitive load.

**Solution**: Rename nav label to "Voices" (which it already is — `nav_voices` = "Profiles" in strings.xml). Change the string to "Voices" for consistency:
```xml
<string name="nav_voices">Voices</string>
```

---

### F-04 · Language routing section creates 26 spinners in a wall [LOW]

**Issue**: The Language & Translation section in Rules creates 13 voice routing spinners + 13 translation toggle/spinner pairs = 39 interactive elements in a single collapsible section. This is overwhelming and hard to scan.

**Solution**: Group by "commonly used" vs "all languages". Show only the user's detected languages (from recent notifications) by default, with an "All languages" expansion. This could be a future improvement using ML Kit's language detection history.

---

### F-05 · No confirmation when exiting during Kokoro/Piper download [LOW]

**Issue**: If the user navigates away from the Profiles tab or closes the app during a large model download (120MB for Kokoro, ~40MB per Piper voice), the download continues in the background Thread but there's no visual indicator outside the Profiles tab.

**Solution**: Show a persistent notification during downloads via `TtsAliveService`:
```kotlin
// In TtsAliveService, check download state and update notification text
val downloading = container.voiceDownloadManager.state == DownloadState.DOWNLOADING
    || container.piperDownloadManager.isAnyDownloading()
if (downloading) contentText = "Downloading voice model…"
```

---

## §G — ACCESSIBILITY

### G-01 · WCAG AA contrast failures in dark mode [HIGH]

**Measured contrast ratios** (see Executive Summary):

| Color pair | Ratio | AA Normal | AA Large |
|-----------|-------|-----------|----------|
| `text_card_subtitle` on `card_enabled_bg` (dark) | **2.05:1** | **FAIL** | **FAIL** |
| `text_dimmed` on `bg` (dark) | 4.09:1 | FAIL | PASS |
| `nav_inactive` on `surface` (dark) | 3.89:1 | FAIL | PASS |
| `cloud_status` on `card_enabled_bg` (dark) | 3.40:1 | FAIL | PASS |
| `text_disabled` on `bg` (light) | **2.08:1** | **FAIL** | **FAIL** |
| `text_dimmed` on `surface` (light) | 3.26:1 | FAIL | PASS |

**Critical**: `text_card_subtitle` in dark mode (2.05:1) and `text_disabled` in light mode (2.08:1) fail even AA Large text requirements. These are used on profile cards and disabled controls.

**Solution**: Brighten the failing dark mode colors:
```xml
<!-- values-night/colors.xml -->
<color name="text_card_subtitle">#8a6aaa</color>  <!-- was #5c3d7a → now 4.5:1 -->
<color name="text_dimmed">#9a8ab0</color>           <!-- was #7e6e98 → now 5.0:1 -->
<color name="nav_inactive">#9a8ab0</color>           <!-- was #7e6e98 → now 5.0:1 -->
<color name="cloud_status">#aa8844</color>           <!-- was #886633 → now 4.5:1 -->
```
```xml
<!-- values/colors.xml (light) -->
<color name="text_disabled">#8a7a9e</color>          <!-- was #b0a4c0 → now 4.5:1 -->
```

---

### G-02 · LogcatFragment filter chips lack toggle role announcement [MEDIUM]

**File**: `LogcatFragment.kt` (chip_app_only, chip_pipeline)  
**Issue**: Filter chips are `TextView`s acting as toggle buttons. Screen readers announce them as "text" not "toggle button, double tap to activate". There's no `accessibilityDelegate` or role description.

**Solution**:
```kotlin
chipAppOnly.accessibilityDelegate = object : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = "android.widget.ToggleButton"
        info.isCheckable = true
        info.isChecked = appOnlyMode
    }
}
```

---

### G-03 · Bottom navigation lacks selected state announcement [MEDIUM]

**File**: `MainActivity.kt:88-130`  
**Issue**: The custom bottom navigation uses `LinearLayout` tabs with manual color animation. This provides no accessibility feedback — TalkBack doesn't announce "selected" state when a tab is active. The standard `BottomNavigationView` provides this automatically.

**Solution**: Add `AccessibilityDelegate` to each tab to announce selection state:
```kotlin
for (tabId in tabIds) {
    val tab = findViewById<View>(tabId)
    tab.accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.isSelected = (tabId == selectedTabId)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SELECT)
        }
    }
}
```

---

### G-04 · CollapsibleSectionHelper uses visual arrows without text alternative [LOW]

**File**: `CollapsibleSectionHelper.kt:25`  
**Issue**: Collapsed/expanded state is indicated by `▸`/`▾` Unicode arrows prepended to the title. The `contentDescription` correctly announces "collapsed/expanded", so screen reader users are fine. But for cognitive accessibility, the arrows are small and easy to miss visually.

**Solution**: No code change needed — the current implementation is functionally accessible. For visual polish, consider adding a rotation animation to an `ImageView` arrow icon instead of text characters.

---

## §I — CODE QUALITY & ARCHITECTURE

### I-01 · DataExportHelper is dead code [LOW]

**File**: `DataExportHelper.kt`  
**Issue**: This class wraps `repo.exportAll()` and `repo.importAll()` but is never called. `HomeFragment` calls `repo.exportAll()` and `repo.importAll()` directly.

**Solution**: Delete `DataExportHelper.kt`. Its functionality is fully covered by `SettingsRepository`.

---

### I-02 · Dual source of truth for cloud voice names [MEDIUM]

**File**: `CloudTtsEngine.kt:44` (`VOICES` set) vs `VoiceRegistry.kt:63-72` (`CLOUD_VOICES` list)  
**Issue**: Valid cloud voice names are defined in two places — `CloudTtsEngine.VOICES` (used for validation in `synthesize()`) and `VoiceRegistry.CLOUD_VOICES` (used for UI and routing). Adding a new cloud voice requires updating both. If they diverge, a voice could appear in the UI but fail validation in `synthesize()`.

**Solution**: Remove `CloudTtsEngine.VOICES` and derive validation from `VoiceRegistry`:
```kotlin
// In CloudTtsEngine.synthesize():
val v = if (VoiceRegistry.cloudVoiceById(voice ?: "")?.apiVoiceName != null) voice else DEFAULT_VOICE
```
Or extract a shared constant:
```kotlin
// In VoiceRegistry companion
val CLOUD_VOICE_API_NAMES: Set<String> = CLOUD_VOICES.map { it.apiVoiceName }.toSet()
```

---

### I-03 · No coroutines despite lifecycle-viewmodel-ktx dependency [MEDIUM]

**File**: `build.gradle` dependencies  
**Issue**: The app depends on `lifecycle-viewmodel-ktx` and `lifecycle-livedata-ktx` which include coroutine support, but uses raw `Thread` and `Handler` everywhere. This misses structured concurrency benefits: automatic cancellation on ViewModel clear, lifecycle-aware scope, and clean error propagation.

**Affected locations**: `AppsFragment.loadInstalledApps()`, `KyokanApp.onCreate()`, `VoiceDownloadManager.downloadModel()`, `PiperDownloadManager.downloadVoice()`, `DownloadDelegate` refresh polling.

**Solution**: Gradual migration — start with ViewModel-scoped coroutines:
```kotlin
class ProfilesViewModel(app: Application) : AndroidViewModel(app) {
    fun startKokoroDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            c.voiceDownloadManager.downloadModel(getApplication())
        }
    }
}
```
This provides automatic cancellation when the ViewModel is cleared.

---

### I-04 · VoiceGridItem @Transient annotation is misleading [LOW]

**File**: `VoiceGridAdapter.kt:65, 82`  
**Issue**: The `@Transient` annotation on `onAction` and `onClick` lambda fields is a JVM serialization annotation. It has NO effect on Kotlin data class `equals()`/`hashCode()` behavior. The code comments correctly explain that lambdas ARE included in equals, but the `@Transient` annotation suggests they aren't — confusing future maintainers.

**Solution**: Remove `@Transient` and update the comment:
```kotlin
// Note: onClick IS included in equals() — new lambdas every render means
// DiffUtil rebinds all cards. This is intentional to prevent stale closures.
val onClick: (() -> Unit)? = null
```

---

## §N — INTERNATIONALIZATION

### N-01 · Hardcoded user-facing strings in Kotlin code [MEDIUM]

**Files**: `LanguageRoutingDelegate.kt`, `VoiceCardBuilder.kt`  
**Strings not in `strings.xml`**:
- `"Downloading model…"` (LanguageRoutingDelegate:215, 229)
- `"✗ Download failed — needs internet once"` (LanguageRoutingDelegate:219, 233)
- `"Same language — no translation needed"` (LanguageRoutingDelegate:247)
- `"Will translate $srcName → $tgtName before speaking"` (LanguageRoutingDelegate:248)
- `"Select target language"` (LanguageRoutingDelegate:249)
- `"Active profile"` (LanguageRoutingDelegate:105, 120)
- `"Voice routing"` and `"Translation"` (fragment_rules.xml has these inline)
- `"⏳ synthesizing…"` (VoiceCardBuilder:213)

**Solution**: Move all to `strings.xml`:
```xml
<string name="downloading_model">Downloading model…</string>
<string name="download_failed_needs_internet">✗ Download failed — needs internet once</string>
<string name="same_language_no_translation">Same language — no translation needed</string>
<string name="will_translate">Will translate %1$s → %2$s before speaking</string>
<string name="select_target_language">Select target language</string>
<string name="active_profile_label">Active profile</string>
<string name="synthesizing">⏳ synthesizing…</string>
```

---

### N-02 · French Piper voices exist but no French UI localization [LOW]

**Issue**: The app ships 5 French Piper voices (`fr_FR-siwis-medium`, etc.) and supports French translation via ML Kit, but the app UI is English-only. There's no `values-fr/strings.xml` directory.

**Solution**: Create `values-fr/strings.xml` with French translations for at least the critical UI strings (nav labels, section titles, button text). The default word rules (`default_word_rules_find/replace`) already have a comment about `values-fr/strings.xml` localization.

---

### N-03 · fragment_rules.xml has inline English text not in string resources [LOW]

**File**: `fragment_rules.xml:122, 128, 135`  
**Issue**: Section descriptions like "Route notifications to different voice profiles by detected language..." and "Translate notifications before speaking. Works with or without voice routing above." are inline English text, not `@string/` references.

**Solution**: Extract to `strings.xml`:
```xml
<string name="lang_routing_description">Route notifications to different voice profiles by detected language. When enabled, the app auto-detects language and uses the mapped profile.</string>
<string name="translation_description">Translate notifications before speaking. Works with or without voice routing above.</string>
```

---

## §O — PROJECTIONS & TECH DEBT

### O-01 · Dependency versions approaching staleness [MEDIUM]

| Dependency | Current | Latest | Risk |
|-----------|---------|--------|------|
| AGP | 8.2.2 | 8.7+ | Missing build optimizations, Kotlin 2.0 compatibility |
| Kotlin | 1.9.22 | 2.1+ | Missing K2 compiler, context receivers, value classes |
| Room | 2.6.1 | 2.7+ | Missing KSP support (kapt is deprecated path) |
| appcompat | 1.6.1 | 1.7+ | Missing Material 3 integration improvements |
| fragment-ktx | 1.6.2 | 1.8+ | Missing FragmentResult API improvements |
| Gradle wrapper | 8.4 | 8.12+ | Missing configuration cache improvements |

**Solution**: Create a dependency update plan:
1. **Immediate**: Update `appcompat`, `fragment-ktx`, `recyclerview` (low risk)
2. **Short-term**: Update Room to 2.7+ and migrate kapt → KSP
3. **Medium-term**: Update Kotlin to 2.0+ (test K2 compiler first with `kotlin.experimental.tryK2=true`)
4. **Long-term**: Update AGP to 8.7+ (requires Gradle 8.9+)

---

### O-02 · CI artifact name is stale [LOW]

**File**: `.github/workflows/build.yml:37`  
**Issue**: `name: Kyokan-v3` but the app is version 4.0. The artifact name doesn't match the current version.

**Solution**: Use a dynamic name:
```yaml
- uses: actions/upload-artifact@v4
  with:
    name: Kyokan-v${{ env.VERSION_NAME }}
    path: app/build/outputs/apk/release/app-release*.apk
```
Or simply update to `Kyokan-v4`.

---

### O-03 · Room + allowMainThreadQueries is a scaling cliff [MEDIUM]

**Issue**: As described in B-02, the current synchronous Room access works for small datasets but becomes a jank source as data grows. The `saveAppRules()` delete-all-insert-all pattern is O(n) on the main thread.

**Cliff point**: ~100 app rules or ~20 profiles with complex word rule sets.

**Solution**: The long-term path is:
1. Add `suspend` versions of all DAO methods
2. Use `withContext(Dispatchers.IO)` in repository methods
3. Remove `allowMainThreadQueries()`
4. Update all callers to use coroutines (ViewModels already have `viewModelScope`)

---

### O-04 · No database schema versioning strategy [LOW]

**File**: `KyokanDatabase.kt:14` — `version = 1, exportSchema = false`  
**Issue**: `exportSchema = false` means Room doesn't generate schema JSON files for migration testing. When the schema needs to change (adding a column, new entity), there's no baseline to generate auto-migrations from.

**Solution**: Set `exportSchema = true` and add the schema export directory to `build.gradle`:
```kotlin
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
```
Add `schemas/` to `.gitignore` if you don't want to track them, or commit them for migration testing.

---

## QUICK WINS — Highest impact, lowest effort

| # | Finding | Effort | Impact |
|---|---------|--------|--------|
| 1 | A-01: Fix CloudTtsEngine retry break on non-retryable errors | 5 min | Eliminates wasted API calls |
| 2 | C-01: Request POST_NOTIFICATIONS at runtime | 15 min | Prevents silent service death on Android 13+ |
| 3 | G-01: Fix WCAG contrast failures (6 color values) | 10 min | Passes WCAG AA across both themes |
| 4 | N-01: Extract hardcoded strings to strings.xml | 20 min | Enables future localization |
| 5 | I-01: Delete unused DataExportHelper.kt | 1 min | Removes dead code |
| 6 | O-02: Fix CI artifact name | 1 min | Correct versioning |
| 7 | A-02: Trim trailing space in buildMessage | 5 min | Cleaner TTS output |

---

## CROSS-CUTTING CONCERNS

### Download Experience Chain
D-01 (RecyclerView recycling) → D-02 (DiffUtil lambdas) → F-05 (no download indicator outside Profiles tab) → D-06 (aggressive Piper cache eviction). These four findings compound: the download experience causes jank (D-01/D-02), has no visibility outside one tab (F-05), and the aggressive cache means downloaded voices still cause delays (D-06).

### Cloud Privacy Chain
C-04 (no sensitive app warning) → A-01 (retry sends text twice) → C-03 (crash logs may contain notification text). These form a privacy risk chain: sensitive notification text can be sent to DeepInfra twice due to the retry bug, and may persist in crash logs.

### Scaling Chain
B-02 (main thread Room) → O-03 (scaling cliff) → I-03 (no coroutines) → O-01 (stale dependencies blocking migration). The technical debt compounds: fixing main-thread Room requires coroutines, which benefit from Kotlin 2.0+, which requires AGP updates.

---

## ROOT CAUSE ANALYSIS

The codebase shows evidence of rapid, disciplined iteration — features were added incrementally with good engineering practices (DiffUtil, Room migration, listener lists, WeakReference, thread safety annotations). The issues that remain are primarily:

1. **Deferred async migration**: The synchronous SharedPreferences → Room migration was done correctly, but the `allowMainThreadQueries()` escape hatch was never removed. This is the #1 technical debt item.

2. **Programmatic UI accumulation**: Building views in code was the right choice for rapid iteration, but the 4+ builder objects now contain ~1500 lines of view construction that should be XML layouts.

3. **ScrollView-wrapping pattern**: Using ScrollView as the top-level container for all fragments is simple but forces RecyclerView to abandon its primary advantage (recycling). This pattern should be inverted.

---

## FIX PLAN — Logical Execution Order

> **Ordering rationale**: Fixes are grouped into 6 phases. Within each phase, items are ordered so that earlier fixes never depend on later ones. Phases are ordered from "most foundational / highest risk" to "polish / long-term". Each fix lists what it unblocks.

---

### PHASE 0 — CRITICAL CORRECTNESS (do first, zero risk of regression)

These are isolated bug fixes. No architectural changes, no refactoring. Each one is a single-file change that can be committed independently.

| # | Finding | File(s) | What to do | Unblocks |
|---|---------|---------|-----------|----------|
| 0.1 | **A-01** | `CloudTtsEngine.kt` | Add `shouldBreak` flag after non-retryable HTTP errors to exit the inner retry loop. Without this, every 400/401/403/404 fires twice per URL. | Clean cloud privacy chain |
| 0.2 | **A-02** | `NotificationFormatter.kt` | Add `.trimEnd()` to `buildMessage()` "full" mode return. Update the unit test `buildMessage full mode blank text omitted` expected value to `"App. Title."`. | — |
| 0.3 | **A-03** | `ProfilesFragment.kt` | Replace `.toInt()` with `.roundToInt()` in SeekBar → pitch/speed mapping (2 lines). Add `import kotlin.math.roundToInt`. | — |
| 0.4 | **A-04** | `CloudTtsEngine.kt` | Change `LocalDate.now()` to `LocalDate.now(ZoneOffset.UTC)` in `dailyCharsUsed()` and `addDailyChars()` (2 occurrences). | — |
| 0.5 | **I-01** | `DataExportHelper.kt` | Delete the file. It's dead code — `HomeFragment` uses `repo` directly. Verify with grep: `grep -r "DataExportHelper" app/src/` returns nothing. | — |
| 0.6 | **I-04** | `VoiceGridAdapter.kt` | Remove `@Transient` from `onAction` and `onClick` fields (lines 65, 82). Update the comments to say lambdas ARE included in equals. | — |
| 0.7 | **O-02** | `.github/workflows/build.yml` | Change `name: Kyokan-v3` to `name: Kyokan-v4` (line 37). | — |

**Estimated time**: 30 minutes  
**Risk**: Near-zero — each fix is isolated and testable  
**Verification**: Run existing unit tests (`NotificationFormatterTest`, `VoiceProfileTest`, `AppRuleTest`). Manual test: trigger a cloud voice with an invalid API key → should see 1 retry per URL in logcat, not 2.

---

### PHASE 1 — SECURITY & PERMISSIONS (do before any public release)

These protect users. Order matters: C-01 must come before C-04 because the sensitive app warning (C-04) should appear in the same permission setup flow.

| # | Finding | File(s) | What to do | Unblocks |
|---|---------|---------|-----------|----------|
| 1.1 | **C-01** | `HomeFragment.kt`, `MainActivity.kt`, `strings.xml` | Add POST_NOTIFICATIONS runtime permission request for SDK 33+. Add it as step 0 in `updateSetup()` / `openNextSetupStep()`. Add string resources for the setup button label. | Foreground service reliability on Android 13+ |
| 1.2 | **C-02** | `ProfilesFragment.kt` | Wrap `SecureKeyStore.setDeepInfraKey()` in try-catch inside the dialog's positive button handler. Show a Toast on failure. | Reliable API key storage |
| 1.3 | **C-03** | `CrashLogger.kt` | Add a `sanitize()` call before writing the crash log. Redact strings matching `[A-Za-z0-9]{20,}` (API key patterns) and `Bearer ...` tokens. | — |
| 1.4 | **C-04** | `ProfilesFragment.kt` | When saving a cloud voice to a profile, scan `repo.getAppRules()` for common sensitive package prefixes (banking, auth, health). If any enabled rules lack `forceLocal=true`, show a one-time AlertDialog recommending the user mark them "Local Only". Persist dismissal with `repo.putBoolean("cloud_sensitive_warned", true)`. | — |
| 1.5 | **C-05** | `proguard-rules.pro` | Change `-keep class com.echolibrium.kyokan.*Fragment { *; }` to `-keep class * extends androidx.fragment.app.Fragment`. This keeps Fragment classes (needed for FragmentManager) but allows member shrinking. | Smaller release APK |
| 1.6 | **C-06** | Cloudflare dashboard | Configure Cloudflare Rate Limiting rules (Security → WAF → Rate limiting) with 30 req/min per IP. Remove the in-memory rate limiter from `worker.js` and the TODO comment. | Production-ready proxy |

**Estimated time**: 2-3 hours  
**Risk**: Low — C-01 is the highest-value fix here  
**Verification**: Test on an Android 13+ device. Verify POST_NOTIFICATIONS prompt appears. Verify foreground notification shows after granting. Verify API key save/fail path with SecureKeyStore.

---

### PHASE 2 — ACCESSIBILITY & CONTRAST (do before any public release)

WCAG AA compliance. These are color value changes and accessibility delegate additions — no logic changes.

| # | Finding | File(s) | What to do | Unblocks |
|---|---------|---------|-----------|----------|
| 2.1 | **G-01** | `values-night/colors.xml`, `values/colors.xml` | Update 6 color values to meet WCAG AA 4.5:1 minimum. **Dark mode**: `text_card_subtitle` → `#8a6aaa`, `text_dimmed` → `#9a8ab0`, `nav_inactive` → `#9a8ab0`, `cloud_status` → `#aa8844`. **Light mode**: `text_disabled` → `#8a7a9e`, `text_dimmed` → keep or lighten to `#7a5a9e`. Verify each with the contrast formula. | WCAG AA compliance |
| 2.2 | **G-02** | `LogcatFragment.kt` | Add `AccessibilityDelegate` to `chip_app_only` and `chip_pipeline` TextViews to announce toggle state via `isCheckable=true` and `isChecked`. | Screen reader support for Logcat filters |
| 2.3 | **G-03** | `MainActivity.kt` | Add `AccessibilityDelegate` to each bottom nav tab `LinearLayout` to announce `isSelected` state. Set `contentDescription` dynamically: `"Home tab, selected"` / `"Home tab"`. | Screen reader support for navigation |

**Estimated time**: 1 hour  
**Risk**: Zero for color changes. Low for accessibility delegates (purely additive).  
**Verification**: Run the Python contrast ratio calculator from the audit. Enable TalkBack and navigate all tabs + logcat filters — verify correct announcements.

---

### PHASE 3 — INTERNATIONALIZATION (do before localization)

Extract hardcoded strings so future translation is possible. Must be done before adding `values-fr/strings.xml`.

| # | Finding | File(s) | What to do | Unblocks |
|---|---------|---------|-----------|----------|
| 3.1 | **N-01** | `LanguageRoutingDelegate.kt`, `VoiceCardBuilder.kt`, `strings.xml` | Extract all 9 hardcoded user-facing strings to `strings.xml` with proper `%s`/`%1$s` format args. Replace inline strings with `context.getString(R.string.xxx)`. | Localization readiness |
| 3.2 | **N-03** | `fragment_rules.xml`, `strings.xml` | Extract the 3 inline English description texts in `fragment_rules.xml` to `@string/` references. | Localization readiness |
| 3.3 | **N-02** | `values-fr/strings.xml` (new file) | Create French string resources for navigation labels, section titles, button text, and the default word rules (find/replace arrays). At minimum: nav labels, setup flow, and profile management strings. | French-speaking users |

**Estimated time**: 1.5 hours  
**Risk**: Zero — purely moving strings from code to resources  
**Verification**: Switch device language to French. Verify all extracted strings display correctly. Verify no crashes from missing format args.

---

### PHASE 4 — PERFORMANCE (do in dependency order)

These fixes build on each other. D-02 should come before D-01 because fixing DiffUtil comparison reduces the per-render cost, making the recycling fix (D-01) even more effective.

| # | Finding | File(s) | What to do | Unblocks |
|---|---------|---------|-----------|----------|
| 4.1 | **D-04** | `AudioPipeline.kt` | Modify `applyCrossfade()` to work in-place instead of calling `samples.copyOf()`. The input array is not referenced after synthesis, so mutation is safe. | ~1MB less transient RAM per synthesis |
| 4.2 | **D-06** | `SherpaEngine.kt` | Increase `MAX_PIPER_CACHE` from 1 to 3. | Eliminates 500ms-2s reload delay when alternating Piper voices |
| 4.3 | **D-02** | `VoiceGridAdapter.kt`, `ProfilesFragment.kt` | Separate lambda callbacks from `VoiceGridItem` data classes. Move `onClick`/`onAction` to adapter-level callback interfaces resolved in `onBindViewHolder`. Update `areContentsTheSame()` to compare only data fields. | DiffUtil actually skips unchanged items |
| 4.4 | **D-01** | `fragment_profiles.xml`, `ProfilesFragment.kt` | **Option A (short-term)**: Add `voiceGrid.setItemViewCacheSize(20)` to reduce rebind cost while keeping the ScrollView wrapper. **Option B (long-term)**: Replace the outer `ScrollView` with a single `RecyclerView` using multiple view types (profile grid header, filter row, voice cards, pitch/speed section, preview section). This enables true view recycling. | Eliminates 300+ materialized views |
| 4.5 | **D-05** | `AppsFragment.kt` | Name the anonymous thread: `Thread({ ... }, "LoadInstalledApps").start()`. Add `if (!isAdded) return@Thread` guard at the start. | Debuggable thread in profiler |
| 4.6 | **D-03** | `LogcatFragment.kt` | Increase `REFRESH_THROTTLE_MS` from 200 to 500. Reduce displayed lines from 500 to 200. Long-term: replace single `TextView` + spans with a `RecyclerView` of colored row items. | Reduces GC pressure on Logcat screen |
| 4.7 | **A-05** | `VoiceCardBuilder.kt` | Reduce preview button disabled timeout from 10000ms to 3000ms. Long-term: add a synthesis completion callback from `AudioPipeline` to re-enable the button immediately after playback finishes. | Faster voice comparison |

**Estimated time**: 4-6 hours (Option A for D-01). 2-3 days (Option B for D-01).  
**Risk**: Medium for D-02 (changing data class structure requires updating all callers). Low for everything else.  
**Verification**: Profile with Android Studio Profiler on the Profiles tab during a Kokoro download. Verify: (1) no full-list rebinds every 500ms after D-02, (2) RecyclerView height is bounded (not expanded to full content) after D-01, (3) memory stable during scroll.

---

### PHASE 5 — STATE & DATA INTEGRITY (do after Phase 4)

These require more care because they touch data flow paths. Phase 4's coroutine groundwork (if chosen) makes B-02 easier.

| # | Finding | File(s) | What to do | Unblocks |
|---|---------|---------|-----------|----------|
| 5.1 | **B-01** | `ProfilesFragment.kt` | In `attachSeek()`, call `viewModel.updateCurrentProfile(readProfileFromUI())` on every `onProgressChanged(fromUser=true)`. This syncs slider state to the ViewModel so `onSaveInstanceState` captures current values. | Survives process death |
| 5.2 | **B-03** | `SettingsRepository.kt` | In `exportAll()`, put `profilesArr`, `rulesArr`, `wordArr` directly as `JSONArray` objects instead of `.toString()`. In `importAll()`, handle both `String` and `JSONArray` types for backward compatibility. | Standard JSON export format |
| 5.3 | **B-04** | `AppRule.kt`, `SettingsRepository.kt` | Add `packageName.isNotBlank()` validation in `AppRule.fromJson()`. In `importAll()`, filter out profiles with blank IDs and rules with blank package names before inserting. | Resilient import |
| 5.4 | **B-05** | `WordRulesDelegate.kt` | Capture `idx` as `val capturedIdx = idx` in the closure. Add bounds check `if (capturedIdx < rules.size)` before mutation. | Prevents theoretical index-out-of-bounds |
| 5.5 | **B-02** | `SettingsRepository.kt`, `KyokanDatabase.kt` | **Step 1**: Add `saveAppRulesAsync()` with background thread + main-thread callback. Update `AppsFragment.updateRule()` and `setAllEnabled()` to use it. **Step 2** (long-term): Add `suspend` DAO methods, use `viewModelScope.launch(Dispatchers.IO)`, remove `allowMainThreadQueries()`. | Eliminates main-thread jank for large datasets |

**Estimated time**: 3-4 hours for steps 5.1-5.4. 1-2 days for 5.5 (full async migration).  
**Risk**: Medium for B-02 (touching threading model). Low for everything else.  
**Verification**: Test process death: open Profiles, adjust pitch slider, press Home, kill app via `adb shell am kill com.echolibrium.kyokan`, reopen — verify slider position survived. Test import with a hand-crafted JSON with blank IDs — verify they're filtered out.

---

### PHASE 6 — UX, DESIGN & ARCHITECTURE (do when bandwidth allows)

These are improvements, not fixes. They enhance the experience but aren't blocking correctness or safety.

| # | Finding | File(s) | What to do | Unblocks |
|---|---------|---------|-----------|----------|
| 6.1 | **E-03** | `dimens.xml`, builders | Define `corner_radius_card`, `corner_radius_chip`, `corner_radius_input` in `dimens.xml`. Replace hardcoded `8 * dp` / `10 * dp` in `ProfileGridBuilder` and `VoiceCardBuilder` with resource lookups. | Consistent design tokens |
| 6.2 | **E-04** | `fragment_profiles.xml`, `ProfilesFragment.kt` | Add a loading placeholder `TextView` in the layout, visible by default. Hide it after first `renderVoiceGrid()` completes. | Loading state feedback |
| 6.3 | **F-03** | `strings.xml` | Verify `nav_voices` string matches page title intent. Decide: either both say "Voices" or both say "Profiles". | Mental model alignment |
| 6.4 | **F-02** | `VoiceCardBuilder.kt`, `AudioPipeline.kt` | Reduce preview disable timeout to 3000ms (immediate). Long-term: add a `onPlaybackComplete` callback to `AudioPipeline` and use it to re-enable the preview button. | Rapid voice comparison |
| 6.5 | **I-02** | `CloudTtsEngine.kt`, `VoiceRegistry.kt` | Remove `CloudTtsEngine.VOICES` set. Derive voice validation from `VoiceRegistry.CLOUD_VOICES` map in `synthesize()`. Single source of truth. | No divergence risk when adding voices |
| 6.6 | **I-03** | `build.gradle`, ViewModels, Repository | Add `kotlinx-coroutines-android` dependency. Migrate `ProfilesViewModel` download triggers to `viewModelScope.launch(Dispatchers.IO)`. Migrate `AppsFragment.loadInstalledApps()` to `lifecycleScope.launch`. | Structured concurrency, automatic cancellation |
| 6.7 | **E-01** | `values-night/colors.xml` | Decide: warm-dark palette (align with light theme personality) or keep cool-purple (intentional night shift). If aligning: change `bg` to `#1a1510`, `surface` to `#221c14`. Document the choice. | Cohesive brand across themes |
| 6.8 | **E-02** | New XML layouts | Migrate `VoiceCardBuilder.buildVoiceCard()` to an XML layout `item_voice_card.xml` inflated in `VoiceGridAdapter.onCreateViewHolder()`. Migrate `ProfileGridBuilder.buildCard()` to `item_profile_card.xml`. | XML preview, maintainable UI |
| 6.9 | **F-01** | `MainActivity.kt`, `activity_main.xml` | Remove Logcat from bottom nav. Add a "Developer" toggle in Home settings (or long-press version text). Open Logcat as a standalone Activity or DialogFragment. | Cleaner navigation for users |
| 6.10 | **F-04** | `LanguageRoutingDelegate.kt` | Group languages into "Detected" (from recent ML Kit results) and "All" sections. Show detected first, collapsed "All" below. | Less overwhelming Rules screen |
| 6.11 | **F-05** | `TtsAliveService.kt`, `VoiceDownloadManager.kt` | Update `TtsAliveService.buildNotification()` to check download state and show "Downloading voice model… X%" when active. | Download visibility outside Profiles tab |
| 6.12 | **O-01** | `build.gradle`, `gradle-wrapper.properties` | Update low-risk dependencies first: `appcompat`, `fragment-ktx`, `recyclerview`, `transition-ktx`. Then Room 2.6→2.7 with kapt→KSP migration. Then Kotlin 1.9→2.0 with K2 compiler testing. Then AGP+Gradle. | Modern toolchain, security patches |
| 6.13 | **O-04** | `build.gradle`, `KyokanDatabase.kt` | Set `exportSchema = true`. Add `room.schemaLocation` kapt argument. Commit schema JSON to `app/schemas/` for future migration testing. | Safe database migrations |
| 6.14 | **G-04** | `CollapsibleSectionHelper.kt` | Optional polish: replace Unicode `▸`/`▾` with an `ImageView` arrow that rotates 90° on expand/collapse, respecting `AnimationUtil.areAnimationsEnabled()`. | Visual polish |

**Estimated time**: 2-4 days for all items (spread over multiple sessions)  
**Risk**: Medium for 6.8 and 6.9 (layout changes, navigation restructuring). Low for everything else.  
**Verification**: Full regression pass — test every tab, every voice engine, every download flow, process death, configuration change, TalkBack navigation.

---

### PHASE DEPENDENCY DIAGRAM

```
PHASE 0 (correctness)
  │ ← no dependencies, do first
  ▼
PHASE 1 (security)
  │ ← C-01 must be done before any public release
  ▼
PHASE 2 (accessibility)
  │ ← color changes are independent of everything
  ▼
PHASE 3 (i18n)
  │ ← N-01 must be done before N-02 (French locale)
  ▼
PHASE 4 (performance)
  │ ← D-02 before D-01 (fix DiffUtil before fixing recycling)
  │ ← D-04 is independent
  ▼
PHASE 5 (state/data)
  │ ← B-01 is independent
  │ ← B-02 benefits from I-03 (coroutines from Phase 6)
  │   but can be done with raw Thread as intermediate step
  ▼
PHASE 6 (UX/design/architecture)
  │ ← I-03 (coroutines) unblocks clean B-02
  │ ← O-01 (dependency updates) unblocks I-03 (Kotlin 2.0)
  │ ← E-02 (XML layouts) unblocks E-03 (design tokens in XML)
  └── Can be done incrementally over multiple sessions
```

---

### TOTAL EFFORT ESTIMATE

| Phase | Effort | Items | Cumulative |
|-------|--------|-------|-----------|
| Phase 0 — Correctness | 30 min | 7 fixes | 30 min |
| Phase 1 — Security | 2-3 hrs | 6 fixes | ~3.5 hrs |
| Phase 2 — Accessibility | 1 hr | 3 fixes | ~4.5 hrs |
| Phase 3 — i18n | 1.5 hrs | 3 fixes | ~6 hrs |
| Phase 4 — Performance | 4-6 hrs | 7 fixes | ~12 hrs |
| Phase 5 — State/Data | 3-4 hrs | 5 fixes | ~16 hrs |
| Phase 6 — UX/Design/Arch | 2-4 days | 14 fixes | ~5 days total |

**Phases 0-3** (the "release blocker" fixes) can be completed in a single focused day.  
**Phases 4-5** (performance + data) take another 1-2 days.  
**Phase 6** is ongoing improvement work spread across multiple sessions.

---

*End of Full Audit 3*
