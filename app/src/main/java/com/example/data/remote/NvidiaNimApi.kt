package com.example.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@JsonClass(generateAdapter = true)
data class NimMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class NimChatRequest(
    val model: String,
    val messages: List<NimMessage>,
    val temperature: Double = 0.6,
    @Json(name = "top_p") val topP: Double = 0.95,
    @Json(name = "max_tokens") val maxTokens: Int = 2048,
    val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class NimImageUrl(
    val url: String
)

@JsonClass(generateAdapter = true)
data class NimVisionContentPart(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: NimImageUrl? = null
)

@JsonClass(generateAdapter = true)
data class NimVisionMessage(
    val role: String,
    val content: List<NimVisionContentPart>
)

@JsonClass(generateAdapter = true)
data class NimVisionChatRequest(
    val model: String,
    val messages: List<NimVisionMessage>,
    val temperature: Double = 0.4,
    @Json(name = "max_tokens") val maxTokens: Int = 1536,
    val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class NimChoiceMessage(
    val role: String? = null,
    val content: String? = null,
    @Json(name = "reasoning_content") val reasoningContent: String? = null
)

@JsonClass(generateAdapter = true)
data class NimChoice(
    val index: Int? = null,
    val message: NimChoiceMessage? = null
)

@JsonClass(generateAdapter = true)
data class NimChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<NimChoice>? = null
)

@JsonClass(generateAdapter = true)
data class NimRemoteModelItem(
    val id: String = "",
    @Json(name = "owned_by") val ownedBy: String? = null
)

@JsonClass(generateAdapter = true)
data class NimModelsListResponse(
    val data: List<NimRemoteModelItem>? = null
)

enum class NimKeyStatus(val label: String) {
    NOT_SET("No Key Saved"),
    STORED_LOCAL_UNVERIFIED("Key Stored Locally (Unverified)"),
    VERIFIED_LIVE("Key Verified by Live Request"),
    INVALID_KEY_401("Invalid / Revoked Key (401)"),
    RATE_LIMITED_429("Rate-Limited (429)"),
    OFFLINE("Offline / Network Unreachable")
}

data class NimKeyState(
    val isStoredLocally: Boolean = false,
    val maskedFingerprint: String = "",
    val status: NimKeyStatus = NimKeyStatus.NOT_SET,
    val lastTestedTimestampMs: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val liveRemoteModelCount: Int = 0,
    val statusMessage: String = "Add your NVIDIA NIM key (nvapi-...) to connect."
)

enum class NimErrorCategory(val label: String, val shouldAbortAllModels: Boolean, val shouldFailover: Boolean) {
    BAD_REQUEST_400("Payload / Schema Error (400)", false, true),
    INVALID_KEY_401("Invalid / Expired API Key (401)", true, false),
    FORBIDDEN_403("Model Access Restricted (403)", false, true),
    MODEL_NOT_FOUND_404("Model ID Unavailable (404)", false, true),
    TIMEOUT_408("Request Timeout (408)", false, true),
    PAYLOAD_TOO_LARGE_413("Prompt Too Large (413)", false, false),
    RATE_LIMITED_429("Rate Limited (429)", false, true),
    SERVER_5XX("Upstream Server Error (5xx)", false, true),
    OFFLINE("Offline / Network Error", true, false),
    UNKNOWN("Unexpected Error", false, true)
}

class NimHttpException(
    val httpCode: Int,
    val category: NimErrorCategory,
    val retryAfterSeconds: Long? = null,
    val safeMessage: String
) : Exception(safeMessage)

enum class ModelHealthStatus(val label: String) {
    UNTESTED("Untested"),
    VERIFIED("Verified"),
    DEGRADED("Degraded"),
    RATE_LIMITED("Rate-Limited"),
    UNAVAILABLE("Unavailable"),
    DISABLED_BY_USER("Disabled")
}

data class ModelHealthRecord(
    val modelId: String,
    val status: ModelHealthStatus = ModelHealthStatus.UNTESTED,
    val latencyMs: Long = 0L,
    val lastCheckedMs: Long = 0L,
    val lastErrorCode: Int = 0,
    val lastErrorSummary: String = "",
    val cooldownUntilMs: Long = 0L,
    val disabledByUser: Boolean = false
) {
    fun isCircuitOpen(nowMs: Long = System.currentTimeMillis()): Boolean =
        disabledByUser || (cooldownUntilMs > nowMs)
}

data class NimInferenceSettings(
    val temperature: Float = 0.6f,
    val topP: Float = 0.95f,
    val maxTokens: Int = 2048,
    val streamingEnabled: Boolean = true,
    val thinkingModeEnabled: Boolean = true,
    val customSystemPrompt: String = ""
)

data class NimStreamOutput(
    val content: String,
    val reasoningTrace: String,
    val ttftMs: Long = 0L,
    val totalMs: Long = 0L
)

data class NimConnectionCheck(
    val ok: Boolean,
    val httpCode: Int = 200,
    val latencyMs: Long,
    val availableModelCount: Int,
    val sampleModelIds: List<String>,
    val message: String
)

object NimErrorClassifier {
    fun maskKeyFingerprint(rawKey: String): String {
        val clean = rawKey.trim().removePrefix("Bearer ").trim()
        if (clean.isEmpty()) return ""
        val tail = clean.takeLast(4)
        val prefix = if (clean.startsWith("nvapi-")) "nvapi-" else ""
        return "${prefix}••••$tail"
    }

    fun parseRetryAfterSeconds(headerValue: String?): Long? {
        val trimmed = headerValue?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed.toLongOrNull()?.coerceIn(1L, 120L)
    }

    fun classifyHttpCode(code: Int, rawBody: String, retryAfterHeader: String? = null): NimHttpException {
        val snippet = sanitizeErrorBody(rawBody)
        val retryAfter = parseRetryAfterSeconds(retryAfterHeader)
        return when (code) {
            400 -> NimHttpException(code, NimErrorCategory.BAD_REQUEST_400, null, "HTTP 400: Request/parameter rejected ($snippet)")
            401 -> NimHttpException(code, NimErrorCategory.INVALID_KEY_401, null, "HTTP 401: NVIDIA API key is invalid or revoked. Re-enter a valid nvapi- key.")
            403 -> NimHttpException(code, NimErrorCategory.FORBIDDEN_403, null, "HTTP 403: Your key does not have access to this model ($snippet)")
            404 -> NimHttpException(code, NimErrorCategory.MODEL_NOT_FOUND_404, null, "HTTP 404: Model ID not enabled on this endpoint ($snippet)")
            408 -> NimHttpException(code, NimErrorCategory.TIMEOUT_408, null, "HTTP 408: Request timed out upstream.")
            413 -> NimHttpException(code, NimErrorCategory.PAYLOAD_TOO_LARGE_413, null, "HTTP 413: Prompt payload exceeded model limit; chunking required.")
            429 -> NimHttpException(
                code,
                NimErrorCategory.RATE_LIMITED_429,
                retryAfter,
                "HTTP 429: Rate limit reached${retryAfter?.let { " (retry after ${it}s)" }.orEmpty()}."
            )
            in 500..599 -> NimHttpException(code, NimErrorCategory.SERVER_5XX, retryAfter, "HTTP $code: NVIDIA NIM upstream error ($snippet)")
            else -> NimHttpException(code, NimErrorCategory.UNKNOWN, retryAfter, "HTTP $code: $snippet")
        }
    }

    fun classifyThrowable(t: Throwable): NimHttpException {
        if (t is NimHttpException) return t
        if (t is HttpException) {
            val code = t.code()
            val retryAfter = t.response()?.headers()?.get("Retry-After")
            val body = runCatching { t.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
            return classifyHttpCode(code, body, retryAfter)
        }
        if (t is UnknownHostException) {
            return NimHttpException(0, NimErrorCategory.OFFLINE, null, "Device appears offline or DNS could not reach integrate.api.nvidia.com.")
        }
        if (t is SocketTimeoutException) {
            return NimHttpException(408, NimErrorCategory.TIMEOUT_408, null, "Connection to NVIDIA NIM timed out.")
        }
        if (t is IOException) {
            return NimHttpException(0, NimErrorCategory.OFFLINE, null, "Network I/O error: ${sanitizeErrorBody(t.localizedMessage.orEmpty())}")
        }
        return NimHttpException(0, NimErrorCategory.UNKNOWN, null, sanitizeErrorBody(t.localizedMessage ?: "Unknown error"))
    }

    fun sanitizeErrorBody(raw: String): String {
        return raw
            .replace(Regex("nvapi-[A-Za-z0-9_-]+"), "nvapi-REDACTED")
            .replace(Regex("Bearer\\s+[A-Za-z0-9._-]+"), "Bearer REDACTED")
            .replace("\n", " ")
            .trim()
            .take(180)
            .ifBlank { "check key or model availability" }
    }
}

interface NvidiaNimService {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: NimChatRequest
    ): NimChatResponse

    @POST("v1/chat/completions")
    suspend fun visionCompletion(
        @Header("Authorization") authorization: String,
        @Body request: NimVisionChatRequest
    ): NimChatResponse

    @GET("v1/models")
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): NimModelsListResponse
}

