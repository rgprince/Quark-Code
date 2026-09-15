package com.rg.quarkcode.backend

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** Lenient long: accepts number, numeric string, boolean (true=1/false=0), null. */
object LenientLongOrNull : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientLongOrNull", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long? {
        if (decoder is JsonDecoder) {
            val el = decoder.decodeJsonElement()
            if (el is JsonPrimitive) {
                if (el.isString) {
                    val s = el.content.trim()
                    if (s.isEmpty() || s.equals("null", true)) return null
                    s.toLongOrNull()?.let { return it }
                    if (s.equals("true", true)) return 1L
                    if (s.equals("false", true)) return 0L
                    return null
                }
                el.longOrNull?.let { return it }
                el.booleanOrNull?.let { return if (it) 1L else 0L }
                return null
            }
            return null
        }
        return runCatching { decoder.decodeLong() }.getOrNull()
    }
    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }
}

object LenientLong : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long =
        LenientLongOrNull.deserialize(decoder) ?: 0L
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/**
 * Archived is the drawer-empty root cause: some servers send `archived` as a
 * boolean or omit it, while the old `Long?` model threw on booleans and
 * `getList` failed silently to an empty list. Accept number/boolean/string.
 * true -> 1 (archived), false/0/null -> not archived.
 */
object ArchivedSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Archived", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Long? =
        LenientLongOrNull.deserialize(decoder)?.takeIf { it != 0L }
    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }
}

@Serializable
data class Health(
    val healthy: Boolean = false,
    val version: String = ""
)

// Session / message / part shapes for the Serve API.
@Serializable
data class SessionTime(
    @Serializable(with = LenientLong::class) val created: Long = 0L,
    @Serializable(with = LenientLongOrNull::class) val updated: Long? = null,
    @Serializable(with = LenientLongOrNull::class) val completed: Long? = null,
    @Serializable(with = ArchivedSerializer::class) val archived: Long? = null
) {
    /** Normalised: null or 0 = active, anything else = archived. */
    val isArchived: Boolean get() = archived != null && archived != 0L
}

@Serializable
data class SessionTokens(
    @Serializable(with = LenientLong::class) val input: Long = 0L,
    @Serializable(with = LenientLong::class) val output: Long = 0L,
    @Serializable(with = LenientLong::class) val reasoning: Long = 0L,
    val cache: CacheTokens? = null
) {
    // Ring accounting: input + cache reads + generated tokens. The old
    // `input + cache` missed output/reasoning so refreshCost shrank `used`
    // mid-turn and the meter looked stuck.
    val contextUsed: Long
        get() = input + (cache?.read ?: 0L) + output + reasoning
}

@Serializable
data class CacheTokens(
    @Serializable(with = LenientLong::class) val read: Long = 0L,
    @Serializable(with = LenientLong::class) val write: Long = 0L
)

@Serializable
data class SessionInfo(
    val id: String,
    val title: String = "",
    val cost: Double = 0.0,
    @SerialName("parentID") val parentId: String? = null,
    val directory: String? = null,
    val time: SessionTime = SessionTime(),
    val tokens: SessionTokens? = null,
    // Per-session tagged model when the server reports one (null-safe:
    // unknown keys are ignored, so older servers just yield null).
    val model: ModelRef? = null
)

@Serializable
data class SessionStatus(
    val type: String = "idle"
)

/**
 * The drawer-empty crash in the screenshot:
 * `Field 'modelID' is required ... missing at path: $[0].model`.
 * This server sends `"model": {}` (empty object) on sessions, so required
 * `providerID`/`modelID` threw and the whole `GET session` list decoded to
 * nothing. Every field is optional with `""` default and all known key
 * casings (`providerID`/`providerId`/`provider_id`, same for model) are
 * accepted; a missing/empty/null model decodes to `ModelRef("", "")`
 * instead of killing the list.
 */
object ModelRefSerializer : KSerializer<ModelRef> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ModelRef", PrimitiveKind.STRING)

    private fun JsonObject.pick(vararg keys: String): String {
        keys.forEach { key ->
            (get(key) as? JsonPrimitive)?.let {
                if (it.isString) it.content.takeIf { s -> s.isNotBlank() }?.let { v -> return v }
                else it.longOrNull?.let { n -> return n.toString() }
            }
        }
        return ""
    }

    override fun deserialize(decoder: Decoder): ModelRef {
        if (decoder !is JsonDecoder) return ModelRef()
        val el = decoder.decodeJsonElement()
        if (el !is JsonObject) return ModelRef()
        return ModelRef(
            providerId = el.pick("providerID", "providerId", "provider_id", "provider"),
            modelId = el.pick("modelID", "modelId", "model_id", "model", "id", "name")
        )
    }

    override fun serialize(encoder: Encoder, value: ModelRef) {
        if (encoder is kotlinx.serialization.json.JsonEncoder) {
            encoder.encodeJsonElement(
                kotlinx.serialization.json.buildJsonObject {
                    put("providerID", JsonPrimitive(value.providerId))
                    put("modelID", JsonPrimitive(value.modelId))
                }
            )
        } else {
            encoder.encodeString("${value.providerId}/${value.modelId}")
        }
    }
}

@Serializable(with = ModelRefSerializer::class)
data class ModelRef(
    val providerId: String = "",
    val modelId: String = ""
) {
    val isBlank: Boolean get() = providerId.isBlank() && modelId.isBlank()
}

