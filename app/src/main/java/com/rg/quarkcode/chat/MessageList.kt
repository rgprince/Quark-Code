package com.rg.quarkcode.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    expandedParts: Set<String>,
    modifier: Modifier = Modifier,
    onTogglePart: (String) -> Unit,
    onToggleTodo: (String, String) -> Unit,
    onAllow: (String) -> Unit,
    onDeny: (String) -> Unit,
    onRememberChange: (String, Boolean) -> Unit,
    onRetry: (String) -> Unit,
    onAnswer: (String, String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            if (message.isUser) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Card(
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 4.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                AssistantMessage(
                    message = message,
                    expandedParts = expandedParts,
                    onTogglePart = onTogglePart,
                    onToggleTodo = onToggleTodo,
                    onAllow = onAllow,
                    onDeny = onDeny,
                    onRememberChange = onRememberChange,
                    onRetry = onRetry,
                    onAnswer = onAnswer
                )
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    expandedParts: Set<String>,
    onTogglePart: (String) -> Unit,
    onToggleTodo: (String, String) -> Unit,
    onAllow: (String) -> Unit,
    onDeny: (String) -> Unit,
    onRememberChange: (String, Boolean) -> Unit,
    onRetry: (String) -> Unit,
    onAnswer: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val texts = message.parts.filterIsInstance<ChatPart.Text>()
    val reasoning = message.parts.filterIsInstance<ChatPart.Reasoning>()
    val tools = message.parts.filterIsInstance<ChatPart.Tool>()
    val patches = message.parts.filterIsInstance<ChatPart.Patch>()
    val images = message.parts.filterIsInstance<ChatPart.Image>()
    val errors = message.parts.filterIsInstance<ChatPart.Error>()
    val questions = message.parts.filterIsInstance<ChatPart.QuestionOption>()
    val activityKey = "act-${message.id}"
    val activityOpen = expandedParts.contains(activityKey)
    val hasActivity = tools.isNotEmpty() || reasoning.any { it.text.isNotBlank() }

    Column(modifier = modifier.fillMaxWidth()) {
        val body = texts.joinToString("") { it.text }
        if (body.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        if (hasActivity) {
            Spacer(modifier = Modifier.height(6.dp))
            ActivitySummaryRow(
                toolCount = tools.size,
                fileCount = tools.count { it.name in FILE_TOOL_NAMES },
                reasoningCount = reasoning.count { it.text.isNotBlank() },
                running = message.isStreaming,
                expanded = activityOpen,
                onToggle = { onTogglePart(activityKey) }
            )
        }
        if (!hasActivity || activityOpen) {
            reasoning.forEach { part ->
                if (part.text.isNotBlank() || expandedParts.contains(part.id)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ThinkingCard(
                        part = part,
                        expanded = expandedParts.contains(part.id),
                        onToggle = { onTogglePart(part.id) }
                    )
                }
            }
            tools.forEach { part ->
                Spacer(modifier = Modifier.height(6.dp))
                ToolCard(
                    part = part,
                    messageId = message.id,
                    onToggleTodo = onToggleTodo
                )
            }
            patches.forEach { part ->
                Spacer(modifier = Modifier.height(6.dp))
                PatchCard(files = part.files)
            }
        }
        images.forEach { part ->
            Spacer(modifier = Modifier.height(6.dp))
            AsyncImage(
                model = part.url,
                contentDescription = part.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            )
        }
        if (questions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            QuestionCard(
                options = questions,
                onAnswer = onAnswer
            )
        }
        errors.forEach { part ->
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = part.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { onRetry(message.id) }) {
                        Text("Retry")
                    }
                }
            }
        }
        message.permission?.let { request ->
            Spacer(modifier = Modifier.height(6.dp))
            PermissionCard(
                request = request,
                onAllow = { onAllow(request.id) },
                onDeny = { onDeny(request.id) },
                onRememberChange = { remember ->
                    onRememberChange(request.id, remember)
                }
            )
        }
    }
}

@Composable
private fun ActivitySummaryRow(
    toolCount: Int,
    fileCount: Int,
    reasoningCount: Int,
    running: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val phrases = buildList {
                if (toolCount > 0) add("Ran $toolCount")
                if (fileCount > 0) add("read $fileCount")
                if (reasoningCount > 0) add("thinking")
            }
            Text(
                text = (if (running) "Working… " else "") + phrases.joinToString(" · ")
                    .ifEmpty { "Activity" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }
    }
}

@Composable
private fun PatchCard(
    files: List<String>,
    modifier: Modifier = Modifier
) {
    if (files.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Patch · ${files.size} file(s)",
                style = MaterialTheme.typography.titleSmall
            )
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

private val FILE_TOOL_NAMES = setOf("read", "glob", "grep", "ls", "find")
