package com.rg.quarkcode.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

// Plain compact todo box: small surface, small text, no flash.
@Composable
fun TodoCard(
    todos: List<TodoItem>,
    modifier: Modifier = Modifier,
    onToggle: ((String) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    if (todos.isEmpty()) return
    var collapsed by remember { mutableStateOf(false) }
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
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$done/${todos.size} tasks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { collapsed = !collapsed }
                )
                IconButton(
                    onClick = { collapsed = !collapsed },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                        contentDescription = if (collapsed) "Expand tasks" else "Collapse tasks",
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss tasks",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (!collapsed) {
                todos.forEach { todo ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = todo.done,
                            enabled = onToggle != null,
                            onCheckedChange = { onToggle?.invoke(todo.id) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = todo.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = if (todo.done) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
