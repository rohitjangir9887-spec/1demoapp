package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskCategory(val label: String) {
    GENERAL("General"),
    RESEARCH("Research"),
    CODE("Code"),
    WRITING("Writing"),
    PLANNING("Planning"),
    SCREEN("Screen"),
    AUTOMATION("24h Auto");

    companion object {
        fun fromRaw(raw: String): TaskCategory =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

enum class TaskPriority(val label: String) {
    LOW("Low"),
    NORMAL("Normal"),
    HIGH("High"),
    URGENT("Urgent");

    companion object {
        fun fromRaw(raw: String): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class TaskStatus(val label: String) {
    QUEUED("Queued"),
    RUNNING("Running"),
    COMPLETED("Completed"),
    FAILED("Failed");

    companion object {
        fun fromRaw(raw: String): TaskStatus =
            entries.firstOrNull { it.name == raw } ?: QUEUED
    }
}

enum class ScheduleMode(val label: String) {
    ONCE("Run Once"),
    HOURLY("Every 1h"),
    DAILY("Daily 24h"),
    CONTINUOUS_24H("24h Always-On");

    companion object {
        fun fromRaw(raw: String): ScheduleMode =
            entries.firstOrNull { it.name == raw } ?: ONCE
    }
}

enum class MessageRole {
    USER,
    ASSISTANT;

    companion object {
        fun fromRaw(raw: String): MessageRole =
            entries.firstOrNull { it.name == raw } ?: ASSISTANT
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Aura User",
    val preferredModelId: String = AiModelsCatalog.AUTO_SWARM_ID,
    val swarmEnabled: Boolean = true,
    val maxPowerEnabled: Boolean = true,
    val autoSearchEnabled: Boolean = true,
    val totalSwarmRuns: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "displayName" to displayName.trim().ifBlank { "Aura User" }.take(80),
        "preferredModelId" to preferredModelId.ifBlank { AiModelsCatalog.AUTO_SWARM_ID }.take(120),
        "swarmEnabled" to swarmEnabled,
        "maxPowerEnabled" to maxPowerEnabled,
        "autoSearchEnabled" to autoSearchEnabled,
        "totalSwarmRuns" to totalSwarmRuns.coerceIn(0L, 1_000_000L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AssistantTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val prompt: String = "",
    val category: String = TaskCategory.GENERAL.name,
    val priority: String = TaskPriority.NORMAL.name,
    val status: String = TaskStatus.QUEUED.name,
    val scheduleMode: String = ScheduleMode.ONCE.name,
    val assignedModels: List<String> = emptyList(),
    val resultText: String? = null,
    val executionSteps: List<String> = emptyList(),
    val progressPercent: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): TaskCategory = TaskCategory.fromRaw(category)
    fun priorityEnum(): TaskPriority = TaskPriority.fromRaw(priority)
    fun statusEnum(): TaskStatus = TaskStatus.fromRaw(status)
    fun scheduleEnum(): ScheduleMode = ScheduleMode.fromRaw(scheduleMode)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().ifBlank { "Untitled Task" }.take(160),
        "prompt" to prompt.trim().ifBlank { title.trim().ifBlank { "Execute task" } }.take(4000),
        "category" to TaskCategory.fromRaw(category).name,
        "priority" to TaskPriority.fromRaw(priority).name,
        "status" to TaskStatus.QUEUED.name,
        "scheduleMode" to ScheduleMode.fromRaw(scheduleMode).name,
        "assignedModels" to assignedModels.take(6).map { it.take(80) },
        "resultText" to resultText?.take(24000),
        "executionSteps" to executionSteps.take(12).map { it.take(240) },
        "progressPercent" to progressPercent.coerceIn(0L, 100L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = MessageRole.USER.name,
    val content: String = "",
    val modelUsed: String = "Aura Swarm",
    val swarmModels: List<String> = emptyList(),
    val searchSources: List<String> = emptyList(),
    val usedWebSearch: Boolean = false,
    val hasScreenContext: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): MessageRole = MessageRole.fromRaw(role)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "role" to MessageRole.fromRaw(role).name,
        "content" to content.trim().ifBlank { "..." }.take(24000),
        "modelUsed" to modelUsed.trim().ifBlank { "Aura Swarm" }.take(120),
        "swarmModels" to swarmModels.take(6).map { it.take(80) },
        "searchSources" to searchSources.take(10).map { it.take(300) },
        "usedWebSearch" to usedWebSearch,
        "hasScreenContext" to hasScreenContext,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
