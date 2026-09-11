package com.rg.quarkcode.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Health(
    val healthy: Boolean = false,
    val version: String = ""
)

// Session / message / part shapes ported from AndCode's OpenCodeApiModels.
@Serializable
data class SessionTime(
    val created: Long = 0L,
    val updated: Long? = null,
    val completed: Long? = null,
    val archived: Long? = null
)

@Serializable
data class SessionTokens(
    val input: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val cache: CacheTokens? = null
) {
    val contextUsed: Long
        get() = input + (cache?.read ?: 0L)
}

@Serializable
data class CacheTokens(
    val read: Long = 0L,
    val write: Long = 0L
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

@Serializable
data class ModelRef(
    @SerialName("providerID") val providerId: String,
    @SerialName("modelID") val modelId: String
)

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
// Fallback catalog shape: GET config/providers (subset of AndCode's).
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

// Slash catalog shapes (AndCode IA: GET command / GET skill, POST session/{id}/command).
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

// Provider auth shapes (AndCode IA: GET provider/auth, PUT auth/{id}, DELETE auth/{id}).
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

// Review shapes (AndCode IA: GET session/{id}/diff, POST session/{id}/summarize,
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
