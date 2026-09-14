package com.rg.quarkcode.chat

import com.rg.quarkcode.backend.MessagePart
import com.rg.quarkcode.backend.MessageWithParts
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class ToolStatus { PENDING, RUNNING, COMPLETED, ERROR, UNKNOWN }

data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean
)

sealed interface ChatPart {
    val id: String

    data class Text(override val id: String, val text: String) : ChatPart
    data class Reasoning(override val id: String, val text: String) : ChatPart
    data class Tool(
        override val id: String,
        val name: String,
        val status: ToolStatus,
        val title: String? = null,
        val input: String? = null,
        val output: String? = null,
        val outputTruncated: Boolean = false,
        val error: String? = null,
        val todos: List<TodoItem> = emptyList()
    ) : ChatPart

    data class Patch(override val id: String, val files: List<String>) : ChatPart
    data class Image(
        override val id: String,
        val mime: String,
        val url: String,
        val filename: String? = null
    ) : ChatPart

    data class QuestionOption(
        override val id: String,
        val requestId: String,
        val label: String,
        val description: String? = null
    ) : ChatPart

    data class Error(override val id: String, val message: String) : ChatPart
}

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val parts: List<ChatPart> = emptyList(),
    val timestamp: Long = 0L,
    val isStreaming: Boolean = false,
    val permission: PermissionRequest? = null
) {
    val text: String
        get() = parts.filterIsInstance<ChatPart.Text>().joinToString("") { it.text }
}

data class PermissionRequest(
    val id: String,
    val tool: String,
    val summary: String,
    val remember: Boolean = false
)

private const val MAX_TOOL_OUTPUT_CHARS = 4000

fun MessagePart.toChatPart(): ChatPart? {
    val partId = id ?: return null
    val stateMap = state.orEmpty()
    return when (type) {
        "text" -> ChatPart.Text(partId, text.orEmpty())
        "reasoning" -> ChatPart.Reasoning(partId, extractReasoningText(text, stateMap))
        "file", "image" -> {
            val partUrl = url.orEmpty()
            val partMime = imageMime(mime, partUrl)
            if (partMime != null && partUrl.isNotBlank()) {
                ChatPart.Image(id = partId, mime = partMime, url = partUrl, filename = filename)
            } else {
                null
            }
        }
        "tool" -> {
            val inputText = formatToolInput(stateMap["input"])
            val rawOutput = stateMap["output"]?.jsonPrimitiveOrNull()
            val truncated = rawOutput != null && rawOutput.length > MAX_TOOL_OUTPUT_CHARS
            val toolName = tool ?: "tool"
            ChatPart.Tool(
                id = partId,
                name = toolName,
                status = parseToolStatus(stateMap["status"]?.jsonPrimitiveOrNull()),
                title = stateMap["title"]?.jsonPrimitiveOrNull()?.takeIf { it.isNotBlank() }
                    ?: inputText?.lineSequence()?.firstOrNull(),
                input = inputText,
                output = if (truncated) rawOutput?.takeLast(MAX_TOOL_OUTPUT_CHARS) else rawOutput,
                outputTruncated = truncated,
                error = stateMap["error"]?.jsonPrimitiveOrNull(),
                todos = if (toolName == "todowrite") {
                    parseTodosFromInput(stateMap["input"])
                } else {
                    emptyList()
                }
            )
        }
        "patch" -> ChatPart.Patch(partId, extractPatchFiles(stateMap))
        else -> null
    }
}

fun extractReasoningText(topLevelText: String?, state: Map<String, JsonElement>): String {
    if (!topLevelText.isNullOrBlank()) return topLevelText
    state["text"]?.jsonPrimitiveOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
    state["content"]?.jsonPrimitiveOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
    state["reasoning"]?.jsonPrimitiveOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
    for (key in listOf("reasoningDetails", "reasoning_details", "details")) {
        extractReasoningDetailsText(state[key])?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return ""
}

private fun extractReasoningDetailsText(element: JsonElement?): String? {
    val array = element as? JsonArray ?: return null
    return array.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        obj["text"]?.jsonPrimitiveOrNull()?.takeIf { it.isNotBlank() }
            ?: obj["content"]?.jsonPrimitiveOrNull()?.takeIf { it.isNotBlank() }
            ?: obj["summary"]?.jsonPrimitiveOrNull()?.takeIf { it.isNotBlank() }
    }.joinToString("").takeIf { it.isNotBlank() }
}

