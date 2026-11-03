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

    fun migrateApiKeys(c: Context) {
        try {
            val preferences = sp(c)
            if (!preferences.getBoolean("openrouter_key_migrated", false)) {
                preferences.edit()
                    .remove("api_key")
                    .remove("gemini_api_key")
                    .putBoolean("openrouter_key_migrated", true)
                    .apply()
            }
        } catch (_: Exception) {}
    }

    fun getOpenRouterKey(c: Context): String =
        try { sp(c).getString("openrouter_api_key", "") ?: "" } catch (e: Exception) { "" }

    fun setOpenRouterKey(c: Context, key: String) {
        try { sp(c).edit().putString("openrouter_api_key", key).apply() } catch (_: Exception) {}
    }

    fun getFacts(c: Context): List<String> = try {
        val arr = JSONArray(sp(c).getString("facts", "[]"))
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun setFacts(c: Context, facts: List<String>) {
        try { sp(c).edit().putString("facts", JSONArray(facts).toString()).apply() } catch (_: Exception) {}
    }

    fun getConversation(c: Context): List<Msg> = try {
        val arr = JSONArray(sp(c).getString("conversation", "[]"))
        List(arr.length()) { index ->
            val item = arr.getJSONObject(index)
            Msg(item.getString("role"), item.getString("text"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun setConversation(c: Context, messages: List<Msg>) {
        val arr = JSONArray()
        messages.forEach { arr.put(org.json.JSONObject().put("role", it.role).put("text", it.text)) }
        try { sp(c).edit().putString("conversation", arr.toString()).apply() } catch (_: Exception) {}
    }
}
