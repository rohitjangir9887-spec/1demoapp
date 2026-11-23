package com.example.data.local

import android.content.Context
import com.example.data.model.AiModelsCatalog
import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.ScheduleMode
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UserProfile
import com.example.data.repository.AuraDataSource
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local on-device store used when the user chooses optional Guest Mode (no Google Sign-In).
 * Never accesses Firestore, upholding zero-trust cloud security while keeping all app features
 * (Chat, 24h Tasks, 33 Models, Voice, Screen Share) fully persistent across restarts.
 */
class LocalAuraStore(context: Context) : AuraDataSource {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    private val _profile = MutableStateFlow(loadProfile())
    private val _tasks = MutableStateFlow(loadTasks())
    private val _messages = MutableStateFlow(loadMessages())

    override suspend fun ensureUserProfile(displayName: String?): Result<Unit> {
        synchronized(lock) {
            val current = _profile.value
            val updated = if (current.userId.isBlank()) {
                current.copy(
                    userId = GUEST_USER_ID,
                    displayName = displayName?.takeIf { it.isNotBlank() } ?: "Guest Mode"
                )
            } else {
                current
            }
            saveProfileLocked(updated)
        }
        return Result.success(Unit)
    }

    override fun observeUserProfile(): Flow<UserProfile> = _profile.asStateFlow()

    override suspend fun updatePreferences(
        preferredModelId: String,
        swarmEnabled: Boolean,
        maxPowerEnabled: Boolean,
        autoSearchEnabled: Boolean
    ): Result<Unit> {
        synchronized(lock) {
            val updated = _profile.value.copy(
                preferredModelId = preferredModelId.ifBlank { AiModelsCatalog.AUTO_SWARM_ID }.take(120),
                swarmEnabled = swarmEnabled,
                maxPowerEnabled = maxPowerEnabled,
                autoSearchEnabled = autoSearchEnabled,
                updatedAt = Timestamp.now()
            )
            saveProfileLocked(updated)
        }
        return Result.success(Unit)
    }

    override suspend fun incrementSwarmRuns(): Result<Unit> {
        synchronized(lock) {
            val updated = _profile.value.copy(
                totalSwarmRuns = _profile.value.totalSwarmRuns + 1L,
                updatedAt = Timestamp.now()
            )
            saveProfileLocked(updated)
        }
        return Result.success(Unit)
    }

    override fun observeTasks(): Flow<List<AssistantTask>> = _tasks.asStateFlow()

