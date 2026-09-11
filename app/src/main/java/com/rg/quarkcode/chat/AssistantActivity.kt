package com.rg.quarkcode.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Expressive activity row: tonal card, split affordances — chevron expands
// inline, "Details" opens the sheet. Never one ambiguous tap target.
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
        shape = RoundedCornerShape(16.dp),
        color = container,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .semantics { contentDescription = "Working" },
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (hasError) Icons.Filled.ErrorOutline else Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (hasError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = (if (running) "Working… " else "") + summarizeActivity(parts),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse activity inline" else "Expand activity inline",
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = onOpenSheet,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInFull,
                    contentDescription = "Open activity details",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantActivitySheet(
    parts: List<ChatPart>,
    messageId: String,
    modifier: Modifier = Modifier,
    autoExpandReasoning: Boolean = false,
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
            items(parts.filter { (it as? ChatPart.Reasoning)?.text?.isNotBlank() != false }, key = { it.id }) { part ->
                when (part) {
                    is ChatPart.Reasoning -> ReasoningCard(part = part, autoExpand = autoExpandReasoning, expanded = null, onToggle = null)
                    is ChatPart.Tool -> QuarkToolCard(part = part, messageId = messageId, detailed = detailedTools, onToggleTodo = onToggleTodo)
                    is ChatPart.Patch -> PatchSheetCard(files = part.files)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun ReasoningCard(
    part: ChatPart.Reasoning,
    modifier: Modifier = Modifier,
    autoExpand: Boolean = false,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null
) {
    if (part.text.isBlank() && expanded == false) return
    var internal by remember { mutableStateOf(autoExpand) }
    val isOpen = expanded ?: internal
    val tokens = remember(part.text) { "${part.text.length / 4} tokens" }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking · $tokens",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { if (onToggle != null) onToggle() else internal = !internal },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isOpen) "Collapse reasoning" else "Expand reasoning",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (isOpen) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = part.text.ifBlank { "No reasoning captured." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = part.text.lineSequence().firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
    var expanded by remember { mutableStateOf(part.status == ToolStatus.RUNNING || part.status == ToolStatus.PENDING || detailed) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = part.name.toolCategoryIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = part.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    part.title?.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ToolStatusChip(status = part.status)
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse tool" else "Expand tool",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (expanded) {
                part.input?.takeIf { it.isNotBlank() }?.let { input ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "input", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = input,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
                part.output?.takeIf { it.isNotBlank() }?.let { output ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "output", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = (if (part.outputTruncated) "…(truncated)\n" else "") + output,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                part.error?.takeIf { it.isNotBlank() }?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (part.todos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TodoCard(todos = part.todos, onToggle = { todoId -> onToggleTodo(messageId, todoId) })
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
    val container = when (status) {
        ToolStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
        ToolStatus.RUNNING, ToolStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
        ToolStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        ToolStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = modifier.semantics {
            contentDescription = "Tool ${status.name.lowercase()}"
        },
        shape = RoundedCornerShape(100.dp),
        color = container
    ) {
        Text(
            text = status.name.lowercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Patch · ${files.size} file(s)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            files.forEach { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
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
