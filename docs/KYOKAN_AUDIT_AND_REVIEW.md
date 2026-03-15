# KYŌKAN v4.0 — AUDIT + IMPLEMENTATION REVIEW

**Original Audit Date:** March 15, 2026
**Implementation Review Date:** March 15, 2026
**Auditor:** Claude Opus 4.6 (app-audit + design-aesthetic-audit + scope-context skills)

---

## DOCUMENT STRUCTURE

This document contains three parts:

1. **PART A** — Original audit findings (82 findings across all 13 parts)
2. **PART B** — Review of the other instance's implementation attempt
3. **PART C** — Consolidated task list: what still needs to be done

---

# ═══════════════════════════════════════════════════════
# PART A — ORIGINAL AUDIT
# ═══════════════════════════════════════════════════════

## EXECUTIVE SUMMARY

| Severity | Count |
|----------|-------|
| CRITICAL | 4 |
| HIGH | 11 |
| MEDIUM | 30 |
| LOW | 26 |
| INFO | 12 |
| **TOTAL** | **83** |

> Note: 83 not 82 — one HIGH finding (word rules dead code) was discovered
> during the implementation review that should have been caught in the original audit.

**Root Causes (two account for ~70% of findings):**

1. **No architecture layer between UI and data.** Fragments directly manage state, persistence, downloads, playback, and rendering.
2. **Total identity fracture between brand and UI.** The logos (watercolor ensō on washi), the name (共感 = empathy), and the product vision communicate a warm, organic, living voice companion. The UI communicates a terminal debug panel.

---

## §0 — APP CONTEXT BLOCK

```yaml
App Name:      Kyōkan (共感)
Package:       com.echolibrium.kyokan
Version:       4.0 (versionCode 4)
Platform:      Native Android (Kotlin), minSdk 26, targetSdk 35, arm64-v8a only
Architecture:  Single-Activity + Fragment-based, no ViewModel/LiveData,
               manual SharedPreferences persistence, singleton service objects
Domain:        Accessibility / Companion — notification reader with AI-driven
               human-like adaptive voices, emphasis on user social dynamic
Tech Stack:    Kotlin, XML layouts (no Compose), sherpa-onnx (native JNI),
               OkHttp, ML Kit (translate + language-id),
               EncryptedSharedPreferences, Cloudflare Worker proxy
Build:         Gradle 8.2.2, Kotlin 1.9.22, Java 17, AGP, ProGuard release
Secondary:     Python async TTS router (kyokan-tts/) — standalone dev/test tool
State:         SharedPreferences (JSON via org.json), no Room/SQLite, no ViewModel
Sensitive:     DeepInfra API key (EncryptedSharedPreferences),
               notification content (transient in memory only)
```

## §I.4 — Five-Axis Aesthetic Profile

Based on brand identity (watercolor ensō logos) + product vision (empathic voice companion):

| Axis | Current | Target | Gap |
|------|---------|--------|-----|
| Warmth | 1/5 | 4/5 | **CRITICAL** |
| Playfulness | 1/5 | 3/5 | **HIGH** |
| Density | 4/5 | 2/5 | **HIGH** |
| Motion | 0/5 | 3/5 | **CRITICAL** |
| Refinement | 2/5 | 4/5 | **HIGH** |

## Brand Identity (from logos)

Both logos are watercolor on washi (Japanese handmade paper) — spirals evoking sound waves, ripples of resonance, a voice expanding outward. Ensō-like incomplete circles. Deep indigo-purple through lavender to rose-pink. The medium says: organic, imperfect, alive, breathing.

**Brand palette (extracted from logos):**

| Role | Color | Description |
|------|-------|-------------|
| Deep center | ~#2d1b4e | Dark indigo (spiral core) |
| Mid-tone | ~#5c3d7a | Rich purple |
| Wash | ~#9b7eb8 | Soft lavender |
| Warm accent | ~#c48da0 | Rose-pink |
| Ground | ~#f5f0eb | Washi cream (already `ic_launcher_background`) |

---

## ALL FINDINGS (indexed)

### CRITICAL (4)

