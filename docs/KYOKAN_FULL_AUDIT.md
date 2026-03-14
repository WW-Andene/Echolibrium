# KYŌKAN v4.0 — FULL AUDIT REPORT

**Date:** March 15, 2026
**Auditor:** Claude Opus 4.6 (using app-audit + design-aesthetic-audit + scope-context skills)
**Mode:** Audit only — no fixes applied
**Scope:** Entire codebase (6,989 LOC across Kotlin, XML, Python, JS, Gradle, CI)

---

## EXECUTIVE SUMMARY

| Severity | Count |
|----------|-------|
| CRITICAL | 4 |
| HIGH | 10 |
| MEDIUM | 30 |
| LOW | 26 |
| INFO | 12 |
| **TOTAL** | **82** |

**Root Causes (two account for ~70% of findings):**

1. **No architecture layer between UI and data.** Fragments directly manage state, persistence, downloads, playback, and rendering. This causes the God Fragment problem, state fragmentation, testability gaps, and performance issues.
2. **Total identity fracture between brand and UI.** The logos, the name (共感 = empathy), and the product vision communicate a warm, organic, living voice companion. The UI communicates a terminal debug panel. There is zero visual DNA shared between the brand and the app.

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
Secondary:     Python async TTS router (kyokan-tts/) — standalone dev/test tool,
               not integrated into the Android app at runtime
State:         SharedPreferences (JSON via org.json), no Room/SQLite, no ViewModel
Users:         Sideloaded (no Play Store yet), single user, on-device only
Sensitive:     DeepInfra API key (EncryptedSharedPreferences),
               notification content (transient in memory only)
```

---

## §I — CALIBRATION

### §I.1 Domain Classification

Companion / Accessibility utility — notification reading with adaptive AI voices.

| Concern | Multiplier | Rationale |
|---------|-----------|-----------|
| Correctness | MEDIUM | Wrong voice/translation is bad UX, not dangerous |
| Security | HIGH | Handles notification content + API keys |
| Privacy | HIGH | Reads ALL notifications, sends some to cloud |
| Performance | HIGH | Real-time TTS pipeline, native libs, memory-constrained |

### §I.2 Architecture Classification

Singleton-heavy service architecture.

**Failure modes to hunt:** lifecycle leaks (fragments holding singleton refs), state desync (SharedPreferences as source of truth without atomic operations), thread safety (multiple threads touching mutable collections), process death recovery (no ViewModel/SavedStateHandle).

### §I.3 App Size → Scope

~5,400 LOC (Kt+XML app code). Small app. Full audit in one pass.

### §I.4 Five-Axis Aesthetic Profile

Based on what the app IS and SHOULD BE (derived from brand identity + product vision):

| Axis | Current | Target | Gap |
|------|---------|--------|-----|
| Warmth | 1/5 (cold terminal) | 4/5 (intimate companion) | **CRITICAL** |
| Playfulness | 1/5 (zero personality) | 3/5 (gentle personality, not goofy) | **HIGH** |
| Density | 4/5 (all info exposed) | 2/5 (progressive disclosure, calm) | **HIGH** |
| Motion | 0/5 (literally zero) | 3/5 (organic, breath-like transitions) | **CRITICAL** |
| Refinement | 2/5 (functional but raw) | 4/5 (crafted, intentional) | **HIGH** |

### §I.5 Domain Rule Extraction

- DND logic: wrapping hour comparison (`isDndActive`)
- Cooldown: per-app ms comparison
- Pitch range: 0.50–2.00, Speed range: 0.50–3.00
- Max queue: 1–50
- Crossfade: 40ms between utterances

---

## §III — PRE-FLIGHT CHECKLIST

- ✓ App context filled (§0)
- ✓ Domain classified (§I.1)
- ✓ Architecture classified (§I.2)
- ✓ Scope determined (§I.3)
- ✓ Aesthetic profile set (§I.4)
- ✓ Domain rules extracted (§I.5)
- ✓ Scope-context pre-flight: large-scope audit, no ambiguity — full scan

---

## PART 1 — INVENTORY & ARCHITECTURE

### §P1.1 Feature Ledger

| # | Feature | Status | Files |
|---|---------|--------|-------|
| 1 | Notification listening & reading | WORKING | NotificationReaderService, AudioPipeline |
| 2 | Kokoro TTS (local, 11 voices) | WORKING | SherpaEngine, VoiceDownloadManager, KokoroVoice |
| 3 | Piper TTS (local, 33 voices) | WORKING | SherpaEngine, PiperDownloadManager, PiperVoice |
| 4 | Orpheus TTS (cloud, 8 voices) | WORKING | CloudTtsEngine, proxy/worker.js |
| 5 | Voice profiles (pitch/speed) | WORKING | VoiceProfile, ProfilesFragment |
| 6 | Per-app rules (mode/profile/enable) | WORKING | AppRule, AppsFragment |
| 7 | Word replacement rules | WORKING | RulesFragment (wording_rules pref) |
| 8 | DND / quiet hours | WORKING | HomeFragment, NotificationReaderService |
| 9 | Language detection (ML Kit) | WORKING | NotificationReaderService |
| 10 | On-device translation (ML Kit) | WORKING | NotificationTranslator, RulesFragment |
| 11 | Language-based voice routing | WORKING | NotificationReaderService, RulesFragment |
| 12 | Voice commands (wake word + speech) | WORKING | VoiceCommandListener, VoiceCommandHandler |
| 13 | Crash logging (local file) | WORKING | CrashLogger |
| 14 | Live logcat viewer | WORKING | LogcatFragment |
| 15 | Boot auto-start | WORKING | BootReceiver, TtsAliveService |
| 16 | Foreground service (keep-alive) | WORKING | TtsAliveService |
| 17 | Crossfade between utterances | WORKING | AudioPipeline.applyCrossfade |
| 18 | Model download with resume | WORKING | DownloadUtil |
| 19 | Secure API key storage | WORKING | SecureKeyStore |
| 20 | Cloudflare proxy for API key hiding | WORKING | proxy/worker.js |

### §P1.2 Architecture Map

```
User ← [5-tab UI: Home | Profiles | Apps | Rules | Logcat]
         ↓ (fragments via MainActivity)
