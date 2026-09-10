package com.quark.agent.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Health(
    val healthy: Boolean = false,
    val version: String = ""
)

@Serializable
data class SessionInfo(
    val id: String,
    val title: String = "",
    val cost: Double = 0.0,
    @SerialName("parentID") val parentId: String? = null
)

@Serializable
data class SessionStatus(
    val type: String = "idle"
)

@Serializable
data class MessagePart(
    val type: String = "text",
    val text: String = "",
    val tool: String? = null,
    val output: String? = null
)

@Serializable
data class MessageInfo(
    val id: String,
    val role: String
)

@Serializable
data class MessageWithParts(
    val info: MessageInfo,
    val parts: List<MessagePart> = emptyList()
)

@Serializable
data class SendMessageBody(
    val model: ModelRef? = null,
    val agent: String? = null,
    val parts: List<MessagePart>
)

@Serializable
data class ModelRef(
    @SerialName("providerID") val providerId: String,
    @SerialName("modelID") val modelId: String
)

@Serializable
data class CreateSessionBody(
    val title: String? = null
)

@Serializable
data class PermissionResponse(
    val response: String,
    val remember: Boolean = false
)

@Serializable
data class ServerTodo(
    val id: String = "",
    val content: String = "",
    val status: String = ""
)

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
data class McpStatus(
    val enabled: Boolean = false,
    val version: String = ""
)

@Serializable
data class McpAddBody(
    val name: String,
    val config: McpAddConfig
)

@Serializable
data class McpAddConfig(
    val type: String = "remote",
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)
