package com.rg.quarkcode.chat

// Quark-original transcript mapping.
// Built directly from the opencode Serve API shapes declared in
// backend/ServeModels.kt (MessagePart / MessageWithParts / MessageInfo).
// No third-party client source was referenced for this file.

import com.rg.quarkcode.backend.MessagePart
import com.rg.quarkcode.backend.MessageWithParts
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class ToolStatus { PENDING, RUNNING, COMPLETED, ERROR, UNKNOWN }

data class TodoItem(val id: String, val text: String, val done: Boolean)

// UI-level pieces derived from server parts. Field names are stable because
// Timeline.kt / MessageList.kt / ChatViewModel.kt consume them.
/* Quark transcript pieces: order groups tiny inline kinds first. */
sealed interface ChatPart {
    val id: String
    data class Text(override val id: String, val text: String) : ChatPart
    data class Image(override val id: String, val mime: String, val url: String, val filename: String? = null) : ChatPart
    data class Reasoning(override val id: String, val text: String) : ChatPart
    data class QuestionOption(override val id: String, val requestId: String, val label: String, val description: String? = null) : ChatPart
    /* Tool + patch carry structured payloads, kept after the inline kinds. */
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
    data class Error(override val id: String, val message: String) : ChatPart
}

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val parts: List<ChatPart> = emptyList(),
    val timestamp: Long = 0L,
    val isStreaming: Boolean = false,
    val permission: PermissionRequest? = null,
    val durationMs: Long? = null
) {
    val text: String
        get() = parts.filterIsInstance<ChatPart.Text>().joinToString("") { it.text }
}

data class PermissionRequest(val id: String, val tool: String, val summary: String, val remember: Boolean = false)

private const val TOOL_TAIL_KEEP = 4000

fun MessagePart.toChatPart(): ChatPart? = buildByKind()

// Real media builder kept separate so the dispatch stays readable.
private fun MessagePart.toMediaPart(): ChatPart? {
    val pid = id ?: return null
    val link = url.orEmpty()
    if (link.isBlank()) return null
    val guess = guessImageMime(mime, link) ?: return null
    return ChatPart.Image(id = pid, mime = guess, url = link, filename = filename)
}

private fun MessagePart.toToolPart(): ChatPart? {
    val pid = id ?: return null
    val bag = state.orEmpty()
    val shortInput = shortenInput(bag["input"])
    val rawOut = bag["output"]?.asTrimmed()
    val sliced = rawOut != null && rawOut.length > TOOL_TAIL_KEEP
    val label = tool ?: "tool"
    val headLine = bag["title"]?.asTrimmed()?.takeIf { it.isNotBlank() }
        ?: shortInput?.lineSequence()?.firstOrNull()
    val stateText = bag["status"]?.asTrimmed()
    return ChatPart.Tool(
        id = pid,
        name = label,
        status = toolPhase(stateText),
        title = headLine,
        input = shortInput,
        output = if (sliced) rawOut?.takeLast(TOOL_TAIL_KEEP) else rawOut,
        outputTruncated = sliced,
        error = bag["error"]?.asTrimmed(),
        todos = if (label == "todowrite") readTodoList(bag["input"]) else emptyList()
    )
}

// Dispatch helper that keeps MessagePart fields (tool/url/mime) intact.
// This is the function actually used by toChatPart() via extension delegation.
private fun MessagePart.buildByKind(): ChatPart? {
    return when (type) {
        "text" -> {
            val pid = id ?: return null
            ChatPart.Text(pid, text.orEmpty())
        }
        "reasoning" -> {
            val pid = id ?: return null
            ChatPart.Reasoning(pid, pickThinkingText(text, state.orEmpty()))
        }
        "file", "image" -> toMediaPart()
        "tool" -> toToolPart()
        "patch" -> {
            val pid = id ?: return null
            ChatPart.Patch(pid, collectPatchPaths(state.orEmpty()))
        }
        else -> null
    }
}

fun extractReasoningText(topLevelText: String?, state: Map<String, JsonElement>): String =
    pickThinkingText(topLevelText, state)

private fun pickThinkingText(top: String?, bag: Map<String, JsonElement>): String {
    if (!top.isNullOrBlank()) return top
    for (key in arrayOf("text", "content", "reasoning")) {
        val hit = bag[key]?.asTrimmed()
        if (!hit.isNullOrBlank()) return hit
    }
    for (key in arrayOf("reasoningDetails", "reasoning_details", "details")) {
        val deep = detailText(bag[key])
        if (!deep.isNullOrBlank()) return deep
    }
    return ""
}

private fun detailText(node: JsonElement?): String? {
    val list = node as? JsonArray ?: return null
    val out = StringBuilder()
    for (entry in list) {
        val obj = entry as? JsonObject ?: continue
        val hit = obj["text"]?.asTrimmed()?.takeIf { it.isNotBlank() }
            ?: obj["content"]?.asTrimmed()?.takeIf { it.isNotBlank() }
            ?: obj["summary"]?.asTrimmed()?.takeIf { it.isNotBlank() }
            ?: continue
        out.append(hit)
    }
    val merged = out.toString()
    return merged.takeIf { it.isNotBlank() }
}

fun imageMime(declaredMime: String?, url: String): String? = guessImageMime(declaredMime, url)