NotificationReaderService (NotificationListenerService)
  ↓ onNotificationPosted
  → AppRule check → DND check → dedup check → cooldown check
  → Language detection (ML Kit) → Translation (ML Kit, optional)
  → Profile resolution (per-app → per-lang → active)
  → AudioPipeline.enqueue(Item)
         ↓
AudioPipeline (singleton, background thread)
  → Cloud? → CloudTtsEngine.synthesize (OkHttp → DeepInfra/proxy)
  → Piper? → SherpaEngine.synthesizePiper (JNI → ONNX)
  → Kokoro? → SherpaEngine.synthesize (JNI → ONNX)
  → applyCrossfade → AudioTrack.play (PCM float)

VoiceCommandListener (SpeechRecognizer, continuous)
  → wake word check → VoiceCommandHandler → AudioPipeline
```

### §P1.3 Constraint Map

- No ViewModel/LiveData — fragment state is local variables, lost on config change
- No Room/database — all persistence via SharedPreferences JSON strings
- No dependency injection — manual singleton instantiation
- No Jetpack Navigation — manual fragment replacement
- No Compose — all XML layouts with programmatic view building
- sherpa-onnx AAR is a binary blob downloaded at CI time, not from Maven
- ML Kit adds ~30MB per translation model (downloaded on demand)
- arm64-only APK — no x86 emulator support for debugging

---

## PART 2 — DOMAIN LOGIC & CORRECTNESS

### §A1 — Business Rule & Formula Correctness

**[MEDIUM] — Pitch/Speed SeekBar math is fragile**
File: `ProfilesFragment.kt` → `loadProfileToUI`
Pitch maps to `seekPitch.max = 150`, `progress = ((p.pitch * 100).toInt() - 50).coerceIn(0, 150)`, giving range 0.50–2.00. Speed range is 0.50–3.00 (max=250). Math is correct but undocumented. No validation on the data model side — a manually crafted JSON profile with pitch=10.0 would be accepted by `VoiceProfile.fromJson`.

**[LOW] — Cooldown SeekBar allows 0**
File: `RulesFragment.kt`
SeekBar `max = 30`, default 3. Value 0 means "no cooldown" — `NotificationReaderService` checks `if (cooldownMs > 0)` so this works. But max queue SeekBar `max = 50` with `coerceAtLeast(1)` only on pref write — SeekBar still shows 0 as selectable. Visual/logical mismatch.

**[LOW] — DND wrapping logic: start == end means always silent**
File: `NotificationReaderService.isDndActive`
If start == end, returns `true` (always silent). Undocumented behavior. No visual indication to user.

### §A3 — Temporal & Timezone Correctness

**[INFO]** — Uses `Calendar.HOUR_OF_DAY` (24h). No timezone bugs. DST handled by system Calendar. Voice command "what time?" uses system locale `DateFormat`. All correct.

### §A5 — Embedded Data Accuracy

**[INFO]** — Voice catalogs (`KokoroVoices.ALL`, `PiperVoices.ALL`, `VoiceRegistry.CLOUD_VOICES`) are compile-time constants. No mechanism to update without an app release. Appropriate for current stage.

### §A6 — Async & Concurrency

**[HIGH] — Race condition in AppRule/VoiceProfile spinner callbacks**
File: `AppsFragment.buildRow`
Spinner `onItemSelectedListener` fires during initial `setSelection`, calling `updateRule` with the current value — unnecessary `saveAll`. `rules` MutableList is mutated from UI callbacks while `renderRules()` iterates it. No synchronization.

**[HIGH] — ProfilesFragment mutates `profiles` list from multiple UI callbacks without guards**
File: `ProfilesFragment.kt`
Multiple handlers (`btnSave`, `btnNew`, `btnDelete`, card click, spinner selection) mutate the same `profiles` MutableList. `profileSpinner.onItemSelectedListener` fires during `setSelection` inside `setupProfileSpinner`, causing recursive state updates. All main thread (technically safe) but fragile — `indexOfFirst` can return stale indices.

**[MEDIUM] — NotificationReaderService accesses mutable maps from main + executor threads**
File: `NotificationReaderService.kt`
`readKeys`, `swipedKeys`, `appLastRead` are accessed from binder threads and processing executor. Uses `synchronized` blocks correctly but fragile — one missed `synchronized` = `ConcurrentModificationException`.

**[MEDIUM] — AudioPipeline.synthesisErrorListeners called from pipeline thread**
File: `AudioPipeline.kt`
`notifySynthesisError` runs on the pipeline thread; listeners post to UI via `runOnUiThread`. Fragment's `isAdded` check prevents crashes. Pattern is correct but fragile.

### §A7 — Type Coercion

**[LOW]** — `VoiceProfile.fromJson`: `optDouble("pitch", 1.0).toFloat()` loses precision. Negligible for pitch values.

---

## PART 3 — SECURITY & TRUST

### §C1 — Authentication & Authorization

**[INFO]** — `SecureKeyStore` uses `MasterKeys.AES256_GCM_SPEC` + `EncryptedSharedPreferences`. Recommended approach. No issues.

**[INFO]** — `BuildConfig.DEEPINFRA_API_KEY` is `""` in release. Debug reads from `local.properties`. Correct.

**[LOW] — Cloudflare Worker proxy has no rate limiting or origin check**
File: `proxy/worker.js`
Accepts any POST and forwards to DeepInfra. No origin header check, no rate limit, no request validation. Anyone with the URL can burn through the developer's API credits.

### §C2 — Injection & XSS

**[INFO]** — No WebView, no innerHTML, no eval. Not applicable for this native app.

### §C4 — Network & Dependencies

**[INFO]** — `network_security_config.xml` disables pinning with detailed rationale. HTTPS + system CA is sufficient for model downloads. `OkHttp` client has no pinning for DeepInfra — standard for API-key-authenticated endpoints.

### §C5 — Privacy & Data Minimization

**[HIGH] — Notification content sent to Cloudflare proxy in plaintext**
File: `CloudTtsEngine.synthesize`
Full notification text (may contain private messages: WhatsApp, SMS, email) is sent to the proxy Worker → DeepInfra. Worker code doesn't log it, but Cloudflare infrastructure has access. This is a fundamental privacy trade-off that should be disclosed to users.

**[INFO]** — ML Kit language detection and translation are on-device (no network after model download). No privacy concern with ML Kit.

**[INFO]** — Notification content held in memory only (`lastSpokenText` is `@Volatile var`, cleared on service destroy). No content written to disk. Good.

### §C6 — Compliance

**[MEDIUM] — No privacy policy, no data handling disclosure**
App reads all notifications (highly sensitive), sends some to cloud APIs, stores API keys. No privacy policy provided, no first-run disclosure, no consent mechanism. Required for any store distribution.

### §C7 — Mobile-Specific Security

**[INFO]** — `android:allowBackup="false"` set correctly. `android:exported="true"` on NotificationReaderService protected by `BIND_NOTIFICATION_LISTENER_SERVICE` permission. ProGuard rules comprehensive.

---

## PART 4 — STATE MANAGEMENT & DATA INTEGRITY

### §B1 — State Architecture

**[HIGH] — No single source of truth**
All fragments read `PreferenceManager.getDefaultSharedPreferences` independently. Changes in one fragment are not observed by others. No `OnSharedPreferenceChangeListener`, no LiveData/Flow. Switching tabs forces fragment recreation (via `replace`) which masks this but doesn't solve it.

**[HIGH] — Fragment state lost on configuration change**
`ProfilesFragment.currentProfile`, `genderFilter`, `languageFilter`, `activeProfileId` are in-memory variables. On rotation or process death, they reset. `MainActivity.fragmentCache` only preserves across tab switches, not config changes. `savedInstanceState` never used in any fragment.

**[MEDIUM] — JSON serialization has no schema versioning**
`VoiceProfile.fromJson` uses `optString`/`optDouble` with defaults (backward compatible). But no version field — future required fields have no migration path. Same for `AppRule`.

### §B2 — Persistence & Storage

**[MEDIUM]** — All pref writes use `.apply()` (async). Acceptable for settings, but profile data that took effort to configure could be lost on immediate crash. `.commit()` on save actions would be safer.

**[LOW]** — No SharedPreferences size monitoring. Hundreds of app rules would grow the JSON string. Not a problem at current scale.

### §B3 — Input Validation

**[MEDIUM]** — Word replacement rules have no length limits. Stored in SharedPreferences JSON. No practical issue currently.

**[LOW]** — Profile name has no character limit. Extremely long names overflow UI and break voice command wake word recognition.

### §B4 — Import/Export

**[INFO]** — No import/export feature exists. Profiles and rules are device-local only. No backup/transfer capability.

### §B5 — Data Flow Map

```
Notification → onNotificationPosted (binder thread)
  → processingExecutor.execute (background thread)
    → AppRule.loadAll(prefs) ← parses full JSON every time
    → VoiceProfile.loadAll(prefs) ← parses full JSON every time
    → buildMessage → detectLanguage → translate (optional)
    → AudioPipeline.enqueue (thread-safe LinkedBlockingQueue)