@Serializable
data class MessageInfo(
    val id: String,
    @SerialName("sessionID") val sessionId: String = "",
    val role: String = "user",
    val time: SessionTime = SessionTime(),
    val agent: String? = null,
    val model: ModelRef? = null,
    val tokens: SessionTokens? = null,
    val error: MessageError? = null
)

// Typed message error, e.g. ApiError; aborts never read as failures.
@Serializable
data class MessageError(
    val name: String? = null,
    val data: Map<String, JsonElement>? = null
) {
    val message: String?
        get() = data?.get("message")
            ?.takeIf { it is JsonPrimitive }
            ?.let { (it as JsonPrimitive).content }
            ?.takeIf { it.isNotBlank() }

    val isAbort: Boolean
        get() = name in setOf("MessageAbortedError", "AbortError")
}

@Serializable
data class MessagePart(
    val id: String? = null,
    @SerialName("sessionID") val sessionId: String? = null,
    @SerialName("messageID") val messageId: String? = null,
    val type: String = "text",
    val text: String? = null,
    val filename: String? = null,
    val mime: String? = null,
    val url: String? = null,
    val tool: String? = null,
    val callID: String? = null,
    val state: Map<String, JsonElement>? = null
)

@Serializable
data class MessageWithParts(
    val info: MessageInfo,
    val parts: List<MessagePart> = emptyList()
) {
    val text: String
        get() = parts.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("")
}

// Server event envelope shapes (parsed by ServeEvents).
// Fallback catalog shape: GET config/providers.
@Serializable
data class ProvidersResponse(
    val providers: List<ProviderEntry> = emptyList(),
    val default: Map<String, String> = emptyMap()
)

@Serializable
data class ProviderEntry(
    val id: String = "",
    val name: String = "",
    val models: List<ModelEntry> = emptyList()
)

@Serializable
data class ModelEntry(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class ProviderCatalog(
    val all: List<OpenCodeProvider> = emptyList(),
    val default: Map<String, String> = emptyMap(),
    val connected: List<String> = emptyList()
)

@Serializable
data class OpenCodeProvider(
    val id: String,
    val name: String = "",
    val models: Map<String, OpenCodeModel> = emptyMap()
) {
    fun displayName(): String = name.ifEmpty { id }
}

@Serializable
data class OpenCodeModel(
    val id: String = "",
    @SerialName("providerID") val providerId: String? = null,
    val name: String = "",
    val status: String? = null,
    val limit: OpenCodeModelLimit? = null,
    val variants: Map<String, JsonElement> = emptyMap()
) {
    fun displayName(): String = name.ifEmpty { id }
    fun isActive(): Boolean = status == null || status == "active"
}

@Serializable
data class OpenCodeModelLimit(
    val context: Long = 0L,
    val output: Long = 0L
)

@Serializable
data class ServerTodo(
    val content: String = "",
    val status: String = "",
    val priority: String = ""
)

@Serializable
data class PermissionAsk(
    val id: String,
    @SerialName("sessionID") val sessionId: String,
    val permission: String = "tool",
    val patterns: List<String> = emptyList(),
    val metadata: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class QuestionAsk(
    val id: String,
    @SerialName("sessionID") val sessionId: String,
    val questions: List<QuestionPrompt> = emptyList(),
    val directory: String? = null
)

@Serializable
data class QuestionPrompt(
    val question: String,
    val header: String? = null,
    val options: List<QuestionOption> = emptyList(),
    val multiple: Boolean = false,
    val custom: Boolean = true
)

@Serializable
data class QuestionOption(
    val label: String,
    val description: String? = null
)

@Serializable
data class McpStatus(
    val status: String? = null,
    val error: String? = null
)

// Slash catalog shapes (GET command / GET skill, POST session/{id}/command).
@Serializable
data class OpenCodeCommand(
    val name: String = "",
    val description: String? = null,
    val template: String? = null,
    val source: String? = null
)

@Serializable
data class OpenCodeSkill(
    val name: String = "",
    val description: String? = null,
    val location: String? = null
)

@Serializable
data class OpenCodeAgent(
    val name: String = "",
    val description: String? = null,
    val mode: String? = null
)

// Provider auth shapes (GET provider/auth, PUT auth/{id}, DELETE auth/{id}).
@Serializable
data class ProviderAuthWhen(
    val key: String = "",
    val op: String = "",
    val value: String = ""
)

@Serializable
data class ProviderAuthOption(
    val label: String = "",
    val value: String = ""
)

@Serializable
data class ProviderAuthPrompt(
    val type: String = "",
    val key: String = "",
    val message: String = "",
    val placeholder: String? = null,
    val options: List<ProviderAuthOption> = emptyList()
)

@Serializable
data class ProviderAuthMethod(
    val type: String = "",
    val label: String = "",
    val prompts: List<ProviderAuthPrompt> = emptyList()
)

// Review shapes (GET session/{id}/diff, POST session/{id}/summarize,
// PATCH session/{id} rename).
@Serializable
data class OpenCodeFileChange(
    val file: String? = null,
    val path: String? = null,
    val patch: String? = null,
    val additions: Double = 0.0,
    val deletions: Double = 0.0,
    val status: String? = null
) {
    val displayPath: String get() = file ?: path ?: "unknown"
}
