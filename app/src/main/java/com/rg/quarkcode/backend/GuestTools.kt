package com.rg.quarkcode.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Optional guest tools, each installed/removed independently via the guest's
 * own apt. Separate menu from opencode on purpose: the agent core stays
 * untouched while tools come and go.
 */
data class ToolDef(
    val id: String,
    val label: String,
    val description: String,
    val debianPkg: String,
    val approxMb: String
)

data class GuestCmdResult(
    val code: Int,
    val output: String
)

object GuestTools {

    val ALL = listOf(
        ToolDef("git", "Git", "Repos, worktrees, diff context", "git", "~60 MB"),
        ToolDef("node", "Node.js", "npm/npx-based MCPs and scripts", "nodejs", "~200 MB"),
        ToolDef("gh", "GitHub CLI", "gh commands inside the agent", "gh", "~40 MB"),
        ToolDef("ripgrep", "ripgrep", "Fast code search", "ripgrep", "~5 MB"),
        ToolDef("fzf", "fzf", "Fuzzy finder for scripts", "fzf", "~5 MB")
    )

    suspend fun isInstalled(
        run: suspend (List<String>) -> GuestCmdResult,
        def: ToolDef
    ): Boolean {
        val result = runCatching { run(listOf("dpkg-query", "-W", "-f=\${Status}", def.debianPkg)) }
            .getOrNull() ?: return false
        return result.code == 0 && result.output.contains("install ok installed")
    }

    /**
     * Heals an interrupted dpkg state. apt dies with "dpkg was interrupted,
     * you must manually run 'dpkg --configure -a'" after a killed download,
     * reboot, or dropped process — and then EVERY later apt command
     * (install AND remove) fails the same way until someone configures.
     * Best-effort: never fails the caller, just logs.
     */
    private suspend fun healDpkg(
        run: suspend (List<String>) -> GuestCmdResult,
        onLog: suspend (String) -> Unit
    ) {
        onLog("$ dpkg --configure -a (repair interrupted installs)")
        // Best-effort must stay brief: a wedged dpkg (stale lock, prompt)
        // must not eat the whole 10-minute command timeout in silence.
        val healed = withTimeoutOrNull(120_000L) {
            runCatching { run(listOf("dpkg", "--configure", "-a")) }.getOrNull()
        }
        onLog((healed?.output ?: "heal skipped: runner failed or timed out").takeLast(1500))
    }

    private fun needsHeal(output: String): Boolean =
        output.contains("dpkg was interrupted", ignoreCase = true)

    /**
     * Kills leftover apt/dpkg writers from crashed earlier runs (orphaned
     * `apt-get`/`dpkg`/`unattended-upgrade` processes keep holding the
     * frontend lock forever, so every later install hangs to its timeout).
     * Read-only `dpkg-query` is never touched — the Tools screen polls it
     * concurrently. Best-effort: no ps/grep in the guest, no kill, just logs.
     */
    private suspend fun clearStaleApt(
        run: suspend (List<String>) -> GuestCmdResult,
        onLog: suspend (String) -> Unit
    ) {
        val ps = runCatching {
            // Lines look like "  123 apt-get": match the comm column, never
            // the pid column (anchoring at line start would match nothing).
            // comm truncates to 15 chars, hence "unattended-upg".
            run(listOf("sh", "-c", "ps -eo pid,comm | grep -E ' (apt-get|apt|dpkg|unattended-upg)$' || true"))
        }.getOrNull() ?: return
        val pids = ps.output.lineSequence()
            .mapNotNull { line ->
                line.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
            }
            .filter { it > 1 }
            .distinct()
            .toList()
        if (pids.isEmpty()) return
        onLog("clearing ${pids.size} stale apt/dpkg process(es): ${pids.joinToString(",")}")
        runCatching { run(listOf("kill", "-9") + pids.map { it.toString() }) }
        delay(2000L)
    }

    /**
     * Transient lock contention (a sibling install's dpkg still running) is
     * NOT an interrupted state: never "heal" it, just wait. Matches the apt
     * lock errors verbatim so genuine failures still return immediately.
     */
    private fun isLockContention(output: String): Boolean =
        output.contains("could not get lock", ignoreCase = true) ||
            output.contains("unable to acquire the dpkg frontend lock", ignoreCase = true) ||
            output.contains("is another process using it", ignoreCase = true)

