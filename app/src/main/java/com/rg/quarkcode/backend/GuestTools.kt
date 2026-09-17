package com.rg.quarkcode.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val healed = runCatching { run(listOf("dpkg", "--configure", "-a")) }.getOrNull()
        onLog((healed?.output ?: "heal skipped: runner failed").takeLast(1500))
    }

    private fun needsHeal(output: String): Boolean =
        output.contains("dpkg was interrupted", ignoreCase = true)

    suspend fun install(
        run: suspend (List<String>) -> GuestCmdResult,
        def: ToolDef,
        onLog: suspend (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog("$ apt-get install ${def.debianPkg}")
        healDpkg(run, onLog)
        val update = run(listOf("apt-get", "update"))
        onLog(update.output.takeLast(1500))
        if (update.code != 0) {
            if (needsHeal(update.output)) {
                healDpkg(run, onLog)
                val retry = run(listOf("apt-get", "update"))
                onLog(retry.output.takeLast(1500))
                if (retry.code != 0) return@withContext false
            } else {
                return@withContext false
            }
        }
        val result = run(listOf("apt-get", "install", "-y", def.debianPkg))
        onLog(result.output.takeLast(3000))
        if (result.code == 0) return@withContext true
        if (needsHeal(result.output)) {
            onLog("Interrupted dpkg state detected — repairing and retrying once")
            healDpkg(run, onLog)
            val retry = run(listOf("apt-get", "install", "-y", def.debianPkg))
            onLog(retry.output.takeLast(3000))
            return@withContext retry.code == 0
        }
        false
    }

    suspend fun remove(
        run: suspend (List<String>) -> GuestCmdResult,
        def: ToolDef,
        onLog: suspend (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog("$ apt-get remove ${def.debianPkg}")
        healDpkg(run, onLog)
        val result = run(listOf("apt-get", "remove", "-y", def.debianPkg))
        onLog(result.output.takeLast(3000))
        if (result.code == 0) return@withContext true
        if (needsHeal(result.output)) {
            onLog("Interrupted dpkg state detected — repairing and retrying once")
            healDpkg(run, onLog)
            val retry = run(listOf("apt-get", "remove", "-y", def.debianPkg))
            onLog(retry.output.takeLast(3000))
            return@withContext retry.code == 0
        }
        false
    }
}
