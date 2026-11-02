package com.example.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.UUID

/** Synthèse vocale partagée entre l'écran et le service. */
object Voice {
    var speaking by mutableStateOf(false)
        private set
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var pending: Pair<String, (() -> Unit)?>? = null
    private val callbacks = HashMap<String, () -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRENCH
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { main.post { speaking = true } }
                    override fun onDone(id: String?) { finish(id) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { finish(id) }
                    override fun onStop(id: String?, interrupted: Boolean) { finish(id) }
                })
                ready = true
            } else {
                failed = true
            }
            pending?.let { speak(it.first, it.second) }
            pending = null
        }
    }

    private fun finish(id: String?) {
        val cb = synchronized(callbacks) { callbacks.remove(id) }
        main.post {
            speaking = false
            cb?.invoke()
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (failed) { onDone?.let { main.post(it) }; return }
        if (!ready) { pending = text to onDone; return }
        val id = UUID.randomUUID().toString()
        if (onDone != null) synchronized(callbacks) { callbacks[id] = onDone }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        tts?.stop()
        speaking = false
        pending = null
        synchronized(callbacks) { callbacks.clear() }
    }
}
