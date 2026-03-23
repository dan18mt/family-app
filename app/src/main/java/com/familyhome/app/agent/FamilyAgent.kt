package com.familyhome.app.agent

import com.familyhome.app.BuildConfig
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import javax.inject.Inject

private const val MODEL = "claude-haiku-4-5-20251001"
private const val MAX_TOKENS = 1024
private const val MAX_TOOL_ITERATIONS = 5

/**
 * Orchestrates a multi-turn conversation with the Claude API.
 *
 * Flow:
 * 1. Build the message list with a system prompt tailored to the user's role.
 * 2. Send to Claude. If the response contains tool_use blocks, dispatch each
 *    tool via [AgentTools] and feed the results back as tool_result blocks.
 * 3. Repeat until Claude returns stop_reason = "end_turn" or we hit the
 *    [MAX_TOOL_ITERATIONS] safety limit.
 */
class FamilyAgent @Inject constructor(
    private val apiService: AnthropicApiService,
    private val agentTools: AgentTools,
) {
    // In-memory conversation history (cleared when the screen is recreated)
    private val conversationHistory = mutableListOf<AnthropicMessage>()

    suspend fun chat(userMessage: String, actor: User): AgentResponse {
        if (BuildConfig.ANTHROPIC_API_KEY.isBlank()) {
            return AgentResponse(
                reply   = "AI assistant is not configured. Add ANTHROPIC_API_KEY to local.properties.",
                isError = true,
            )
        }

        // Append user message to history
        conversationHistory.add(
            AnthropicMessage(
                role    = "user",
                content = AnthropicContent.Text(userMessage),
            )
        )

        return runCatching {
            var iterations = 0
            var finalReply = ""

            while (iterations < MAX_TOOL_ITERATIONS) {
                iterations++

                val response = apiService.sendMessage(
                    apiKey  = BuildConfig.ANTHROPIC_API_KEY,
                    request = AnthropicRequest(
                        model    = MODEL,
                        maxTokens = MAX_TOKENS,
                        system   = buildSystemPrompt(actor),
                        tools    = FAMILY_AGENT_TOOLS,
                        messages = conversationHistory,
                    ),
                )

                // Store assistant response in history
                conversationHistory.add(
                    AnthropicMessage(
                        role    = "assistant",
                        content = AnthropicContent.Blocks(response.content),
                    )
                )

                val toolUseBlocks = response.content.filterIsInstance<ContentBlock.ToolUseBlock>()

                if (response.stopReason == "end_turn" || toolUseBlocks.isEmpty()) {
                    // Extract final text reply
                    finalReply = response.content
                        .filterIsInstance<ContentBlock.TextBlock>()
                        .joinToString(" ") { it.text }
                        .ifBlank { "Done." }
                    break
                }

                // Execute tools and build tool_result blocks
                val toolResults = toolUseBlocks.map { toolBlock ->
                    val result = agentTools.dispatch(toolBlock.name, toolBlock.input, actor)
                    ContentBlock.ToolResultBlock(
                        toolUseId = toolBlock.id,
                        content   = result,
                    )
                }

                // Append tool results as a new user turn
                conversationHistory.add(
                    AnthropicMessage(
                        role    = "user",
                        content = AnthropicContent.Blocks(toolResults),
                    )
                )
            }

            AgentResponse(reply = finalReply)
        }.getOrElse { e ->
            AgentResponse(
                reply   = "Sorry, I encountered an error: ${e.message}",
                isError = true,
            )
        }
    }

    private fun buildSystemPrompt(actor: User): String {
        val roleContext = when (actor.role) {
            Role.FATHER -> "The user is ${actor.name}, the Father (admin). They have full access."
            Role.WIFE   -> "The user is ${actor.name}. They can manage the family but cannot add/remove members."
            Role.KID    -> "The user is ${actor.name}, a child. Only show and modify their own data."
        }
        return """
            You are FamilyHome Assistant, a helpful AI for managing a household.
            $roleContext

            You can help the family log chores, manage pantry stock, track expenses, and get summaries.
            When the user says something like "we finished the dishes", call log_chore automatically.
            When amounts are mentioned in conversation, assume IDR unless stated otherwise.
            Keep responses short and friendly. After calling a tool, briefly confirm what was done.
        """.trimIndent()
    }

    fun clearHistory() = conversationHistory.clear()
}
