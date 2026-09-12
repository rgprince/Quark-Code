package com.rg.quarkcode.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Slim activity row: 10dp radius, single line, compact affordances.
@Composable
fun AssistantActivityRow(
    parts: List<ChatPart>,
    running: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onOpenSheet: () -> Unit
) {
    val hasError = parts.filterIsInstance<ChatPart.Tool>().any { it.status == ToolStatus.ERROR }
    val container = when {
        hasError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = container,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 10.dp, end = 2.dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(15.dp)
                        .semantics { contentDescription = "Working" },
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (hasError) Icons.Filled.ErrorOutline else Icons.Filled.Terminal,
                    contentDescription = null,
                    tint = if (hasError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = if (running) "Working…" else plainActivityLabel(parts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse activity inline" else "Expand activity inline",
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(
                onClick = onOpenSheet,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInFull,
                    contentDescription = "Open activity details",
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// Plain-words label for the collapsed row: "3 tool calls", "Changed 2 files".
// (The jargon-y "Ran 3 · read 2 · edit 1" summary still titles the sheet.)
private fun plainActivityLabel(parts: List<ChatPart>): String {
    val tools = parts.filterIsInstance<ChatPart.Tool>()
    val patches = parts.filterIsInstance<ChatPart.Patch>().size
    if (tools.isEmpty() && patches > 0) {
        return if (patches == 1) "Changed 1 file" else "Changed $patches files"
    }
    return if (tools.size == 1) "1 tool call" else "${tools.size} tool calls"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantActivitySheet(
    parts: List<ChatPart>,
    messageId: String,
    modifier: Modifier = Modifier,
    detailedTools: Boolean = false,
    onToggleTodo: (String, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    ModalBottomSheet(modifier = modifier, onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summarizeActivity(parts),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                parts.filter { it is ChatPart.Tool || it is ChatPart.Patch },
                key = { it.id }
            ) { part ->
                when (part) {
                    is ChatPart.Tool -> QuarkToolCard(part = part, messageId = messageId, detailed = detailedTools, onToggleTodo = onToggleTodo)
                    is ChatPart.Patch -> PatchSheetCard(files = part.files)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun QuarkToolCard(
    part: ChatPart.Tool,
    messageId: String,
    modifier: Modifier = Modifier,
    detailed: Boolean = false,
    onToggleTodo: (String, String) -> Unit = { _, _ -> }
) {
    val isSubagent = part.name.equals("task", ignoreCase = true)
    val displayName = if (isSubagent) "subagent" else part.name
    val summary = remember(part) { prettyToolSummary(part) }
    var expanded by remember {
        mutableStateOf(
            part.status == ToolStatus.RUNNING ||
                part.status == ToolStatus.PENDING ||
                part.status == ToolStatus.ERROR ||
                detailed
        )
    }
    val statusColor = when (part.status) {
        ToolStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        ToolStatus.RUNNING, ToolStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        ToolStatus.ERROR -> MaterialTheme.colorScheme.error
        ToolStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .semantics {
                            contentDescription = "Tool ${part.status.name.lowercase()}"
                        }
                )
                Icon(
                    imageVector = part.name.toolCategoryIcon(),
                    contentDescription = null,
                    tint = part.name.toolCategoryTint(),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse tool" else "Expand tool",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (expanded) {
                part.output?.takeIf { it.isNotBlank() }?.let { output ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (if (part.outputTruncated) "…(truncated)\n" else "") + output,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                part.error?.takeIf { it.isNotBlank() }?.let { error ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (part.todos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TodoCard(todos = part.todos, onToggle = { todoId -> onToggleTodo(messageId, todoId) })
                }
                if (part.output.isNullOrBlank() && part.error.isNullOrBlank() && part.todos.isEmpty()) {
                    part.input?.takeIf { it.isNotBlank() }?.let { input ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = input.take(300),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolStatusChip(status: ToolStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        ToolStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        ToolStatus.RUNNING, ToolStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        ToolStatus.ERROR -> MaterialTheme.colorScheme.error
        ToolStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Tool ${status.name.lowercase()}"
        },
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = status.name.lowercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun String.toolCategoryTint(): Color = when (toToolCategory()) {
    ToolCategory.COMMAND -> MaterialTheme.colorScheme.tertiary
    ToolCategory.READ -> MaterialTheme.colorScheme.primary
    ToolCategory.EDIT -> MaterialTheme.colorScheme.secondary
    ToolCategory.SUBAGENT -> MaterialTheme.colorScheme.secondary
    ToolCategory.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun String.toolCategoryIcon(): ImageVector = when (toToolCategory()) {
    ToolCategory.COMMAND -> Icons.Filled.Terminal
    ToolCategory.READ -> Icons.Filled.Visibility
    ToolCategory.EDIT -> Icons.Filled.Description
    ToolCategory.SUBAGENT -> Icons.Filled.Hub
    ToolCategory.OTHER -> Icons.Filled.Build
}

@Composable
private fun PatchSheetCard(files: List<String>, modifier: Modifier = Modifier) {
    if (files.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
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
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}
