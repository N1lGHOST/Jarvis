package com.example.jarvis

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Conversation conservée entre les ouvertures de l'application. */
object Convo {
    val messages = mutableStateListOf<Msg>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun restore(ctx: Context) {
        if (messages.isEmpty()) messages.addAll(Prefs.getConversation(ctx.applicationContext))
    }

    fun clear(ctx: Context) {
        messages.clear()
        Prefs.setConversation(ctx.applicationContext, emptyList())
        error = null
    }

    /** Renvoie le texte à dire à voix haute. */
    suspend fun ask(ctx: Context, apiKey: String, text: String): String {
        val app = ctx.applicationContext
        messages.add(Msg("user", text))
        persist(app)
        error = null
        handleMemoryRequest(app, text)?.let { answer ->
            messages.add(Msg("assistant", answer))
            persist(app)
            return answer
        }
        loading = true
        val history = messages.takeLast(20).dropWhile { it.role != "user" }
        return try {
            val answer = withContext(Dispatchers.IO) {
                OpenRouterClient.ask(apiKey, history, Prefs.getFacts(app))
            }
            messages.add(Msg("assistant", answer))
            persist(app)
            answer
        } catch (e: IOException) {
            error = e.message ?: "Pas de connexion internet"
            "Je n'ai pas de connexion internet."
        } catch (e: Exception) {
            error = e.message ?: "Erreur inconnue"
            "Désolé, une erreur est survenue."
        } finally {
            loading = false
        }
    }

    private fun persist(ctx: Context) {
        Prefs.setConversation(ctx, messages.takeLast(40))
    }

    private fun handleMemoryRequest(ctx: Context, text: String): String? {
        val trimmed = text.trim()
        val remember = Regex(
            "(?i)^(?:souviens-toi que|souviens-toi|mémorise que|mémorise|memorise que|memorise|retiens)\\s+(.+)$"
        ).matchEntire(trimmed)
        if (remember != null) {
            val fact = remember.groupValues[1].trim()
            if (fact.isBlank()) return null
            val facts = Prefs.getFacts(ctx).toMutableList()
            if (facts.none { it.equals(fact, ignoreCase = true) }) facts.add(fact)
            Prefs.setFacts(ctx, facts.takeLast(50))
            return "C'est noté, je m'en souviendrai."
        }

        val forget = Regex("(?i)^(?:oublie|efface de ta mémoire|efface de ta memoire)\\s+(.+)$")
            .matchEntire(trimmed) ?: return null
        val keyword = forget.groupValues[1].trim()
        val facts = Prefs.getFacts(ctx)
        val kept = if (keyword.equals("tout", ignoreCase = true)) {
            emptyList()
        } else {
            facts.filterNot { it.contains(keyword, ignoreCase = true) }
        }
        Prefs.setFacts(ctx, kept)
        val removed = facts.size - kept.size
        return if (removed == 0) "Je n'ai rien trouvé à oublier." else "C'est oublié."
    }
}
