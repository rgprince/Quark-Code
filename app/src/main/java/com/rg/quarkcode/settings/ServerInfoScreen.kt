package com.rg.quarkcode.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Server inspector (and-code ServerInfo parity): version, raw config view +
// edit/save, providers, commands, skills. No server-log viewer exists
// upstream — there is no log endpoint to wire.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerInfoScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit
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
                title = { Text("Server info") }
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
            item(key = "version") {
                InfoCard(title = "Version") {
                    Text(
                        text = state.serverVersion ?: "unknown — open Settings → Test & save first",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item(key = "config") {
                InfoCard(title = "Config") {
                    if (state.infoLoading && state.serverConfigJson == null) {
                        CircularProgressIndicator()
                    } else if (state.configEditing) {
                        OutlinedTextField(
                            value = state.configDraft,
                            onValueChange = onDraftChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 420.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        state.configError?.let { message ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onSave,
                                enabled = !state.configSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save")
                            }
                            OutlinedButton(
                                onClick = onCancelEdit,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                        }
                    } else {
                        Text(
                            text = state.serverConfigJson ?: "No config loaded.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onStartEdit,
                            enabled = state.serverConfigJson != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit")
                        }
                    }
                }
            }
            item(key = "providers-header") {
                Text(
                    text = "Providers (${state.infoProviders.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(state.infoProviders, key = { it.id }) { provider ->
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            item(key = "commands-header") {
                Text(
                    text = "Commands (${state.infoCommands.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(state.infoCommands, key = { it.name }) { command ->
                Column {
                    Text(text = "/${command.name}", style = MaterialTheme.typography.bodyMedium)
                    command.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item(key = "skills-header") {
                Text(
                    text = "Skills (${state.infoSkills.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(state.infoSkills, key = { it.name }) { skill ->
                Column {
                    Text(text = skill.name, style = MaterialTheme.typography.bodyMedium)
                    skill.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
