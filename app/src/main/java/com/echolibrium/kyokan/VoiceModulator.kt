package com.echolibrium.kyokan

/**
 * Takes a VoiceProfile (baseline) and a SignalMap (context)
 * and produces ModulatedVoice (what actually gets sent to TTS).
 *
 * Enhanced with:
 *   - PersonalitySensitivity: profile-specific signal reaction multipliers
 *   - Time-of-day: environmental modifier applied as pre-signal baseline
 *   - Flood tiers: notification overload affects voice quality
 *   - Emotion blends: complex mixed states produce specific modulation
 *   - Sarcasm: flattened intonation for detected ironic content
 *   - Modulation budget cap: prevents runaway compounding
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
    // DSP fields — computed here, applied in AudioDsp
    val jitterAmount:   Float = 0f,
    val shouldTrailOff: Boolean = false,
    val volume:         Float = 1.0f
)

/** Time-of-day baseline modifier */
private data class TimeModifier(
    val pitch: Float = 0f,
    val speed: Float = 0f,
    val breath: Int = 0,
    val intonation: Float = 0f
)

object VoiceModulator {

    private const val MAX_PITCH_DELTA = 0.25f
    private const val MAX_SPEED_DELTA = 0.35f

    fun modulate(profile: VoiceProfile, signal: SignalMap): ModulatedVoice {
        return try {
            modulateInternal(profile, signal)
        } catch (e: Exception) {
            // Fallback to safe defaults on any modulation error
            ModulatedVoice(
                pitch = profile.pitch.guardNaN(1.0f),
                speed = profile.speed.guardNaN(1.0f),
                breathIntensity = profile.breathIntensity,
                breathCurvePosition = profile.breathCurvePosition.guardNaN(0f),
                breathPause = profile.breathPause,
                stutterIntensity = profile.stutterIntensity,
                stutterFrequency = profile.stutterFrequency,
                stutterPosition = profile.stutterPosition.guardNaN(0f),
                stutterPause = profile.stutterPause,
                intonationIntensity = profile.intonationIntensity,
                intonationVariation = profile.intonationVariation.guardNaN(0.5f)
            )
        }
    }

