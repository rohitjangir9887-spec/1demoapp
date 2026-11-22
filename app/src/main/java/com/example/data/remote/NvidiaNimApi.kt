package com.example.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

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
    @Json(name = "max_tokens") val maxTokens: Int = 2048,
    val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class NimChoiceMessage(
    val role: String? = null,
    val content: String? = null
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

interface NvidiaNimService {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: NimChatRequest
    ): NimChatResponse
}

object NvidiaNimClient {
    private const val BASE_URL = "https://integrate.api.nvidia.com/"
    private const val PREFS = "nim_private_settings"
    private const val KEY_ALIAS = "niva_nvidia_key_v1"
    @Volatile private var runtimeKey = ""

    /** Load only from this device's encrypted private store, never from the APK. */
    fun initialize(context: Context) {
        runtimeKey = try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val iv = prefs.getString("iv", null)
            val encrypted = prefs.getString("encrypted", null)
            if (iv.isNullOrEmpty() || encrypted.isNullOrEmpty()) "" else {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val secret = store.getKey(KEY_ALIAS, null) as SecretKey
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secret,
                    GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
                String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
            }
        } catch (_: Exception) { "" }
    }

    fun saveKey(context: Context, input: String) {
        val normalized = input.trim().removePrefix("Bearer ").trim()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (normalized.isEmpty()) {
            prefs.edit().remove("iv").remove("encrypted").apply()
            runtimeKey = ""
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
        check(prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()) { "Could not save local API key" }
        runtimeKey = normalized
    }

    fun isConfigured(): Boolean = runtimeKey.isNotBlank()

    fun authHeader(): String = "Bearer $runtimeKey"

    val service: NvidiaNimService by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NvidiaNimService::class.java)
    }
}