| ID | Finding | Section |
|----|---------|---------|
| C1 | Color palette contradicts brand identity entirely — zero logo colors in UI | §DC1 |
| C2 | Zero motion/animation in an app about living voice | §DM1 |
| C3 | Design tokens encode the wrong identity (terminal, not companion) | §DTA1 |
| C4 | Atmosphere (cold void) contradicts brand (warm washi watercolor) | §DSA1 |

### HIGH (11)

| ID | Finding | Section |
|----|---------|---------|
| H1 | Race condition in spinner callbacks (fires on setSelection) | §A6 — AppsFragment.buildRow |
| H2 | ProfilesFragment mutable state from multiple callbacks | §A6 — ProfilesFragment |
| H3 | No single source of truth — SharedPreferences fragmentation | §B1 — All fragments |
| H4 | Fragment state lost on config change (no savedInstanceState/ViewModel) | §B1 — All fragments |
| H5 | renderVoiceGrid rebuilds 52+ cards every second during downloads | §D1 — ProfilesFragment |
| H6 | Notification content sent to cloud proxy in plaintext (privacy) | §C5 — CloudTtsEngine → proxy |
| H7 | Monospace typography destroys human voice identity | §DT1 — Entire app |
| H8 | Voice cards are data cells, not character portraits | §DCO1 — ProfilesFragment |
| H9 | Multiple text colors fail WCAG AA contrast | §G4 — colors.xml, styles.xml |
| H10 | God Fragment: ProfilesFragment at 848 LOC | §J4 — ProfilesFragment |
| H11 | Word replacement rules are DEAD CODE — never consumed by notification pipeline | §A1 — NotificationReaderService + RulesFragment |

> H11 was discovered during implementation review. `wording_rules` is written by
> RulesFragment but `NotificationReaderService.buildMessage()` never reads or applies
> substitutions. The entire Word Rules feature is a no-op in BOTH versions.

### MEDIUM (30)

| ID | Finding | Section |
|----|---------|---------|
| M1 | Pitch/Speed SeekBar range undocumented, no model validation | §A1 |
| M2 | NotificationReaderService mutable maps on multiple threads (fragile sync) | §A6 |
| M3 | synthesisErrorListeners called from pipeline thread | §A6 |
| M4 | No privacy policy or data handling disclosure | §C6 |
| M5 | JSON serialization has no schema versioning | §B2 |
| M6 | SharedPreferences .apply() for profile data (crash = data loss) | §B2 |
| M7 | Word replacement rules have no length limits | §B3 |
| M8 | AppRule/VoiceProfile.loadAll called on every notification (no cache) | §B5 |
| M9 | MutableList shared between background thread and UI (fragile) | §B6 |
| M10 | LogcatFragment.refreshDisplay rebuilds full SpannableStringBuilder | §D1 |
| M11 | Piper voice cache holds up to 160MB native memory (2 models) | §D4 |
| M12 | No RecyclerView — all lists are LinearLayout + addView | §D5 |
| M13 | Profile cards functional but emotionless (same look regardless of personality) | §DCO1 |
| M14 | Programmatic views have no shared style system | §DCO1 |
| M15 | Information density works against companion identity | §DH1 |
| M16 | Surface atmosphere contradicts brand (flat void vs warm washi) | §DSA1 |
| M17 | States don't carry companion personality (empty/loading/error) | §DST1 |
| M18 | Loading states are text-only ("LOADING...", "42%") | §DST1 |
| M19 | No tablet/landscape consideration | §DRC1 |
| M20 | Rules fragment overloaded (4 feature areas in one tab) | §F1 |
| M21 | Download "⬇ ALL" has no confirmation or size warning | §F2 |
| M22 | First-run is permission-focused, not feature-focused | §F3 |
| M23 | Programmatic views have no contentDescription (accessibility) | §G1 |
| M24 | Filter buttons below 48dp touch target | §G2 |
| M25 | No edge-to-edge / system bar handling | §H3 |
| M26 | SeekBar listener pattern duplicated 3 times | §J2 |
| M27 | renderVoiceGrid 300+ lines with repeated per-engine patterns | §J2 |
| M28 | All business logic in Fragments (untestable) | §J4 |
| M29 | 11 singletons with mutable state (untestable) | §J4 |
| M30 | Hardcoded English strings in programmatic UI (not in strings.xml) | §K1 |

### LOW (26)

