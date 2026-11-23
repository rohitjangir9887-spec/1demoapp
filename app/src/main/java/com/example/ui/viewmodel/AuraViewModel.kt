package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AuraSwarmEngine
import com.example.data.ai.OrchestrationMode
import com.example.data.ai.RequestDiagnostics
import com.example.data.ai.RoutingConfig
import com.example.data.ai.RoutingProfile
import com.example.data.ai.SwarmStepUpdate
import com.example.data.ai.VendorPolicy
import com.example.data.local.LocalAuraStore
import com.example.data.local.MemoryNote
import com.example.data.local.MemoryVaultStore
import com.example.data.model.AiModelsCatalog
import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.ScheduleMode
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UserProfile
import com.example.data.remote.ImageKitAsset
import com.example.data.remote.ImageKitClient
import com.example.data.remote.ImageKitConfig
import com.example.data.remote.ModelHealthRecord
import com.example.data.remote.MongoDbClient
import com.example.data.remote.MongoDbConfig
import com.example.data.remote.NimInferenceSettings
import com.example.data.remote.NimKeyState
import com.example.data.remote.NvidiaNimClient
import com.example.data.repository.AuraDataSource
import com.example.data.repository.AuraRepository
import com.example.firebase.FirebaseRuntime
import com.example.screen.ScreenCaptureState
import com.example.screen.ScreenCaptureStore
import com.example.tools.JulieToolRegistry
import com.example.tools.PendingToolAction
import com.example.tools.ToolActionLog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val TAG = "AuraViewModel"

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

data class LiveSwarmActivity(
    val isRunning: Boolean = false,
    val activeModelName: String = "",
    val stageLabel: String = "",
    val progressPercent: Int = 0,
    val steps: List<String> = emptyList(),
    val streamingText: String = "",
    val thinkingTrace: String = "",
    val parallelTasksRunning: Int = 0
)