    /**
     * Runs one apt/dpkg command, waiting through transient locks held by a
     * sibling install instead of failing. Non-lock failures return at once,
     * so error handling above (heal/retry/false) behaves exactly as before.
     */
    private suspend fun runApt(
        run: suspend (List<String>) -> GuestCmdResult,
        args: List<String>,
        onLog: suspend (String) -> Unit,
        maxWaitTries: Int = 6
    ): GuestCmdResult {
        var attempt = 0
        while (true) {
            val result = run(args)
            if (result.code == 0 || !isLockContention(result.output) || attempt >= maxWaitTries) {
                return result
            }
            attempt++
            val waitSec = 5L * attempt
            onLog("apt lock held by another process — waiting ${waitSec}s (try $attempt/$maxWaitTries)")
            delay(waitSec * 1000L)
        }
    }

    suspend fun install(
        run: suspend (List<String>) -> GuestCmdResult,
        def: ToolDef,
        onLog: suspend (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog("$ apt-get install ${def.debianPkg}")
        clearStaleApt(run, onLog)
        healDpkg(run, onLog)
        val update = runApt(run, listOf("apt-get", "update"), onLog)
        onLog(update.output.takeLast(1500))
        if (update.code != 0) {
            if (needsHeal(update.output)) {
                healDpkg(run, onLog)
                val retry = runApt(run, listOf("apt-get", "update"), onLog)
                onLog(retry.output.takeLast(1500))
                if (retry.code != 0) {
                    onLog("✗ ${def.label} install failed: package lists won't update (exit ${retry.code}) — check network, then retry")
                    return@withContext false
                }
            } else {
                onLog("✗ ${def.label} install failed: package lists won't update (exit ${update.code}) — check network, then retry")
                return@withContext false
            }
        }
        val result = runApt(
            run,
            // confdef/conold: never stop on a conffile prompt (stdin is
            // closed and DEBIAN_FRONTEND is noninteractive, belt and braces).
            listOf(
                "apt-get", "install", "-y",
                "-o", "Dpkg::Options::=--force-confdef",
                "-o", "Dpkg::Options::=--force-confold",
                def.debianPkg
            ),
            onLog
        )
        onLog(result.output.takeLast(3000))
        if (result.code == 0) {
            onLog("✓ ${def.label} installed")
            return@withContext true
        }
        if (result.code == 124) {
            onLog("✗ ${def.label} install timed out (exit 124) — stale lock or stalled download, retry from the Tools row")
            return@withContext false
        }
        if (needsHeal(result.output)) {
            onLog("Interrupted dpkg state detected — repairing and retrying once")
            healDpkg(run, onLog)
            val retry = runApt(
                run,
                listOf(
                    "apt-get", "install", "-y",
                    "-o", "Dpkg::Options::=--force-confdef",
                    "-o", "Dpkg::Options::=--force-confold",
                    def.debianPkg
                ),
                onLog
            )
            onLog(retry.output.takeLast(3000))
            if (retry.code == 0) onLog("✓ ${def.label} installed")
            else onLog("✗ ${def.label} install failed (exit ${retry.code}) — see output above")
            return@withContext retry.code == 0
        }
        onLog("✗ ${def.label} install failed (exit ${result.code}) — see output above")
        false
    }

    suspend fun remove(
        run: suspend (List<String>) -> GuestCmdResult,
        def: ToolDef,
        onLog: suspend (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog("$ apt-get remove ${def.debianPkg}")
        clearStaleApt(run, onLog)
        healDpkg(run, onLog)
        val result = runApt(run, listOf("apt-get", "remove", "-y", def.debianPkg), onLog)
        onLog(result.output.takeLast(3000))
        if (result.code == 0) {
            onLog("✓ ${def.label} removed")
            return@withContext true
        }
        if (needsHeal(result.output)) {
            onLog("Interrupted dpkg state detected — repairing and retrying once")
            healDpkg(run, onLog)
            val retry = runApt(run, listOf("apt-get", "remove", "-y", def.debianPkg), onLog)
            onLog(retry.output.takeLast(3000))
            if (retry.code == 0) onLog("✓ ${def.label} removed")
            else onLog("✗ ${def.label} removal failed (exit ${retry.code}) — see output above")
            return@withContext retry.code == 0
        }
        onLog("✗ ${def.label} removal failed (exit ${result.code}) — see output above")
        false
    }
}
