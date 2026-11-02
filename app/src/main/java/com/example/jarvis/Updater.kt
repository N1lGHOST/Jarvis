package com.example.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Release(val versionCode: Long, val name: String, val apkUrl: String)

/** Mise à jour en un clic depuis les Releases d'un dépôt GitHub public. */
object Updater {
    var available by mutableStateOf<Release?>(null)
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)

    fun installedName(ctx: Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    @Suppress("DEPRECATION")
    private fun installedCode(ctx: Context): Long {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
    }

    /** manual = true : affiche aussi « à jour » et les erreurs. */
    suspend fun check(ctx: Context, repo: String, manual: Boolean = false) {
        if (!Regex("[\\w.-]+/[\\w.-]+").matches(repo)) {
            if (manual) status = "Dépôt invalide : écris pseudo/nom (ex. marc/jarvis)"
            return
        }
        busy = true
        if (manual) status = "Vérification…"
        try {
            val rel = withContext(Dispatchers.IO) { fetchLatest(repo) }
            if (rel != null && rel.versionCode > installedCode(ctx)) {
                available = rel
                status = "Nouvelle version disponible : ${rel.name}"
            } else {
                available = null
                status = if (manual) "Jarvis est à jour (version ${installedName(ctx)})" else ""
            }
        } catch (e: IOException) {
            if (manual) status = "Pas de connexion internet"
        } catch (e: Exception) {
            if (manual) status = e.message ?: "Erreur"
        } finally {
            busy = false
        }
    }

    private fun fetchLatest(repo: String): Release? {
        val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Jarvis-Android")
            val code = conn.responseCode
            if (code == 404) throw Exception("Aucune version trouvée : dépôt introuvable, privé, ou pas encore compilé")
            if (code !in 200..299) throw Exception("GitHub : erreur $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val vc = Regex("\\d+").find(tag)?.value?.toLongOrNull() ?: return null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Release(vc, json.optString("name", tag).ifBlank { tag }, a.getString("browser_download_url"))
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** À appeler depuis l'interface (thread principal). */
    suspend fun downloadAndInstall(ctx: Context) {
        val rel = available ?: return
        busy = true
        status = "Téléchargement…"
        try {
            val file = withContext(Dispatchers.IO) { download(ctx, rel.apkUrl) }
            status = ""
            install(ctx, file)
        } catch (e: IOException) {
            status = "Téléchargement impossible (connexion ?)"
        } catch (e: Exception) {
            status = "Échec : ${e.message}"
        } finally {
            busy = false
        }
    }

    private fun download(ctx: Context, url: String): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "jarvis-update.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Jarvis-Android")
            if (conn.responseCode !in 200..299) throw Exception("serveur ${conn.responseCode}")
            conn.inputStream.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        } finally {
            conn.disconnect()
        }
        if (out.length() < 100_000) throw Exception("fichier téléchargé invalide")
        return out
    }

    private fun install(ctx: Context, file: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            status = "Autorise Jarvis à installer des applis, puis appuie à nouveau sur Installer."
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