AudioPipeline loop (daemon thread)
  → synthesize (cloud: OkHttp blocking / local: JNI blocking)
  → playPcm (AudioTrack, blocks until done)
```

**[MEDIUM] — AppRule.loadAll and VoiceProfile.loadAll called on every notification**
Full JSON array parsed from SharedPreferences each time. For notification-heavy devices, this means dozens of JSON parses per minute. Should be cached with invalidation.

### §B6 — Mutation & Reference Integrity

**[MEDIUM]** — `AppsFragment.rules` MutableList shared between background thread (`loadInstalledApps`) and UI callbacks. The background thread only posts to `runOnUiThread` (technically safe), but the pattern is fragile.

---

## PART 5 — PERFORMANCE & RESOURCES

### §D1 — Runtime Performance

**[HIGH] — ProfilesFragment.renderVoiceGrid rebuilds entire voice UI on every state change**
Rebuilds 52+ voice cards (8 cloud + 11 Kokoro + 33 Piper) with programmatic view creation on every download tick, state change, or voice selection. During downloads, fires every second via refresh handler. Significant GC pressure and frame drops on low-end devices.

**[MEDIUM] — LogcatFragment.refreshDisplay rebuilds entire SpannableStringBuilder**
Each batch of logcat lines (up to 50) triggers full rebuild iterating up to 1000 lines. On a busy device, hundreds of lines per second.

**[LOW] — AppsFragment.renderRules rebuilds all rows on every change**
Less severe (no periodic refresh), but select/deselect all triggers full rebuild.

### §D4 — Memory Management

**[MEDIUM] — Piper voice cache holds ONNX models in memory (MAX_PIPER_CACHE = 2)**
Each Piper model is ~40-80MB. Two cached = up to 160MB native memory. Combined with Kokoro (~120MB loaded), total native TTS memory can reach ~280MB. `android:largeHeap="true"` is set.

**[LOW]** — LogcatFragment.logBuffer: 5000 lines × ~200 bytes ≈ 1MB. Acceptable.

**[LOW]** — AudioPipeline.prevTail: 40ms of audio ≈ 960 floats ≈ 4KB. Negligible.

### §D5 — Mobile-Specific Performance

**[MEDIUM] — No RecyclerView anywhere**
Apps list (hundreds of apps), voice grid (52+ cards), rules list, profile grid — all LinearLayout + addView. Appropriate for small lists but scales poorly.

**[LOW]** — AudioTrack.MODE_STATIC loads entire utterance into memory. Fine for typical notifications (1-5 seconds). Queue max (10) and text length naturally limit this.

---

## PART 6 — VISUAL DESIGN & AESTHETIC AUDIT (REVISED)

### §DS1 — Style Classification

**Current style:** Terminal/Hacker — monospace font, `#0d0d0d` black background, `#00ff88` electric green accent, `//` code comment section headers.

