package com.example

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.signOutUser
import com.example.data.local.LocalAuraStore
import com.example.data.local.MemoryVaultStore
import com.example.data.model.AiModelsCatalog
import com.example.data.model.ScheduleMode
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.remote.ImageKitClient
import com.example.data.remote.MongoDbClient
import com.example.data.remote.NvidiaNimClient
import com.example.firebase.FirebaseRuntime
import com.example.firebase.configureAppCheck
import com.example.ui.components.AmbientAuraBackground
import com.example.ui.components.DynamicIslandCapsule
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ModelsScreen
import com.example.ui.screens.ScreenHubScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.AuraAiTheme
import com.example.ui.theme.AuraBlue
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraNimGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.SlateMist
import com.example.ui.viewmodel.AuraViewModel
import com.example.ui.viewmodel.DataUiState
import com.google.firebase.auth.FirebaseAuth

enum class AuraTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val accent: Color
) {
    CHAT("chat", "Julie", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, AuraBlue),
    TASKS("tasks", "24h Tasks", Icons.Rounded.TaskAlt, Icons.Outlined.TaskAlt, AuraNimGreen),
    MODELS("models", "NIM Models", Icons.Rounded.Hub, Icons.Outlined.Hub, AuraCyan),
    SCREEN("screen", "Hub", Icons.Rounded.ScreenShare, Icons.Outlined.ScreenShare, AuraCoral)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent before UI/ViewModels
        runCatching { configureAppCheck(this, intent) }
            .onFailure { Log.e("MainActivity", "AppCheck startup warning", it) }

        // Load locally encrypted NVIDIA NIM, ImageKit, MongoDB configs & Memory Vault safely
        runCatching { NvidiaNimClient.initialize(applicationContext) }
            .onFailure { Log.e("MainActivity", "NvidiaNimClient init warning", it) }
        runCatching { ImageKitClient.initialize(applicationContext) }
            .onFailure { Log.e("MainActivity", "ImageKitClient init warning", it) }
        runCatching { MongoDbClient.initialize(applicationContext) }
            .onFailure { Log.e("MainActivity", "MongoDbClient init warning", it) }
        runCatching { MemoryVaultStore.initialize(applicationContext) }
            .onFailure { Log.e("MainActivity", "MemoryVaultStore init warning", it) }

        runCatching {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            )
        }

        setContent {
            AuraAiTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val firebaseAuth = remember(context) { FirebaseRuntime.authOrNull(context) }
    var currentUser by remember { mutableStateOf(firebaseAuth?.currentUser) }
    var showOptionalAuthScreen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(firebaseAuth) {
        if (firebaseAuth == null) {
            onDispose {}
        } else {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                currentUser = auth.currentUser
                if (auth.currentUser != null) {
                    showOptionalAuthScreen = false
                }
            }
            firebaseAuth.addAuthStateListener(listener)
            onDispose { firebaseAuth.removeAuthStateListener(listener) }
        }
    }

    if (showOptionalAuthScreen && currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = firebaseAuth?.currentUser
                showOptionalAuthScreen = false
            },
            onContinueAsGuest = {
                showOptionalAuthScreen = false
            }
        )
    } else {
        val isSignedIn = currentUser != null
        val sessionKey = currentUser?.uid ?: LocalAuraStore.GUEST_USER_ID
        val email = currentUser?.email
        key(sessionKey) {
            AuraMainShell(
                sessionKey = sessionKey,
                isSignedIn = isSignedIn,
                userEmail = email,
                onRequestSignIn = { showOptionalAuthScreen = true }
            )
        }
    }
}

