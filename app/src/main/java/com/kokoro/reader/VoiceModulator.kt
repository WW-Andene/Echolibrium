package com.kokoro.reader

import kotlin.random.Random

/**
 * Takes a VoiceProfile (baseline), SignalMap (context), and MoodState
 * and produces ModulatedVoice (what actually gets sent to TTS).
 *
 * Enhanced with:
 *   - MoodState: sustained background vocal state affects baseline
 *   - Time-of-day: environmental modifier applied as pre-signal baseline
 *   - Flood tiers: notification overload affects voice quality
 *   - Personality sensitivity: profile-specific signal reaction multipliers
 *   - Intonation floor: minimum natural prosodic variation
 *   - Emotional blend: complex mixed states produce specific modulation
 *   - Unknown sender: stranger voice behavior
 *   - Sarcasm: flattened intonation for detected ironic content
 *   - Reassurance: calmer voice for reassuring messages
 */
data class ModulatedVoice(
    val pitch:       Float,
    val speed:       Float,
    val breathIntensity:     Int,
    val breathCurvePosition: Float,
    val breathPause:         Int,
    val stutterIntensity:    Int,
    val stutterFrequency:    Int,
    val stutterPosition:     Float,
    val stutterPause:        Int,
    val intonationIntensity: Int,
    val intonationVariation: Float,
    // New fields for downstream DSP
    val jitterAmount:   Float = 0f,
    val shouldTrailOff: Boolean = false
)

/** Time-of-day baseline modifier (§6.0) */
data class TimeModifier(
    val pitch: Float = 0f,
    val speed: Float = 0f,
    val breath: Int = 0,
    val intonation: Float = 0f,
    val label: String = "NEUTRAL"
)

object VoiceModulator {

    private const val MIN_INTONATION_FLOOR = 5  // §8.4: minimum natural prosodic variation

