package com.quark.agent.connect

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quark.agent.backend.Connection
import com.quark.agent.backend.ConnectionStore
import com.quark.agent.backend.ServeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConnectUiState(
    val host: String = "http://localhost:4096",
    val username: String = "opencode",
    val password: String = "",
    val checking: Boolean = false,
    val healthy: Boolean = false,
    val version: String = "",
    val error: String? = null
)

class ConnectViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ConnectionStore(application)
    var uiState by mutableStateOf(ConnectUiState())
        private set

    init {
        viewModelScope.launch {
            store.connection.collect { saved ->
                uiState = uiState.copy(
                    host = saved.host,
                    username = saved.username,
                    password = saved.password
                )
            }
        }
    }

    fun onHostChange(value: String) {
        uiState = uiState.copy(host = value, healthy = false, error = null)
    }

    fun onUsernameChange(value: String) {
        uiState = uiState.copy(username = value, healthy = false, error = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, healthy = false, error = null)
    }

    fun testConnection(onOk: () -> Unit) {
        val host = normalizeHost(uiState.host)
        val snapshot = uiState.copy(host = host)
        uiState = snapshot.copy(checking = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    ServeClient.service(snapshot.host, snapshot.username, snapshot.password).health()
                }
            }
            result.onSuccess { health ->
                store.save(Connection(snapshot.host, snapshot.username, snapshot.password))
                uiState = snapshot.copy(
                    checking = false,
                    healthy = true,
                    version = health.version,
                    error = null
                )
                onOk()
            }.onFailure { err ->
                uiState = snapshot.copy(
                    checking = false,
                    healthy = false,
                    error = friendlyError(err)
                )
            }
        }
    }

    private fun normalizeHost(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.contains("://")) trimmed else "http://$trimmed"
    }

    private fun friendlyError(err: Throwable): String {
        val message = err.message ?: ""
        return when {
            "Cleartext" in message ->
                "HTTP blocked: update Quark (cleartext fix) and retry."
            "Failed to connect" in message || "refused" in message.lowercase() ->
                "No server at this address. In Termux run: opencode serve --port 4096"
            "401" in message || "Unauthorized" in message ->
                "Wrong username or password (server: OPENCODE_SERVER_USERNAME/PASSWORD)."
            else -> message.ifEmpty { "Unreachable" }
        }
    }
}