@Composable
private fun AuraMainShell(
    sessionKey: String,
    isSignedIn: Boolean,
    userEmail: String?,
    onRequestSignIn: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: AuraViewModel = viewModel(
        key = sessionKey,
        factory = remember(application, isSignedIn) {
            AuraViewModel.Factory(application = application, isCloudSynced = isSignedIn)
        }
    )
    val scope = rememberCoroutineScope()

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val liveSwarm by viewModel.liveSwarm.collectAsStateWithLifecycle()
    val screenState by viewModel.screenCaptureState.collectAsStateWithLifecycle()
    val isWatchdogActive by viewModel.isWatchdog24hActive.collectAsStateWithLifecycle()
    val isSplittingGoal by viewModel.isSplittingGoal.collectAsStateWithLifecycle()
    val nimKeyState by viewModel.nimKeyStateFlow.collectAsStateWithLifecycle()
    val nimSettings by viewModel.nimSettingsFlow.collectAsStateWithLifecycle()
    val routingConfig by viewModel.routingConfig.collectAsStateWithLifecycle()
    val modelHealthMap by viewModel.modelHealthMapFlow.collectAsStateWithLifecycle()
    val lastDiagnostics by viewModel.lastDiagnostics.collectAsStateWithLifecycle()
    val isProbingModels by viewModel.isProbingModels.collectAsStateWithLifecycle()
    val pendingToolConfirmation by viewModel.pendingToolConfirmation.collectAsStateWithLifecycle()
    val imageKitConfig by viewModel.imageKitConfigFlow.collectAsStateWithLifecycle()
    val imageKitAssets by viewModel.imageKitAssetsFlow.collectAsStateWithLifecycle()
    val isUploadingImageKit by viewModel.isUploadingImageKit.collectAsStateWithLifecycle()
    val mongoConfig by viewModel.mongoConfigFlow.collectAsStateWithLifecycle()
    val isSyncingMongo by viewModel.isSyncingMongo.collectAsStateWithLifecycle()
    val memoryNotes by viewModel.memoryNotesFlow.collectAsStateWithLifecycle()
    val toolActionLogs by viewModel.toolActionLogsFlow.collectAsStateWithLifecycle()
    val bannerError by viewModel.bannerError.collectAsStateWithLifecycle()
    val infoNotice by viewModel.infoNotice.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(AuraTab.CHAT) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = selectedTab != AuraTab.CHAT) {
        selectedTab = AuraTab.CHAT
    }

    LaunchedEffect(bannerError) {
        bannerError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBannerError()
        }
    }

    LaunchedEffect(infoNotice) {
        infoNotice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfoNotice()
        }
    }

    val completedTasksCount = (tasksState as? DataUiState.Success)?.data
        ?.count { it.statusEnum() == TaskStatus.COMPLETED } ?: 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        AmbientAuraBackground(
            primaryGlow = selectedTab.accent,
            secondaryGlow = AuraNimGreen
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 700.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = ObsidianSurface.copy(alpha = 0.92f),
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        Spacer(Modifier.height(16.dp))
                        AuraTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                        tint = if (selected) tab.accent else SlateMist
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) PearlWhite else SlateMist
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DynamicIslandCapsule(
                            liveSwarm = liveSwarm,
                            isScreenSharing = screenState.isSharingActive,
                            isWatchdog24hActive = isWatchdogActive,
                            modelCount = AiModelsCatalog.allModels.size,
                            liveScreenFrames = screenState.frameCount,
                            onClick = {
                                selectedTab = when {
                                    screenState.isSharingActive -> AuraTab.SCREEN
                                    liveSwarm.parallelTasksRunning > 0 -> AuraTab.TASKS
                                    else -> AuraTab.MODELS
                                }
                            },
                            modifier = Modifier.testTag("dynamic_island_capsule")
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                            label = "tabTransition"
                        ) { tab ->
                            when (tab) {
                                AuraTab.CHAT -> ChatScreen(
                                    messagesState = messagesState,
                                    profile = profile,
                                    liveSwarm = liveSwarm,
                                    routingConfig = routingConfig,
                                    lastDiagnostics = lastDiagnostics,
                                    pendingToolConfirmation = pendingToolConfirmation,
                                    screenCaptureState = screenState,
                                    isCloudSynced = isSignedIn,
                                    onSendPrompt = { prompt, useSearch, attachScreen ->
                                        viewModel.sendChatPrompt(prompt, useSearch, attachScreen)
                                    },
                                    onCancelGeneration = { viewModel.cancelActiveChatRequest() },
                                    onInitiateVoiceTask = { title, prompt ->
                                        viewModel.createTask(
                                            title = title,
                                            prompt = prompt,
                                            category = TaskCategory.AUTOMATION,
                                            priority = TaskPriority.HIGH,
                                            scheduleMode = ScheduleMode.CONTINUOUS_24H,
                                            runImmediately = true
                                        )
                                        selectedTab = AuraTab.TASKS
                                    },
                                    onTriggerToolIntent = { viewModel.handleToolIntent(it) },
                                    onConfirmPendingTool = { viewModel.confirmPendingTool() },
                                    onDismissPendingTool = { viewModel.dismissPendingTool() },
                                    onToggleSwarm = { enabled ->
                                        viewModel.updateSwarmSettings(swarmEnabled = enabled)
                                    },
                                    onToggleSearch = { enabled ->
                                        viewModel.updateSwarmSettings(autoSearchEnabled = enabled)
                                    },
                                    onOpenScreenShareTab = { selectedTab = AuraTab.SCREEN },
                                    onClearChat = { viewModel.clearChatHistory() }
                                )

                                AuraTab.TASKS -> TasksScreen(
                                    tasksState = tasksState,
                                    isWatchdog24hActive = isWatchdogActive,
                                    isSplittingGoal = isSplittingGoal,
                                    parallelTasksRunning = liveSwarm.parallelTasksRunning,
                                    onToggleWatchdog24h = { viewModel.toggleWatchdog24h(it) },
                                    onCreateTask = { t, p, c, pr, sc, now ->
                                        viewModel.createTask(t, p, c, pr, sc, now)
                                    },
                                    onSplitGoalIntoMultiTasks = { goal, autoExec ->
                                        viewModel.splitGoalAndQueueTasks(goal, autoExec)
                                    },
                                    onRunAllQueued = { viewModel.runAllQueuedTasksInParallel() },
                                    onRunSingleTask = { viewModel.runSingleTask(it) },
                                    onDuplicateAndRunTask = { viewModel.duplicateAndRunTask(it) },
                                    onRetryTask = { viewModel.retryTask(it) },
                                    onDeleteTask = { viewModel.deleteTask(it) }
                                )

                                AuraTab.MODELS -> ModelsScreen(
                                    profile = profile,
                                    keyState = nimKeyState,
                                    nimSettings = nimSettings,
                                    routingConfig = routingConfig,
                                    modelHealthMap = modelHealthMap,
                                    lastDiagnostics = lastDiagnostics,
                                    isProbingModels = isProbingModels,
                                    onSelectModel = { viewModel.selectModel(it) },
                                    onUpdateSettings = { swarm, maxPwr, search ->
                                        viewModel.updateSwarmSettings(swarm, maxPwr, search)
                                    },
                                    onUpdateNimSettings = { viewModel.updateNimInferenceSettings(it) },
                                    onUpdateRoutingConfig = { p, o, v, m ->
                                        viewModel.updateRoutingConfig(p, o, v, m)
                                    },
                                    onTestConnection = { viewModel.testNvidiaNimConnection() },
                                    onProbePrimaryFleet = { viewModel.probePrimaryNemotronFleet() },
                                    onProbeSingleModel = { viewModel.probeSingleModel(it) },
                                    onToggleModelDisabled = { id, disabled ->
                                        viewModel.setModelDisabled(id, disabled)
                                    }
                                )

                                AuraTab.SCREEN -> ScreenHubScreen(
                                    screenState = screenState,
                                    profile = profile,
                                    isSignedIn = isSignedIn,
                                    userEmail = userEmail,
                                    completedTaskCount = completedTasksCount,
                                    imageKitConfig = imageKitConfig,
                                    imageKitAssets = imageKitAssets,
                                    isUploadingImageKit = isUploadingImageKit,
                                    mongoConfig = mongoConfig,
                                    isSyncingMongo = isSyncingMongo,
                                    memoryNotes = memoryNotes,
                                    toolActionLogs = toolActionLogs,
                                    onAnalyzeScreenInChat = { question ->
                                        selectedTab = AuraTab.CHAT
                                        viewModel.sendChatPrompt(
                                            prompt = question,
                                            forceGoogleSearch = false,
                                            includeAttachedScreen = true
                                        )
                                    },
                                    onUploadScreenToImageKit = { viewModel.uploadCurrentScreenToImageKit() },
                                    onTestImageKit = { viewModel.testImageKitConnection() },
                                    onDeleteImageKitAsset = { viewModel.deleteImageKitAsset(it) },
                                    onTestMongo = { viewModel.testMongoDbConnection() },
                                    onSyncMongoNow = { viewModel.syncAllToMongoDbNow() },
                                    onAddMemoryNote = { title, content -> viewModel.addMemoryNote(title, content) },
                                    onToggleMemoryPin = { viewModel.toggleMemoryPin(it) },
                                    onDeleteMemoryNote = { viewModel.deleteMemoryNote(it) },
                                    onClearMemoryVault = { viewModel.clearMemoryVault() },
                                    onSignInClick = onRequestSignIn,
                                    onSignOut = {
                                        runCatching {
                                            signOutUser(context, CredentialManager.create(context), scope)
                                        }.onFailure { Log.e("MainActivity", "Sign out warning", it) }
                                    }
                                )
                            }
                        }
                    }

                    if (!isExpandedScreen) {
                        IosFloatingGlassDock(
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 52.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
private fun IosFloatingGlassDock(
    selectedTab: AuraTab,
    onSelectTab: (AuraTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = ObsidianSurface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, ObsidianCardBorder),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuraTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val itemShape = RoundedCornerShape(20.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(itemShape)
                            .background(
                                if (isSelected) tab.accent.copy(alpha = 0.16f) else Color.Transparent
                            )
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 8.dp)
                            .testTag("nav_tab_${tab.route}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = if (isSelected) tab.accent else SlateMist,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) PearlWhite else SlateMist,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