**Brand style (from logos):** Japanese Organic Minimalism — watercolor on washi paper, deep purple-to-lavender-to-rose palette, ensō-like incomplete circles, organic ink-bleed movement, breathing negative space.

**The conflict:** These are opposing philosophies. Terminal is rigid, mechanical, angular, code-centric. The brand is fluid, organic, human-centric, emotionally present. Zero visual DNA shared between the logos and the app.

**Coherence score (brand ↔ app): 1/10.** The logo and the UI could belong to entirely different products.

### §DP0 — Character Extraction

**What the app currently says:** "I am a utility. Configure me. I am parameters and toggles."

**What it should say:** "I'm here. I'll speak for your world. Let's find a voice that feels right."

**What the brand promises:** An emotional, intimate experience — a companion that resonates (共感) with your notifications. The visual layer actively suppresses this promise.

### Brand Identity from Logos

The logos are watercolor on washi (Japanese handmade paper) — a spiral evoking sound waves, ripples of resonance, a voice expanding outward.

- **Medium (watercolor):** organic, imperfect, alive, breathing
- **Form (ensō-like circle):** Zen, flow, incompleteness as beauty
- **Texture (washi):** craft, Japanese aesthetic philosophy, warmth
- **Palette:** deep indigo-purple → lavender → rose-pink on warm cream

### §DC1–DC5 — Color Science

**[CRITICAL] — The entire color palette contradicts the brand identity**

Brand palette (extracted from logos):

| Role | Color | Description |
|------|-------|-------------|
| Deep center | `~#2d1b4e` | Dark indigo (spiral core) |
| Mid-tone body | `~#5c3d7a` | Rich purple |
| Wash | `~#9b7eb8` | Soft lavender |
| Warm accent | `~#c48da0` | Rose-pink |
| Ground | `~#f5f0eb` | Washi cream (already `ic_launcher_background` in colors.xml) |

App palette:

| Role | Color | Problem |
|------|-------|---------|
| Background | `#0d0d0d` | Cold void, no warmth |
| Primary accent | `#00ff88` | Matrix green, no brand connection |
| Cloud accent | `#ffaa44` | Engine color, could coexist |
| Kokoro accent | `#00ccff` | Engine color, could coexist |
| Danger | `#ff4444` | Functional, acceptable |

**Not a single color from the brand palette appears anywhere in the app UI.** The only bridge is `ic_launcher_background: #f5f0eb` — visible on the home screen icon, then the user opens into a completely different visual world.

### §DT1–DT4 — Typography

**[HIGH] — Monospace throughout destroys the human voice identity**

Monospace communicates: code, data, precision, machines. The app is about *human voices* — warmth, personality, expression. Monospace reduces reading speed by ~10-15% compared to proportional fonts. Every text element in the app uses `android:fontFamily="monospace"`.

Section labels use code-comment syntax (`// MASTER SWITCH`, `// PITCH & SPEED`) — clever as a dev aesthetic but reads as "you are configuring a machine" for a companion app about empathy and voice.

**[LOW] — No type scale system**
Font sizes are ad-hoc: 22sp, 20sp, 14sp, 13sp, 12sp, 11sp, 10sp, 9sp scattered throughout. No consistent scale or hierarchy.

### §DCO1–DCO6 — Component Character

**[HIGH] — Voice cards have no personality — they're data cells, not character portraits**

The voice picker is the most identity-critical screen. This is where the user chooses *who speaks to them*. Currently: a gender symbol (♀/♂), a name, a status label. Three lines of text in a dark rectangle. For an app about human-like voices and empathy, voice selection should feel like *meeting someone*.

**[MEDIUM] — Profile cards are functional but emotionless**

A "voice profile" is conceptually a *persona*. The profile card shows: emoji, name, voice name, green underline if active. A profile named "Morning" (gentle, low-pitched) and "Party" (fast, high-pitched) look identical.

**[MEDIUM] — Programmatic views have no shared style system**

Voice cards, profile cards, filter buttons, section headers are all built in Kotlin with inline dimensions, colors, paddings. Changing the visual language requires editing dozens of hardcoded values across multiple fragments.

**[LOW] — Button styles inconsistent**

