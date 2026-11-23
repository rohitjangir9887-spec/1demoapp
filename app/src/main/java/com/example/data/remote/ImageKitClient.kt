package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.data.security.CryptoVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ImageKitConfig(
    val urlEndpoint: String = "",
    val publicKey: String = "",
    val folder: String = "/aura_ai",
    val hasPrivateKey: Boolean = false
) {
    val isReady: Boolean
        get() = hasPrivateKey && urlEndpoint.isNotBlank()
}

data class ImageKitAsset(
    val fileId: String,
    val name: String,
    val url: String,
    val thumbnailUrl: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val uploadedAtMillis: Long
)

data class ImageKitStatusResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long = 0L
)

/**
 * Real ImageKit.io Cloud Storage client.
 * - Private API key is encrypted on-device in AndroidKeyStore via [CryptoVault].
 * - Uploads captured screen frames & picked images via multipart HTTPS to upload.imagekit.io.
 */
object ImageKitClient {
    private const val UPLOAD_URL = "https://upload.imagekit.io/api/v1/files/upload"
    private const val FILES_API_URL = "https://api.imagekit.io/v1/files"
    private const val PREFS_NAME = "aura_imagekit_store_v1"
    private const val SECRET_PRIVATE_KEY = "imagekit_private_key"
    private const val KEY_URL_ENDPOINT = "ik_url_endpoint"
    private const val KEY_PUBLIC_KEY = "ik_public_key"
    private const val KEY_FOLDER = "ik_folder"
    private const val KEY_ASSETS_JSON = "ik_assets_json"

    @Volatile
    private var runtimePrivateKey: String = ""

    private val _configFlow = MutableStateFlow(ImageKitConfig())
    val configFlow: StateFlow<ImageKitConfig> = _configFlow.asStateFlow()

