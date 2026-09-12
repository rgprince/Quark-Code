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
 * Supervisor for the on-device opencode server process.
 *
 * Lives as a process-wide singleton (so the future terminal shell can attach
 * to the same handle/logs) — the foreground service is just its keeper.
 * Transport matches the existing client: `serve --port 4096` on loopback
 * with basic auth, so ChatViewModel talks to it like any other backend.
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

    fun appendLog(line: String) {
        val clean = line.take(500)
        if (clean.isBlank()) return
        val next = (_logs.value + clean).takeLast(200)
        _logs.value = next
    }

    /**
     * Starts `serve` through the system linker and waits for /global/health.
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
            val elf = RuntimeFiles.elf(context)
            if (!RuntimeFiles.isPresent(context)) {
                _state.value = State.Stopped("Runtime not downloaded — open Settings → Device")
                return@withContext false
            }
            RuntimeFiles.ensureDirs(context)
            val store = RuntimeStore(context.applicationContext)
            val password = store.password()
            val home = RuntimeFiles.home(context)
            val workspace = RuntimeFiles.workspace(context)
            val pb = ProcessBuilder(
                RuntimeFiles.linker(),
                elf.absolutePath,
                "serve",
                "--port", PORT.toString(),
                "--hostname", "127.0.0.1"
            )
            pb.directory(workspace)
            pb.redirectErrorStream(true)
            val env = pb.environment()
            env["HOME"] = home.absolutePath
            env["TMPDIR"] = home.resolve("tmp").absolutePath
            env["PATH"] = "/system/bin:/vendor/bin"
            env["TERM"] = "dumb"
            env["OPENCODE_SERVER_PASSWORD"] = password
            _state.value = State.Starting
            appendLog("$ opencode serve --port $PORT (local device backend)")
            val proc = pb.start()
            process = proc
            thread(isDaemon = true, name = "opencode-log") {
                try {
                    proc.inputStream.bufferedReader().forEachLine { appendLog(it) }
                } catch (_: Exception) {
                }
            }
            val version = runCatching { store.prefs.first().runtimeVersion }.getOrNull()
            repeat(45) {
                delay(1000L)
                if (!isAlive()) {
                    _state.value = State.Stopped("Server process died during startup — see logs")
                    return@withContext false
                }
                if (checkHealth(password)) {
                    _state.value = State.Running(version ?: "local")
                    appendLog("✓ backend answering on 127.0.0.1:$PORT")
                    return@withContext true
                }
            }
            _state.value = State.Stopped("Backend did not answer in 45s — see logs")
            return@withContext false
        } catch (e: Exception) {
            _state.value = State.Stopped(e.message?.take(200) ?: "Could not start backend")
            return@withContext false
        } finally {
            starting = false
        }
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

    suspend fun healthNow(password: String): Boolean = withContext(Dispatchers.IO) {
        checkHealth(password)
    }

    private val healthHttp = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun checkHealth(password: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("http://127.0.0.1:$PORT/global/health")
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
