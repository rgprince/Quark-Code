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

    suspend fun install(
        run: suspend (List<String>) -> GuestCmdResult,
        def: ToolDef,
        onLog: suspend (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog("$ apt-get install ${def.debianPkg}")
        val update = run(listOf("apt-get", "update"))
        onLog(update.output.takeLast(1500))
        if (update.code != 0) return@withContext false
        val result = run(listOf("apt-get", "install", "-y", def.debianPkg))
        onLog(result.output.takeLast(3000))
        result.code == 0
    }

    suspend fun remove(
        run: suspend (List<String>) -> GuestCmdResult,
        def: ToolDef,
        onLog: suspend (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog("$ apt-get remove ${def.debianPkg}")
        val result = run(listOf("apt-get", "remove", "-y", def.debianPkg))
        onLog(result.output.takeLast(3000))
        result.code == 0
    }
}
