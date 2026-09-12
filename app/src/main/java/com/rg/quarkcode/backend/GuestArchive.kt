package com.rg.quarkcode.backend

import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Safe archive extraction (commons-compress): traversal-guarded, symlink-
 * deferred like a rootfs needs, with progress callbacks.
 */
object GuestArchive {

    suspend fun extractTarXz(
        input: InputStream,
        dest: File,
        onProgress: suspend (files: Long, bytes: Long) -> Unit = { _, _ -> }
    ) {
        XZCompressorInputStream(BufferedInputStream(input)).use { xz ->
            extractTar(xz, dest, onProgress)
        }
    }

    suspend fun extractTarGz(
        input: InputStream,
        dest: File,
        onProgress: suspend (files: Long, bytes: Long) -> Unit = { _, _ -> }
    ) {
        GzipCompressorInputStream(BufferedInputStream(input)).use { gz ->
            extractTar(gz, dest, onProgress)
        }
    }

    private suspend fun extractTar(
        input: InputStream,
        dest: File,
        onProgress: suspend (files: Long, bytes: Long) -> Unit
    ) {
        dest.mkdirs()
        val root = dest.canonicalFile
        val pendingLinks = mutableListOf<Pair<File, String>>()
        var files = 0L
        var bytes = 0L
        TarArchiveInputStream(input).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val target = File(dest, entry.name).canonicalFile
                require(target == root || target.path.startsWith(root.path + File.separator)) {
                    "Archive escapes destination: ${entry.name}"
                }
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isSymbolicLink -> {
                        target.parentFile?.mkdirs()
                        pendingLinks += target to entry.linkName
                    }
                    entry.isFile -> {
                        target.parentFile?.mkdirs()
                        if (target.exists()) target.delete()
                        target.outputStream().buffered().use { out -> tar.copyTo(out) }
                        val mode = entry.mode
                        target.setReadable(true, false)
                        target.setWritable(true, true)
                        if (mode and 0b001001001 != 0) target.setExecutable(true, false)
                        files++
                        bytes += entry.size
                        if (files % 500L == 0L) onProgress(files, bytes)
                    }
                }
                entry = tar.nextEntry
            }
        }
        // Deepest first so parent links resolve after their targets exist.
        pendingLinks.sortByDescending { (target, _) -> target.path.count { it == File.separatorChar } }
        for ((target, linkName) in pendingLinks) {
            runCatching {
                if (target.exists() || java.nio.file.Files.isSymbolicLink(target.toPath())) return@runCatching
                target.parentFile?.mkdirs()
                java.nio.file.Files.createSymbolicLink(target.toPath(), java.nio.file.Paths.get(linkName))
            }
        }
        onProgress(files, bytes)
    }
}
