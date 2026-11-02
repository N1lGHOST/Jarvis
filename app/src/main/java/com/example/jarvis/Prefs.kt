package com.example.jarvis

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

/** Clé API et mémoire de Jarvis, chiffrées sur le téléphone. */
object Prefs {
    @Volatile private var cached: android.content.SharedPreferences? = null

    private fun sp(c: Context): android.content.SharedPreferences =
        cached ?: synchronized(this) {
            cached ?: EncryptedSharedPreferences.create(
                c.applicationContext,
                "jarvis_secure",
                MasterKey.Builder(c.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { cached = it }
        }

    fun getKey(c: Context): String =
        try { sp(c).getString("api_key", "") ?: "" } catch (e: Exception) { "" }

    fun setKey(c: Context, key: String) {
        try { sp(c).edit().putString("api_key", key).apply() } catch (_: Exception) {}
    }

    fun getRepo(c: Context): String =
        try { sp(c).getString("repo", "") ?: "" } catch (e: Exception) { "" }

    fun setRepo(c: Context, v: String) {
        try { sp(c).edit().putString("repo", v).apply() } catch (_: Exception) {}
    }

    fun getAllowConfirm(c: Context): Boolean =
        try { sp(c).getBoolean("allow_confirm", false) } catch (e: Exception) { false }

    fun setAllowConfirm(c: Context, v: Boolean) {
        try { sp(c).edit().putBoolean("allow_confirm", v).apply() } catch (_: Exception) {}
    }

    fun getFacts(c: Context): List<String> = try {
        val arr = JSONArray(sp(c).getString("facts", "[]"))
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun setFacts(c: Context, facts: List<String>) {
        try { sp(c).edit().putString("facts", JSONArray(facts).toString()).apply() } catch (_: Exception) {}
    }
}
