package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraMint
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.SlateMist
import com.example.voice.VoiceInputState

/**
 * Live audio-reactive voice listening panel shown above the chat composer while recording.
 */
@Composable
fun VoiceListeningPanel(
    voiceState: VoiceInputState,
    handsFreeEnabled: Boolean,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = voiceState.isListening,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("voice_listening_panel"),
            cornerRadius = 20.dp,
            accentBorderColor = AuraCoral
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live 5-bar waveform reactive to real SpeechRecognizer audioRmsDb
                VoiceRmsWaveform(audioLevel = voiceState.audioRmsDb)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = AuraCoral,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (handsFreeEnabled) "HANDS-FREE LISTENING" else "VOICE DICTATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraCoral,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = voiceState.partialTranscript.ifBlank {
                            stringResource(R.string.voice_listening_hint)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = PearlWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onStopListening,
                    shape = CircleShape,
                    color = AuraCoral,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_stop_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = stringResource(R.string.voice_stop_desc),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRmsWaveform(audioLevel: Float) {
    val multipliers = listOf(0.55f, 0.85f, 1.0f, 0.8f, 0.5f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(32.dp)
    ) {
        multipliers.forEachIndexed { idx, factor ->
            val targetHeight = (8f + (22f * audioLevel.coerceIn(0.08f, 1f) * factor)).dp
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "bar_$idx"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(listOf(AuraCoral, AuraAmber))
                    )
            )
        }
    }
}

/**
 * Inline permission rationale / settings prompt when RECORD_AUDIO is denied.
 */
@Composable
fun MicrophonePermissionBanner(
    visible: Boolean,
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("mic_permission_banner"),
            cornerRadius = 18.dp,
            accentBorderColor = AuraAmber
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MicOff,
                        contentDescription = null,
                        tint = AuraAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.mic_permission_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = PearlWhite,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss microphone notice",
                            tint = SlateMist,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = if (isPermanentlyDenied) {
                        stringResource(R.string.mic_permission_denied_permanent)
                    } else {
                        stringResource(R.string.mic_permission_rationale)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMist
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isPermanentlyDenied) {
                        Button(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuraAmber,
                                contentColor = ObsidianBg
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("open_app_settings_button")
                        ) {
                            Icon(Icons.Rounded.Settings, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.mic_open_settings),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Button(
                            onClick = onRequestPermission,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuraBlue,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("grant_mic_permission_button")
                        ) {
                            Text(
                                text = stringResource(R.string.mic_grant_access),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dismissible inline notice when SpeechRecognizer reports an error (e.g., no speech detected).
 */
@Composable
fun VoiceErrorPill(
    errorMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !errorMessage.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurfaceElevated,
            border = BorderStroke(1.dp, AuraCoral.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("voice_error_pill")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuraCyan,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss voice error",
                        tint = SlateMist,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