fun imageMime(declaredMime: String?, url: String): String? {
    declaredMime?.takeIf { it.startsWith("image/") }?.let { return it }
    if (url.startsWith("data:image/")) {
        return url.substringAfter("data:").substringBefore(';').takeIf { it.isNotBlank() }
    }
    return when (url.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> null
    }
}

fun parseToolStatus(raw: String?): ToolStatus = when (raw?.lowercase()) {
    "pending" -> ToolStatus.PENDING
    "running", "in_progress" -> ToolStatus.RUNNING
    "completed", "done", "success" -> ToolStatus.COMPLETED
    "error", "failed" -> ToolStatus.ERROR
    else -> ToolStatus.UNKNOWN
}

fun formatToolInput(element: JsonElement?): String? {
    if (element == null) return null
    (element as? JsonPrimitive)?.let { return it.content.take(500) }
    return element.toString().take(500)
}

fun parseTodosFromInput(element: JsonElement?): List<TodoItem> {
    val obj = element as? JsonObject ?: return emptyList()
    val todos = obj["todos"] as? JsonArray ?: return emptyList()
    return todos.mapIndexedNotNull { index, entry ->
        val item = entry as? JsonObject ?: return@mapIndexedNotNull null
        val content = (item["content"] as? JsonPrimitive)?.content ?: return@mapIndexedNotNull null
        val status = (item["status"] as? JsonPrimitive)?.content ?: "pending"
        TodoItem(
            id = "todowrite-$index",
            text = content,
            done = status.equals("completed", ignoreCase = true) ||
                status.equals("done", ignoreCase = true)
        )
    }
}

fun parseTodosFromMarkdown(body: String): List<TodoItem> {
    val pattern = Regex("""^-\s*\[( |x|X)]\s+(.+)$""", RegexOption.MULTILINE)
    return pattern.findAll(body).mapIndexed { index, match ->
        TodoItem(
            id = "todo-$index-${match.value.hashCode()}",
            text = match.groupValues[2].trim(),
            done = match.groupValues[1].trim().equals("x", ignoreCase = true)
        )
    }.toList()
}

private fun extractPatchFiles(state: Map<String, JsonElement>): List<String> {
    val files = state["files"] as? JsonArray ?: return emptyList()
    return files.mapNotNull { (it as? JsonPrimitive)?.content }
}

fun MessageWithParts.toUiMessage(): ChatMessage? {
    if (info.id.isBlank()) return null
    val parts = parts.mapNotNull { it.toChatPart() }
    val error = info.error
    val allParts = if (error != null && !error.isAbort) {
        parts + ChatPart.Error(
            id = "${info.id}-error",
            message = error.message ?: error.name ?: "Request failed"
        )
    } else {
        parts
    }
    return ChatMessage(
        id = info.id,
        isUser = info.role == "user",
        parts = allParts,
        timestamp = info.time.created
    )
}

// Completion is read off the transcript: a fresh assistant message with no
// tools still RUNNING/PENDING means done. A fresh message alone is NOT
// enough — the first text chunk commits mid-turn while tools still run, and
// ending there parked the composer on send for the rest of the turn.
fun turnFinished(
    serverMessages: List<MessageWithParts>,
    idsBeforeSend: Set<String>,
    mapped: List<ChatMessage>
): Boolean {
    val fresh = serverMessages.filter { it.info.role == "assistant" && it.info.id !in idsBeforeSend }
    if (fresh.isEmpty()) return false
    val byId = mapped.associateBy { it.id }
    return fresh.none { msg ->
        byId[msg.info.id]?.parts?.filterIsInstance<ChatPart.Tool>()?.any {
            it.status == ToolStatus.RUNNING || it.status == ToolStatus.PENDING
        } == true
    }
}

// Reload merge that keeps streamed-only messages until the transcript
// produces its own copy under the same id.
fun mergeReloadedMessages(
    reloaded: List<ChatMessage>,
    existing: List<ChatMessage>,
    retainIds: Set<String> = emptySet()
): List<ChatMessage> {
    if (reloaded.isEmpty()) return existing
    val reloadedIds = reloaded.map { it.id }.toSet()
    val retained = existing.filter { it.id in retainIds && it.id !in reloadedIds }
    return reloaded + retained
}

private fun JsonElement.jsonPrimitiveOrNull(): String? =
    (this as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
