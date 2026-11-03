package com.example.jarvis

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.AlarmClock
import android.provider.Settings
import java.text.Normalizer
import java.util.Locale

data class DeviceActionResult(
    val answer: String,
    val needsForeground: Boolean = false
)

object DeviceActions {
    var pendingCommand: String? = null
        private set

    fun takePendingCommand(): String? = pendingCommand.also { pendingCommand = null }

    fun execute(ctx: Context, text: String, allowActivityLaunch: Boolean): DeviceActionResult? {
        val command = normalize(text).trim().trimEnd('.', '!', '?')
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        when {
            command.matches(Regex("^(monte|augmente) (le )?(son|volume)(.*)$")) -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return DeviceActionResult("Je monte le volume.")
            }
            command.matches(Regex("^(baisse|diminue) (le )?(son|volume)(.*)$")) -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return DeviceActionResult("Je baisse le volume.")
            }
            Regex("^(?:mets|regle|regles) (?:le )?(?:son|volume) a (\\d{1,3})(?: pourcent|%)?$")
                .find(command) != null -> {
                val percent = Regex("^(?:mets|regle|regles) (?:le )?(?:son|volume) a (\\d{1,3})(?: pourcent|%)?$")
                    .find(command)?.groupValues?.get(1)?.toIntOrNull()
                if (percent == null || percent !in 0..100) {
                    return DeviceActionResult("Indique un volume entre 0 et 100 pour cent.")
                }
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audio.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (max * percent / 100f).toInt(),
                    AudioManager.FLAG_SHOW_UI
                )
                return DeviceActionResult("Le volume est réglé à $percent pour cent.")
            }
            command.matches(Regex("^(coupe|coupe-moi|mets) (le )?(son|volume) (en sourdine|au minimum|a zero)$")) -> {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                return DeviceActionResult("Le volume multimédia est coupé.")
            }
            command.matches(Regex("^(ouvre|affiche) (les )?parametres( du telephone)?$")) ->
                return launch(ctx, allowActivityLaunch, text, Intent(Settings.ACTION_SETTINGS), "J'ouvre les paramètres.")
            command.contains("parametres wifi") || command.contains("reglages wifi") ->
                return launch(ctx, allowActivityLaunch, text, Intent(Settings.ACTION_WIFI_SETTINGS), "J'ouvre les réglages Wi-Fi.")
            command.contains("parametres bluetooth") || command.contains("reglages bluetooth") ->
                return launch(ctx, allowActivityLaunch, text, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "J'ouvre les réglages Bluetooth.")
            command.contains("parametres affichage") || command.contains("reglages affichage") ->
                return launch(ctx, allowActivityLaunch, text, Intent(Settings.ACTION_DISPLAY_SETTINGS), "J'ouvre les réglages d'affichage.")
        }

        alarmIntent(command)?.let { intent ->
            return launch(ctx, allowActivityLaunch, text, intent, "J'ouvre l'horloge pour confirmer le réveil.")
        }
        timerIntent(command)?.let { intent ->
            return launch(ctx, allowActivityLaunch, text, intent, "J'ouvre l'horloge pour démarrer le minuteur.")
        }

        val appName = Regex(
            "^(?:ouvre|lance|demarre) (?:l application |l appli |l assistant |le jeu |la |le |les )?(.+)$"
        )
            .find(command)?.groupValues?.get(1)?.trim()
        if (appName != null) {
            val knownAssistant = appName in setOf("assistant google", "google assistant")
            if (knownAssistant) {
                val assistantIntent = Intent(Intent.ACTION_VOICE_COMMAND)
                if (assistantIntent.resolveActivity(ctx.packageManager) != null) {
                    return launch(ctx, allowActivityLaunch, text, assistantIntent, "J'ouvre l'assistant vocal disponible.")
                }
            }
            val appIntent = findAppIntent(ctx, appName)
                ?: return DeviceActionResult("Je ne trouve pas l'application « $appName » sur ce téléphone.")
            return launch(ctx, allowActivityLaunch, text, appIntent, "J'ouvre $appName.")
        }
        return null
    }

    private fun launch(
        ctx: Context,
        allowActivityLaunch: Boolean,
        command: String,
        intent: Intent,
        answer: String
    ): DeviceActionResult {
        if (!allowActivityLaunch) {
            pendingCommand = command
            return DeviceActionResult(
                "Android bloque l'ouverture d'une autre application depuis l'arrière-plan. Touche la notification Jarvis pour continuer.",
                needsForeground = true
            )
        }
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            DeviceActionResult(answer)
        } catch (_: ActivityNotFoundException) {
            DeviceActionResult("Cette action n'est pas prise en charge par une application installée.")
        } catch (_: SecurityException) {
            DeviceActionResult("Android n'autorise pas cette action sur ce téléphone.")
        }
    }

    private fun findAppIntent(ctx: Context, requestedName: String): Intent? {
        val launchers = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = ctx.packageManager.queryIntentActivities(launchers, 0)
        val candidate = matches.firstOrNull {
            it.activityInfo.packageName != ctx.packageName &&
                normalize(it.loadLabel(ctx.packageManager).toString()).contains(requestedName)
        } ?: return null
        return ctx.packageManager.getLaunchIntentForPackage(candidate.activityInfo.packageName)
    }

    private fun alarmIntent(command: String): Intent? {
        if (!command.contains("reveil") && !command.contains("alarme")) return null
        val match = Regex(".*(?:reveil|alarme).*?(?:a )?(\\d{1,2})(?:h(?:(\\d{1,2}))?|:(\\d{1,2}))?.*")
            .matchEntire(command) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = (match.groupValues[2].ifBlank { match.groupValues[3] }).toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
    }

    private fun timerIntent(command: String): Intent? {
        if (!command.contains("minuteur") && !command.contains("minuterie") && !command.contains("timer")) return null
        val match = Regex(".*?(\\d+)\\s*(minutes?|mins?|secondes?|secs?).*").matchEntire(command) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        if (amount <= 0) return null
        val unit = match.groupValues[2]
        val seconds = if (unit.startsWith("sec")) {
            if (amount > AlarmManager.INTERVAL_DAY.toInt()) return null
            amount
        } else {
            if (amount > AlarmManager.INTERVAL_DAY.toInt() / 60) return null
            amount * 60
        }
        return Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("['’]"), " ")
            .replace(Regex("\\s+"), " ")
}