    private fun modulateInternal(profile: VoiceProfile, signal: SignalMap): ModulatedVoice {
        val traj  = trajectoryMultiplier(signal.trajectory)
        val inten = signal.intensityLevel
        val sens  = profile.sensitivity

        // ── Time-of-day baseline ──────────────────────────────────────────
        val timeMod = timeOfDayModifier(signal.hourOfDay)

        // ── Continuous signal strengths × sensitivity ─────────────────────
        val distressStrength = when (signal.warmth) {
            WarmthLevel.DISTRESSED -> 1.0f
            WarmthLevel.LOW        -> 0.1f
            else -> 0f
        } * sens.distressSensitivity

        val urgencyStrength = when (signal.urgencyType) {
            UrgencyType.EXPIRING  -> 1.0f
            UrgencyType.BLOCKING  -> 0.8f
            UrgencyType.REAL      -> 0.6f
            UrgencyType.SOFT      -> 0.2f
            UrgencyType.NONE      -> 0f
        } * sens.distressSensitivity

        val emotionalStrength = when (signal.stakesType) {
            StakesType.EMOTIONAL  -> 1.0f
            StakesType.FINANCIAL  -> 0.6f
            StakesType.PHYSICAL   -> 0.5f
            StakesType.TECHNICAL  -> 0.1f
            StakesType.FAKE       -> 0f
            StakesType.NONE       -> 0f
        } * sens.warmthSensitivity

        val fakeStrength = (if (signal.stakesType == StakesType.FAKE) 0.8f else 0f) * sens.fakeSensitivity

        // ── Pitch ─────────────────────────────────────────────────────────
        var pitchDelta = lerp(0f, 0.25f, (urgencyStrength * 0.5f + distressStrength * 0.5f) * inten * traj)
        pitchDelta += timeMod.pitch
        if (signal.unknownFactor) pitchDelta += 0.03f
        val pitch = (profile.pitch + pitchDelta - fakeStrength * 0.05f).coerceIn(0.5f, 2.0f)

        // ── Speed ─────────────────────────────────────────────────────────
        var speedDelta = urgencyStrength * 0.35f * inten * traj * sens.speedReactivity
        val speedDown = if (signal.trajectory == Trajectory.COLLAPSED) 0.18f else 0f
        speedDelta += timeMod.speed
        if (signal.unknownFactor) speedDelta -= 0.05f
        speedDelta += floodSpeedMod(signal.floodTier)
        if (signal.senderPressure > 0.8f) speedDelta -= 0.05f
        val speed = (profile.speed + speedDelta - speedDown - fakeStrength * 0.12f).coerceIn(0.5f, 3.0f)

        // ── Breathiness ───────────────────────────────────────────────────
        var msgBreath = lerp(0f, 0.8f, distressStrength * inten * traj) +
                        lerp(0f, 0.3f, if (signal.emojiSad) inten else 0f)
        if (signal.unknownFactor) msgBreath -= 0.04f
        if (signal.senderPressure > 0.6f) msgBreath += 0.05f
        val breathBase = profile.breathIntensity.toFloat() + timeMod.breath
        val breathIntensity = blendAdd(breathBase, breathBase > 0, msgBreath * 35f + floodBreathMod(signal.floodTier), traj).toInt().coerceIn(0, 100)

        // ── Stutter ───────────────────────────────────────────────────────
        val msgStutter = lerp(0f, 0.9f, urgencyStrength * inten * traj) +
                         lerp(0f, 0.6f, distressStrength * inten * traj) +
                         lerp(0f, 0.3f, if (signal.register == Register.RAW) inten else 0f)
        val msgStutterClamped = msgStutter.coerceIn(0f, 1f)
        val stutterIntensity  = blendAdd(profile.stutterIntensity.toFloat(), profile.stutterIntensity > 0, msgStutterClamped * 30f, traj).toInt().coerceIn(0, 100)
        val stutterFrequency  = blendAdd(profile.stutterFrequency.toFloat(), profile.stutterFrequency > 0, msgStutterClamped * 25f, traj).toInt().coerceIn(0, 100)

        // ── Intonation ────────────────────────────────────────────────────
        var msgInton = lerp(0f, 0.8f, (emotionalStrength * 0.7f + inten * 0.3f) * traj * sens.rangeReactivity) -
                       lerp(0f, 0.4f, fakeStrength)
        msgInton += timeMod.intonation
        // Sarcasm: flatten intonation
        if (signal.detectedSarcasm) msgInton *= 0.7f
        // Flood fatigue
        msgInton *= floodIntonationMul(signal.floodTier)

        val intonationIntensity = (profile.intonationIntensity + msgInton * 35f)
            .coerceIn(0f, 100f).toInt()
        val intonationVariation = (profile.intonationVariation + msgInton * 0.2f)
            .coerceIn(0f, 1f)

        // ── Modulation budget cap ─────────────────────────────────────────
        val cappedPitch = capDelta(profile.pitch, pitch, MAX_PITCH_DELTA)
        val cappedSpeed = capDelta(profile.speed, speed, MAX_SPEED_DELTA)

        // ── Emotion blend overrides ───────────────────────────────────────
        val blended = applyBlend(signal.emotionBlend, cappedPitch, cappedSpeed, breathIntensity, intonationIntensity)

        // ── Jitter ────────────────────────────────────────────────────────
        val jitterAmount = (distressStrength * 0.12f + urgencyStrength * 0.04f)
            .coerceIn(0.01f, 0.15f)

        // ── Trailing off ──────────────────────────────────────────────────
        val shouldTrailOff = signal.trajectory == Trajectory.COLLAPSED

        // ── Volume ────────────────────────────────────────────────────────
        var volumeDelta = 0f
        volumeDelta += urgencyStrength * 0.15f
        volumeDelta -= distressStrength * 0.10f
        if (signal.hourOfDay in 22..23 || signal.hourOfDay in 0..6) volumeDelta -= 0.08f
        if (signal.floodTier == FloodTier.OVERWHELMED) volumeDelta -= 0.06f
        if (signal.trajectory == Trajectory.COLLAPSED) volumeDelta -= 0.10f
        val volume = (1.0f + volumeDelta).coerceIn(0.4f, 1.3f)

        return ModulatedVoice(
            pitch               = blended.first.guardNaN(1.0f),
            speed               = blended.second.guardNaN(1.0f),
            breathIntensity     = blended.third,
            breathCurvePosition = profile.breathCurvePosition.guardNaN(0f),
            breathPause         = profile.breathPause,
            stutterIntensity    = stutterIntensity,
            stutterFrequency    = stutterFrequency,
            stutterPosition     = profile.stutterPosition.guardNaN(0f),
            stutterPause        = profile.stutterPause,
            intonationIntensity = blended.fourth,
            intonationVariation = intonationVariation.guardNaN(0.5f),
            jitterAmount        = jitterAmount,
            shouldTrailOff      = shouldTrailOff,
            volume              = volume
        )
    }

    /** Replace NaN or Infinity with a safe default */
    private fun Float.guardNaN(default: Float): Float =
        if (this.isNaN() || this.isInfinite()) default else this

    // ── Time-of-day modifier ──────────────────────────────────────────────
    private fun timeOfDayModifier(hour: Int): TimeModifier = when (hour) {
        in 5..7   -> TimeModifier(pitch = -0.05f, speed = -0.08f, breath = 3, intonation = -0.10f)
        in 8..11  -> TimeModifier()
        in 12..14 -> TimeModifier(pitch = -0.02f, speed = 0.03f, breath = 1, intonation = 0.05f)
        in 15..18 -> TimeModifier(speed = 0.05f, intonation = 0.08f)
        in 19..21 -> TimeModifier(pitch = -0.04f, speed = -0.05f, breath = 4, intonation = -0.05f)
        in 22..23 -> TimeModifier(pitch = -0.08f, speed = -0.12f, breath = 8, intonation = -0.15f)
        in 0..4   -> TimeModifier(pitch = -0.10f, speed = -0.18f, breath = 12, intonation = -0.20f)
        else -> TimeModifier()
    }

    // ── Flood tier modifiers ──────────────────────────────────────────────
    private fun floodSpeedMod(tier: FloodTier) = when (tier) {
        FloodTier.CALM, FloodTier.ACTIVE, FloodTier.BUSY -> 0f
        FloodTier.FLOODED     -> 0.08f
        FloodTier.OVERWHELMED -> 0.05f
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

    // ── Emotion blend overrides ───────────────────────────────────────────
    private data class Quad(val first: Float, val second: Float, val third: Int, val fourth: Int)

    private fun applyBlend(
        blend: EmotionBlend, pitch: Float, speed: Float, breath: Int, intonation: Int
    ): Quad = when (blend) {
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

    private fun capDelta(baseline: Float, modulated: Float, maxDelta: Float): Float {
        val delta = modulated - baseline
        return if (kotlin.math.abs(delta) > maxDelta) {
            baseline + maxDelta * if (delta > 0f) 1f else -1f
        } else modulated
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
}
