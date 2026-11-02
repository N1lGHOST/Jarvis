package com.example.jarvis

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Écoute en continu le mot « Jarvis », puis traite la commande qui suit. */
class WakeService : Service() {

    companion object {
        var running by mutableStateOf(false)
        fun start(c: Context) =
            ContextCompat.startForegroundService(c, Intent(c, WakeService::class.java))
        fun stop(c: Context) { c.stopService(Intent(c, WakeService::class.java)) }
        private val WAKE = Regex("(?i)jarvis|jarvice|gervais")
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var awaitingCommand = false
    private var busy = false
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifs.ensure(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, Notifs.CH_SERVICE)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis écoute")
            .setContentText("Dis « Jarvis » suivi de ta demande")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notif)
        }
        Voice.init(this)
        running = true
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { stopSelf(); return }
        listen()
    }

    private fun listen(delayMs: Long = 300) {
        if (stopped) return
        main.postDelayed({
            if (stopped || busy) return@postDelayed
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
                r.setRecognitionListener(listener)
                r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                })
            }
        }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            handle(text.trim())
        }
        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { stopSelf(); return }
            listen(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 300)
        }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handle(text: String) {
        if (awaitingCommand) {
            awaitingCommand = false
            if (text.isNotEmpty()) process(text) else listen()
            return
        }
        val m = WAKE.find(text)
        if (m == null) { listen(); return }
        val cmd = text.substring(m.range.last + 1).trim { !it.isLetterOrDigit() }
        if (cmd.isEmpty()) {
            busy = true
            Voice.speak("Oui ?") { busy = false; awaitingCommand = true; listen(100) }
        } else {
            process(cmd)
        }
    }

    private fun process(cmd: String) {
        busy = true
        scope.launch {
            val key = Prefs.getKey(this@WakeService)
            val answer = if (key.isBlank()) "Ajoute d'abord ta clé API dans l'application."
                         else Convo.ask(this@WakeService, key, cmd)
            Voice.speak(answer) { busy = false; listen(200) }
        }
    }

    override fun onDestroy() {
        stopped = true
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        Voice.stop()
        scope.cancel()
        super.onDestroy()
    }
}
