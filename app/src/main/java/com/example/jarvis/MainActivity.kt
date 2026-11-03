package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Void = Color(0xFF080B13)
private val Neon = Color(0xFF7AA8FF)
private val Ice = Color(0xFFEAF1FF)
private val Muted = Color(0xFF929FB8)
private val Panel = Color(0xFF141D2D)
private val Lavender = Color(0xFFB8A0FF)
private val Cyan = Color(0xFF72D8F2)

class MainActivity : ComponentActivity() {
    private var pendingDeviceAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Voice.init(this)
        Prefs.migrateApiKeys(this)
        Convo.restore(this)
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 19)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 19)
        pendingDeviceAction = intent?.getStringExtra(WakeWordService.EXTRA_PENDING_ACTION)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Void) {
                    JarvisScreen(
                        pendingDeviceAction = pendingDeviceAction,
                        clearPendingDeviceAction = { pendingDeviceAction = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeviceAction = intent.getStringExtra(WakeWordService.EXTRA_PENDING_ACTION)
    }
}

@Composable
private fun JarvisScreen(
    pendingDeviceAction: String?,
    clearPendingDeviceAction: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contentScrollState = rememberScrollState()
    var aiProvider by remember { mutableStateOf(Prefs.getAiProvider(context)) }
    var apiKey by remember(aiProvider) {
        mutableStateOf(
            if (aiProvider == "gemini") Prefs.getGeminiKey(context)
            else Prefs.getOpenRouterKey(context)
        )
    }
    var showSettings by remember { mutableStateOf(false) }
    var showWakeDisclosure by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var showInventory by remember { mutableStateOf(false) }
    var inventory by remember { mutableStateOf(Prefs.getInventory(context)) }
    var inventoryName by remember { mutableStateOf("") }
    var inventoryQuantity by remember { mutableStateOf("1") }
    var showWebSearch by remember { mutableStateOf(false) }
    var webSearchInput by remember { mutableStateOf("") }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || Convo.loading) return
        scope.launch {
            val answer = Convo.ask(context, apiKey, trimmed, aiProvider = aiProvider)
            Voice.speak(answer)
        }
    }

    val wakePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val microphoneGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            grants[Manifest.permission.POST_NOTIFICATIONS] == true ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (microphoneGranted && notificationsGranted) {
            Prefs.setWakeWordEnabled(context, true)
            WakeWordService.start(context)
        } else {
            Convo.error = "Autorise le microphone et les notifications pour activer l'écoute en arrière-plan."
        }
    }

    fun requestWakeWordPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter {
            context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            Prefs.setWakeWordEnabled(context, true)
            WakeWordService.start(context)
        } else {
            wakePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        if (!enabled) {
            Prefs.setWakeWordEnabled(context, false)
            WakeWordService.stop(context)
            return
        }
        if (!Prefs.hasWakeWordDisclosureConsent(context)) {
            showWakeDisclosure = true
            return
        }
        requestWakeWordPermissions()
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) send(spoken)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecognition(speechLauncher) { listening = it }
        else Convo.error = "Autorise le microphone pour parler à Jarvis."
    }
    val ttsSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Voice.useSystemVoice(context)
    }

    LaunchedEffect(Unit) {
        if (Prefs.isWakeWordEnabled(context) &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED)
        ) {
            WakeWordService.start(context)
        }
    }

    LaunchedEffect(pendingDeviceAction) {
        val command = pendingDeviceAction ?: return@LaunchedEffect
        val result = DeviceActions.execute(context, command, allowActivityLaunch = true)
        if (result != null) {
            Convo.recordActionResult(context, result.answer)
            Voice.speak(result.answer)
        }
        WakeWordService.pendingActionHandled(context)
        clearPendingDeviceAction()
    }

    LaunchedEffect(Convo.messages.size) {
        contentScrollState.animateScrollTo(contentScrollState.maxValue)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111B32), Void, Color(0xFF070910))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.jarvis_logo),
                        contentDescription = "Logo Jarvis",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, Neon.copy(alpha = 0.72f), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "JARVIS",
                            color = Ice,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 3.sp
                        )
                        Text(
                            "WORKFLOW ASSISTANT",
                            color = Muted,
                            fontSize = 8.sp,
                            letterSpacing = 1.3.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { Voice.updateMuted(!Voice.muted) }) {
                        Text(
                            if (Voice.muted) "MUET" else "SON",
                            color = if (Voice.muted) Muted else Neon,
                            fontSize = 10.sp
                        )
                    }
                    TextButton(onClick = { showSettings = true }) {
                        Text("RÉGLAGES", color = Ice.copy(alpha = 0.8f), fontSize = 10.sp, letterSpacing = 0.7.sp)
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(contentScrollState)
                    .padding(top = 8.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Panel.copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (WakeWordState.active) "VEILLE ACTIVE" else "ACTIONS LOCALES",
                            color = if (WakeWordState.active) Cyan else Ice,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            if (WakeWordState.active) "Micro actif · ${WakeWordState.status}"
                            else "Commandes sur l'appareil · IA via OpenRouter",
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = WakeWordState.active,
                        onCheckedChange = { setWakeWordEnabled(it) }
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1B2943).copy(alpha = 0.96f), Panel.copy(alpha = 0.94f))
                            )
                        )
                        .border(1.dp, Neon.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "TON ESPACE DE TRAVAIL",
                        color = Neon,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "Bonjour. Que faisons-nous ?",
                        color = Ice,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    Text(
                        "Parle à Jarvis ou demande-lui une action.",
                        color = Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Box(
                        Modifier.fillMaxWidth().height(188.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        JarvisOrb(
                            listening = listening,
                            speaking = Voice.speaking,
                            thinking = Convo.loading,
                            diameter = 160.dp,
                            onClick = {
                                if (!listening && !Convo.loading) {
                                    if (Voice.speaking) Voice.stop()
                                    if (WakeWordState.active) {
                                        WakeWordService.requestCommand(context)
                                    } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        startRecognition(speechLauncher) { listening = it }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        )
                    }
                    Text(
                        when {
                            Convo.loading -> "JARVIS RÉFLÉCHIT"
                            Voice.speaking -> "JARVIS RÉPOND"
                            listening || (WakeWordState.active && WakeWordState.listening) -> "JARVIS ÉCOUTE"
                            WakeWordState.active -> "DIS « JARVIS » OU TOUCHE L'ORB"
                            else -> "TOUCHE L'ORB POUR PARLER"
                        },
                        color = if (listening || Voice.speaking || Convo.loading) Cyan else Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.6.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ACTIONS RAPIDES", color = Ice, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Text("SUR CET APPAREIL", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard(
                        title = "Mon stock",
                        detail = "Articles & quantités",
                        accent = Lavender,
                        modifier = Modifier.weight(1f)
                    ) {
                        inventory = Prefs.getInventory(context)
                        showInventory = true
                    }
                    QuickActionCard(
                        title = "Batterie",
                        detail = "Niveau actuel",
                        accent = Cyan,
                        modifier = Modifier.weight(1f)
                    ) { send("quel est le niveau de ma batterie") }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        title = "Recherche Web",
                        detail = "Sur demande",
                        accent = Neon,
                        modifier = Modifier.weight(1f)
                    ) { showWebSearch = true }
                    QuickActionCard(
                        title = "Minuteur",
                        detail = "5 minutes",
                        accent = Color(0xFFFFB978),
                        modifier = Modifier.weight(1f)
                    ) { send("mets un minuteur de 5 minutes") }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("CONVERSATION", color = Ice, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Text(
                        if (Convo.messages.isEmpty()) "NOUVELLE CONVERSATION" else "${Convo.messages.size} MESSAGES",
                        color = Muted,
                        fontSize = 8.sp,
                        letterSpacing = 0.8.sp
                    )
                }
                if (Convo.error != null) {
                    Text(
                        text = Convo.error.orEmpty(),
                        color = Color(0xFFFF9E9E),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
                if (Convo.messages.isEmpty()) {
                    Text(
                        "Essaie « quelle heure est-il ? », « liste mon stock » ou une question à Jarvis.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                } else {
                    Convo.messages.takeLast(8).forEach { message ->
                        MessageCard(message)
                        Spacer(Modifier.height(9.dp))
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Parle-moi…", color = Muted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ice,
                        unfocusedTextColor = Ice,
                        focusedBorderColor = Neon.copy(alpha = 0.8f),
                        unfocusedBorderColor = Muted.copy(alpha = 0.35f),
                        cursorColor = Neon,
                        focusedContainerColor = Panel.copy(alpha = 0.85f),
                        unfocusedContainerColor = Panel.copy(alpha = 0.65f)
                    )
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !Convo.loading,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Neon,
                        contentColor = Void,
                        disabledContainerColor = Neon.copy(alpha = 0.22f),
                        disabledContentColor = Muted
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Text("↑", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showSettings) {
        var draftProvider by remember(aiProvider) { mutableStateOf(aiProvider) }
        var draft by remember(draftProvider, apiKey) {
            mutableStateOf(
                if (draftProvider == "gemini") Prefs.getGeminiKey(context)
                else Prefs.getOpenRouterKey(context)
            )
        }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("Réglages Jarvis", color = Ice) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text("FOURNISSEUR IA", color = Ice, fontSize = 12.sp, letterSpacing = 1.sp)
                    Text(
                        "Les actions du téléphone sont exécutées localement. Pour les réponses IA, le message, les échanges récents (qui peuvent inclure des commandes locales) et les souvenirs fournis sont envoyés au fournisseur choisi.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("gemini" to "GEMINI", "openrouter" to "OPENROUTER").forEach { (id, label) ->
                            val selected = draftProvider == id
                            Button(
                                onClick = {
                                    if (draftProvider != id) draftProvider = id
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Neon.copy(alpha = 0.18f) else Void,
                                    contentColor = if (selected) Neon else Muted
                                )
                            ) {
                                Text(label, fontSize = 10.sp, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                    Text(
                        if (draftProvider == "gemini") {
                            "Gemini 2.5 Flash · API Google AI Studio. Cette option nécessite une clé Gemini."
                        } else {
                            "OpenRouter free · modèle gratuit disponible selon le compte et le fournisseur."
                        },
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text("Clé API ${if (draftProvider == "gemini") "Gemini" else "OpenRouter"}") },
                        placeholder = {
                            Text(if (draftProvider == "gemini") "Clé Google AI Studio" else "sk-or-v1-…")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Ice,
                            unfocusedTextColor = Ice,
                            focusedBorderColor = Neon,
                            unfocusedBorderColor = Muted.copy(alpha = 0.45f),
                            focusedLabelColor = Neon,
                            unfocusedLabelColor = Muted,
                            cursorColor = Neon
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        val intent = Intent("com.android.settings.TTS_SETTINGS")
                        if (intent.resolveActivity(context.packageManager) != null) {
                            ttsSettingsLauncher.launch(intent)
                        } else {
                            Convo.error = "Les réglages vocaux Android ne sont pas disponibles sur ce téléphone."
                        }
                    }) {
                        Text("CHOISIR LA VOIX DU TÉLÉPHONE", color = Neon)
                    }
                    Text(
                        "Après avoir choisi le moteur et la langue dans Android, sélectionne une voix française disponible dans la liste ci-dessous. Le profil de tonalité ne garantit pas un timbre ou un accent précis.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("PROFIL DE TONALITÉ", color = Ice, fontSize = 12.sp, letterSpacing = 1.sp)
                    Text(
                        "Ces profils modifient la hauteur de la voix, pas son timbre. Le résultat dépend du moteur vocal du téléphone.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("man" to "HOMME", "woman" to "FEMME").forEach { (profile, label) ->
                            val selected = Voice.speechProfile == profile
                            Button(
                                onClick = {
                                    Voice.updateProfile(profile)
                                    Voice.speak("Bonjour, Monsieur.")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Neon.copy(alpha = 0.18f) else Void,
                                    contentColor = if (selected) Neon else Muted
                                )
                            ) {
                                Text(label, fontSize = 11.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                    var showVoiceMenu by remember { mutableStateOf(false) }
                    val selectedVoiceLabel = Voice.availableVoices
                        .firstOrNull { it.name == Voice.selectedVoiceName }?.label
                    Box {
                        TextButton(
                            onClick = { showVoiceMenu = true },
                            enabled = Voice.availableVoices.isNotEmpty()
                        ) {
                            Text(
                                selectedVoiceLabel?.let { "VOIX : $it" }
                                    ?: "Aucune voix française listée",
                                color = if (Voice.availableVoices.isNotEmpty()) Neon else Muted
                            )
                        }
                        DropdownMenu(
                            expanded = showVoiceMenu,
                            onDismissRequest = { showVoiceMenu = false }
                        ) {
                            Voice.availableVoices.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        Voice.selectVoice(option.name)
                                        showVoiceMenu = false
                                        Voice.speak("Bonjour, Monsieur.")
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("TONALITÉ ${"%.1f".format(java.util.Locale.FRANCE, Voice.pitch)}", color = Muted, fontSize = 12.sp)
                    Slider(
                        value = Voice.pitch,
                        onValueChange = { Voice.updatePitch(it) },
                        valueRange = 0.7f..1.3f,
                        onValueChangeFinished = { Voice.speak("Bonjour, Monsieur.") }
                    )
                    TextButton(onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        } catch (_: ActivityNotFoundException) {
                            Convo.error = "Les réglages d'autorisation Android ne sont pas disponibles."
                        }
                    }) {
                        Text("AUTORISATIONS DE L'APPLICATION", color = Neon)
                    }
                    Text(
                        "Gère ici les autorisations Android, notamment le microphone et les notifications. Aucun service d'accessibilité n'est utilisé.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { Convo.clear(context) }) {
                        Text("Effacer la conversation", color = Color(0xFFFF9E9E))
                    }
                    Text(
                        "Veille = microphone actif, même avant « Jarvis ». Android privilégie la reconnaissance hors ligne si disponible; sinon son service vocal peut traiter les extraits. Les requêtes IA et les échanges récents sont transmis à Gemini ou OpenRouter. Une clé dans une APK peut être extraite. Mémoire locale : ${Prefs.getFacts(context).size} élément(s).",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    apiKey = draft.trim()
                    if (draftProvider == "gemini") {
                        Prefs.setGeminiKey(context, apiKey)
                    } else {
                        Prefs.setOpenRouterKey(context, apiKey)
                    }
                    Prefs.setAiProvider(context, draftProvider)
                    aiProvider = draftProvider
                    showSettings = false
                }) { Text("ENREGISTRER", color = Neon) }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("FERMER", color = Muted)
                }
            }
        )
    }

    if (showWebSearch) {
        AlertDialog(
            onDismissRequest = { showWebSearch = false },
            containerColor = Panel,
            title = { Text("Recherche Web", color = Ice) },
            text = {
                Column {
                    Text(
                        "La recherche s'ouvrira dans le navigateur de ton téléphone.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = webSearchInput,
                        onValueChange = { webSearchInput = it },
                        singleLine = true,
                        label = { Text("Que veux-tu chercher ?") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Ice,
                            unfocusedTextColor = Ice,
                            focusedBorderColor = Neon,
                            unfocusedBorderColor = Muted.copy(alpha = 0.45f),
                            focusedLabelColor = Neon,
                            unfocusedLabelColor = Muted,
                            cursorColor = Neon
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = webSearchInput.isNotBlank(),
                    onClick = {
                        send("cherche sur le Web ${webSearchInput.trim()}")
                        webSearchInput = ""
                        showWebSearch = false
                    }
                ) { Text("RECHERCHER", color = Neon) }
            },
            dismissButton = {
                TextButton(onClick = { showWebSearch = false }) {
                    Text("ANNULER", color = Muted)
                }
            }
        )
    }

    if (showInventory) {
        AlertDialog(
            onDismissRequest = { showInventory = false },
            containerColor = Panel,
            title = { Text("Mon stock", color = Ice) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    if (inventory.isEmpty()) {
                        Text("Aucun article pour le moment.", color = Muted, fontSize = 13.sp)
                    } else {
                        inventory.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.name, color = Ice, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    Inventory.setQuantity(context, inventory, item.name, item.quantity - 1)
                                    inventory = Prefs.getInventory(context)
                                }) { Text("−", color = Neon, fontSize = 20.sp) }
                                Text("${item.quantity}", color = Ice, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = {
                                    Inventory.setQuantity(context, inventory, item.name, item.quantity + 1)
                                    inventory = Prefs.getInventory(context)
                                }) { Text("+", color = Neon, fontSize = 20.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inventoryName,
                        onValueChange = { inventoryName = it },
                        singleLine = true,
                        label = { Text("Article") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Ice,
                            unfocusedTextColor = Ice,
                            focusedBorderColor = Neon,
                            unfocusedBorderColor = Muted.copy(alpha = 0.45f),
                            focusedLabelColor = Neon,
                            unfocusedLabelColor = Muted,
                            cursorColor = Neon
                        )
                    )
                    OutlinedTextField(
                        value = inventoryQuantity,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit)) inventoryQuantity = value
                        },
                        singleLine = true,
                        label = { Text("Quantité") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Ice,
                            unfocusedTextColor = Ice,
                            focusedBorderColor = Neon,
                            unfocusedBorderColor = Muted.copy(alpha = 0.45f),
                            focusedLabelColor = Neon,
                            unfocusedLabelColor = Muted,
                            cursorColor = Neon
                        )
                    )
                    Text(
                        "À la voix : « ajoute 2 pommes », « retire 1 pomme », « fixe la quantité de pommes à 5 » ou « liste mon stock ».",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = inventoryName.isNotBlank() && inventoryQuantity.toIntOrNull() != null,
                    onClick = {
                        Inventory.setQuantity(
                            context,
                            inventory,
                            inventoryName,
                            inventoryQuantity.toIntOrNull() ?: 0
                        )
                        inventory = Prefs.getInventory(context)
                        inventoryName = ""
                        inventoryQuantity = "1"
                    }
                ) { Text("ENREGISTRER", color = Neon) }
            },
            dismissButton = {
                TextButton(onClick = { showInventory = false }) {
                    Text("FERMER", color = Muted)
                }
            }
        )
    }

    if (showWakeDisclosure) {
        AlertDialog(
            onDismissRequest = { showWakeDisclosure = false },
            containerColor = Panel,
            title = { Text("Écoute en arrière-plan", color = Ice) },
            text = {
                Text(
                    "Jarvis garde une notification visible et utilise le microphone jusqu'à ce que tu désactives la veille. Android transmet la voix au service de reconnaissance installé sur le téléphone; il peut traiter aussi les paroles entendues avant « Jarvis ». Active ce mode seulement si tu l'acceptes. Android ou l'économie d'énergie peuvent interrompre l'écoute.",
                    color = Muted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWakeDisclosure = false
                    Prefs.setWakeWordDisclosureConsent(context)
                    requestWakeWordPermissions()
                }) { Text("CONTINUER", color = Neon) }
            },
            dismissButton = {
                TextButton(onClick = { showWakeDisclosure = false }) {
                    Text("ANNULER", color = Muted)
                }
            }
        )
    }
}

private fun startRecognition(
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onListeningChanged: (Boolean) -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Jarvis t'écoute…")
    }
    try {
        onListeningChanged(true)
        launcher.launch(intent)
    } catch (e: ActivityNotFoundException) {
        onListeningChanged(false)
        Convo.error = "La reconnaissance vocale n'est pas disponible sur ce téléphone."
    }
}

@Composable
private fun JarvisOrb(
    listening: Boolean,
    speaking: Boolean,
    thinking: Boolean,
    diameter: Dp,
    onClick: () -> Unit
) {
    val active = listening || speaking || thinking
    val rotation = remember { Animatable(0f) }
    val pulse by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = tween(350),
        label = "orbPulse"
    )
    LaunchedEffect(active) {
        if (active) {
            while (true) {
                rotation.animateTo(360f, tween(5_000))
                rotation.snapTo(0f)
            }
        } else {
            rotation.snapTo(0f)
        }
    }
    val glow = if (active) Cyan else Neon

    Box(
        modifier = Modifier
            .size(diameter)
            .shadow(30.dp, CircleShape, ambientColor = glow.copy(alpha = 0.24f), spotColor = glow.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.12f), Void.copy(alpha = 0.3f), Color.Transparent)))
            .border(1.dp, glow.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val center = this.center
            val maxRadius = size.minDimension * 0.48f
            drawCircle(glow.copy(alpha = 0.035f), radius = maxRadius, center = center)
            drawCircle(
                glow.copy(alpha = 0.16f),
                radius = maxRadius * pulse,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                glow.copy(alpha = 0.25f),
                radius = maxRadius * 0.83f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawArc(
                color = glow.copy(alpha = 0.78f),
                startAngle = rotation.value,
                sweepAngle = 92f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    center.x - maxRadius * 0.93f,
                    center.y - maxRadius * 0.93f
                ),
                size = androidx.compose.ui.geometry.Size(maxRadius * 1.86f, maxRadius * 1.86f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = glow.copy(alpha = 0.48f),
                startAngle = -rotation.value * 1.4f,
                sweepAngle = 48f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(
                    center.x - maxRadius * 0.72f,
                    center.y - maxRadius * 0.72f
                ),
                size = androidx.compose.ui.geometry.Size(maxRadius * 1.44f, maxRadius * 1.44f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Image(
            painter = painterResource(R.drawable.jarvis_logo),
            contentDescription = "Emblème Jarvis",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(diameter * 0.62f)
                .clip(CircleShape)
                .border(1.dp, Neon.copy(alpha = 0.52f), CircleShape)
        )
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Panel.copy(alpha = 0.9f))
            .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(title.take(1).uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, color = Ice, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun MessageCard(message: Msg) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (user) Color(0xFF1C3455) else Panel.copy(alpha = 0.9f))
                .border(
                    1.dp,
                    if (user) Neon.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.055f),
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 15.dp, vertical = 11.dp)
        ) {
            Text(
                if (user) "TOI" else "JARVIS",
                color = if (user) Neon.copy(alpha = 0.72f) else Muted,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(message.text, color = Ice.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}
