package com.echolibrium.kyokan

import android.util.Log

/**
 * Context object carried through the DSP chain per utterance.
 * Each node can read and contribute to the context.
 */
data class UtteranceContext(
    val modulated: ModulatedVoice,
    val signal: SignalMap,
    val landmarks: PhonicLandmarks? = null,
    val sampleRate: Int = 24000
)

/**
 * Single processing stage in the audio DSP pipeline.
 * Implementations should be stateless — all state flows through UtteranceContext.
 */
interface DspNode {
    val name: String
    var enabled: Boolean

    /**
     * Process PCM samples in-place or return a new array.
     * Return the (possibly new) samples array.
     */
    fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray
}

/**
 * Ordered chain of DspNodes. Replaces the hardcoded sequence in AudioDsp.apply().
 *
 * Benefits over the monolithic approach:
 *   - Reorder nodes per-profile
 *   - Disable individual stages at runtime
 *   - Log per-node timing for profiling
 *   - Insert custom nodes without modifying AudioDsp
 *
 * Thread safety: DspChain itself is not thread-safe.
 * AudioPipeline processes one item at a time on a single daemon thread,
 * so concurrent access is not expected.
 */
class DspChain {
    private val nodes = mutableListOf<DspNode>()

    fun add(node: DspNode): DspChain { nodes.add(node); return this }
    fun addAll(vararg nodeList: DspNode): DspChain { nodes.addAll(nodeList); return this }
    fun remove(name: String) { nodes.removeAll { it.name == name } }
    fun clear() { nodes.clear() }
    fun find(name: String): DspNode? = nodes.find { it.name == name }

    /** Move a node to a new position (0-indexed). */
    fun reorder(name: String, newIndex: Int) {
        val node = nodes.find { it.name == name } ?: return
        nodes.remove(node)
        nodes.add(newIndex.coerceIn(0, nodes.size), node)
    }

    /** Process samples through all enabled nodes in order. */
    fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        var pcm = samples
        for (node in nodes) {
            if (!node.enabled) continue
            pcm = try {
                node.process(pcm, ctx)
            } catch (e: Exception) {
                Log.w("DspChain", "Node '${node.name}' failed, skipping", e)
                pcm
            }
        }
        return pcm
    }

    /** List node names and their enabled state (for debugging/UI). */
    fun describe(): List<Pair<String, Boolean>> = nodes.map { it.name to it.enabled }

    companion object {
        /**
         * Build the default chain matching the current AudioDsp.apply() order.
         * Each node delegates to the existing AudioDsp static methods.
         */
        fun default(): DspChain = DspChain().addAll(
            VolumeNode(),
            SaturationNode(),
            FormantSmoothingNode(),
            BreathinessNode(),
            SpectralTiltNode(),
            JitterNode(),
            VocalFryNode(),
            TrailingOffNode(),
            SoftLimiterNode()
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Built-in DSP nodes — thin wrappers around existing AudioDsp methods
// ═══════════════════════════════════════════════════════════════════

class VolumeNode : DspNode {
    override val name = "volume"
    override var enabled = true
    override fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        val vol = ctx.modulated.volume
        if (vol != 1.0f) for (i in samples.indices) samples[i] *= vol
        return samples
    }
}

class SaturationNode : DspNode {
    override val name = "saturation"
    override var enabled = true
    override fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        val drive = ctx.modulated.volume
        if (drive > 1.05f) {
            val g = 1f + (drive - 1f) * 0.5f
            for (i in samples.indices) samples[i] = kotlin.math.tanh(samples[i] * g).toFloat()
        }
        return samples
    }
}

class FormantSmoothingNode : DspNode {
    override val name = "formant_smoothing"
    override var enabled = true
    override fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        val landmarks = ctx.landmarks ?: return samples
        if (landmarks.dynamicRange < 0.15f) return samples
        val alpha = (0.02f + landmarks.dynamicRange * 0.03f).coerceIn(0.01f, 0.08f)
        var prev = samples[0]
        for (i in 1 until samples.size) {
            samples[i] = prev + alpha * (samples[i] - prev)
            prev = samples[i]
        }
        return samples
    }
}

class BreathinessNode : DspNode {
    override val name = "breathiness"
    override var enabled = true
    override fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        if (ctx.modulated.breathIntensity < 5) return samples
        AudioDsp.applyBreathinessPublic(samples, ctx.sampleRate, ctx.modulated.breathIntensity, ctx.landmarks)
        return samples
    }
}

class SpectralTiltNode : DspNode {
    override val name = "spectral_tilt"
    override var enabled = true
    override fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        if (ctx.modulated.breathIntensity < 10) return samples
        AudioDsp.applySpectralTiltPublic(samples, ctx.sampleRate, ctx.modulated.breathIntensity / 100f)
        return samples
    }
}

class JitterNode : DspNode {
    override val name = "jitter"
    override var enabled = true
    override fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        if (ctx.modulated.jitterAmount < 0.01f) return samples
        AudioDsp.applyJitterPublic(samples, ctx.sampleRate, ctx.modulated.jitterAmount, ctx.landmarks)
        return samples
    }
}

class VocalFryNode : DspNode {
    override val name = "vocal_fry"
    override var enabled = true
    override fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        val landmarks = ctx.landmarks ?: return samples
        AudioDsp.applyVocalFryPublic(samples, ctx.sampleRate, landmarks)
        return samples
    }
}

class TrailingOffNode : DspNode {
    override val name = "trailing_off"
    override var enabled = true
    override fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        if (!ctx.modulated.shouldTrailOff) return samples
        AudioDsp.applyTrailingOffPublic(samples)
        return samples
    }
}

class SoftLimiterNode : DspNode {
    override val name = "soft_limiter"
    override var enabled = true
    override fun process(samples: FloatArray, ctx: UtteranceContext): FloatArray {
        AudioDsp.applySoftLimiterPublic(samples)
        return samples
    }
}
