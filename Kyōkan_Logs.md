# Kyōkan Issue & Fix Log

> Chronological record of every issue encountered and fixed, for continuity between Claude sessions.
> Most recent entries are at the top.

---

## v5.0.0 — Clean Rebuild (March 12, 2026)

**Branch:** `claude/remove-kokoro-apk-My8qx`
**Decision:** Full rebuild from scratch. Previous 35+ commits after PR #16 created
compounding complexity that made the app unlaunchable. See `docs/REBUILD_PLAN.md`
for phased approach.

### What was wrong with v4.0

1. **espeak-ng-data missing** — Piper's phonemization data was bundled inside the
   Kokoro model tar.bz2. Removing Kokoro removed espeak-ng-data. Engine couldn't init.
2. **SherpaEngine grew to ~1500 lines** — Device profiling, crash recovery, exponential
   backoff, config memory, escalation ladder, heap reservation, model pre-warming.
   Each "fix" added complexity that masked the real issue.
3. **Cross-process architecture fragile** — :tts process isolation, TtsBridge IPC,
   watchdog, stale status files, SharedPreferences races.
4. **ORT version mismatch** — CI-generated .ort files incompatible with AAR's bundled
   ORT 1.17.1. Silent SIGSEGV on load.

### Lessons carried forward

- Test each feature on device before adding the next
- espeak-ng-data is required by Piper (now downloaded explicitly)
- MediaTek needs single-threaded inference (SIGSEGV on thread pool init)
- AssetManager-based OfflineTts constructors crash on Xiaomi/MediaTek
- Keep engine code minimal until proven necessary
- Version-gate any persistent crash state

---

> Previous logs archived. See git history for v4.0 and earlier.
