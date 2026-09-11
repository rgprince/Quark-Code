package com.rg.quarkcode.chat

// Timeline grouping ported from AndCode's AssistantActivityGroup (study-only,
// Quark styling kept). Collapses pending Reasoning+Tool parts into one Activity
// entry so the transcript stays readable; blank reasoning never forms a group.
sealed interface TimelineEntry {
    val id: String

    data class UserMessage(val message: ChatMessage) : TimelineEntry {
        override val id: String = "user:${message.id}"
    }

    data class Body(val messageId: String, val part: ChatPart.Text) : TimelineEntry {
        override val id: String = "body:${part.id}"
    }

    data class Image(val messageId: String, val part: ChatPart.Image) : TimelineEntry {
        override val id: String = "image:${part.id}"
    }

    data class Error(val part: ChatPart.Error) : TimelineEntry {
        override val id: String = "error:${part.id}"
    }

    data class Activity(override val id: String, val parts: List<ChatPart>) : TimelineEntry

    data class Todo(override val id: String, val todos: List<TodoItem>) : TimelineEntry

    data class Footer(override val id: String, val timestamp: Long, val text: String = "") : TimelineEntry
}

fun groupConversationTimeline(messages: List<ChatMessage>): List<TimelineEntry> {
    val entries = mutableListOf<TimelineEntry>()
    messages.forEach { message ->
        if (message.isUser) {
            entries.add(TimelineEntry.UserMessage(message))
            return@forEach
        }
        message.permission?.let {
            // Permission cards render separately above activity; keep message row for parts.
        }
        val pending = mutableListOf<ChatPart>()
        var occurrence = 0
        fun flushActivity() {
            if (pending.isEmpty()) return
            val firstId = pending.first().id
            val key = if (occurrence == 0) "activity:$firstId" else "activity:$firstId:$occurrence"
            occurrence++
            entries.add(TimelineEntry.Activity(key, pending.toList()))
            pending.clear()
        }
        message.parts.forEach { part ->
            when (part) {
                is ChatPart.Reasoning -> {
                    if (part.text.isBlank()) return@forEach
                    pending.add(part)
                }
                is ChatPart.Tool -> {
                    if (part.name == "todowrite" && part.todos.isNotEmpty()) {
                        flushActivity()
                        entries.add(TimelineEntry.Todo("todo:${part.id}", part.todos))
                    } else {
                        pending.add(part)
                    }
                }
                is ChatPart.Patch -> pending.add(part)
                is ChatPart.Text -> {
                    if (part.text.isBlank()) return@forEach
                    flushActivity()
                    entries.add(TimelineEntry.Body(message.id, part))
                }
                is ChatPart.Image -> {
                    flushActivity()
                    entries.add(TimelineEntry.Image(message.id, part))
                }
                is ChatPart.Error -> {
                    flushActivity()
                    entries.add(TimelineEntry.Error(part))
                }
                is ChatPart.QuestionOption -> {
                    // Questions render in the question section; don't break activity grouping.
                }
            }
        }
        flushActivity()
        if (message.timestamp > 0L && message.parts.isNotEmpty() && !message.isStreaming) {
            entries.add(TimelineEntry.Footer("footer:${message.id}", message.timestamp, message.text))
        }
    }
    return entries
}

fun findActivityParts(messages: List<ChatMessage>, groupId: String): List<ChatPart> {
    messages.forEach { message ->
        val pending = mutableListOf<ChatPart>()
        var occurrence = 0
        fun check(): List<ChatPart>? {
            if (pending.isEmpty()) return null
            val firstId = pending.first().id
            val key = if (occurrence == 0) "activity:$firstId" else "activity:$firstId:$occurrence"
            occurrence++
            return if (key == groupId) pending.toList() else null
        }
        message.parts.forEach { part ->
            when (part) {
                is ChatPart.Reasoning -> {
                    if (part.text.isNotBlank()) pending.add(part)
                }
                is ChatPart.Tool -> {
                    if (part.name == "todowrite" && part.todos.isNotEmpty()) {
                        check()?.let { return it }
                    } else {
                        pending.add(part)
                    }
                }
                is ChatPart.Patch -> pending.add(part)
                is ChatPart.Text -> {
                    if (part.text.isNotBlank()) {
                        check()?.let { return it }
                    }
                }
                is ChatPart.Image, is ChatPart.Error -> {
                    check()?.let { return it }
                }
                is ChatPart.QuestionOption -> Unit
            }
        }
        check()?.let { return it }
    }
    return emptyList()
}

