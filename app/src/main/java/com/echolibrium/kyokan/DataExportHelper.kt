package com.echolibrium.kyokan

import android.content.Context

/**
 * B-10: Simple data export/import for backup and device migration.
 * I-07: Delegates to SettingsRepository for all data access.
 */
object DataExportHelper {

    fun exportAll(ctx: Context): String =
        ctx.container.repo.exportAll().toString(2)

    fun importAll(ctx: Context, json: String): Boolean =
        try {
            ctx.container.repo.importAll(org.json.JSONObject(json))
        } catch (_: Exception) {
            false
        }
}
