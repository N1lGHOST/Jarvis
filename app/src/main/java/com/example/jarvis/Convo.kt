package com.example.jarvis

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Conversation partagée entre l'écran et le service (à appeler depuis le thread principal). */
object Convo {
    val messages = mutableStateListOf<Msg>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    /** Renvoie le texte à dire à voix haute. */
    suspend fun ask(ctx: Context, apiKey: String, text: String): String {
        val app = ctx.applicationContext
        messages.add(Msg("user", text))
        error = null
        loading = true
        val history = messages.takeLast(20).dropWhile { it.role != "user" }
        return try {
            val answer = withContext(Dispatchers.IO) {
                ClaudeClient.ask(apiKey, history, Prefs.getFacts(app)) { name, input ->
                    Actions(app).run(name, input)
                }
            }
            messages.add(Msg("assistant", answer))
            answer
        } catch (e: IOException) {
            error = "Pas de connexion internet"
            "Je n'ai pas de connexion internet."
        } catch (e: Exception) {
            error = e.message ?: "Erreur inconnue"
            "Désolé, une erreur est survenue."
        } finally {
            loading = false
        }
    }
}
