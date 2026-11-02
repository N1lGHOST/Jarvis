package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

object AppState { @Volatile var foreground = false }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Voice.init(this)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { JarvisScreen() } } }
    }

    override fun onStart() { super.onStart(); AppState.foreground = true }
    override fun onStop() { AppState.foreground = false; super.onStop() }
}

@Composable
fun JarvisScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(Prefs.getKey(ctx)) }
    var showSettings by remember { mutableStateOf(apiKey.isBlank()) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun send(text: String) {
        if (Convo.loading) return
        if (apiKey.isBlank()) { showSettings = true; return }
        scope.launch { Voice.speak(Convo.ask(ctx, apiKey, text)) }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) send(said)
        }
    }

    fun listen() {
        if (WakeService.running) {
            Convo.error = "Désactive le mot d'activation pour utiliser le micro ici, ou dis « Jarvis »."
            return
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à Jarvis…")
        }
        try { micLauncher.launch(i) } catch (e: ActivityNotFoundException) {
            Convo.error = "Reconnaissance vocale indisponible sur ce téléphone"
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) WakeService.start(ctx)
        else Convo.error = "Le micro est nécessaire pour le mot d'activation"
    }

    LaunchedEffect(Convo.messages.size) {
        if (Convo.messages.isNotEmpty()) listState.animateScrollToItem(Convo.messages.size - 1)
    }

    LaunchedEffect(Unit) {
        val repo = Prefs.getRepo(ctx)
        if (repo.isNotBlank()) Updater.check(ctx, repo)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Jarvis", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { showSettings = true }) { Text("Réglages") }
        }
        Updater.available?.let { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mise à jour disponible : ${r.name}", Modifier.weight(1f))
                Button(enabled = !Updater.busy, onClick = { scope.launch { Updater.downloadAndInstall(ctx) } }) { Text("Installer") }
            }
        }
        if (Updater.status.isNotBlank() && !showSettings) Text(Updater.status, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mot d'activation « Jarvis »")
                Text("Écoute en continu : consomme de la batterie.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = WakeService.running, onCheckedChange = { on ->
                if (on) {
                    val need = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
                    if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
                    permLauncher.launch(need.toTypedArray())
                } else {
                    WakeService.stop(ctx)
                }
            })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Convo.messages) { Bubble(it) }
        }
        Convo.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp)) }
        if (Convo.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Écris à Jarvis…") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (input.isNotBlank()) { send(input.trim()); input = "" } }) { Text("OK") }
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(onClick = { listen() }) { Text("🎤") }
        }
    }

    if (showSettings) {
        var draft by remember { mutableStateOf(apiKey) }
        var facts by remember { mutableStateOf(Prefs.getFacts(ctx)) }
        var allowConfirm by remember { mutableStateOf(Prefs.getAllowConfirm(ctx)) }
        var repo by remember { mutableStateOf(Prefs.getRepo(ctx)) }
        AlertDialog(
            onDismissRequest = { if (apiKey.isNotBlank()) showSettings = false },
            title = { Text("Réglages") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it }, singleLine = true,
                        label = { Text("Clé API Anthropic") },
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("sk-ant-…") }
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Service d'accessibilité : " + if (JarvisAccessibilityService.connected) "activé" else "désactivé",
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    }) { Text("1. Autoriser les paramètres restreints") }
                    TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                        Text("2. Activer Jarvis dans Accessibilité")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Jarvis peut appuyer sur Envoyer / Appeler", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Switch(checked = allowConfirm, onCheckedChange = { allowConfirm = it; Prefs.setAllowConfirm(ctx, it) })
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Mises à jour (version ${Updater.installedName(ctx)})", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = repo, onValueChange = { repo = it; Prefs.setRepo(ctx, it.trim()) },
                        singleLine = true, label = { Text("Dépôt GitHub (pseudo/nom)") }
                    )
                    TextButton(enabled = !Updater.busy, onClick = {
                        scope.launch { Updater.check(ctx, repo.trim(), manual = true) }
                    }) { Text("Vérifier les mises à jour") }
                    if (Updater.status.isNotBlank()) Text(Updater.status, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Text("Mémoire de Jarvis : ${facts.size} information(s)", style = MaterialTheme.typography.titleSmall)
                    facts.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (facts.isNotEmpty()) {
                        TextButton(onClick = { Prefs.setFacts(ctx, emptyList()); facts = emptyList() }) {
                            Text("Effacer la mémoire")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    apiKey = draft.trim()
                    Prefs.setKey(ctx, apiKey)
                    showSettings = false
                }) { Text("Enregistrer") }
            }
        )
    }
}

@Composable
fun Bubble(m: Msg) {
    val mine = m.role == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) { Text(m.text, Modifier.padding(12.dp)) }
    }
}
