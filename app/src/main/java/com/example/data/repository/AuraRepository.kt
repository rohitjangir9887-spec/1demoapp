package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AiModelsCatalog
import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.ScheduleMode
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.UserProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AuraRepository(private val db: FirebaseFirestore) : AuraDataSource {

    // CRITICAL: Always initialize with the named R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    override suspend fun ensureUserProfile(displayName: String?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = displayName?.trim()?.takeIf { it.isNotBlank() } ?: "Aura User"
                val initialProfile = UserProfile(
                    userId = uid,
                    displayName = safeName,
                    preferredModelId = AiModelsCatalog.AUTO_SWARM_ID,
                    swarmEnabled = true,
                    maxPowerEnabled = true,
                    autoSearchEnabled = true,
                    totalSwarmRuns = 0L
                )
                docRef.set(initialProfile.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override fun observeUserProfile(): Flow<UserProfile> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: UserProfile(userId = uid)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun updatePreferences(
        preferredModelId: String,
        swarmEnabled: Boolean,
        maxPowerEnabled: Boolean,
        autoSearchEnabled: Boolean
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            docRef.update(
                mapOf(
                    "preferredModelId" to preferredModelId.ifBlank { AiModelsCatalog.AUTO_SWARM_ID }.take(120),
                    "swarmEnabled" to swarmEnabled,
                    "maxPowerEnabled" to maxPowerEnabled,
                    "autoSearchEnabled" to autoSearchEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun incrementSwarmRuns(): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            docRef.update(
                mapOf(
                    "totalSwarmRuns" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 24h Autonomous Tasks ---

    override fun observeTasks(): Flow<List<AssistantTask>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/tasks"
        emitAll(
            db.collection("users").document(uid).collection("tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AssistantTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun createTask(
        title: String,
        prompt: String,
        category: TaskCategory,
        priority: TaskPriority,
        scheduleMode: ScheduleMode,
        assignedModels: List<String>
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(160)
            val cleanPrompt = prompt.trim().ifBlank { cleanTitle }.take(4000)
            require(cleanTitle.isNotEmpty()) { "Task title cannot be empty" }

            val docRef = db.collection("users").document(uid).collection("tasks").document()
            path = docRef.path
            val task = AssistantTask(
                userId = uid,
                title = cleanTitle,
                prompt = cleanPrompt,
                category = category.name,
                priority = priority.name,
                status = TaskStatus.QUEUED.name,
                scheduleMode = scheduleMode.name,
                assignedModels = assignedModels.take(6),
                resultText = null,
                executionSteps = listOf("Queued for 24h Aura Swarm"),
                progressPercent = 0L
            )
            docRef.set(task.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateTaskExecution(
        taskId: String,
        status: TaskStatus,
        assignedModels: List<String>,
        resultText: String?,
        executionSteps: List<String>,
        progressPercent: Int
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Task ID required" }
            val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
            path = docRef.path
            val payload = mutableMapOf<String, Any>(
                "status" to status.name,
                "assignedModels" to assignedModels.take(6).map { it.take(80) },
                "executionSteps" to executionSteps.take(12).map { it.take(240) },
                "progressPercent" to progressPercent.coerceIn(0, 100).toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (resultText != null) {
                payload["resultText"] = resultText.take(24000)
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun retryFailedTask(taskId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Task ID required" }
            val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "status" to TaskStatus.QUEUED.name,
                    "resultText" to "",
                    "executionSteps" to listOf("Re-queued for Aura Swarm"),
                    "progressPercent" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteTask(taskId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Task ID required" }
            val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Chat Messages ---

    override fun observeMessages(): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/messages"
        emitAll(
            db.collection("users").document(uid).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun addMessage(
        role: MessageRole,
        content: String,
        modelUsed: String,
        swarmModels: List<String>,
        searchSources: List<String>,
        usedWebSearch: Boolean,
        hasScreenContext: Boolean
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/messages"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("messages").document()
            path = docRef.path
            val msg = ChatMessage(
                userId = uid,
                role = role.name,
                content = content.trim().ifBlank { "..." }.take(24000),
                modelUsed = modelUsed.trim().ifBlank { "Aura Swarm" }.take(120),
                swarmModels = swarmModels.take(6),
                searchSources = searchSources.take(10),
                usedWebSearch = usedWebSearch,
                hasScreenContext = hasScreenContext
            )
            docRef.set(msg.toCreateMap(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun clearChatHistory(messageIds: List<String>): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/messages"
        return try {
            val uid = requireUserId()
            if (messageIds.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            val col = db.collection("users").document(uid).collection("messages")
            messageIds.forEach { id ->
                if (id.isNotBlank()) {
                    batch.delete(col.document(id))
                }
            }
            path = col.path
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
