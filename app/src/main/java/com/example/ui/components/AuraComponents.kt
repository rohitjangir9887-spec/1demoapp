package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraMint
import com.example.ui.theme.AuraNimGreen
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSpecularTop
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.SlateMist
import com.example.ui.viewmodel.LiveSwarmActivity
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic Island-inspired animated live status capsule at the top of Aura AI.
 * Smoothly morphs width and glows when 33-model swarm, 24h parallel tasks, or Screen Share are active.
 */
@Composable
fun DynamicIslandCapsule(
    liveSwarm: LiveSwarmActivity,
    isScreenSharing: Boolean,
    isWatchdog24hActive: Boolean,
    modelCount: Int = 33,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = liveSwarm.isRunning || liveSwarm.parallelTasksRunning > 0 || isScreenSharing
    val capsuleWidth by animateDpAsState(
        targetValue = if (isExpanded) 316.dp else 214.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandWidth"
    )

    val infinite = rememberInfiniteTransition(label = "islandPulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val accentColor = when {
        isScreenSharing -> AuraCoral
        liveSwarm.isRunning || liveSwarm.parallelTasksRunning > 0 -> AuraCyan
        isWatchdog24hActive -> AuraMint
        else -> SlateMist
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color(0xFF020408),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    accentColor.copy(alpha = if (isExpanded) 0.7f else 0.35f),
                    ObsidianSpecularTop,
                    accentColor.copy(alpha = 0.25f)
                )
            )
        ),
        shadowElevation = 12.dp,
        modifier = modifier
            .width(capsuleWidth)
            .height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = if (isExpanded) pulseAlpha else 0.9f))
                )
                Spacer(Modifier.width(9.dp))
                AnimatedContent(
                    targetState = when {
                        isScreenSharing -> "LIVE SCREEN VISION"
                        liveSwarm.isRunning -> liveSwarm.activeModelName.ifBlank { "Swarm Thinking..." }
                        liveSwarm.parallelTasksRunning > 0 -> "${liveSwarm.parallelTasksRunning} Tasks Running"
                        isWatchdog24hActive -> "24h Ready • $modelCount Models"
                        else -> "Aura Standby"
                    },
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "islandText"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = PearlWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            when {
                isScreenSharing -> Icon(
                    imageVector = Icons.Rounded.ScreenShare,
                    contentDescription = "Screen Share Active",
                    tint = AuraCoral,
                    modifier = Modifier.size(16.dp)
                )
                liveSwarm.isRunning -> Text(
                    text = "${liveSwarm.progressPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraCyan,
                    fontWeight = FontWeight.Bold
                )
                else -> Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Max Power Swarm",
                    tint = AuraAmber,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Authentic iOS-inspired squircle icon tile with luminous diagonal gradient,
 * inner top glass highlight, and tactile spring press physics.
 */
@Composable
fun IosSquircleIcon(
    gradientStart: Color,
    gradientEnd: Color,
    size: Dp = 48.dp,
    cornerRadius: Dp = 14.dp,
    icon: ImageVector? = null,
    badgeText: String? = null,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "squircleScale"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .size(size)
        .scale(scale)
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(gradientStart, gradientEnd)
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))
            ),
            shape = shape
        )
        .let { m ->
            if (onClick != null) {
                m.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else m
        }

    Box(modifier = baseModifier, contentAlignment = Alignment.Center) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(size * 0.48f)
            )
        } else if (badgeText != null) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Translucent Obsidian Glass card with subtle specular top edge.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    containerColor: Color = ObsidianSurface.copy(alpha = 0.88f),
    accentBorderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = if (accentBorderColor != null) {
        Brush.verticalGradient(
            listOf(accentBorderColor.copy(alpha = 0.65f), accentBorderColor.copy(alpha = 0.14f))
        )
    } else {
        Brush.verticalGradient(
            listOf(ObsidianSpecularTop.copy(alpha = 0.25f), ObsidianCardBorder.copy(alpha = 0.1f))
        )
    }

    Surface(
        modifier = modifier.let {
            if (onClick != null) it.clip(shape).clickable(onClick = onClick) else it
        },
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderBrush)
    ) {
        Column(content = content)
    }
}

/**
 * Animated ambient fluid aura canvas rendered subtly behind primary screens.
 */
@Composable
fun AmbientAuraBackground(
    modifier: Modifier = Modifier,
    primaryGlow: Color = AuraBlue,
    secondaryGlow: Color = AuraNimGreen
) {
    val infinite = rememberInfiniteTransition(label = "ambientAura")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auraPhase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val x1 = w * (0.28f + 0.16f * cos(phase))
        val y1 = h * (0.14f + 0.06f * sin(phase))
        val x2 = w * (0.78f + 0.14f * sin(phase * 0.8f))
        val y2 = h * (0.34f + 0.08f * cos(phase * 0.8f))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryGlow.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(x1, y1),
                radius = w * 0.68f
            ),
            radius = w * 0.68f,
            center = Offset(x1, y1)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryGlow.copy(alpha = 0.09f), Color.Transparent),
                center = Offset(x2, y2),
                radius = w * 0.56f
            ),
            radius = w * 0.56f,
            center = Offset(x2, y2)
        )
    }
}

/**
 * Live Multi-Model Swarm Collaboration Visualizer card shown while models collaborate.
 */
@Composable
fun LiveSwarmPipelineBanner(
    liveSwarm: LiveSwarmActivity,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = liveSwarm.isRunning,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
        modifier = modifier
    ) {
        val infinite = rememberInfiniteTransition(label = "swarmShimmer")
        val sweep by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sweep"
        )

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                AuraBlue.copy(alpha = 0.12f * sweep),
                                AuraNimGreen.copy(alpha = 0.14f * (1f - sweep)),
                                AuraCyan.copy(alpha = 0.12f * sweep)
                            )
                        )
                    )
                },
            cornerRadius = 20.dp,
            accentBorderColor = AuraCyan
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Swarm Collaborating",
                            tint = AuraCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = liveSwarm.activeModelName.ifBlank { "Aura Multi-Model Swarm" },
                            style = MaterialTheme.typography.titleSmall,
                            color = PearlWhite
                        )
                    }
                    Text(
                        text = "${liveSwarm.progressPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = AuraNimGreen
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = liveSwarm.stageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMist
                )

                if (liveSwarm.steps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    liveSwarm.steps.takeLast(2).forEach { step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AuraMint)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = step,
                                style = MaterialTheme.typography.labelSmall,
                                color = PearlWhite.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color = AuraBlue,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) accentColor.copy(alpha = 0.22f) else ObsidianSurfaceElevated.copy(alpha = 0.7f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor.copy(alpha = 0.8f) else ObsidianCardBorder
        ),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else SlateMist,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) PearlWhite else SlateMist,
                maxLines = 1
            )
        }
    }
}
