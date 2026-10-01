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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // One apt writer per guest: tapping Install on fzf then gh used to run
    // two `apt-get install` proot processes at once and both died on the
    // dpkg frontend lock (pid-held). Second tool now waits its turn.
    private val aptMutex = Mutex()

    var debianStages by mutableStateOf<List<InstallStage>>(emptyList())
        private set
    var opencodeStages by mutableStateOf<List<InstallStage>>(emptyList())
        private set
    var debianVersion by mutableStateOf<String?>(null)
        private set
    /** True readiness: marker AND a real debian tree (not just a saved label). */
    var debianReady by mutableStateOf(false)
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
            val debReady = withContext(Dispatchers.IO) { DebianInstaller.isInstalled(app) }
            val ocPresent = withContext(Dispatchers.IO) { GuestOpencode.isInstalled(app) }
            debianVersion = debVer
            debianReady = debReady
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
                // Retry reuses the last resolved asset (no API call); a fresh
                // "check" clears it first via the update path below.
                val asset = withContext(Dispatchers.IO) { store.cachedDebianAsset() }
                    ?: withContext(Dispatchers.IO) {
                        DebianInstaller.resolve().also { store.saveDebianAsset(it) }
                    }
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
                // Chain: a user who switched the backend on shouldn't tap
                // through three menus — keep walking toward a running server.
                if (enabled && !GuestOpencode.isInstalled(app)) downloadOpencode()
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

    fun downloadOpencode(checkUpdate: Boolean = false) {
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
                // Update checks bypass the cached asset so they see upstream.
                if (checkUpdate) withContext(Dispatchers.IO) { store.clearOpencodeAsset() }
                // Migration: cached 0.0.x assets point at the archived
                // opencode-ai repo (no server mode), sst/opencode is archived,
                // and the .zip era never existed upstream (real asset is the
                // glibc `opencode-linux-arm64.tar.gz`). Drop them so retry
                // actually fetches the pinned anomalyco build instead of
                // re-installing the same dead binary.
                withContext(Dispatchers.IO) {
                    val stale = store.cachedOpencodeAsset()
                    if (stale != null &&
                        (stale.url.contains("opencode-ai/opencode") ||
                            stale.url.contains("sst/opencode") ||
                            stale.fileName.endsWith(".zip", ignoreCase = true) ||
                            stale.version.startsWith("0.0"))
                    ) {
                        store.clearOpencodeAsset()
                    }
                }
                val asset = withContext(Dispatchers.IO) { store.cachedOpencodeAsset() }
                    ?: withContext(Dispatchers.IO) {
                        GuestOpencode.resolve().also { store.saveOpencodeAsset(it) }
                    }
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
                // Chain: finish the job — start the server the user asked for.
                if (enabled) start()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    opencodeStages = emptyList()
                } else {
                    val failed = opencodeStages.firstOrNull { it.state == StageState.ACTIVE }?.id
                    // Resolve failures are fully described by the banner above
                    // (vm.error) — keep the stage row short so the same text
                    // isn't shown twice.
                    val detail = if (failed == GuestOpencode.STAGE_RESOLVE) {
                        "retry with the button below"
                    } else {
                        e.message?.take(160) ?: "failed"
                    }
                    // A failed resolve must never poison the cache: the next
                    // tap re-resolves live instead of reusing a stale asset.
                    if (failed == GuestOpencode.STAGE_RESOLVE) {
                        withContext(Dispatchers.IO) { store.clearOpencodeAsset() }
                    }
                    opencodeStages = if (failed != null) {
                        setStage(opencodeStages, failed, StageState.ERROR, detail = detail)
                    } else {
                        opencodeStages
                    }
                    error = e.message?.take(320) ?: "opencode install failed"
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
                // Queued behind a running install: say so, or the log goes
                // quiet after "$ apt-get install" and reads as a silent stall.
                if (aptMutex.isLocked) {
                    LocalBackend.appendLog("[${row.def.id}] waiting for the running install to finish…")
                }
                val ok = aptMutex.withLock {
                    GuestTools.install(
                        // dpkg bookkeeping gets a short budget: with the 5s
                        // wait slices a wedged configure now fails fast
                        // instead of eating the whole install timeout.
                        run = { args ->
                            LocalBackend.runGuest(
                                app, args,
                                if (args.firstOrNull() == "dpkg") 90_000L else 600_000L
                            )
                        },
                        def = row.def,
                        onLog = { line -> LocalBackend.appendLog("[${row.def.id}] $line") }
                    )
                }
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
                if (aptMutex.isLocked) {
                    LocalBackend.appendLog("[${row.def.id}] waiting for the running install to finish…")
                }
                aptMutex.withLock {
                    GuestTools.remove(
                        run = { args ->
                            LocalBackend.runGuest(
                                app, args,
                                if (args.firstOrNull() == "dpkg") 90_000L else 300_000L
                            )
                        },
                        def = row.def,
                        onLog = { line -> LocalBackend.appendLog("[${row.def.id}] $line") }
                    )
                }
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
            if (!value) {
                stop()
                return@launch
            }
            // Toggle ON means "make it run": walk the whole chain, skipping
            // whatever is already in place. No dead toggles.
            error = null
            when {
                !DebianInstaller.isInstalled(app) -> downloadDebian()
                !GuestOpencode.isInstalled(app) -> downloadOpencode()
                else -> start()
            }
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
            val pkg = java.io.File(RuntimeFiles.root(app), "opencode.pkg")
            if (pkg.exists()) pkg.delete()
            // Legacy name from the tar.gz era — clean once, then forget.
            val tgz = java.io.File(RuntimeFiles.root(app), "opencode.tgz")
            if (tgz.exists()) tgz.delete()
        }
    }
}
