package com.example.data.repository

import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.ScheduleMode
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Unified storage contract implemented by:
 * - [AuraRepository] (Cloud Firestore when signed in with Google)
 * - [com.example.data.local.LocalAuraStore] (On-device storage when in Guest Mode)
 */
interface AuraDataSource {
    suspend fun ensureUserProfile(displayName: String?): Result<Unit>
    fun observeUserProfile(): Flow<UserProfile>
    suspend fun updatePreferences(
        preferredModelId: String,
        swarmEnabled: Boolean,
        maxPowerEnabled: Boolean,
        autoSearchEnabled: Boolean
    ): Result<Unit>

    suspend fun incrementSwarmRuns(): Result<Unit>

    fun observeTasks(): Flow<List<AssistantTask>>
    suspend fun createTask(
        title: String,
        prompt: String,
        category: TaskCategory,
        priority: TaskPriority,
        scheduleMode: ScheduleMode,
        assignedModels: List<String>
    ): Result<String>

    suspend fun updateTaskExecution(
        taskId: String,
        status: TaskStatus,
        assignedModels: List<String>,
        resultText: String?,
        executionSteps: List<String>,
        progressPercent: Int
    ): Result<Unit>

    suspend fun retryFailedTask(taskId: String): Result<Unit>
    suspend fun deleteTask(taskId: String): Result<Unit>

    fun observeMessages(): Flow<List<ChatMessage>>
    suspend fun addMessage(
        role: MessageRole,
        content: String,
        modelUsed: String,
        swarmModels: List<String> = emptyList(),
        searchSources: List<String> = emptyList(),
        usedWebSearch: Boolean = false,
        hasScreenContext: Boolean = false
    ): Result<Unit>

    suspend fun clearChatHistory(messageIds: List<String>): Result<Unit>
}