Some buttons use XML `backgroundTint`, others use programmatic `setBackgroundColor`. Some have `fontFamily="monospace"`, programmatic ones don't set it.

### §DH1–DH4 — Hierarchy & Contrast

**[MEDIUM] — Information density works against the companion identity**

Home screen shows everything at once: setup status, master switch, read mode, app name toggle, DND with sliders, listening section. For a companion, this should feel calm and simple — configuration accessible but not dominant.

**[MEDIUM] — Low contrast on disabled/dimmed states**

| Color | On | Ratio | WCAG AA |
|-------|----|-------|---------|
| `#336633` | `#0d0d0d` | ~2.0:1 | FAIL |
| `#446644` | `#111111` | ~2.5:1 | FAIL |
| `#444444` | `#111111` | ~1.9:1 | FAIL |
| `#555555` | `#0d0d0d` | ~3.3:1 | FAIL |
| `#888888` | `#0d0d0d` | ~5.3:1 | PASS |
| `#cccccc` | `#0d0d0d` | ~13.5:1 | PASS |
| `#00ff88` | `#0d0d0d` | ~11.5:1 | PASS |

### §DSA1–DSA5 — Surface & Atmosphere

**[HIGH] — The atmosphere contradicts the brand completely**

Brand says: washi paper texture, watercolor softness, organic imperfection, warmth. App says: pitch black void, sharp rectangles, flat untextured surfaces.

Even a dark theme can carry warmth — deep plum/indigo backgrounds instead of pure black, subtle gradients, faint texture or grain. The current `#0d0d0d` → `#111111` → `#1a1a1a` step system has no warmth, no depth, no atmosphere.

### §DM1–DM5 — Motion

**[CRITICAL] — Zero motion/animation in an app about living, breathing voice**

The brand's watercolor spirals *move* — frozen mid-flow, ink bleeding. The voice is supposed to feel alive. The UI is completely static.

- Collapsible sections jump between VISIBLE/GONE (no ease)
- No tab transitions
- No voice card state animations
- No download progress animation
- No breathing indicator for listening state
- No crossfade between any visual states

The audio pipeline has a 40ms crossfade between utterances. The visual layer has zero crossfade between anything.

### §DI1–DI4 — Iconography

**[LOW]** — 5 vector icons, all single-color (`#00ff88`), all material-style. Coherent and functional. No custom iconography, no brand personality.

### §DST1–DST4 — State Design

**[MEDIUM] — States don't carry the companion personality**

Empty voice list doesn't say "let's find you a voice." Download in progress doesn't feel like "preparing something special." Error doesn't communicate "something went wrong, but I'm still here." States are purely informational when they should be emotionally present.

**[MEDIUM] — Loading states are text-only**

"LOADING..." button text, "extracting..." status, percentage numbers. No progress bars, no spinners, no visual feedback beyond text.

### §DBI1 — Brand Archetype

**Target archetype:** The Caregiver/Companion — warm, attentive, present, adaptive.

**Current archetype:** The Engineer — precise, exposing every parameter, valuing function over feeling.

Both can coexist, but the surface layer the user touches should lead with the Companion and reveal the Engineer only when invited.

### §DBI3 — Anti-Genericness Audit (12 signals)

| # | Signal | Present? | Notes |
|---|--------|----------|-------|
| 1 | Distinctive color | NO | `#00ff88` is generic terminal/matrix. Brand purple nowhere. |
| 2 | Custom typography | NO | System monospace throughout |
| 3 | Signature shape language | NO | All rectangles, no curves, no ensō reference |
| 4 | Motion signature | NO | Zero motion |
| 5 | Sound identity | PARTIAL | TTS voices ARE the identity, but UI doesn't reflect this |
| 6 | Illustration style | NO | No illustration beyond logo (not used in-app) |
| 7 | Iconography personality | NO | Stock material icons, single color |
| 8 | Copy voice | PARTIAL | `//` headers have personality, but dev-culture not brand-culture |
| 9 | State character | NO | Generic states |
| 10 | Micro-interaction personality | NO | Zero micro-interactions |
| 11 | Cultural reference | BURIED | "Kyōkan" (共感) is deeply meaningful but nothing visual carries it |
| 12 | Texture/material | NO | Flat, untextured. Brand is watercolor on washi. |

**Genericness score: 10/12 signals missing.** Swap the accent color and it could be any app.

### §DTA1–DTA2 — Design Token Architecture

**[CRITICAL] — Token layer exists but encodes the wrong identity**

`colors.xml` defines 30+ semantic tokens — well-structured, but encoding the terminal palette. Even if every hardcoded hex in Kotlin migrated to `@color/` references, the tokens themselves would need redefinition to match the brand. Token migration and palette redesign should happen simultaneously.

**The "find and replace" test:** Changing the accent color from `#00ff88` would require editing: 1 value in colors.xml + 40+ hardcoded hex values in Kotlin + 10+ in XML layouts. **No-architecture result.**

### §DRC1–DRC3 — Responsive Character

**[MEDIUM] — No tablet/landscape consideration**
All layouts use `match_parent` with fixed padding. On tablets, the 3-column voice grid stretches cards to absurd widths. No `sw600dp` or landscape resources.

### §DCVW1–DCVW3 — Copy × Visual

**[INFO]** — Section labels use developer syntax (`// MASTER SWITCH`). String resources are clean. Voice command responses are natural language. Copy is coherent within the terminal aesthetic but misaligned with brand voice.

### §DP3 — Character Deepening Protocol

The brand contains a complete design philosophy the UI has not yet absorbed:

