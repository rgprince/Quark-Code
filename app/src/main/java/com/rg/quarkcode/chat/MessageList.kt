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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
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
    busy: Boolean = false,
    awaitingReply: Boolean = false,
    autoScroll: Boolean = true,
    playfulStatus: Boolean = true,
    showThoughts: Boolean = true,
    comfortable: Boolean = false,
    showTimestamps: Boolean = true,
    thinkingSecs: Int = 0,
    thoughtMs: Long? = null,
    thoughtText: String = "",
    thoughtExpanded: Boolean = false,
    onToggleThought: () -> Unit = {},
    textScale: Float = 1f,
    streamingIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    detailedTools: Boolean = false,
    onTogglePart: (String) -> Unit = {},
    onToggleTodo: (String, String) -> Unit = { _, _ -> },
    onAllow: (String) -> Unit = {},
    onDeny: (String) -> Unit = {},
    onRememberChange: (String, Boolean) -> Unit = { _, _ -> },
    onRetry: (String) -> Unit = {},
    onAnswer: (String, String) -> Unit = { _, _ -> }
) {
    val timeline = remember(messages) { groupConversationTimeline(messages) }
    var sheetGroupId by remember { mutableStateOf<String?>(null) }
    val sheetParts = remember(sheetGroupId, messages) {
        sheetGroupId?.let { findActivityParts(messages, it) } ?: emptyList()
    }
    val listState = rememberLazyListState()
    // Smooth follow: stick to bottom on new content only when already near the end.
    LaunchedEffect(timeline.size, thinking, autoScroll) {
        if (!autoScroll) return@LaunchedEffect
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
    val lastUserIndex = remember(timeline) {
        timeline.indexOfLast { it is TimelineEntry.UserMessage }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(if (comfortable) 10.dp else 6.dp)
    ) {
        itemsIndexed(
            timeline,
            key = { _, entry -> entry.id },
            contentType = { _, entry -> entry.javaClass.simpleName }
        ) { index, entry ->
            when (entry) {
                is TimelineEntry.UserMessage -> {
                    UserBubble(
                        message = entry.message,
                        textScale = textScale,
                        showTimestamp = showTimestamps,
                        modifier = Modifier.animateItem()
                    )
                    // Think-then-reply order: the thought line belongs right
                    // after the prompt it answered. Shown when the live timer
                    // recorded a phase OR when the transcript itself holds
                    // reasoning — old chats never run the timer, so without
                    // this fallback the line never appears for them.
                    if (index == lastUserIndex && showThoughts &&
                        (thoughtMs != null || thoughtText.isNotBlank())
                    ) {
                        ThoughtDoneRow(
                            ms = thoughtMs,
                            text = thoughtText,
                            expanded = thoughtExpanded,
                            onToggle = onToggleThought,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                is TimelineEntry.Body -> AssistantBody(
                    text = hideToolCallEcho(entry.part.text),
                    textScale = textScale,
                    streaming = streamingIds.contains(entry.messageId),
                    modifier = Modifier.animateItem()
                )
                is TimelineEntry.Image -> AsyncImage(
                    model = entry.part.url,
                    contentDescription = entry.part.filename ?: "Attached image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
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
                            // Retry needs the MESSAGE id (VM looks up the last
                            // user text before it) — the old code passed the
                            // part id, so retry silently did nothing.
                            onClick = { onRetry(entry.messageId) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                is TimelineEntry.Activity -> {
                    // Reasoning-only groups carry no tools: the thought-line
                    // after the prompt (ThoughtDoneRow) is their only surface.
                    if (entry.parts.none { it is ChatPart.Tool || it is ChatPart.Patch }) {
                        return@itemsIndexed
                    }
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
                                Spacer(modifier = Modifier.height(4.dp))
                                entry.parts.forEach { part ->
                                    when (part) {
                                        // Reasoning lives in the thought line above the reply.
                                        is ChatPart.Reasoning -> Unit
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
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        is ChatPart.Patch -> {
                                            PatchInlineCard(files = part.files)
                                            Spacer(modifier = Modifier.height(4.dp))
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
                is TimelineEntry.Footer -> {
                    val showTime = showTimestamps && entry.timestamp > 0L
                    if (showTime) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                    Text(
                        text = formatTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
                }
            }
        }
        // Live status lives at the transcript tail, never above the composer.
        // While the prompt has NO reply content at all, a playful cooking
        // bubble keeps the illusion of work (words rotate automatically and
        // it is replaced the moment real content streams in). Otherwise the
        // thinking tail shows, derived from busy so a long pre-tool phase
        // with no activity row yet never looks dead. Both are suppressed
        // while a running activity row already shows progress.
        val lastIsUser = messages.lastOrNull()?.isUser == true
        val showCooking = awaitingReply && lastIsUser && playfulStatus
        if (showCooking) {
            item(key = "cooking-bubble") {
                CookingBubble(modifier = Modifier.animateItem())
            }
        } else if ((thinking || busy) && !hasRunningActivity) {
            item(key = "thinking-tail") {
                ThinkingTail(
                    seconds = thinkingSecs,
                    modifier = Modifier.animateItem()
                )
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
                detailedTools = detailedTools,
                onToggleTodo = onToggleTodo,
                onDismiss = { sheetGroupId = null }
            )
        }
    }
}

// Thought line: italic tertiary text with a tertiary side bar, sitting just
// above the model response. Time sits beside the label ("Thought · 16ms");
// tap expands the reasoning text.
@Composable
private fun ThoughtDoneRow(
    ms: Long?,
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = text.isNotBlank(), onClick = onToggle)
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.tertiary)
            )
            Text(
                text = if (ms != null) "Thought · ${formatDuration(ms)}" else "Thought",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontStyle = FontStyle.Italic
                ),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            if (text.isNotBlank()) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide reasoning" else "Show reasoning",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (expanded && text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 2.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String =
    if (ms < 1000L) "${ms.coerceAtLeast(0L)}ms"
    else "%.1fs".format(ms / 1000f)

// Playful pre-reply bubble: while the sent prompt has no reply content yet,
// shows a soft assistant-style bubble cycling "Cooking… / Doodling… / …"
// every ~1.4s. Pure illusion-of-work; vanishes the moment real content lands.
@Composable
private fun CookingBubble(
    modifier: Modifier = Modifier
) {
    val words = remember {
        listOf("Cooking", "Doodling", "Crafting", "Brewing", "Stirring", "Sketching", "Simmering", "Polishing")
    }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1400L)
            index = (index + 1) % words.size
        }
    }
    Row(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            modifier = Modifier.semantics { contentDescription = "Assistant is ${words[index]}" }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                Text(
                    text = "${words[index]}…",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun ThinkingTail(    seconds: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics { contentDescription = "Assistant is thinking" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.tertiary)
        )
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
        Text(
            text = if (seconds >= 3) "thinking… ${seconds}s" else "thinking…",
            style = MaterialTheme.typography.labelMedium.copy(
                fontStyle = FontStyle.Italic
            ),
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun UserBubble(
    message: ChatMessage,
    textScale: Float = 1f,
    showTimestamp: Boolean = true,
    modifier: Modifier = Modifier
) {
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
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                SelectionContainer {
                    Text(
                        text = hideToolCallEcho(body),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale
                        )
                    )
                }
                if (showTimestamp && message.timestamp > 0L) {
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
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
private fun AssistantBody(
    text: String,
    textScale: Float = 1f,
    streaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return
    RichTextDocument(
        text = text,
        textScale = textScale,
        streaming = streaming,
        modifier = modifier
    )
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
