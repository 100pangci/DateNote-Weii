package com.datenote.app.data.remote

import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.data.secure.SecureApiKeyStore
import com.datenote.app.domain.parser.AiResponseParser
import com.datenote.app.domain.parser.ValidatedAiResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class AiNotConfiguredException : Exception()
class AiNetworkException(cause: Throwable) : Exception(cause)

class AiRepository(
    private val preferencesRepository: UserPreferencesRepository,
    private val apiKeyStore: SecureApiKeyStore,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val parser = AiResponseParser(json)
    private val client = HttpClient(Android) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 45_000
        }
    }

    suspend fun parse(input: String): ValidatedAiResult {
        val preferences = preferencesRepository.preferences.first()
        val key = apiKeyStore.read()
        if (key.isBlank() || preferences.aiBaseUrl.isBlank() || preferences.aiModel.isBlank()) throw AiNotConfiguredException()
        val prompt = AiSystemPrompt.build(defaultReminderMinutes = preferences.defaultReminderMinutes)
        val response = complete(preferences.aiBaseUrl, key, preferences.aiModel, prompt, input, useResponseFormat = true)
        return parser.parse(response)
    }

    suspend fun testConnection(): Boolean {
        val preferences = preferencesRepository.preferences.first()
        val key = apiKeyStore.read()
        if (key.isBlank() || preferences.aiBaseUrl.isBlank() || preferences.aiModel.isBlank()) throw AiNotConfiguredException()
        complete(
            preferences.aiBaseUrl,
            key,
            preferences.aiModel,
            "只回复 JSON：{\"ok\":true}",
            "请测试连接，不要处理排期。",
            useResponseFormat = true,
        )
        return true
    }

    private suspend fun complete(
        baseUrl: String,
        key: String,
        model: String,
        systemPrompt: String,
        userInput: String,
        useResponseFormat: Boolean,
    ): String {
        val request = ChatCompletionRequest(
            model = model,
            messages = listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userInput)),
            responseFormat = if (useResponseFormat) ResponseFormat() else null,
        )
        return try {
            client.post(normalizeBaseUrl(baseUrl) + "/chat/completions") {
                bearerAuth(key)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(request)
            }.body<ChatCompletionResponse>().choices.firstOrNull()?.message?.content.orEmpty()
        } catch (error: Exception) {
            if (useResponseFormat && error is ResponseException) {
                return complete(baseUrl, key, model, systemPrompt, userInput, useResponseFormat = false)
            }
            throw AiNetworkException(error)
        }
    }

    fun close() { client.close() }

    companion object {
        fun normalizeBaseUrl(value: String): String {
            var result = value.trim().trimEnd('/')
            while (result.endsWith("/v1/v1")) result = result.removeSuffix("/v1")
            return if (result.endsWith("/v1")) result else "$result/v1"
        }
    }
}
