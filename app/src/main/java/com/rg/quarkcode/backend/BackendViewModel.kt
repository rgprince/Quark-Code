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

enum class StageState { PENDING, ACTIVE, DONE, ERROR }

data class InstallStage(
    val id: String,
    val label: String,
    val state: StageState = StageState.PENDING,
    /** 0..1 when known, negative when indeterminate. */
    val fraction: Float = 0f,
    val detail: String = ""
)

data class ToolRowState(
    val def: ToolDef,
    val installed: Boolean = false,
    val working: Boolean = false
)

/**
 * On-device backend manager: three SEPARATE sections with their own staged
 * progress — (1) Debian guest, (2) official opencode, (3) optional tools —
 * so people always see exactly how far each download got.
 */
class BackendViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.applicationContext
    private val store = RuntimeStore(app)
    private var debianJob: Job? = null
    private var opencodeJob: Job? = null
    private val toolJobs = mutableMapOf<String, Job>()

    var debianStages by mutableStateOf<List<InstallStage>>(emptyList())
        private set
    var opencodeStages by mutableStateOf<List<InstallStage>>(emptyList())
        private set
    var debianVersion by mutableStateOf<String?>(null)
        private set
    var opencodeVersion by mutableStateOf<String?>(null)
        private set
    var debianBytes by mutableStateOf(0L)
        private set
    var opencodeBytes by mutableStateOf(0L)
        private set
    var enabled by mutableStateOf(false)
        private set
    var supported by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var tools by mutableStateOf<List<ToolRowState>>(GuestTools.ALL.map { ToolRowState(it) })
        private set

    val backendState = LocalBackend.state
    val backendLogs = LocalBackend.logs

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val prefs = withContext(Dispatchers.IO) { store.prefs.first() }
            val debVer = withContext(Dispatchers.IO) { DebianInstaller.installedVersion(app) }
            val ocPresent = withContext(Dispatchers.IO) { GuestOpencode.isInstalled(app) }
            debianVersion = debVer
            opencodeVersion = prefs.opencodeVersion.takeIf { ocPresent }
            debianBytes = withContext(Dispatchers.IO) { RuntimeFiles.guestBytes(app) }
            opencodeBytes = withContext(Dispatchers.IO) {
                GuestOpencode.guestBinary(app).takeIf { it.isFile }?.length() ?: 0L
            }
            enabled = prefs.backendEnabled
            supported = RuntimeFiles.supportedAbi() != null
            if (debVer != null && ocPresent) refreshTools()
        }
    }

    private fun setStage(
        stages: List<InstallStage>,
        id: String,
        state: StageState,
        fraction: Float = 0f,
        detail: String = ""
    ): List<InstallStage> = stages.map {
        if (it.id == id) it.copy(state = state, fraction = fraction, detail = detail) else it
    }

    // ---- Section 1: Debian guest ----

    fun downloadDebian() {
        if (debianJob?.isActive == true) return
        error = null
        debianStages = listOf(
            InstallStage(DebianInstaller.STAGE_RESOLVE, "Find Debian release"),
            InstallStage(DebianInstaller.STAGE_DOWNLOAD, "Download rootfs"),
            InstallStage(DebianInstaller.STAGE_EXTRACT, "Extract system"),
            InstallStage(DebianInstaller.STAGE_CONFIGURE, "Configure")
        )
        debianJob = viewModelScope.launch {
            try {
                debianStages = setStage(debianStages, DebianInstaller.STAGE_RESOLVE, StageState.ACTIVE)
                val asset = withContext(Dispatchers.IO) { DebianInstaller.resolve() }
                    ?: error("No Debian rootfs release found")
                debianStages = setStage(
                    debianStages, DebianInstaller.STAGE_RESOLVE, StageState.DONE, 1f, asset.fileName
                )
                withContext(Dispatchers.IO) {
                    DebianInstaller.install(app, asset) { stage, fraction, detail ->
                        withContext(Dispatchers.Main) {
                            debianStages = setStage(debianStages, stage, StageState.ACTIVE, fraction, detail)
                        }
                    }
                }
                withContext(Dispatchers.IO) { store.setDebianVersion(asset.version) }
                debianStages = debianStages.map { it.copy(state = StageState.DONE, fraction = 1f) }
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    debianStages = emptyList()
                } else {
                    val failed = debianStages.firstOrNull { it.state == StageState.ACTIVE }?.id
                    debianStages = if (failed != null) {
                        setStage(debianStages, failed, StageState.ERROR, detail = e.message?.take(160) ?: "failed")
                    } else {
                        debianStages
                    }
                    error = e.message?.take(200) ?: "Debian install failed"
                }
            }
        }
    }

    fun cancelDebian() {
        debianJob?.cancel()
        debianStages = emptyList()
    }

    fun deleteDebian() {
        viewModelScope.launch {
            QuarkBackendService.stop(app)
            LocalBackend.stop()
            withContext(Dispatchers.IO) {
                RuntimeFiles.guest(app).deleteRecursively()
                FileCleanup.deleteTmp(app)
                store.setDebianVersion(null)
                store.setOpencodeVersion(null)
            }
            debianStages = emptyList()
            opencodeStages = emptyList()
            refresh()
        }
    }

    // ---- Section 2: official opencode ----

    fun downloadOpencode() {
        if (opencodeJob?.isActive == true) return
        if (!DebianInstaller.isInstalled(app)) {
            error = "Install the Debian system first"
            return
        }
        error = null
        opencodeStages = listOf(
            InstallStage(GuestOpencode.STAGE_RESOLVE, "Find official release"),
            InstallStage(GuestOpencode.STAGE_DOWNLOAD, "Download opencode"),
            InstallStage(GuestOpencode.STAGE_EXTRACT, "Install into guest"),
            InstallStage(GuestOpencode.STAGE_VERIFY, "Verify")
        )
        opencodeJob = viewModelScope.launch {
            try {
                opencodeStages = setStage(opencodeStages, GuestOpencode.STAGE_RESOLVE, StageState.ACTIVE)
                val asset = withContext(Dispatchers.IO) { GuestOpencode.resolve() }
                    ?: error("No official opencode release found")
                opencodeStages = setStage(
                    opencodeStages, GuestOpencode.STAGE_RESOLVE, StageState.DONE, 1f,
                    "${asset.fileName} · v${asset.version}"
                )
                withContext(Dispatchers.IO) {
                    GuestOpencode.install(app, asset) { stage, fraction, detail ->
                        withContext(Dispatchers.Main) {
                            opencodeStages = setStage(opencodeStages, stage, StageState.ACTIVE, fraction, detail)
                        }
                    }
                }
                withContext(Dispatchers.IO) { store.setOpencodeVersion(asset.version) }
                opencodeStages = opencodeStages.map { it.copy(state = StageState.DONE, fraction = 1f) }
                refresh()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    opencodeStages = emptyList()
                } else {
                    val failed = opencodeStages.firstOrNull { it.state == StageState.ACTIVE }?.id
                    opencodeStages = if (failed != null) {
                        setStage(opencodeStages, failed, StageState.ERROR, detail = e.message?.take(160) ?: "failed")
                    } else {
                        opencodeStages
                    }
                    error = e.message?.take(200) ?: "opencode install failed"
                }
            }
        }
    }

    fun cancelOpencode() {
        opencodeJob?.cancel()
        opencodeStages = emptyList()
    }

    fun deleteOpencode() {
        viewModelScope.launch {
            QuarkBackendService.stop(app)
            LocalBackend.stop()
            withContext(Dispatchers.IO) {
                GuestOpencode.guestBinary(app).parentFile?.deleteRecursively()
                store.setOpencodeVersion(null)
            }
            opencodeStages = emptyList()
            refresh()
        }
    }

    // ---- Section 3: tools ----

    fun refreshTools() {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                GuestTools.ALL.map { def ->
                    val installed = GuestTools.isInstalled(
                        run = { args -> LocalBackend.runGuest(app, args, 30_000L) },
                        def = def
                    )
                    ToolRowState(def, installed)
                }
            }
            tools = rows
        }
    }

    fun installTool(id: String) {
        val row = tools.firstOrNull { it.def.id == id } ?: return
        if (toolJobs[id]?.isActive == true) return
        tools = tools.map { if (it.def.id == id) it.copy(working = true) else it }
        toolJobs[id] = viewModelScope.launch {
            try {
                val ok = GuestTools.install(
                    run = { args -> LocalBackend.runGuest(app, args, 600_000L) },
                    def = row.def,
                    onLog = { line -> LocalBackend.appendLog("[${row.def.id}] $line") }
                )
                if (!ok) error = "${row.def.label} install failed — see server log"
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    error = e.message?.take(200) ?: "${row.def.label} install failed"
                }
            }
            tools = tools.map { if (it.def.id == id) it.copy(working = false) else it }
            refreshTools()
        }
    }

    fun removeTool(id: String) {
        val row = tools.firstOrNull { it.def.id == id } ?: return
        if (toolJobs[id]?.isActive == true) return
        tools = tools.map { if (it.def.id == id) it.copy(working = true) else it }
        toolJobs[id] = viewModelScope.launch {
            try {
                GuestTools.remove(
                    run = { args -> LocalBackend.runGuest(app, args, 300_000L) },
                    def = row.def,
                    onLog = { line -> LocalBackend.appendLog("[${row.def.id}] $line") }
                )
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    error = e.message?.take(200) ?: "${row.def.label} removal failed"
                }
            }
            tools = tools.map { if (it.def.id == id) it.copy(working = false) else it }
            refreshTools()
        }
    }

    // ---- Service ----

    fun setBackendEnabled(value: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setEnabled(value) }
            enabled = value
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

    private object FileCleanup {
        fun deleteTmp(app: android.content.Context) {
            val tmp = java.io.File(RuntimeFiles.root(app), "debian.tmp")
            if (tmp.exists()) tmp.delete()
            val tgz = java.io.File(RuntimeFiles.root(app), "opencode.tgz")
            if (tgz.exists()) tgz.delete()
        }
    }
}
