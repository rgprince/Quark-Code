package com.rg.quarkcode.backend

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Supervisor for the on-device backend: official opencode `serve` running
 * inside the Debian proot guest.
 *
 * Process-wide singleton (the future terminal shell attaches to the same
 * handle/logs) — the foreground service is just its keeper. Transport
 * matches the existing client: loopback 4096 + basic auth.
 */
object LocalBackend {

    const val PORT = 4096
    const val USERNAME = "opencode"

    sealed interface State {
        data object Idle : State
        data object Starting : State
        data class Running(val version: String) : State
        data class Stopped(val error: String?) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var process: Process? = null
    private var starting = false

    fun isAlive(): Boolean = try {
        process?.isAlive == true
    } catch (_: Exception) {
        false
    }

    /** Ready when the Debian guest AND the official opencode binary exist. */
    fun isInstalled(context: Context): Boolean =
        DebianInstaller.isInstalled(context) && GuestOpencode.isInstalled(context)

    fun appendLog(line: String) {
        val clean = line.take(500)
        if (clean.isBlank()) return
        _logs.value = (_logs.value + clean).takeLast(200)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun guestBase(context: Context, suite: ProotSuite.Paths): ArrayList<String> {
        val guest = RuntimeFiles.guest(context)
        return arrayListOf(
            suite.proot.absolutePath,
            "-0",
            "--kill-on-exit",
            "--link2symlink",
            "--sysvipc",
            "-r", guest.absolutePath,
            "-w", "/workspace",
            // Host device nodes the Bun/Node runtime needs (/dev/urandom,
            // /proc/self/maps). PRoot emulates part of /proc, but explicit
            // binds match the Termux proot-distro recipe and cost nothing.
            "-b", "/dev:/dev",
            "-b", "/proc:/proc",
            "-b", "${RuntimeFiles.workspace(context).absolutePath}:/workspace"
        )
    }

    /**
     * Clean environment seen INSIDE the Debian guest, passed via
     * `/usr/bin/env -i` so host loader paths (LD_LIBRARY_PATH for the
     * proot binary itself, any stray LD_PRELOAD from the app process)
     * never leak into glibc binaries — that leak is the classic silent
     * execve/linker death ("No such file or directory", dynamic symbol
     * collisions). The host env for the proot process itself stays in
     * [ProotSuite.Paths.baseEnv] and is set on the ProcessBuilder, not here.
     */
    private fun guestCleanEnv(password: String): List<String> {
        val path = "/root/.opencode/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        return listOf(
            "HOME=/root",
            "PATH=$path",
            "TERM=dumb",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "TMPDIR=/tmp",
            "TZ=UTC",
            "XDG_RUNTIME_DIR=/tmp",
            // Belt-and-braces for the desktop `opencode web` habit: even in
            // `serve` mode a spawned helper must open nothing and exit 0
            // (shim installed by DebianInstaller.ensureGuestShims).
            "BROWSER=/usr/local/bin/xdg-open",
            "OPENCODE_SERVER_PASSWORD=$password",
            "OPENCODE_DISABLE_AUTOUPDATE=true"
        )
    }

    /** Host-side env for the proot process itself (loader paths stay here). */
    private fun hostEnv(suite: ProotSuite.Paths): Map<String, String> {
        // suite.baseEnv() already carries LD_LIBRARY_PATH (libsDir first so
        // libtalloc.so.2 resolves), PROOT_LOADER(_32), PROOT_TMP_DIR. Never
        // add LD_PRELOAD here — Termux's Bionic wrapper poisons glibc guests.
        return HashMap(suite.baseEnv())
    }

