package com.rg.quarkcode.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

// Webview todo overlay as an inline Kai-style card with progress dots.
@Composable
fun TodoCard(
    todos: List<TodoItem>,
    modifier: Modifier = Modifier,
    onToggle: ((String) -> Unit)? = null
) {
    if (todos.isEmpty()) return
    val done = todos.count { it.done }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Tasks $done/${todos.size}",
                style = MaterialTheme.typography.titleSmall
            )
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
