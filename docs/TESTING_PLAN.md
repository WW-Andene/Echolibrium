# Minimum Test Suite (Priority Order)

## Unit Tests (JUnit)
1. **VoiceProfile serialization round-trip** — `toJson()` → `fromJson()` produces identical object
2. **AppRule serialization round-trip** — same pattern
3. **DND time logic** — `isDndActive()` for: same-hour (disabled), wrap-around midnight, normal range
4. **Word rule application** — case-insensitive matching, empty rules, overlapping rules
5. **buildMessage formatting** — all 4 read modes: full, title_only, app_only, text_only

## Integration Tests (Android Instrumented)
6. **Profile save/load cycle** — create profile, modify, save, reload, verify
7. **AudioPipeline crossfade** — verify no NaN/Infinity in output samples
8. **VoiceRegistry consistency** — all Kokoro + Piper IDs are unique, all have valid sample rates

## Manual Test Checklist
9. **Rotation on every screen** — tab state, profile edits, logcat scroll position
10. **Light mode on every screen** — no invisible text, no broken colors
