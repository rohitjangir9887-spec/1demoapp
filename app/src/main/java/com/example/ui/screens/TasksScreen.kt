package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.TrendingAiProjectsCatalog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantTask
import com.example.data.model.ScheduleMode
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassChip
import com.example.ui.components.IosSquircleIcon
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraIndigo
import com.example.ui.theme.AuraMint
import com.example.ui.theme.AuraNimGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.SlateMist
import com.example.ui.viewmodel.DataUiState

@Composable
fun TasksScreen(
    tasksState: DataUiState<List<AssistantTask>>,
    isWatchdog24hActive: Boolean,
    isSplittingGoal: Boolean,
    parallelTasksRunning: Int,
    onToggleWatchdog24h: (Boolean) -> Unit,
    onCreateTask: (String, String, TaskCategory, TaskPriority, ScheduleMode, Boolean) -> Unit,
    onSplitGoalIntoMultiTasks: (String, Boolean) -> Unit,
    onRunAllQueued: () -> Unit,
    onRunSingleTask: (AssistantTask) -> Unit,
    onDuplicateAndRunTask: (AssistantTask) -> Unit,
    onRetryTask: (AssistantTask) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var selectedTaskForReport by remember { mutableStateOf<AssistantTask?>(null) }
    var filterStatus by remember { mutableStateOf<TaskStatus?>(null) }

    val allTasks = (tasksState as? DataUiState.Success)?.data.orEmpty()
    val filteredTasks = remember(allTasks, filterStatus) {
        if (filterStatus == null) allTasks else allTasks.filter { it.statusEnum() == filterStatus }
    }
    val queuedCount = allTasks.count { it.statusEnum() == TaskStatus.QUEUED }
    val completedCount = allTasks.count { it.statusEnum() == TaskStatus.COMPLETED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "24h Task Swarm",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PearlWhite
                    )
                    Text(
                        text = "$completedCount done • $queuedCount queued • $parallelTasksRunning running",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraMint
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosSquircleIcon(
                        gradientStart = AuraIndigo,
                        gradientEnd = AuraBlue,
                        size = 44.dp,
                        cornerRadius = 13.dp,
                        icon = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Split Goal into Multiple Tasks",
                        onClick = { showSplitDialog = true },
                        modifier = Modifier.testTag("ai_split_tasks_button")
                    )
                    IosSquircleIcon(
                        gradientStart = AuraNimGreen,
                        gradientEnd = AuraMint,
                        size = 44.dp,
                        cornerRadius = 13.dp,
                        icon = Icons.Rounded.Add,
                        contentDescription = "Create New Task",
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("create_task_button")
                    )
                }
            }

            // 24/7 Autonomous Control Banner
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                accentBorderColor = if (isWatchdog24hActive) AuraMint else null
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = if (isWatchdog24hActive) AuraMint else SlateMist,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "24-Hour Autonomous Engine",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PearlWhite
                                )
                                Text(
                                    text = if (isWatchdog24hActive) {
                                        "Auto-executes 24h tasks with multi-model swarm"
                                    } else {
                                        "Paused • Manual launch only"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMist
                                )
                            }
                        }
                        Switch(
                            checked = isWatchdog24hActive,
                            onCheckedChange = onToggleWatchdog24h,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = AuraMint
                            ),
                            modifier = Modifier.testTag("watchdog_24h_switch")
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRunAllQueued,
                            enabled = queuedCount > 0,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuraBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("run_all_parallel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Run All ($queuedCount)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = { showSplitDialog = true },
                            enabled = !isSplittingGoal,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AuraCyan.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("multi_task_goal_button")
                        ) {
                            if (isSplittingGoal) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AuraCyan,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = AuraCyan,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "AI Multi-Tasks",
                                style = MaterialTheme.typography.labelLarge,
                                color = AuraCyan
                            )
                        }
                    }
                }
            }

            val context = LocalContext.current
            val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
            val isBatteryIgnored = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                } else true
            }

            if (!isBatteryIgnored) {
                Spacer(Modifier.height(10.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    accentBorderColor = AuraAmber
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryAlert,
                            contentDescription = null,
                            tint = AuraAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "24h Background Run (Doze Mode)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PearlWhite
                            )
                            Text(
                                text = "Disable battery restrictions so Julie runs tasks even when the phone is locked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMist
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    } else {
                                        Intent(Settings.ACTION_SETTINGS)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AuraAmber),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Allow 24h",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraAmber
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Trending Public AI & GitHub Projects
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.TrendingUp,
                        contentDescription = null,
                        tint = AuraNimGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "TRENDING PUBLIC AI & GITHUB PROJECTS",
                        style = MaterialTheme.typography.labelMedium,
                        color = AuraNimGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "1-Click Run",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMist
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrendingAiProjectsCatalog.projects.forEach { proj ->
                    GlassCard(
                        modifier = Modifier.width(260.dp),
                        cornerRadius = 18.dp,
                        accentBorderColor = when (proj.category) {
                            "Google AI" -> AuraCyan
                            "GitHub Agent" -> AuraBlue
                            "Automation" -> AuraMint
                            else -> AuraNimGreen
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AuraBlue.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, AuraBlue.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = proj.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = proj.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = PearlWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = proj.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMist,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    onCreateTask(
                                        proj.title,
                                        proj.prompt,
                                        proj.taskCategory,
                                        proj.priority,
                                        proj.defaultScheduleMode,
                                        true
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AuraNimGreen.copy(alpha = 0.25f),
                                    contentColor = Color(0xFFBEF264)
                                ),
                                border = BorderStroke(1.dp, AuraNimGreen.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Run Swarm Task",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassChip(
                    label = "All (${allTasks.size})",
                    selected = filterStatus == null,
                    onClick = { filterStatus = null },
                    accentColor = AuraCyan
                )
                TaskStatus.entries.forEach { st ->
                    GlassChip(
                        label = st.label,
                        selected = filterStatus == st,
                        onClick = { filterStatus = if (filterStatus == st) null else st },
                        accentColor = statusColor(st)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Task List
            when (tasksState) {
                is DataUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AuraMint)
                    }
                }

                is DataUiState.Error -> {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentBorderColor = AuraCoral
                    ) {
                        Text(
                            text = tasksState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraCoral,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is DataUiState.Success -> {
                    if (filteredTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IosSquircleIcon(
                                    gradientStart = AuraNimGreen,
                                    gradientEnd = AuraBlue,
                                    size = 60.dp,
                                    cornerRadius = 18.dp,
                                    icon = Icons.Rounded.TaskAlt,
                                    contentDescription = "No tasks yet"
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "No autonomous tasks yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PearlWhite
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Tap AI Multi-Tasks to break any goal into parallel swarm tasks, or create one manually.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMist
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredTasks, key = { it.id }) { task ->
                                TaskSwarmCard(
                                    task = task,
                                    onInspect = { selectedTaskForReport = task },
                                    onRun = { onRunSingleTask(task) },
                                    onDuplicateRun = { onDuplicateAndRunTask(task) },
                                    onRetry = { onRetryTask(task) },
                                    onDelete = { onDeleteTask(task.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, prompt, cat, prio, sched, runNow ->
                showCreateDialog = false
                onCreateTask(title, prompt, cat, prio, sched, runNow)
            }
        )
    }

    if (showSplitDialog) {
        SplitGoalDialog(
            onDismiss = { showSplitDialog = false },
            onSubmit = { goal, autoExec ->
                showSplitDialog = false
                onSplitGoalIntoMultiTasks(goal, autoExec)
            }
        )
    }

    selectedTaskForReport?.let { task ->
        TaskReportDialog(
            task = task,
            onDismiss = { selectedTaskForReport = null },
            onDuplicateAndRun = {
                selectedTaskForReport = null
                onDuplicateAndRunTask(task)
            }
        )
    }
}

@Composable
private fun TaskSwarmCard(
    task: AssistantTask,
    onInspect: () -> Unit,
    onRun: () -> Unit,
    onDuplicateRun: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val status = task.statusEnum()
    val sColor = statusColor(status)
    val animatedProgress by animateFloatAsState(
        targetValue = (task.progressPercent.coerceIn(0L, 100L) / 100f),
        animationSpec = tween(420),
        label = "taskProgress"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        cornerRadius = 22.dp,
        accentBorderColor = if (status == TaskStatus.RUNNING) AuraCyan else sColor.copy(alpha = 0.55f),
        onClick = onInspect
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = status.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = sColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianSurfaceElevated
                    ) {
                        Text(
                            text = "${task.categoryEnum().label} • ${task.scheduleEnum().label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateMist,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete task",
                        tint = SlateMist,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = PearlWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = task.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = SlateMist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (task.assignedModels.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    task.assignedModels.forEach { model ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AuraBlue.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = model,
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = status == TaskStatus.RUNNING || status == TaskStatus.COMPLETED) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        color = sColor,
                        trackColor = ObsidianSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }

            if (!task.resultText.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = task.resultText,
                    style = MaterialTheme.typography.bodySmall,
                    color = PearlWhite.copy(alpha = 0.88f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceElevated.copy(alpha = 0.7f))
                        .padding(10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status) {
                    TaskStatus.QUEUED -> {
                        Button(
                            onClick = onRun,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuraBlue),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Launch Swarm", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    TaskStatus.RUNNING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AuraCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Collaborating (${task.progressPercent}%)...",
                                style = MaterialTheme.typography.labelMedium,
                                color = AuraCyan
                            )
                        }
                    }

                    TaskStatus.COMPLETED -> {
                        TextButton(onClick = onDuplicateRun) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp), tint = AuraCyan)
                            Spacer(Modifier.width(4.dp))
                            Text("Re-run Fresh", color = AuraCyan)
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = onInspect,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuraMint, contentColor = ObsidianBg),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Read Report", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    TaskStatus.FAILED -> {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuraCoral),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry Task", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun statusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.QUEUED -> AuraAmber
    TaskStatus.RUNNING -> AuraCyan
    TaskStatus.COMPLETED -> AuraMint
    TaskStatus.FAILED -> AuraCoral
}

@Composable
private fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, TaskCategory, TaskPriority, ScheduleMode, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TaskCategory.RESEARCH) }
    var priority by remember { mutableStateOf(TaskPriority.HIGH) }
    var schedule by remember { mutableStateOf(ScheduleMode.CONTINUOUS_24H) }
    var runNow by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("New 24h Autonomous Task", color = PearlWhite) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Detailed Instructions for Swarm") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium, color = SlateMist)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskCategory.entries.forEach { cat ->
                        GlassChip(
                            label = cat.label,
                            selected = category == cat,
                            onClick = { category = cat },
                            accentColor = AuraBlue
                        )
                    }
                }
                Text("24h Schedule", style = MaterialTheme.typography.labelMedium, color = SlateMist)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScheduleMode.entries.forEach { mode ->
                        GlassChip(
                            label = mode.label,
                            selected = schedule == mode,
                            onClick = { schedule = mode },
                            accentColor = AuraMint
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run immediately with Max Swarm", style = MaterialTheme.typography.bodySmall, color = PearlWhite)
                    Switch(checked = runNow, onCheckedChange = { runNow = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, prompt, category, priority, schedule, runNow) },
                enabled = title.isNotBlank()
            ) {
                Text(if (runNow) "Create & Run" else "Queue Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SplitGoalDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, Boolean) -> Unit
) {
    var goal by remember { mutableStateOf("") }
    var autoExecute by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("AI Multi-Task Splitter", color = PearlWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Describe a big goal. Aura AI will split it into 3–5 parallel tasks and dispatch multiple models automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMist
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("e.g., Launch my YouTube channel & content plan") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run all generated tasks in parallel", style = MaterialTheme.typography.bodySmall, color = PearlWhite)
                    Switch(checked = autoExecute, onCheckedChange = { autoExecute = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(goal, autoExecute) },
                enabled = goal.isNotBlank()
            ) {
                Text("Split & Dispatch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TaskReportDialog(
    task: AssistantTask,
    onDismiss: () -> Unit,
    onDuplicateAndRun: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text(task.title, color = PearlWhite) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Collaborating Models: ${task.assignedModels.joinToString(", ").ifBlank { "Aura Swarm" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AuraCyan
                )
                if (task.executionSteps.isNotEmpty()) {
                    Text("Execution Trace", style = MaterialTheme.typography.titleSmall, color = AuraMint)
                    task.executionSteps.forEach { step ->
                        Text("• $step", style = MaterialTheme.typography.bodySmall, color = SlateMist)
                    }
                }
                Text("AI Output Report", style = MaterialTheme.typography.titleSmall, color = PearlWhite)
                Text(
                    text = task.resultText?.ifBlank { "Task has not completed yet." } ?: "Task has not completed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PearlWhite.copy(alpha = 0.92f)
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (task.statusEnum() == TaskStatus.COMPLETED) {
                TextButton(onClick = onDuplicateAndRun) { Text("Run Fresh Copy") }
            }
        }
    )
}
