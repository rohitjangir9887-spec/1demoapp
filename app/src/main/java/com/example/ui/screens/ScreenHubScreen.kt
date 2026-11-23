package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TouchApp
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.accessibility.JulieAccessibilityController
import com.example.data.local.MemoryNote
import com.example.data.local.MemoryVaultStore
import com.example.data.model.AiModelsCatalog
import com.example.data.model.UserProfile
import com.example.data.remote.ImageKitAsset
import com.example.data.remote.ImageKitClient
import com.example.data.remote.ImageKitConfig
import com.example.data.remote.MongoAuthMode
import com.example.data.remote.MongoDbClient
import com.example.data.remote.MongoDbConfig
import com.example.overlay.JulieFloatingBubbleService
import com.example.screen.ScreenCaptureService
import com.example.screen.ScreenCaptureState
import com.example.screen.ScreenCaptureStore
import com.example.tools.ToolActionLog
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassChip
import com.example.ui.components.IosSquircleIcon
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraMint
import com.example.ui.theme.AuraNimGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.SlateMist
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenHubScreen(
    screenState: ScreenCaptureState,
    profile: UserProfile,
    isSignedIn: Boolean,
    userEmail: String?,
    completedTaskCount: Int,
    imageKitConfig: ImageKitConfig,
    imageKitAssets: List<ImageKitAsset>,
    isUploadingImageKit: Boolean,
    mongoConfig: MongoDbConfig,
    isSyncingMongo: Boolean,
    memoryNotes: List<MemoryNote>,
    toolActionLogs: List<ToolActionLog>,
    onAnalyzeScreenInChat: (String) -> Unit,
    onUploadScreenToImageKit: () -> Unit,
    onTestImageKit: () -> Unit,
    onDeleteImageKitAsset: (String) -> Unit,
    onTestMongo: () -> Unit,
    onSyncMongoNow: () -> Unit,
    onAddMemoryNote: (String, String) -> Unit,
    onToggleMemoryPin: (String) -> Unit,
    onDeleteMemoryNote: (String) -> Unit,
    onClearMemoryVault: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var screenQuestion by remember {
        mutableStateOf("Explain what is on my live screen and tell me the best next action.")
    }

    // ImageKit local form state
    var ikUrlEndpoint by remember(imageKitConfig.urlEndpoint) { mutableStateOf(imageKitConfig.urlEndpoint) }
    var ikPublicKey by remember(imageKitConfig.publicKey) { mutableStateOf(imageKitConfig.publicKey) }
    var ikPrivateKey by remember { mutableStateOf("") }
    var ikFolder by remember(imageKitConfig.folder) { mutableStateOf(imageKitConfig.folder) }
    var ikStatus by remember { mutableStateOf("") }

    // MongoDB local form state
    var mongoUrl by remember(mongoConfig.endpointUrl) { mutableStateOf(mongoConfig.endpointUrl) }
    var mongoDataSource by remember(mongoConfig.dataSource) { mutableStateOf(mongoConfig.dataSource) }
    var mongoDatabase by remember(mongoConfig.database) { mutableStateOf(mongoConfig.database) }
    var mongoBearerMode by remember(mongoConfig.authMode) { mutableStateOf(mongoConfig.authMode == MongoAuthMode.BEARER) }
    var mongoAutoSync by remember(mongoConfig.autoSyncEnabled) { mutableStateOf(mongoConfig.autoSyncEnabled) }
    var mongoSecret by remember { mutableStateOf("") }
    var mongoStatus by remember { mutableStateOf("") }

    // Memory Vault quick input
    var memoryTitle by remember { mutableStateOf("") }
    var memoryContent by remember { mutableStateOf("") }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.start(context, result.resultCode, result.data!!)
        } else {
            ScreenCaptureStore.setSharingActive(false, "Screen share permission declined.")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        val scaled = scaleBitmapForAi(decoded, 1280)
                        ScreenCaptureStore.updateCapturedFrame(scaled, "Picked Photo", isLiveStreamFrame = false)
                    }
                }
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "screenPulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (screenState.isSharingActive) 1.12f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Live Screen, Cloud & Vault",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PearlWhite
                )
                Text(
                    text = "Real-time 1–3 FPS Screen Share • ImageKit CDN Storage • MongoDB Cloud • Memory Vault",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMist
                )
            }

            // 1. Live Continuous Screen Share Control Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 26.dp,
                accentBorderColor = if (screenState.isSharingActive) AuraCoral else AuraBlue
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    (if (screenState.isSharingActive) AuraCoral else AuraBlue).copy(alpha = 0.18f)
                                )
                        )
                        IosSquircleIcon(
                            gradientStart = if (screenState.isSharingActive) AuraCoral else AuraBlue,
                            gradientEnd = if (screenState.isSharingActive) AuraAmber else AuraCyan,
                            size = 60.dp,
                            cornerRadius = 18.dp,
                            icon = if (screenState.isSharingActive) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                            contentDescription = "Live Screen Share"
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    val isTrulyLive = screenState.isSharingActive && screenState.frameCount > 0L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isTrulyLive) "LIVE Stream Active" else if (screenState.isSharingActive) "Starting Stream..." else "Live Screen Share",
                            style = MaterialTheme.typography.titleLarge,
                            color = PearlWhite
                        )
                        if (isTrulyLive) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = AuraCoral.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, AuraCoral)
                            ) {
                                Text(
                                    text = "LIVE #${screenState.frameCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraCoral,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    val timestampStr = if (screenState.lastCapturedTimestampMs > 0L) {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(screenState.lastCapturedTimestampMs))
                    } else "—"
                    Text(
                        text = "${screenState.statusMessage} • Updated $timestampStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMist
                    )

                    Spacer(Modifier.height(12.dp))

                    // Configurable 1–3 FPS selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Stream FPS:", style = MaterialTheme.typography.labelSmall, color = SlateMist)
                        listOf(1, 2, 3).forEach { fps ->
                            GlassChip(
                                label = "$fps FPS",
                                selected = screenState.targetFps == fps,
                                onClick = { ScreenCaptureService.setTargetFps(context, fps) },
                                accentColor = AuraCoral
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (screenState.isSharingActive) {
                                    ScreenCaptureService.stop(context)
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (screenState.isSharingActive) AuraCoral else AuraBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("start_stop_screen_share_button")
                        ) {
                            Icon(
                                imageVector = if (screenState.isSharingActive) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (screenState.isSharingActive) "STOP" else "Start Live")
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, AuraCyan.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pick_screenshot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = AuraCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Pick Photo", color = AuraCyan)
                        }
                    }

                    if (screenState.isSharingActive) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { ScreenCaptureService.captureFrame(context) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AuraMint.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("capture_live_frame_button")
                        ) {
                            Icon(Icons.Rounded.CameraAlt, null, tint = AuraMint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Force Fresh Frame Now", color = AuraMint)
                        }
                    }
                }
            }

            // 2. Captured Frame Vision Preview + ImageKit Upload
            screenState.latestBitmap?.let { bitmap ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    accentBorderColor = AuraMint
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Screen Preview (${screenState.resolutionLabel})",
                                style = MaterialTheme.typography.titleMedium,
                                color = PearlWhite
                            )
                            Text(
                                text = screenState.captureSourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraMint
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Captured screen preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianSurfaceElevated)
                                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = screenQuestion,
                            onValueChange = { screenQuestion = it },
                            label = { Text("Ask Julie Vision about this screen") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuraMint,
                                unfocusedBorderColor = ObsidianCardBorder,
                                focusedTextColor = PearlWhite,
                                unfocusedTextColor = PearlWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onAnalyzeScreenInChat(screenQuestion) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AuraNimGreen,
                                    contentColor = Color(0xFF071400)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("analyze_screen_button")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Analyze Vision", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                enabled = !isUploadingImageKit,
                                onClick = onUploadScreenToImageKit,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, AuraCyan.copy(0.6f)),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("upload_screen_imagekit_button")
                            ) {
                                if (isUploadingImageKit) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AuraCyan)
                                } else {
                                    Icon(Icons.Rounded.CloudUpload, null, tint = AuraCyan, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("ImageKit", color = AuraCyan)
                                }
                            }
                        }
                    }
                }
            }

            // 2B. Draggable Floating Julie Bubble, Animated AI Pointer & Optional Accessibility Control
            val isOverlayRunning by JulieFloatingBubbleService.isOverlayRunning.collectAsStateWithLifecycle()
            val accessibilityState by JulieAccessibilityController.state.collectAsStateWithLifecycle()
            val canDrawOverlay = JulieFloatingBubbleService.canDrawOverlays(context)
            var assistStatus by remember { mutableStateOf("") }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentBorderColor = if (isOverlayRunning || accessibilityState.isConnected) AuraCyan.copy(0.65f) else ObsidianCardBorder
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Layers, null, tint = AuraCyan, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Floating Julie Bubble & AI Control",
                                style = MaterialTheme.typography.titleMedium,
                                color = PearlWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (isOverlayRunning) "Bubble ON" else "Bubble OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverlayRunning) AuraMint else SlateMist
                        )
                    }

                    Text(
                        text = "Draggable floating orb with mini chat, live status, visible AI gesture pointer, and instant STOP. Accessibility is strictly optional, never reads passwords, and never bypasses the lockscreen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMist
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!canDrawOverlay) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    runCatching { context.startActivity(intent) }
                                    assistStatus = "Enable 'Display over other apps' for Julie AI, then tap Launch Bubble."
                                } else if (isOverlayRunning) {
                                    JulieFloatingBubbleService.stop(context)
                                    assistStatus = "Floating Julie Bubble closed."
                                } else {
                                    JulieFloatingBubbleService.start(context)
                                    assistStatus = "Floating Julie Orb active! Tap orb for mini chat or drag to edge."
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOverlayRunning) AuraCoral else AuraCyan,
                                contentColor = if (isOverlayRunning) PearlWhite else Color(0xFF00141A)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("toggle_floating_bubble_button")
                        ) {
                            Text(
                                text = when {
                                    !canDrawOverlay -> "Grant Overlay"
                                    isOverlayRunning -> "Stop Bubble"
                                    else -> "Launch Bubble"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            enabled = canDrawOverlay,
                            onClick = {
                                val dm = context.resources.displayMetrics
                                JulieFloatingBubbleService.showAnimatedPointer(
                                    context = context,
                                    x = dm.widthPixels * 0.5f,
                                    y = dm.heightPixels * 0.42f,
                                    label = "Julie AI Pointer"
                                )
                                assistStatus = "Animated AI Pointer displayed at screen center."
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AuraNimGreen.copy(0.65f)),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("test_ai_pointer_button")
                        ) {
                            Icon(Icons.Rounded.TouchApp, null, tint = AuraNimGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test Pointer", color = AuraNimGreen)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AccessibilityNew,
                                null,
                                tint = if (accessibilityState.isConnected) AuraNimGreen else SlateMist,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (accessibilityState.isConnected) {
                                    "Accessibility: Connected (${accessibilityState.visibleNodeCount} nodes)"
                                } else {
                                    "Accessibility: Off (Optional)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (accessibilityState.isConnected) AuraNimGreen else PearlWhite
                            )
                        }
                        TextButton(
                            onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            },
                            modifier = Modifier.testTag("open_accessibility_settings_button")
                        ) {
                            Text("Settings", color = AuraCyan)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = accessibilityState.isConnected,
                            onClick = {
                                if (accessibilityState.isEmergencyStopped) {
                                    JulieAccessibilityController.resumeFromStop()
                                }
                                assistStatus = JulieAccessibilityController.inspectVisibleUi()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AuraCyan.copy(0.55f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("inspect_ui_button")
                        ) {
                            Text("Inspect UI", color = AuraCyan)
                        }

                        Button(
                            onClick = {
                                JulieAccessibilityController.emergencyStop()
                                ScreenCaptureService.stop(context)
                                JulieFloatingBubbleService.stop(context)
                                assistStatus = "Emergency STOP executed: Screen Share, Bubble & Gestures halted."
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuraCoral),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("emergency_stop_all_button")
                        ) {
                            Icon(Icons.Rounded.StopCircle, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("STOP ALL", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (assistStatus.isNotBlank()) {
                        Text(assistStatus, style = MaterialTheme.typography.labelSmall, color = AuraCyan)
                    }

                    if (accessibilityState.stepLogs.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, ObsidianCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Step-by-Step Control Log",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PearlWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                accessibilityState.stepLogs.take(4).forEach { log ->
                                    Text(
                                        text = "[${log.timestamp}] ${log.action}: ${log.detail}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (log.succeeded) AuraMint else AuraCoral,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryCard(
                    value = "${AiModelsCatalog.allModels.size}",
                    label = "NIM Models",
                    accent = AuraNimGreen,
                    modifier = Modifier.weight(1f)
                )
                TelemetryCard(
                    value = "${profile.totalSwarmRuns}",
                    label = "AI Runs",
                    accent = AuraCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryCard(
                    value = "$completedTaskCount",
                    label = "Tasks Done",
                    accent = AuraAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            // 4. ImageKit.io Cloud Storage Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentBorderColor = if (imageKitConfig.isReady) AuraCyan.copy(0.6f) else ObsidianCardBorder
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudUpload, null, tint = AuraCyan, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ImageKit Cloud Storage",
                                style = MaterialTheme.typography.titleMedium,
                                color = PearlWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (imageKitConfig.isReady) "Configured ✓" else "Not Set",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (imageKitConfig.isReady) AuraMint else SlateMist
                        )
                    }

                    OutlinedTextField(
                        value = ikUrlEndpoint,
                        onValueChange = { ikUrlEndpoint = it },
                        label = { Text("URL Endpoint") },
                        placeholder = { Text("https://ik.imagekit.io/your_id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("imagekit_url_input")
                    )
                    OutlinedTextField(
                        value = ikPublicKey,
                        onValueChange = { ikPublicKey = it },
                        label = { Text("Public Key (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ikPrivateKey,
                        onValueChange = { ikPrivateKey = it },
                        label = { Text(if (imageKitConfig.hasPrivateKey) "Replace Private Key (encrypted)" else "Private Key (private_...)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("imagekit_private_key_input")
                    )
                    OutlinedTextField(
                        value = ikFolder,
                        onValueChange = { ikFolder = it },
                        label = { Text("Upload Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                ImageKitClient.saveConfig(context, ikUrlEndpoint, ikPublicKey, ikPrivateKey, ikFolder)
                                    .onSuccess {
                                        ikPrivateKey = ""
                                        ikStatus = "ImageKit settings encrypted & saved."
                                    }
                                    .onFailure {
                                        ikStatus = it.localizedMessage ?: "Invalid ImageKit settings"
                                    }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AuraCyan, contentColor = Color(0xFF00141A))
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            enabled = imageKitConfig.isReady,
                            onClick = onTestImageKit,
                            border = BorderStroke(1.dp, AuraMint.copy(0.6f))
                        ) {
                            Text("Test", color = AuraMint)
                        }
                        if (imageKitConfig.isReady) {
                            TextButton(onClick = {
                                ImageKitClient.clearConfig(context)
                                ikUrlEndpoint = ""
                                ikPublicKey = ""
                                ikPrivateKey = ""
                                ikStatus = "ImageKit credentials removed."
                            }) {
                                Text("Clear", color = AuraCoral)
                            }
                        }
                    }

                    if (ikStatus.isNotBlank()) {
                        Text(ikStatus, style = MaterialTheme.typography.labelSmall, color = AuraCyan)
                    }

                    if (imageKitAssets.isNotEmpty()) {
                        Text(
                            text = "Uploaded CDN Gallery (${imageKitAssets.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = PearlWhite
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            imageKitAssets.forEach { asset ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ObsidianSurfaceElevated,
                                    border = BorderStroke(1.dp, ObsidianCardBorder),
                                    modifier = Modifier.width(140.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        AsyncImage(
                                            model = asset.thumbnailUrl.ifBlank { asset.url },
                                            contentDescription = asset.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(82.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = asset.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PearlWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("ImageKit URL", asset.url))
                                                    ikStatus = "Copied CDN URL"
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Rounded.ContentCopy, "Copy URL", tint = AuraCyan, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { onDeleteImageKitAsset(asset.fileId) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Rounded.Delete, "Delete asset", tint = AuraCoral, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. MongoDB Cloud Connection Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentBorderColor = if (mongoConfig.isReady) AuraNimGreen.copy(0.6f) else ObsidianCardBorder
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Storage, null, tint = AuraNimGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "MongoDB Cloud Connection",
                                style = MaterialTheme.typography.titleMedium,
                                color = PearlWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (mongoConfig.isReady) "Connected ✓" else "Not Set",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mongoConfig.isReady) AuraNimGreen else SlateMist
                        )
                    }

                    OutlinedTextField(
                        value = mongoUrl,
                        onValueChange = { mongoUrl = it },
                        label = { Text("MongoDB HTTPS Endpoint / Data API URL") },
                        placeholder = { Text("https://data.mongodb-api.com/app/.../endpoint/data/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("mongodb_url_input")
                    )
                    OutlinedTextField(
                        value = mongoSecret,
                        onValueChange = { mongoSecret = it },
                        label = { Text(if (mongoConfig.hasApiKey) "Replace API Key / Bearer Token" else "API Key or Bearer Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("mongodb_secret_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = mongoDataSource,
                            onValueChange = { mongoDataSource = it },
                            label = { Text("Data Source") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mongoDatabase,
                            onValueChange = { mongoDatabase = it },
                            label = { Text("Database") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use Bearer Auth Header", style = MaterialTheme.typography.labelMedium, color = PearlWhite)
                        Switch(
                            checked = mongoBearerMode,
                            onCheckedChange = { mongoBearerMode = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = AuraCyan)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Sync Tasks & Chats", style = MaterialTheme.typography.labelMedium, color = PearlWhite)
                        Switch(
                            checked = mongoAutoSync,
                            onCheckedChange = { mongoAutoSync = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = AuraNimGreen)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                MongoDbClient.saveConfig(
                                    context = context,
                                    endpointUrl = mongoUrl,
                                    apiKeyInput = mongoSecret,
                                    dataSource = mongoDataSource,
                                    database = mongoDatabase,
                                    autoSyncEnabled = mongoAutoSync,
                                    authMode = if (mongoBearerMode) MongoAuthMode.BEARER else MongoAuthMode.API_KEY
                                ).onSuccess {
                                    mongoSecret = ""
                                    mongoStatus = "MongoDB config encrypted & saved."
                                }.onFailure {
                                    mongoStatus = it.localizedMessage ?: "Invalid MongoDB endpoint"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AuraNimGreen, contentColor = Color(0xFF071400))
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            enabled = mongoConfig.isReady,
                            onClick = onTestMongo,
                            border = BorderStroke(1.dp, AuraCyan.copy(0.6f))
                        ) {
                            Text("Test", color = AuraCyan)
                        }
                        OutlinedButton(
                            enabled = mongoConfig.isReady && !isSyncingMongo,
                            onClick = onSyncMongoNow,
                            border = BorderStroke(1.dp, AuraNimGreen.copy(0.6f))
                        ) {
                            if (isSyncingMongo) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AuraNimGreen)
                            } else {
                                Icon(Icons.Rounded.Sync, null, tint = AuraNimGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sync", color = AuraNimGreen)
                            }
                        }
                    }

                    if (mongoStatus.isNotBlank()) {
                        Text(mongoStatus, style = MaterialTheme.typography.labelSmall, color = AuraNimGreen)
                    }
                }
            }

            // 6. User-Controlled Memory Vault & Tool Action Log
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentBorderColor = AuraAmber.copy(0.45f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bookmark, null, tint = AuraAmber, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Julie Memory Vault (${memoryNotes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = PearlWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (memoryNotes.isNotEmpty()) {
                            Row {
                                TextButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Julie Memory Vault", MemoryVaultStore.exportAsJson()))
                                }) {
                                    Text("Export", color = AuraCyan)
                                }
                                TextButton(onClick = onClearMemoryVault) {
                                    Text("Forget All", color = AuraCoral)
                                }
                            }
                        }
                    }
                    Text(
                        text = "Pinned local memories stay on this device and give Julie context automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMist
                    )

                    OutlinedTextField(
                        value = memoryTitle,
                        onValueChange = { memoryTitle = it },
                        label = { Text("Memory title (e.g. Preferred language)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = memoryContent,
                        onValueChange = { memoryContent = it },
                        label = { Text("Detail (e.g. Reply concisely in Hinglish)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = memoryTitle.isNotBlank() || memoryContent.isNotBlank(),
                        onClick = {
                            onAddMemoryNote(memoryTitle, memoryContent)
                            memoryTitle = ""
                            memoryContent = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraAmber, contentColor = Color(0xFF140D00))
                    ) {
                        Text("Save to Memory Vault", fontWeight = FontWeight.Bold)
                    }

                    memoryNotes.take(5).forEach { note ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, if (note.pinned) AuraAmber.copy(0.5f) else ObsidianCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(note.title, style = MaterialTheme.typography.titleSmall, color = PearlWhite)
                                    Text(note.content, style = MaterialTheme.typography.bodySmall, color = SlateMist, maxLines = 2)
                                }
                                TextButton(onClick = { onToggleMemoryPin(note.id) }) {
                                    Text(if (note.pinned) "Pinned" else "Pin", color = AuraAmber)
                                }
                                IconButton(onClick = { onDeleteMemoryNote(note.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.Delete, "Delete memory", tint = AuraCoral, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (toolActionLogs.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "VERIFIED ANDROID TOOL LOG (${toolActionLogs.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateMist,
                            fontWeight = FontWeight.Bold
                        )
                        toolActionLogs.take(4).forEach { log ->
                            Text(
                                text = "• [${log.toolLabel}] ${log.detail}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.succeeded) AuraMint else AuraCoral
                            )
                        }
                    }
                }
            }

            // 7. Optional Google Sign-In / Guest Mode Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentBorderColor = if (isSignedIn) AuraMint.copy(alpha = 0.4f) else AuraBlue.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSignedIn) profile.displayName else "Guest Mode (No Login Required)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PearlWhite
                        )
                        Text(
                            text = if (isSignedIn) {
                                userEmail ?: "Google Cloud Synced"
                            } else {
                                "Saved locally on device • Sign in anytime for cloud sync"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMist
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (isSignedIn) {
                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AuraCoral.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out",
                                tint = AuraCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Sign Out", color = AuraCoral)
                        }
                    } else {
                        Button(
                            onClick = onSignInClick,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuraBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("hub_sign_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Login,
                                contentDescription = "Sign in with Google",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Sign In")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TelemetryCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        accentBorderColor = accent.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SlateMist
            )
        }
    }
}

private fun scaleBitmapForAi(source: Bitmap, maxDim: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (w <= maxDim && h <= maxDim) return source
    val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
    val newW = (w * ratio).toInt().coerceAtLeast(1)
    val newH = (h * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, newW, newH, true)
}
