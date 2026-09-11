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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Providers submenu: loads lazily on open so Settings landing stays instant.
// Tap a row for API-key dialog; radio sets the default provider.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onProviderChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenDialog: (String) -> Unit,
    onAuthKeyChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onDisconnect: () -> Unit,
    onCloseDialog: () -> Unit
) {
    LaunchedEffect(Unit) { onOpen() }
    var query by remember { mutableStateOf("") }
    val visible = remember(state.providers, query) {
        if (query.isBlank()) state.providers
        else state.providers.filter {
            it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Providers") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search providers") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (state.providersLoading) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Loading providers…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.providersError?.let { message ->
                item(key = "error") {
                    Column {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
            if (!state.providersLoading && state.providersError == null && visible.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No providers found. Test the connection first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(visible, key = { it.id }) { provider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDialog(provider.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.selectedProviderId == provider.id,
                        onClick = { onProviderChange(provider.id) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = provider.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (provider.connected) "Connected · tap for API key"
                            else "Not connected · tap to add key",
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
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
        state.authDialog?.let { dialog ->
            ProviderAuthDialog(
                dialog = dialog,
                onKeyChange = onAuthKeyChange,
                onSave = onSaveKey,
                onDisconnect = onDisconnect,
                onDismiss = onCloseDialog
            )
        }
    }
}
