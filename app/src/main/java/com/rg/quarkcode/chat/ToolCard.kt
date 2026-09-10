package com.rg.quarkcode.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Full tool card: status dot, name + title, input/output, error, todos.
@Composable
fun ToolCard(
    part: ChatPart.Tool,
    messageId: String,
    modifier: Modifier = Modifier,
    onToggleTodo: (String, String) -> Unit
) {
    var outputOpen by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = statusColor(part.status)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = part.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    part.title?.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = part.status.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(part.status)
                )
            }
            part.input?.takeIf { it.isNotBlank() }?.let { input ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = input,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (outputOpen) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            part.error?.takeIf { it.isNotBlank() }?.let { error ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (part.output != null || part.todos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = { outputOpen = !outputOpen },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = if (outputOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (outputOpen) "Collapse" else "Expand"
                    )
                }
            }
            if (outputOpen) {
                part.output?.takeIf { it.isNotBlank() }?.let { output ->
                    Text(
                        text = (if (part.outputTruncated) "…(truncated)\n" else "") + output,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (part.todos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TodoCard(
                        todos = part.todos,
                        onToggle = { todoId -> onToggleTodo(messageId, todoId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: ToolStatus) = when (status) {
    ToolStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    ToolStatus.RUNNING, ToolStatus.PENDING -> MaterialTheme.colorScheme.tertiary
    ToolStatus.ERROR -> MaterialTheme.colorScheme.error
    ToolStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
