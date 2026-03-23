package com.familyhome.app.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Anthropic Messages API request / response ────────────────────────────────

@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val tools: List<AnthropicTool>? = null,
    val messages: List<AnthropicMessage>,
)

@Serializable
data class AnthropicMessage(
    val role: String,           // "user" | "assistant"
    val content: AnthropicContent,
)

/** Content can be a plain string or a list of blocks — we use a sealed wrapper. */
@Serializable(with = AnthropicContentSerializer::class)
sealed class AnthropicContent {
    data class Text(val text: String) : AnthropicContent()
    data class Blocks(val blocks: List<ContentBlock>) : AnthropicContent()
}

@Serializable
sealed class ContentBlock {
    @Serializable @SerialName("text")
    data class TextBlock(val type: String = "text", val text: String) : ContentBlock()

    @Serializable @SerialName("tool_use")
    data class ToolUseBlock(
        val type: String = "tool_use",
        val id: String,
        val name: String,
        val input: kotlinx.serialization.json.JsonObject,
    ) : ContentBlock()

    @Serializable @SerialName("tool_result")
    data class ToolResultBlock(
        val type: String = "tool_result",
        @SerialName("tool_use_id") val toolUseId: String,
        val content: String,
    ) : ContentBlock()
}

@Serializable
data class AnthropicTool(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: InputSchema,
)

@Serializable
data class InputSchema(
    val type: String = "object",
    val properties: Map<String, PropertyDef>,
    val required: List<String> = emptyList(),
)

@Serializable
data class PropertyDef(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
)

// ── Response ─────────────────────────────────────────────────────────────────

@Serializable
data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    @SerialName("stop_reason") val stopReason: String,
    val model: String,
    val usage: UsageInfo,
)

@Serializable
data class UsageInfo(
    @SerialName("input_tokens")  val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int,
)

// ── Agent result ──────────────────────────────────────────────────────────────

data class AgentResponse(
    val reply: String,
    val isError: Boolean = false,
)
