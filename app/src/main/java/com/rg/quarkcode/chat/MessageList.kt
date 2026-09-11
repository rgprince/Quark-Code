package com.rg.quarkcode.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    expandedParts: Set<String>,
    thinking: Boolean,
    autoExpandReasoning: Boolean,
    speakingId: String?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    detailedTools: Boolean = false,
    onTogglePart: (String) -> Unit = {},
    onToggleTodo: (String, String) -> Unit = { _, _ -> },
    onAllow: (String) -> Unit = {},
    onDeny: (String) -> Unit = {},
    onRememberChange: (String, Boolean) -> Unit = { _, _ -> },
    onRetry: (String) -> Unit = {},
    onAnswer: (String, String) -> Unit = { _, _ -> },
    onSpeak: (String, String) -> Unit = { _, _ -> }
) {
    val timeline = remember(messages) { groupConversationTimeline(messages) }
    var sheetGroupId by remember { mutableStateOf<String?>(null) }
    val sheetParts = remember(sheetGroupId, messages) {
        sheetGroupId?.let { findActivityParts(messages, it) } ?: emptyList()
    }
    val listState = rememberLazyListState()
    // Smooth follow: stick to bottom on new content only when already near the end.
    LaunchedEffect(timeline.size, thinking) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (last - visible <= 2) {
            listState.animateScrollToItem(last)
        }
    }
    val hasRunningActivity = remember(timeline) {
        timeline.any { entry ->
            entry is TimelineEntry.Activity && entry.parts.filterIsInstance<ChatPart.Tool>().any {
                it.status == ToolStatus.RUNNING || it.status == ToolStatus.PENDING
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(timeline, key = { it.id }, contentType = { it.javaClass.simpleName }) { entry ->
            when (entry) {
                is TimelineEntry.UserMessage -> UserBubble(
                    message = entry.message,
                    modifier = Modifier.animateItem()
                )
                is TimelineEntry.Body -> AssistantBody(
                    text = hideToolCallEcho(entry.part.text),
                    modifier = Modifier.animateItem()
                )
                is TimelineEntry.Image -> AsyncImage(
                    model = entry.part.url,
                    contentDescription = entry.part.filename ?: "Attached image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .animateItem()
                )
                is TimelineEntry.Error -> Card(
                    modifier = Modifier.animateItem(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text(
                                text = "Something went wrong",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = entry.part.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { onRetry(entry.part.id) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                is TimelineEntry.Activity -> {
                    val open = expandedParts.contains(entry.id)
                    val running = entry.parts.filterIsInstance<ChatPart.Tool>().any {
                        it.status == ToolStatus.RUNNING || it.status == ToolStatus.PENDING
                    }
                    Column(modifier = Modifier.animateItem()) {
                        AssistantActivityRow(
                            parts = entry.parts,
                            running = running,
                            expanded = open,
                            onToggle = { onTogglePart(entry.id) },
                            onOpenSheet = { sheetGroupId = entry.id }
                        )
                        AnimatedVisibility(
                            visible = open,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(6.dp))
                                entry.parts.forEach { part ->
                                    when (part) {
                                        is ChatPart.Reasoning -> {
                                            if (part.text.isNotBlank()) {
                                                ReasoningCard(
                                                    part = part,
                                                    expanded = autoExpandReasoning || expandedParts.contains(part.id),
                                                    onToggle = { onTogglePart(part.id) }
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }
                                        }
                                        is ChatPart.Tool -> {
                                            if (part.name == "todowrite" && part.todos.isNotEmpty()) {
                                                TodoCard(todos = part.todos, onToggle = null)
                                            } else {
                                                QuarkToolCard(
                                                    part = part,
                                                    messageId = "",
                                                    detailed = detailedTools,
                                                    onToggleTodo = onToggleTodo
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                        is ChatPart.Patch -> {
                                            PatchInlineCard(files = part.files)
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
                is TimelineEntry.Todo -> TodoCard(
                    todos = entry.todos,
                    onToggle = null,
                    modifier = Modifier.animateItem()
                )
                is TimelineEntry.Footer -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (entry.text.isNotBlank()) {
                        val speaking = speakingId == entry.id
                        IconButton(
                            onClick = { onSpeak(entry.id, entry.text) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (speaking) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = if (speaking) "Stop readout" else "Read aloud",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Text(
                        text = formatTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Live status lives at the transcript tail, never above the composer.
        // Suppressed while a running activity row already shows progress.
        if (thinking && !hasRunningActivity) {
            item(key = "thinking-tail") {
                ThinkingTail(modifier = Modifier.animateItem())
            }
        }
        // Permissions + questions render after timeline, like AndCode.
        items(messages.mapNotNull { it.permission }, key = { "perm:${it.id}" }) { request ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                modifier = Modifier.animateItem()
            ) {
                PermissionCard(
                    request = request,
                    onAllow = { onAllow(request.id) },
                    onDeny = { onDeny(request.id) },
                    onRememberChange = { remember -> onRememberChange(request.id, remember) }
                )
            }
        }
        items(
            messages.flatMap { m -> m.parts.filterIsInstance<ChatPart.QuestionOption>().map { m.id to it } },
            key = { (_, opt) -> opt.id }
        ) { (_, opt) ->
            QuestionCard(
                options = listOf(opt),
                onAnswer = onAnswer,
                modifier = Modifier.animateItem()
            )
        }
    }
    sheetGroupId?.let {
        if (sheetParts.isNotEmpty()) {
            AssistantActivitySheet(
                parts = sheetParts,
                messageId = "",
                autoExpandReasoning = autoExpandReasoning,
                detailedTools = detailedTools,
                onToggleTodo = onToggleTodo,
                onDismiss = { sheetGroupId = null }
            )
        }
    }
}

@Composable
private fun ThinkingTail(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Assistant is working"
        },
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = "Thinking…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UserBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val body = message.text
    if (body.isBlank()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .semantics(mergeDescendants = true) {
                    if (message.timestamp > 0L) {
                        contentDescription = "You at ${formatTime(message.timestamp)}: $body"
                    }
                },
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp
            ),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                SelectionContainer {
                    Text(text = hideToolCallEcho(body), style = MaterialTheme.typography.bodyMedium)
                }
                if (message.timestamp > 0L) {
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantBody(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    val blocks = remember(text) { parseMarkdownLite(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                blocks.forEach { block ->
                    when (block) {
                        is LiteBlock.Prose -> Text(
                            text = renderInline(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        is LiteBlock.Code -> CodeBlockCard(lang = block.lang, code = block.code)
                    }
                }
            }
        }
    }
}

private sealed interface LiteBlock {
    data class Prose(val text: String, val style: androidx.compose.ui.text.TextStyle) : LiteBlock
    data class Code(val lang: String, val code: String) : LiteBlock
}

@Composable
private fun androidx.compose.ui.text.TextStyle.withDefault(): androidx.compose.ui.text.TextStyle = this

private fun parseMarkdownLite(text: String): List<LiteBlock> {
    val out = mutableListOf<LiteBlock>()
    val lines = text.split("\n")
    val prose = StringBuilder()
    var i = 0
    fun flushProse() {
        if (prose.isNotEmpty()) {
            out.add(LiteBlock.Prose(prose.toString().trim(), androidx.compose.ui.text.TextStyle.Default))
            prose.clear()
        }
    }
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            flushProse()
            val lang = line.trim().removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.appendLine(lines[i])
                i++
            }
            out.add(LiteBlock.Code(lang.ifBlank { "code" }, code.toString().trimEnd()))
        } else {
            prose.appendLine(line)
        }
        i++
    }
    flushProse()
    return out.ifEmpty { listOf(LiteBlock.Prose(text, androidx.compose.ui.text.TextStyle.Default)) }
}

@Composable
private fun renderInline(text: String): AnnotatedString {
    // Handles **bold** and `inline code`; headings render via block prefix.
    return buildAnnotatedString {
        var t = text
        // Strip heading markers for display.
        t.lines().forEachIndexed { idx, line ->
            if (idx > 0) append("\n")
            val stripped = when {
                line.startsWith("### ") -> line.removePrefix("### ")
                line.startsWith("## ") -> line.removePrefix("## ")
                line.startsWith("# ") -> line.removePrefix("# ")
                line.startsWith("> ") -> line.removePrefix("> ")
                else -> line
            }
            var rest = stripped
            while (rest.isNotEmpty()) {
                val bold = Regex("""\*\*(.+?)\*\*""").find(rest)
                val code = Regex("""`(.+?)`""").find(rest)
                val next = listOfNotNull(
                    bold?.let { it.range.first to it },
                    code?.let { it.range.first to it }
                ).minByOrNull { it.first }?.second
                if (next == null) {
                    append(rest)
                    break
                }
                append(rest.substring(0, next.range.first))
                if (next.value.startsWith("**")) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(next.groupValues[1])
                    }
                } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = androidx.compose.ui.graphics.Color.Transparent
                        )
                    ) {
                        append(next.groupValues[1])
                    }
                }
                rest = rest.substring(next.range.last + 1)
            }
        }
    }
}

@Composable
private fun CodeBlockCard(lang: String, code: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lang,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PatchInlineCard(files: List<String>, modifier: Modifier = Modifier) {
    if (files.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Patch · ${files.size} file(s)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            files.forEach { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "  $file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
