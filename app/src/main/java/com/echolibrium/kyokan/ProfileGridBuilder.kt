package com.echolibrium.kyokan

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Builds the profile card grid UI.
 *
 * Extracted from ProfilesFragment (Phase 3.3) to reduce God Fragment size.
 * Stateless object — all state comes from parameters.
 */
object ProfileGridBuilder {

    interface Callbacks {
        fun onProfileSelected(profile: VoiceProfile)
        fun onProfileRenamed(profileId: String, newName: String)
    }

    fun renderGrid(
        target: LinearLayout,
        profiles: List<VoiceProfile>,
        activeProfileId: String,
        callbacks: Callbacks
    ) {
        val ctx = target.context
        target.removeAllViews()
        val columns = 3
        var row: LinearLayout? = null

        profiles.forEachIndexed { index, p ->
            if (index % columns == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = 8 }
                }
                target.addView(row)
            }

            val isActive = p.id == activeProfileId
            row!!.addView(buildCard(ctx, p, isActive, callbacks))
        }

        val remainder = profiles.size % columns
        if (remainder != 0) {
            for (i in remainder until columns) {
                row!!.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
            }
        }
    }

    private fun buildCard(
        ctx: Context, p: VoiceProfile, isActive: Boolean, callbacks: Callbacks
    ): View {
        val dp = ctx.resources.displayMetrics.density
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPaddingRelative((8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8 * dp
                if (isActive) {
                    setColor(AppColors.cardActiveBg(ctx))
                    setStroke((2 * dp).toInt(), AppColors.accentRose(ctx))
                } else {
                    setColor(AppColors.cardEnabledBg(ctx))
                    setStroke(1, AppColors.cardBorder(ctx))
                }
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = (4 * dp).toInt(); it.marginEnd = (4 * dp).toInt()
            }

            val voiceEntry = VoiceRegistry.byId(p.voiceName)
            contentDescription = "Profile ${p.name}${if (voiceEntry != null) ", voice ${voiceEntry.displayName}" else ""}${if (isActive) ", active" else ""}. Long press to rename."

            // Ripple touch feedback
            val rippleAttr = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, rippleAttr, true)
            foreground = ctx.getDrawable(rippleAttr.resourceId)

            setOnClickListener { callbacks.onProfileSelected(p) }
            setOnLongClickListener { showRenameDialog(ctx, p, callbacks); true }
        }

        // Emoji
        card.addView(TextView(ctx).apply {
            text = p.emoji; textSize = 24f; gravity = Gravity.CENTER
        })

        // Name
        card.addView(TextView(ctx).apply {
            text = p.name; textSize = 11f
            setTextColor(if (isActive) AppColors.accentRose(ctx) else AppColors.textSecondary(ctx))
            gravity = Gravity.CENTER; maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        // Voice name
        val voiceEntry = VoiceRegistry.byId(p.voiceName)
        if (voiceEntry != null) {
            card.addView(TextView(ctx).apply {
                text = voiceEntry.displayName; textSize = 9f
                setTextColor(if (isActive) AppColors.textSection(ctx) else AppColors.textDisabled(ctx))
                gravity = Gravity.CENTER; maxLines = 1
            })
        }

        // Personality hint (M13)
        val hint = personalityHint(p.pitch, p.speed)
        if (hint.isNotEmpty()) {
            card.addView(TextView(ctx).apply {
                text = hint; textSize = 8f
                setTextColor(if (isActive) AppColors.textDimmed(ctx) else AppColors.textCardSubtitle(ctx))
                gravity = Gravity.CENTER; maxLines = 1
                setPadding(0, (2 * dp).toInt(), 0, 0)
            })
        }

        // Active indicator bar
        if (isActive) {
            card.addView(View(ctx).apply {
                setBackgroundColor(AppColors.accentRose(ctx))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (2 * dp).toInt()
                ).also { it.marginStart = (12 * dp).toInt(); it.topMargin = (8 * dp).toInt(); it.marginEnd = (12 * dp).toInt() }
            })
        }

        return card
    }

    private fun personalityHint(pitch: Float, speed: Float): String {
        val pitchDesc = when {
            pitch >= 1.5f -> "high"
            pitch <= 0.7f -> "deep"
            else -> null
        }
        val speedDesc = when {
            speed >= 1.8f -> "fast"
            speed <= 0.7f -> "slow"
            else -> null
        }
        return listOfNotNull(pitchDesc, speedDesc).joinToString(" · ")
    }

    private fun showRenameDialog(ctx: Context, p: VoiceProfile, callbacks: Callbacks) {
        val et = EditText(ctx).apply {
            setText(p.name)
            hint = ctx.getString(R.string.profile_name_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(40))
            selectAll()
        }
        AlertDialog.Builder(ctx)
            .setTitle(ctx.getString(R.string.rename_profile))
            .setView(et)
            .setPositiveButton(ctx.getString(R.string.save)) { _, _ ->
                val newName = et.text.toString().trim().ifBlank { p.name }
                callbacks.onProfileRenamed(p.id, newName)
            }
            .setNegativeButton(ctx.getString(R.string.cancel), null)
            .show()
    }
}
