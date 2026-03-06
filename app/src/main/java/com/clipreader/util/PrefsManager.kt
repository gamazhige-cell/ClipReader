package com.clipreader.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PrefsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePrimaryEngine(engine: String) {
        sharedPreferences.edit().putString("primary_engine", engine).apply()
    }

    fun getPrimaryEngine(): String? {
        return sharedPreferences.getString("primary_engine", "Microsoft Azure TTS")
    }

    fun saveFallbackEngine(engine: String) {
        sharedPreferences.edit().putString("fallback_engine", engine).apply()
    }

    fun getFallbackEngine(): String? {
        return sharedPreferences.getString("fallback_engine", "System TTS (本机离线)")
    }

    fun saveAzureKey(key: String) {
        sharedPreferences.edit().putString("azure_api_key", key).apply()
    }

    fun getAzureKey(): String? {
        return sharedPreferences.getString("azure_api_key", "")
    }

    fun saveAzureRegion(region: String) {
        sharedPreferences.edit().putString("azure_region", region).apply()
    }

    fun getAzureRegion(): String? {
        return sharedPreferences.getString("azure_region", "eastasia")
    }
}
