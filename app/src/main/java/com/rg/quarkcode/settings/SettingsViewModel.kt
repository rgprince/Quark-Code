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

data class SettingsUiState(
    val host: String = "http://localhost:4096",
    val username: String = "opencode",
    val password: String = "",
    val testing: Boolean = false,
    val testResult: String? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
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
