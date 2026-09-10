package com.quark.agent.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onToggleTools: (String) -> Unit,
    onToggleTodo: (String, String) -> Unit,
    onAllow: (String) -> Unit,
    onDeny: (String) -> Unit,
    onRememberChange: (String, Boolean) -> Unit,
    onRetry: (String) -> Unit,
    toolsExpanded: Map<String, Boolean>
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (message.isError) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = { onRetry(message.id) }) {
                                    Text("Retry")
                                }
                            }
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    if (message.toolRuns > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        ToolPulseCard(
                            runs = message.toolRuns,
                            files = message.filesRead,
                            log = message.toolLog,
                            expanded = toolsExpanded[message.id] == true,
                            onToggle = { onToggleTools(message.id) }
                        )
                    }
                    if (message.todos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TodoCard(
                            todos = message.todos,
                            onToggle = { todoId -> onToggleTodo(message.id, todoId) }
                        )
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
                    message.imageUrl?.let { url ->
                        Spacer(modifier = Modifier.height(6.dp))
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }
            }
        }
    }
}
