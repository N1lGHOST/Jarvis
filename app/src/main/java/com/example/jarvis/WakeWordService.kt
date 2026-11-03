package com.example.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

object WakeWordState {
    var active by mutableStateOf(false)
        internal set
    var listening by mutableStateOf(false)
        internal set
    var status by mutableStateOf("Désactivé")
        internal set
}

class WakeWordService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null
    private var awaitingCommand = false
    private var stoppingByUser = false
    private var isSpeaking = false
    private var restartQueued = false
    private var pendingCommand: String? = null
    private var restartDelay = 600L

    override fun onCreate() {
        super.onCreate()
        Voice.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PENDING_HANDLED) {
            if (!WakeWordState.active) {
                stopSelf()
                return START_NOT_STICKY
            }
            pendingCommand = null
            notificationManager().notify(NOTIFICATION_ID, buildNotification())
            return START_STICKY
        }
        if (intent?.action == ACTION_REQUEST_COMMAND) {
            if (WakeWordState.active) {
                awaitingCommand = true
                say("Oui ?")
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stoppingByUser = true
            Prefs.setWakeWordEnabled(this, false)
            stopListeningAndService()
            return START_NOT_STICKY
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            WakeWordState.status = "Autorisation microphone requise"
            Prefs.setWakeWordEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (WakeWordState.active && intent?.action == ACTION_START) return START_STICKY

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        WakeWordState.active = true
        WakeWordState.status = "Activation « Jarvis » prête"
        startRecognition()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        serviceScope.cancel()
        main.removeCallbacksAndMessages(null)
        WakeWordState.active = false
        WakeWordState.listening = false
        if (stoppingByUser) {
            WakeWordState.status = "Désactivé"
        } else {
            WakeWordState.status = "Écoute suspendue"
        }
        super.onDestroy()
    }

    private fun startRecognition() {
        if (isSpeaking) return
        if (recognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                WakeWordState.status = "Reconnaissance vocale indisponible sur ce téléphone"
                Prefs.setWakeWordEnabled(this, false)
                stoppingByUser = true
                stopSelf()
                return
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(listener)
            }
        }
        if (restartQueued) return
        restartQueued = true
        main.postDelayed({
            restartQueued = false
            val speech = recognizer ?: return@postDelayed
            if (stoppingByUser) return@postDelayed
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }
                speech.startListening(intent)
                WakeWordState.listening = true
                WakeWordState.status = if (awaitingCommand) "Écoute ta commande…" else "Je veille au mot « Jarvis »"
            } catch (_: SecurityException) {
                WakeWordState.status = "Autorisation microphone manquante"
                Prefs.setWakeWordEnabled(this, false)
                stoppingByUser = true
                stopSelf()
            } catch (_: IllegalStateException) {
                scheduleRestart(1_500)
            }
        }, restartDelay)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            WakeWordState.listening = true
        }

        override fun onBeginningOfSpeech() {
            WakeWordState.status = if (awaitingCommand) "Je t'écoute…" else "Mot d'activation en attente"
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            WakeWordState.listening = false
            WakeWordState.status = "Analyse de la voix…"
        }

        override fun onError(error: Int) {
            WakeWordState.listening = false
            if (isSpeaking) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                WakeWordState.status = "Autorisation microphone requise"
                Prefs.setWakeWordEnabled(this@WakeWordService, false)
                stoppingByUser = true
                stopSelf()
                return
            }
            if (error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
            ) {
                WakeWordState.status = "Vérifie la connexion du service vocal"
                restartDelay = (restartDelay * 2).coerceAtMost(8_000)
            } else {
                restartDelay = 900
            }
            scheduleRestart(restartDelay)
        }

        override fun onResults(results: android.os.Bundle?) {
            WakeWordState.listening = false
            restartDelay = 600
            val recognized = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            handleRecognizedText(recognized)
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    }

    private fun handleRecognizedText(text: String) {
        val normalized = normalize(text)
        val wakeMatch = Regex("\\bjarvis\\b").find(normalized)
        if (wakeMatch != null) {
            val command = normalized.substring(wakeMatch.range.last + 1).trim()
            if (command.isNotBlank()) {
                awaitingCommand = false
                runCommand(text.substringAfter(Regex("(?i)jarvis").find(text)?.value.orEmpty(), "").trim())
            } else {
                awaitingCommand = true
                main.postDelayed({
                    if (awaitingCommand && !stoppingByUser) {
                        awaitingCommand = false
                        scheduleRestart(500)
                    }
                }, COMMAND_WINDOW_MS)
                say("Oui ?")
            }
            return
        }

        if (awaitingCommand && text.isNotBlank()) {
            awaitingCommand = false
            runCommand(text)
        } else {
            scheduleRestart(500)
        }
    }

    private fun runCommand(text: String) {
        val apiKey = Prefs.getOpenRouterKey(this)
        serviceScope.launch {
            WakeWordState.status = "Je m'en occupe…"
            val answer = Convo.ask(this@WakeWordService, apiKey, text, allowActivityLaunch = false)
            pendingCommand = DeviceActions.takePendingCommand()
            if (pendingCommand != null) {
                notificationManager().notify(NOTIFICATION_ID, buildNotification())
            }
            say(answer)
        }
    }

    private fun say(text: String) {
        isSpeaking = true
        WakeWordState.listening = false
        WakeWordState.status = "Jarvis répond…"
        recognizer?.cancel()
        Voice.speak(text) {
            isSpeaking = false
            WakeWordState.status = "Je veille au mot « Jarvis »"
            scheduleRestart(800)
        }
    }

    private fun scheduleRestart(delay: Long) {
        if (stoppingByUser || restartQueued) return
        restartQueued = true
        main.postDelayed({
            restartQueued = false
            startRecognition()
        }, delay)
    }

    private fun stopListeningAndService() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        WakeWordState.active = false
        WakeWordState.listening = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            pendingCommand?.let { putExtra(EXTRA_PENDING_ACTION, it) }
        }
        val openPending = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, WakeWordService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val status = if (pendingCommand != null) {
            "Touche pour ouvrir l'application demandée"
        } else {
            "Écoute active — dis « Jarvis »; touche pour ouvrir"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.jarvis_logo)
            .setContentTitle("Jarvis veille")
            .setContentText(status)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Désactiver", stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Écoute Jarvis",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indique que Jarvis attend son mot d'activation."
            }
            notificationManager().createNotificationChannel(channel)
        }
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_START = "com.example.jarvis.action.START_WAKE_WORD"
        const val ACTION_STOP = "com.example.jarvis.action.STOP_WAKE_WORD"
        const val ACTION_REQUEST_COMMAND = "com.example.jarvis.action.REQUEST_COMMAND"
        const val ACTION_PENDING_HANDLED = "com.example.jarvis.action.PENDING_HANDLED"
        const val EXTRA_PENDING_ACTION = "pending_device_action"
        private const val CHANNEL_ID = "jarvis_wake_word"
        private const val NOTIFICATION_ID = 24
        private const val COMMAND_WINDOW_MS = 8_000L

        fun start(ctx: Context) {
            val intent = Intent(ctx, WakeWordService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(ctx, intent)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, WakeWordService::class.java).setAction(ACTION_STOP))
        }

        fun pendingActionHandled(ctx: Context) {
            ctx.startService(Intent(ctx, WakeWordService::class.java).setAction(ACTION_PENDING_HANDLED))
        }

        fun requestCommand(ctx: Context) {
            ctx.startService(Intent(ctx, WakeWordService::class.java).setAction(ACTION_REQUEST_COMMAND))
        }

        private fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
    }
}