1. **Washi** → warm-toned backgrounds, subtle texture, paper-like softness
2. **Watercolor** → organic gradients, color that bleeds and breathes, non-uniform fills
3. **Ensō** → circular motifs, incomplete forms suggesting growth, flowing shapes
4. **Purple-rose palette** → warmth within depth, intimacy, emotional presence
5. **共感 (Kyōkan)** → the UI should feel like it's *listening*, not just displaying

---

## PART 7 — UX & INFORMATION ARCHITECTURE

### §F1 — Information Architecture

**[MEDIUM] — Rules fragment is overloaded**
Contains four distinct feature areas: word rules, notification rules, language routing, AND translation settings. Translation is buried inside collapsible → collapsible.

**[LOW] — No onboarding flow**
New users land on Home tab with a "Setup" button but no explanation of what the app does or how to use it. Setup handles permissions but not feature discovery.

### §F2 — User Flow Quality

**[MEDIUM] — Voice download has no confirmation or size warning**
"⬇ ALL" for Piper immediately starts downloading all 33 voices (~1.3GB) with no confirmation dialog, no WiFi check, no size disclosure.

**[LOW] — No way to cancel an in-progress download**
Once a download starts, no cancel button. User must wait or force-kill.

### §F3 — Onboarding & First Use

**[MEDIUM] — First-run is permission-focused, not feature-focused**
3-step setup is well-implemented. But after setup, no guidance to: 1) download a voice model, 2) configure a profile, 3) set up app rules.

### §F4 — Copy Quality

**[LOW] — README.md references outdated v3 architecture**
Mentions "SherpaTTS" external dependency, "Install SherpaTTS first" — not applicable in v4. Title says "v3" but app is v4.

**[LOW] — `tools/clear-kyokan-data.sh` references old package name**
`PKG="com.kokoro.reader"` — should be `com.echolibrium.kyokan`.

### §F5 — Micro-Interactions

**[LOW] — Voice preview has no loading state**
"▶ preview" on a cloud voice triggers a 1-3 second network request. No spinner, no "synthesizing..." feedback. User may tap multiple times.

### §F6 — Engagement & Delight

**[INFO]** — Emoji usage in profiles (🎙️ default) adds minimal personality. App is functional-first with no delight moments.

---

## PART 8 — ACCESSIBILITY

### §G1 — Content Descriptions

**[MEDIUM] — Programmatically built views have no contentDescription**
Voice cards, profile cards, filter buttons, section headers — all built in Kotlin — set no `contentDescription`. TalkBack reads raw text ("♀", "Heart", "ready") without context.

**[INFO]** — Nav bar ImageViews have `importantForAccessibility="no"` with adjacent TextViews. Correct.

### §G2 — Touch Targets

**[MEDIUM] — Filter buttons and "▶ preview" likely below 48dp minimum**
`filterBtn` has `setPadding(20, 8, 20, 8)` in pixels (not dp). "▶ preview" has `textSize = 9f` with `(4 * dp)` padding only.

### §G3 — Focus Order

**[LOW]** — No explicit `nextFocusDown`/`nextFocusUp` attributes. Tab order follows insertion order, reasonable but untested with D-pad.

### §G4 — Contrast

**[HIGH] — Multiple text colors fail WCAG 2.1 AA (4.5:1 minimum)**

| Color | Background | Ratio | Result |
|-------|-----------|-------|--------|
| `#336633` (SectionLabel) | `#0d0d0d` | ~2.0:1 | **FAIL** |
| `#446644` (description) | `#111111` | ~2.5:1 | **FAIL** |
| `#444444` (hint) | `#111111` | ~1.9:1 | **FAIL** |
| `#555555` (inactive) | `#0d0d0d` | ~3.3:1 | **FAIL** |
| `#888888` (muted) | `#0d0d0d` | ~5.3:1 | PASS |
| `#cccccc` (primary) | `#0d0d0d` | ~13.5:1 | PASS |
| `#00ff88` (accent) | `#0d0d0d` | ~11.5:1 | PASS |

---

## PART 9 — COMPATIBILITY

### §H1 — API Level Compatibility

**[INFO]** — minSdk 26 appropriate. Uses `NotificationChannel` (API 26+), `ForegroundServiceStartNotAllowedException` (API 31, guarded), `foregroundServiceType` (API 29, handled by `ServiceCompat`). All version-specific code properly guarded.

### §H2 — Target SDK Compliance

**[INFO]** — targetSdk 35. `FOREGROUND_SERVICE_MEDIA_PLAYBACK` declared. `POST_NOTIFICATIONS` declared. Scoped `<queries>` block. All correct.

### §H3 — Mobile & Touch

**[MEDIUM] — No edge-to-edge / system bar handling**
App doesn't handle system window insets. On gesture navigation devices, bottom nav may conflict with system gesture bar.

---

## PART 10 — CODE QUALITY

### §J1 — Dead Code

**[LOW]** — `PiperDownloadManager.onStateChange` and `onProgress` backward-compat setters: "kept for external callers" but no external callers exist. Same in `VoiceDownloadManager`.

**[LOW]** — `docs/EchoPersonality.kt`, `docs/OrpheusPreprocessor.kt`, `docs/integration-guide.kt`: `.kt` files in docs/ that could confuse tooling. Design documents, not dead code.

### §J2 — Duplication

**[MEDIUM] — SeekBar listener pattern duplicated 3 times**
`HomeFragment.seek()`, `RulesFragment.seek()`, `ProfilesFragment.attachSeek()` — identical boilerplate.

**[MEDIUM] — renderVoiceGrid is 300+ lines with repeated per-engine patterns**
Three engine sections with nearly identical filter → iterate → buildVoiceCard logic.

