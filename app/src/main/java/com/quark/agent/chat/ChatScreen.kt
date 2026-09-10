package com.quark.agent.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(
    state: ChatUiState,
    modifier: Modifier = Modifier,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAllow: (String) -> Unit,
    onDeny: (String) -> Unit,
    onRememberChange: (String, Boolean) -> Unit,
    onToggleTodo: (String, String) -> Unit,
    onToggleTools: (String) -> Unit,
    onModelSheet: (Boolean) -> Unit,
    onSpacesSheet: (Boolean) -> Unit,
    onContextSheet: (Boolean) -> Unit,
    onRuntimeChange: (Runtime) -> Unit,
    onModelChange: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onAgentSelect: (String) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            QuarkTopBar(
                project = state.project,
                usedFraction = ringFraction(state.stats),
                usedLabel = ringLabel(state.stats),
                statusOk = true,
                onSpaces = { onSpacesSheet(true) },
                onTokenClick = { onContextSheet(true) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
        ) {
            if (state.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Say hi to start a native session",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                MessageList(
                    messages = state.messages,
                    modifier = Modifier.weight(1f),
                    onToggleTools = onToggleTools,
                    onToggleTodo = onToggleTodo,
                    onAllow = onAllow,
                    onDeny = onDeny,
                    onRememberChange = onRememberChange,
                    toolsExpanded = state.toolsExpanded
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OrbitComposer(
                input = state.input,
                model = state.model,
                meterLabel = meterLabel(state.stats),
                costLabel = "$" + "%.4f".format(state.stats.cost),
                sending = state.sending,
                onInputChange = onInputChange,
                onSend = onSend,
                onModelClick = { onModelSheet(true) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (state.modelSheet) {
        ModelSheet(
            runtime = state.runtime,
            models = listOf(state.model),
            selected = state.model,
            favorites = state.favorites,
            onRuntimeChange = onRuntimeChange,
            onModelChange = onModelChange,
            onFavoriteToggle = onFavoriteToggle,
            onDismiss = { onModelSheet(false) }
        )
    }
    if (state.spacesSheet) {
        SpacesSheet(
            agent = state.agent,
            agents = listOf("Native opencode", "Remote server"),
            projects = listOf(state.project),
            recents = emptyList(),
            onAgentSelect = onAgentSelect,
            onDismiss = { onSpacesSheet(false) }
        )
    }
    if (state.contextSheet) {
        ContextSheet(
            stats = state.stats,
            model = state.model,
            onDismiss = { onContextSheet(false) },
            onExport = { onContextSheet(false) }
        )
    }
}

private fun ringFraction(stats: ContextStats): Float {
    if (stats.limit <= 0L) return 0f
    return (stats.used.toFloat() / stats.limit.toFloat()).coerceIn(0f, 1f)
}

private fun ringLabel(stats: ContextStats): String {
    if (stats.limit <= 0L) return "—"
    return "${(stats.used * 100) / stats.limit}"
}

private fun meterLabel(stats: ContextStats): String {
    return "${shortCount(stats.used)} / ${shortCount(stats.limit)}"
}

private fun shortCount(value: Long): String = when {
    value >= 1_000_000L -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000L -> "%.1fk".format(value / 1_000.0)
    else -> value.toString()
}
