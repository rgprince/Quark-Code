package com.rg.quarkcode.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Share/export helpers. Sandbox rule: only files inside the agent
 * workspace can ever leave the app — [uriFor] returns null for anything
 * else (guest system files, absolute outsiders), so SHARE and OPEN-WITH
 * intents can never exfiltrate outside the sandbox.
 */
object ShareKit {

    private fun authority(context: Context): String = "${context.packageName}.provider"

    /** Content URI for a workspace file, or null when outside the sandbox. */
    fun uriFor(context: Context, workspace: File, file: File): Uri? {
        val target = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val base = runCatching { workspace.canonicalFile }.getOrNull() ?: return null
        if (!FilesGate.contains(base, target)) return null
        if (!target.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(context, authority(context), target)
        }.getOrNull()
    }

    fun share(context: Context, workspace: File, files: List<File>): Boolean {
        val pairs = files.mapNotNull { file -> uriFor(context, workspace, file)?.let { file to it } }
        if (pairs.isEmpty()) return false
        val intent = if (pairs.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = FilesRepo.mimeOf(pairs.first().first)
                putExtra(Intent.EXTRA_STREAM, pairs.first().second)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(pairs.map { it.second })
                )
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(
                Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return true
    }

    fun openWith(context: Context, workspace: File, file: File): Boolean {
        val uri = uriFor(context, workspace, file) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, FilesRepo.mimeOf(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }
}
