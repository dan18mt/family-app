package com.familyhome.app.agent

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Custom serializer that handles the Anthropic API's dual-format content field:
 *   - As a plain string (user messages): "hello"
 *   - As a list of blocks (assistant messages): [{"type":"text","text":"hello"}, ...]
 */
object AnthropicContentSerializer : KSerializer<AnthropicContent> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("AnthropicContent")

    override fun serialize(encoder: Encoder, value: AnthropicContent) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is AnthropicContent.Text   -> jsonEncoder.encodeString(value.text)
            is AnthropicContent.Blocks -> {
                val array = buildJsonArray {
                    value.blocks.forEach { block ->
                        add(jsonEncoder.json.encodeToJsonElement(ContentBlock.serializer(), block))
                    }
                }
                jsonEncoder.encodeJsonElement(array)
            }
        }
    }

    override fun deserialize(decoder: Decoder): AnthropicContent {
        val jsonDecoder = decoder as JsonDecoder
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> AnthropicContent.Text(element.content)
            is JsonArray     -> AnthropicContent.Blocks(
                element.map {
                    jsonDecoder.json.decodeFromJsonElement(ContentBlock.serializer(), it)
                }
            )
            else -> AnthropicContent.Text("")
        }
    }
}
