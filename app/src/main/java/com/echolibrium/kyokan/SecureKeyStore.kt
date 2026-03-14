package com.echolibrium.kyokan

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for API keys (DeepInfra, etc.).
 * Uses "kyokan_secure_prefs" with AES256-GCM encryption.
 */
object SecureKeyStore {

    private const val PREFS_FILE = "kyokan_secure_prefs"
    private const val KEY_DEEPINFRA = "deepinfra_api_key"

    private fun getSecurePrefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getDeepInfraKey(ctx: Context): String? {
        return getSecurePrefs(ctx).getString(KEY_DEEPINFRA, null)
    }

    fun setDeepInfraKey(ctx: Context, key: String) {
        getSecurePrefs(ctx).edit().putString(KEY_DEEPINFRA, key).apply()
    }
}
