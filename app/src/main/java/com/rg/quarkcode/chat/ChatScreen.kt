package com.rg.quarkcode.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
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
    onHiddenToggle: (String) -> Unit,
    onAgentSelect: (String) -> Unit,
    onRetry: (String) -> Unit,
    onAbort: () -> Unit,
    onOpenSession: (String) -> Unit,
    onRetryCatalog: () -> Unit,
    onAnswer: (String, String) -> Unit,
    onModeChange: (String) -> Unit,
    onVariantChange: (String?) -> Unit,
    onSlashSelect: (SlashSuggestion) -> Unit,
    onAtSelect: (AtFile) -> Unit,
    onVoiceResult: (String) -> Unit,
    onSpeak: (String, String) -> Unit,
    onDismissTodos: () -> Unit,
    onOpenSettings: () -> Unit,
    onMenu: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val recognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { onVoiceResult(it) }
        }
    }
    fun launchRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        runCatching { recognizerLauncher.launch(intent) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchRecognizer()
    }
    // Auto-readout: speak the newest assistant text once the turn completes.
    val lastAssistantText = remember(state.messages) {
        state.messages.lastOrNull { !it.isUser }?.text.orEmpty()
    }
    LaunchedEffect(lastAssistantText, state.autoSpeak, state.sending) {
        if (state.autoSpeak && !state.sending && lastAssistantText.isNotBlank()) {
            onSpeak("auto", lastAssistantText)
        }
    }
    // Busy = sending flags OR live work the flags can lag behind (streaming
    // deltas, running tools). The stop button and progress follow this, so a
    // stale idle event can never flip us back to send-arrow mid-reply.
    val busy = state.sending || state.thinking ||
        state.messages.any { message ->
            message.isStreaming || message.parts.any { part ->
                part is ChatPart.Tool &&
                    (part.status == ToolStatus.RUNNING || part.status == ToolStatus.PENDING)
            }
        }
    // Think timer: how long the last thinking phase took, shown as a tiny
    // "Thought for 2.3s" row once it ends. Purely UI-local, no backend cost.
    var thinkStart by remember { mutableStateOf<Long?>(null) }
    var lastThoughtMs by remember { mutableStateOf<Long?>(null) }
    var thoughtExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.thinking) {
        if (state.thinking) {
            thinkStart = System.currentTimeMillis()
            lastThoughtMs = null
            thoughtExpanded = false
        } else {
            thinkStart?.let {
                lastThoughtMs = System.currentTimeMillis() - it
                thinkStart = null
            }
        }
    }
    val lastThoughtText = remember(state.messages) {
        state.messages.flatMap { it.parts }.filterIsInstance<ChatPart.Reasoning>()
            .lastOrNull { it.text.isNotBlank() }?.text?.takeLast(600).orEmpty()
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            QuarkTopBar(
                project = state.project,
                usedFraction = ringFraction(state.stats),
                usedLabel = ringLabel(state.stats),
                statusOk = state.connected,
                scrollBehavior = scrollBehavior,
                onMenu = onMenu,
                onSpaces = onMenu,
                onTokenClick = { onContextSheet(true) }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.messages.isEmpty()) {
                QuarkEmptyState(
                    modifier = Modifier.weight(1f),
                    onSuggestion = { onInputChange(it) }
                )
            } else {
                if (state.sessionTodos.isNotEmpty() && state.todosVisible) {
                    TodoCard(
                        todos = state.sessionTodos,
                        onToggle = null,
                        onDismiss = onDismissTodos,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                MessageList(
                    messages = state.messages,
                    expandedParts = state.expandedParts,
                    thinking = state.thinking,
                    autoExpandReasoning = state.autoExpandReasoning,
                    speakingId = state.speakingId,
                    thoughtMs = lastThoughtMs,
                    thoughtText = lastThoughtText,
                    thoughtExpanded = thoughtExpanded,
                    onToggleThought = { thoughtExpanded = !thoughtExpanded },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f),
                    onTogglePart = onTogglePart,
                    onToggleTodo = onToggleTodo,
                    onAllow = onAllow,
                    onDeny = onDeny,
                    onRememberChange = onRememberChange,
                    onRetry = onRetry,
                    onAnswer = onAnswer,
                    onSpeak = onSpeak
                )
            }
            OrbitComposer(
                input = state.input,
                model = state.model,
                meterLabel = meterLabel(state.stats),
                meterFraction = ringFraction(state.stats),
                sending = busy,
                queuedCount = state.queuedCount,
                modes = state.modes,
                mode = state.mode,
                variants = state.variants,
                selectedVariant = state.selectedVariant,
                slashSuggestions = if (state.input.startsWith("/")) {
                    state.slashCommands.filter {
                        it.name.startsWith(state.input.trim(), ignoreCase = true)
                    }
                } else {
                    emptyList()
                },
                atSuggestions = state.atSuggestions,
                onInputChange = onInputChange,
                onSend = onSend,
                onAbort = onAbort,
                onMicClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        launchRecognizer()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onModelClick = { onModelSheet(true) },
                onModeChange = onModeChange,
                onVariantChange = onVariantChange,
                onSlashSelect = onSlashSelect,
                onAtSelect = onAtSelect,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
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
            hiddenModels = state.hiddenModels,
            catalogLoading = state.catalogLoading,
            catalogError = state.catalogError,
            onRuntimeChange = onRuntimeChange,
            onModelChange = onModelChange,
            onFavoriteToggle = onFavoriteToggle,
            onHiddenToggle = onHiddenToggle,
            onRetryCatalog = onRetryCatalog,
            onOpenProviderSettings = {
                onModelSheet(false)
                onOpenSettings()
            },
            onDismiss = { onModelSheet(false) }
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

@Composable
private fun QuarkEmptyState(
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = "What should we build?",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Native session · pick a model, / for commands, @ for files",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onSuggestion("/help ") },
                    label = { Text("/help — what can you do?") }
                )
                AssistChip(
                    onClick = { onSuggestion("Explain this repo ") },
                    label = { Text("Explain this repo") }
                )
                AssistChip(
                    onClick = { onSuggestion("Review my changes ") },
                    label = { Text("Review my changes") }
                )
            }
            FilledTonalButton(onClick = { onSuggestion("Say hi, start a native session ") }) {
                Text("Start chatting")
            }
        }
    }
}

private fun ringFraction(stats: ContextStats): Float {
    if (stats.limit <= 0L) return 0f
    return (stats.used.toFloat() / stats.limit.toFloat()).coerceIn(0f, 1f)
}

private fun ringLabel(stats: ContextStats): String {
    if (stats.limit <= 0L) return "—"
    val pct = ((stats.used.coerceAtLeast(0L) * 100) / stats.limit).coerceAtMost(100L)
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