| ID | Finding | Section |
|----|---------|---------|
| L1 | Cooldown SeekBar allows 0 — visual/logical mismatch | §A1 |
| L2 | DND start==end permanently mutes with no indication | §A1 |
| L3 | optDouble.toFloat precision loss (negligible) | §A7 |
| L4 | Cloudflare Worker has no rate limiting | §C1 |
| L5 | No SharedPreferences size monitoring | §B2 |
| L6 | Profile name has no character limit | §B3 |
| L7 | AppsFragment.renderRules full rebuild on every change | §D1 |
| L8 | LogcatFragment.logBuffer 5000 entries in memory | §D4 |
| L9 | AudioPipeline.prevTail float array (4KB, negligible) | §D4 |
| L10 | AudioTrack.MODE_STATIC loads full utterance | §D5 |
| L11 | No type scale system | §DT1 |
| L12 | Button styles inconsistent | §DCO1 |
| L13 | Iconography: 5 material icons, no personality | §DI1 |
| L14 | No dark mode / light mode support | §DC1 |
| L15 | No onboarding flow | §F1 |
| L16 | No download cancel capability | §F2 |
| L17 | README references outdated v3 architecture | §F4 |
| L18 | clear-kyokan-data.sh references old package name (com.kokoro.reader) | §F4 |
| L19 | Voice preview has no loading state | §F5 |
| L20 | No explicit focus order attributes | §G3 |
| L21 | Dead backward-compat setters in download managers | §J1 |
| L22 | .kt files in docs/ could confuse tooling | §J1 |
| L23 | Spinner setup pattern repeated across fragments | §J2 |
| L24 | Inconsistent Kyōkan vs Echolibrium naming | §J3 |
| L25 | testSpeak method name misleading | §J3 |
| L26 | Language routing hardcoded to English/French only | §K1 |

### INFO (12)

| ID | Finding |
|----|---------|
| I1 | Voice catalogs are hardcoded (appropriate for now) |
| I2 | DND uses HOUR_OF_DAY correctly |
| I3 | No WebView/XSS surface |
| I4 | ML Kit is on-device (no privacy concern) |
| I5 | Notification content in memory only |
| I6 | ProGuard rules comprehensive |
| I7 | No import/export feature |
| I8 | Copy coherent within terminal aesthetic |
| I9 | Emoji adds minimal personality |
| I10 | API level compatibility correct |
| I11 | No LLM integration / no prompt injection surface |
| I12 | All Maven deps on recent stable versions |

---

# ═══════════════════════════════════════════════════════
# PART B — IMPLEMENTATION REVIEW
# ═══════════════════════════════════════════════════════

## What the other Claude instance did

### Files added (11 new)
- `AppContainer.kt` — manual DI container
- `KyokanApp.kt` — Application subclass
- `ProfilesViewModel.kt` — ViewModel for profiles
- `HomeViewModel.kt` — ViewModel for home
- `VoiceCardBuilder.kt` — extracted voice card UI builders
- `WordRulesDelegate.kt` — extracted from RulesFragment
- `NotificationRulesDelegate.kt` — extracted from RulesFragment
- `LanguageRoutingDelegate.kt` — extracted from RulesFragment
- `SeekBarUtil.kt` — shared SeekBar listener
- `SpinnerUtil.kt` — shared Spinner setup
- `AppRuleAdapter.kt` — adapter for app rules
- `item_app_rule.xml` — layout for app rule row
- `values-night/colors.xml` — dark mode colors
- `dimens.xml` — type scale + spacing tokens
- `docs/KYOKAN_FULL_AUDIT.md` — the original audit report

### Files modified (34)
Every Kotlin source file, every XML layout, styles, colors, strings, proxy, README, manifest, build.gradle, clear script.

### Files renamed (3)
- `docs/EchoPersonality.kt` → `.md`
- `docs/OrpheusPreprocessor.kt` → `.md`
- `docs/integration-guide.kt` → `.md`

---

## SCORECARD: What was addressed from the audit

### WELL DONE (genuine improvements)

