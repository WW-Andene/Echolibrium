# Kyōkan Issue & Fix Log

> Chronological record of every issue encountered and fixed, for continuity between Claude sessions.
> Most recent fixes are at the top.

---

## Session: March 2026 (claude/resume-after-crash-mZ6T1)

Focus: Crash recovery reliability — making the engine resilient to native SIGSEGV on Xiaomi/MediaTek.

### Issue 8: Piper fallback SIGSEGV causes infinite crash loop with no backoff
**Commit:** `b29b5d3`
**Symptom:** On Xiaomi 2306EPN60G (MediaTek Dimensity), the engine stays stuck on "starting" indefinitely. Logcat shows the process restarting every ~23 seconds with "Previous session did not exit cleanly" but zero SherpaEngine output.
**Root cause:** When Kokoro crashed (SIGSEGV), the recovery path called `initPiperFallback()` — but Piper uses the same sherpa-onnx native library (`OfflineTts` constructor). If the native stack is fundamentally broken on this device, Piper ALSO SIGSEGVs. Critically, the `init_in_progress` flag was NOT set before the Piper call, so the crash counter never incremented past the initial value. The app looped forever at `crashCount=1` with no escalation and no error shown.
**Fix:**
1. Set `init_in_progress=true` (with `commit()`) BEFORE calling `initPiperFallback()` in the crash-recovery path, so Piper SIGSEGVs properly increment the crash counter
2. Write status (`"trying fallback engine…"`) before any native call so the UI never stays stuck on `"starting"`
3. After 5+ total crashes (`NATIVE_BROKEN_THRESHOLD`), skip ALL native code entirely and show a clear error: `"native engine incompatible"`. Still retries after 2.5 min in case conditions change
4. `forceRetry()` resets everything so the user can manually override

### Issue 7: Piper fallback delayed until 3rd crash — 70s of no TTS
**Commit:** `f57922d`
**Symptom:** Users on Xiaomi/MediaTek devices where Kokoro SIGSEGVs had to wait through 3 crash-restart cycles (~70 seconds) with no TTS output before Piper kicked in.
**Root cause:** `CRASH_PIPER_FIRST_THRESHOLD` was 3 — the old design required 3 consecutive crashes before activating Piper. This was overly conservative for deterministic failures.
**Fix:** Activate Piper fallback on the very first detected crash (`crashCount > 0`). Kokoro retries continue in the background with exponential backoff (5s, 10s, 20s...). Removed `CRASH_PIPER_FIRST_THRESHOLD` constant.

### Issue 6: `SocVendor` visibility causes compilation error
**Commit:** `e2d9290`
**Symptom:** Build fails: `'public' function exposes its 'private-in-class' parameter type SocVendor`
**Root cause:** `SocVendor` was declared `private enum class` but used as a field type in the public `DeviceProfile` data class.
**Fix:** Removed `private` modifier from `SocVendor` enum.

### Issue 5: Missing `CRASH_RESET_WINDOW_MS` constant
**Commit:** `1e00e21`
**Symptom:** Build fails: `Unresolved reference: CRASH_RESET_WINDOW_MS`
**Root cause:** The constant was accidentally removed when replacing the old crash limit system with exponential backoff. It was still referenced at line 555 for resetting the crash counter after 5 minutes.
**Fix:** Re-added `CRASH_RESET_WINDOW_MS = 300_000L` alongside the new backoff constants. Updated stale KDoc reference from `MAX_INIT_CRASHES` to new backoff behavior.

### Enhancement: Never-give-up init + WakeLock + mmap pre-warming
**Commit:** `4a6380b`
**Changes:**
- Replace crash limit (`MAX_INIT_CRASHES=3`) with exponential backoff that never gives up
- Add `PARTIAL_WAKE_LOCK` during Stage 2 native model loading to prevent MIUI CPU throttling
- Upgrade model pre-warming from sequential read to mmap + `MappedByteBuffer.load()` for forced page residency
- Add TTS process staleness detection in TtsBridge (timestamp-based health check)
- Add battery optimization exemption utilities for Xiaomi/MIUI
- Add `WAKE_LOCK` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permissions

---

## Session: Earlier March 2026 (claude/project-review-zdqX6, merged)

Focus: World-class engine initialization — device profiling, config memory, escalation ladder.

### Enhancement: Device profiling, config memory, pre-warming, adaptive timeout
**Commit:** `347e45e`
**Changes:**
- Comprehensive `DeviceProfile` with RAM, CPU cores, SoC vendor, Xiaomi/MIUI detection
- Device tier scoring (HIGH/MEDIUM/LOW) drives all decisions
- Config memory: persists exact config that successfully loaded Kokoro to SharedPreferences. Next launch skips escalation ladder and uses known-good config directly
- Model pre-warming: sequentially reads entire model file into OS page cache before ORT opens it
- Heap reservation: allocates 25% of max heap, touches every page, then releases — prevents VM region collisions
- Adaptive timeout: .ort 30s base / .onnx 90s base × device tier multiplier
- Thread priority elevation: `THREAD_PRIORITY_URGENT_AUDIO` during native model load
- Granular phase telemetry with init log files

