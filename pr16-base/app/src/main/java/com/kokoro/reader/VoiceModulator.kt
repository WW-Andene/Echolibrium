package com.kokoro.reader

/**
 * Takes a VoiceProfile (baseline) and a SignalMap (context)
 * and produces ModulatedVoice (what actually gets sent to TTS).
 *
 * Two rules:
 *   Addition:       signal has intensity, profile baseline doesn't
 *                   result = baseline + (signal * sensitivity)
 *
 *   Multiplication: signal has intensity AND profile already has it
 *                   result = baseline * (1 + signal * sensitivity)
 *                   two things resonating = exponential, not linear
 *
 * Trajectory scales the multiplier:
 *   FLAT      → 1.0x
 *   BUILDING  → 1.3x  (ramping up)
 *   PEAKED    → 1.6x  (full force)
 *   COLLAPSED → 0.7x  (exhausted, broke — less energy, not more)
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

object VoiceModulator {

    fun modulate(profile: VoiceProfile, signal: SignalMap): ModulatedVoice {
        val traj  = trajectoryMultiplier(signal.trajectory)
        val inten = signal.intensityLevel  // 0..1 smooth float

        // Continuous signal strengths (0..1) — no hard switches
        val distressStrength = when (signal.warmth) {
            WarmthLevel.DISTRESSED -> 1.0f
            WarmthLevel.LOW        -> 0.1f
            else -> 0f
        }
        val urgencyStrength = when (signal.urgencyType) {
            UrgencyType.EXPIRING  -> 1.0f
            UrgencyType.BLOCKING  -> 0.8f
            UrgencyType.REAL      -> 0.6f
            UrgencyType.SOFT      -> 0.2f
            UrgencyType.NONE      -> 0f
        }
        val emotionalStrength = when (signal.stakesType) {
            StakesType.EMOTIONAL  -> 1.0f
            StakesType.FINANCIAL  -> 0.6f
            StakesType.PHYSICAL   -> 0.5f
            StakesType.TECHNICAL  -> 0.1f
            StakesType.FAKE       -> 0f
            StakesType.NONE       -> 0f
        }
        val fakeStrength = if (signal.stakesType == StakesType.FAKE) 0.8f else 0f

        // ── Pitch — smooth lerp from baseline ────────────────────────────────
        val pitchDelta = lerp(0f, 0.25f, (urgencyStrength * 0.5f + distressStrength * 0.5f) * inten * traj)
        val pitch = (profile.pitch + pitchDelta - fakeStrength * 0.05f).coerceIn(0.5f, 2.0f)

        // ── Speed ─────────────────────────────────────────────────────────────
        val speedUp   = urgencyStrength * 0.35f * inten * traj
        val speedDown = if (signal.trajectory == Trajectory.COLLAPSED) 0.18f else 0f
        val speed = (profile.speed + speedUp - speedDown - fakeStrength * 0.12f).coerceIn(0.5f, 3.0f)

        // ── Breathiness ───────────────────────────────────────────────────────
        val msgBreath = lerp(0f, 0.8f, distressStrength * inten * traj) +
                        lerp(0f, 0.3f, if (signal.emojiSad) inten else 0f)
        val breathIntensity = blendAdd(profile.breathIntensity.toFloat(), profile.breathIntensity > 0, msgBreath * 35f, traj).toInt().coerceIn(0, 100)

        // ── Stutter ───────────────────────────────────────────────────────────
        val msgStutter = lerp(0f, 0.9f, urgencyStrength * inten * traj) +
                         lerp(0f, 0.6f, distressStrength * inten * traj) +
                         lerp(0f, 0.3f, if (signal.register == Register.RAW) inten else 0f)
        val msgStutterClamped = msgStutter.coerceIn(0f, 1f)
        val stutterIntensity  = blendAdd(profile.stutterIntensity.toFloat(), profile.stutterIntensity > 0, msgStutterClamped * 30f, traj).toInt().coerceIn(0, 100)
        val stutterFrequency  = blendAdd(profile.stutterFrequency.toFloat(), profile.stutterFrequency > 0, msgStutterClamped * 25f, traj).toInt().coerceIn(0, 100)

        // ── Intonation ────────────────────────────────────────────────────────
        val msgInton = lerp(0f, 0.8f, (emotionalStrength * 0.7f + inten * 0.3f) * traj) -
                       lerp(0f, 0.4f, fakeStrength)  // flatten fake stakes
        val intonationIntensity = (profile.intonationIntensity + msgInton * 35f)
            .coerceIn(0f, 100f).toInt()
        val intonationVariation = (profile.intonationVariation + msgInton * 0.2f)
            .coerceIn(0f, 1f)

        // ── Jitter — micro-amplitude variation for humanizing ──────────────
        val jitterAmount = (distressStrength * 0.12f + urgencyStrength * 0.04f)
            .coerceIn(0.01f, 0.15f)

        // ── Trailing off — for exhausted/collapsed states ─────────────────
        val shouldTrailOff = signal.trajectory == Trajectory.COLLAPSED

        // ── Volume — contextual gain adjustment ───────────────────────────
        var volumeDelta = 0f
        volumeDelta += urgencyStrength * 0.15f
        volumeDelta -= distressStrength * 0.10f
        if (signal.hourOfDay in 22..23 || signal.hourOfDay in 0..6) volumeDelta -= 0.08f
        if (signal.trajectory == Trajectory.COLLAPSED) volumeDelta -= 0.10f
        val volume = (1.0f + volumeDelta).coerceIn(0.4f, 1.3f)

        return ModulatedVoice(
            pitch               = pitch,
            speed               = speed,
            breathIntensity     = breathIntensity,
            breathCurvePosition = profile.breathCurvePosition,
            breathPause         = profile.breathPause,
            stutterIntensity    = stutterIntensity,
            stutterFrequency    = stutterFrequency,
            stutterPosition     = profile.stutterPosition,
            stutterPause        = profile.stutterPause,
            intonationIntensity = intonationIntensity,
            intonationVariation = intonationVariation,
            jitterAmount        = jitterAmount,
            shouldTrailOff      = shouldTrailOff,
            volume              = volume
        )
    }

    // Linear interpolation
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    // Addition if only one side has signal, multiplication if both resonate
    private fun blendAdd(base: Float, baseHasIt: Boolean, msgSignal: Float, traj: Float): Float {
        return if (baseHasIt && msgSignal > 0f)
            base * (1f + msgSignal / 35f * traj)  // resonance
        else
            base + msgSignal * traj               // addition
    }

    private fun trajectoryMultiplier(t: Trajectory) = when (t) {
        Trajectory.FLAT      -> 1.0f
        Trajectory.BUILDING  -> 1.3f
        Trajectory.PEAKED    -> 1.6f
        Trajectory.COLLAPSED -> 0.7f  // exhausted, not amplified
    }
}