| Finding | What was done | Score |
|---------|--------------|-------|
| M29 — Singletons | 8 singletons → classes + AppContainer DI | 8/10 |
| M28 — Logic in fragments | ProfilesViewModel + HomeViewModel created | 6/10 |
| M20 — Rules overloaded | RulesFragment decomposed into 3 delegates (297→78 LOC) | 9/10 |
| M26 — SeekBar duplication | SeekBarUtil extracted | 8/10 |
| L4 — Proxy no rate limit | Rate limiting (30/min/IP), input validation, max length | 8/10 |
| M8 — JSON parsed every notif | cachedRules/cachedProfiles with invalidation listener | 8/10 |
| L26 — Lang routing EN/FR only | LanguageRoutingDelegate now dynamic for ALL 13 languages | 9/10 |
| M21 — No download confirmation | confirmDownloadAllPiper() with size estimate dialog | 7/10 |
| L18 — Wrong package name | clear-kyokan-data.sh updated to com.echolibrium.kyokan | 10/10 |
| L22 — .kt docs files | Renamed to .md | 10/10 |
| H7 — Monospace typography | Changed to sans-serif in XML layouts, logcat kept monospace | 7/10 |
| L11 — No type scale | dimens.xml with 8sp–20sp scale + 48dp touch target | 8/10 |
| C2 — Zero motion (partial) | Breathing alpha pulse on listening, AutoTransition on collapsibles, fade on fragment swap | 2/10 |
| M23 — No contentDescription | Added to voice cards, section headers, toggles | 6/10 |
| L19 — Preview no loading | "⏳ synthesizing…" feedback with 2s timeout | 7/10 |
| H4 — State lost on config (partial) | genderFilter/languageFilter in savedInstanceState | 3/10 |

### POORLY DONE (surface-level or counterproductive)

| Finding | What was done | Problem | Score |
|---------|--------------|---------|-------|
| C1 — Wrong palette | Swapped green→purple hex values | Colors don't match brand logos; token NAMES still say "green" | 2/10 |
| C3 — Wrong tokens | Changed token values to purple | `@color/green` = `#7844a0`. Semantic names are lies. | 1/10 |
| C4 — Cold atmosphere | `#0d0d0d` → `#110d18` | Still a dark void with purple tint. No warmth/texture. | 2/10 |
| H8 — Data cell cards | Added circular avatar, nationality subtitle | Still data cells. No personality, no "meeting someone" feeling. | 3/10 |
| H10 — God Fragment | Extracted VoiceCardBuilder | ProfilesFragment: 848→807 lines. Still a God Fragment. | 3/10 |
| H5 — Rebuild every second | Added 500ms throttle | Still removes all views and rebuilds. No RecyclerView/diffing. | 2/10 |
| L14 — No dark/light mode | Added DayNight theme + toggle + values-night | Light theme is DOA — Kotlin hardcoded dark colors break it | 1/10 |

### NOT ADDRESSED AT ALL

| Finding | Status |
|---------|--------|
| H1 — Spinner race condition | NOT FIXED |
| H3 — No single source of truth (partial — profiles only) | MOSTLY NOT FIXED |
| H6 — Notification privacy disclosure | NOT FIXED |
| H9 — WCAG contrast (partial — some fixed, hint/disabled still fail) | PARTIALLY FIXED |
| H11 — Word rules dead code | NOT FIXED (also missed) |
| M2 — Thread safety fragile sync | DOCUMENTED but not structurally fixed |
| M4 — No privacy policy | NOT FIXED |
| M5 — No JSON schema versioning | NOT FIXED |
| M12 — No RecyclerView | NOT FIXED |
| M13 — Emotionless profile cards | NOT FIXED |
| M15 — Information density | NOT FIXED |
| M17/M18 — State design / loading states | NOT FIXED |
| M25 — No edge-to-edge | NOT FIXED |
| Section header copy (`// MASTER SWITCH`) | NOT CHANGED |

---

## REGRESSIONS INTRODUCED BY THE OTHER INSTANCE

### REG-1: [CRITICAL] — Light theme is broken for all programmatic views

DayNight theme added. Toggle works. XML layouts respond to theme.
BUT: ProfilesFragment has 20 `companion const val` dark-only hex colors.
VoiceCardBuilder has 15+ hardcoded dark hex values.
WordRulesDelegate has 6+ hardcoded dark hex values.

**Result:** Flipping to light mode gives cream backgrounds (from XML `@color/bg`)
with dark purple cards (from Kotlin hardcoded `0xFF181222`). Unreadable.