object NvidiaNimClient {
    private const val BASE_URL = "https://integrate.api.nvidia.com/"
    private const val CHAT_COMPLETIONS_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
    private const val PREFS = "nim_private_settings"
    private const val KEY_ALIAS = "niva_nvidia_key_v1"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private const val HEALTH_TTL_MS = 15 * 60 * 1000L

    @Volatile
    private var runtimeKey = ""

    private val _isConfiguredFlow = MutableStateFlow(false)
    val isConfiguredFlow: StateFlow<Boolean> = _isConfiguredFlow.asStateFlow()

    private val _keyStateFlow = MutableStateFlow(NimKeyState())
    val keyStateFlow: StateFlow<NimKeyState> = _keyStateFlow.asStateFlow()

    private val _settingsFlow = MutableStateFlow(NimInferenceSettings())
    val settingsFlow: StateFlow<NimInferenceSettings> = _settingsFlow.asStateFlow()

    private val _liveRemoteModelIds = MutableStateFlow<List<String>>(emptyList())
    val liveRemoteModelIds: StateFlow<List<String>> = _liveRemoteModelIds.asStateFlow()

    private val _healthMapFlow = MutableStateFlow<Map<String, ModelHealthRecord>>(emptyMap())
    val healthMapFlow: StateFlow<Map<String, ModelHealthRecord>> = _healthMapFlow.asStateFlow()

