package com.example.data.remote

import android.content.Context
import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessage
import com.example.data.security.CryptoVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class MongoAuthMode(val label: String) {
    API_KEY("api-key header"),
    BEARER("Authorization Bearer")
}

data class MongoDbConfig(
    val endpointUrl: String = "",
    val dataSource: String = "Cluster0",
    val database: String = "aura_ai",
    val autoSyncEnabled: Boolean = true,
    val authMode: MongoAuthMode = MongoAuthMode.API_KEY,
    val hasApiKey: Boolean = false,
    val lastSyncedCount: Int = 0
) {
    val isReady: Boolean
        get() = hasApiKey && endpointUrl.isNotBlank() && database.isNotBlank()
}

data class MongoSyncResult(
    val ok: Boolean,
    val message: String,
    val syncedDocuments: Int = 0,
    val latencyMs: Long = 0L
)

/**
 * Real MongoDB HTTPS REST / Atlas Data API cloud sync client.
 * - Supports MongoDB Atlas Data API or any compatible HTTPS MongoDB REST bridge
 *   exposing /action/findOne, /action/find, /action/insertOne, /action/updateOne, /action/deleteOne.
 * - API key / Bearer token is encrypted on-device via AndroidKeyStore ([CryptoVault]).
 * - Strictly validates HTTPS endpoints, identifier characters, and JSON payloads (no raw query interpolation).
 */
object MongoDbClient {
    private const val PREFS_NAME = "aura_mongodb_store_v1"
    private const val SECRET_API_KEY = "mongodb_api_key"
    private const val KEY_ENDPOINT = "mongo_endpoint"
    private const val KEY_DATASOURCE = "mongo_datasource"
    private const val KEY_DATABASE = "mongo_database"
    private const val KEY_AUTOSYNC = "mongo_autosync"
    private const val KEY_AUTH_MODE = "mongo_auth_mode"
    private const val KEY_LAST_COUNT = "mongo_last_count"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val IDENTIFIER_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")

    @Volatile
    private var runtimeApiKey: String = ""

    private val _configFlow = MutableStateFlow(MongoDbConfig())
    val configFlow: StateFlow<MongoDbConfig> = _configFlow.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        runtimeApiKey = CryptoVault.readSecret(context, SECRET_API_KEY)
        val mode = runCatching {
            MongoAuthMode.valueOf(prefs.getString(KEY_AUTH_MODE, MongoAuthMode.API_KEY.name)!!)
        }.getOrDefault(MongoAuthMode.API_KEY)

