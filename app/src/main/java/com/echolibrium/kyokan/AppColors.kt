package com.echolibrium.kyokan

import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Theme-aware color resolver for all programmatic view building.
 *
 * Every color used in Kotlin code goes through here, ensuring light/dark theme
 * consistency. Resolves from @color/ resources which have values/ and values-night/ variants.
 *
 * Usage: `AppColors.primary(ctx)` instead of `0xFF7844a0.toInt()`
 */
object AppColors {

    // ── Core brand ──────────────────────────────────────────────────────────
    fun primary(ctx: Context)      = ContextCompat.getColor(ctx, R.color.primary)
    fun primaryDark(ctx: Context)  = ContextCompat.getColor(ctx, R.color.primary_dark)
    fun bg(ctx: Context)           = ContextCompat.getColor(ctx, R.color.bg)
    fun surface(ctx: Context)      = ContextCompat.getColor(ctx, R.color.surface)
    fun surfaceElevated(ctx: Context) = ContextCompat.getColor(ctx, R.color.surface_elevated)
    fun navInactive(ctx: Context)  = ContextCompat.getColor(ctx, R.color.nav_inactive)

    // ── Text ────────────────────────────────────────────────────────────────
    fun textPrimary(ctx: Context)      = ContextCompat.getColor(ctx, R.color.text_primary)
    fun textBright(ctx: Context)       = ContextCompat.getColor(ctx, R.color.text_bright)
    fun textSecondary(ctx: Context)    = ContextCompat.getColor(ctx, R.color.text_secondary)
    fun textHint(ctx: Context)         = ContextCompat.getColor(ctx, R.color.text_hint)
    fun textMuted(ctx: Context)        = ContextCompat.getColor(ctx, R.color.text_muted)
    fun textDimmed(ctx: Context)       = ContextCompat.getColor(ctx, R.color.text_dimmed)
    fun textSection(ctx: Context)      = ContextCompat.getColor(ctx, R.color.text_section)
    fun textSectionLabel(ctx: Context) = ContextCompat.getColor(ctx, R.color.text_section_label)
    fun textDisabled(ctx: Context)     = ContextCompat.getColor(ctx, R.color.text_disabled)
    fun textOnAccent(ctx: Context)     = ContextCompat.getColor(ctx, R.color.text_on_accent)
    fun textSubtitle(ctx: Context)     = ContextCompat.getColor(ctx, R.color.text_subtitle)
    fun textCardSubtitle(ctx: Context) = ContextCompat.getColor(ctx, R.color.text_card_subtitle)

    // ── Engine accents ──────────────────────────────────────────────────────
    fun engineOrpheus(ctx: Context) = ContextCompat.getColor(ctx, R.color.engine_orpheus)
    fun engineKokoro(ctx: Context)  = ContextCompat.getColor(ctx, R.color.engine_kokoro)
    fun enginePiper(ctx: Context)   = ContextCompat.getColor(ctx, R.color.engine_piper)
    fun cloudStatus(ctx: Context)   = ContextCompat.getColor(ctx, R.color.cloud_status)

    // ── Functional accents ──────────────────────────────────────────────────
    fun accentRed(ctx: Context)  = ContextCompat.getColor(ctx, R.color.accent_red)
    fun accentDnd(ctx: Context)  = ContextCompat.getColor(ctx, R.color.accent_dnd)
    fun accentSave(ctx: Context) = ContextCompat.getColor(ctx, R.color.accent_save)
    fun accentRose(ctx: Context) = ContextCompat.getColor(ctx, R.color.accent_rose)

    // ── Gender ──────────────────────────────────────────────────────────────
    fun genderFemale(ctx: Context)    = ContextCompat.getColor(ctx, R.color.gender_female)
    fun genderMale(ctx: Context)      = ContextCompat.getColor(ctx, R.color.gender_male)
    fun genderNeutral(ctx: Context)   = ContextCompat.getColor(ctx, R.color.gender_neutral)
    fun genderFemaleDim(ctx: Context) = ContextCompat.getColor(ctx, R.color.gender_female_dim)
    fun genderMaleDim(ctx: Context)   = ContextCompat.getColor(ctx, R.color.gender_male_dim)

    /** Resolve gender color by name, with enabled/dimmed variant */
    fun genderColor(ctx: Context, gender: String, enabled: Boolean = true): Int = when (gender) {
        "Female" -> if (enabled) genderFemale(ctx) else genderFemaleDim(ctx)
        "Male"   -> if (enabled) genderMale(ctx) else genderMaleDim(ctx)
        else     -> if (enabled) genderNeutral(ctx) else textDisabled(ctx)
    }

    // ── Status ──────────────────────────────────────────────────────────────
    fun statusReady(ctx: Context) = ContextCompat.getColor(ctx, R.color.status_ready)
    fun statusError(ctx: Context) = ContextCompat.getColor(ctx, R.color.status_error)

    // ── Backgrounds ─────────────────────────────────────────────────────────
    fun btnPrimaryBg(ctx: Context)  = ContextCompat.getColor(ctx, R.color.btn_primary_bg)
    fun btnRedBg(ctx: Context)      = ContextCompat.getColor(ctx, R.color.btn_red_bg)
    fun btnBlueBg(ctx: Context)     = ContextCompat.getColor(ctx, R.color.btn_blue_bg)
    fun cardActiveBg(ctx: Context)  = ContextCompat.getColor(ctx, R.color.card_active_bg)
    fun cardEnabledBg(ctx: Context) = ContextCompat.getColor(ctx, R.color.card_enabled_bg)
    fun cardDisabledBg(ctx: Context) = ContextCompat.getColor(ctx, R.color.card_disabled_bg)
    fun cardBorder(ctx: Context)    = ContextCompat.getColor(ctx, R.color.card_border)
    fun filterActiveBg(ctx: Context) = ContextCompat.getColor(ctx, R.color.filter_active_bg)

    // ── Divider & Input ─────────────────────────────────────────────────────
    fun divider(ctx: Context)  = ContextCompat.getColor(ctx, R.color.divider)
    fun rowBg(ctx: Context)    = ContextCompat.getColor(ctx, R.color.row_bg)
    fun inputBg(ctx: Context)  = ContextCompat.getColor(ctx, R.color.input_bg)
}
