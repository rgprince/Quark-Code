package com.rg.quarkcode.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

// Expressive session todo strip: wavy-ish linear progress, 48dp targets,
// collapsible + dismissible so it never pins screen space.
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
    val fraction = if (todos.isEmpty()) 0f else done.toFloat() / todos.size.toFloat()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Tasks $done of ${todos.size} done" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tasks $done/${todos.size}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { collapsed = !collapsed }
                    )
                    IconButton(
                        onClick = { collapsed = !collapsed },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                            contentDescription = if (collapsed) "Expand tasks" else "Collapse tasks",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (onDismiss != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss tasks",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                if (!collapsed) {
                    todos.forEach { todo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = todo.done,
                                enabled = onToggle != null,
                                onCheckedChange = { onToggle?.invoke(todo.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = todo.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (todo.done) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
