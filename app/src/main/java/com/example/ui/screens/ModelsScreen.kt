package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.OrchestrationMode
import com.example.data.ai.RequestDiagnostics
import com.example.data.ai.RoutingConfig
import com.example.data.ai.RoutingProfile
import com.example.data.ai.VendorPolicy
import com.example.data.model.AiModelSpec
import com.example.data.model.AiModelsCatalog
import com.example.data.model.ModelSpecialty
import com.example.data.model.UserProfile
import com.example.data.remote.ModelHealthRecord
import com.example.data.remote.ModelHealthStatus
import com.example.data.remote.NimInferenceSettings
import com.example.data.remote.NimKeyState
import com.example.data.remote.NimKeyStatus
import com.example.data.remote.NvidiaNimClient
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
fun ModelsScreen(
    profile: UserProfile,
    keyState: NimKeyState,
    nimSettings: NimInferenceSettings,
    routingConfig: RoutingConfig,
    modelHealthMap: Map<String, ModelHealthRecord>,
    lastDiagnostics: RequestDiagnostics?,
    isProbingModels: Boolean,
    onSelectModel: (String) -> Unit,
    onUpdateSettings: (swarmEnabled: Boolean, maxPowerEnabled: Boolean, autoSearchEnabled: Boolean) -> Unit,
    onUpdateNimSettings: (NimInferenceSettings) -> Unit,
    onUpdateRoutingConfig: (RoutingProfile, OrchestrationMode, VendorPolicy, Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onProbePrimaryFleet: () -> Unit,
    onProbeSingleModel: (String) -> Unit,
    onToggleModelDisabled: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSpecialty by remember { mutableStateOf<ModelSpecialty?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showRolePools by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var nimApiKeyInput by remember { mutableStateOf("") }
    var nimFeedback by remember { mutableStateOf("") }

    val verifiedCount = remember(modelHealthMap) {
        modelHealthMap.values.count { it.status == ModelHealthStatus.VERIFIED && !it.disabledByUser }
    }

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
            contentPadding = PaddingValues(top = 6.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero NVIDIA NIM Nemotron-First Card
            item(key = "swarm_hero") {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFF0A0F1A),
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(listOf(AuraNimGreen.copy(0.65f), AuraCyan.copy(0.6f)))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box {
                        Image(
                            painter = painterResource(id = R.drawable.img_swarm_mesh_bg_1795350293224),
                            contentDescription = "NVIDIA NIM Neural Mesh",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(215.dp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            ObsidianBg.copy(alpha = 0.45f),
                                            ObsidianBg.copy(alpha = 0.95f)
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
                                        text = "100% NVIDIA NIM • NEMOTRON-FIRST",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFBEF264),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "$verifiedCount verified • ${profile.totalSwarmRuns} runs",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AuraCyan
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Julie Nemotron Router & Diagnostics",
                                style = MaterialTheme.typography.headlineSmall,
                                color = PearlWhite
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "ONE Nemotron Manager for complex multi-step tasks, Nemotron specialists by role, and at most 2 verified backups per role. Zero Gemini models.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMist
                            )

                            Spacer(Modifier.height(12.dp))
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

            // Provider Key Card (Persisted Masked Fingerprint + Distinct Statuses + Test/Probe)
            item(key = "nvidia_nim_settings") {
                val statusAccent = when (keyState.status) {
                    NimKeyStatus.VERIFIED_LIVE -> AuraMint
                    NimKeyStatus.STORED_LOCAL_UNVERIFIED -> AuraAmber
                    NimKeyStatus.INVALID_KEY_401 -> AuraCoral
                    NimKeyStatus.RATE_LIMITED_429 -> AuraAmber
                    NimKeyStatus.OFFLINE -> AuraCoral
                    NimKeyStatus.NOT_SET -> SlateMist
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    accentBorderColor = statusAccent.copy(alpha = 0.55f)
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
                            Text(
                                text = "Providers → NVIDIA NIM",
                                style = MaterialTheme.typography.titleMedium,
                                color = PearlWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = statusAccent.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, statusAccent.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = keyState.status.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (keyState.isStoredLocally) {
                            val timeText = if (keyState.lastTestedTimestampMs > 0L) {
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(Date(keyState.lastTestedTimestampMs))
                            } else "Never tested"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceElevated,
                                border = BorderStroke(1.dp, ObsidianCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Stored Key: ${keyState.maskedFingerprint}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PearlWhite
                                    )
                                    Text(
                                        text = "Last check: $timeText • Latency: ${keyState.lastLatencyMs}ms • Listed: ${keyState.liveRemoteModelCount} models",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateMist
                                    )
                                    Text(
                                        text = keyState.statusMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusAccent
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Key stays encrypted in AndroidKeyStore on this device. Never hardcoded or committed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMist
                            )
                        }

                        OutlinedTextField(
                            value = nimApiKeyInput,
                            onValueChange = { nimApiKeyInput = it },
                            label = {
                                Text(if (keyState.isStoredLocally) "Replace NVIDIA NIM Key" else "NVIDIA NIM API Key")
                            },
                            placeholder = { Text("nvapi-...") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuraNimGreen,
                                unfocusedBorderColor = ObsidianCardBorder,
                                focusedTextColor = PearlWhite,
                                unfocusedTextColor = PearlWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nvidia_nim_api_key_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                enabled = nimApiKeyInput.isNotBlank(),
                                onClick = {
                                    try {
                                        NvidiaNimClient.saveKey(context, nimApiKeyInput)
                                        nimApiKeyInput = ""
                                        nimFeedback = "Key encrypted locally. Tap Test Connection to verify live."
                                    } catch (e: Exception) {
                                        nimFeedback = "Save failed: ${e.localizedMessage ?: "unknown"}"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AuraNimGreen,
                                    contentColor = Color(0xFF071400)
                                ),
                                modifier = Modifier.testTag("save_nim_key_button")
                            ) {
                                Text(if (keyState.isStoredLocally) "Replace" else "Save Key", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                enabled = keyState.isStoredLocally,
                                onClick = onTestConnection,
                                border = BorderStroke(1.dp, AuraCyan.copy(alpha = 0.6f)),
                                modifier = Modifier.testTag("test_nim_connection_button")
                            ) {
                                Text("Test", color = AuraCyan)
                            }
                            if (keyState.isStoredLocally) {
                                TextButton(onClick = {
                                    NvidiaNimClient.saveKey(context, "")
                                    nimApiKeyInput = ""
                                    nimFeedback = "Key deleted from this device."
                                }) {
                                    Text("Remove", color = AuraCoral)
                                }
                            }
                        }

                        OutlinedButton(
                            enabled = keyState.isStoredLocally && !isProbingModels,
                            onClick = onProbePrimaryFleet,
                            border = BorderStroke(1.dp, AuraNimGreen.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("probe_nemotron_fleet_button")
                        ) {
                            if (isProbingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AuraNimGreen
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Probing Nemotron Endpoints Live...", color = AuraNimGreen)
                            } else {
                                Icon(Icons.Rounded.Speed, null, tint = AuraNimGreen, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Live Probe Nemotron Candidates (Verify Key Access)", color = AuraNimGreen)
                            }
                        }

                        if (nimFeedback.isNotBlank()) {
                            Text(nimFeedback, style = MaterialTheme.typography.labelSmall, color = AuraCyan)
                        }
                    }
                }
            }

            // Routing Controls & NVIDIA Inference Parameters
            item(key = "routing_and_params") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    accentBorderColor = AuraCyan.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Routing Policy & NIM Controls",
                            style = MaterialTheme.typography.titleMedium,
                            color = PearlWhite,
                            fontWeight = FontWeight.Bold
                        )

                        // Routing Profile Chips: Auto / Fast / Deep / Max Power
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoutingProfile.entries.forEach { prof ->
                                GlassChip(
                                    label = prof.label,
                                    selected = routingConfig.profile == prof,
                                    onClick = {
                                        onUpdateRoutingConfig(
                                            prof,
                                            routingConfig.orchestrationMode,
                                            routingConfig.vendorPolicy,
                                            routingConfig.managerEnabled
                                        )
                                    },
                                    accentColor = AuraCyan
                                )
                            }
                        }

                        // Orchestration & Vendor Policy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassChip(
                                label = routingConfig.orchestrationMode.label,
                                selected = routingConfig.orchestrationMode == OrchestrationMode.MANAGER_PLUS_SPECIALISTS,
                                onClick = {
                                    val next = if (routingConfig.orchestrationMode == OrchestrationMode.MANAGER_PLUS_SPECIALISTS) {
                                        OrchestrationMode.SINGLE_MODEL
                                    } else OrchestrationMode.MANAGER_PLUS_SPECIALISTS
                                    onUpdateRoutingConfig(routingConfig.profile, next, routingConfig.vendorPolicy, routingConfig.managerEnabled)
                                },
                                accentColor = AuraAmber
                            )
                            GlassChip(
                                label = routingConfig.vendorPolicy.label,
                                selected = routingConfig.vendorPolicy == VendorPolicy.NEMOTRON_ONLY,
                                onClick = {
                                    val next = if (routingConfig.vendorPolicy == VendorPolicy.NEMOTRON_ONLY) {
                                        VendorPolicy.VERIFIED_BACKUPS_ALLOWED
                                    } else VendorPolicy.NEMOTRON_ONLY
                                    onUpdateRoutingConfig(routingConfig.profile, routingConfig.orchestrationMode, next, routingConfig.managerEnabled)
                                },
                                accentColor = AuraNimGreen
                            )
                        }

                        // Streaming & Thinking switches
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactPowerToggle(
                                title = "SSE Stream",
                                checked = nimSettings.streamingEnabled,
                                accent = AuraCyan,
                                onCheckedChange = { onUpdateNimSettings(nimSettings.copy(streamingEnabled = it)) },
                                modifier = Modifier.weight(1f)
                            )
                            CompactPowerToggle(
                                title = "Think Trace",
                                checked = nimSettings.thinkingModeEnabled,
                                accent = AuraBlue,
                                onCheckedChange = { onUpdateNimSettings(nimSettings.copy(thinkingModeEnabled = it)) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Temperature & Max Tokens sliders
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Temperature", style = MaterialTheme.typography.labelSmall, color = SlateMist)
                                Text(
                                    text = "%.2f".format(nimSettings.temperature),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PearlWhite
                                )
                            }
                            Slider(
                                value = nimSettings.temperature,
                                onValueChange = { onUpdateNimSettings(nimSettings.copy(temperature = it)) },
                                valueRange = 0f..1.2f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AuraNimGreen,
                                    activeTrackColor = AuraNimGreen
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Max Output Tokens", style = MaterialTheme.typography.labelSmall, color = SlateMist)
                                Text(
                                    text = "${nimSettings.maxTokens}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PearlWhite
                                )
                            }
                            Slider(
                                value = nimSettings.maxTokens.toFloat(),
                                onValueChange = {
                                    val snapped = (it.toInt() / 256) * 256
                                    onUpdateNimSettings(nimSettings.copy(maxTokens = snapped.coerceIn(512, 4096)))
                                },
                                valueRange = 512f..4096f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AuraCyan,
                                    activeTrackColor = AuraCyan
                                )
                            )
                        }

                        // Role Pools Expand Toggle + Copy Diagnostics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showRolePools = !showRolePools }) {
                                Text(
                                    if (showRolePools) "Hide Role Pools (8)" else "View Nemotron Role Pools (8)",
                                    color = AuraCyan
                                )
                            }
                            if (lastDiagnostics != null) {
                                OutlinedButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Julie Diagnostics", lastDiagnostics.toCopyableReport()))
                                        nimFeedback = "Copied sanitized diagnostics (secrets excluded)."
                                    },
                                    border = BorderStroke(1.dp, AuraMint.copy(0.55f))
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, tint = AuraMint, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Diagnostics", color = AuraMint, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (showRolePools) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AiModelsCatalog.rolePools.forEach { pool ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = ObsidianSurfaceElevated,
                                        border = BorderStroke(1.dp, ObsidianCardBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = pool.role.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = AuraNimGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Primary: ${pool.nemotronPrimaryIds.first().substringAfterLast('/')}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PearlWhite
                                            )
                                            Text(
                                                text = "Backup A: ${pool.backupAId.substringAfterLast('/')} • Backup B: ${pool.backupBId.substringAfterLast('/')}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SlateMist
                                            )
                                        }
                                    }
                                }
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
                            contentDescription = "Auto Nemotron Router"
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Router (Nemotron-First + 2 Backups)",
                                style = MaterialTheme.typography.titleMedium,
                                color = PearlWhite
                            )
                            Text(
                                text = "Single-flight routing with live health failover & 50K+ prompt chunking",
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
                        placeholder = { Text("Search ${AiModelsCatalog.allModels.size} NVIDIA NIM models...") },
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

            // Chat & Vision Models with Per-Model Live Health, Probe, and Disable Toggle
            items(filteredModels, key = { it.id }) { model ->
                ModelSquircleCard(
                    spec = model,
                    health = modelHealthMap[model.id],
                    isSelected = profile.preferredModelId == model.id,
                    canProbe = keyState.isStoredLocally && !isProbingModels,
                    onClick = { onSelectModel(model.id) },
                    onProbe = { onProbeSingleModel(model.id) },
                    onToggleDisabled = { disabled -> onToggleModelDisabled(model.id, disabled) }
                )
            }

            // Separated Non-Chat Models (Embeddings & Translation — Never Routed to Chat)
            item(key = "non_chat_header") {
                Text(
                    text = "NON-CHAT SPECIALIZED MODELS (ISOLATED FROM CHAT FAILOVER)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMist,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(AiModelsCatalog.nonChatModels, key = { it.id }) { nonChat ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IosSquircleIcon(
                            gradientStart = Color(nonChat.accentHexStart),
                            gradientEnd = Color(nonChat.accentHexEnd),
                            size = 42.dp,
                            cornerRadius = 12.dp,
                            badgeText = nonChat.shortBadge,
                            contentDescription = nonChat.displayName
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${nonChat.displayName} (${nonChat.modality.label})",
                                style = MaterialTheme.typography.titleSmall,
                                color = PearlWhite
                            )
                            Text(
                                text = "${nonChat.id} • ${nonChat.endpointPath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateMist,
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
                .padding(horizontal = 12.dp, vertical = 4.dp),
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
    health: ModelHealthRecord?,
    isSelected: Boolean,
    canProbe: Boolean,
    onClick: () -> Unit,
    onProbe: () -> Unit,
    onToggleDisabled: (Boolean) -> Unit
) {
    val startColor = Color(spec.accentHexStart)
    val endColor = Color(spec.accentHexEnd)
    val healthStatus = health?.status ?: ModelHealthStatus.UNTESTED
    val isDisabled = health?.disabledByUser == true
    val healthColor = when (healthStatus) {
        ModelHealthStatus.VERIFIED -> AuraMint
        ModelHealthStatus.DEGRADED, ModelHealthStatus.RATE_LIMITED -> AuraAmber
        ModelHealthStatus.UNAVAILABLE -> AuraCoral
        ModelHealthStatus.DISABLED_BY_USER -> SlateMist
        ModelHealthStatus.UNTESTED -> SlateMist
    }

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
                    size = 46.dp,
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
                            color = if (isDisabled) SlateMist else PearlWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AuraNimGreen.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = spec.parameterScale,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBEF264),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${spec.vendor} • ${spec.contextWindow} • ${spec.id}",
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

            Spacer(Modifier.height(8.dp))

            // Live Health Pill + Probe + Disable Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(healthColor)
                    )
                    Text(
                        text = buildString {
                            append(healthStatus.label)
                            if (health != null && health.latencyMs > 0L) {
                                append(" • ${health.latencyMs}ms")
                            }
                            if (spec.isCandidateSlot && healthStatus == ModelHealthStatus.UNTESTED) {
                                append(" (Candidate)")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = healthColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        enabled = canProbe,
                        onClick = onProbe,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Probe", style = MaterialTheme.typography.labelSmall, color = AuraCyan)
                    }
                    TextButton(
                        onClick = { onToggleDisabled(!isDisabled) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (isDisabled) "Enable" else "Mute",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDisabled) AuraMint else SlateMist
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Power & Speed telemetry bars + Capability icons
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

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (spec.supportsVision) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = "Supports Vision",
                            tint = AuraAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (spec.supportsThinking) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = "Supports Reasoning Trace",
                            tint = AuraCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "NVIDIA NIM Ready",
                        tint = AuraNimGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
