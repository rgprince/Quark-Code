package com.rg.quarkcode.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rg.quarkcode.backend.ThemeMode

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
    onProviderChange: (String) -> Unit,
    onRetryProviders: () -> Unit,
    onOpenProviderDialog: (String) -> Unit,
    onAuthKeyChange: (String) -> Unit,
    onSaveProviderKey: () -> Unit,
    onDisconnectProvider: () -> Unit,
    onCloseProviderDialog: () -> Unit,
    autoExpandReasoning: Boolean,
    onAutoExpandChange: (Boolean) -> Unit,
    detailedTools: Boolean,
    onDetailedChange: (Boolean) -> Unit,
    serverVersion: String?,
    diagnosticsText: String,
    onNewMcpNameChange: (String) -> Unit,
    onNewMcpUrlChange: (String) -> Unit,
    onAddMcp: () -> Unit,
    onToggleMcp: (String, Boolean) -> Unit,
    onOpen: () -> Unit
) {
    LaunchedEffect(Unit) { onOpen() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "providers") {
                SettingsSection(title = "Default provider", icon = Icons.Filled.Storage) {
                    if (state.providersLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Loading providers…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    state.providersError?.let { message ->
                        Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = onRetryProviders) { Text("Retry") }
                    }
                    if (!state.providersLoading && state.providersError == null && state.providers.isEmpty()) {
                        Text(
                            text = "Connect first, then pick the provider used for new chats. The model picker will only show this provider.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.providers.forEach { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProviderDialog(provider.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.selectedProviderId == provider.id,
                                onClick = { onProviderChange(provider.id) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = provider.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (provider.connected) "Connected · tap for API key" else "Not connected · tap to add key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (provider.connected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                            if (state.selectedProviderId == provider.id) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            state.authDialog?.let { dialog ->
                ProviderAuthDialog(
                    dialog = dialog,
                    onKeyChange = onAuthKeyChange,
                    onSave = onSaveProviderKey,
                    onDisconnect = onDisconnectProvider,
                    onDismiss = onCloseProviderDialog
                )
            }
            item(key = "connection") {
                SettingsSection(title = "Connection", icon = Icons.Filled.Cloud) {
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.testing) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text("Test & save")
                    }
                }
            }
            item(key = "appearance") {
                SettingsSection(title = "Appearance", icon = Icons.Filled.Palette) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                selected = state.theme == mode,
                                onClick = { onThemeChange(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }
            item(key = "chat") {
                SettingsSection(title = "Chat", icon = Icons.Filled.Chat) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Auto-expand thinking", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Open reasoning cards without tapping",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoExpandReasoning, onCheckedChange = onAutoExpandChange)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Detailed tool cards", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Expand tool input/output by default",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = detailedTools, onCheckedChange = onDetailedChange)
                    }
                }
            }
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
                                Text(text = name, style = MaterialTheme.typography.bodyLarge)
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add server")
                    }
                }
            }
            item(key = "about") {
                val clipboard = LocalClipboardManager.current
                SettingsSection(title = "Server & diagnostics", icon = Icons.Filled.Dns) {
                    Text(
                        text = "Server version: ${serverVersion ?: "unknown — Test & save first"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(diagnosticsText)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy diagnostics")
                    }
                }
            }
            item(key = "about-app") {
                SettingsSection(title = "About", icon = Icons.Filled.Info) {
                    Text(
                        text = "Quark Code — native opencode client. Backend: opencode web/serve on-device or remote. MCP over Streamable HTTP only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ProviderAuthDialog(
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
