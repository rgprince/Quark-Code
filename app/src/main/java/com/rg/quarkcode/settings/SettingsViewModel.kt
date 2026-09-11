package com.rg.quarkcode.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rg.quarkcode.backend.Connection
import com.rg.quarkcode.backend.ConnectionStore
import com.rg.quarkcode.backend.Health
import com.rg.quarkcode.backend.McpStatus
import com.rg.quarkcode.backend.OpenCodeUrl
import com.rg.quarkcode.backend.ServeApi
import com.rg.quarkcode.backend.ThemeMode
import com.rg.quarkcode.backend.ThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ProviderChoice(
    val id: String = "",
    val name: String = "",
    val connected: Boolean = true
)

data class SettingsUiState(
    val host: String = "http://localhost:4096",
    val username: String = "opencode",
    val password: String = "",
    val testing: Boolean = false,
    val testResult: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val providers: List<ProviderChoice> = emptyList(),
    val selectedProviderId: String? = null,
    val providersLoading: Boolean = false,
    val providersError: String? = null,
    val autoExpandReasoning: Boolean = false,
    val detailedTools: Boolean = false,
    val serverVersion: String? = null,
    val mcpServers: Map<String, McpStatus> = emptyMap(),
    val mcpLoading: Boolean = false,
    val newMcpName: String = "",
    val newMcpUrl: String = "",
    val mcpError: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val connectionStore = ConnectionStore(application)
    private val themeStore = ThemeStore(application)
    private val modelStore = com.rg.quarkcode.backend.ModelStore(application)
    private val chatPrefs = com.rg.quarkcode.chat.ChatPrefs(application)
    private var providerModels: Map<String, List<String>> = emptyMap()

    var uiState by mutableStateOf(SettingsUiState())
        private set

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { connectionStore.connection.first() }
            val theme = withContext(Dispatchers.IO) { themeStore.mode.first() }
            uiState = uiState.copy(
                host = saved.host,
                username = saved.username,
                password = saved.password,
                theme = theme
            )
        }
    }

    fun onHostChange(value: String) {
        uiState = uiState.copy(host = value, testResult = null)
    }

    fun onUsernameChange(value: String) {
        uiState = uiState.copy(username = value, testResult = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, testResult = null)
    }

    fun testAndSave(onSaved: (Connection) -> Unit) {
        val normalized = runCatching { OpenCodeUrl.normalize(uiState.host) }
        val hostError = normalized.exceptionOrNull()?.message
        if (hostError != null) {
            uiState = uiState.copy(testing = false, testResult = hostError)
            return
        }
        val snapshot = uiState.copy(host = normalized.getOrThrow())
        uiState = snapshot.copy(testing = true, testResult = null)
        viewModelScope.launch {
            val result = runCatching {
                ServeApi(snapshot.host, snapshot.username, snapshot.password)
                    .get<Health>("global/health")
            }
            result.onSuccess { health ->
                val connection = Connection(snapshot.host, snapshot.username, snapshot.password)
                connectionStore.save(connection)
                uiState = snapshot.copy(
                    testing = false,
                    testResult = "Connected" + (health.version.takeIf { it.isNotBlank() }?.let { " v$it" } ?: "")
                )
                onSaved(connection)
                loadProviders()
            }.onFailure { err ->
                uiState = snapshot.copy(testing = false, testResult = err.message ?: "Unreachable")
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        uiState = uiState.copy(theme = mode)
        viewModelScope.launch {
            themeStore.setMode(mode)
        }
    }

    fun loadAll() {
        loadMcp()
        loadProviders()
        loadChatPrefs()
        loadServerInfo()
    }

    private fun loadChatPrefs() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { chatPrefs.prefs.first() } }
                .getOrNull()?.let { prefs ->
                    uiState = uiState.copy(
                        autoExpandReasoning = prefs.autoExpand,
                        detailedTools = prefs.detailed
                    )
                }
        }
    }

    fun setAutoExpand(value: Boolean) {
        uiState = uiState.copy(autoExpandReasoning = value)
        viewModelScope.launch { chatPrefs.setAutoExpand(value) }
    }

    fun setDetailedTools(value: Boolean) {
        uiState = uiState.copy(detailedTools = value)
        viewModelScope.launch { chatPrefs.setDetailed(value) }
    }

    private fun loadServerInfo() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { connectionStore.connection.first() }
            val host = saved.host.ifBlank { return@launch }
            runCatching {
                ServeApi(host, saved.username, saved.password).get<Health>("global/health")
            }.onSuccess { health ->
                uiState = uiState.copy(
                    serverVersion = health.version.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    fun diagnosticsText(): String = buildString {
        appendLine("Quark Code 0.1.0 (com.rg.quarkcode)")
        appendLine("Host: ${uiState.host}")
        appendLine("Server: ${uiState.serverVersion ?: "unknown"}")
        appendLine("Provider: ${uiState.selectedProviderId ?: "none"}")
        appendLine("Theme: ${uiState.theme}")
        appendLine("MCP servers: ${uiState.mcpServers.size}")
    }

    fun loadProviders() {
        viewModelScope.launch {
            // Read the SAVED connection (uiState may still hold unsaved edits or defaults
            // while init is loading — that race made the provider list silently empty).
            val saved = withContext(Dispatchers.IO) { connectionStore.connection.first() }
            val host = saved.host.ifBlank { uiState.host }
            if (host.isBlank()) {
                uiState = uiState.copy(providersLoading = false, providersError = "Set the server URL first, then Test & save.")
                return@launch
            }
            val api = ServeApi(host, saved.username, saved.password)
            uiState = uiState.copy(providersLoading = true, providersError = null)
            val catalog = runCatching { api.get<com.rg.quarkcode.backend.ProviderCatalog>("provider") }
                .getOrNull()
                ?.takeIf { it.all.isNotEmpty() }
                ?: runCatching { api.get<com.rg.quarkcode.backend.ProvidersResponse>("config/providers") }
                    .getOrNull()
                    ?.let { fallback ->
                        com.rg.quarkcode.backend.ProviderCatalog(
                            all = fallback.providers.map { entry ->
                                com.rg.quarkcode.backend.OpenCodeProvider(
                                    id = entry.id,
                                    name = entry.name,
                                    models = entry.models.associate { model ->
                                        model.id to com.rg.quarkcode.backend.OpenCodeModel(id = model.id, name = model.name)
                                    }
                                )
                            },
                            default = fallback.default,
                            connected = fallback.providers.map { it.id }
                        )
                    }
                    ?.takeIf { it.all.isNotEmpty() }
            if (catalog == null) {
                uiState = uiState.copy(providersLoading = false, providersError = "No providers reachable. Test connection first.")
                return@launch
            }
            providerModels = catalog.all.associate { p -> p.id to p.models.keys.toList() }
            val connected = catalog.connected.toSet()
            val sel = runCatching { withContext(Dispatchers.IO) { modelStore.selection.first() } }.getOrNull()
            uiState = uiState.copy(
                providersLoading = false,
                providers = catalog.all.map { p ->
                    ProviderChoice(p.id, p.displayName(), connected.contains(p.id))
                },
                selectedProviderId = sel?.providerId ?: catalog.all.firstOrNull()?.id
            )
        }
    }

    fun onProviderChange(providerId: String) {
        uiState = uiState.copy(selectedProviderId = providerId)
        viewModelScope.launch {
            val modelId = providerModels[providerId]?.firstOrNull()
            modelStore.selectModel(providerId, modelId)
        }
    }

    private fun client(): ServeApi? {
        val state = uiState
        if (state.host.isBlank()) return null
        return ServeApi(state.host, state.username, state.password)
    }

    fun loadMcp() {
        viewModelScope.launch {
            val api = client() ?: return@launch
            uiState = uiState.copy(mcpLoading = true, mcpError = null)
            runCatching { api.get<Map<String, McpStatus>>("mcp") }
                .onSuccess { servers ->
                    uiState = uiState.copy(mcpLoading = false, mcpServers = servers)
                }
                .onFailure { err ->
                    uiState = uiState.copy(
                        mcpLoading = false,
                        mcpError = err.message ?: "Could not load MCP servers"
                    )
                }
        }
    }

    fun onNewMcpNameChange(value: String) {
        uiState = uiState.copy(newMcpName = value)
    }

    fun onNewMcpUrlChange(value: String) {
        uiState = uiState.copy(newMcpUrl = value)
    }

    fun addMcp() {
        val name = uiState.newMcpName.trim()
        val url = uiState.newMcpUrl.trim()
        if (name.isEmpty() || url.isEmpty()) {
            uiState = uiState.copy(mcpError = "Name and URL are required.")
            return
        }
        viewModelScope.launch {
            val api = client() ?: return@launch
            uiState = uiState.copy(mcpLoading = true, mcpError = null)
            runCatching {
                api.post<McpStatus>(
                    "mcp",
                    buildJsonObject {
                        put("name", name)
                        put(
                            "config",
                            buildJsonObject {
                                put("type", "remote")
                                put("url", url)
                            }
                        )
                    }
                )
            }.onSuccess {
                uiState = uiState.copy(newMcpName = "", newMcpUrl = "")
                loadMcp()
            }.onFailure { err ->
                uiState = uiState.copy(
                    mcpLoading = false,
                    mcpError = err.message ?: "Could not add server"
                )
            }
        }
    }

    fun toggleMcp(name: String, enable: Boolean) {
        viewModelScope.launch {
            val api = client() ?: return@launch
            runCatching {
                api.postUnit(
                    "mcp/${api.encodePath(name)}/${if (enable) "connect" else "disconnect"}",
                    kotlinx.serialization.json.JsonObject(emptyMap())
                )
            }
            loadMcp()
        }
    }
}