        _configFlow.value = MongoDbConfig(
            endpointUrl = prefs.getString(KEY_ENDPOINT, "").orEmpty(),
            dataSource = prefs.getString(KEY_DATASOURCE, "Cluster0").orEmpty().ifBlank { "Cluster0" },
            database = prefs.getString(KEY_DATABASE, "aura_ai").orEmpty().ifBlank { "aura_ai" },
            autoSyncEnabled = prefs.getBoolean(KEY_AUTOSYNC, true),
            authMode = mode,
            hasApiKey = runtimeApiKey.isNotBlank(),
            lastSyncedCount = prefs.getInt(KEY_LAST_COUNT, 0)
        )
    }

    fun saveConfig(
        context: Context,
        endpointUrl: String,
        apiKeyInput: String,
        dataSource: String,
        database: String,
        autoSyncEnabled: Boolean,
        authMode: MongoAuthMode = MongoAuthMode.API_KEY
    ): Result<MongoDbConfig> {
        val validatedEndpoint = CryptoVault.validatePublicHttpsUrl(endpointUrl)
            .getOrElse { return Result.failure(it) }
        val cleanSource = dataSource.trim().ifBlank { "Cluster0" }
        val cleanDb = database.trim().ifBlank { "aura_ai" }

        if (!IDENTIFIER_REGEX.matches(cleanSource)) {
            return Result.failure(IllegalArgumentException("Data Source name can only contain letters, numbers, _ and -."))
        }
        if (!IDENTIFIER_REGEX.matches(cleanDb)) {
            return Result.failure(IllegalArgumentException("Database name can only contain letters, numbers, _ and -."))
        }

        val trimmedKey = apiKeyInput.trim()
        if (trimmedKey.isNotEmpty()) {
            CryptoVault.saveSecret(context, SECRET_API_KEY, trimmedKey)
            runtimeApiKey = trimmedKey
        }
        if (runtimeApiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your MongoDB API Key or Bearer Token."))
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ENDPOINT, validatedEndpoint)
            .putString(KEY_DATASOURCE, cleanSource)
            .putString(KEY_DATABASE, cleanDb)
            .putBoolean(KEY_AUTOSYNC, autoSyncEnabled)
            .putString(KEY_AUTH_MODE, authMode.name)
            .apply()

        val updated = _configFlow.value.copy(
            endpointUrl = validatedEndpoint,
            dataSource = cleanSource,
            database = cleanDb,
            autoSyncEnabled = autoSyncEnabled,
            authMode = authMode,
            hasApiKey = true
        )
        _configFlow.value = updated
        return Result.success(updated)
    }

    fun toggleAutoSync(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTOSYNC, enabled).apply()
        _configFlow.value = _configFlow.value.copy(autoSyncEnabled = enabled)
    }

    fun clearConfig(context: Context) {
        CryptoVault.saveSecret(context, SECRET_API_KEY, "")
        runtimeApiKey = ""
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        _configFlow.value = MongoDbConfig()
    }

    fun isConfigured(): Boolean = _configFlow.value.isReady

    suspend fun testConnection(): MongoSyncResult = withContext(Dispatchers.IO) {
        val cfg = _configFlow.value
        if (!cfg.isReady) {
            return@withContext MongoSyncResult(false, "MongoDB connection is not configured yet.")
        }
        val start = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("dataSource", cfg.dataSource)
            put("database", cfg.database)
            put("collection", "aura_tasks")
            put("filter", JSONObject())
        }
        executeAction(cfg, "findOne", payload).fold(
            onSuccess = {
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                MongoSyncResult(
                    ok = true,
                    message = "MongoDB connected (${cfg.database}@${cfg.dataSource} • ${elapsed}ms)",
                    latencyMs = elapsed
                )
            },
            onFailure = { err ->
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                MongoSyncResult(
                    ok = false,
                    message = "MongoDB error: ${err.localizedMessage ?: "Check endpoint URL & API key"}",
                    latencyMs = elapsed
                )
            }
        )
    }

    suspend fun upsertTask(task: AssistantTask): Result<Unit> = withContext(Dispatchers.IO) {
        val cfg = _configFlow.value
        if (!cfg.isReady || !cfg.autoSyncEnabled) return@withContext Result.success(Unit)
        val doc = JSONObject().apply {
            put("taskId", task.id)
            put("title", task.title)
            put("prompt", task.prompt)
            put("category", task.category)
            put("priority", task.priority)
            put("status", task.status)
            put("scheduleMode", task.scheduleMode)
            put("assignedModels", JSONArray(task.assignedModels))
            put("resultText", task.resultText.orEmpty())
            put("progressPercent", task.progressPercent)
            put("updatedAtMillis", System.currentTimeMillis())
        }
        val payload = JSONObject().apply {
            put("dataSource", cfg.dataSource)
            put("database", cfg.database)
            put("collection", "aura_tasks")
            put("filter", JSONObject().put("taskId", task.id))
            put("update", JSONObject().put("\$set", doc))
            put("upsert", true)
        }
        executeAction(cfg, "updateOne", payload).map { Unit }
    }

    suspend fun insertMessage(message: ChatMessage): Result<Unit> = withContext(Dispatchers.IO) {
        val cfg = _configFlow.value
        if (!cfg.isReady || !cfg.autoSyncEnabled) return@withContext Result.success(Unit)
        val doc = JSONObject().apply {
            put("messageId", message.id)
            put("role", message.role)
            put("content", message.content)
            put("modelUsed", message.modelUsed)
            put("swarmModels", JSONArray(message.swarmModels))
            put("usedWebSearch", message.usedWebSearch)
            put("hasScreenContext", message.hasScreenContext)
            put("createdAtMillis", System.currentTimeMillis())
        }
        val payload = JSONObject().apply {
            put("dataSource", cfg.dataSource)
            put("database", cfg.database)
            put("collection", "aura_messages")
            put("filter", JSONObject().put("messageId", message.id))
            put("update", JSONObject().put("\$set", doc))
            put("upsert", true)
        }
        executeAction(cfg, "updateOne", payload).map { Unit }
    }

    suspend fun insertMediaAsset(asset: ImageKitAsset): Result<Unit> = withContext(Dispatchers.IO) {
        val cfg = _configFlow.value
        if (!cfg.isReady) return@withContext Result.success(Unit)
        val doc = JSONObject().apply {
            put("fileId", asset.fileId)
            put("name", asset.name)
            put("url", asset.url)
            put("thumbnailUrl", asset.thumbnailUrl)
            put("sizeBytes", asset.sizeBytes)
            put("width", asset.width)
            put("height", asset.height)
            put("uploadedAtMillis", asset.uploadedAtMillis)
        }
        val payload = JSONObject().apply {
            put("dataSource", cfg.dataSource)
            put("database", cfg.database)
            put("collection", "aura_media")
            put("filter", JSONObject().put("fileId", asset.fileId))
            put("update", JSONObject().put("\$set", doc))
            put("upsert", true)
        }
        executeAction(cfg, "updateOne", payload).map { Unit }
    }

    suspend fun syncSnapshotNow(
        context: Context,
        tasks: List<AssistantTask>,
        messages: List<ChatMessage>,
        assets: List<ImageKitAsset>
    ): MongoSyncResult = withContext(Dispatchers.IO) {
        val cfg = _configFlow.value
        if (!cfg.isReady) {
            return@withContext MongoSyncResult(false, "Configure MongoDB endpoint & API key first.")
        }
        val start = System.currentTimeMillis()
        var synced = 0
        var firstError: String? = null

        for (task in tasks.take(25)) {
            val doc = JSONObject().apply {
                put("taskId", task.id)
                put("title", task.title)
                put("prompt", task.prompt)
                put("category", task.category)
                put("priority", task.priority)
                put("status", task.status)
                put("scheduleMode", task.scheduleMode)
                put("assignedModels", JSONArray(task.assignedModels))
                put("resultText", task.resultText.orEmpty())
                put("progressPercent", task.progressPercent)
                put("updatedAtMillis", System.currentTimeMillis())
            }
            val payload = JSONObject().apply {
                put("dataSource", cfg.dataSource)
                put("database", cfg.database)
                put("collection", "aura_tasks")
                put("filter", JSONObject().put("taskId", task.id))
                put("update", JSONObject().put("\$set", doc))
                put("upsert", true)
            }
            executeAction(cfg, "updateOne", payload)
                .onSuccess { synced++ }
                .onFailure { if (firstError == null) firstError = it.localizedMessage }
            if (firstError != null) break
        }

        if (firstError == null) {
            for (msg in messages.takeLast(30)) {
                val doc = JSONObject().apply {
                    put("messageId", msg.id)
                    put("role", msg.role)
                    put("content", msg.content)
                    put("modelUsed", msg.modelUsed)
                    put("swarmModels", JSONArray(msg.swarmModels))
                    put("usedWebSearch", msg.usedWebSearch)
                    put("hasScreenContext", msg.hasScreenContext)
                    put("createdAtMillis", System.currentTimeMillis())
                }
                val payload = JSONObject().apply {
                    put("dataSource", cfg.dataSource)
                    put("database", cfg.database)
                    put("collection", "aura_messages")
                    put("filter", JSONObject().put("messageId", msg.id))
                    put("update", JSONObject().put("\$set", doc))
                    put("upsert", true)
                }
                executeAction(cfg, "updateOne", payload)
                    .onSuccess { synced++ }
                    .onFailure { if (firstError == null) firstError = it.localizedMessage }
                if (firstError != null) break
            }
        }

        if (firstError == null) {
            for (asset in assets.take(20)) {
                insertMediaAsset(asset)
                    .onSuccess { synced++ }
                    .onFailure { if (firstError == null) firstError = it.localizedMessage }
                if (firstError != null) break
            }
        }

        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        if (firstError != null) {
            MongoSyncResult(false, "MongoDB sync failed: $firstError", synced, elapsed)
        } else {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_COUNT, synced)
                .apply()
            _configFlow.value = _configFlow.value.copy(lastSyncedCount = synced)
            MongoSyncResult(
                ok = true,
                message = "Synced $synced documents to MongoDB (${cfg.database}) in ${elapsed}ms",
                syncedDocuments = synced,
                latencyMs = elapsed
            )
        }
    }

    private fun executeAction(
        cfg: MongoDbConfig,
        action: String,
        payload: JSONObject
    ): Result<JSONObject> {
        return try {
            val base = cfg.endpointUrl.trimEnd('/')
            val url = if (base.endsWith("/action")) "$base/$action" else "$base/action/$action"
            val builder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

            when (cfg.authMode) {
                MongoAuthMode.API_KEY -> builder.addHeader("api-key", runtimeApiKey)
                MongoAuthMode.BEARER -> builder.addHeader("Authorization", "Bearer $runtimeApiKey")
            }

            httpClient.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errText = raw.take(200).ifBlank { response.message }
                    return Result.failure(IllegalStateException("HTTP ${response.code}: $errText"))
                }
                Result.success(if (raw.isNotBlank()) JSONObject(raw) else JSONObject())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
