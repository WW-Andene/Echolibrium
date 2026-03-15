package com.echolibrium.kyokan

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Extracted UI builders for voice cards, section headers, and grid layout.
 * Reduces ProfilesFragment LOC and provides reusable card components.
 */
object VoiceCardBuilder {

    fun buildSectionHeader(
        ctx: Context, title: String, subtitle: String, accent: Int,
        onDownloadAll: (() -> Unit)? = null, downloadIcon: String = "⬇ ALL"
    ): View {
        val dp = ctx.resources.displayMetrics.density
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, (14 * dp).toInt(), 0, (10 * dp).toInt()); layoutParams = lp
        }

        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(View(ctx).apply {
            setBackgroundColor(accent)
            layoutParams = LinearLayout.LayoutParams(4, (36 * dp).toInt()).also {
                it.setMargins(0, 0, (10 * dp).toInt(), 0)
            }
        })
        titleRow.addView(TextView(ctx).apply {
            text = title; textSize = 14f; setTextColor(accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (onDownloadAll != null) {
            titleRow.addView(Button(ctx).apply {
                text = downloadIcon; textSize = 10f; setTextColor(accent)
                setBackgroundColor(0xFF181222.toInt())
                setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
                minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                setOnClickListener { onDownloadAll() }
            })
        }
        container.addView(titleRow)

        container.addView(TextView(ctx).apply {
            text = subtitle; textSize = 10f; setTextColor(0xFF7e6e98.toInt())
            setPadding((14 * dp).toInt(), (2 * dp).toInt(), 0, 0)
        })

        return container
    }

    fun buildVoiceCard(
        ctx: Context, name: String, icon: String, iconColor: Int,
        status: String, statusColor: Int,
        voiceId: String, active: Boolean, accent: Int,
        enabled: Boolean, onClick: (() -> Unit)?,
        onPreview: ((voiceId: String, name: String) -> Unit)? = null
    ): View {
        val dp = ctx.resources.displayMetrics.density
        val entry = VoiceRegistry.byId(voiceId)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
            layoutParams = lp

            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10 * dp
                if (active) {
                    setColor(0xFF2a1828.toInt())
                    setStroke((2 * dp).toInt(), accent)
                } else {
                    setColor(if (enabled) 0xFF1a1428.toInt() else 0xFF181222.toInt())
                    setStroke(1, if (enabled) 0xFF2a2040.toInt() else 0xFF201830.toInt())
                }
            }

            contentDescription = "$name voice, ${if (icon == "♀") "female" else if (icon == "♂") "male" else "unknown gender"}, $status${if (active) ", selected" else ""}"
            if (onClick != null) setOnClickListener { onClick() }

            // Circular avatar with gender icon
            val avatarSize = (36 * dp).toInt()
            addView(TextView(ctx).apply {
                text = icon; textSize = 18f; gravity = Gravity.CENTER
                setTextColor(iconColor)
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).also {
                    it.setMargins(0, 0, 0, (6 * dp).toInt())
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (enabled) {
                        iconColor.and(0x00FFFFFF).or(0x1A000000)
                    } else {
                        0xFF1a1428.toInt()
                    })
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })

            // Voice name
            addView(TextView(ctx).apply {
                text = name; textSize = 13f; gravity = Gravity.CENTER
                setTextColor(when {
                    active  -> accent
                    enabled -> 0xFFddd6e8.toInt()
                    else    -> 0xFF8a7a9e.toInt()
                })
                typeface = if (active) android.graphics.Typeface.DEFAULT_BOLD
                           else android.graphics.Typeface.DEFAULT
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            // Nationality / language tag
            val nationality = entry?.nationality ?: ""
            if (nationality.isNotBlank()) {
                addView(TextView(ctx).apply {
                    text = nationality; textSize = 9f; gravity = Gravity.CENTER
                    setTextColor(if (enabled) 0xFF8a7a9e.toInt() else 0xFF7e6e98.toInt())
                    setPadding(0, (1 * dp).toInt(), 0, (3 * dp).toInt())
                })
            }

            // Status line
            addView(TextView(ctx).apply {
                text = status; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(statusColor)
                setPadding(0, (2 * dp).toInt(), 0, 0)
            })

            // Active accent bar
            if (active) {
                addView(View(ctx).apply {
                    setBackgroundColor(accent)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (2 * dp).toInt()
                    ).also { it.setMargins((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), 0) }
                })
            }

            // Inline preview button
            if (enabled && onPreview != null) {
                addView(TextView(ctx).apply {
                    text = "▶ preview"
                    textSize = 9f
                    gravity = Gravity.CENTER
                    setTextColor(accent.and(0x00FFFFFF.toInt()).or(0x99000000.toInt()))
                    setPadding(0, (4 * dp).toInt(), 0, 0)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Preview $name voice"
                    setOnClickListener { onPreview(voiceId, name) }
                })
            }
        }
    }

    fun addVoiceRows(target: LinearLayout, cards: List<View>, columns: Int = 3) {
        val ctx = target.context
        cards.chunked(columns).forEach { rowCards ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowCards.forEach { row.addView(it) }
            repeat(columns - rowCards.size) {
                row.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            target.addView(row)
        }
    }

    fun emptyLabel(ctx: Context, msg: String): View {
        return TextView(ctx).apply {
            text = msg; setTextColor(0xFF9b7eb8.toInt()); textSize = 12f
            setPadding(6, 8, 0, 8)
        }
    }
}
