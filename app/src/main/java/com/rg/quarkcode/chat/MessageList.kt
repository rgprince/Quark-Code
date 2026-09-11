package com.rg.quarkcode.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
    modifier: Modifier = Modifier,
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
    LaunchedEffect(timeline.size, thinking) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (last - visible <= 2) {
            listState.animateScrollToItem(last)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(timeline, key = { it.id }) { entry ->
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
                    contentDescription = entry.part.filename,
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
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = entry.part.message, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onRetry(entry.part.id) }) {
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
                        if (open) {
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
                is TimelineEntry.Todo -> TodoCard(
                    todos = entry.todos,
                    onToggle = null,
                    modifier = Modifier.animateItem()
                )
                is TimelineEntry.Footer -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = formatTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Live status lives at the transcript tail (AndCode parity), never above the composer.
        if (thinking) {
            item(key = "thinking-tail") {
                ThinkingTail(modifier = Modifier.animateItem())
            }
        }
        // Permissions + questions render after timeline (above tail, like AndCode).
        items(messages.mapNotNull { it.permission }, key = { "perm:${it.id}" }) { request ->
            Spacer(modifier = Modifier.height(6.dp))
            PermissionCard(
                request = request,
                onAllow = { onAllow(request.id) },
                onDeny = { onDeny(request.id) },
                onRememberChange = { remember -> onRememberChange(request.id, remember) }
            )
        }
        items(
            messages.flatMap { m -> m.parts.filterIsInstance<ChatPart.QuestionOption>().map { m.id to it } },
            key = { (_, opt) -> opt.id }
        ) { (_, opt) ->
            Spacer(modifier = Modifier.height(6.dp))
            QuestionCard(options = listOf(opt), onAnswer = onAnswer)
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

// Pulsing tail indicator (opencode-android typing-dot idea, Quark tokens).
@Composable
private fun ThinkingTail(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkingAlpha"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .alpha(alpha)
            )
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
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (message.timestamp > 0L) {
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp, end = 6.dp)
                )
            }
            Surface(
                modifier = Modifier.widthIn(max = 340.dp),
                shape = RoundedCornerShape(
                    topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 5.dp
                ),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = hideToolCallEcho(body), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun AssistantBody(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Column(modifier = modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.animateContentSize()
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Patch · ${files.size} file(s)", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            files.forEach { file ->
                Text(
                    text = file,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
