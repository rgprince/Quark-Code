package com.rg.quarkcode.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Collapsed activity row (AndCode IA, Quark Void/Paper tokens).
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
    val accent = when {
        hasError -> MaterialTheme.colorScheme.error
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSheet),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (hasError) Icons.Filled.ErrorOutline else Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = (if (running) "Working… " else "") + summarizeActivity(parts),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand"
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
    onToggleTodo: (String, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    ModalBottomSheet(modifier = modifier, onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summarizeActivity(parts),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(parts.filter { (it as? ChatPart.Reasoning)?.text?.isNotBlank() != false }, key = { it.id }) { part ->
                when (part) {
                    is ChatPart.Reasoning -> ReasoningCard(part = part, autoExpand = autoExpandReasoning, expanded = null, onToggle = null)
                    is ChatPart.Tool -> QuarkToolCard(part = part, messageId = messageId, onToggleTodo = onToggleTodo)
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (onToggle != null) onToggle() else internal = !internal
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isOpen) "Collapse" else "Expand"
                )
            }
            if (isOpen) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = part.text.ifBlank { "No reasoning captured." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
    onToggleTodo: (String, String) -> Unit = { _, _ -> }
) {
    var expanded by remember { mutableStateOf(part.status == ToolStatus.RUNNING || part.status == ToolStatus.PENDING) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = part.name.toolCategoryIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = part.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                part.title?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } ?: Spacer(modifier = Modifier.weight(1f))
                ToolStatusChip(status = part.status)
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp)
                )
            }
            if (expanded) {
                part.input?.takeIf { it.isNotBlank() }?.let { input ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "input", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = input,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
                part.output?.takeIf { it.isNotBlank() }?.let { output ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "output", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = (if (part.outputTruncated) "…(truncated)\n" else "") + output,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    )
                }
                part.error?.takeIf { it.isNotBlank() }?.let { error ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (part.todos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = status.name.lowercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = "Patch · ${files.size} file(s)", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            files.forEach { file ->
                Text(text = file, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