    /**
     * Full modulation with mood and time-of-day.
     * Backward-compatible: defaults allow calling without mood.
     */
    fun modulate(
        profile: VoiceProfile,
        signal: SignalMap,
        mood: MoodState = MoodState(),
        hourOfDay: Int = signal.hourOfDay
    ): ModulatedVoice {
        val traj  = trajectoryMultiplier(signal.trajectory)
        val inten = signal.intensityLevel
        val sens  = profile.sensitivity

        // ── Time-of-day baseline (§6.0) ───────────────────────────────────
        val timeMod = timeOfDayModifier(hourOfDay)

        // ── Mood baseline (§1.0) ──────────────────────────────────────────
        val decayedMood = mood.decayed(sens.moodDecayRate)

        // ── Continuous signal strengths × sensitivity (§3.0) ──────────────
        val rawDistress = when (signal.warmth) {
            WarmthLevel.DISTRESSED -> 1.0f
            WarmthLevel.LOW        -> 0.1f
            else -> 0f
        }
        val rawUrgency = when (signal.urgencyType) {
            UrgencyType.EXPIRING  -> 1.0f
            UrgencyType.BLOCKING  -> 0.8f
            UrgencyType.REAL      -> 0.6f
            UrgencyType.SOFT      -> 0.2f
            UrgencyType.NONE      -> 0f
        }
        val rawEmotional = when (signal.stakesType) {
            StakesType.EMOTIONAL  -> 1.0f
            StakesType.FINANCIAL  -> 0.6f
            StakesType.PHYSICAL   -> 0.5f
            StakesType.TECHNICAL  -> 0.1f
            StakesType.FAKE       -> 0f
            StakesType.NONE       -> 0f
        }
        val rawFake = if (signal.stakesType == StakesType.FAKE) 0.8f else 0f

        // Apply personality sensitivity coefficients (§3.0)
        val distressStrength  = rawDistress  * sens.distressSensitivity
        val urgencyStrength   = rawUrgency   * sens.distressSensitivity
        val emotionalStrength = rawEmotional * sens.warmthSensitivity
        val fakeStrength      = rawFake      * sens.fakeSensitivity

        // ── Reassurance dampening (§2.1) ──────────────────────────────────
        val reassuranceFactor = if (signal.has(Intent.REASSURANCE)) 0.5f else 1.0f

        // ── Pitch ─────────────────────────────────────────────────────────
        var pitchDelta = lerp(0f, 0.25f, (urgencyStrength * 0.5f + distressStrength * 0.5f) * inten * traj * reassuranceFactor)
        pitchDelta += timeMod.pitch  // time-of-day
        // Mood baseline (§1.0)
        if (decayedMood.arousal > 0.7f) pitchDelta += 0.08f
        if (decayedMood.arousal < 0.2f) pitchDelta -= 0.06f
        if (decayedMood.valence < -0.5f) pitchDelta += 0.04f
        if (decayedMood.valence > 0.6f) pitchDelta -= 0.02f
        // Unknown sender (§8.5): slight alertness
        if (signal.unknownFactor) pitchDelta += 0.03f
        val pitch = (profile.pitch + pitchDelta - fakeStrength * 0.05f).coerceIn(0.5f, 2.0f)

        // ── Speed ─────────────────────────────────────────────────────────
        var speedDelta = urgencyStrength * 0.35f * inten * traj * sens.speedReactivity * reassuranceFactor
        val speedDown = if (signal.trajectory == Trajectory.COLLAPSED) 0.18f else 0f
        speedDelta += timeMod.speed  // time-of-day
        // Mood baseline (§1.0)
        if (decayedMood.arousal > 0.7f) speedDelta += 0.10f
        if (decayedMood.arousal < 0.2f) speedDelta -= 0.15f
        if (decayedMood.stability < 0.4f) speedDelta += (Random.nextFloat() - 0.5f) * 0.10f
        // Unknown sender (§8.5): slightly slower, more careful
        if (signal.unknownFactor) speedDelta -= 0.05f
        // Flood tier (§8.3)
        speedDelta += floodSpeedMod(signal.floodTier)
        // Sender pressure (§7.0)
        if (signal.senderPressure > 0.8f) speedDelta -= 0.05f
        val speed = (profile.speed + speedDelta - speedDown - fakeStrength * 0.12f).coerceIn(0.5f, 3.0f)

        // ── Breathiness ───────────────────────────────────────────────────
        var msgBreath = lerp(0f, 0.8f, distressStrength * inten * traj * reassuranceFactor) +
                        lerp(0f, 0.3f, if (signal.emojiSad) inten else 0f)
        // Mood breathiness (§1.0)
        if (decayedMood.valence < -0.6f) msgBreath += 0.08f
        if (decayedMood.arousal < 0.2f) msgBreath += 0.06f
        // Unknown sender: less breathy (§8.5)
        if (signal.unknownFactor) msgBreath -= 0.04f
        // Sender pressure (§7.0)
        if (signal.senderPressure > 0.6f) msgBreath += 0.05f
        val breathBase = profile.breathIntensity.toFloat() + timeMod.breath
        val breathIntensity = blendAdd(breathBase, breathBase > 0, msgBreath * 35f + floodBreathMod(signal.floodTier), traj).toInt().coerceIn(0, 100)

        // ── Stutter ───────────────────────────────────────────────────────
        val msgStutter = lerp(0f, 0.9f, urgencyStrength * inten * traj * reassuranceFactor) +
                         lerp(0f, 0.6f, distressStrength * inten * traj * reassuranceFactor) +
                         lerp(0f, 0.3f, if (signal.register == Register.RAW) inten else 0f)
        val msgStutterClamped = msgStutter.coerceIn(0f, 1f)
        val stutterIntensity  = blendAdd(profile.stutterIntensity.toFloat(), profile.stutterIntensity > 0, msgStutterClamped * 30f, traj).toInt().coerceIn(0, 100)
        val stutterFrequency  = blendAdd(profile.stutterFrequency.toFloat(), profile.stutterFrequency > 0, msgStutterClamped * 25f, traj).toInt().coerceIn(0, 100)

        // ── Intonation ────────────────────────────────────────────────────
        var msgInton = lerp(0f, 0.8f, (emotionalStrength * 0.7f + inten * 0.3f) * traj * sens.rangeReactivity) -
                       lerp(0f, 0.4f, fakeStrength)
        msgInton += timeMod.intonation  // time-of-day
        // Sarcasm: flatten intonation slightly (§8.2)
        if (signal.detectedSarcasm) msgInton *= 0.7f
        // Mood intonation (§1.0)
        var intonMoodMul = 1.0f
        if (decayedMood.arousal < 0.2f) intonMoodMul *= 0.7f
        if (decayedMood.stability < 0.4f) intonMoodMul *= 1.3f
        if (decayedMood.valence > 0.5f) intonMoodMul *= 1.1f
        // Flood tier (§8.3)
        intonMoodMul *= floodIntonationMul(signal.floodTier)

        var intonationIntensity = (profile.intonationIntensity + msgInton * 35f * intonMoodMul)
            .coerceIn(0f, 100f).toInt()
        // §8.4: Intonation floor — minimum natural variation (Robot exempt)
        if (profile.name != "Robot") {
            intonationIntensity = maxOf(intonationIntensity, MIN_INTONATION_FLOOR)
        }

        var intonationVariation = (profile.intonationVariation + msgInton * 0.2f)
            .coerceIn(0f, 1f)
        if (decayedMood.stability < 0.4f) intonationVariation = (intonationVariation * 1.3f).coerceIn(0f, 1f)

        // ── Emotional blend overrides (§2.3) ──────────────────────────────
        // Blends modify the combination rather than stacking independently
        val blendedPitch = applyBlend(signal.emotionBlend, pitch, speed, breathIntensity, intonationIntensity)

        // ── Jitter (§5.1) — computed here, applied in AudioDsp ───────────
        val arousalExcess = maxOf(0f, decayedMood.arousal - 0.6f)
        val jitterAmount = (distressStrength * 0.12f + arousalExcess * 0.08f).coerceIn(0.01f, 0.15f)

        // ── Trailing off (§4.3B) ──────────────────────────────────────────
        val shouldTrailOff = decayedMood.arousal < 0.25f || signal.trajectory == Trajectory.COLLAPSED

        return ModulatedVoice(
            pitch               = blendedPitch.first.guardNaN(1.0f),
            speed               = blendedPitch.second.guardNaN(1.0f),
            breathIntensity     = blendedPitch.third,
            breathCurvePosition = profile.breathCurvePosition.guardNaN(0f),
            breathPause         = profile.breathPause,
            stutterIntensity    = stutterIntensity,
            stutterFrequency    = stutterFrequency,
            stutterPosition     = profile.stutterPosition.guardNaN(0f),
            stutterPause        = profile.stutterPause,
            intonationIntensity = blendedPitch.fourth,
            intonationVariation = intonationVariation.guardNaN(0.5f),
            jitterAmount        = jitterAmount,
            shouldTrailOff      = shouldTrailOff
        )
    }