private fun guessImageMime(claimed: String?, link: String): String? {
    if (claimed != null && claimed.startsWith("image/")) return claimed
    if (link.startsWith("data:image/")) {
        val after = link.removePrefix("data:").substringBefore(';', "")
        if (after.isNotBlank()) return after
    }
    val tail = link.substringBefore('?').substringBefore('#')
    val ext = tail.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> null
    }
}

fun parseToolStatus(raw: String?): ToolStatus = toolPhase(raw)

private fun toolPhase(value: String?): ToolStatus {
    return when (value?.lowercase()) {
        "pending" -> ToolStatus.PENDING
        "running", "in_progress" -> ToolStatus.RUNNING
        "completed", "done", "success" -> ToolStatus.COMPLETED
        "error", "failed" -> ToolStatus.ERROR
        else -> ToolStatus.UNKNOWN
    }
}

fun formatToolInput(element: JsonElement?): String? = shortenInput(element)

private fun shortenInput(node: JsonElement?): String? {
    if (node == null) return null
    val prim = node as? JsonPrimitive
    if (prim != null) return prim.content.take(500)
    return node.toString().take(500)
}

fun parseTodosFromInput(element: JsonElement?): List<TodoItem> = readTodoList(element)

private fun readTodoList(node: JsonElement?): List<TodoItem> {
    val root = node as? JsonObject ?: return emptyList()
    val rows = root["todos"] as? JsonArray ?: return emptyList()
    val acc = ArrayList<TodoItem>(rows.size)
    rows.forEachIndexed { index, row ->
        val obj = row as? JsonObject ?: return@forEachIndexed
        val body = (obj["content"] as? JsonPrimitive)?.content ?: return@forEachIndexed
        val flag = (obj["status"] as? JsonPrimitive)?.content ?: "pending"
        val finished = flag.equals("completed", ignoreCase = true) || flag.equals("done", ignoreCase = true)
        acc.add(TodoItem(id = "todowrite-$index", text = body, done = finished))
    }
    return acc
}

fun parseTodosFromMarkdown(body: String): List<TodoItem> {
    val check = Regex("""^-\s*\[( |x|X)]\s+(.+)$""", RegexOption.MULTILINE)
    return check.findAll(body).mapIndexed { index, hit ->
        val mark = hit.groupValues[1].trim()
        TodoItem(
            id = "todo-$index-${hit.value.hashCode()}",
            text = hit.groupValues[2].trim(),
            done = mark.equals("x", ignoreCase = true)
        )
    }.toList()
}

private fun collectPatchPaths(bag: Map<String, JsonElement>): List<String> {
    val rows = bag["files"] as? JsonArray ?: return emptyList()
    val acc = ArrayList<String>(rows.size)
    for (row in rows) {
        val name = (row as? JsonPrimitive)?.content ?: continue
        acc.add(name)
    }
    return acc
}

fun MessageWithParts.toUiMessage(): ChatMessage? {
    if (info.id.isBlank()) return null
    val uiParts = parts.mapNotNull { it.buildByKind() }
    val fault = info.error
    val withFault = if (fault != null && !fault.isAbort) {
        val why = fault.message ?: fault.name ?: "Request failed"
        uiParts + ChatPart.Error(id = "${info.id}-error", message = why)
    } else {
        uiParts
    }
    val span = info.time.completed?.let { end ->
        val start = info.time.created
        if (start > 0L && end > start) end - start else null
    }
    return ChatMessage(
        id = info.id,
        isUser = info.role == "user",
        parts = withFault,
        timestamp = info.time.created,
        durationMs = span
    )
}

// A turn is over when every fresh assistant message has no tool still open.
// Fresh = assistant + id not seen before this send.
fun turnFinished(
    serverMessages: List<MessageWithParts>,
    idsBeforeSend: Set<String>,
    mapped: List<ChatMessage>
): Boolean {
    val live = serverMessages.filter { it.info.role == "assistant" && it.info.id !in idsBeforeSend }
    if (live.isEmpty()) return false
    val known = mapped.associateBy { it.id }
    for (msg in live) {
        val tools = known[msg.info.id]?.parts?.filterIsInstance<ChatPart.Tool>() ?: emptyList()
        for (t in tools) {
            if (t.status == ToolStatus.RUNNING || t.status == ToolStatus.PENDING) return false
        }
    }
    return true
}

// Keep streamed-only rows until the transcript re-emits them under the same id.
fun mergeReloadedMessages(
    reloaded: List<ChatMessage>,
    existing: List<ChatMessage>,
    retainIds: Set<String> = emptySet()
): List<ChatMessage> {
    if (reloaded.isEmpty()) return existing
    if (retainIds.isEmpty()) return ArrayList(reloaded)
    val seen = HashSet<String>(reloaded.size * 2)
    for (m in reloaded) seen.add(m.id)
    val keep = ArrayList<ChatMessage>(existing.size)
    for (m in existing) {
        if (m.id in retainIds && m.id !in seen) keep.add(m)
    }
    if (keep.isEmpty()) return ArrayList(reloaded)
    val out = ArrayList<ChatMessage>(reloaded.size + keep.size)
    out.addAll(reloaded)
    out.addAll(keep)
    return out
}

private fun JsonElement.asTrimmed(): String? =
    (this as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
