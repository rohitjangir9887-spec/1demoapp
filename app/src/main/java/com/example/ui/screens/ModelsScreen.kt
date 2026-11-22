package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AiModelSpec
import com.example.data.model.AiModelsCatalog
import com.example.data.model.ModelProvider
import com.example.data.model.ModelSpecialty
import com.example.data.model.UserProfile
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassChip
import com.example.ui.components.IosSquircleIcon
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraMint
import com.example.ui.theme.AuraNimGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.SlateMist

@Composable
fun ModelsScreen(
    profile: UserProfile,
    isNvidiaNimConfigured: Boolean,
    onSelectModel: (String) -> Unit,
    onUpdateSettings: (swarmEnabled: Boolean, maxPowerEnabled: Boolean, autoSearchEnabled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSpecialty by remember { mutableStateOf<ModelSpecialty?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredModels = remember(selectedSpecialty, searchQuery) {
        AiModelsCatalog.allModels.filter { spec ->
            val matchesCat = selectedSpecialty == null || spec.specialty == selectedSpecialty
            val matchesQuery = searchQuery.isBlank() ||
                spec.displayName.contains(searchQuery, ignoreCase = true) ||
                spec.vendor.contains(searchQuery, ignoreCase = true) ||
                spec.id.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero 33-Model Swarm Mesh Card
            item(key = "swarm_hero") {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF0A0F1A),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(listOf(AuraNimGreen.copy(0.6f), AuraCyan.copy(0.6f)))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box {
                        Image(
                            painter = painterResource(id = R.drawable.img_swarm_mesh_bg_1795350293224),
                            contentDescription = "33-Model Neural Prism Mesh",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            ObsidianBg.copy(alpha = 0.45f),
                                            ObsidianBg.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                        )
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = AuraNimGreen.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, AuraNimGreen.copy(alpha = 0.7f))
                                ) {
                                    Text(
                                        text = "${AiModelsCatalog.allModels.size} MODELS • NVIDIA NIM + GEMINI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFBEF264),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "${profile.totalSwarmRuns} runs",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AuraCyan
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Max Power Multi-Model Swarm",
                                style = MaterialTheme.typography.headlineSmall,
                                color = PearlWhite
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isNvidiaNimConfigured) {
                                    "Direct NVIDIA NIM Free Endpoints LIVE + Firebase AI Logic Gemini Search & Vision."
                                } else {
                                    "Live Firebase AI Logic Swarm active. Add NVIDIA_API_KEY in AI Studio Secrets for direct NIM endpoints."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMist
                            )

                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CompactPowerToggle(
                                    title = "Auto Swarm",
                                    checked = profile.swarmEnabled,
                                    accent = AuraNimGreen,
                                    onCheckedChange = {
                                        onUpdateSettings(it, profile.maxPowerEnabled, profile.autoSearchEnabled)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("models_auto_swarm_toggle")
                                )
                                CompactPowerToggle(
                                    title = "Max Power",
                                    checked = profile.maxPowerEnabled,
                                    accent = AuraAmber,
                                    onCheckedChange = {
                                        onUpdateSettings(profile.swarmEnabled, it, profile.autoSearchEnabled)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("models_max_power_toggle")
                                )
                            }
                        }
                    }
                }
            }

            // Auto-Orchestrator Pin Card
            item(key = "auto_swarm_selector") {
                val isAutoSelected = profile.preferredModelId == AiModelsCatalog.AUTO_SWARM_ID
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_auto_swarm_card"),
                    cornerRadius = 22.dp,
                    accentBorderColor = if (isAutoSelected) AuraAmber else null,
                    onClick = { onSelectModel(AiModelsCatalog.AUTO_SWARM_ID) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IosSquircleIcon(
                            gradientStart = AuraAmber,
                            gradientEnd = AuraNimGreen,
                            size = 48.dp,
                            cornerRadius = 14.dp,
                            icon = Icons.Rounded.AutoAwesome,
                            contentDescription = "Auto Best Model Router"
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Orchestrator (Best of 33)",
                                style = MaterialTheme.typography.titleMedium,
                                color = PearlWhite
                            )
                            Text(
                                text = "Automatically teams up the top 3 models per prompt at Max Power",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMist
                            )
                        }
                        if (isAutoSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Selected",
                                tint = AuraAmber,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Search & Specialty Filter Row
            item(key = "model_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search 33 models (Nemotron, DeepSeek, Qwen, Llama...)") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = SlateMist) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraCyan,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = PearlWhite,
                            unfocusedTextColor = PearlWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("model_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassChip(
                            label = "All (${AiModelsCatalog.allModels.size})",
                            selected = selectedSpecialty == null,
                            onClick = { selectedSpecialty = null },
                            accentColor = AuraCyan
                        )
                        ModelSpecialty.entries.forEach { sp ->
                            GlassChip(
                                label = sp.label,
                                selected = selectedSpecialty == sp,
                                onClick = { selectedSpecialty = if (selectedSpecialty == sp) null else sp },
                                accentColor = AuraNimGreen
                            )
                        }
                    }
                }
            }

            // All 33 Models Cards
            items(filteredModels, key = { it.id }) { model ->
                ModelSquircleCard(
                    spec = model,
                    isSelected = profile.preferredModelId == model.id,
                    onClick = { onSelectModel(model.id) }
                )
            }
        }
    }
}

@Composable
private fun CompactPowerToggle(
    title: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurfaceElevated.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, if (checked) accent.copy(0.6f) else ObsidianCardBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = PearlWhite
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = accent
                )
            )
        }
    }
}

@Composable
private fun ModelSquircleCard(
    spec: AiModelSpec,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val startColor = Color(spec.accentHexStart)
    val endColor = Color(spec.accentHexEnd)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_card_${spec.shortBadge}"),
        cornerRadius = 22.dp,
        accentBorderColor = if (isSelected) startColor else null,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosSquircleIcon(
                    gradientStart = startColor,
                    gradientEnd = endColor,
                    size = 48.dp,
                    cornerRadius = 14.dp,
                    badgeText = spec.shortBadge,
                    contentDescription = spec.displayName
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = spec.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = PearlWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (spec.provider == ModelProvider.NVIDIA_NIM) {
                                AuraNimGreen.copy(alpha = 0.18f)
                            } else {
                                AuraBlue.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text = spec.parameterScale,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (spec.provider == ModelProvider.NVIDIA_NIM) Color(0xFFBEF264) else AuraCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${spec.vendor} • ${spec.specialty.label} • ${spec.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Primary Model",
                        tint = AuraMint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Power & Speed telemetry bars + Capability pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Power", style = MaterialTheme.typography.labelSmall, color = SlateMist)
                        Text("${spec.powerScore}", style = MaterialTheme.typography.labelSmall, color = PearlWhite)
                    }
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { spec.powerScore / 100f },
                        color = startColor,
                        trackColor = ObsidianSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speed", style = MaterialTheme.typography.labelSmall, color = SlateMist)
                        Text("${spec.speedScore}", style = MaterialTheme.typography.labelSmall, color = PearlWhite)
                    }
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { spec.speedScore / 100f },
                        color = AuraCyan,
                        trackColor = ObsidianSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (spec.supportsVision) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = "Supports Vision",
                            tint = AuraAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (spec.supportsSearch) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = "Supports Google Search",
                            tint = AuraBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Swarm Compatible",
                        tint = AuraNimGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
