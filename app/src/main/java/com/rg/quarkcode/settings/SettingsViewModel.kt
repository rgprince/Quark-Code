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

data class ProviderAuthDialog(
    val providerId: String = "",
    val providerName: String = "",
    val methodLabels: List<String> = emptyList(),
    val apiKey: String = "",
    val saving: Boolean = false,
    val error: String? = null
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
    val authDialog: ProviderAuthDialog? = null,
    val autoExpandReasoning: Boolean = false,
    val detailedTools: Boolean = false,
    val textScale: Float = 1f,
    val serverVersion: String? = null,
    val serverConfigJson: String? = null,
    val configDraft: String = "",
    val configEditing: Boolean = false,
    val configSaving: Boolean = false,
    val configError: String? = null,
    val infoProviders: List<ProviderChoice> = emptyList(),
    val infoCommands: List<com.rg.quarkcode.backend.OpenCodeCommand> = emptyList(),
    val infoSkills: List<com.rg.quarkcode.backend.OpenCodeSkill> = emptyList(),
    val infoLoading: Boolean = false,
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
    private var authMethods: Map<String, List<com.rg.quarkcode.backend.ProviderAuthMethod>> = emptyMap()

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
        loadChatPrefs()
        loadServerInfo()
    }

    private fun loadChatPrefs() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { chatPrefs.prefs.first() } }
                .getOrNull()?.let { prefs ->
                    uiState = uiState.copy(
                        autoExpandReasoning = prefs.autoExpand,
                        detailedTools = prefs.detailed,
                        textScale = prefs.textScale
                    )
                }
        }
    }

    fun setAutoExpand(value: Boolean) {
        uiState = uiState.copy(autoExpandReasoning = value)
        viewModelScope.launch { chatPrefs.setAutoExpand(value) }
    }

    fun setTextScale(value: Float) {
        val clamped = value.coerceIn(0.8f, 1.3f)
        uiState = uiState.copy(textScale = clamped)
        viewModelScope.launch { chatPrefs.setTextScale(clamped) }
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
        appendLine("Quark Code ${appVersion()} (com.rg.quarkcode)")
        appendLine("Host: ${uiState.host}")
        appendLine("Server: ${uiState.serverVersion ?: "unknown"}")
        appendLine("Provider: ${uiState.selectedProviderId ?: "none"}")
        appendLine("Theme: ${uiState.theme}")
        appendLine("MCP servers: ${uiState.mcpServers.size}")
        appendLine("Memory: ${memoryLine()}")
        appendLine("Storage: ${storageLine()}")
    }

    private fun appVersion(): String = runCatching {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        info.versionName ?: "0.1.0"
    }.getOrNull() ?: "0.1.0"

    private fun memoryLine(): String {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()) / 1048576
        val max = runtime.maxMemory() / 1048576
        return "app ${used}M / max ${max}M"
    }

    private fun storageLine(): String = runCatching {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val free = stat.availableBytes / 1073741824
        val total = stat.totalBytes / 1073741824
        "free ${free}G / total ${total}G"
    }.getOrNull() ?: "unknown"

    // ---- Server info inspector (and-code ServerInfo parity: config + lists) ----

    fun loadServerInfoFull() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { connectionStore.connection.first() }
            val host = saved.host.ifBlank { return@launch }
            val api = ServeApi(host, saved.username, saved.password)
            uiState = uiState.copy(infoLoading = true)
            val config = runCatching {
                api.get<kotlinx.serialization.json.JsonObject>("config")
            }.getOrNull()
            val providers = runCatching {
                api.get<com.rg.quarkcode.backend.ProvidersResponse>("config/providers")
            }.getOrNull()
            val commands = runCatching {
                api.getList<com.rg.quarkcode.backend.OpenCodeCommand>("command")
            }.getOrNull()
            val skills = runCatching {
                api.getList<com.rg.quarkcode.backend.OpenCodeSkill>("skill")
            }.getOrNull()
            val pretty = config?.let {
                kotlinx.serialization.json.Json { prettyPrint = true }
                    .encodeToString(kotlinx.serialization.json.JsonObject.serializer(), it)
            }
            uiState = uiState.copy(
                infoLoading = false,
                serverConfigJson = pretty,
                configDraft = pretty ?: uiState.configDraft,
                infoProviders = providers?.providers?.map {
                    ProviderChoice(it.id, it.name.ifBlank { it.id }, true)
                } ?: emptyList(),
                infoCommands = commands ?: emptyList(),
                infoSkills = skills ?: emptyList()
            )
        }
    }

    fun startEditConfig() {
        uiState = uiState.copy(
            configEditing = true,
            configDraft = uiState.serverConfigJson ?: "",
            configError = null
        )
    }

    fun cancelEditConfig() {
        uiState = uiState.copy(configEditing = false, configError = null)
    }

    fun onConfigDraftChange(value: String) {
        uiState = uiState.copy(configDraft = value, configError = null)
    }

    fun saveConfig() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { connectionStore.connection.first() }
            val host = saved.host.ifBlank { return@launch }
            val body = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(uiState.configDraft)
                    as? kotlinx.serialization.json.JsonObject
                    ?: throw IllegalArgumentException("Config must be a JSON object")
            }.getOrElse { err ->
                uiState = uiState.copy(configError = err.message ?: "Invalid JSON")
                return@launch
            }
            uiState = uiState.copy(configSaving = true, configError = null)
            val result = runCatching {
                ServeApi(host, saved.username, saved.password)
                    .patch<kotlinx.serialization.json.JsonObject>("config", body)
            }
            result.onSuccess {
                uiState = uiState.copy(configSaving = false, configEditing = false)
                loadServerInfoFull()
            }.onFailure { err ->
                uiState = uiState.copy(
                    configSaving = false,
                    configError = err.message ?: "Could not save config"
                )
            }
        }
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
            val methods = runCatching {
                api.get<Map<String, List<com.rg.quarkcode.backend.ProviderAuthMethod>>>("provider/auth")
            }.getOrNull() ?: emptyMap()
            authMethods = methods
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

    // Provider detail dialog: API key add/remove (AndCode IA:
    // PUT auth/{id} {type api,key}, DELETE auth/{id}).
    fun openProviderDialog(providerId: String) {
        val name = uiState.providers.firstOrNull { it.id == providerId }?.name ?: providerId
        val labels = authMethods[providerId]?.map { it.label.ifBlank { it.type } } ?: emptyList()
        uiState = uiState.copy(
            authDialog = ProviderAuthDialog(
                providerId = providerId,
                providerName = name,
                methodLabels = labels
            )
        )
    }

    fun closeProviderDialog() {
        uiState = uiState.copy(authDialog = null)
    }

    fun onAuthKeyChange(value: String) {
        uiState.authDialog?.let { dialog ->
            uiState = uiState.copy(authDialog = dialog.copy(apiKey = value, error = null))
        }
    }

    fun saveProviderKey() {
        val dialog = uiState.authDialog ?: return
        val key = dialog.apiKey.trim()
        if (key.isEmpty()) {
            uiState = uiState.copy(authDialog = dialog.copy(error = "Paste an API key first."))
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(authDialog = dialog.copy(saving = true, error = null))
            val saved = withContext(Dispatchers.IO) { connectionStore.connection.first() }
            val result = runCatching {
                ServeApi(saved.host, saved.username, saved.password).putUnit(
                    "auth/${dialog.providerId}",
                    buildJsonObject {
                        put("type", "api")
                        put("key", key)
                    }
                )
            }
            result.onSuccess {
                uiState = uiState.copy(authDialog = null)
                loadProviders()
            }.onFailure { err ->
                uiState = uiState.copy(
                    authDialog = dialog.copy(saving = false, error = err.message ?: "Could not save key")
                )
            }
        }
    }

    fun disconnectProvider() {
        val dialog = uiState.authDialog ?: return
        viewModelScope.launch {
            uiState = uiState.copy(authDialog = dialog.copy(saving = true, error = null))
            val saved = withContext(Dispatchers.IO) { connectionStore.connection.first() }
            val result = runCatching {
                ServeApi(saved.host, saved.username, saved.password)
                    .deleteUnit("auth/${dialog.providerId}")
            }
            result.onSuccess {
                uiState = uiState.copy(authDialog = null)
                loadProviders()
            }.onFailure { err ->
                uiState = uiState.copy(
                    authDialog = dialog.copy(saving = false, error = err.message ?: "Could not disconnect")
                )
            }
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
