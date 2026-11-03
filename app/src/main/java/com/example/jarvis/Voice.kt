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
    var muted by mutableStateOf(false)
        private set
    var pitch by mutableStateOf(0.8f)
        private set
    private var tts: TextToSpeech? = null
    private var appContext: Context? = null
    private var ready = false
    private var failed = false
    private var pending: Pair<String, (() -> Unit)?>? = null
    private val callbacks = HashMap<String, () -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun init(ctx: Context) {
        if (tts != null) return
        appContext = ctx.applicationContext
        muted = Prefs.isSpeechMuted(ctx)
        pitch = Prefs.getSpeechPitch(ctx)
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                main.post {
                    val engine = tts ?: return@post
                    engine.language = Locale.FRENCH
                    engine.setPitch(pitch)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) { main.post { speaking = true } }
                        override fun onDone(id: String?) { finish(id) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) { finish(id) }
                        override fun onStop(id: String?, interrupted: Boolean) { finish(id) }
                    })
                    ready = true
                    pending?.let { speak(it.first, it.second) }
                    pending = null
                }
            } else {
                failed = true
                pending?.second?.let { main.post(it) }
                pending = null
            }
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
        if (muted) { onDone?.let { main.post(it) }; return }
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

    fun reload(ctx: Context) {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
        failed = false
        init(ctx)
    }

    fun updatePitch(value: Float) {
        pitch = value.coerceIn(0.7f, 1.3f)
        tts?.setPitch(pitch)
        appContext?.let { Prefs.setSpeechPitch(it, pitch) }
    }

    fun updateMuted(value: Boolean) {
        muted = value
        appContext?.let { Prefs.setSpeechMuted(it, value) }
        if (value) stop()
    }
}