### Enhancement: Staged probe, memory prep, auto-escalation
**Commit:** `2a9cb89`
**Changes:**
- Stage 0: Native stack probe — loads tiny Piper model first to verify JNI/ORT works
- Stage 1: Memory preparation — 2-pass GC + finalization before loading 311MB model
- Stage 2: Auto-escalation — tries progressively safer ORT configs within a single init cycle (optimal → 1-thread/cpu → 1-thread/cpu/debug). No crash counter penalty for Java exceptions.

### Issue 4: Disk/RAM pre-checks, integrity verification
**Commit:** `1aea0af`
**Problems:**
1. No disk space check — model extraction could fail silently mid-write
2. No RAM check — ONNX Runtime silently fails malloc() on low RAM → SIGSEGV
3. No extraction integrity check — truncated files from power loss went undetected
4. Timeouts incorrectly incremented crash counter (causing false lockouts)
**Fixes:** Added 400MB disk check, 350MB RAM check, model >100MB integrity check, timeout no longer increments crash counter.

### Issue 3: sherpa-onnx version and memory diagnostics missing
**Commit:** `93f1032`
**Fix:** Log sherpa-onnx library version and device memory state at init start. Written to engine_init_*.log files for user bug reports.

### Enhancement: Automatic Piper fallback system
**Commit:** `e4055e1`
**Changes:**
- When Kokoro fails, automatically falls back to bundled Piper voice (`en_US-lessac-medium`)
- Three fallback levels: crash loop, warmUp() failure, synthesis-time failure
- `synthesizeWithFallback()` routes to Piper if Kokoro is down
- UI shows "TTS engine: ready (Piper fallback)" in warning color

### Issue 2: Engine init failure logging
**Commit:** `c4398a5`
**Fix:** Write detailed log files (`engine_init_*.log`) on every init failure path. Tapping log path in UI copies content to clipboard.

---

## Session: February 2026 (claude/project-review-zdqX6, PR #48/#49)

Focus: SIGSEGV on Xiaomi/MediaTek — the longest debugging saga.

### Issue 1: SIGSEGV crash on Xiaomi/MediaTek during TTS model loading

This was the original critical bug. It took **8 sequential fix attempts** to fully resolve, each peeling back a layer of the onion:

#### Attempt 1: Delay engine init (❌ insufficient)
**Commit:** `7566cbe`
**Theory:** ONNX Runtime races with HWUI's CommonPool thread pool initialization.
**Fix:** Delay `warmUp()` by 1.5s via `Handler.postDelayed()`.
**Result:** Crash moved from T+2s to T+3.1s. Still crashed.

#### Attempt 2: Post-first-frame init (❌ insufficient)
**Commit:** `0c2b653`
**Theory:** HWUI CommonPool is lazily initialized; need to wait for first frame render.
**Fix:** Trigger warmUp via `window.decorView.post{}` after first layout+draw.
**Result:** Still crashed. HWUI init timing was irrelevant.

#### Attempt 3: Disable hardware acceleration (❌ MIUI ignores it)
**Commit:** `d736042`
**Theory:** No HWUI → no CommonPool mutex → no corruption.
**Fix:** `android:hardwareAccelerated="false"` in manifest.
**Result:** MIUI ignores this flag. Still crashed.

#### Attempt 4: Process isolation (✅ core fix)
**Commit:** `78e69ba`
**Theory:** The crash is caused by ONNX Runtime corrupting HWUI's static mutex (libc++ ODR violation). Put TTS in a process with no HWUI at all.
**Fix:** Run `NotificationReaderService` in `:tts` process. New `TtsBridge` for IPC, `TtsCommandReceiver` for commands.
**Result:** SIGSEGV stopped in the :tts process. But new issues emerged...

#### Attempt 5: Crash-loop breaker + retry button
**Commit:** `684892e`
**Problem:** When OfflineTts() constructor SIGSEGVs, Android auto-restarts the service → infinite crash loop. Engine appeared stuck at ~40%.
**Fix:** SharedPreferences-based `init_in_progress` flag + crash counter. After 3 crashes, stop retrying and show error. Retry button in UI.

#### Attempt 6: Three compounding bugs
**Commit:** `1fe7b4a`
**Problems:**
1. Double `warmUp()` race — ReaderApplication and Service both called it; Service's `writeStatus("starting")` overwrote crash-loop error
2. AudioPipeline bypass — `ensureKokoroReady()` called `initializeKokoro()` directly, bypassing crash-loop breaker
3. Timeouts not counted — hanging inits (no SIGSEGV, just blocked) never incremented crash counter
**Fixes:** Removed warmUp from ReaderApplication, added crash counter check in initializeKokoro, timeouts now increment counter.

