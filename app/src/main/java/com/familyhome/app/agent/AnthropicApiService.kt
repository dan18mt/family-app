package com.familyhome.app.agent

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AnthropicApiService {
    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key")         apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("content-type")      contentType: String = "application/json",
        @Body request: AnthropicRequest,
    ): AnthropicResponse
}