    /**
     * Starts official `opencode serve` in the guest, waits for /global/health.
     * Returns true once the server answers (callers may then attach).
     */
    suspend fun start(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isAlive()) return@withContext true
        if (starting) {
            repeat(60) {
                if (isAlive() && _state.value is State.Running) return@withContext true
                delay(1000L)
            }
            return@withContext isAlive()
        }
        starting = true
        try {
            if (RuntimeFiles.supportedAbi() == null) {
                _state.value = State.Stopped("Only arm64 devices are supported for now")
                return@withContext false
            }
            val suite = runCatching {
                ProotSuite(context, RuntimeFiles.root(context)).ensureInstalled()
            }.getOrElse { err ->
                _state.value = State.Stopped(err.message?.take(200) ?: "proot suite missing")
                return@withContext false
            }
            if (!DebianInstaller.isInstalled(context)) {
                _state.value = State.Stopped("Debian not downloaded — open Settings → Device")
                return@withContext false
            }
            if (!GuestOpencode.isInstalled(context)) {
                _state.value = State.Stopped("opencode not downloaded — open Settings → Device")
                return@withContext false
            }
            RuntimeFiles.ensureDirs(context)
            val store = RuntimeStore(context.applicationContext)
            val password = store.password()
            // Self-heal PATH + xdg-open shims (cheap, idempotent): covers
            // installs from before the shim era and manual guest edits.
            runCatching { DebianInstaller.ensureGuestShims(RuntimeFiles.guest(context)) }
            // Dead-binary guard: v0.0.x (archived Go repo) has no server
            // mode — launching it only prints usage. Fail fast with the
            // fix instead of a cryptic log.
            val installedVersion = runCatching { store.prefs.first().opencodeVersion }.getOrNull()
            if (installedVersion != null && installedVersion.startsWith("0.0")) {
                _state.value = State.Stopped(
                    "opencode v$installedVersion can't run a server — " +
                        "Device → Check for opencode update (v${GuestOpencode.PINNED_TAG})"
                )
                return@withContext false
            }
            // Same-backend rule: if something already answers on 4096
            // (stale process, manual start, previous run we lost track of),
            // adopt it instead of grabbing the port and dying with EADDRINUSE.
            if (checkHealth(password)) {
                val version = runCatching { store.prefs.first().opencodeVersion }.getOrNull()
                _state.value = State.Running(version ?: "official")
                appendLog("✓ already answering on 127.0.0.1:$PORT — adopted")
                return@withContext true
            }
            val cmd = guestBase(context, suite)
            // NEVER `opencode web` here: web is hardcoded for desktop Linux
            // and spawns `xdg-open http://…` after boot — with no X11/Wayland
            // session the spawn throws ENOENT and kills the whole Bun server.
            // `serve` is the headless mode: no browser spawn, same API.
            // env -i: the guest must see ONLY the clean list (no
            // LD_LIBRARY_PATH/LD_PRELOAD from the host proot env).
            cmd.addAll(listOf("/usr/bin/env", "-i"))
            cmd.addAll(guestCleanEnv(password))
            cmd.addAll(listOf(GuestOpencode.GUEST_BIN, "serve", "--port", PORT.toString(), "--hostname", "127.0.0.1"))
            val pb = ProcessBuilder(cmd)
            pb.directory(RuntimeFiles.workspace(context))
            pb.redirectErrorStream(true)
            val env = pb.environment()
            env.clear()
            // Host env ONLY: loader paths for proot itself. Guest isolation
            // comes from `env -i` above. Clearing first also strips any
            // LD_PRELOAD inherited from the app process (Termux Bionic
            // wrapper would otherwise poison glibc).
            env.putAll(hostEnv(suite))
            _state.value = State.Starting
            appendLog("$ opencode serve --port $PORT (official, Debian guest)")
            val proc = pb.start()
            process = proc
            thread(isDaemon = true, name = "opencode-log") {
                try {
                    proc.inputStream.bufferedReader().forEachLine { appendLog(it) }
                } catch (_: Exception) {
                }
            }
            val version = runCatching { store.prefs.first().opencodeVersion }.getOrNull()
            repeat(60) {
                delay(1000L)
                if (!isAlive()) {
                    // Was: generic "died during startup — see logs" (the
                    // silent-fail complaint). Now the tail rides along so the
                    // Device tab shows the linker/execve reason inline.
                    _state.value = State.Stopped(
                        "Server died during startup: ${recentLogs(5)}"
                    )
                    return@withContext false
                }
                if (checkHealth(password)) {
                    _state.value = State.Running(version ?: "official")
                    appendLog("✓ backend answering on 127.0.0.1:$PORT")
                    return@withContext true
                }
            }
            _state.value = State.Stopped("Backend silent 60s (no /global/health): ${recentLogs(5)}")
            return@withContext false
        } catch (e: Exception) {
            _state.value = State.Stopped(e.message?.take(200) ?: "Could not start backend")
            return@withContext false
        } finally {
            starting = false
        }
    }

    /** One-shot guest command (apt, dpkg-query, …) for the tools menu. */
    suspend fun runGuest(
        context: Context,
        args: List<String>,
        timeoutMs: Long = 180_000L
    ): GuestCmdResult = withContext(Dispatchers.IO) {
        val suite = ProotSuite(context, RuntimeFiles.root(context)).ensureInstalled()
        val store = RuntimeStore(context.applicationContext)
        val cmd = guestBase(context, suite)
        // Same env -i isolation as start(): apt/dpkg/ripgrep die the same
        // silent linker death if LD_LIBRARY_PATH leaks into the guest.
        cmd.addAll(listOf("/usr/bin/env", "-i"))
        cmd.addAll(guestCleanEnv(store.password()))
        cmd.addAll(args)
        val pb = ProcessBuilder(cmd)
        pb.directory(RuntimeFiles.workspace(context))
        pb.redirectErrorStream(true)
        val env = pb.environment()
        env.clear()
        env.putAll(hostEnv(suite))
        val proc = pb.start()
        val out = StringBuilder()
        val reader = thread(isDaemon = true, name = "guest-cmd") {
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(out) { out.appendLine(line.take(500)) }
                }
            } catch (_: Exception) {
            }
        }
        val finished = try {
            proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            false
        }
        if (!finished) {
            runCatching { proc.destroyForcibly() }
        }
        reader.join(3000L)
        GuestCmdResult(
            code = if (finished) proc.exitValue() else 124,
            output = out.toString().takeLast(8000)
        )
    }

    fun stop() {
        try {
            process?.destroy()
            try {
                if (process?.waitFor(3, TimeUnit.SECONDS) == false) process?.destroyForcibly()
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
        process = null
        _state.value = State.Idle
        appendLog("■ backend stopped")
    }

    private val healthHttp = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** Last N log lines inline for Stopped errors (kills the silent fail). */
    private fun recentLogs(n: Int): String {
        val tail = _logs.value.takeLast(n).filter { it.isNotBlank() }
        if (tail.isEmpty()) return "no output — reinstall Debian + opencode (Device tab)"
        return tail.joinToString(" | ").take(320)
    }

    private fun checkHealth(password: String): Boolean {
        // New server: /global/health. Old v0.0.55 Go server: /health.
        // Try both so existing downloads keep working.
        return checkUrl(password, "http://127.0.0.1:$PORT/global/health") ||
            checkUrl(password, "http://127.0.0.1:$PORT/health")
    }

    private fun checkUrl(password: String, url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Authorization", Credentials.basic(USERNAME, password))
                .get()
                .build()
            healthHttp.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }
}
