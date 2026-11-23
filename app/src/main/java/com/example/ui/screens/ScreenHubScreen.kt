package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.os.Build
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.screen.ScreenCaptureService
import com.example.screen.ScreenCaptureState
import com.example.screen.ScreenCaptureStore
import com.example.ui.components.GlassCard
import com.example.ui.components.IosSquircleIcon
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraMint
import com.example.ui.theme.AuraNimGreen
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.SlateMist

@Composable
fun ScreenHubScreen(
    screenState: ScreenCaptureState,
    profile: UserProfile,
    isSignedIn: Boolean,
    userEmail: String?,
    completedTaskCount: Int,
    onAnalyzeScreenInChat: (String) -> Unit,
    onSignInClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var screenQuestion by remember {
        mutableStateOf("Explain what is on my screen and tell me the best next action.")
    }

    // Real Android MediaProjection Screen Share permission launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.start(context, result.resultCode, result.data!!)
        } else {
            ScreenCaptureStore.setSharingActive(false, "Screen share permission declined.")
        }
    }

    // Android 13+ notification permission for foreground screen share indicator
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    // Zero-permission Android Photo Picker for instant screenshot/image analysis (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        val scaled = scaleBitmapForAi(decoded, 1280)
                        ScreenCaptureStore.updateCapturedFrame(scaled, "Picked Screenshot")
                    }
                }
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "screenPulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (screenState.isSharingActive) 1.14f else 1.02f,
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
                    text = "Live Screen Share & Hub",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PearlWhite
                )
                Text(
                    text = "Share your live screen or pick a screenshot for instant multimodal AI help",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMist
                )
            }

            // Live Screen Share Control Card
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
                                .size(82.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    (if (screenState.isSharingActive) AuraCoral else AuraBlue).copy(alpha = 0.18f)
                                )
                        )
                        IosSquircleIcon(
                            gradientStart = if (screenState.isSharingActive) AuraCoral else AuraBlue,
                            gradientEnd = if (screenState.isSharingActive) AuraAmber else AuraCyan,
                            size = 64.dp,
                            cornerRadius = 18.dp,
                            icon = if (screenState.isSharingActive) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                            contentDescription = "Live Screen Share"
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (screenState.isSharingActive) "Live Screen Share Active" else "Share Screen with Aura AI",
                        style = MaterialTheme.typography.titleLarge,
                        color = PearlWhite
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = screenState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMist
                    )

                    Spacer(Modifier.height(16.dp))

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
                            Text(if (screenState.isSharingActive) "Stop Share" else "Start Live Share")
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
                            Text("Pick Screenshot", color = AuraCyan)
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
                            Text("Capture Fresh Screen Frame Now", color = AuraMint)
                        }
                    }
                }
            }

            // Captured Frame Vision Preview & Instant Ask
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
                                text = "Captured Screen Preview",
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
                            label = { Text("Ask Aura Vision about this screen") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuraMint,
                                unfocusedBorderColor = ObsidianCardBorder,
                                focusedTextColor = PearlWhite,
                                unfocusedTextColor = PearlWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onAnalyzeScreenInChat(screenQuestion) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuraNimGreen,
                                contentColor = Color(0xFF071400)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("analyze_screen_button")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Analyze Screen with Vision Swarm",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 24h Telemetry Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryCard(
                    value = "33",
                    label = "AI Models",
                    accent = AuraNimGreen,
                    modifier = Modifier.weight(1f)
                )
                TelemetryCard(
                    value = "${profile.totalSwarmRuns}",
                    label = "Swarm Runs",
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

            // Account & Optional Cloud Sync Card
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
