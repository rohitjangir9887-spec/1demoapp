package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.UserProfile
import com.example.screen.ScreenCaptureState
import com.example.screen.ScreenCaptureStore
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassChip
import com.example.ui.components.IosSquircleIcon
import com.example.ui.components.LiveSwarmPipelineBanner
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
import java.util.Locale

@Composable
fun ChatScreen(
    messagesState: DataUiState<List<ChatMessage>>,
    profile: UserProfile,
    liveSwarm: LiveSwarmActivity,
    screenCaptureState: ScreenCaptureState,
    onSendPrompt: (prompt: String, useSearch: Boolean, attachScreen: Boolean) -> Unit,
    onToggleSwarm: (Boolean) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onOpenScreenShareTab: () -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var attachScreenEnabled by remember(screenCaptureState.latestBitmap) {
        mutableStateOf(screenCaptureState.latestBitmap != null)
    }

    // Text-to-Speech engine for reading assistant replies aloud
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
            }
        }
        ttsEngine = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    // Android Speech Recognizer launcher (zero extra permission via RecognizerIntent)
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
            }
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? DataUiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size, liveSwarm.streamingText.length) {
        val target = messages.size + (if (liveSwarm.isRunning) 1 else 0)
        if (target > 0) {
            listState.animateScrollToItem(target - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            // Compact Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Aura Assistant",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PearlWhite
                    )
                    Text(
                        text = if (profile.swarmEnabled && profile.maxPowerEnabled) {
                            "Max Power Auto-Swarm • 33 Models"
                        } else {
                            "Direct Model • Live Cloud Sync"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraCyan
                    )
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
                                        onSpeak = { text ->
                                            ttsEngine?.speak(
                                                text.take(1200),
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                "aura_tts"
                                            )
                                        },
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
                                            if (liveSwarm.streamingText.isNotBlank()) {
                                                GlassCard(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    cornerRadius = 22.dp,
                                                    accentBorderColor = AuraBlue
                                                ) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Text(
                                                            text = "STREAMING LIVE",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AuraCyan
                                                        )
                                                        Spacer(Modifier.height(6.dp))
                                                        Text(
                                                            text = liveSwarm.streamingText,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = PearlWhite
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
            }

            // Attached Screen Frame Preview (when live screen share / screenshot exists)
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
                                    text = "Screen Frame Attached",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PearlWhite
                                )
                                Text(
                                    text = "${screenCaptureState.captureSourceLabel} • Aura Vision ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraCoral
                                )
                            }
                            IconButton(
                                onClick = {
                                    attachScreenEnabled = false
                                    ScreenCaptureStore.clearCapturedFrame()
                                },
                                modifier = Modifier.size(40.dp)
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

            // Quick Mode Toggle Row (Max Swarm, Google Search, Share Screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassChip(
                    label = if (profile.swarmEnabled) "Auto Swarm ON" else "Single Model",
                    selected = profile.swarmEnabled,
                    onClick = { onToggleSwarm(!profile.swarmEnabled) },
                    accentColor = AuraNimGreen,
                    icon = Icons.Rounded.Bolt,
                    modifier = Modifier.testTag("toggle_swarm_chip")
                )
                GlassChip(
                    label = "Google Search",
                    selected = profile.autoSearchEnabled,
                    onClick = { onToggleSearch(!profile.autoSearchEnabled) },
                    accentColor = AuraBlue,
                    icon = Icons.Rounded.Public,
                    modifier = Modifier.testTag("toggle_search_chip")
                )
                GlassChip(
                    label = if (screenCaptureState.isSharingActive) "Screen Live" else "Share Screen",
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
            }

            // Floating Frosted Composer Bar
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = ObsidianSurfaceElevated.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, ObsidianCardBorder),
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
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Aura AI...")
                            }
                            runCatching { voiceLauncher.launch(intent) }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_input_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Voice dictation",
                            tint = AuraCyan
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = if (attachScreenEnabled && screenCaptureState.latestBitmap != null) {
                                    "Ask about your shared screen..."
                                } else {
                                    "Ask Aura anything or search Google..."
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

                    val canSend = inputText.isNotBlank() && !liveSwarm.isRunning
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

@Composable
private fun EmptyChatQuickLaunch(
    onPickPrompt: (String, Boolean) -> Unit,
    onOpenScreenShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IosSquircleIcon(
            gradientStart = AuraBlue,
            gradientEnd = AuraCyan,
            size = 64.dp,
            cornerRadius = 18.dp,
            icon = Icons.Rounded.AutoAwesome,
            contentDescription = "Aura AI Swarm"
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "What shall we build or solve?",
            style = MaterialTheme.typography.headlineSmall,
            color = PearlWhite
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Multiple models collaborate automatically with live Google Search",
            style = MaterialTheme.typography.bodySmall,
            color = SlateMist
        )
        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickPromptTile(
                title = "Google Live Search",
                subtitle = "Today's top AI & tech breakthroughs",
                icon = Icons.Rounded.Public,
                start = AuraBlue,
                end = AuraCyan,
                onClick = {
                    onPickPrompt("Search Google for today's biggest AI & tech news and summarize the top 5 highlights.", true)
                },
                modifier = Modifier.weight(1f)
            )
            QuickPromptTile(
                title = "Code Architect",
                subtitle = "DeepSeek R1 + Qwen Coder swarm",
                icon = Icons.Rounded.Code,
                start = AuraIndigo,
                end = AuraBlue,
                onClick = {
                    onPickPrompt("Design a scalable clean architecture blueprint with Kotlin & Jetpack Compose best practices.", false)
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
                title = "Share Screen Vision",
                subtitle = "Let Aura inspect your screen live",
                icon = Icons.Rounded.ScreenShare,
                start = AuraCoral,
                end = AuraAmber,
                onClick = onOpenScreenShare,
                modifier = Modifier.weight(1f)
            )
            QuickPromptTile(
                title = "24h Productivity Plan",
                subtitle = "Multi-model daily schedule",
                icon = Icons.Rounded.TaskAlt,
                start = AuraNimGreen,
                end = AuraMint,
                onClick = {
                    onPickPrompt("Create a high-output 24-hour study & work schedule with deep focus blocks and automation tips.", false)
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
                            text = "SCREEN CONTEXT ATTACHED",
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
                        IconButton(
                            onClick = { onSpeak(message.content) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Read response aloud",
                                tint = SlateMist,
                                modifier = Modifier.size(18.dp)
                            )
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
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PearlWhite
                    )

                    if (message.searchSources.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "GOOGLE SEARCH SOURCES",
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