enum class ToolCategory { COMMAND, READ, EDIT, SUBAGENT, OTHER }

fun String.toToolCategory(): ToolCategory = when (lowercase()) {
    "bash", "shell", "exec", "command" -> ToolCategory.COMMAND
    "read", "glob", "grep", "ls", "find", "list", "webfetch" -> ToolCategory.READ
    "edit", "write", "patch", "multiedit", "apply_patch" -> ToolCategory.EDIT
    "task", "todowrite", "todo" -> ToolCategory.SUBAGENT
    else -> ToolCategory.OTHER
}

fun summarizeActivity(parts: List<ChatPart>): String {
    val tools = parts.filterIsInstance<ChatPart.Tool>()
    val reasoning = parts.filterIsInstance<ChatPart.Reasoning>().count { it.text.isNotBlank() }
    val patches = parts.filterIsInstance<ChatPart.Patch>().size
    if (parts.isEmpty()) return "Activity"
    val phrases = mutableListOf<String>()
    if (tools.isNotEmpty()) {
        val byCat = tools.groupBy { it.name.toToolCategory() }
        val reads = (byCat[ToolCategory.READ]?.size ?: 0)
        val edits = (byCat[ToolCategory.EDIT]?.size ?: 0) + patches
        val cmds = (byCat[ToolCategory.COMMAND]?.size ?: 0)
        val subs = (byCat[ToolCategory.SUBAGENT]?.size ?: 0)
        val other = (byCat[ToolCategory.OTHER]?.size ?: 0)
        if (tools.size == 1) phrases.add("Ran 1")
        else phrases.add("Ran ${tools.size}")
        if (reads > 0) phrases.add("read $reads")
        if (edits > 0) phrases.add("edit $edits")
        if (cmds > 0) phrases.add("cmd $cmds")
        if (subs > 0) phrases.add("sub $subs")
        if (other > 0 && tools.size <= 2) phrases.add(tools.first().name)
    }
    if (reasoning > 0) phrases.add("thinking")
    return phrases.joinToString(" · ").ifEmpty { "Activity" }
}

fun hideToolCallEcho(text: String): String =
    text.replace(Regex("""Called the \w+ tool with \{[^}]*\}"""), "").trim()

// Compact one-line summary per tool so collapsed rows read like terminal
// output (no raw JSON dumps). Falls back to title, then first input line.
fun prettyToolSummary(part: ChatPart.Tool): String {
    val input = part.input.orEmpty()
    fun field(vararg keys: String): String? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val obj = org.json.JSONObject(trimmed)
            keys.firstNotNullOfOrNull { key ->
                obj.optString(key, "").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
    val raw: String? = when (part.name.lowercase()) {
        "bash", "shell", "exec", "command" ->
            field("command", "cmd", "script")
        "grep", "find", "glob", "ls", "list" ->
            field("pattern", "query", "path", "glob")
        "task" -> field("description", "prompt", "command")
        "read", "edit", "write", "multiedit", "apply_patch" ->
            field("filePath", "path", "file", "edits")
        "webfetch" -> field("url")
        "websearch" -> field("query")
        "todowrite", "todo" ->
            part.todos.takeIf { it.isNotEmpty() }?.let { todos ->
                "${todos.count { t -> t.done }}/${todos.size} done"
            }
        else -> null
    }
    val summary = raw ?: part.title?.takeIf { it.isNotBlank() }
    ?: input.lineSequence().firstOrNull { it.isNotBlank() }
    return summary?.trim()?.take(90).orEmpty()
}