### REG-2: [HIGH] — Token naming is semantically wrong

`@color/green` = `#7844a0` (purple)
`@color/green_dark` = `#5e3080` (darker purple)
`@color/btn_green_bg` = `#e8ddf5` (light purple)

Every future developer, every AI assistant, every code search for "green" will be
misled. This is actively worse than the original because before, green was green.

### REG-3: [HIGH] — Parallel color system created

Colors now live in THREE places:
1. `values/colors.xml` — light theme tokens (purple, named "green")
2. `values-night/colors.xml` — dark theme tokens
3. `ProfilesFragment.companion` — 20 hardcoded dark-only constants

These will inevitably drift. Updating one won't update the others.

### REG-4: [MEDIUM] — AppContainer creates all instances eagerly

`SherpaEngine`, `AudioPipeline`, `NotificationTranslator` created at app start
even if user only opens settings. Native memory and threading overhead.
Should be `lazy`.

### REG-5: [LOW] — "Translate to:" hardcoded string in LanguageRoutingDelegate:167

New code introduced a hardcoded English string that wasn't in the original.

---

# ═══════════════════════════════════════════════════════
# PART C — CONSOLIDATED TASK LIST
# ═══════════════════════════════════════════════════════

What actually needs to happen, in priority order, starting from the reworked codebase
(which has good architectural changes that should be kept).

## PHASE 0: Fix regressions (before anything else)

### 0.1 — Rename color tokens (REG-2)
Find-and-replace across entire codebase:
- `green` → `primary` (in XML refs AND colors.xml)
- `green_dark` → `primary_dark`
- `btn_green_bg` → `btn_primary_bg`
This is purely mechanical. Touch every `@color/green` reference in XML and Kotlin.

### 0.2 — Create ThemeColors helper and kill all hardcoded hex (REG-1, REG-3)
Create a `ThemeColors` object or extension that resolves `@color/` resources at runtime:
```kotlin
object ThemeColors {
    fun primary(ctx: Context) = ContextCompat.getColor(ctx, R.color.primary)
    fun surface(ctx: Context) = ContextCompat.getColor(ctx, R.color.surface)
    fun cardActiveBg(ctx: Context) = ContextCompat.getColor(ctx, R.color.card_active_bg)
    // ... all 20+ colors used in programmatic views
}
```
Then replace every `0xFF181222.toInt()` in VoiceCardBuilder, ProfilesFragment companion,
WordRulesDelegate, LanguageRoutingDelegate with `ThemeColors.xxx(ctx)`.
Delete all `companion const val COLOR_*` from ProfilesFragment.

### 0.3 — Make AppContainer lazy (REG-4)
```kotlin
val sherpaEngine: SherpaEngine by lazy { SherpaEngine(voiceDownloadManager, piperDownloadManager) }
val audioPipeline: AudioPipeline by lazy { AudioPipeline(cloudTtsEngine, sherpaEngine) }
```

### 0.4 — Move hardcoded string to strings.xml (REG-5)
`"Translate to:"` in LanguageRoutingDelegate:167 → `@string/translate_to_label`

## PHASE 1: Fix critical bugs

### 1.1 — Wire up word replacement rules (H11)
In `NotificationReaderService.buildMessage()` or in `processItem()` after `buildMessage`:
```kotlin
val rules = prefs.getString("wording_rules", null)
if (rules != null) {
    // parse JSON, apply find→replace on rawText
}
```
Cache the parsed rules alongside cachedRules/cachedProfiles.

### 1.2 — Fix spinner race condition (H1)
In AppsFragment.buildRow and ProfilesFragment, set `onItemSelectedListener` BEFORE `setSelection`,
or use a flag to ignore the initial programmatic selection:
```kotlin
var userInitiated = false
spinner.setSelection(idx)
spinner.post { userInitiated = true }
spinner.onItemSelectedListener = object { ... if (!userInitiated) return ... }
```

## PHASE 2: Aesthetic — do it right this time

