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
            "-b", "${RuntimeFiles.workspace(context).absolutePath}:/workspace"
        )
    }

    private fun guestEnv(
        context: Context,
        suite: ProotSuite.Paths,
        password: String
    ): Map<String, String> {
        val env = HashMap(suite.baseEnv())
        env["HOME"] = "/root"
        env["PATH"] = "/root/.opencode/bin:/usr/local/bin:/usr/bin:/bin"
        env["TERM"] = "dumb"
        env["LANG"] = "C.UTF-8"
        env["OPENCODE_SERVER_PASSWORD"] = password
        env["OPENCODE_DISABLE_AUTOUPDATE"] = "true"
        env.remove("TMPDIR")
        return env
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
            val cmd = guestBase(context, suite)
            // v0.0.55 (opencode-ai, Go) has `serve` but NO --port/--hostname
            // flags — and its defaults are already 4096 + 127.0.0.1. Newer
            // sst/opencode accepts the flags but doesn't need them either,
            // so run bare `serve` for compat with what you already
            // downloaded (no re-download on limited internet).
            cmd.addAll(listOf(GuestOpencode.GUEST_BIN, "serve"))
            val pb = ProcessBuilder(cmd)
            pb.directory(RuntimeFiles.workspace(context))
            pb.redirectErrorStream(true)
            val env = pb.environment()
            env.clear()
            env.putAll(guestEnv(context, suite, password))
            _state.value = State.Starting
            appendLog("$ opencode serve (official, Debian guest)")
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
                    _state.value = State.Stopped("Server process died during startup — see logs")
                    return@withContext false
                }
                if (checkHealth(password)) {
                    _state.value = State.Running(version ?: "official")
                    appendLog("✓ backend answering on 127.0.0.1:$PORT")
                    return@withContext true
                }
            }
            _state.value = State.Stopped("Backend did not answer in 60s — see logs")
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
        cmd.addAll(args)
        val pb = ProcessBuilder(cmd)
        pb.directory(RuntimeFiles.workspace(context))
        pb.redirectErrorStream(true)
        val env = pb.environment()
        env.clear()
        env.putAll(guestEnv(context, suite, store.password()))
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
