package com.rg.quarkcode.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
    onTogglePart: (String) -> Unit,
    onModelSheet: (Boolean) -> Unit,
    onSpacesSheet: (Boolean) -> Unit,
    onContextSheet: (Boolean) -> Unit,
    onRuntimeChange: (Runtime) -> Unit,
    onModelChange: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onAgentSelect: (String) -> Unit,
    onRetry: (String) -> Unit,
    onAbort: () -> Unit,
    onOpenSession: (String) -> Unit,
    onRetryCatalog: () -> Unit,
    onAnswer: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onMenu: () -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            QuarkTopBar(
                project = state.project,
                usedFraction = ringFraction(state.stats),
                usedLabel = ringLabel(state.stats),
                statusOk = state.connected,
                onMenu = onMenu,
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
                if (state.sessionTodos.isNotEmpty()) {
                    TodoCard(
                        todos = state.sessionTodos,
                        onToggle = null
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                MessageList(
                    messages = state.messages,
                    expandedParts = state.expandedParts,
                    modifier = Modifier.weight(1f),
                    onTogglePart = onTogglePart,
                    onToggleTodo = onToggleTodo,
                    onAllow = onAllow,
                    onDeny = onDeny,
                    onRememberChange = onRememberChange,
                    onRetry = onRetry,
                    onAnswer = onAnswer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (state.thinking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thinking…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OrbitComposer(
                input = state.input,
                model = state.model,
                meterLabel = meterLabel(state.stats),
                costLabel = "$" + "%.4f".format(state.stats.cost),
                sending = state.sending,
                onInputChange = onInputChange,
                onSend = onSend,
                onAbort = onAbort,
                onModelClick = { onModelSheet(true) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (state.modelSheet) {
        ModelSheet(
            runtime = state.runtime,
            models = state.catalog,
            selectedId = state.selectedModelKey,
            selectedProviderId = state.selectedProviderId,
            providerName = state.providers.firstOrNull { it.id == state.selectedProviderId }?.name
                ?: state.selectedProviderId ?: "Auto",
            favorites = state.favorites,
            recents = state.modelRecents,
            catalogLoading = state.catalogLoading,
            catalogError = state.catalogError,
            onRuntimeChange = onRuntimeChange,
            onModelChange = onModelChange,
            onFavoriteToggle = onFavoriteToggle,
            onRetryCatalog = onRetryCatalog,
            onOpenProviderSettings = {
                onModelSheet(false)
                onOpenSettings()
            },
            onDismiss = { onModelSheet(false) }
        )
    }
    if (state.spacesSheet) {
        SpacesSheet(
            agent = state.agent,
            agents = listOf("Native opencode", "Remote server"),
            projects = listOf(state.project),
            recents = state.recents,
            onAgentSelect = onAgentSelect,
            onRecentSelect = onOpenSession,
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
    val pct = ((stats.used.coerceAtLeast(0L) * 100) / stats.limit).coerceAtMost(999L)
    return "$pct%"
}

private fun meterLabel(stats: ContextStats): String {
    return "${shortCount(stats.used)} / ${shortCount(stats.limit)}"
}

private fun shortCount(value: Long): String = when {
    value >= 1_000_000L -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000L -> "%.1fk".format(value / 1_000.0)
    else -> value.toString()
}
