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

    fun getGeminiKey(c: Context): String =
        try { sp(c).getString("gemini_api_key", "") ?: "" } catch (e: Exception) { "" }

    fun setGeminiKey(c: Context, key: String) {
        try { sp(c).edit().putString("gemini_api_key", key).apply() } catch (_: Exception) {}
    }

    fun getAiProvider(c: Context): String =
        try { sp(c).getString("ai_provider", "openrouter") ?: "openrouter" }
        catch (e: Exception) { "openrouter" }

    fun setAiProvider(c: Context, provider: String) {
        if (provider !in setOf("gemini", "openrouter")) return
        try { sp(c).edit().putString("ai_provider", provider).apply() } catch (_: Exception) {}
    }

    fun isWakeWordEnabled(c: Context): Boolean =
        try { sp(c).getBoolean("wake_word_enabled", false) } catch (_: Exception) { false }

    fun setWakeWordEnabled(c: Context, enabled: Boolean) {
        try { sp(c).edit().putBoolean("wake_word_enabled", enabled).apply() } catch (_: Exception) {}
    }

    fun hasWakeWordDisclosureConsent(c: Context): Boolean =
        try { sp(c).getBoolean("wake_word_disclosure_consent", false) } catch (_: Exception) { false }

    fun setWakeWordDisclosureConsent(c: Context) {
        try { sp(c).edit().putBoolean("wake_word_disclosure_consent", true).apply() } catch (_: Exception) {}
    }

    fun getSpeechPitch(c: Context): Float =
        try { sp(c).getFloat("speech_pitch", 0.8f).coerceIn(0.7f, 1.3f) } catch (_: Exception) { 0.8f }

    fun setSpeechPitch(c: Context, pitch: Float) {
        try { sp(c).edit().putFloat("speech_pitch", pitch.coerceIn(0.7f, 1.3f)).apply() } catch (_: Exception) {}
    }

    fun isSpeechMuted(c: Context): Boolean =
        try { sp(c).getBoolean("speech_muted", false) } catch (_: Exception) { false }

    fun setSpeechMuted(c: Context, muted: Boolean) {
        try { sp(c).edit().putBoolean("speech_muted", muted).apply() } catch (_: Exception) {}
    }

    fun getSpeechProfile(c: Context): String =
        try { sp(c).getString("speech_profile", "man") ?: "man" } catch (_: Exception) { "man" }

    fun setSpeechProfile(c: Context, profile: String) {
        try { sp(c).edit().putString("speech_profile", profile).apply() } catch (_: Exception) {}
    }

    fun getTtsVoiceName(c: Context): String =
        try { sp(c).getString("tts_voice_name", "") ?: "" } catch (_: Exception) { "" }

    fun setTtsVoiceName(c: Context, name: String) {
        try { sp(c).edit().putString("tts_voice_name", name).apply() } catch (_: Exception) {}
    }

    fun getFacts(c: Context): List<String> = try {
        val arr = JSONArray(sp(c).getString("facts", "[]"))
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun setFacts(c: Context, facts: List<String>) {
        try { sp(c).edit().putString("facts", JSONArray(facts).toString()).apply() } catch (_: Exception) {}
    }

    fun getInventory(c: Context): List<StockItem> = try {
        val arr = JSONArray(sp(c).getString("inventory", "[]"))
        List(arr.length()) { index ->
            val item = arr.getJSONObject(index)
            StockItem(item.getString("name"), item.getInt("quantity"))
        }.filter { it.name.isNotBlank() && it.quantity > 0 }
    } catch (e: Exception) {
        emptyList()
    }

    fun setInventory(c: Context, items: List<StockItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            if (item.name.isNotBlank() && item.quantity > 0) {
                arr.put(org.json.JSONObject().put("name", item.name).put("quantity", item.quantity))
            }
        }
        sp(c).edit().putString("inventory", arr.toString()).apply()
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
