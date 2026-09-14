package com.rg.quarkcode.files

import android.content.Context
import com.rg.quarkcode.backend.DebianInstaller
import com.rg.quarkcode.backend.RuntimeFiles
import java.io.File

/**
 * Sandbox gate: every host-side file operation in the Files feature must
 * resolve through here. Two roots exist, both under app-private storage:
 *
 *   WORKSPACE → files/backend/workspace  (read + write, bound as /workspace)
 *   GUEST     → files/backend/guest      (read-only Debian rootfs view)
 *
 * Nothing outside these roots is reachable: symlinks and `..` segments are
 * collapsed to canonical paths and rejected when they escape. The agent
 * itself only ever sees /workspace inside the guest, so it physically
 * cannot reach host storage — and the only way files enter the workspace
 * from outside is the user-driven SAF import in FilesScreen.
 */
object FilesGate {

    enum class Root { WORKSPACE, GUEST }

    fun rootDir(context: Context, root: Root): File = when (root) {
        Root.WORKSPACE -> RuntimeFiles.workspace(context)
        Root.GUEST -> RuntimeFiles.guest(context)
    }

    fun label(root: Root): String = when (root) {
        Root.WORKSPACE -> "Workspace"
        Root.GUEST -> "System"
    }

    /** Guest path as the agent sees it (/workspace or / inside Debian). */
    fun guestPath(root: Root, relative: String): String {
        val clean = relative.trim('/').replace('\\', '/')
        return when (root) {
            Root.WORKSPACE -> if (clean.isEmpty()) "/workspace" else "/workspace/$clean"
            Root.GUEST -> if (clean.isEmpty()) "/" else "/$clean"
        }
    }

    fun canWrite(root: Root): Boolean = root == Root.WORKSPACE

    fun guestInstalled(context: Context): Boolean =
        DebianInstaller.isInstalled(context)

    /**
     * Resolves [relative] under [root]. Returns null when the path escapes
     * the root (absolute path, `..` breakout, symlink pointing outside).
     * Empty/blank relative resolves to the root itself.
     */
    fun resolve(context: Context, root: Root, relative: String): File? {
        val base = rootDir(context, root)
        val trimmed = relative.trim().replace('\\', '/').trim('/')
        if (trimmed.isEmpty()) return base
        if (trimmed.startsWith("/")) return null
        val candidate = File(base, trimmed).canonicalFile
        return if (contains(base, candidate)) candidate else null
    }

    /** Relative path of [file] under [root], or null when outside. */
    fun relativeTo(context: Context, root: Root, file: File): String? {
        val base = rootDir(context, root).canonicalFile
        val target = file.canonicalFile
        if (!contains(base, target)) return null
        val rel = target.absolutePath.removePrefix(base.absolutePath).trim('/')
        return rel
    }

    fun contains(rootDir: File, file: File): Boolean {
        val base = rootDir.canonicalPath
        val target = file.canonicalPath
        return target == base || target.startsWith(base + File.separator)
    }
}
