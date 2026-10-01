package com.rg.quarkcode.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

// Flat compact todo box: one single card, all items inside as tight rows
// with a tiny check dot (no 48dp checkboxes eating the chat window).
@Composable
fun TodoCard(
    todos: List<TodoItem>,
    modifier: Modifier = Modifier,
    onToggle: ((String) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    if (todos.isEmpty()) return
    // Keyed by content identity: unkeyed remember{} reset the collapse state
    // on every streamed todo delta (key churn reads as flicker).
    var collapsed by remember(todos.map { it.id }) { mutableStateOf(false) }
    val done = todos.count { it.done }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Tasks $done of ${todos.size} done" },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$done/${todos.size} tasks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { collapsed = !collapsed }
                )
                IconButton(
                    onClick = { collapsed = !collapsed },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                        contentDescription = if (collapsed) "Expand tasks" else "Collapse tasks",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss tasks",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
            if (!collapsed) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    todos.forEach { todo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = onToggle != null) { onToggle?.invoke(todo.id) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(17.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (todo.done) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    )
                                    .semantics {
                                        contentDescription = if (todo.done) "Done" else "Not done"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (todo.done) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Text(
                                text = todo.text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDecoration = if (todo.done) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    }
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