    /** Replace NaN or Infinity with a safe default */
    private fun Float.guardNaN(default: Float): Float =
        if (this.isNaN() || this.isInfinite()) default else this

    // ── Time-of-day modifier (§6.0) ───────────────────────────────────────
    private fun timeOfDayModifier(hour: Int): TimeModifier = when (hour) {
        in 5..7   -> TimeModifier(pitch = -0.05f, speed = -0.08f, breath = 3, intonation = -0.10f, label = "MORNING_WARMUP")
        in 8..11  -> TimeModifier(label = "MORNING")
        in 12..14 -> TimeModifier(pitch = -0.02f, speed = 0.03f, breath = 1, intonation = 0.05f, label = "MIDDAY")
        in 15..18 -> TimeModifier(speed = 0.05f, intonation = 0.08f, label = "AFTERNOON")
        in 19..21 -> TimeModifier(pitch = -0.04f, speed = -0.05f, breath = 4, intonation = -0.05f, label = "EVENING")
        in 22..23 -> TimeModifier(pitch = -0.08f, speed = -0.12f, breath = 8, intonation = -0.15f, label = "LATE_NIGHT")
        in 0..4   -> TimeModifier(pitch = -0.10f, speed = -0.18f, breath = 12, intonation = -0.20f, label = "DEAD_OF_NIGHT")
        else -> TimeModifier()
    }

    // ── Flood tier modifiers (§8.3) ───────────────────────────────────────
    private fun floodSpeedMod(tier: FloodTier) = when (tier) {
        FloodTier.CALM, FloodTier.ACTIVE, FloodTier.BUSY -> 0f
        FloodTier.FLOODED     -> 0.08f
        FloodTier.OVERWHELMED -> 0.05f  // paradoxical slowing under overload
    }
    private fun floodBreathMod(tier: FloodTier) = when (tier) {
        FloodTier.CALM, FloodTier.ACTIVE, FloodTier.BUSY -> 0f
        FloodTier.FLOODED     -> 5f
        FloodTier.OVERWHELMED -> 10f
    }
    private fun floodIntonationMul(tier: FloodTier) = when (tier) {
        FloodTier.CALM, FloodTier.ACTIVE, FloodTier.BUSY -> 1.0f
        FloodTier.FLOODED     -> 0.85f
        FloodTier.OVERWHELMED -> 0.70f
    }

    // ── Emotional blend (§2.3) ────────────────────────────────────────────
    // Returns (pitch, speed, breathIntensity, intonationIntensity)
    private fun applyBlend(
        blend: EmotionBlend, pitch: Float, speed: Float, breath: Int, intonation: Int
    ): Quad<Float, Float, Int, Int> = when (blend) {
        EmotionBlend.NERVOUS_EXCITEMENT -> Quad(
            pitch + 0.06f, speed + 0.08f, (breath - 3).coerceAtLeast(0), intonation + 10
        )
        EmotionBlend.SUPPRESSED_TENSION -> Quad(
            pitch + 0.03f, speed - 0.04f, breath, (intonation - 8).coerceAtLeast(0)
        )
        EmotionBlend.NOSTALGIC_WARMTH -> Quad(
            pitch - 0.04f, speed - 0.06f, breath + 5, intonation + 5
        )
        EmotionBlend.RESIGNED_ACCEPTANCE -> Quad(
            pitch - 0.02f, speed - 0.08f, breath, (intonation - 12).coerceAtLeast(0)
        )
        EmotionBlend.WORRIED_AFFECTION -> Quad(
            pitch + 0.02f, speed - 0.02f, breath + 3, intonation + 3
        )
        EmotionBlend.NONE -> Quad(pitch, speed, breath, intonation)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    private fun blendAdd(base: Float, baseHasIt: Boolean, msgSignal: Float, traj: Float): Float {
        return if (baseHasIt && msgSignal > 0f)
            base * (1f + msgSignal / 35f * traj)
        else
            base + msgSignal * traj
    }

    private fun trajectoryMultiplier(t: Trajectory) = when (t) {
        Trajectory.FLAT      -> 1.0f
        Trajectory.BUILDING  -> 1.3f
        Trajectory.PEAKED    -> 1.6f
        Trajectory.COLLAPSED -> 0.7f
    }

    /** Simple 4-tuple for returning blend results */
    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
