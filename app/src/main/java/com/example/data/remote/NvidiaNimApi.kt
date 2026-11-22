package com.example.data.remote

import com.example.BuildConfig
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
    private const val PLACEHOLDER_KEY = "MY_NVIDIA_NIM_API_KEY"

    fun isConfigured(): Boolean {
        val key = BuildConfig.NVIDIA_API_KEY.trim()
        return key.isNotEmpty() && key != PLACEHOLDER_KEY
    }

    fun authHeader(): String = "Bearer ${BuildConfig.NVIDIA_API_KEY.trim()}"

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
