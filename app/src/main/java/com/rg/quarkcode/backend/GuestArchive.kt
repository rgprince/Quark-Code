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

    /** anomalyco/opencode ships `opencode-linux-arm64.tar.gz` (single binary). Zip kept as legacy fallback. */
    suspend fun extractZip(
        input: InputStream,
        dest: File,
        onProgress: suspend (files: Long, bytes: Long) -> Unit = { _, _ -> }
    ) {
        dest.mkdirs()
        val root = dest.canonicalFile
        var files = 0L
        var bytes = 0L
        java.util.zip.ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val target = File(dest, entry.name).canonicalFile
                require(target == root || target.path.startsWith(root.path + File.separator)) {
                    "Archive escapes destination: ${entry.name}"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    if (target.exists()) target.delete()
                    target.outputStream().buffered().use { out -> zip.copyTo(out) }
                    target.setReadable(true, false)
                    target.setWritable(true, true)
                    // The opencode binary must stay exec-able after extract.
                    if (target.name == "opencode" || !entry.name.contains('.')) {
                        target.setExecutable(true, false)
                    }
                    files++
                    bytes += entry.compressedSize.coerceAtLeast(0L)
                    if (files % 50L == 0L) onProgress(files, bytes)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        onProgress(files, bytes)
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
        // Merged-/usr rootfs (Debian trixie) is symlink-heavy: /bin, /lib,
        // /sbin are relative links (bin -> usr/bin, lib -> usr/lib). The
        // tar may also contain real files under those names if entries were
        // materialized as dirs before their link entry arrived — a stale
        // directory where a symlink belongs breaks the loader path
        // (/lib/ld-linux-aarch64.so.1) and surfaces as execve ENOENT
        // ("No such file or directory"). So always replace: delete whatever
        // is there (dir or file or stale link) and recreate the exact link.
        pendingLinks.sortByDescending { (target, _) -> target.path.count { it == File.separatorChar } }
        for ((target, linkName) in pendingLinks) {
            runCatching {
                if (java.nio.file.Files.isSymbolicLink(target.toPath())) {
                    java.nio.file.Files.deleteIfExists(target.toPath())
                } else if (target.isDirectory) {
                    // Only delete if empty — a non-empty dir means real
                    // content was extracted there; keep it and skip the link
                    // (normalizeRootfs/loader still finds usr/ originals).
                    if (target.listFiles()?.isEmpty() != false) target.deleteRecursively()
                    else continue
                } else if (target.exists()) {
                    target.delete()
                }
                if (target.exists() || java.nio.file.Files.isSymbolicLink(target.toPath())) return@runCatching
                target.parentFile?.mkdirs()
                java.nio.file.Files.createSymbolicLink(target.toPath(), java.nio.file.Paths.get(linkName))
            }
        }
        onProgress(files, bytes)
    }
}