    override suspend fun createTask(
        title: String,
        prompt: String,
        category: TaskCategory,
        priority: TaskPriority,
        scheduleMode: ScheduleMode,
        assignedModels: List<String>
    ): Result<String> {
        val cleanTitle = title.trim().take(160)
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Task title cannot be empty"))
        }
        val cleanPrompt = prompt.trim().ifBlank { cleanTitle }.take(4000)
        val newId = "local_${UUID.randomUUID().toString().take(12)}"
        val now = Timestamp.now()
        val task = AssistantTask(
            id = newId,
            userId = GUEST_USER_ID,
            title = cleanTitle,
            prompt = cleanPrompt,
            category = category.name,
            priority = priority.name,
            status = TaskStatus.QUEUED.name,
            scheduleMode = scheduleMode.name,
            assignedModels = assignedModels.take(6),
            resultText = null,
            executionSteps = listOf("Queued for 24h Aura Swarm"),
            progressPercent = 0L,
            createdAt = now,
            updatedAt = now
        )
        synchronized(lock) {
            val updated = listOf(task) + _tasks.value
            saveTasksLocked(updated)
        }
        return Result.success(newId)
    }

    override suspend fun updateTaskExecution(
        taskId: String,
        status: TaskStatus,
        assignedModels: List<String>,
        resultText: String?,
        executionSteps: List<String>,
        progressPercent: Int
    ): Result<Unit> {
        synchronized(lock) {
            val updated = _tasks.value.map { existing ->
                if (existing.id == taskId) {
                    existing.copy(
                        status = status.name,
                        assignedModels = assignedModels.take(6).map { it.take(80) },
                        resultText = resultText?.take(24000) ?: existing.resultText,
                        executionSteps = executionSteps.take(12).map { it.take(240) },
                        progressPercent = progressPercent.coerceIn(0, 100).toLong(),
                        updatedAt = Timestamp.now()
                    )
                } else {
                    existing
                }
            }
            saveTasksLocked(updated)
        }
        return Result.success(Unit)
    }

    override suspend fun retryFailedTask(taskId: String): Result<Unit> {
        synchronized(lock) {
            val updated = _tasks.value.map { existing ->
                if (existing.id == taskId) {
                    existing.copy(
                        status = TaskStatus.QUEUED.name,
                        resultText = "",
                        executionSteps = listOf("Re-queued for Aura Swarm"),
                        progressPercent = 0L,
                        updatedAt = Timestamp.now()
                    )
                } else {
                    existing
                }
            }
            saveTasksLocked(updated)
        }
        return Result.success(Unit)
    }

    override suspend fun deleteTask(taskId: String): Result<Unit> {
        synchronized(lock) {
            val updated = _tasks.value.filterNot { it.id == taskId }
            saveTasksLocked(updated)
        }
        return Result.success(Unit)
    }

    override fun observeMessages(): Flow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun addMessage(
        role: MessageRole,
        content: String,
        modelUsed: String,
        swarmModels: List<String>,
        searchSources: List<String>,
        usedWebSearch: Boolean,
        hasScreenContext: Boolean
    ): Result<Unit> {
        val msg = ChatMessage(
            id = "msg_${UUID.randomUUID().toString().take(12)}",
            userId = GUEST_USER_ID,
            role = role.name,
            content = content.trim().ifBlank { "..." }.take(24000),
            modelUsed = modelUsed.trim().ifBlank { "Aura Swarm" }.take(120),
            swarmModels = swarmModels.take(6),
            searchSources = searchSources.take(10),
            usedWebSearch = usedWebSearch,
            hasScreenContext = hasScreenContext,
            createdAt = Timestamp.now()
        )
        synchronized(lock) {
            val updated = (_messages.value + msg).takeLast(120)
            saveMessagesLocked(updated)
        }
        return Result.success(Unit)
    }

    override suspend fun clearChatHistory(messageIds: List<String>): Result<Unit> {
        synchronized(lock) {
            val idSet = messageIds.toSet()
            val updated = _messages.value.filterNot { it.id in idSet }
            saveMessagesLocked(updated)
        }
        return Result.success(Unit)
    }

    // --- Serialization Helpers ---

    private fun saveProfileLocked(profile: UserProfile) {
        _profile.value = profile
        val json = JSONObject().apply {
            put("userId", profile.userId)
            put("displayName", profile.displayName)
            put("preferredModelId", profile.preferredModelId)
            put("swarmEnabled", profile.swarmEnabled)
            put("maxPowerEnabled", profile.maxPowerEnabled)
            put("autoSearchEnabled", profile.autoSearchEnabled)
            put("totalSwarmRuns", profile.totalSwarmRuns)
        }
        prefs.edit().putString(KEY_PROFILE, json.toString()).apply()
    }

    private fun loadProfile(): UserProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return UserProfile(
            userId = GUEST_USER_ID,
            displayName = "Guest Mode"
        )
        return try {
            val obj = JSONObject(raw)
            UserProfile(
                userId = obj.optString("userId", GUEST_USER_ID),
                displayName = obj.optString("displayName", "Guest Mode"),
                preferredModelId = obj.optString("preferredModelId", AiModelsCatalog.AUTO_SWARM_ID),
                swarmEnabled = obj.optBoolean("swarmEnabled", true),
                maxPowerEnabled = obj.optBoolean("maxPowerEnabled", true),
                autoSearchEnabled = obj.optBoolean("autoSearchEnabled", true),
                totalSwarmRuns = obj.optLong("totalSwarmRuns", 0L)
            )
        } catch (_: Exception) {
            UserProfile(userId = GUEST_USER_ID, displayName = "Guest Mode")
        }
    }

    private fun saveTasksLocked(tasks: List<AssistantTask>) {
        _tasks.value = tasks
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("userId", t.userId)
                    put("title", t.title)
                    put("prompt", t.prompt)
                    put("category", t.category)
                    put("priority", t.priority)
                    put("status", t.status)
                    put("scheduleMode", t.scheduleMode)
                    put("assignedModels", JSONArray(t.assignedModels))
                    put("resultText", t.resultText ?: JSONObject.NULL)
                    put("executionSteps", JSONArray(t.executionSteps))
                    put("progressPercent", t.progressPercent)
                }
            )
        }
        prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    private fun loadTasks(): List<AssistantTask> {
        val raw = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        AssistantTask(
                            id = obj.optString("id"),
                            userId = obj.optString("userId", GUEST_USER_ID),
                            title = obj.optString("title"),
                            prompt = obj.optString("prompt"),
                            category = obj.optString("category", TaskCategory.GENERAL.name),
                            priority = obj.optString("priority", TaskPriority.NORMAL.name),
                            status = obj.optString("status", TaskStatus.QUEUED.name),
                            scheduleMode = obj.optString("scheduleMode", ScheduleMode.ONCE.name),
                            assignedModels = obj.optJSONArray("assignedModels").toStringList(),
                            resultText = if (obj.isNull("resultText")) null else obj.optString("resultText"),
                            executionSteps = obj.optJSONArray("executionSteps").toStringList(),
                            progressPercent = obj.optLong("progressPercent", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveMessagesLocked(messages: List<ChatMessage>) {
        _messages.value = messages
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("userId", m.userId)
                    put("role", m.role)
                    put("content", m.content)
                    put("modelUsed", m.modelUsed)
                    put("swarmModels", JSONArray(m.swarmModels))
                    put("searchSources", JSONArray(m.searchSources))
                    put("usedWebSearch", m.usedWebSearch)
                    put("hasScreenContext", m.hasScreenContext)
                }
            )
        }
        prefs.edit().putString(KEY_MESSAGES, arr.toString()).apply()
    }

    private fun loadMessages(): List<ChatMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        ChatMessage(
                            id = obj.optString("id"),
                            userId = obj.optString("userId", GUEST_USER_ID),
                            role = obj.optString("role", MessageRole.ASSISTANT.name),
                            content = obj.optString("content"),
                            modelUsed = obj.optString("modelUsed", "Aura Swarm"),
                            swarmModels = obj.optJSONArray("swarmModels").toStringList(),
                            searchSources = obj.optJSONArray("searchSources").toStringList(),
                            usedWebSearch = obj.optBoolean("usedWebSearch", false),
                            hasScreenContext = obj.optBoolean("hasScreenContext", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                add(optString(i))
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "aura_guest_local_store"
        private const val KEY_PROFILE = "guest_profile_json"
        private const val KEY_TASKS = "guest_tasks_json"
        private const val KEY_MESSAGES = "guest_messages_json"
        const val GUEST_USER_ID = "guest_local_user"
    }
}