    private val _assetsFlow = MutableStateFlow<List<ImageKitAsset>>(emptyList())
    val assetsFlow: StateFlow<List<ImageKitAsset>> = _assetsFlow.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        runtimePrivateKey = CryptoVault.readSecret(context, SECRET_PRIVATE_KEY)
        val endpoint = prefs.getString(KEY_URL_ENDPOINT, "").orEmpty()
        val publicKey = prefs.getString(KEY_PUBLIC_KEY, "").orEmpty()
        val folder = sanitizeFolder(prefs.getString(KEY_FOLDER, "/aura_ai").orEmpty())
        _configFlow.value = ImageKitConfig(
            urlEndpoint = endpoint,
            publicKey = publicKey,
            folder = folder,
            hasPrivateKey = runtimePrivateKey.isNotBlank()
        )
        _assetsFlow.value = loadAssets(prefs)
    }

    fun saveConfig(
        context: Context,
        urlEndpoint: String,
        publicKey: String,
        privateKeyInput: String,
        folder: String = "/aura_ai"
    ): Result<ImageKitConfig> {
        val validatedEndpoint = CryptoVault.validatePublicHttpsUrl(urlEndpoint)
            .getOrElse { return Result.failure(it) }
        val safeFolder = sanitizeFolder(folder)
        val cleanPublic = publicKey.trim().take(160)
        val trimmedPrivate = privateKeyInput.trim()

        if (trimmedPrivate.isNotEmpty()) {
            CryptoVault.saveSecret(context, SECRET_PRIVATE_KEY, trimmedPrivate)
            runtimePrivateKey = trimmedPrivate
        }

        if (runtimePrivateKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your ImageKit Private Key (private_...) to enable uploads."))
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_URL_ENDPOINT, validatedEndpoint)
            .putString(KEY_PUBLIC_KEY, cleanPublic)
            .putString(KEY_FOLDER, safeFolder)
            .apply()

        val updated = ImageKitConfig(
            urlEndpoint = validatedEndpoint,
            publicKey = cleanPublic,
            folder = safeFolder,
            hasPrivateKey = true
        )
        _configFlow.value = updated
        return Result.success(updated)
    }

    fun clearConfig(context: Context) {
        CryptoVault.saveSecret(context, SECRET_PRIVATE_KEY, "")
        runtimePrivateKey = ""
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_URL_ENDPOINT)
            .remove(KEY_PUBLIC_KEY)
            .remove(KEY_FOLDER)
            .apply()
        _configFlow.value = ImageKitConfig()
    }

    fun isConfigured(): Boolean = _configFlow.value.isReady

    private fun basicAuthHeader(): String {
        val raw = "$runtimePrivateKey:"
        val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    suspend fun testConnection(): ImageKitStatusResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext ImageKitStatusResult(false, "ImageKit is not configured yet.")
        }
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("$FILES_API_URL?limit=1")
                .addHeader("Authorization", basicAuthHeader())
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                if (response.isSuccessful) {
                    ImageKitStatusResult(
                        ok = true,
                        message = "ImageKit connected (${elapsed}ms) • Storage ready",
                        latencyMs = elapsed
                    )
                } else {
                    ImageKitStatusResult(
                        ok = false,
                        message = "ImageKit HTTP ${response.code}: Check your Private Key.",
                        latencyMs = elapsed
                    )
                }
            }
        } catch (e: Exception) {
            ImageKitStatusResult(
                ok = false,
                message = "ImageKit error: ${e.localizedMessage ?: "Network unreachable"}"
            )
        }
    }

    suspend fun uploadBitmap(
        context: Context,
        bitmap: Bitmap,
        label: String = "aura_capture"
    ): Result<ImageKitAsset> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Configure ImageKit URL Endpoint & Private Key first in the Screen & Cloud Hub.")
            )
        }
        try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
            val base64Image = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            val safeName = sanitizeFileName(label) + "_" + System.currentTimeMillis() + ".jpg"
            val folder = _configFlow.value.folder

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "data:image/jpeg;base64,$base64Image")
                .addFormDataPart("fileName", safeName)
                .addFormDataPart("folder", folder)
                .addFormDataPart("useUniqueFileName", "true")
                .addFormDataPart("tags", "aura-ai,android,nvidia-nim")
                .build()

            val request = Request.Builder()
                .url(UPLOAD_URL)
                .addHeader("Authorization", basicAuthHeader())
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverMsg = runCatching { JSONObject(rawBody).optString("message") }.getOrNull()
                    return@withContext Result.failure(
                        IllegalStateException(
                            "ImageKit upload failed (${response.code}): ${serverMsg?.ifBlank { response.message } ?: response.message}"
                        )
                    )
                }
                val json = JSONObject(rawBody)
                val asset = ImageKitAsset(
                    fileId = json.optString("fileId"),
                    name = json.optString("name", safeName),
                    url = json.optString("url"),
                    thumbnailUrl = json.optString("thumbnailUrl", json.optString("url")),
                    sizeBytes = json.optLong("size", 0L),
                    width = json.optInt("width", bitmap.width),
                    height = json.optInt("height", bitmap.height),
                    uploadedAtMillis = System.currentTimeMillis()
                )
                val updated = (listOf(asset) + _assetsFlow.value).take(30)
                _assetsFlow.value = updated
                persistAssets(context, updated)
                Result.success(asset)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAsset(context: Context, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = fileId.trim()
        if (cleanId.isEmpty() || !cleanId.matches(Regex("^[A-Za-z0-9_-]{4,80}$"))) {
            return@withContext Result.failure(IllegalArgumentException("Invalid ImageKit file ID."))
        }
        try {
            if (isConfigured()) {
                val request = Request.Builder()
                    .url("$FILES_API_URL/$cleanId")
                    .addHeader("Authorization", basicAuthHeader())
                    .delete()
                    .build()
                httpClient.newCall(request).execute().use { /* remove remotely if still exists */ }
            }
            val updated = _assetsFlow.value.filterNot { it.fileId == cleanId }
            _assetsFlow.value = updated
            persistAssets(context, updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeFolder(raw: String): String {
        val cleaned = raw.trim().replace("..", "").replace(Regex("[^A-Za-z0-9/_-]"), "")
        val normalized = if (cleaned.startsWith("/")) cleaned else "/$cleaned"
        return normalized.ifBlank { "/aura_ai" }.take(64)
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return cleaned.ifBlank { "aura_img" }.take(32)
    }

    private fun persistAssets(context: Context, list: List<ImageKitAsset>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("fileId", a.fileId)
                    put("name", a.name)
                    put("url", a.url)
                    put("thumbnailUrl", a.thumbnailUrl)
                    put("sizeBytes", a.sizeBytes)
                    put("width", a.width)
                    put("height", a.height)
                    put("uploadedAtMillis", a.uploadedAtMillis)
                }
            )
        }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ASSETS_JSON, arr.toString())
            .apply()
    }

    private fun loadAssets(prefs: android.content.SharedPreferences): List<ImageKitAsset> {
        val raw = prefs.getString(KEY_ASSETS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ImageKitAsset(
                            fileId = o.optString("fileId"),
                            name = o.optString("name"),
                            url = o.optString("url"),
                            thumbnailUrl = o.optString("thumbnailUrl"),
                            sizeBytes = o.optLong("sizeBytes"),
                            width = o.optInt("width"),
                            height = o.optInt("height"),
                            uploadedAtMillis = o.optLong("uploadedAtMillis")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