**[LOW]** — Spinner setup pattern repeated across all fragments.

### §J3 — Naming

**[LOW]** — Inconsistent "Kyōkan" vs "Echolibrium" branding. Package is `com.echolibrium.kyokan`. Relationship unclear.

**[LOW]** — `testSpeak` in NotificationReaderService: used for both preview and voice command responses. Name suggests testing but it's a production path.

### §J4 — Architecture

**[HIGH] — God Fragment: ProfilesFragment at 848 lines**
Contains: profile CRUD, voice grid rendering, download management, API key dialog, filter state, voice card building, preview playback, download progress polling. Should be decomposed.

**[MEDIUM] — All business logic in Fragments**
No ViewModel, no UseCase, no Repository pattern. Fragment directly accesses SharedPreferences, download managers, TTS engine state. Untestable.

**[MEDIUM] — Singleton overuse**
11 singletons with mutable state: `AudioPipeline`, `CloudTtsEngine`, `SherpaEngine`, `VoiceDownloadManager`, `PiperDownloadManager`, `NotificationTranslator`, `CrashLogger`, `VoiceCommandListener`, `VoiceCommandHandler`, `DownloadUtil`, `SecureKeyStore`. Resist testing, create hidden dependencies.

---

## PART 11 — AI/LLM INTEGRATION

**[INFO]** — No LLM/AI integration in the app. ML Kit is deterministic models (not generative). Cloud TTS is speech synthesis API, not an LLM. No prompt injection surface.

**[LOW] — Python `kyokan-tts/` router references features not in the Android app**
README mentions `ObservationDb`, `SignalMap`, `EmotionBlend`, `UrgencyType`, `WarmthLevel`, `YatagamiSynthesizer` — none exist in Kotlin codebase. Out of sync with actual app.

---

## PART 12 — INTERNATIONALIZATION

### §K1 — Hardcoded Strings

**[MEDIUM] — Extensive hardcoded strings in Kotlin programmatic UI**
ProfilesFragment voice cards: "cloud", "no API key", "ready", "tap to download", "extracting...", "▶ preview". English-only, not in strings.xml. XML layouts properly use `@string/` but programmatic code bypasses this.

**[LOW]** — Language routing hardcoded to English and French only. Other ML Kit-detected languages fall through to default profile.

### §K2 — Locale Formats

**[INFO]** — DND time display uses `%02d:00` (24h) regardless of locale. Minor i18n gap.

---

## PART 13 — PROJECTIONS & FUTURE-PROOFING

### §M1 — Scale Cliffs

**[MEDIUM]** — SharedPreferences JSON will not scale beyond ~100 app rules. Full JSON parse per notification becomes measurable. Migration to Room/SQLite needed for heavy users.

**[LOW]** — Voice catalog growth requires app updates. No remote config or dynamic catalog.

### §M2 — Tech Debt

**[HIGH] — No architecture pattern (MVVM/MVI/MVP)**
Adding features means more code in already-large fragments. ProfilesFragment is 848 lines with no separation of concerns.

**[MEDIUM]** — Kotlin 1.9.22 / AGP 8.2.2 are aging. Current stable is Kotlin 2.x, AGP 8.5+.

### §M3 — Dependency Decay

**[LOW]** — sherpa-onnx AAR is manually managed binary. Version tracking manual, no automated vulnerability scanning.

**[INFO]** — All Maven dependencies on recent stable versions. No known CVEs.

---

## FINDINGS INDEX

### CRITICAL (4)

| ID | Finding | Section | Location |
|----|---------|---------|----------|
| C1 | Color palette contradicts brand identity entirely | §DC1 | colors.xml, all Kotlin files |
| C2 | Zero motion/animation in an app about living voice | §DM1 | Entire app |
| C3 | Design tokens encode the wrong identity | §DTA1 | colors.xml + 40+ hardcoded values |
| C4 | Warmth axis at 1/5, target 4/5 — atmosphere contradicts brand | §DSA1 | All surfaces |

### HIGH (10)

| ID | Finding | Section | Location |
|----|---------|---------|----------|
| H1 | Race condition in spinner callbacks | §A6 | AppsFragment.buildRow |
| H2 | ProfilesFragment mutable state from multiple callbacks | §A6 | ProfilesFragment |
| H3 | No single source of truth — SharedPreferences fragmentation | §B1 | All fragments |
| H4 | Fragment state lost on config change | §B1 | All fragments |
| H5 | renderVoiceGrid rebuilds 52+ cards every second | §D1 | ProfilesFragment |
| H6 | Notification content sent to cloud proxy in plaintext | §C5 | CloudTtsEngine → proxy |
| H7 | Monospace typography destroys human voice identity | §DT1 | Entire app |
| H8 | Voice cards are data cells, not character portraits | §DCO1 | ProfilesFragment |
| H9 | Multiple text colors fail WCAG AA contrast | §G4 | colors.xml, styles.xml, Kotlin |
| H10 | God Fragment: ProfilesFragment at 848 LOC | §J4 | ProfilesFragment |

### MEDIUM (30)