class AuraViewModel(
    application: Application,
    val isCloudSynced: Boolean,
    private val repository: AuraDataSource = if (isCloudSynced && FirebaseRuntime.isFirebaseConfigured(application)) {
        runCatching { AuraRepository(application.applicationContext) }
            .onFailure { Log.e(TAG, "Firestore repository init failed; falling back to local store", it) }
            .getOrElse { LocalAuraStore(application.applicationContext) }
    } else {
        LocalAuraStore(application.applicationContext)
    },
    private val swarmEngine: AuraSwarmEngine = AuraSwarmEngine(application.applicationContext)
) : AndroidViewModel(application) {

    val screenCaptureState: StateFlow<ScreenCaptureState> = ScreenCaptureStore.state
    val isNvidiaNimConfiguredFlow: StateFlow<Boolean> = NvidiaNimClient.isConfiguredFlow
    val nimKeyStateFlow: StateFlow<NimKeyState> = NvidiaNimClient.keyStateFlow
    val nimSettingsFlow: StateFlow<NimInferenceSettings> = NvidiaNimClient.settingsFlow
    val liveRemoteModelIdsFlow: StateFlow<List<String>> = NvidiaNimClient.liveRemoteModelIds
    val modelHealthMapFlow: StateFlow<Map<String, ModelHealthRecord>> = NvidiaNimClient.healthMapFlow

    val imageKitConfigFlow: StateFlow<ImageKitConfig> = ImageKitClient.configFlow
    val imageKitAssetsFlow: StateFlow<List<ImageKitAsset>> = ImageKitClient.assetsFlow
    val mongoConfigFlow: StateFlow<MongoDbConfig> = MongoDbClient.configFlow
    val memoryNotesFlow: StateFlow<List<MemoryNote>> = MemoryVaultStore.notesFlow
    val toolActionLogsFlow: StateFlow<List<ToolActionLog>> = JulieToolRegistry.actionLogs

    val userProfile: StateFlow<UserProfile> = repository.observeUserProfile()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.e(TAG, "Profile stream error: ${(firestoreError ?: error).message}")
            emit(UserProfile(displayName = if (isCloudSynced) "Julie User" else "Guest Mode"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserProfile(displayName = if (isCloudSynced) "Julie User" else "Guest Mode")
        )

    val tasksState: StateFlow<DataUiState<List<AssistantTask>>> = repository.observeTasks()
        .map<List<AssistantTask>, DataUiState<List<AssistantTask>>> { DataUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(DataUiState.Error(root.message ?: "Failed to sync tasks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Loading
        )

    val messagesState: StateFlow<DataUiState<List<ChatMessage>>> = repository.observeMessages()
        .map<List<ChatMessage>, DataUiState<List<ChatMessage>>> { DataUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(DataUiState.Error(root.message ?: "Failed to sync messages"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Loading
        )

    private val _liveSwarm = MutableStateFlow(LiveSwarmActivity())
    val liveSwarm: StateFlow<LiveSwarmActivity> = _liveSwarm.asStateFlow()

    private val _routingConfig = MutableStateFlow(RoutingConfig())
    val routingConfig: StateFlow<RoutingConfig> = _routingConfig.asStateFlow()

    private val _lastDiagnostics = MutableStateFlow<RequestDiagnostics?>(null)
    val lastDiagnostics: StateFlow<RequestDiagnostics?> = _lastDiagnostics.asStateFlow()

    private val _pendingToolConfirmation = MutableStateFlow<PendingToolAction?>(null)
    val pendingToolConfirmation: StateFlow<PendingToolAction?> = _pendingToolConfirmation.asStateFlow()

    private val _bannerError = MutableStateFlow<String?>(null)
    val bannerError: StateFlow<String?> = _bannerError.asStateFlow()

    private val _infoNotice = MutableStateFlow<String?>(null)
    val infoNotice: StateFlow<String?> = _infoNotice.asStateFlow()

    private val _isWatchdog24hActive = MutableStateFlow(true)
    val isWatchdog24hActive: StateFlow<Boolean> = _isWatchdog24hActive.asStateFlow()

    private val _isSplittingGoal = MutableStateFlow(false)
    val isSplittingGoal: StateFlow<Boolean> = _isSplittingGoal.asStateFlow()

    private val _isUploadingImageKit = MutableStateFlow(false)
    val isUploadingImageKit: StateFlow<Boolean> = _isUploadingImageKit.asStateFlow()

    private val _isSyncingMongo = MutableStateFlow(false)
    val isSyncingMongo: StateFlow<Boolean> = _isSyncingMongo.asStateFlow()

    private val _isProbingModels = MutableStateFlow(false)
    val isProbingModels: StateFlow<Boolean> = _isProbingModels.asStateFlow()

    private val parallelSemaphore = Semaphore(3)
    private var watchdogJob: Job? = null
    private var activeChatJob: Job? = null

    val isNvidiaNimConfigured: Boolean
        get() = swarmEngine.isNvidiaNimDirectActive()

    init {
        com.example.data.remote.GoogleGeminiClient.init(application)
        viewModelScope.launch {
            val displayName = if (isCloudSynced) {
                FirebaseRuntime.currentUserOrNull(application)?.displayName ?: "Julie User"
            } else {
                "Guest Mode"
            }
            repository.ensureUserProfile(displayName)
        }
        start24hWatchdogLoop()
    }

    val isGoogleGeminiConfiguredFlow: StateFlow<Boolean> = com.example.data.remote.GoogleGeminiClient.isConfiguredFlow

    fun saveGoogleGeminiKey(key: String) {
        com.example.data.remote.GoogleGeminiClient.saveApiKey(getApplication(), key)
        _infoNotice.value = "Google Gemini Free API Key saved in secure Keystore"
    }

    fun removeGoogleGeminiKey() {
        com.example.data.remote.GoogleGeminiClient.removeApiKey(getApplication())
        _infoNotice.value = "Google Gemini Key removed"
    }

    fun clearBannerError() {
        _bannerError.value = null
    }

    fun clearInfoNotice() {
        _infoNotice.value = null
    }

    fun updateNimInferenceSettings(settings: NimInferenceSettings) {
        NvidiaNimClient.updateSettings(getApplication(), settings)
    }

    fun updateRoutingConfig(
        profile: RoutingProfile = _routingConfig.value.profile,
        orchestrationMode: OrchestrationMode = _routingConfig.value.orchestrationMode,
        vendorPolicy: VendorPolicy = _routingConfig.value.vendorPolicy,
        managerEnabled: Boolean = _routingConfig.value.managerEnabled
    ) {
        _routingConfig.value = RoutingConfig(
            profile = profile,
            orchestrationMode = orchestrationMode,
            vendorPolicy = vendorPolicy,
            managerEnabled = managerEnabled
        )
    }

    fun testNvidiaNimConnection(onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            val res = NvidiaNimClient.testConnectionAndFetchModels(getApplication())
            onResult(res.message)
            if (res.ok) _infoNotice.value = res.message else _bannerError.value = res.message
        }
    }

    fun probeSingleModel(modelId: String) {
        viewModelScope.launch {
            _isProbingModels.value = true
            try {
                val rec = NvidiaNimClient.probeModel(getApplication(), modelId)
                _infoNotice.value = "${modelId.substringAfterLast('/')}: ${rec.status.label} (${rec.lastErrorSummary})"
            } finally {
                _isProbingModels.value = false
            }
        }
    }

    fun probePrimaryNemotronFleet() {
        if (_isProbingModels.value) return
        viewModelScope.launch {
            _isProbingModels.value = true
            try {
                val targets = listOf(
                    "nvidia/nemotron-3-ultra-550b-a55b",
                    "nvidia/nemotron-3-super-120b-a12b",
                    "nvidia/nemotron-3.5-bolt-e4b",
                    "nvidia/nemotron-3.5-lightning-30b-a3b",
                    "nvidia/llama-3.3-nemotron-super-49b-v1",
                    "nvidia/llama-3.1-nemotron-70b-instruct"
                )
                var verified = 0
                for (id in targets) {
                    val rec = NvidiaNimClient.probeModel(getApplication(), id)
                    if (rec.status.name == "VERIFIED") verified++
                    if (rec.lastErrorCode == 401) {
                        _bannerError.value = "HTTP 401: Invalid API key. Stopping probe immediately."
                        return@launch
                    }
                }
                _infoNotice.value = "Live probe finished: $verified/${targets.size} Nemotron models verified on your key."
            } finally {
                _isProbingModels.value = false
            }
        }
    }

    fun setModelDisabled(modelId: String, disabled: Boolean) {
        NvidiaNimClient.setModelDisabled(getApplication(), modelId, disabled)
    }

    // --- Memory Vault ---

    fun addMemoryNote(title: String, content: String, pinned: Boolean = true) {
        MemoryVaultStore.addNote(getApplication(), title, content, pinned)
        _infoNotice.value = "Saved to on-device Memory Vault"
    }

    fun toggleMemoryPin(id: String) {
        MemoryVaultStore.togglePin(getApplication(), id)
    }

    fun deleteMemoryNote(id: String) {
        MemoryVaultStore.deleteNote(getApplication(), id)
    }

    fun clearMemoryVault() {
        MemoryVaultStore.clearAll(getApplication())
        _infoNotice.value = "Cleared Memory Vault"
    }

    // --- Safe Validated Android Tools ---

    fun handleToolIntent(action: PendingToolAction) {
        if (action.type.requiresConfirmation) {
            _pendingToolConfirmation.value = action
            return
        }
        executeValidatedTool(action)
    }

    fun confirmPendingTool() {
        val action = _pendingToolConfirmation.value ?: return
        _pendingToolConfirmation.value = null
        executeValidatedTool(action)
    }

    fun dismissPendingTool() {
        _pendingToolConfirmation.value = null
    }

    private fun executeValidatedTool(action: PendingToolAction) {
        JulieToolRegistry.execute(getApplication(), action)
            .onSuccess { detail -> _infoNotice.value = detail }
            .onFailure { err -> _bannerError.value = err.localizedMessage ?: "Tool execution failed" }
    }

    // --- ImageKit Cloud Storage ---

    fun uploadCurrentScreenToImageKit(label: String = "julie_screen") {
        val bitmap = screenCaptureState.value.latestBitmap
        if (bitmap == null) {
            _bannerError.value = "Capture a screen frame or pick an image first."
            return
        }
        uploadBitmapToImageKit(bitmap, label)
    }

    fun uploadBitmapToImageKit(bitmap: Bitmap, label: String = "julie_image") {
        if (_isUploadingImageKit.value) return
        viewModelScope.launch {
            _isUploadingImageKit.value = true
            try {
                ImageKitClient.uploadBitmap(getApplication(), bitmap, label)
                    .onSuccess { asset ->
                        _infoNotice.value = "Uploaded to ImageKit: ${asset.name}"
                        if (MongoDbClient.isConfigured() && MongoDbClient.configFlow.value.autoSyncEnabled) {
                            MongoDbClient.insertMediaAsset(asset)
                        }
                    }
                    .onFailure { err ->
                        _bannerError.value = err.localizedMessage ?: "ImageKit upload failed"
                    }
            } finally {
                _isUploadingImageKit.value = false
            }
        }
    }

    fun deleteImageKitAsset(fileId: String) {
        viewModelScope.launch {
            ImageKitClient.deleteAsset(getApplication(), fileId)
                .onSuccess { _infoNotice.value = "Removed from ImageKit storage" }
                .onFailure { _bannerError.value = it.localizedMessage ?: "Delete failed" }
        }
    }

    fun testImageKitConnection(onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            val res = ImageKitClient.testConnection()
            onResult(res.message)
            if (res.ok) _infoNotice.value = res.message else _bannerError.value = res.message
        }
    }

    // --- MongoDB Cloud Connection ---

    fun testMongoDbConnection(onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            val res = MongoDbClient.testConnection()
            onResult(res.message)
            if (res.ok) _infoNotice.value = res.message else _bannerError.value = res.message
        }
    }

    fun syncAllToMongoDbNow(onResult: (String) -> Unit = {}) {
        if (_isSyncingMongo.value) return
        viewModelScope.launch {
            _isSyncingMongo.value = true
            try {
                val tasks = (tasksState.value as? DataUiState.Success)?.data.orEmpty()
                val messages = (messagesState.value as? DataUiState.Success)?.data.orEmpty()
                val assets = imageKitAssetsFlow.value
                val res = MongoDbClient.syncSnapshotNow(
                    context = getApplication(),
                    tasks = tasks,
                    messages = messages,
                    assets = assets
                )
                onResult(res.message)
                if (res.ok) _infoNotice.value = res.message else _bannerError.value = res.message
            } finally {
                _isSyncingMongo.value = false
            }
        }
    }

    // --- 24h Watchdog ---

    fun toggleWatchdog24h(enabled: Boolean) {
        _isWatchdog24hActive.value = enabled
        if (enabled) {
            start24hWatchdogLoop()
            _infoNotice.value = "Autonomous task runner armed (foreground/active session)"
        } else {
            watchdogJob?.cancel()
            watchdogJob = null
            _infoNotice.value = "Autonomous task runner paused"
        }
    }

    private fun start24hWatchdogLoop() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            while (isActive && _isWatchdog24hActive.value) {
                delay(15_000L)
                if (!NvidiaNimClient.isConfigured()) continue
                val currentTasks = (tasksState.value as? DataUiState.Success)?.data.orEmpty()
                val autoQueued = currentTasks.filter {
                    it.statusEnum() == TaskStatus.QUEUED &&
                        it.scheduleEnum() == ScheduleMode.CONTINUOUS_24H
                }
                if (autoQueued.isNotEmpty() && _liveSwarm.value.parallelTasksRunning == 0) {
                    runMultipleTasksInParallel(autoQueued.take(3))
                }
            }
        }
    }

    // --- Model & Swarm Preferences ---

    fun selectModel(modelId: String) {
        val current = userProfile.value
        viewModelScope.launch {
            val result = repository.updatePreferences(
                preferredModelId = modelId,
                swarmEnabled = if (modelId == AiModelsCatalog.AUTO_SWARM_ID) true else current.swarmEnabled,
                maxPowerEnabled = current.maxPowerEnabled,
                autoSearchEnabled = current.autoSearchEnabled
            )
            result.onFailure { _bannerError.value = it.localizedMessage ?: "Failed to save model choice" }
        }
    }

    fun updateSwarmSettings(
        swarmEnabled: Boolean = userProfile.value.swarmEnabled,
        maxPowerEnabled: Boolean = userProfile.value.maxPowerEnabled,
        autoSearchEnabled: Boolean = userProfile.value.autoSearchEnabled
    ) {
        val current = userProfile.value
        viewModelScope.launch {
            val result = repository.updatePreferences(
                preferredModelId = current.preferredModelId,
                swarmEnabled = swarmEnabled,
                maxPowerEnabled = maxPowerEnabled,
                autoSearchEnabled = autoSearchEnabled
            )
            result.onFailure { _bannerError.value = it.localizedMessage ?: "Failed to update settings" }
        }
    }

    // --- Live Single-Flight NVIDIA NIM Chat + Web Research + Vision ---

    fun cancelActiveChatRequest() {
        activeChatJob?.cancel()
        activeChatJob = null
        _liveSwarm.value = _liveSwarm.value.copy(
            isRunning = false,
            streamingText = "",
            thinkingTrace = "",
            progressPercent = 0
        )
        _infoNotice.value = "Cancelled active generation"
    }

    fun sendChatPrompt(
        prompt: String,
        forceGoogleSearch: Boolean? = null,
        includeAttachedScreen: Boolean = false
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _liveSwarm.value.isRunning) return

        val profile = userProfile.value
        val useSearch = forceGoogleSearch ?: profile.autoSearchEnabled
        val screenBitmap: Bitmap? = if (includeAttachedScreen) screenCaptureState.value.latestBitmap else null

        val pinnedMemories = MemoryVaultStore.pinnedSummaryContext()
        val recentHistory = (messagesState.value as? DataUiState.Success)?.data
            ?.takeLast(6)
            ?.joinToString("\n") { "${it.role}: ${it.content.take(400)}" }
            .orEmpty()
        val recentContext = buildString {
            if (pinnedMemories.isNotBlank()) {
                append("Pinned User Memory Vault:\n$pinnedMemories\n\n")
            }
            if (recentHistory.isNotBlank()) {
                append(recentHistory)
            }
        }

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            _bannerError.value = null
            repository.addMessage(
                role = MessageRole.USER,
                content = trimmed,
                modelUsed = "User",
                usedWebSearch = useSearch,
                hasScreenContext = screenBitmap != null
            )

            _liveSwarm.value = LiveSwarmActivity(
                isRunning = true,
                activeModelName = "Julie Nemotron Router",
                stageLabel = if (screenBitmap != null) {
                    "Analyzing live screen via NVIDIA Vision..."
                } else {
                    "Routing request (single-flight lock active)..."
                },
                progressPercent = 12,
                streamingText = "",
                thinkingTrace = ""
            )

            try {
                val result = swarmEngine.executeQuery(
                    userPrompt = trimmed,
                    conversationContext = recentContext,
                    preferredModelId = profile.preferredModelId,
                    swarmEnabled = profile.swarmEnabled,
                    maxPowerEnabled = profile.maxPowerEnabled,
                    useGoogleSearch = useSearch,
                    attachedBitmap = screenBitmap,
                    routingConfig = _routingConfig.value,
                    useSingleFlightLock = true,
                    onStageUpdate = { update: SwarmStepUpdate ->
                        _liveSwarm.value = _liveSwarm.value.copy(
                            activeModelName = update.activeModelName,
                            stageLabel = update.stageLabel,
                            progressPercent = update.progressPercent,
                            steps = update.completedSteps
                        )
                    },
                    onThinkingToken = { partialThink ->
                        _liveSwarm.value = _liveSwarm.value.copy(thinkingTrace = partialThink)
                    },
                    onStreamToken = { partialText ->
                        _liveSwarm.value = _liveSwarm.value.copy(streamingText = partialText)
                    }
                )

                _lastDiagnostics.value = result.diagnostics

                val storedContent = if (result.reasoningTrace.isNotBlank()) {
                    "Thinking Summary:\n${result.reasoningTrace.take(800)}\n\n${result.finalText}"
                } else {
                    result.finalText
                }

                repository.addMessage(
                    role = MessageRole.ASSISTANT,
                    content = storedContent,
                    modelUsed = result.primaryEngineLabel,
                    swarmModels = result.collaboratingModels,
                    searchSources = result.searchSources,
                    usedWebSearch = result.usedWebSearch,
                    hasScreenContext = screenBitmap != null
                )
                repository.incrementSwarmRuns()

                if (MongoDbClient.isConfigured() && MongoDbClient.configFlow.value.autoSyncEnabled) {
                    MongoDbClient.insertMessage(
                        ChatMessage(
                            id = "msg_${System.currentTimeMillis()}",
                            role = MessageRole.ASSISTANT.name,
                            content = storedContent,
                            modelUsed = result.primaryEngineLabel,
                            swarmModels = result.collaboratingModels,
                            usedWebSearch = result.usedWebSearch,
                            hasScreenContext = screenBitmap != null
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat execution failed", e)
                _bannerError.value = e.localizedMessage ?: "NVIDIA NIM execution failed. Check 33 Models diagnostics."
            } finally {
                _liveSwarm.value = _liveSwarm.value.copy(
                    isRunning = false,
                    streamingText = "",
                    thinkingTrace = "",
                    progressPercent = 0
                )
            }
        }
    }

    fun clearChatHistory() {
        val ids = (messagesState.value as? DataUiState.Success)?.data?.map { it.id }.orEmpty()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.clearChatHistory(ids)
                .onSuccess { _infoNotice.value = "Chat history cleared" }
                .onFailure { _bannerError.value = it.localizedMessage ?: "Failed to clear chat" }
        }
    }

    // --- Autonomous Multi-Task Execution ---

    fun createTask(
        title: String,
        prompt: String,
        category: TaskCategory,
        priority: TaskPriority,
        scheduleMode: ScheduleMode,
        runImmediately: Boolean
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _bannerError.value = "Please enter a task title."
            return
        }
        val cleanPrompt = prompt.trim().ifBlank { cleanTitle }
        val profile = userProfile.value
        val recommended = AiModelsCatalog.selectBestSwarmForPrompt(
            prompt = cleanPrompt,
            hasImage = false,
            needsSearch = category == TaskCategory.RESEARCH || profile.autoSearchEnabled,
            pinnedModelId = profile.preferredModelId
        ).map { it.displayName }

        viewModelScope.launch {
            val result = repository.createTask(
                title = cleanTitle,
                prompt = cleanPrompt,
                category = category,
                priority = priority,
                scheduleMode = scheduleMode,
                assignedModels = recommended
            )
            result.onSuccess { newId ->
                val newTask = AssistantTask(
                    id = newId,
                    title = cleanTitle,
                    prompt = cleanPrompt,
                    category = category.name,
                    priority = priority.name,
                    status = TaskStatus.QUEUED.name,
                    scheduleMode = scheduleMode.name,
                    assignedModels = recommended
                )
                if (MongoDbClient.isConfigured() && MongoDbClient.configFlow.value.autoSyncEnabled) {
                    MongoDbClient.upsertTask(newTask)
                }
                if (runImmediately) {
                    executeSingleTask(newTask)
                } else {
                    _infoNotice.value = "Task queued for Julie NVIDIA Swarm"
                }
            }.onFailure {
                _bannerError.value = it.localizedMessage ?: "Failed to create task"
            }
        }
    }

    fun splitGoalAndQueueTasks(goal: String, autoExecute: Boolean) {
        val cleanGoal = goal.trim()
        if (cleanGoal.isEmpty() || _isSplittingGoal.value) return

        viewModelScope.launch {
            _isSplittingGoal.value = true
            _bannerError.value = null
            try {
                val planned = swarmEngine.splitGoalIntoTasks(cleanGoal)
                val createdTasks = mutableListOf<AssistantTask>()
                val profile = userProfile.value
                for (item in planned) {
                    val cat = TaskCategory.fromRaw(item.category)
                    val prio = TaskPriority.fromRaw(item.priority)
                    val models = AiModelsCatalog.selectBestSwarmForPrompt(
                        prompt = item.prompt,
                        hasImage = false,
                        needsSearch = cat == TaskCategory.RESEARCH || profile.autoSearchEnabled,
                        pinnedModelId = profile.preferredModelId
                    ).map { it.displayName }

                    val created = repository.createTask(
                        title = item.title,
                        prompt = item.prompt,
                        category = cat,
                        priority = prio,
                        scheduleMode = ScheduleMode.ONCE,
                        assignedModels = models
                    )
                    created.onSuccess { id ->
                        val t = AssistantTask(
                            id = id,
                            title = item.title,
                            prompt = item.prompt,
                            category = cat.name,
                            priority = prio.name,
                            status = TaskStatus.QUEUED.name,
                            scheduleMode = ScheduleMode.ONCE.name,
                            assignedModels = models
                        )
                        createdTasks.add(t)
                        if (MongoDbClient.isConfigured() && MongoDbClient.configFlow.value.autoSyncEnabled) {
                            MongoDbClient.upsertTask(t)
                        }
                    }
                }
                _infoNotice.value = "Julie created ${createdTasks.size} parallel tasks"
                if (autoExecute && createdTasks.isNotEmpty()) {
                    runMultipleTasksInParallel(createdTasks)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to split goal", e)
                _bannerError.value = e.localizedMessage ?: "Could not break down goal"
            } finally {
                _isSplittingGoal.value = false
            }
        }
    }

    fun runAllQueuedTasksInParallel() {
        val tasks = (tasksState.value as? DataUiState.Success)?.data
            ?.filter { it.statusEnum() == TaskStatus.QUEUED }
            .orEmpty()
        if (tasks.isEmpty()) {
            _infoNotice.value = "No queued tasks waiting to run."
            return
        }
        runMultipleTasksInParallel(tasks)
    }

    fun runSingleTask(task: AssistantTask) {
        if (task.statusEnum() == TaskStatus.COMPLETED) return
        viewModelScope.launch {
            executeSingleTask(task)
        }
    }

    fun duplicateAndRunTask(task: AssistantTask) {
        createTask(
            title = task.title,
            prompt = task.prompt,
            category = task.categoryEnum(),
            priority = task.priorityEnum(),
            scheduleMode = task.scheduleEnum(),
            runImmediately = true
        )
    }

    fun retryTask(task: AssistantTask) {
        viewModelScope.launch {
            repository.retryFailedTask(task.id).onSuccess {
                executeSingleTask(task.copy(status = TaskStatus.QUEUED.name))
            }.onFailure {
                _bannerError.value = it.localizedMessage ?: "Failed to retry task"
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId).onFailure {
                _bannerError.value = it.localizedMessage ?: "Failed to delete task"
            }
        }
    }

    private fun runMultipleTasksInParallel(tasks: List<AssistantTask>) {
        viewModelScope.launch {
            tasks.map { task ->
                async {
                    parallelSemaphore.withPermit {
                        executeSingleTask(task)
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun executeSingleTask(task: AssistantTask) {
        val profile = userProfile.value
        val activeCount = _liveSwarm.value.parallelTasksRunning + 1
        _liveSwarm.value = _liveSwarm.value.copy(
            parallelTasksRunning = activeCount,
            activeModelName = task.assignedModels.firstOrNull() ?: "Julie Nemotron",
            stageLabel = "Running $activeCount autonomous task${if (activeCount > 1) "s" else ""}..."
        )

        val steps = mutableListOf("Julie Nemotron activated for \"${task.title}\"")
        repository.updateTaskExecution(
            taskId = task.id,
            status = TaskStatus.RUNNING,
            assignedModels = task.assignedModels,
            resultText = null,
            executionSteps = steps,
            progressPercent = 18
        )

        try {
            val useSearch = task.categoryEnum() == TaskCategory.RESEARCH || profile.autoSearchEnabled
            val result = swarmEngine.executeQuery(
                userPrompt = "Task Title: ${task.title}\nCategory: ${task.categoryEnum().label}\nInstructions: ${task.prompt}",
                preferredModelId = profile.preferredModelId,
                swarmEnabled = profile.swarmEnabled,
                maxPowerEnabled = profile.maxPowerEnabled,
                useGoogleSearch = useSearch,
                routingConfig = _routingConfig.value,
                useSingleFlightLock = false,
                onStageUpdate = { update ->
                    viewModelScope.launch {
                        if (update.progressPercent in 25..90) {
                            repository.updateTaskExecution(
                                taskId = task.id,
                                status = TaskStatus.RUNNING,
                                assignedModels = task.assignedModels,
                                resultText = null,
                                executionSteps = (steps + update.completedSteps).distinct().take(12),
                                progressPercent = update.progressPercent
                            )
                        }
                    }
                }
            )

            val allSteps = (steps + result.steps).distinct().take(12)
            repository.updateTaskExecution(
                taskId = task.id,
                status = TaskStatus.COMPLETED,
                assignedModels = result.collaboratingModels,
                resultText = result.finalText,
                executionSteps = allSteps,
                progressPercent = 100
            )
            repository.incrementSwarmRuns()
            if (MongoDbClient.isConfigured() && MongoDbClient.configFlow.value.autoSyncEnabled) {
                MongoDbClient.upsertTask(
                    task.copy(
                        status = TaskStatus.COMPLETED.name,
                        assignedModels = result.collaboratingModels,
                        resultText = result.finalText,
                        progressPercent = 100L
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed: ${task.id}", e)
            repository.updateTaskExecution(
                taskId = task.id,
                status = TaskStatus.FAILED,
                assignedModels = task.assignedModels,
                resultText = "Execution error: ${e.localizedMessage ?: "Unknown failure"}",
                executionSteps = (steps + "Error: ${e.localizedMessage ?: "Failed"}").take(12),
                progressPercent = 0
            )
            _bannerError.value = "Task \"${task.title}\" failed: ${e.localizedMessage}"
        } finally {
            val remaining = (_liveSwarm.value.parallelTasksRunning - 1).coerceAtLeast(0)
            _liveSwarm.value = _liveSwarm.value.copy(
                parallelTasksRunning = remaining,
                stageLabel = if (remaining > 0) "Running $remaining autonomous tasks..." else ""
            )
        }
    }

    class Factory(
        private val application: Application,
        private val isCloudSynced: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuraViewModel(application = application, isCloudSynced = isCloudSynced) as T
        }
    }
}
