package com.echolibrium.kyokan

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * WaveformView — Kyōkan's visual signature element.
 *
 * Art Direction Brief §SIGNATURE: "The waveform IS Kyōkan's visual identity.
 * Remove it and the product loses its soul."
 *
 * Renders animated vertical bars shaped by a bell-curve envelope with two
 * overlapping sine waves at different frequencies, creating organic breathing.
 *
 * Usage in XML:
 *   <com.echolibrium.kyokan.WaveformView
 *       android:layout_width="match_parent"
 *       android:layout_height="60dp"
 *       app:waveform_barCount="24"
 *       app:waveform_active="true" />
 *
 * Usage in Kotlin:
 *   waveformView.isActive = true
 *   waveformView.hue = 345f  // rose-magenta accent
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Number of vertical bars */
    var barCount: Int = 24
        set(value) { field = value; invalidate() }

    /** Whether the waveform is animated (listening) or flatlined (paused) */
    var isActive: Boolean = true
        set(value) {
            field = value
            if (value) startAnimation() else stopAnimation()
            invalidate()
        }

    /** HSV hue for the bar gradient (0-360). Default: 345 = rose-magenta from Brief */
    var hue: Float = 345f
        set(value) { field = value; updateColors(); invalidate() }

    /** Secondary hue for gradient top. Default: hue + 15 */
    var hueEnd: Float = 360f
        set(value) { field = value; updateColors(); invalidate() }

    // ── Internal ──

    private var time = 0f
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRadiusPx = 3f * resources.displayMetrics.density
    private val minBarH = 2f * resources.displayMetrics.density

    private var colorStart = Color.HSVToColor(200, floatArrayOf(345f, 0.65f, 0.65f))
    private var colorEnd = Color.HSVToColor(100, floatArrayOf(360f, 0.50f, 0.48f))
    private var glowColor = Color.HSVToColor(50, floatArrayOf(345f, 0.60f, 0.55f))

    private var animator: ValueAnimator? = null

    init {
        // Hardware acceleration for smooth animation
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updateColors()
    }

    private fun updateColors() {
        colorStart = Color.HSVToColor(216, floatArrayOf(hue % 360, 0.65f, 0.65f))
        colorEnd = Color.HSVToColor(102, floatArrayOf(hueEnd % 360, 0.50f, 0.48f))
        glowColor = Color.HSVToColor(50, floatArrayOf(hue % 360, 0.60f, 0.55f))
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L // ~60fps tick
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                time += 0.06f
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isActive) startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        val barW = 3.5f * density
        val gap = 4.5f * density
        val totalW = barCount * barW + (barCount - 1) * gap
        val startX = (w - totalW) / 2f
        val center = barCount / 2f
        val maxBarH = h * 0.85f

        for (i in 0 until barCount) {
            val dist = abs(i - center) / center
            val envelope = 1f - dist * 0.75f

            val barH = if (isActive) {
                maxBarH * envelope *
                    (0.3f + 0.7f * abs(sin(time * 0.06f + i * 0.42f).toFloat())) *
                    (0.55f + 0.45f * abs(sin(time * 0.035f + i * 0.75f).toFloat()))
            } else {
                minBarH
            }

            val x = startX + i * (barW + gap)
            val cy = h / 2f
            val top = cy - barH / 2f
            val bottom = cy + barH / 2f

            // Bar gradient: accent bottom → faded top
            barPaint.shader = LinearGradient(
                x, bottom, x, top,
                colorStart, colorEnd,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(x, top, x + barW, bottom, barRadiusPx, barRadiusPx, barPaint)

            // Soft glow on tall bars
            if (isActive && barH > maxBarH * 0.35f) {
                glowPaint.color = glowColor
                glowPaint.maskFilter = BlurMaskFilter(6f * density, BlurMaskFilter.Blur.NORMAL)
                canvas.drawRoundRect(x, top, x + barW, bottom, barRadiusPx, barRadiusPx, glowPaint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val defaultW = (barCount * 3.5f * density + (barCount - 1) * 4.5f * density + 24 * density).toInt()
        val defaultH = (60 * density).toInt()

        val w = resolveSize(defaultW, widthMeasureSpec)
        val h = resolveSize(defaultH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }
}
