package com.rg.quarkcode.backend

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackendPanelState(
    val enabled: Boolean = false,
    val supported: Boolean = true,
    val backendState: LocalBackend.State = LocalBackend.State.Idle,
    val downloadedVersion: String? = null,
    val sizeBytes: Long = 0L,
    val downloading: Boolean = false,
    val downloadBytes: Long = 0L,
    val downloadTotal: Long = -1L,
    val downloadVersion: String? = null,
    val error: String? = null
)

/**
 * On-device backend manager (Settings → Device tab). Downloads the native
 * opencode ELF on first opt-in, then starts/stops/deletes it. The APK never
 * carries the runtime — it stays lean.
 */
class BackendViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.applicationContext
    private val store = RuntimeStore(app)
    private var downloadJob: Job? = null
    private var pendingAsset: RuntimeAsset? = null

    var uiState by mutableStateOf(BackendPanelState())
        private set

    val backendState = LocalBackend.state
    val backendLogs = LocalBackend.logs

    init {
        viewModelScope.launch {
            LocalBackend.state.collect { state ->
                uiState = uiState.copy(backendState = state)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prefs = withContext(Dispatchers.IO) { store.prefs.first() }
            val present = withContext(Dispatchers.IO) { RuntimeFiles.isPresent(app) }
            val size = withContext(Dispatchers.IO) { RuntimeFiles.totalBytes(app) }
            uiState = uiState.copy(
                enabled = prefs.backendEnabled,
                supported = RuntimeFiles.supportedAbi() != null,
                downloadedVersion = prefs.runtimeVersion.takeIf { present },
                sizeBytes = size,
                error = if (!present && prefs.runtimeVersion != null) {
                    "Runtime files missing — download again"
                } else {
                    uiState.error
                }
            )
        }
    }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setEnabled(value) }
            uiState = uiState.copy(enabled = value)
        }
    }

    fun download() {
        if (downloadJob?.isActive == true) return
        uiState = uiState.copy(downloading = true, downloadBytes = 0L, downloadTotal = -1L, error = null)
        downloadJob = viewModelScope.launch {
            try {
                val asset = withContext(Dispatchers.IO) { RuntimeDownloader.resolveLatest() }
                    ?: error("No compatible backend release found on GitHub")
                pendingAsset = asset
                uiState = uiState.copy(downloadVersion = asset.version, downloadTotal = asset.size)
                withContext(Dispatchers.IO) {
                    RuntimeDownloader.download(asset, RuntimeFiles.elf(app)) { bytes, total ->
                        withContext(Dispatchers.Main) {
                            uiState = uiState.copy(downloadBytes = bytes, downloadTotal = total)
                        }
                    }
                }
                withContext(Dispatchers.IO) { store.setVersion(asset.version) }
                uiState = uiState.copy(downloading = false)
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    uiState = uiState.copy(downloading = false)
                } else {
                    uiState = uiState.copy(
                        downloading = false,
                        error = e.message?.take(200) ?: "Download failed"
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        pendingAsset = null
        uiState = uiState.copy(downloading = false)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val elf = RuntimeFiles.elf(app)
                if (!RuntimeFiles.isPresent(app) && elf.exists()) elf.delete()
            }
            refresh()
        }
    }

    fun start() {
        QuarkBackendService.start(app)
    }

    fun stop() {
        QuarkBackendService.stop(app)
    }

    fun restart() {
        stop()
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500L)
            QuarkBackendService.start(app)
        }
    }

    fun deleteRuntime() {
        viewModelScope.launch {
            QuarkBackendService.stop(app)
            LocalBackend.stop()
            withContext(Dispatchers.IO) {
                RuntimeFiles.root(app).deleteRecursively()
                store.setVersion(null)
            }
            refresh()
        }
    }
}
