package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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

private val Void = Color(0xFF050A10)
private val DeepTeal = Color(0xFF071921)
private val Neon = Color(0xFF48F0D0)
private val Ice = Color(0xFFB7FFF2)
private val Muted = Color(0xFF8BA4AB)
private val Panel = Color(0xFF101D25)

class MainActivity : ComponentActivity() {
    private var pendingDeviceAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Voice.init(this)
        Prefs.migrateApiKeys(this)
        Convo.restore(this)
        window.statusBarColor = android.graphics.Color.rgb(5, 10, 16)
        window.navigationBarColor = android.graphics.Color.rgb(5, 10, 16)
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
    var listening by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

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
    val ttsSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Voice.reload(context)
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
                    TextButton(onClick = { Voice.setMuted(!Voice.muted) }) {
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
                        if (WakeWordState.active) WakeWordState.status else "Dis « Jarvis » en gardant la notification",
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
                    Convo.loading -> "JE RÉFLÉCHIS"
                    Voice.speaking -> "JE TE RÉPONDS"
                    listening || (WakeWordState.active && WakeWordState.listening) -> "JE T'ÉCOUTE"
                    WakeWordState.active -> "DIS « JARVIS » OU TOUCHE L'ORB"
                    else -> "TOUCHE L'ORB POUR PARLER"
                },
                color = if (listening || Voice.speaking || Convo.loading) Neon else Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.2.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

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
        var draft by remember(apiKey) { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("Connexion OpenRouter", color = Ice) },
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
                        Text("CONFIGURER LA VOIX ANDROID", color = Neon)
                    }
                    Text("Tonalité ${"%.1f".format(java.util.Locale.FRANCE, Voice.pitch)}", color = Muted, fontSize = 12.sp)
                    Slider(
                        value = Voice.pitch,
                        onValueChange = { Voice.setPitch(it) },
                        valueRange = 0.7f..1.3f,
                        onValueChangeFinished = { Voice.speak("Bonjour, Monsieur.") }
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { Convo.clear(context) }) {
                        Text("Effacer la conversation", color = Color(0xFFFF9E9E))
                    }
                    Text(
                        "L'écoute en arrière-plan nécessite une notification et envoie la voix au service de reconnaissance installé sur le téléphone. Les commandes de réglages et d'applications sont limitées par Android. Les messages IA sont transmis à OpenRouter et à son fournisseur. Une clé dans une APK peut être extraite. Mémoire locale : ${Prefs.getFacts(context).size} élément(s).",
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
    val transition = rememberInfiniteTransition(label = "orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing)),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (listening || speaking || thinking) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label = "pulse"
    )
    val glow = if (listening || speaking || thinking) Neon else Color(0xFF19AAB1)

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
                startAngle = rotation,
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
                startAngle = -rotation * 1.4f,
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
private fun MessageCard(message: Msg) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (user) Color(0xFF103A40) else Panel.copy(alpha = 0.9f))
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
