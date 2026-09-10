package com.rg.quarkcode.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            item(key = "connection") {
                SettingsSection(title = "Connection") {
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
                SettingsSection(title = "Appearance") {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.theme == mode,
                                onClick = { onThemeChange(mode) }
                            )
                            Text(
                                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            item(key = "mcp") {
                SettingsSection(title = "MCP servers (HTTP)") {
                    if (state.mcpLoading) {
                        CircularProgressIndicator()
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
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = status.error ?: status.status ?: "unknown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val connected = status.error == null
                            OutlinedButton(
                                onClick = { onToggleMcp(name, !connected) }
                            ) {
                                Text(if (connected) "Stop" else "Start")
                            }
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
                SettingsSection(title = "About") {
                    Text(
                        text = "Quark Code — native opencode client. Backend: opencode web/serve on-device or remote. MCP over Streamable HTTP only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