    /** Load encrypted NVIDIA NIM key, masked fingerprint, health cache & settings from device. */
    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runtimeKey = try {
            val iv = prefs.getString("iv", null)
            val encrypted = prefs.getString("encrypted", null)
            if (iv.isNullOrEmpty() || encrypted.isNullOrEmpty()) "" else {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val secret = store.getKey(KEY_ALIAS, null) as? SecretKey
                if (secret == null) "" else {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        secret,
                        GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
                    )
                    String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
                }
            }
        } catch (_: Throwable) {
            ""
        }
        val stored = runtimeKey.isNotBlank()
        _isConfiguredFlow.value = stored

        val fingerprint = if (stored) {
            runCatching { prefs.getString("masked_fp", null) }.getOrNull().takeIf { !it.isNullOrBlank() }
                ?: NimErrorClassifier.maskKeyFingerprint(runtimeKey)
        } else ""

        val savedStatusName = runCatching { prefs.getString("key_status", null) }.getOrNull()
        val restoredStatus = when {
            !stored -> NimKeyStatus.NOT_SET
            savedStatusName != null -> runCatching { NimKeyStatus.valueOf(savedStatusName) }
                .getOrDefault(NimKeyStatus.STORED_LOCAL_UNVERIFIED)
            else -> NimKeyStatus.STORED_LOCAL_UNVERIFIED
        }

        _keyStateFlow.value = NimKeyState(
            isStoredLocally = stored,
            maskedFingerprint = fingerprint,
            status = restoredStatus,
            lastTestedTimestampMs = runCatching { prefs.getLong("last_test_ms", 0L) }.getOrDefault(0L),
            lastLatencyMs = runCatching { prefs.getLong("last_latency_ms", 0L) }.getOrDefault(0L),
            liveRemoteModelCount = runCatching { prefs.getInt("last_live_count", 0) }.getOrDefault(0),
            statusMessage = runCatching { prefs.getString("last_status_msg", null) }.getOrNull()
                ?: if (stored) "Key stored locally ($fingerprint) • Tap Test Connection to verify live."
                else "Add your NVIDIA NIM key (nvapi-...) to connect."
        )

        _settingsFlow.value = NimInferenceSettings(
            temperature = runCatching { prefs.getFloat("nim_temp", 0.6f) }.getOrDefault(0.6f).coerceIn(0f, 1.2f),
            topP = runCatching { prefs.getFloat("nim_top_p", 0.95f) }.getOrDefault(0.95f).coerceIn(0.1f, 1.0f),
            maxTokens = runCatching { prefs.getInt("nim_max_tokens", 2048) }.getOrDefault(2048).coerceIn(512, 4096),
            streamingEnabled = runCatching { prefs.getBoolean("nim_stream", true) }.getOrDefault(true),
            thinkingModeEnabled = runCatching { prefs.getBoolean("nim_thinking", true) }.getOrDefault(true),
            customSystemPrompt = runCatching { prefs.getString("nim_sys_prompt", "") }.getOrNull().orEmpty()
        )

        runCatching { loadHealthRegistry(prefs) }
    }

    fun updateSettings(context: Context, updated: NimInferenceSettings) {
        val safe = updated.copy(
            temperature = updated.temperature.coerceIn(0f, 1.2f),
            topP = updated.topP.coerceIn(0.1f, 1.0f),
            maxTokens = updated.maxTokens.coerceIn(512, 4096),
            customSystemPrompt = updated.customSystemPrompt.take(1200)
        )
        _settingsFlow.value = safe
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat("nim_temp", safe.temperature)
            .putFloat("nim_top_p", safe.topP)
            .putInt("nim_max_tokens", safe.maxTokens)
            .putBoolean("nim_stream", safe.streamingEnabled)
            .putBoolean("nim_thinking", safe.thinkingModeEnabled)
            .putString("nim_sys_prompt", safe.customSystemPrompt)
            .apply()
    }

    fun saveKey(context: Context, input: String) {
        val normalized = input.trim().removePrefix("Bearer ").trim()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (normalized.isEmpty()) {
            prefs.edit()
                .remove("iv")
                .remove("encrypted")
                .remove("masked_fp")
                .putString("key_status", NimKeyStatus.NOT_SET.name)
                .putLong("last_test_ms", 0L)
                .putLong("last_latency_ms", 0L)
                .putInt("last_live_count", 0)
                .putString("last_status_msg", "API key removed from this device.")
                .apply()
            runtimeKey = ""
            _isConfiguredFlow.value = false
            _liveRemoteModelIds.value = emptyList()
            _keyStateFlow.value = NimKeyState(
                isStoredLocally = false,
                status = NimKeyStatus.NOT_SET,
                statusMessage = "API key removed from this device."
            )
            return
        }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            generator.generateKey()
        }
        val secret = store.getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val fp = NimErrorClassifier.maskKeyFingerprint(normalized)
        val msg = "Key stored locally ($fp) • Run Test Connection to verify live."
        check(
            prefs.edit()
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("masked_fp", fp)
                .putString("key_status", NimKeyStatus.STORED_LOCAL_UNVERIFIED.name)
                .putString("last_status_msg", msg)
                .commit()
        ) { "Could not save local API key" }
        runtimeKey = normalized
        _isConfiguredFlow.value = true
        _keyStateFlow.value = _keyStateFlow.value.copy(
            isStoredLocally = true,
            maskedFingerprint = fp,
            status = NimKeyStatus.STORED_LOCAL_UNVERIFIED,
            statusMessage = msg
        )
    }

    fun isConfigured(): Boolean = runtimeKey.isNotBlank()

    fun authHeader(): String = "Bearer $runtimeKey"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    val service: NvidiaNimService by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NvidiaNimService::class.java)
    }

    /**
     * Tests the saved NVIDIA NIM key against GET /v1/models, updates key verification status
     * honestly, and seeds health records for models returned by the live key.
     */
    suspend fun testConnectionAndFetchModels(context: Context? = null): NimConnectionCheck = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext NimConnectionCheck(
                ok = false,
                httpCode = 0,
                latencyMs = 0L,
                availableModelCount = 0,
                sampleModelIds = emptyList(),
                message = "No NVIDIA NIM key saved yet."
            )
        }
        val start = System.currentTimeMillis()
        try {
            val response = service.listModels(authHeader())
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val ids = response.data.orEmpty().mapNotNull { it.id.takeIf(String::isNotBlank) }.distinct()
            _liveRemoteModelIds.value = ids
            val msg = "Verified live (${ids.size} models listed for your key • ${elapsed}ms)"
            updateKeyStatus(
                context = context,
                status = NimKeyStatus.VERIFIED_LIVE,
                latencyMs = elapsed,
                liveCount = ids.size,
                message = msg
            )
            NimConnectionCheck(
                ok = true,
                httpCode = 200,
                latencyMs = elapsed,
                availableModelCount = ids.size,
                sampleModelIds = ids.take(8),
                message = msg
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val classified = NimErrorClassifier.classifyThrowable(e)
            val newStatus = when (classified.category) {
                NimErrorCategory.INVALID_KEY_401 -> NimKeyStatus.INVALID_KEY_401
                NimErrorCategory.RATE_LIMITED_429 -> NimKeyStatus.RATE_LIMITED_429
                NimErrorCategory.OFFLINE -> NimKeyStatus.OFFLINE
                else -> NimKeyStatus.STORED_LOCAL_UNVERIFIED
            }
            updateKeyStatus(
                context = context,
                status = newStatus,
                latencyMs = elapsed,
                liveCount = 0,
                message = classified.safeMessage
            )
            NimConnectionCheck(
                ok = false,
                httpCode = classified.httpCode,
                latencyMs = elapsed,
                availableModelCount = 0,
                sampleModelIds = emptyList(),
                message = classified.safeMessage
            )
        }
    }

    /**
     * Sends a tiny real inference probe (`max_tokens = 8`) to verify whether a specific model ID
     * is genuinely live and accessible on the user's current key.
     */
    suspend fun probeModel(context: Context?, modelId: String): ModelHealthRecord = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext ModelHealthRecord(
                modelId = modelId,
                status = ModelHealthStatus.UNTESTED,
                lastErrorSummary = "Add NVIDIA API key first"
            )
        }
        val start = System.currentTimeMillis()
        try {
            val resp = service.chatCompletion(
                authorization = authHeader(),
                request = NimChatRequest(
                    model = modelId,
                    messages = listOf(NimMessage("user", "Reply with OK")),
                    temperature = 0.0,
                    topP = 1.0,
                    maxTokens = 8,
                    stream = false
                )
            )
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val text = resp.choices?.firstOrNull()?.message?.content.orEmpty()
            val record = ModelHealthRecord(
                modelId = modelId,
                status = if (text.isNotBlank()) ModelHealthStatus.VERIFIED else ModelHealthStatus.DEGRADED,
                latencyMs = elapsed,
                lastCheckedMs = System.currentTimeMillis(),
                lastErrorCode = 200,
                lastErrorSummary = if (text.isNotBlank()) "Verified live (${elapsed}ms)" else "Empty probe reply",
                cooldownUntilMs = 0L,
                disabledByUser = _healthMapFlow.value[modelId]?.disabledByUser ?: false
            )
            recordModelHealth(context, record)
            record
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val classified = NimErrorClassifier.classifyThrowable(e)
            val record = recordFailureFromException(context, modelId, classified, elapsed)
            record
        }
    }

    fun recordSuccess(context: Context?, modelId: String, latencyMs: Long) {
        val prev = _healthMapFlow.value[modelId]
        val rec = ModelHealthRecord(
            modelId = modelId,
            status = ModelHealthStatus.VERIFIED,
            latencyMs = latencyMs,
            lastCheckedMs = System.currentTimeMillis(),
            lastErrorCode = 200,
            lastErrorSummary = "Live OK (${latencyMs}ms)",
            cooldownUntilMs = 0L,
            disabledByUser = prev?.disabledByUser ?: false
        )
        recordModelHealth(context, rec)
        if (_keyStateFlow.value.status != NimKeyStatus.VERIFIED_LIVE) {
            updateKeyStatus(
                context = context,
                status = NimKeyStatus.VERIFIED_LIVE,
                latencyMs = latencyMs,
                liveCount = _liveRemoteModelIds.value.size,
                message = "Key verified by live request (${latencyMs}ms)"
            )
        }
    }

    fun recordFailureFromException(
        context: Context?,
        modelId: String,
        exception: NimHttpException,
        latencyMs: Long = 0L
    ): ModelHealthRecord {
        val now = System.currentTimeMillis()
        val prev = _healthMapFlow.value[modelId]
        val (status, cooldownMs) = when (exception.category) {
            NimErrorCategory.MODEL_NOT_FOUND_404,
            NimErrorCategory.FORBIDDEN_403 -> ModelHealthStatus.UNAVAILABLE to HEALTH_TTL_MS
            NimErrorCategory.RATE_LIMITED_429 -> {
                val sec = exception.retryAfterSeconds ?: 20L
                ModelHealthStatus.RATE_LIMITED to (sec * 1000L)
            }
            NimErrorCategory.BAD_REQUEST_400,
            NimErrorCategory.SERVER_5XX,
            NimErrorCategory.TIMEOUT_408 -> ModelHealthStatus.DEGRADED to 60_000L
            NimErrorCategory.INVALID_KEY_401 -> {
                updateKeyStatus(context, NimKeyStatus.INVALID_KEY_401, latencyMs, 0, exception.safeMessage)
                ModelHealthStatus.UNTESTED to 0L
            }
            NimErrorCategory.OFFLINE -> {
                updateKeyStatus(context, NimKeyStatus.OFFLINE, latencyMs, 0, exception.safeMessage)
                ModelHealthStatus.UNTESTED to 0L
            }
            else -> ModelHealthStatus.DEGRADED to 30_000L
        }
        val rec = ModelHealthRecord(
            modelId = modelId,
            status = status,
            latencyMs = latencyMs,
            lastCheckedMs = now,
            lastErrorCode = exception.httpCode,
            lastErrorSummary = exception.safeMessage,
            cooldownUntilMs = if (cooldownMs > 0L) now + cooldownMs else 0L,
            disabledByUser = prev?.disabledByUser ?: false
        )
        recordModelHealth(context, rec)
        return rec
    }

    fun setModelDisabled(context: Context?, modelId: String, disabled: Boolean) {
        val existing = _healthMapFlow.value[modelId] ?: ModelHealthRecord(modelId = modelId)
        val updated = existing.copy(
            disabledByUser = disabled,
            status = if (disabled) ModelHealthStatus.DISABLED_BY_USER else existing.status
        )
        recordModelHealth(context, updated)
    }

    fun verifiedModelIds(): Set<String> {
        val fromHealth = _healthMapFlow.value
            .filterValues { it.status == ModelHealthStatus.VERIFIED && !it.disabledByUser }
            .keys
        return fromHealth + _liveRemoteModelIds.value
    }

    fun disabledModelIds(): Set<String> {
        val now = System.currentTimeMillis()
        return _healthMapFlow.value
            .filterValues { it.isCircuitOpen(now) }
            .keys
    }

    private fun recordModelHealth(context: Context?, record: ModelHealthRecord) {
        val updated = _healthMapFlow.value.toMutableMap()
        updated[record.modelId] = record
        _healthMapFlow.value = updated
        context?.let { saveHealthRegistry(it.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE), updated) }
    }

    private fun updateKeyStatus(
        context: Context?,
        status: NimKeyStatus,
        latencyMs: Long,
        liveCount: Int,
        message: String
    ) {
        val now = System.currentTimeMillis()
        _keyStateFlow.value = _keyStateFlow.value.copy(
            status = status,
            lastTestedTimestampMs = now,
            lastLatencyMs = latencyMs,
            liveRemoteModelCount = if (liveCount > 0) liveCount else _keyStateFlow.value.liveRemoteModelCount,
            statusMessage = message
        )
        context?.applicationContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("key_status", status.name)
            ?.putLong("last_test_ms", now)
            ?.putLong("last_latency_ms", latencyMs)
            ?.putInt("last_live_count", _keyStateFlow.value.liveRemoteModelCount)
            ?.putString("last_status_msg", message)
            ?.apply()
    }

    private fun loadHealthRegistry(prefs: android.content.SharedPreferences) {
        val raw = prefs.getString("model_health_json", null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val map = mutableMapOf<String, ModelHealthRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                if (id.isBlank()) continue
                val status = runCatching { ModelHealthStatus.valueOf(o.optString("status")) }
                    .getOrDefault(ModelHealthStatus.UNTESTED)
                map[id] = ModelHealthRecord(
                    modelId = id,
                    status = status,
                    latencyMs = o.optLong("latencyMs", 0L),
                    lastCheckedMs = o.optLong("checkedMs", 0L),
                    lastErrorCode = o.optInt("errCode", 0),
                    lastErrorSummary = o.optString("errSummary", ""),
                    cooldownUntilMs = o.optLong("cooldown", 0L),
                    disabledByUser = o.optBoolean("disabled", false)
                )
            }
            _healthMapFlow.value = map
        }
    }

    private fun saveHealthRegistry(prefs: android.content.SharedPreferences, map: Map<String, ModelHealthRecord>) {
        runCatching {
            val arr = JSONArray()
            map.values.take(60).forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("id", r.modelId)
                        put("status", r.status.name)
                        put("latencyMs", r.latencyMs)
                        put("checkedMs", r.lastCheckedMs)
                        put("errCode", r.lastErrorCode)
                        put("errSummary", r.lastErrorSummary.take(140))
                        put("cooldown", r.cooldownUntilMs)
                        put("disabled", r.disabledByUser)
                    }
                )
            }
            prefs.edit().putString("model_health_json", arr.toString()).apply()
        }
    }

    /**
     * Real Server-Sent Events (SSE) token-by-token streaming from NVIDIA NIM
     * with strict HTTP error taxonomy, Retry-After capture, and clean cancellation.
     */
    suspend fun streamChatCompletion(
        modelId: String,
        messages: List<NimMessage>,
        temperature: Double,
        topP: Double,
        maxTokens: Int,
        onThinkingToken: (String) -> Unit = {},
        onContentToken: (String) -> Unit = {}
    ): NimStreamOutput = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        var ttftMs = 0L

        val payload = JSONObject().apply {
            put("model", modelId)
            put("temperature", temperature)
            put("top_p", topP)
            put("max_tokens", maxTokens)
            put("stream", true)
            put(
                "messages",
                JSONArray().apply {
                    messages.forEach { msg ->
                        put(
                            JSONObject().apply {
                                put("role", msg.role)
                                put("content", msg.content)
                            }
                        )
                    }
                }
            )
        }

        val request = Request.Builder()
            .url(CHAT_COMPLETIONS_URL)
            .addHeader("Authorization", authHeader())
            .addHeader("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val contentBuilder = StringBuilder()
        val thinkingBuilder = StringBuilder()
        var insideThinkTag = false

        val call = httpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    val retryAfter = response.header("Retry-After")
                    throw NimErrorClassifier.classifyHttpCode(response.code, errBody, retryAfter)
                }
                val body = response.body ?: throw NimHttpException(
                    502,
                    NimErrorCategory.SERVER_5XX,
                    null,
                    "Empty streaming response from NVIDIA NIM."
                )
                BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        currentCoroutineContext().ensureActive()
                        val trimmed = line?.trim().orEmpty()
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        if (data.isEmpty() || data == "[DONE]") break

                        runCatching {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices") ?: return@runCatching
                            if (choices.length() == 0) return@runCatching
                            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return@runCatching

                            val reasonPiece = delta.optString("reasoning_content", "")
                            if (reasonPiece.isNotEmpty() && reasonPiece != "null") {
                                if (ttftMs == 0L) ttftMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                                thinkingBuilder.append(reasonPiece)
                                onThinkingToken(thinkingBuilder.toString())
                            }

                            val contentPiece = delta.optString("content", "")
                            if (contentPiece.isNotEmpty() && contentPiece != "null") {
                                if (ttftMs == 0L) ttftMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                                var chunk = contentPiece
                                if (chunk.contains("<think>")) {
                                    insideThinkTag = true
                                    chunk = chunk.replace("<think>", "")
                                }
                                if (chunk.contains("</think>")) {
                                    val parts = chunk.split("</think>", limit = 2)
                                    if (parts[0].isNotEmpty()) {
                                        thinkingBuilder.append(parts[0])
                                        onThinkingToken(thinkingBuilder.toString())
                                    }
                                    insideThinkTag = false
                                    val remainder = parts.getOrNull(1).orEmpty()
                                    if (remainder.isNotEmpty()) {
                                        contentBuilder.append(remainder)
                                        onContentToken(contentBuilder.toString())
                                    }
                                } else if (insideThinkTag) {
                                    thinkingBuilder.append(chunk)
                                    onThinkingToken(thinkingBuilder.toString())
                                } else {
                                    contentBuilder.append(chunk)
                                    onContentToken(contentBuilder.toString())
                                }
                            }
                        }
                    }
                }
            }
        } catch (ce: CancellationException) {
            call.cancel()
            throw ce
        } catch (nim: NimHttpException) {
            throw nim
        } catch (e: Exception) {
            throw NimErrorClassifier.classifyThrowable(e)
        }

        val totalMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        NimStreamOutput(
            content = contentBuilder.toString().trim(),
            reasoningTrace = thinkingBuilder.toString().trim(),
            ttftMs = ttftMs,
            totalMs = totalMs
        )
    }
}
