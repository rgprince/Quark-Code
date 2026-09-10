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
        val snapshot = uiState
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
                    error = err.message ?: "Unreachable"
                )
            }
        }
    }
}
