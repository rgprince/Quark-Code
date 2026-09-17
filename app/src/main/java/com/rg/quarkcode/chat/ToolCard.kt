package com.rg.quarkcode.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Backward-compat wrapper: timeline now uses QuarkToolCard in AssistantActivity.kt.
@Composable
fun ToolCard(
    part: ChatPart.Tool,
    messageId: String,
    modifier: Modifier = Modifier,
    onToggleTodo: (String, String) -> Unit
) {
    QuarkToolCard(part = part, messageId = messageId, modifier = modifier, onToggleTodo = onToggleTodo)
}
