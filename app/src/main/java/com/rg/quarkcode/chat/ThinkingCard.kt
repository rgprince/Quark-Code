package com.rg.quarkcode.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Backward-compat wrapper: timeline now uses ReasoningCard in AssistantActivity.kt.
@Composable
fun ThinkingCard(
    part: ChatPart.Reasoning,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    ReasoningCard(part = part, modifier = modifier, expanded = expanded, onToggle = onToggle)
}
