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
    val intonationVariation: Float
)

object VoiceModulator {

    fun modulate(profile: VoiceProfile, signal: SignalMap): ModulatedVoice {

        val traj = trajectoryMultiplier(signal.trajectory)
        val intensity = signal.intensityLevel

        // ── Pitch ─────────────────────────────────────────────────────────────
        val pitch = when {
            signal.urgencyType == UrgencyType.EXPIRING ->
                add(profile.pitch, 0.15f * intensity * traj)
            signal.emojiAngry || signal.register == Register.DRAMATIC ->
                add(profile.pitch, 0.1f * intensity * traj)
            signal.warmth == WarmthLevel.DISTRESSED ->
                add(profile.pitch, 0.2f * intensity * traj) // voice rises under distress
            signal.stakesType == StakesType.FAKE ->
                profile.pitch * 0.95f // slightly flatten for fake stakes
            else -> profile.pitch
        }.coerceIn(0.5f, 2.0f)

        // ── Speed ─────────────────────────────────────────────────────────────
        val speed = when {
            signal.urgencyType == UrgencyType.EXPIRING ->
                multiply(profile.speed, intensity * traj * 0.4f) // rush
            signal.urgencyType == UrgencyType.REAL ->
                multiply(profile.speed, intensity * traj * 0.3f)
            signal.trajectory == Trajectory.COLLAPSED ->
                profile.speed * 0.85f  // collapsed = slow, heavy
            signal.stakesType == StakesType.FAKE ->
                profile.speed * 0.9f   // game notifs, slightly slower/duller
            else -> profile.speed
        }.coerceIn(0.5f, 3.0f)

        // ── Breathiness ───────────────────────────────────────────────────────
        val messageBreathe = when {
            signal.warmth == WarmthLevel.DISTRESSED -> 0.7f
            signal.trajectory == Trajectory.COLLAPSED -> 0.5f
            signal.emojiSad -> 0.4f
            signal.register == Register.RAW -> 0.3f
            else -> 0f
        }
        val breathIntensity = when {
            profile.breathIntensity > 0 && messageBreathe > 0 ->
                // both have it → multiply
                (profile.breathIntensity * (1f + messageBreathe * traj)).toInt()
            messageBreathe > 0 ->
                // only message has it → add
                (profile.breathIntensity + messageBreathe * 30f * traj).toInt()
            else -> profile.breathIntensity
        }.coerceIn(0, 100)

        // ── Stutter ───────────────────────────────────────────────────────────
        val messageStutter = when {
            signal.urgencyType == UrgencyType.EXPIRING -> 0.8f
            signal.trajectory == Trajectory.BUILDING && signal.intensityLevel > 0.5f -> 0.6f
            signal.warmth == WarmthLevel.DISTRESSED -> 0.5f
            signal.register == Register.RAW -> 0.3f
            else -> 0f
        }
        val stutterIntensity = when {
            profile.stutterIntensity > 0 && messageStutter > 0 ->
                // both → multiply
                (profile.stutterIntensity * (1f + messageStutter * traj)).toInt()
            messageStutter > 0 ->
                // only message → add
                (profile.stutterIntensity + messageStutter * 25f * traj).toInt()
            else -> profile.stutterIntensity
        }.coerceIn(0, 100)

        val stutterFrequency = when {
            profile.stutterFrequency > 0 && messageStutter > 0 ->
                (profile.stutterFrequency * (1f + messageStutter * traj)).toInt()
            messageStutter > 0 ->
                (profile.stutterFrequency + messageStutter * 20f * traj).toInt()
            else -> profile.stutterFrequency
        }.coerceIn(0, 100)

        // ── Intonation ────────────────────────────────────────────────────────
        val messageIntonation = when {
            signal.trajectory == Trajectory.BUILDING -> 0.6f
            signal.trajectory == Trajectory.PEAKED -> 0.8f
            signal.trajectory == Trajectory.COLLAPSED -> 0.3f
            signal.stakesType == StakesType.EMOTIONAL -> 0.5f
            signal.stakesType == StakesType.FAKE -> -0.3f // flatten fake stakes
            signal.register == Register.FORMAL -> -0.2f  // flatten formal
            else -> 0f
        }
        val intonationIntensity = when {
            profile.intonationIntensity > 0 && messageIntonation > 0 ->
                (profile.intonationIntensity * (1f + messageIntonation * traj)).toInt()
            messageIntonation > 0 ->
                (profile.intonationIntensity + messageIntonation * 30f * traj).toInt()
            messageIntonation < 0 ->
                (profile.intonationIntensity * (1f + messageIntonation)).toInt()
            else -> profile.intonationIntensity
        }.coerceIn(0, 100)

        val intonationVariation = when {
            signal.trajectory == Trajectory.PEAKED ->
                (profile.intonationVariation + 0.2f * traj).coerceIn(0f, 1f)
            signal.stakesType == StakesType.FAKE || signal.register == Register.FORMAL ->
                (profile.intonationVariation * 0.6f).coerceIn(0f, 1f)
            else -> profile.intonationVariation
        }

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
            intonationVariation = intonationVariation
        )
    }

    private fun trajectoryMultiplier(t: Trajectory) = when (t) {
        Trajectory.FLAT      -> 1.0f
        Trajectory.BUILDING  -> 1.3f
        Trajectory.PEAKED    -> 1.6f
        Trajectory.COLLAPSED -> 0.7f  // exhausted, not amplified
    }

    // Addition: only one side has intensity
    private fun add(base: Float, delta: Float) = base + delta

    // Multiplication: both sides have it — resonance
    private fun multiply(base: Float, factor: Float) = base * (1f + factor)
}