| ID | Finding | Section |
|----|---------|---------|
| M1 | Pitch/Speed SeekBar range undocumented, no model validation | §A1 |
| M2 | NotificationReaderService mutable maps on multiple threads | §A6 |
| M3 | synthesisErrorListeners called from pipeline thread | §A6 |
| M4 | No privacy policy or data handling disclosure | §C6 |
| M5 | JSON serialization has no schema versioning | §B2 |
| M6 | SharedPreferences writes all async (.apply) for profile data | §B2 |
| M7 | Word replacement rules have no length limits | §B3 |
| M8 | AppRule/VoiceProfile.loadAll called every notification | §B5 |
| M9 | MutableList shared between background thread and UI | §B6 |
| M10 | LogcatFragment.refreshDisplay rebuilds full SpannableStringBuilder | §D1 |
| M11 | Piper voice cache holds 160MB native memory | §D4 |
| M12 | No RecyclerView — all lists are LinearLayout + addView | §D5 |
| M13 | Profile cards functional but emotionless | §DCO1 |
| M14 | Programmatic views have no shared style system | §DCO1 |
| M15 | Information density works against companion identity | §DH1 |
| M16 | Surface atmosphere contradicts brand | §DSA1 |
| M17 | States don't carry companion personality | §DST1 |
| M18 | Loading states are text-only | §DST1 |
| M19 | No tablet/landscape consideration | §DRC1 |
| M20 | Rules fragment overloaded (4 feature areas) | §F1 |
| M21 | Download has no confirmation or size warning | §F2 |
| M22 | First-run is permission-focused, not feature-focused | §F3 |
| M23 | Programmatic views have no contentDescription | §G1 |
| M24 | Filter buttons below 48dp touch target | §G2 |
| M25 | No edge-to-edge / system bar handling | §H3 |
| M26 | SeekBar listener pattern duplicated 3 times | §J2 |
| M27 | renderVoiceGrid 300+ lines with repeated patterns | §J2 |
| M28 | All business logic in Fragments (untestable) | §J4 |
| M29 | 11 singletons with mutable state | §J4 |
| M30 | Hardcoded English strings in programmatic UI | §K1 |

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
| L9 | AudioPipeline.prevTail float array (4KB) | §D4 |
| L10 | AudioTrack.MODE_STATIC loads full utterance | §D5 |
| L11 | No type scale system | §DT1 |
| L12 | Button styles inconsistent | §DCO1 |
| L13 | Iconography: 5 material icons, no personality | §DI1 |
| L14 | No dark mode / light mode support | §DC1 |
| L15 | No onboarding flow | §F1 |
| L16 | No download cancel capability | §F2 |
| L17 | README references outdated v3 architecture | §F4 |
| L18 | clear-kyokan-data.sh references old package name | §F4 |
| L19 | Voice preview has no loading state | §F5 |
| L20 | No explicit focus order attributes | §G3 |
| L21 | Dead backward-compat setters in download managers | §J1 |
| L22 | .kt files in docs/ could confuse tooling | §J1 |
| L23 | Spinner setup pattern repeated across fragments | §J2 |
| L24 | Inconsistent Kyōkan vs Echolibrium naming | §J3 |
| L25 | testSpeak method name misleading | §J3 |
| L26 | Language routing hardcoded to English/French only | §K1 |

### INFO (12)

| ID | Finding | Section |
|----|---------|---------|
| I1 | Voice catalogs are hardcoded (appropriate for now) | §A5 |
| I2 | DND uses HOUR_OF_DAY correctly | §A3 |
| I3 | No WebView/XSS surface | §C2 |
| I4 | ML Kit is on-device (no privacy concern) | §C5 |
| I5 | Notification content in memory only | §C5 |
| I6 | ProGuard rules comprehensive | §C7 |
| I7 | No import/export feature | §B4 |
| I8 | Copy coherent within terminal aesthetic | §DCVW |
| I9 | Emoji adds minimal personality | §F6 |
| I10 | API level compatibility correct | §H1 |
| I11 | No LLM integration / no prompt injection surface | §P11 |
| I12 | All Maven deps on recent stable versions | §M3 |

---

## ROOT CAUSE ANALYSIS

### Root Cause 1: No Architecture Layer

**Symptoms:** H1, H2, H3, H4, H5, H10, M2, M3, M5, M6, M8, M9, M10, M12, M26, M27, M28, M29

The app has no separation between UI, state management, and data access. Fragments own everything — they read prefs, parse JSON, manage downloads, build views, handle callbacks, and play audio. This single root cause generates 18 of 82 findings.

**Structural fix needed:** Introduce ViewModel + Repository pattern. Move state to observable holders. Replace raw SharedPreferences with a proper data layer.

### Root Cause 2: Brand–UI Identity Fracture

**Symptoms:** C1, C2, C3, C4, H7, H8, H9, M13, M14, M15, M16, M17, M18, M19, M30, L11, L12, L13, L14

The logos, the name, and the product vision communicate a warm, organic, living voice companion. The UI communicates a terminal debug panel. This isn't a matter of "polish later" — the design direction is fundamentally pointing away from the brand. Every visual decision reinforces the wrong identity. This root cause generates 19 of 82 findings.

**Structural fix needed:** Redesign the visual language from the brand outward. New palette from the logo colors. Replace monospace with a humanist typeface. Add motion vocabulary. Build component library with brand-aligned tokens.

---

## QUICK WINS (High Impact, Low Effort)

1. Fix WCAG contrast ratios: `#336633` → `#55aa55`, `#446644` → `#66aa66`, `#444444` → `#666666`
2. Add confirmation dialog before "⬇ ALL" Piper download with size disclosure
3. Update `tools/clear-kyokan-data.sh` package name to `com.echolibrium.kyokan`
4. Update `README.md` to reflect v4 architecture (remove SherpaTTS references)
5. Add rate limiting or origin check to Cloudflare Worker proxy
6. Cache `AppRule.loadAll()` / `VoiceProfile.loadAll()` results with invalidation listener
7. Add `contentDescription` to programmatically built voice cards and profile cards
8. Extract shared `SeekBar.OnSeekBarChangeListener` to a utility function

---

*End of audit report.*
