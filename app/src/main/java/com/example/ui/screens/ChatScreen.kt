package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ai.RequestDiagnostics
import com.example.data.ai.RoutingConfig
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.UserProfile
import com.example.screen.ScreenCaptureState
import com.example.screen.ScreenCaptureStore
import com.example.tools.PendingToolAction
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassChip
import com.example.ui.components.IosSquircleIcon
import com.example.ui.components.LiveSwarmPipelineBanner
import com.example.ui.components.MarkdownContent
import com.example.ui.components.MicrophonePermissionBanner
import com.example.ui.components.VoiceErrorPill
import com.example.ui.components.VoiceListeningPanel
import com.example.voice.JulieSpeechSynthesizer
import kotlinx.coroutines.launch
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraIndigo
import com.example.ui.theme.AuraMint
import com.example.ui.theme.AuraNimGreen
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.SlateMist
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.LiveSwarmActivity
import com.example.voice.VoiceCommandResult
import com.example.voice.VoiceInputController
import java.util.Locale

@Composable
fun ChatScreen(
    messagesState: DataUiState<List<ChatMessage>>,
    profile: UserProfile,
    liveSwarm: LiveSwarmActivity,
    routingConfig: RoutingConfig,
    lastDiagnostics: RequestDiagnostics?,
    pendingToolConfirmation: PendingToolAction?,
    screenCaptureState: ScreenCaptureState,
    onSendPrompt: (prompt: String, useSearch: Boolean, attachScreen: Boolean) -> Unit,
    onCancelGeneration: () -> Unit,
    onInitiateVoiceTask: (title: String, prompt: String) -> Unit,
    onTriggerToolIntent: (PendingToolAction) -> Unit,
    onConfirmPendingTool: () -> Unit,
    onDismissPendingTool: () -> Unit,
    onToggleSwarm: (Boolean) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onOpenScreenShareTab: () -> Unit,
    onClearChat: () -> Unit,
    isCloudSynced: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var handsFreeAutoSend by remember { mutableStateOf(true) }
    var useHindiVoice by remember { mutableStateOf(false) }
    var attachScreenEnabled by remember(screenCaptureState.latestBitmap) {
        mutableStateOf(screenCaptureState.latestBitmap != null)
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showMicPermissionBanner by remember { mutableStateOf(false) }
    var isMicPermanentlyDenied by remember { mutableStateOf(false) }

    // High-fidelity speech synthesizer with sentence-chunked streaming, Hindi/English locale & audio focus
    val speechSynthesizer = remember(context) { JulieSpeechSynthesizer(context) }
    val speechUiState by speechSynthesizer.uiState.collectAsStateWithLifecycle()

    DisposableEffect(speechSynthesizer) {
        onDispose { speechSynthesizer.destroy() }
    }

    fun speakResponse(text: String) {
        speechSynthesizer.speak(text, preferHindi = useHindiVoice)
    }

    // Incremental streaming speech: speaks sentences progressively as tokens arrive from AI
    LaunchedEffect(liveSwarm.streamingText) {
        if (liveSwarm.isRunning && liveSwarm.streamingText.isNotBlank()) {
            speechSynthesizer.onStreamingTokens(liveSwarm.streamingText, preferHindi = useHindiVoice)
        } else if (!liveSwarm.isRunning) {
            speechSynthesizer.resetStreamingBuffer()
        }
    }

    val handleVoiceTranscript: (String) -> Unit = { spoken ->
        val clean = spoken.trim()
        if (clean.isNotEmpty()) {
            if (handsFreeAutoSend && !liveSwarm.isRunning) {
                when (val parsed = VoiceInputController.parseVoiceCommand(clean)) {
                    is VoiceCommandResult.TaskInitiation -> {
                        onInitiateVoiceTask(parsed.title, parsed.prompt)
                    }
                    is VoiceCommandResult.ToolIntent -> {
                        onTriggerToolIntent(parsed.action)
                    }
                    is VoiceCommandResult.ChatPrompt -> {
                        onSendPrompt(
                            parsed.text,
                            profile.autoSearchEnabled,
                            attachScreenEnabled && screenCaptureState.latestBitmap != null
                        )
                    }
                }
            } else {
                inputText = if (inputText.isBlank()) clean else "$inputText $clean"
            }
        }
    }

    val latestHandleVoiceTranscript by rememberUpdatedState(handleVoiceTranscript)

    val fallbackVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                latestHandleVoiceTranscript(spoken)
            }
        }
    }

    val voiceController = remember(context) {
        VoiceInputController(
            context = context.applicationContext,
            onFinalTranscript = { transcript -> latestHandleVoiceTranscript(transcript) },
            onFallbackToActivityRecognizer = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        if (useHindiVoice) "hi-IN" else Locale.getDefault().toLanguageTag()
                    )
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Julie (जुली)…")
                }
                runCatching { fallbackVoiceLauncher.launch(intent) }
            }
        )
    }

    DisposableEffect(voiceController) {
        onDispose { voiceController.destroy() }
    }

    val voiceState by voiceController.state.collectAsStateWithLifecycle()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            showMicPermissionBanner = false
            isMicPermanentlyDenied = false
            speechSynthesizer.stop() // Barge-in: silence TTS when mic activates
            voiceController.startListening(if (useHindiVoice) "hi-IN" else Locale.getDefault().toLanguageTag())
        } else {
            val activity = context as? Activity
            val canAskAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.RECORD_AUDIO
                )
            } ?: true
            isMicPermanentlyDenied = !canAskAgain
            showMicPermissionBanner = true
        }
    }

    val onVoiceButtonClicked: () -> Unit = {
        // Barge-in: always stop TTS immediately when user taps mic
        speechSynthesizer.stop()
        val currentlyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = currentlyGranted

        if (currentlyGranted) {
            showMicPermissionBanner = false
            if (voiceState.isListening) {
                voiceController.stopListening()
            } else {
                voiceController.startListening(if (useHindiVoice) "hi-IN" else Locale.getDefault().toLanguageTag())
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val micPulseInfinite = rememberInfiniteTransition(label = "micPulse")
    val micPulseScale by micPulseInfinite.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceState.isListening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val messages = (messagesState as? DataUiState.Success)?.data.orEmpty()

    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            if (total == 0) true
            else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 2
            }
        }
    }

    // Auto-scroll to latest message when user sends or a new message arrives
    LaunchedEffect(messages.size) {
        val target = messages.size + (if (liveSwarm.isRunning) 1 else 0)
        if (target > 0) {
            listState.animateScrollToItem(target - 1)
        }
    }

    // Follow streaming text incrementally ONLY IF the user is already near the bottom
    var lastScrollTokenCount by remember { mutableStateOf(0) }
    LaunchedEffect(liveSwarm.streamingText.length) {
        val len = liveSwarm.streamingText.length
        if (liveSwarm.isRunning && isNearBottom && (len - lastScrollTokenCount > 40 || len == 0)) {
            lastScrollTokenCount = len
            val target = messages.size + 1
            if (target > 0) {
                listState.scrollToItem(target - 1)
            }
        }
    }

    // Ensure the end of response is visible when streaming finishes
    LaunchedEffect(liveSwarm.isRunning) {
        if (!liveSwarm.isRunning && isNearBottom) {
            val target = messages.size
            if (target > 0) {
                listState.animateScrollToItem(target - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            // Compact Julie Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Julie AI • जुली",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PearlWhite
                    )
                    Text(
                        text = "${routingConfig.profile.label} • ${routingConfig.orchestrationMode.label} • ${if (isCloudSynced) "Synced" else "Local"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lastDiagnostics != null) {
                        Surface(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Julie Diagnostics", lastDiagnostics.toCopyableReport()))
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, ObsidianCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.ContentCopy, "Copy Diagnostics", tint = AuraNimGreen, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${lastDiagnostics.totalMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraNimGreen
                                )
                            }
                        }
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear chat history",
                                tint = SlateMist
                            )
                        }
                    }
                }
            }

            // Explicit Tool Confirmation Banner (for sensitive actions like Dialer / Alarm)
            AnimatedVisibility(visible = pendingToolConfirmation != null, enter = fadeIn()) {
                pendingToolConfirmation?.let { pending ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        cornerRadius = 18.dp,
                        accentBorderColor = AuraAmber
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Confirm Action: ${pending.title}",
                                style = MaterialTheme.typography.titleSmall,
                                color = PearlWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${pending.type.label} • Julie requires your confirmation before opening system apps.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateMist
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onConfirmPendingTool,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AuraAmber,
                                        contentColor = Color(0xFF140D00)
                                    )
                                ) {
                                    Text("Confirm & Open", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = onDismissPendingTool) {
                                    Text("Cancel", color = SlateMist)
                                }
                            }
                        }
                    }
                }
            }

            // Messages / Empty Quick-Launch State
            Box(modifier = Modifier.weight(1f)) {
                when (messagesState) {
                    is DataUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AuraCyan)
                        }
                    }

                    is DataUiState.Error -> {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            accentBorderColor = AuraCoral
                        ) {
                            Text(
                                text = messagesState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AuraCoral,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    is DataUiState.Success -> {
                        if (messages.isEmpty() && !liveSwarm.isRunning) {
                            EmptyChatQuickLaunch(
                                onPickPrompt = { prompt, withSearch ->
                                    onSendPrompt(prompt, withSearch, false)
                                },
                                onOpenScreenShare = onOpenScreenShareTab
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { message ->
                                    ChatMessageBubble(
                                        message = message,
                                        onSpeak = { text -> speakResponse(text) },
                                        onOpenLink = { url ->
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                        }
                                    )
                                }

                                if (liveSwarm.isRunning) {
                                    item(key = "live_streaming_bubble") {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LiveSwarmPipelineBanner(liveSwarm = liveSwarm)

                                            if (liveSwarm.thinkingTrace.isNotBlank()) {
                                                GlassCard(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    cornerRadius = 18.dp,
                                                    accentBorderColor = AuraIndigo
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                Icons.Rounded.Psychology,
                                                                contentDescription = "Reasoning trace",
                                                                tint = AuraCyan,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(
                                                                text = "LIVE THINKING TRACE",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = AuraCyan,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = liveSwarm.thinkingTrace.takeLast(520),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = SlateMist
                                                        )
                                                    }
                                                }
                                            }

                                            if (liveSwarm.streamingText.isNotBlank()) {
                                                GlassCard(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    cornerRadius = 22.dp,
                                                    accentBorderColor = AuraBlue
                                                ) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Text(
                                                            text = "STREAMING LIVE (SINGLE-FLIGHT)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AuraCyan
                                                        )
                                                        Spacer(Modifier.height(6.dp))
                                                        MarkdownContent(
                                                            markdown = liveSwarm.streamingText,
                                                            isStreaming = true,
                                                            onOpenLink = { url ->
                                                                runCatching {
                                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Jump to latest message button (visible only when scrolled up)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp, end = 8.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isNearBottom && (messages.isNotEmpty() || liveSwarm.isRunning),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    val target = messages.size + (if (liveSwarm.isRunning) 1 else 0)
                                    if (target > 0) {
                                        listState.animateScrollToItem(target - 1)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = ObsidianSurfaceElevated.copy(alpha = 0.95f),
                            border = BorderStroke(1.dp, AuraCyan.copy(alpha = 0.65f)),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Jump to latest",
                                    tint = AuraCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Jump to latest",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PearlWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Attached Screen Frame Preview
            AnimatedVisibility(
                visible = screenCaptureState.latestBitmap != null && attachScreenEnabled,
                enter = fadeIn()
            ) {
                screenCaptureState.latestBitmap?.let { bmp ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        cornerRadius = 18.dp,
                        accentBorderColor = AuraCoral
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Attached shared screen frame",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (screenCaptureState.isSharingActive) {
                                        "Live Screen Frame #${screenCaptureState.frameCount}"
                                    } else {
                                        "Screen Frame Attached"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PearlWhite
                                )
                                Text(
                                    text = "${screenCaptureState.captureSourceLabel} • NVIDIA Vision ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraCoral
                                )
                            }
                            IconButton(
                                onClick = {
                                    attachScreenEnabled = false
                                    ScreenCaptureStore.clearCapturedFrame()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Detach screen frame",
                                    tint = SlateMist
                                )
                            }
                        }
                    }
                }
            }

            MicrophonePermissionBanner(
                visible = showMicPermissionBanner && !hasMicPermission,
                isPermanentlyDenied = isMicPermanentlyDenied,
                onRequestPermission = {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onOpenSettings = {
                    val settingsIntent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    runCatching { context.startActivity(settingsIntent) }
                },
                onDismiss = { showMicPermissionBanner = false }
            )

            VoiceListeningPanel(
                voiceState = voiceState,
                handsFreeEnabled = handsFreeAutoSend,
                onStopListening = { voiceController.stopListening() }
            )

            VoiceErrorPill(
                errorMessage = voiceState.errorMessage,
                onDismiss = { voiceController.clearError() }
            )

            // Quick Mode Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassChip(
                    label = if (useHindiVoice) "हिंदी Voice" else "EN/Hinglish",
                    selected = useHindiVoice,
                    onClick = { useHindiVoice = !useHindiVoice },
                    accentColor = AuraAmber,
                    icon = Icons.Rounded.Translate,
                    modifier = Modifier.testTag("toggle_hindi_voice_chip")
                )
                GlassChip(
                    label = if (handsFreeAutoSend) {
                        stringResource(R.string.voice_hands_free_on)
                    } else {
                        stringResource(R.string.voice_hands_free_off)
                    },
                    selected = handsFreeAutoSend,
                    onClick = { handsFreeAutoSend = !handsFreeAutoSend },
                    accentColor = AuraCyan,
                    icon = Icons.Rounded.Hearing,
                    modifier = Modifier.testTag("toggle_hands_free_chip")
                )
                GlassChip(
                    label = if (profile.swarmEnabled) "Manager Swarm" else "1 Model",
                    selected = profile.swarmEnabled,
                    onClick = { onToggleSwarm(!profile.swarmEnabled) },
                    accentColor = AuraNimGreen,
                    icon = Icons.Rounded.Bolt,
                    modifier = Modifier.testTag("toggle_swarm_chip")
                )
                GlassChip(
                    label = "Web Context",
                    selected = profile.autoSearchEnabled,
                    onClick = { onToggleSearch(!profile.autoSearchEnabled) },
                    accentColor = AuraBlue,
                    icon = Icons.Rounded.Public,
                    modifier = Modifier.testTag("toggle_search_chip")
                )
                GlassChip(
                    label = if (screenCaptureState.isSharingActive) "Screen LIVE" else "Share Screen",
                    selected = screenCaptureState.isSharingActive || (screenCaptureState.latestBitmap != null && attachScreenEnabled),
                    onClick = {
                        if (screenCaptureState.latestBitmap != null) {
                            attachScreenEnabled = !attachScreenEnabled
                        } else {
                            onOpenScreenShareTab()
                        }
                    },
                    accentColor = AuraCoral,
                    icon = Icons.Rounded.ScreenShare,
                    modifier = Modifier.testTag("chat_screen_share_chip")
                )
                GlassChip(
                    label = "🤖 Google Free AI",
                    selected = false,
                    onClick = { inputText = "Julie! Mujhe Google ke trending Free AI tools ke baare me batao." },
                    accentColor = AuraCyan,
                    icon = Icons.Rounded.AutoAwesome
                )
                GlassChip(
                    label = "🔥 Trending AI",
                    selected = false,
                    onClick = { onSendPrompt("Aaj ke top trending free AI tools kaun se hain? Explain in simple Hinglish.", true, false) },
                    accentColor = AuraAmber,
                    icon = Icons.Rounded.Bolt
                )
                val is24hVoiceRunning by com.example.voice.JulieBackgroundVoiceService.isVoiceServiceRunning.collectAsStateWithLifecycle()
                GlassChip(
                    label = if (is24hVoiceRunning) "24h Voice ON" else "24h Voice OFF",
                    selected = is24hVoiceRunning,
                    onClick = {
                        if (is24hVoiceRunning) {
                            com.example.voice.JulieBackgroundVoiceService.stop(context)
                        } else {
                            com.example.voice.JulieBackgroundVoiceService.start(context)
                        }
                    },
                    accentColor = if (is24hVoiceRunning) AuraNimGreen else SlateMist,
                    icon = Icons.Rounded.Mic
                )
            }

            // Floating Frosted Composer Bar
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = ObsidianSurfaceElevated.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    if (voiceState.isListening) AuraCoral.copy(alpha = 0.7f) else ObsidianCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onVoiceButtonClicked,
                        shape = CircleShape,
                        color = if (voiceState.isListening) {
                            AuraCoral.copy(alpha = 0.24f)
                        } else {
                            AuraCyan.copy(alpha = 0.14f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (voiceState.isListening) AuraCoral else AuraCyan.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .scale(micPulseScale)
                            .testTag("voice_input_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (voiceState.isListening) {
                                    Icons.Rounded.GraphicEq
                                } else {
                                    Icons.Rounded.Mic
                                },
                                contentDescription = if (voiceState.isListening) {
                                    stringResource(R.string.voice_stop_desc)
                                } else {
                                    stringResource(R.string.voice_start_desc)
                                },
                                tint = if (voiceState.isListening) AuraCoral else AuraCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = if (attachScreenEnabled && screenCaptureState.latestBitmap != null) {
                                    "Ask Julie Vision about your live screen..."
                                } else {
                                    "Ask Julie (English / हिंदी / Hinglish)..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateMist
                            )
                        },
                        singleLine = false,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = PearlWhite,
                            unfocusedTextColor = PearlWhite,
                            cursorColor = AuraCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_prompt_input")
                    )

                    Spacer(Modifier.width(4.dp))

                    if (liveSwarm.isRunning) {
                        Surface(
                            onClick = onCancelGeneration,
                            shape = CircleShape,
                            color = AuraCoral,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("stop_chat_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop generation",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        val canSend = inputText.isNotBlank()
                        Surface(
                            onClick = {
                                if (canSend) {
                                    val textToSend = inputText
                                    inputText = ""
                                    onSendPrompt(
                                        textToSend,
                                        profile.autoSearchEnabled,
                                        attachScreenEnabled && screenCaptureState.latestBitmap != null
                                    )
                                }
                            },
                            shape = CircleShape,
                            color = if (canSend) AuraBlue else ObsidianSurface,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_chat_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send prompt",
                                    tint = if (canSend) Color.White else SlateMist,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatQuickLaunch(
    onPickPrompt: (String, Boolean) -> Unit,
    onOpenScreenShare: () -> Unit
) {
    val orbTransition = rememberInfiniteTransition(label = "julieOrbPulse")
    val orbScale by orbTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )
    val glowAlpha by orbTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .scale(orbScale),
                shape = CircleShape,
                color = AuraCyan.copy(alpha = glowAlpha * 0.3f),
                border = BorderStroke(2.dp, AuraNimGreen.copy(alpha = glowAlpha))
            ) {}
            IosSquircleIcon(
                gradientStart = AuraNimGreen,
                gradientEnd = AuraCyan,
                size = 64.dp,
                cornerRadius = 18.dp,
                icon = Icons.Rounded.AutoAwesome,
                contentDescription = "Julie AI"
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "नमस्ते! मैं जुली हूँ — Julie AI",
            style = MaterialTheme.typography.headlineSmall,
            color = PearlWhite
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Google Free AI & NVIDIA Swarm • Live Vision • Hindi Voice Tools",
            style = MaterialTheme.typography.bodySmall,
            color = SlateMist
        )
        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickPromptTile(
                title = "🤖 Google Free AI",
                subtitle = "Gemini 2.0 zero-cost chat",
                icon = Icons.Rounded.AutoAwesome,
                start = AuraCyan,
                end = AuraBlue,
                onClick = {
                    onPickPrompt("Julie, explain how Google Free AI tools help in daily tasks.", false)
                },
                modifier = Modifier.weight(1f)
            )
            QuickPromptTile(
                title = "🔥 Trending AI Tools",
                subtitle = "Top free AI platforms 2026",
                icon = Icons.Rounded.Bolt,
                start = AuraAmber,
                end = AuraCoral,
                onClick = {
                    onPickPrompt("Google par aur internet par top free AI tools kaun kaun se hain aur unhe kaise use karein?", true)
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickPromptTile(
                title = "Live Web Research",
                subtitle = "Today's top AI & NVIDIA news",
                icon = Icons.Rounded.Public,
                start = AuraBlue,
                end = AuraCyan,
                onClick = {
                    onPickPrompt("Summarize today's biggest AI & NVIDIA technology updates in 5 clear bullet points.", true)
                },
                modifier = Modifier.weight(1f)
            )
            QuickPromptTile(
                title = "Code Architect",
                subtitle = "Nemotron Lightning + Manager",
                icon = Icons.Rounded.Code,
                start = AuraIndigo,
                end = AuraBlue,
                onClick = {
                    onPickPrompt("Design a clean MVVM + Coroutines architecture blueprint for a production Android app.", false)
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickPromptTile(
                title = "Live Screen Vision",
                subtitle = "Real-time 1–3 FPS AI stream",
                icon = Icons.Rounded.ScreenShare,
                start = AuraCoral,
                end = AuraAmber,
                onClick = onOpenScreenShare,
                modifier = Modifier.weight(1f)
            )
            QuickPromptTile(
                title = "Daily Action Plan",
                subtitle = "Hindi / English productivity",
                icon = Icons.Rounded.TaskAlt,
                start = AuraNimGreen,
                end = AuraMint,
                onClick = {
                    onPickPrompt("Mere liye ek high-focus daily study & coding schedule banao Hinglish me.", false)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickPromptTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    start: Color,
    end: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            IosSquircleIcon(
                gradientStart = start,
                gradientEnd = end,
                size = 38.dp,
                cornerRadius = 11.dp,
                icon = icon,
                contentDescription = title
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = PearlWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SlateMist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onSpeak: (String) -> Unit,
    onOpenLink: (String) -> Unit
) {
    val context = LocalContext.current
    val isUser = message.roleEnum() == MessageRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(AuraBlue, Color(0xFF005ECB))),
                        shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (message.hasScreenContext) {
                        Text(
                            text = "LIVE SCREEN CONTEXT ATTACHED",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        } else {
            GlassCard(
                modifier = Modifier.fillMaxWidth(0.95f),
                cornerRadius = 22.dp,
                accentBorderColor = if (message.usedWebSearch) AuraBlue else AuraNimGreen
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (message.usedWebSearch) Icons.Rounded.Public else Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = if (message.usedWebSearch) AuraCyan else AuraNimGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = message.modelUsed,
                                style = MaterialTheme.typography.labelMedium,
                                color = AuraCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Julie Response", message.content))
                                    Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy response",
                                    tint = SlateMist,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, message.content)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Julie Response"))
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share response",
                                    tint = SlateMist,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onSpeak(message.content) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = "Read response aloud",
                                    tint = AuraCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (message.swarmModels.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            message.swarmModels.forEach { modelName ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AuraNimGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, AuraNimGreen.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = modelName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFBEF264),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    MarkdownContent(
                        markdown = message.content,
                        isStreaming = false,
                        onOpenLink = onOpenLink
                    )

                    if (message.searchSources.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "LIVE WEB SOURCES",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateMist,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            message.searchSources.forEach { raw ->
                                val parts = raw.split("|", limit = 2)
                                val label = if (parts.size == 2) parts[0] else raw.removePrefix("https://").take(26)
                                val url = if (parts.size == 2) parts[1] else raw
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AuraBlue.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, AuraBlue.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable { onOpenLink(url) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label.take(28),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AuraCyan,
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.OpenInNew,
                                            contentDescription = "Open source",
                                            tint = AuraCyan,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
