package com.datenote.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    val temperature: Double = 0.1,
)

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ResponseFormat(val type: String = "json_object")

@Serializable
data class ChatCompletionResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
data class ChatChoice(val message: ChatMessage)

@Serializable
data class AiResponseDto(
    val items: List<AiItemDto> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class AiItemDto(
    val title: String = "",
    val date: String = "",
    val time: String? = null,
    val category: String? = null,
    val note: String = "",
    val remindBeforeMinutes: Long? = null,
    val confidence: Double = 0.0,
    val uncertainties: List<String> = emptyList(),
)
