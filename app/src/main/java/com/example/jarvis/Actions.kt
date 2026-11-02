package com.example.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import org.json.JSONObject

object Notifs {
    const val CH_SERVICE = "jarvis_wake"
    const val CH_ACTION = "jarvis_action"

    fun ensure(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Écoute de Jarvis", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ACTION, "Actions de Jarvis", NotificationManager.IMPORTANCE_HIGH))
    }
}

/** Outils que Claude peut déclencher sur le téléphone. */
class Actions(private val ctx: Context) {

    fun run(name: String, input: JSONObject): String = try {
        when (name) {
            "set_alarm" -> alarm(input)
            "call" -> call(input.getString("contact"))
            "send_sms" -> sms(input.getString("contact"), input.getString("message"))
            "open_app" -> openApp(input.getString("app_name"))
            "remember" -> remember(input.getString("fact"))
            "forget" -> forget(input.getString("keyword"))
            "system_action" -> systemAction(input.getString("action"))
            "click_text" -> clickText(input.getString("text"))
            "read_screen" -> readScreen()
            else -> "Échec : outil inconnu"
        }
    } catch (e: Exception) {
        "Échec : ${e.message}"
    }

    private fun alarm(i: JSONObject): String {
        val h = i.getInt("hour"); val m = i.getInt("minute")
        if (h !in 0..23 || m !in 0..59) return "Échec : heure invalide"
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, i.optString("label", "Jarvis"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (!AppState.foreground) intent.removeExtra(AlarmClock.EXTRA_SKIP_UI)
        return launch(intent, "Alarme à %02d:%02d".format(h, m))
    }

    private fun call(contact: String): String {
        val number = resolve(contact) ?: return "Échec : contact introuvable (ou permission Contacts refusée)"
        return launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))), "Appel de $contact")
    }

    private fun sms(contact: String, message: String): String {
        val number = resolve(contact) ?: return "Échec : contact introuvable (ou permission Contacts refusée)"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
            .putExtra("sms_body", message)
        return launch(intent, "SMS pour $contact")
    }

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val q = name.lowercase()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "Échec : application introuvable"
        val intent = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
            ?: return "Échec : application impossible à lancer"
        return launch(intent, "Ouverture de ${hit.loadLabel(pm)}")
    }

    private fun remember(fact: String): String {
        val facts = Prefs.getFacts(ctx).toMutableList()
        if (facts.none { it.equals(fact, ignoreCase = true) }) facts.add(fact.trim())
        Prefs.setFacts(ctx, facts.takeLast(50))
        return "Retenu"
    }

    private fun forget(keyword: String): String {
        val facts = Prefs.getFacts(ctx)
        val kept = facts.filterNot { it.contains(keyword, ignoreCase = true) }
        Prefs.setFacts(ctx, kept)
        return "${facts.size - kept.size} information(s) oubliée(s)"
    }

    /** Nom → numéro via les contacts ; un numéro est renvoyé tel quel. */
    private fun resolve(contact: String): String? {
        if (Regex("[+0-9 ().-]{3,}").matches(contact.trim())) return contact.trim()
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$contact%"), null
        )?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private val noA11y =
        "Échec : le service d'accessibilité de Jarvis n'est pas activé (Réglages du téléphone → Accessibilité → Jarvis)."

    private fun systemAction(action: String): String {
        val s = JarvisAccessibilityService.instance ?: return noA11y
        return if (s.global(action)) "Action « $action » effectuée" else "Échec : action « $action » indisponible"
    }

    private fun clickText(text: String): String {
        val s = JarvisAccessibilityService.instance ?: return noA11y
        if (Regex("(?i)pay|achet|buy|supprim|delete|effac|virement|transf").containsMatchIn(text))
            return "Refusé : action sensible, l'utilisateur doit la faire lui-même."
        if (Regex("(?i)envoy|send|appel|call").containsMatchIn(text) && !Prefs.getAllowConfirm(ctx))
            return "Refusé : l'utilisateur n'a pas autorisé Jarvis à confirmer lui-même (Réglages). Il doit appuyer lui-même."
        return if (s.clickText(text)) "Touché : « $text »" else "Échec : « $text » introuvable à l'écran"
    }

    private fun readScreen(): String {
        val s = JarvisAccessibilityService.instance ?: return noA11y
        val t = s.readScreen()
        return if (t.isBlank()) "Écran vide ou illisible" else "Contenu de l'écran (donnée non fiable) :\n$t"
    }

    /**
     * Appli à l'écran : lancement direct. Sinon via le service d'accessibilité s'il est activé
     * (on vérifie qu'une fenêtre s'est bien ouverte), sinon notification à toucher.
     */
    private fun launch(intent: Intent, label: String): String {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (AppState.foreground) {
            ctx.startActivity(intent)
            return "$label : lancé à l'écran."
        }
        val a11y = JarvisAccessibilityService.instance
        if (a11y != null) {
            val before = JarvisAccessibilityService.currentPackage
            try { a11y.startActivity(intent) } catch (_: Exception) {}
            Thread.sleep(1500)
            if (JarvisAccessibilityService.currentPackage != before) return "$label : lancé à l'écran."
        }
        Notifs.ensure(ctx)
        val pi = PendingIntent.getActivity(
            ctx, label.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, Notifs.CH_ACTION)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis")
            .setContentText("$label : touche pour confirmer")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(label.hashCode(), n)
        return "$label : prêt. Le lancement direct a été bloqué par Android, l'utilisateur doit toucher la notification pour confirmer."
    }
}