### 2.1 — Apply the ACTUAL brand palette
In colors.xml (light theme — washi cream base):
```xml
<color name="primary">#5c3d7a</color>        <!-- logo mid-tone purple -->
<color name="primary_dark">#2d1b4e</color>    <!-- logo deep indigo -->
<color name="accent_rose">#c48da0</color>     <!-- logo rose-pink -->
<color name="bg">#f5f0eb</color>              <!-- washi cream -->
<color name="surface">#ede5dc</color>         <!-- warm paper -->
```
In colors.xml (night theme — deep indigo base):
```xml
<color name="primary">#9b7eb8</color>         <!-- logo lavender -->
<color name="primary_dark">#5c3d7a</color>    <!-- logo purple -->
<color name="bg">#1a1028</color>              <!-- deep indigo, NOT pure black -->
<color name="surface">#221838</color>         <!-- warm dark purple -->
```
With ThemeColors in place (Phase 0.2), this change propagates everywhere.

### 2.2 — Replace `// COMMENT` section headers
Change copy from dev-culture to companion-culture:
- `// MASTER SWITCH` → `Control` or just `Notifications`
- `// DEFAULT READ MODE` → `How to read`
- `// DO NOT DISTURB` → `Quiet hours`
- `// LISTENING` → `Voice commands`
- `▸ // WORD RULES` → `▸ Word replacements`
- `▸ // NOTIFICATION RULES` → `▸ Notification behavior`
- `▸ // LANGUAGE PROFILES` → `▸ Language & translation`

### 2.3 — Add motion where it matters most
Priority order:
1. Collapsible sections: already done (AutoTransition) ✓
2. Fragment transitions: already done (fade) ✓
3. Listening indicator: already done (breathing pulse) ✓
4. Voice card selection: brief scale + highlight animation
5. Download progress: determinate progress bar (not just "42%" text)
6. Tab indicator: smooth color transition on nav bar
7. Profile card selection: brief glow/pulse

### 2.4 — Voice card personality
Even without images, cards can carry character:
- Show the voice's language flag emoji prominently
- Use the engine accent color as a subtle gradient tint
- Show pitch/speed if the profile is active ("soft & slow" vs "bright & fast")
- Different corner radius or shape for different engine types

## PHASE 3: Remaining audit fixes

### 3.1 — Privacy
- Add a first-run dialog disclosing: "Cloud voices send notification text to DeepInfra for synthesis"
- Add a privacy section in settings or a link to a privacy policy

### 3.2 — Accessibility
- Fix remaining contrast failures: `text_hint` and `text_disabled` need higher contrast
- Ensure all filter buttons meet 48dp minimum touch target
- Add edge-to-edge inset handling

### 3.3 — Architecture completion
- ProfilesFragment still needs decomposition (807 → target <300)
  - Extract voice grid rendering to a VoiceGridDelegate
  - Extract profile grid to a ProfileGridDelegate  
  - Extract download management to a DownloadDelegate
- RecyclerView for Apps list (can grow to hundreds)
- RecyclerView for voice grid (52+ items rebuilt every tick)

### 3.4 — Data integrity
- Add JSON schema version to VoiceProfile and AppRule
- Use .commit() instead of .apply() on explicit save actions

---

## WHAT TO KEEP FROM THE OTHER INSTANCE'S WORK

These changes are solid and should NOT be reverted:

- ✅ AppContainer + KyokanApp (DI pattern)
- ✅ ProfilesViewModel + HomeViewModel (state management)
- ✅ RulesFragment → 3 delegates (decomposition)
- ✅ LanguageRoutingDelegate (dynamic language support)
- ✅ SeekBarUtil + SpinnerUtil (deduplication)
- ✅ dimens.xml (type scale + spacing tokens)
- ✅ values-night/colors.xml (dark mode structure — values need fixing)
- ✅ DayNight theme (structure — needs the ThemeColors fix to work)
- ✅ Proxy hardening (rate limiting, validation)
- ✅ NotificationReaderService caching (cachedRules/cachedProfiles)
- ✅ Download confirmation dialog
- ✅ sans-serif typography in XML
- ✅ contentDescription additions
- ✅ .kt → .md doc renames
- ✅ Breathing pulse animation
- ✅ AutoTransition on collapsibles
- ✅ Fragment fade transitions
- ✅ savedInstanceState for filter state
- ✅ renderVoiceGridThrottled (500ms debounce)
- ✅ VoiceCardBuilder extraction
- ✅ "synthesizing..." preview feedback

---

*End of audit + review report.*
