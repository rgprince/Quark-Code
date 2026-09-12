package com.rg.quarkcode.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rg.quarkcode.backend.ThemeMode

private enum class SettingsTab(val label: String) {
    CONNECTION("Connection"),
    DEVICE("Device"),
    APPEARANCE("Look"),
    CHAT("Chat"),
    MCP("MCP"),
    STATS("Stats")
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.DARK -> "Void"
    ThemeMode.LIGHT -> "Paper"
    ThemeMode.DYNAMIC -> "Dynamic"
    ThemeMode.AMOLED -> "AMOLED"
}

// Tabbed settings: one concern per tab instead of an endless scroll.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onHostChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTestAndSave: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    providerSummary: String,
    onOpenProviders: () -> Unit,
    detailedTools: Boolean,
    onDetailedChange: (Boolean) -> Unit,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    comfortable: Boolean,
    onComfortableChange: (Boolean) -> Unit,
    showTimestamps: Boolean,
    onShowTimestampsChange: (Boolean) -> Unit,
    sendBehavior: String,
    onSendBehaviorChange: (String) -> Unit,
    autoSpeak: Boolean,
    onAutoSpeakChange: (Boolean) -> Unit,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    playfulStatus: Boolean,
    onPlayfulChange: (Boolean) -> Unit,
    showThoughts: Boolean,
    onShowThoughtsChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onOpenUsage: () -> Unit,
    onNewMcpNameChange: (String) -> Unit,
    onNewMcpUrlChange: (String) -> Unit,
    onAddMcp: () -> Unit,
    onToggleMcp: (String, Boolean) -> Unit,
    onOpen: () -> Unit
) {
    LaunchedEffect(Unit) { onOpen() }
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text("Settings") }
                )
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    edgePadding = 16.dp
                ) {
                    SettingsTab.entries.forEachIndexed { index, entry ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(entry.label) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "top-space") { Spacer(modifier = Modifier.height(4.dp)) }
            when (SettingsTab.entries[tab]) {
                SettingsTab.CONNECTION -> {
                    item(key = "providers") {
                        SettingsSection(title = "Default provider", icon = Icons.Filled.Storage) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenProviders),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Providers & API keys", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = providerSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item(key = "connection") {
                        SettingsSection(title = "Connection", icon = Icons.Filled.Cloud) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = { onHostChange("http://localhost:4096") },
                                    label = { Text("On-device") }
                                )
                                AssistChip(
                                    onClick = { onHostChange("http://192.168.1.2:4096") },
                                    label = { Text("LAN") }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.host,
                                onValueChange = onHostChange,
                                label = { Text("Server URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.username,
                                onValueChange = onUsernameChange,
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = onPasswordChange,
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            state.testResult?.let { result ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = result,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.startsWith("Connected")) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onTestAndSave,
                                enabled = !state.testing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                if (state.testing) {
                                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                }
                                Text("Test & save")
                            }
                        }
                    }
                }
                SettingsTab.DEVICE -> {
                    item(key = "device") {
                        SettingsSection(title = "On-device backend", icon = Icons.Filled.Storage) {
                            DeviceBackendPanel()
                        }
                    }
                }
                SettingsTab.APPEARANCE -> {
                    item(key = "appearance") {
                        SettingsSection(title = "Appearance", icon = Icons.Filled.Palette) {
                            Text(
                                text = "Paper is light, Void is dark, AMOLED is pure black, Dynamic follows your wallpaper (Android 12+).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = state.theme == mode,
                                        onClick = { onThemeChange(mode) },
                                        label = { Text(themeLabel(mode)) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Density",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                listOf("Compact" to false, "Comfortable" to true)
                                    .forEachIndexed { index, (label, value) ->
                                        SegmentedButton(
                                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                                            selected = comfortable == value,
                                            onClick = { onComfortableChange(value) },
                                            label = { Text(label) }
                                        )
                                    }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ChatToggle(
                                title = "Timestamps",
                                subtitle = "Show message times in chat",
                                checked = showTimestamps,
                                onCheckedChange = onShowTimestampsChange
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Text size",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                listOf("Small" to 0.85f, "Medium" to 1f, "Large" to 1.15f)
                                    .forEachIndexed { index, (label, scale) ->
                                        SegmentedButton(
                                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                                            selected = kotlin.math.abs(textScale - scale) < 0.01f,
                                            onClick = { onTextScaleChange(scale) },
                                            label = { Text(label) }
                                        )
                                    }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Live preview at this size:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = "Explain this repo",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize *
                                                textScale.coerceIn(0.8f, 1.3f)
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "The assistant answers in the same size. The chat uses it live.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize *
                                        textScale.coerceIn(0.8f, 1.3f)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                SettingsTab.CHAT -> {
                    item(key = "chat") {
                        SettingsSection(title = "Chat", icon = Icons.Filled.Chat) {
                            ChatToggle(
                                title = "Detailed tool cards",
                                subtitle = "Expand tool input/output by default",
                                checked = detailedTools,
                                onCheckedChange = onDetailedChange
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ChatToggle(
                                title = "Read replies aloud",
                                subtitle = "Auto speak newest answer (speaker icon replays)",
                                checked = autoSpeak,
                                onCheckedChange = onAutoSpeakChange
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { onSendBehaviorChange(if (sendBehavior == "queue") "interrupt" else "queue") }),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Send while busy", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = if (sendBehavior == "queue") "Queue behind the running turn"
                                        else "Interrupt the running turn",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (sendBehavior == "queue") "Queue" else "Interrupt",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            ChatToggle(
                                title = "Auto-scroll",
                                subtitle = "Follow new messages to the bottom",
                                checked = autoScroll,
                                onCheckedChange = onAutoScrollChange
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ChatToggle(
                                title = "Playful status",
                                subtitle = "Cooking… bubble while waiting for a reply",
                                checked = playfulStatus,
                                onCheckedChange = onPlayfulChange
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ChatToggle(
                                title = "Thought lines",
                                subtitle = "Show Thought · time above replies",
                                checked = showThoughts,
                                onCheckedChange = onShowThoughtsChange
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onClearCache),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Clear launch cache", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "Forget saved chats and models shown at startup",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                SettingsTab.MCP -> {
                    item(key = "mcp") {
                        SettingsSection(title = "MCP servers (HTTP)", icon = Icons.Filled.Storage) {
                            if (state.mcpLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                            state.mcpError?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (state.mcpServers.isEmpty() && !state.mcpLoading) {
                                Text(
                                    text = "No servers yet. Add a Streamable-HTTP endpoint below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            state.mcpServers.forEach { (name, status) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = status.error ?: status.status ?: "unknown",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val connected = status.error == null
                                    Switch(checked = connected, onCheckedChange = { onToggleMcp(name, it) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.newMcpName,
                                onValueChange = onNewMcpNameChange,
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.newMcpUrl,
                                onValueChange = onNewMcpUrlChange,
                                label = { Text("https://…/mcp") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onAddMcp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text("Add server")
                            }
                        }
                    }
                }
                SettingsTab.STATS -> {
                    item(key = "stats") {
                        SettingsSection(title = "Stats", icon = Icons.Filled.PieChart) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenUsage),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Usage stats", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "Tokens by week, month and model, with cost",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ChatToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ProviderAuthDialog(
    dialog: ProviderAuthDialog,
    onKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.providerName) },
        text = {
            Column {
                if (dialog.methodLabels.isNotEmpty()) {
                    Text(
                        text = "Auth: " + dialog.methodLabels.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = dialog.apiKey,
                    onValueChange = onKeyChange,
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                dialog.error?.let { message ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !dialog.saving) {
                Text("Save key")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onDisconnect, enabled = !dialog.saving) {
                    Text("Disconnect")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
