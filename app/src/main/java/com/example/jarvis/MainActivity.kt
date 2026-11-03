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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Void = Color(0xFF070914)
private val DeepTeal = Color(0xFF10132B)
private val Neon = Color(0xFF9184FF)
private val Ice = Color(0xFFE6E8FF)
private val Muted = Color(0xFF9AA0C3)
private val Panel = Color(0xFF151A35)

class MainActivity : ComponentActivity() {
    private var pendingDeviceAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Voice.init(this)
        Prefs.migrateApiKeys(this)
        Convo.restore(this)
        window.statusBarColor = android.graphics.Color.rgb(7, 9, 20)
        window.navigationBarColor = android.graphics.Color.rgb(7, 9, 20)
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
    val listState = rememberLazyListState()
    var apiKey by remember { mutableStateOf(Prefs.getOpenRouterKey(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var showWakeDisclosure by remember { mutableStateOf(false) }
    var showStock by remember { mutableStateOf(false) }
    var showAddStock by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var stockItems by remember { mutableStateOf(Prefs.getStockItems(context)) }

    fun updateStock(items: List<StockItem>) {
        stockItems = items
        Prefs.setStockItems(context, items)
    }

    fun addStockItem(name: String, quantity: Int) {
        val existingIndex = stockItems.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        val updated = if (existingIndex >= 0) {
            stockItems.toMutableList().also { items ->
                val old = items[existingIndex]
                items[existingIndex] = old.copy(quantity = old.quantity + quantity)
            }
        } else {
            stockItems + StockItem(name, quantity)
        }
        updateStock(updated)
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || Convo.loading) return
        scope.launch {
            val answer = Convo.ask(context, apiKey, trimmed)
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
    fun requestVoiceInput() {
        if (listening || Convo.loading) return
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
        if (Convo.messages.isNotEmpty()) {
            listState.animateScrollToItem(Convo.messages.takeLast(8).lastIndex)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepTeal, Void, Color(0xFF020509))))
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
                            .size(42.dp)
                            .clip(CircleShape)
                            .border(1.dp, Neon.copy(alpha = 0.65f), CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "JARVIS",
                            color = Ice,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 4.sp
                        )
                        Text(
                            "PERSONAL INTELLIGENCE",
                            color = Muted,
                            fontSize = 8.sp,
                            letterSpacing = 1.8.sp
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
                        Text("RÉGLAGES", color = Neon, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }
            }

            if (showStock) {
                StockScreen(
                    modifier = Modifier.weight(1f),
                    items = stockItems,
                    onBack = { showStock = false },
                    onAdd = { showAddStock = true },
                    onIncrement = { index ->
                        updateStock(stockItems.toMutableList().also { items ->
                            items[index] = items[index].copy(quantity = items[index].quantity + 1)
                        })
                    },
                    onDecrement = { index ->
                        updateStock(stockItems.toMutableList().also { items ->
                            items[index] = items[index].copy(quantity = (items[index].quantity - 1).coerceAtLeast(0))
                        })
                    },
                    onRemove = { index ->
                        updateStock(stockItems.filterIndexed { itemIndex, _ -> itemIndex != index })
                    }
                )
            } else {
            Spacer(Modifier.height(6.dp))
            Text(
                "TON ASSISTANT, TOUJOURS DISPONIBLE",
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Panel.copy(alpha = 0.82f))
                    .border(1.dp, Neon.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (WakeWordState.active) "MODE VEILLE ACTIF" else "MODE VEILLE",
                        color = if (WakeWordState.active) Neon else Ice,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        if (WakeWordState.active) "Veille active · dis « Jarvis »"
                        else "Active la veille ou touche le micro pour parler",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = WakeWordState.active,
                    onCheckedChange = { setWakeWordEnabled(it) }
                )
            }

            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(0.94f),
                contentAlignment = Alignment.Center
            ) {
                JarvisOrb(
                    listening = listening,
                    speaking = Voice.speaking,
                    thinking = Convo.loading,
                    diameter = maxHeight.coerceIn(150.dp, 250.dp),
                    onClick = { requestVoiceInput() }
                )
            }

            Text(
                when {
                    Convo.loading -> "JE RÉFLÉCHIS"
                    Voice.speaking -> "JE TE RÉPONDS"
                    listening -> "JE T'ÉCOUTE"
                    WakeWordState.active -> "VEILLE ACTIVE · DIS « JARVIS »"
                    else -> "TOUCHE L'ORB OU LE MICRO POUR PARLER"
                },
                color = if (listening || Voice.speaking || Convo.loading) Neon else Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.2.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NodeShortcut("VOIX", "◉", selected = listening, onClick = { requestVoiceInput() })
                NodeShortcut("STOCK", "▦", selected = false, onClick = { showStock = true })
                NodeShortcut("BATTERIE", "▤", selected = false, onClick = { send("niveau batterie") })
                NodeShortcut("WEB", "⌕", selected = false, onClick = { input = "cherche sur internet " })
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { send("ouvre les paramètres") }) {
                    Text("PARAMÈTRES", color = Neon, fontSize = 9.sp, letterSpacing = 0.5.sp)
                }
                TextButton(onClick = { send("monte le volume") }) {
                    Text("VOLUME +", color = Neon, fontSize = 9.sp, letterSpacing = 0.5.sp)
                }
                TextButton(onClick = { send("mets un minuteur de 5 minutes") }) {
                    Text("MINUTEUR", color = Neon, fontSize = 9.sp, letterSpacing = 0.5.sp)
                }
            }

            if (Convo.error != null) {
                Text(
                    text = Convo.error.orEmpty(),
                    color = Color(0xFFFF9E9E),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(Convo.messages.takeLast(8)) { _, message ->
                    MessageCard(message)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
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
                    onClick = { requestVoiceInput() },
                    enabled = !listening && !Convo.loading,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Panel,
                        contentColor = Neon,
                        disabledContainerColor = Panel.copy(alpha = 0.55f),
                        disabledContentColor = Muted
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Parler avec le microphone" },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    MicGlyph()
                }
                Spacer(Modifier.width(8.dp))
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
    }

    if (showSettings) {
        var draft by remember(apiKey) { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("Réglages Jarvis", color = Ice) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Réponses IA via openrouter/free : limites et disponibilité varient selon le compte et le modèle. Vérifie les conditions dans OpenRouter; aucun service gratuit n'est garanti.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text("Clé API OpenRouter") },
                        placeholder = { Text("sk-or-v1-…") },
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
                        "Veille = microphone actif, même avant « Jarvis ». Android privilégie la reconnaissance hors ligne si disponible; sinon son service vocal peut traiter les extraits. Les messages IA sont transmis à OpenRouter et à son fournisseur. Une clé dans une APK peut être extraite. Mémoire locale : ${Prefs.getFacts(context).size} élément(s).",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    apiKey = draft.trim()
                    Prefs.setOpenRouterKey(context, apiKey)
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

    if (showAddStock) {
        AddStockDialog(
            onDismiss = { showAddStock = false },
            onAdd = { name, quantity ->
                addStockItem(name, quantity)
                showAddStock = false
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
    val glow = if (active) Neon else Color(0xFF5C67D8)

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
private fun NodeShortcut(
    title: String,
    symbol: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pulse by rememberInfiniteTransition(label = "nodePulse").animateFloat(
        initialValue = 0.72f,
        targetValue = 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_500, delayMillis = title.length * 110),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nodePulseAlpha"
    )
    val emphasis by animateFloatAsState(
        targetValue = if (selected) 1f else pulse,
        animationSpec = tween(300),
        label = "nodeEmphasis"
    )
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Neon.copy(alpha = 0.08f * emphasis))
                .border(1.dp, Neon.copy(alpha = 0.42f * emphasis), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = Neon.copy(alpha = emphasis), fontSize = 21.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(title, color = Muted.copy(alpha = emphasis), fontSize = 8.sp, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun StockScreen(
    modifier: Modifier = Modifier,
    items: List<StockItem>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onIncrement: (Int) -> Unit,
    onDecrement: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("‹ ACCUEIL", color = Neon) }
            Text("STOCK", color = Ice, fontSize = 17.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onAdd) { Text("+ AJOUTER", color = Neon, fontSize = 10.sp) }
        }
        Text(
            "${items.size} article(s) · quantités enregistrées sur cet appareil",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Ton stock est vide.\nTouche + AJOUTER pour commencer.", color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Panel.copy(alpha = 0.94f))
                            .border(1.dp, Neon.copy(alpha = 0.13f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Ice, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(if (item.quantity == 0) "ÉPUISÉ" else "Quantité : ${item.quantity}", color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onDecrement(index) }, enabled = item.quantity > 0) {
                            Text("−", color = if (item.quantity > 0) Neon else Muted, fontSize = 20.sp)
                        }
                        Text(item.quantity.toString(), color = Ice, fontSize = 15.sp)
                        TextButton(onClick = { onIncrement(index) }) {
                            Text("+", color = Neon, fontSize = 20.sp)
                        }
                        TextButton(onClick = { onRemove(index) }) {
                            Text("×", color = Color(0xFFFF9E9E), fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddStockDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    val parsedQuantity = quantity.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Ajouter au stock", color = Ice) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nom de l’article") },
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
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { value -> if (value.all(Char::isDigit)) quantity = value },
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), parsedQuantity ?: 0) },
                enabled = name.isNotBlank() && parsedQuantity != null && parsedQuantity > 0
            ) { Text("AJOUTER", color = Neon) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ANNULER", color = Muted) }
        }
    )
}

@Composable
private fun MicGlyph() {
    Canvas(Modifier.size(19.dp)) {
        val stroke = 1.8.dp.toPx()
        val micWidth = size.width * 0.34f
        val micLeft = (size.width - micWidth) / 2f
        val micTop = size.height * 0.08f
        val micHeight = size.height * 0.54f
        drawRoundRect(
            color = Neon,
            topLeft = androidx.compose.ui.geometry.Offset(micLeft, micTop),
            size = androidx.compose.ui.geometry.Size(micWidth, micHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(micWidth / 2f)
        )
        drawArc(
            color = Neon,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.28f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.5f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            Neon,
            androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.76f),
            androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.92f),
            stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            Neon,
            androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.92f),
            androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.92f),
            stroke,
            cap = StrokeCap.Round
        )
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
                .background(if (user) Color(0xFF25244E) else Panel.copy(alpha = 0.9f))
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
