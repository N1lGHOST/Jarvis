package com.example.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/** Synthèse vocale partagée entre l'écran et le service. */
object Voice {
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
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { finish(id) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { finish(id) }
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
        if (cb != null) main.post(cb)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (failed) { onDone?.let { main.post(it) }; return }
        if (!ready) { pending = text to onDone; return }
        val id = UUID.randomUUID().toString()
        if (onDone != null) synchronized(callbacks) { callbacks[id] = onDone }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() { tts?.stop() }
}
