package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AuraSwarmEngine
import com.example.data.ai.SwarmStepUpdate
import com.example.data.local.LocalAuraStore
import com.example.data.model.AiModelsCatalog
import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.ScheduleMode
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UserProfile
import com.example.data.remote.NvidiaNimClient
import com.example.data.repository.AuraDataSource
import com.example.data.repository.AuraRepository
import com.example.firebase.FirebaseRuntime
import com.example.screen.ScreenCaptureState
import com.example.screen.ScreenCaptureStore
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
    val parallelTasksRunning: Int = 0
)

class AuraViewModel(
    application: Application,
    val isCloudSynced: Boolean,
    private val repository: AuraDataSource = if (isCloudSynced && FirebaseRuntime.isFirebaseConfigured(application)) {
        AuraRepository(application.applicationContext)
    } else {
        LocalAuraStore(application.applicationContext)
    },
    private val swarmEngine: AuraSwarmEngine = AuraSwarmEngine(application.applicationContext)
) : AndroidViewModel(application) {

    val screenCaptureState: StateFlow<ScreenCaptureState> = ScreenCaptureStore.state
    val isNvidiaNimConfiguredFlow: StateFlow<Boolean> = NvidiaNimClient.isConfiguredFlow

    val userProfile: StateFlow<UserProfile> = repository.observeUserProfile()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.e(TAG, "Profile stream error: ${(firestoreError ?: error).message}")
            emit(UserProfile(displayName = if (isCloudSynced) "Aura User" else "Guest Mode"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserProfile(displayName = if (isCloudSynced) "Aura User" else "Guest Mode")
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

    private val _bannerError = MutableStateFlow<String?>(null)
    val bannerError: StateFlow<String?> = _bannerError.asStateFlow()

    private val _infoNotice = MutableStateFlow<String?>(null)
    val infoNotice: StateFlow<String?> = _infoNotice.asStateFlow()

    private val _isWatchdog24hActive = MutableStateFlow(true)
    val isWatchdog24hActive: StateFlow<Boolean> = _isWatchdog24hActive.asStateFlow()

    private val _isSplittingGoal = MutableStateFlow(false)
    val isSplittingGoal: StateFlow<Boolean> = _isSplittingGoal.asStateFlow()

    private val parallelSemaphore = Semaphore(3)
    private var watchdogJob: Job? = null

    val isNvidiaNimConfigured: Boolean
        get() = swarmEngine.isNvidiaNimDirectActive()

    init {
        viewModelScope.launch {
            val displayName = if (isCloudSynced) {
                FirebaseRuntime.currentUserOrNull(application)?.displayName ?: "Aura User"
            } else {
                "Guest Mode"
            }
            repository.ensureUserProfile(displayName)
        }
        start24hWatchdogLoop()
    }

    fun clearBannerError() {
        _bannerError.value = null
    }

    fun clearInfoNotice() {
        _infoNotice.value = null
    }

    fun toggleWatchdog24h(enabled: Boolean) {
        _isWatchdog24hActive.value = enabled
        if (enabled) {
            start24hWatchdogLoop()
            _infoNotice.value = "24/7 Autonomous Task Engine armed"
        } else {
            watchdogJob?.cancel()
            watchdogJob = null
            _infoNotice.value = "24/7 Autonomous Engine paused"
        }
    }

    private fun start24hWatchdogLoop() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            while (isActive && _isWatchdog24hActive.value) {
                delay(15_000L)
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

    // --- Live Chat + Google Search + Screen Vision ---

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

        val recentContext = (messagesState.value as? DataUiState.Success)?.data
            ?.takeLast(6)
            ?.joinToString("\n") { "${it.role}: ${it.content.take(400)}" }
            .orEmpty()

        viewModelScope.launch {
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
                activeModelName = if (profile.swarmEnabled) "Aura Swarm Router" else "Aura AI",
                stageLabel = if (screenBitmap != null) {
                    "Analyzing shared screen frame..."
                } else if (useSearch) {
                    "Routing 33-model swarm + Google Search..."
                } else {
                    "Dispatching best models..."
                },
                progressPercent = 12,
                streamingText = ""
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
                    onStageUpdate = { update: SwarmStepUpdate ->
                        _liveSwarm.value = _liveSwarm.value.copy(
                            activeModelName = update.activeModelName,
                            stageLabel = update.stageLabel,
                            progressPercent = update.progressPercent,
                            steps = update.completedSteps
                        )
                    },
                    onStreamToken = { partialText ->
                        _liveSwarm.value = _liveSwarm.value.copy(streamingText = partialText)
                    }
                )

                repository.addMessage(
                    role = MessageRole.ASSISTANT,
                    content = result.finalText,
                    modelUsed = result.primaryEngineLabel,
                    swarmModels = result.collaboratingModels,
                    searchSources = result.searchSources,
                    usedWebSearch = result.usedWebSearch,
                    hasScreenContext = screenBitmap != null
                )
                repository.incrementSwarmRuns()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat swarm execution failed", e)
                _bannerError.value = e.localizedMessage ?: "AI execution failed. Please try again."
            } finally {
                _liveSwarm.value = _liveSwarm.value.copy(
                    isRunning = false,
                    streamingText = "",
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

    // --- 24/7 Autonomous Multi-Task Execution ---

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
                if (runImmediately) {
                    val tempTask = AssistantTask(
                        id = newId,
                        title = cleanTitle,
                        prompt = cleanPrompt,
                        category = category.name,
                        priority = priority.name,
                        status = TaskStatus.QUEUED.name,
                        scheduleMode = scheduleMode.name,
                        assignedModels = recommended
                    )
                    executeSingleTask(tempTask)
                } else {
                    _infoNotice.value = "Task queued for Aura 24h Swarm"
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
                        createdTasks.add(
                            AssistantTask(
                                id = id,
                                title = item.title,
                                prompt = item.prompt,
                                category = cat.name,
                                priority = prio.name,
                                status = TaskStatus.QUEUED.name,
                                scheduleMode = ScheduleMode.ONCE.name,
                                assignedModels = models
                            )
                        )
                    }
                }
                _infoNotice.value = "AI created ${createdTasks.size} parallel tasks"
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
            activeModelName = task.assignedModels.firstOrNull() ?: "Aura Swarm",
            stageLabel = "Running ${activeCount} autonomous task${if (activeCount > 1) "s" else ""}..."
        )

        val steps = mutableListOf("Swarm activated for \"${task.title}\"")
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
