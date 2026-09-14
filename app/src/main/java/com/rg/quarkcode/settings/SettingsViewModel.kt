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
import com.rg.quarkcode.backend.RuntimeStore
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
    val detailedTools: Boolean = false,
    val textScale: Float = 1f,
    val mcpServers: Map<String, McpStatus> = emptyMap(),
    val mcpLoading: Boolean = false,
    val newMcpName: String = "",
    val newMcpUrl: String = "",
    val mcpError: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val connectionStore = ConnectionStore(application)
    private val runtimeStore = RuntimeStore(application)
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
        loadProviders()
        loadChatPrefs()
    }

    private fun loadChatPrefs() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { chatPrefs.prefs.first() } }
                .getOrNull()?.let { prefs ->
                    uiState = uiState.copy(
                        detailedTools = prefs.detailed,
                        textScale = prefs.textScale
                    )
                }
        }
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

    // ---- Connection resolution + provider catalog (Settings has its own VM) ----
    // Chat auto-attaches the on-device backend (127.0.0.1:4096 + RuntimeStore
    // password) without ever pressing Test & save, so ConnectionStore alone
    // goes stale (empty password → 401 → empty provider/MCP lists). Try the
    // saved remote first, then the on-device loopback, and use whichever
    // answers /global/health.
    private suspend fun candidates(): List<ServeApi> {
        val saved = runCatching {
            withContext(Dispatchers.IO) { connectionStore.connection.first() }
        }.getOrNull()
        val list = mutableListOf<ServeApi>()
        if (saved != null && saved.host.isNotBlank()) {
            list += ServeApi(saved.host, saved.username, saved.password)
        }
        val localPw = runCatching {
            withContext(Dispatchers.IO) { runtimeStore.password() }
        }.getOrNull().orEmpty()
        list += ServeApi("http://127.0.0.1:4096", "opencode", localPw)
        return list
    }

    private suspend fun firstWorkingApi(): ServeApi? {
        val all = candidates()
        for (api in all) {
            val ok = runCatching { api.get<Health>("global/health") }.isSuccess
            if (ok) return api
        }
        return all.firstOrNull()
    }

    private suspend fun loadCatalogFrom(api: ServeApi): com.rg.quarkcode.backend.ProviderCatalog? {
        return runCatching { api.get<com.rg.quarkcode.backend.ProviderCatalog>("provider") }
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
    }

    fun loadProviders() {
        viewModelScope.launch {
            uiState = uiState.copy(providersLoading = true, providersError = null)
            var catalog: com.rg.quarkcode.backend.ProviderCatalog? = null
            var workingApi: ServeApi? = null
            var lastError: String? = null
            for (api in candidates()) {
                val got = loadCatalogFrom(api)
                if (got != null) {
                    catalog = got
                    workingApi = api
                    break
                } else {
                    lastError = runCatching { api.get<Health>("global/health") }
                        .exceptionOrNull()?.message
                }
            }
            if (catalog == null) {
                uiState = uiState.copy(
                    providersLoading = false,
                    providersError = lastError?.let { "No providers reachable ($it). Start the backend or Test & save." }
                        ?: "No providers reachable. Start the backend or Test & save."
                )
                return@launch
            }
            providerModels = catalog.all.associate { p -> p.id to p.models.keys.toList() }
            val connected = catalog.connected.toSet()
            val sel = runCatching { withContext(Dispatchers.IO) { modelStore.selection.first() } }.getOrNull()
            val methods = workingApi?.let { api ->
                runCatching {
                    api.get<Map<String, List<com.rg.quarkcode.backend.ProviderAuthMethod>>>("provider/auth")
                }.getOrNull()
            } ?: emptyMap()
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
            val api = firstWorkingApi()
            if (api == null) {
                uiState = uiState.copy(
                    authDialog = dialog.copy(saving = false, error = "No server reachable. Start the backend or Test & save.")
                )
                return@launch
            }
            val result = runCatching {
                api.putUnit(
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
            val api = firstWorkingApi()
            if (api == null) {
                uiState = uiState.copy(
                    authDialog = dialog.copy(saving = false, error = "No server reachable. Start the backend or Test & save.")
                )
                return@launch
            }
            val result = runCatching {
                api.deleteUnit("auth/${dialog.providerId}")
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

    fun loadMcp() {
        viewModelScope.launch {
            uiState = uiState.copy(mcpLoading = true, mcpError = null)
            // Same stale-connection trap as providers: prefer the saved
            // remote, fall back to the on-device loopback the chat uses.
            val api = firstWorkingApi()
            if (api == null) {
                uiState = uiState.copy(
                    mcpLoading = false,
                    mcpError = "No server reachable. Start the backend or Test & save."
                )
                return@launch
            }
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
            val api = firstWorkingApi()
            if (api == null) {
                uiState = uiState.copy(
                    mcpLoading = false,
                    mcpError = "No server reachable. Start the backend or Test & save."
                )
                return@launch
            }
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
            val api = firstWorkingApi() ?: return@launch
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
