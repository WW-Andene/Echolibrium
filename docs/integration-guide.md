// ═══════════════════════════════════════════════════════════════════════════
// ARCHITECTURE: VOICE ≠ PERSONALITY
// ═══════════════════════════════════════════════════════════════════════════
//
//   Voice (tara, leo, zoe)     = timbre, the mouth, an audio asset
//   Personality (EchoPersonality) = who speaks through that mouth
//   Voice Science (system prompt) = engine that translates personality → delivery
//   Preprocessor                  = text-level direction for current DeepInfra path
//
//   The user picks a voice AND a personality independently.
//   Same personality on different voices = same person, different body.
//   Different personalities on same voice = different people, same body.
//
//
// ═══════════════════════════════════════════════════════════════════════════
// INTEGRATION — AudioPipeline.processItem()
// ═══════════════════════════════════════════════════════════════════════════
//
// Current code (around line 130):
//
//     val result: Pair<FloatArray, Int> = if (VoiceRegistry.isCloud(voiceId)) {
//         synthesizeWithCloud(item.text, item)
//
// Change to:
//
//     val result: Pair<FloatArray, Int> = if (VoiceRegistry.isCloud(voiceId)) {
//         // Load active personality from prefs (Custom Core manages this)
//         val personality = loadActivePersonality(ctx)
//
//         // Process text through personality lens
//         var processed = OrpheusPreprocessor.process(item.text, personality, appName = null)
//
//         // Apply quirks (Tamagotchi behaviors)
//         val tone = OrpheusPreprocessor.detectTonePublic(item.text)  // expose if needed
//         processed = OrpheusPreprocessor.applyQuirks(processed, tone, personality)
//
//         // Track interaction for growth
//         personality.copy(totalInteractions = personality.totalInteractions + 1)
//             .also { saveActivePersonality(ctx, it) }
//
//         synthesizeWithCloud(processed, item)
//
// Kokoro and Piper hit else branches. Raw text. No personality. No processing.
//
//
// ═══════════════════════════════════════════════════════════════════════════
// WHERE PERSONALITY LIVES
// ═══════════════════════════════════════════════════════════════════════════
//
// Now (pre-Custom Core):
//   EchoPersonality serialized to SharedPreferences as JSON.
//   User picks from presets (companion, narrator, chaotic, stoic) in ProfilesFragment.
//   Basic fields evolve via simple rules (familiarityLevel++ over time, etc.)
//
// Future (Custom Core online):
//   EchoPersonality stored in SQLite via echo_behavior.json.
//   Groq (Llama 3.1 8B) analyzes notification patterns and returns behavior patches:
//     - "User gets lots of work Slack notifications at night → add quirk: softer delivery after 9pm"
//     - "User's most frequent contact is Sarah → familiarityLevel for Sarah-sourced notifs increases"
//     - "User dismissed 3 gasp reactions → reduce emotionalRange by 0.05"
//   Patches are JSON diffs applied to EchoPersonality fields.
//
// Future (self-hosted Orpheus):
//   orpheus-system-prompt-template.txt gets populated with personality fields
//   and sent as the system message. The merged model (Orpheus + Llama-3.2-3B-Instruct)
//   processes the personality direction AND renders speech in one forward pass.
//   OrpheusPreprocessor becomes a lightweight pre-cleaner (strip URLs, emoji)
//   since the model handles the personality-to-delivery translation internally.
//
//
// ═══════════════════════════════════════════════════════════════════════════
// EXAMPLES — SAME NOTIFICATION, DIFFERENT PERSONALITIES
// ═══════════════════════════════════════════════════════════════════════════
//
// Input: "Gmail. Your application to Anthropic has been rejected."
//
// ── Echo (companion, high empathy, warm humor) ──
//   Tone: SAD
//   empathyLevel 0.8 → trailing ellipsis
//   agreeableness 0.8 + tagRestraint 0.6 → sigh (0.4 + 0.8*0.3 = 0.64 > 0.6)
//   Output: "Gmail. Your application to Anthropic has been rejected... <sigh>"
//   Orpheus renders: slow, dropping F0, breathy, trailing, exhale at end
//
// ── Chronicle (narrator, low empathy, dry humor) ──
//   Tone: SAD
//   empathyLevel 0.4 → clean period, no trailing
//   tagRestraint 0.8 → tone strength 0.4 + 0.4*0.3 = 0.52 < 0.8 → NO tag
//   Output: "Gmail. Your application to Anthropic has been rejected."
//   Orpheus renders: measured, factual, slight F0 drop but controlled, moves on
//
// ── Spark (chaotic, high emotion, absurd humor) ──
//   Tone: SAD
//   empathyLevel 0.5 → trailing ellipsis (borderline, let it through)
//   tagRestraint 0.2 → tone strength 0.4 + 0.5*0.3 = 0.55 > 0.2 → YES tag
//   agreeableness 0.4 → groan not sigh
//   Output: "Gmail. Your application to Anthropic has been rejected... <groan>"
//   Orpheus renders: dramatic drop, groans with frustration more than sadness
//
// ── Basalt (stoic, minimal emotion, no humor) ──
//   Tone: SAD
//   empathyLevel 0.3 → clean period
//   tagRestraint 0.95 → tone strength 0.4 + 0.3*0.3 = 0.49 < 0.95 → NO tag
//   Output: "Gmail. Your application to Anthropic has been rejected."
//   Orpheus renders: flat, steady, barely registers emotionally, just information
//
//
// ═══════════════════════════════════════════════════════════════════════════
// Input: "WhatsApp. Jake. bro I just walked into a glass door lmao 😂"
//
// ── Echo (companion, warm humor, freq 0.5) ──
//   Tone: HUMOR (lmao + 😂)
//   humorStyle "warm" → chuckle
//   tagRestraint 0.6 → strength 0.5 + 0.5*0.3 = 0.65 > 0.6 → YES
//   Output: "WhatsApp. Jake. bro I just walked into a glass door lmao <chuckle>"
//
// ── Spark (chaotic, absurd humor, freq 0.8) ──
//   Tone: HUMOR
//   humorStyle "absurd" → laugh
//   tagRestraint 0.2 → strength 0.5 + 0.8*0.3 = 0.74 > 0.2 → YES
//   Output: "WhatsApp. Jake. bro I just walked into a glass door lmao <laugh>"
//
// ── Basalt (stoic, no humor, freq 0.05) ──
//   Tone: HUMOR
//   humorStyle "none" → no tag even if threshold passes
//   tagRestraint 0.95 → strength 0.5 + 0.05*0.3 = 0.515 < 0.95 → NO
//   Output: "WhatsApp. Jake. bro I just walked into a glass door lmao"
//   Orpheus renders: completely straight-faced delivery. The content is funny.
//   The voice doesn't think so.
//
//
// ═══════════════════════════════════════════════════════════════════════════
// GROWTH OVER TIME — THE TAMAGOTCHI
// ═══════════════════════════════════════════════════════════════════════════
//
// Week 1 (maturity 1, familiarity 0.0):
//   Personality is fresh. Defaults apply. Reactions are generic.
//   No quirks active beyond presets.
//
// Week 4 (maturity 3, familiarity 0.3):
//   Custom Core has observed patterns:
//     - User gets 40+ Slack notifications daily → quirk: "treats Slack as background noise"
//     - User's most emotional reactions are to WhatsApp from "Sarah"
//     - User is active 7am-1am, quiet 1am-7am
//   Behavior patches applied:
//     - Slack notifications get reduced emotional range (0.6 → 0.3 for Slack)
//     - WhatsApp from Sarah gets elevated warmth
//     - After midnight: formality drops, pace slows
//
// Month 3 (maturity 6, familiarity 0.6):
//   Personality has meaningfully diverged from its preset.
//   - humorFrequency adjusted based on which tags the user responded to
//   - emotionalRange tuned to match what the user seems to prefer
//   - New quirks emerged: "pauses before calendar reminders the user usually ignores"
//   - familiarityLevel high enough that delivery feels personal, not generic
//
// Month 6+ (maturity 8+, familiarity 0.8+):
//   The personality is now unique to this user. No other user has this exact
//   configuration. It's the accumulated result of thousands of interactions
//   filtered through the Groq reasoning engine. It knows the user's world
//   through their notification patterns — their contacts, their apps, their
//   schedule, their emotional responses. It's not just reading notifications.
//   It's a companion that has been paying attention.
//
//
// ═══════════════════════════════════════════════════════════════════════════
// FILE SUMMARY
// ═══════════════════════════════════════════════════════════════════════════
//
// EchoPersonality.kt              — The soul. Structured data. Serializable.
// OrpheusPreprocessor.kt          — Translates personality + content → text direction.
// orpheus-system-prompt-template.txt — For future self-hosted: personality as system message.
// AudioPipeline.kt (modified)     — Loads personality, feeds preprocessor, sends to cloud.
// echo_behavior.json (Custom Core) — Persistent personality state on device.
// Groq (Llama 3.1 8B)            — Reasoning engine that evolves the personality.