#### Attempt 7: init_in_progress flag set too late
**Commit:** `0c70187`
**Problem:** Flag was set inside `initializeKokoro()` AFTER config objects were constructed. Config constructors trigger `System.loadLibrary` (JNI). If native library load SIGSEGVs, flag never gets set → crash counter stays at 0 → infinite loop.
**Fix:** Set flag in `warmUp()` on the main thread, BEFORE the init thread starts. Uses `commit()` for synchronous disk flush.

#### Attempt 8: Force native lib extraction
**Commit:** `f2989db`
**Problem:** On API 23+, Android loads .so directly from APK via mmap. On MediaTek/Xiaomi, mmapping the ~50MB ONNX Runtime .so from the APK triggers SIGSEGV during dlopen.
**Fix:** `useLegacyPackaging = true` in build.gradle (extract .so to filesystem at install time). `extractNativeLibs="true"` in manifest. `numThreads=1` on MediaTek.

### Other fixes in this session:

**Model extraction hardening** (`342e2c9`): Atomic tmp+rename for extraction, version marker for re-extraction on update, synchronized extraction lock.

**Eliminate AssetManager constructors** (`305bbfd`, `2aa62ab`): All model loading switched from `OfflineTts(assetManager=)` to file-based `OfflineTts(config=)`. AssetManager-based constructors crash on some devices.

**NNAPI → CPU provider** (`0ea3215`): NNAPI doesn't support LSTM ops (core to Kokoro model). Was causing model partitioning where Conv/MatMul ran on APU but LSTM fell back to slow reference CPU impl.

**Device-adaptive SoC detection** (`c00d4ec`): Detect SoC vendor at runtime, route MediaTek to NNAPI (later reverted to CPU), Qualcomm to CPU.

**Six IPC/concurrency bugs** (`84988c0`):
1. Handler token mismatch — grouping timer cancellation was no-op
2. postDelayed outside synchronized block — race condition
3. Broken `isMainProcess()` — called non-existent method
4. Crash recovery ran in both processes — SharedPreferences race
5. Dead logcat process spawn — spawned then immediately destroyed
6. TtsBridge TOCTOU race + silent renameTo failure

**Permission button loop on Android 13+** (`c2a1ad0`): Button always sent to App Info, never to Notification Listener Settings. Infinite loop prevented service from starting.

---

## Session: Original Development

### Feature: Chunked synthesis + conversation grouping
**Commit:** `3e721b2`
- Long texts split at sentence boundaries, first chunk plays while rest synthesize
- Rapid messages from same sender buffered 1.5s and merged

### Feature: On-demand voice download system
**Commit:** `6760a4b`
- Split Piper voices: 8 bundled in APK, 36 downloadable from GitHub Releases
- Reduced APK from ~3.4GB to ~600MB

### Feature: ORT pre-optimization
**Commit:** `129f1c3`
- Convert ONNX → ORT format at build time for 5-10x faster model loading

### Feature: Crash reporting
**Commit:** `cf5dd32`
- Detect native crashes via SharedPreferences session tracking
- Recover crash info from logcat
- Show CrashReportActivity with "Report to GitHub" button

### CI/Build fixes
- `f8c2957`: Trigger build on `claude/*` branches
- `a56a28c`: Crash-proof engine startup + CI asset/APK verification
- `9368320`: Self-host all dependencies (AAR, models, voices) on GitHub Releases
- `08e3197`: Fix APK exceeding 4GB ZIP32 limit (delete .onnx after ORT conversion)
- `e3c3d9f`: Fix `download-models.sh` exit code 2 from pipefail + empty glob

---

## Recurring Themes

1. **Xiaomi/MIUI is the #1 source of bugs.** Modified ART runtime, aggressive process killing, CPU throttling, ignored manifest flags. Every fix needs Xiaomi testing.

2. **Native SIGSEGV is not catchable in Java.** The only detection mechanism is the `init_in_progress` SharedPreferences flag. If it's not set before native code runs, the crash is invisible.

3. **Both Kokoro AND Piper use the same native library.** If `libonnxruntime.so` or `libsherpa-onnx-jni.so` fails to load, ALL TTS is broken. The crash recovery system must account for this.

4. **Cross-process IPC is fragile.** The status file can contain stale data if the :tts process crashes. The UI should always consider the timestamp.

5. **Config constructors trigger native library loading.** Even creating `OfflineTtsVitsModelConfig()` in Kotlin can trigger `System.loadLibrary` → SIGSEGV. The `init_in_progress` flag must be set before ANY sherpa-onnx class is instantiated.
