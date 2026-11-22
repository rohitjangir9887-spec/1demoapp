package com.example

import android.app.Application
import android.os.Bundle
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
import com.example.data.remote.NvidiaNimClient
import com.example.data.model.TaskStatus
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

enum class AuraTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val accent: Color
) {
    CHAT("chat", "Chat", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, AuraBlue),
    TASKS("tasks", "24h Tasks", Icons.Rounded.TaskAlt, Icons.Outlined.TaskAlt, AuraNimGreen),
    MODELS("models", "33 Models", Icons.Rounded.Hub, Icons.Outlined.Hub, AuraCyan),
    SCREEN("screen", "Screen", Icons.Rounded.ScreenShare, Icons.Outlined.ScreenShare, AuraCoral)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the user's locally encrypted NVIDIA key before constructing any AI ViewModels.
        NvidiaNimClient.initialize(applicationContext)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent before UI/ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            AuraAiTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    if (currentUser == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val uid = currentUser!!.uid
        val email = currentUser!!.email
        key(uid) {
            AuraMainShell(currentUserId = uid, userEmail = email)
        }
    }
}

@Composable
private fun AuraMainShell(
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: AuraViewModel = viewModel(
        key = currentUserId,
        factory = remember(application) { AuraViewModel.Factory(application) }
    )
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val liveSwarm by viewModel.liveSwarm.collectAsStateWithLifecycle()
    val screenState by viewModel.screenCaptureState.collectAsStateWithLifecycle()
    val isWatchdogActive by viewModel.isWatchdog24hActive.collectAsStateWithLifecycle()
    val isSplittingGoal by viewModel.isSplittingGoal.collectAsStateWithLifecycle()
    val bannerError by viewModel.bannerError.collectAsStateWithLifecycle()
    val infoNotice by viewModel.infoNotice.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(AuraTab.CHAT) }
    val snackbarHostState = remember { SnackbarHostState() }

    // BackHandler returns to home CHAT tab from any secondary tab
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
                    // Dynamic Island Live Capsule at top center
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

                    // Active Tab Content with smooth crossfade
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
                                    screenCaptureState = screenState,
                                    onSendPrompt = { prompt, useSearch, attachScreen ->
                                        viewModel.sendChatPrompt(prompt, useSearch, attachScreen)
                                    },
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
                                    isNvidiaNimConfigured = viewModel.isNvidiaNimConfigured,
                                    onSelectModel = { viewModel.selectModel(it) },
                                    onUpdateSettings = { swarm, maxPwr, search ->
                                        viewModel.updateSwarmSettings(swarm, maxPwr, search)
                                    }
                                )

                                AuraTab.SCREEN -> ScreenHubScreen(
                                    screenState = screenState,
                                    profile = profile,
                                    userEmail = userEmail,
                                    completedTaskCount = completedTasksCount,
                                    onAnalyzeScreenInChat = { question ->
                                        selectedTab = AuraTab.CHAT
                                        viewModel.sendChatPrompt(
                                            prompt = question,
                                            forceGoogleSearch = false,
                                            includeAttachedScreen = true
                                        )
                                    },
                                    onSignOut = {
                                        signOutUser(credentialManager, scope)
                                    }
                                )
                            }
                        }
                    }

                    // Frosted Floating iOS Glass Dock (compact handheld screens)
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
