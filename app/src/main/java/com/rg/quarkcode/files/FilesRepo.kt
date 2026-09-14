package com.rg.quarkcode.files

import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

/**
 * File operations for the sandboxed Files feature. All entry points take
 * gate-resolved [File]s or (rootDir + relative) pairs — never raw
 * user-supplied absolute paths. Callers resolve via [FilesGate.resolve]
 * first; functions double-check containment before any write.
 *
 * Read model (ported from the LightFM FileRepo design, rewritten for
 * Quark): list/sort/search/format/category/mime + zip list/extract via
 * framework java.util.zip (no new dependencies).
 */
object FilesRepo {

    data class FileEntry(
        val name: String,
        /** Path relative to the current root, `/`-separated, "" for root. */
        val relative: String,
        val isDir: Boolean,
        val size: Long,
        val lastModified: Long,
        val extension: String
    )

    enum class SortMode { NAME, SIZE, DATE, TYPE }

    enum class Category { IMAGE, VIDEO, AUDIO, DOC, ARCHIVE, APP, OTHER }

    data class ZipNode(val name: String, val isDir: Boolean, val size: Long)

    fun sortModeOf(raw: String?): SortMode =
        runCatching { SortMode.valueOf(raw ?: "NAME") }.getOrDefault(SortMode.NAME)

    fun toEntry(parentRelative: String, file: File): FileEntry {
        val name = file.name
        val ext = if (file.isDirectory) "" else name.substringAfterLast('.', "").lowercase(Locale.US)
        val relative = if (parentRelative.isEmpty()) name else "$parentRelative/$name"
        return FileEntry(
            name = name,
            relative = relative,
            isDir = file.isDirectory,
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            extension = ext
        )
    }

    /** Lists [relative] under [rootDir]. Empty list on any error. */
    fun list(
        rootDir: File,
        relative: String,
        showHidden: Boolean,
        sort: SortMode,
        foldersFirst: Boolean = true
    ): List<FileEntry> {
        val dir = File(rootDir, relative.trim('/')).canonicalFile
        if (!FilesGate.contains(rootDir, dir)) return emptyList()
        val raw = dir.listFiles()?.toList().orEmpty()
        var items = raw
            .filter { showHidden || !it.name.startsWith(".") }
            .map { toEntry(relative.trim('/'), it) }
        val cmp: Comparator<FileEntry> = when (sort) {
            SortMode.NAME -> compareBy { it.name.lowercase(Locale.US) }
            SortMode.SIZE -> compareByDescending { if (it.isDir) -1L else it.size }
            SortMode.DATE -> compareByDescending { it.lastModified }
            SortMode.TYPE -> compareBy({ it.extension }, { it.name.lowercase(Locale.US) })
        }
        items = if (foldersFirst) {
            items.sortedWith(compareByDescending<FileEntry> { it.isDir }.then(cmp))
        } else {
            items.sortedWith(cmp)
        }
        return items
    }

    fun segments(relative: String): List<String> =
        relative.trim('/').split('/').filter { it.isNotEmpty() }

    fun copyRec(rootDir: File, src: File, dst: File): Boolean {
        if (!FilesGate.contains(rootDir, src.canonicalFile)) return false
        if (!FilesGate.contains(rootDir, dst.canonicalFile)) return false
        return runCatching {
            if (src.isDirectory) {
                dst.mkdirs()
                src.listFiles()?.forEach { copyRec(rootDir, it, File(dst, it.name)) }
            } else {
                dst.parentFile?.mkdirs()
                src.inputStream().use { ins -> dst.outputStream().use { out -> ins.copyTo(out) } }
            }
            true
        }.getOrDefault(false)
    }

    fun moveRec(rootDir: File, src: File, dst: File): Boolean {
        if (!FilesGate.contains(rootDir, src.canonicalFile)) return false
        if (!FilesGate.contains(rootDir, dst.canonicalFile)) return false
        if (src.canonicalPath == dst.canonicalPath) return true
        return runCatching {
            dst.parentFile?.mkdirs()
            if (src.renameTo(dst)) return true
            if (!copyRec(rootDir, src, dst)) return false
            deleteRec(rootDir, src)
            true
        }.getOrDefault(false)
    }

    fun deleteRec(rootDir: File, file: File): Boolean {
        val target = file.canonicalFile
        if (!FilesGate.contains(rootDir, target)) return false
        if (target.canonicalPath == rootDir.canonicalPath) return false
        return runCatching {
            if (target.isDirectory) target.listFiles()?.forEach { deleteRec(rootDir, it) }
            target.delete()
        }.getOrDefault(false)
    }

    fun mkdir(rootDir: File, parent: File, name: String): Boolean {
        val clean = name.trim().trim('/')
        if (clean.isEmpty() || clean.contains('/')) return false
        val dir = File(parent, clean).canonicalFile
        if (!FilesGate.contains(rootDir, dir)) return false
        return runCatching { dir.mkdirs() }.getOrDefault(false)
    }

    fun createFile(rootDir: File, parent: File, name: String): Boolean {
        val clean = name.trim().trim('/')
        if (clean.isEmpty() || clean.contains('/')) return false
        val file = File(parent, clean).canonicalFile
        if (!FilesGate.contains(rootDir, file)) return false
        return runCatching {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }.getOrDefault(false)
    }

    fun rename(rootDir: File, file: File, newName: String): Boolean {
        val clean = newName.trim().trim('/')
        if (clean.isEmpty() || clean.contains('/')) return false
        val src = file.canonicalFile
        if (!FilesGate.contains(rootDir, src)) return false
        val dst = File(src.parentFile, clean).canonicalFile
        if (!FilesGate.contains(rootDir, dst)) return false
        if (dst.exists()) return false
        return runCatching { src.renameTo(dst) }.getOrDefault(false)
    }

    /** Depth-capped name search under [rootDir], 200-result cap. */
    fun search(rootDir: File, query: String, max: Int = 200): List<FileEntry> {
        val out = mutableListOf<FileEntry>()
        val q = query.lowercase(Locale.US)
        if (q.isBlank()) return out
        fun walk(dir: File, rel: String, depth: Int) {
            if (out.size >= max || depth > 6) return
            val kids = dir.listFiles() ?: return
            for (kid in kids) {
                if (out.size >= max) return
                val kidRel = if (rel.isEmpty()) kid.name else "$rel/${kid.name}"
                if (kid.name.lowercase(Locale.US).contains(q)) {
                    out += toEntry(rel, kid).copy(relative = kidRel)
                }
                if (kid.isDirectory && !kid.name.startsWith(".")) {
                    runCatching { walk(kid, kidRel, depth + 1) }
                }
            }
        }
        runCatching { walk(rootDir.canonicalFile, "", 0) }
        return out
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble() / 1024.0
        var u = 0
        while (v >= 1024 && u < units.size - 1) {
            v /= 1024
            u++
        }
        return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[u])
    }

    fun categoryOf(extension: String): Category = when (extension.lowercase(Locale.US)) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "avif" -> Category.IMAGE
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "ts", "m2ts" -> Category.VIDEO
        "mp3", "wav", "flac", "ogg", "m4a", "aac", "opus", "mid" -> Category.AUDIO
        "pdf", "txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub", "csv",
        "kt", "java", "py", "js", "ts", "json", "xml", "html", "css", "c", "cpp", "h",
        "sh", "yml", "yaml", "toml", "gradle", "properties" -> Category.DOC
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> Category.ARCHIVE
        "apk", "aab", "apks" -> Category.APP
        else -> Category.OTHER
    }

    fun mimeOf(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val ext = file.extension.lowercase(Locale.US)
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return when (categoryOf(ext)) {
            Category.IMAGE -> "image/*"
            Category.VIDEO -> "video/*"
            Category.AUDIO -> "audio/*"
            Category.DOC -> if (ext == "pdf") "application/pdf" else "text/plain"
            Category.ARCHIVE -> "application/zip"
            Category.APP -> "application/vnd.android.package-archive"
            Category.OTHER -> "*/*"
        }
    }

    // --- ZIP (framework java.util.zip; .zip read + extract only) ---

    fun isZip(file: File): Boolean =
        !file.isDirectory && file.extension.lowercase(Locale.US) == "zip"

    fun zipEntries(zipFile: File): List<ZipNode> {
        return runCatching {
            ZipFile(zipFile).use { zip ->
                zip.entries().toList().map { entry ->
                    ZipNode(name = entry.name, isDir = entry.isDirectory, size = entry.size.coerceAtLeast(0L))
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Extracts [zipFile] into [destDir]. When [onlyEntry] is set, extracts
     * just that entry. Zip-slip guarded: entries escaping [destDir] are
     * skipped. Returns extracted file count, or -1 on failure.
     */
    fun extractZip(rootDir: File, zipFile: File, destDir: File, onlyEntry: String? = null): Int {
        if (!FilesGate.contains(rootDir, destDir.canonicalFile)) return -1
        return runCatching {
            var count = 0
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().toList()
                    .filter { onlyEntry == null || it.name == onlyEntry }
                for (entry in entries) {
                    val out = File(destDir, entry.name).canonicalFile
                    if (!FilesGate.contains(destDir, out)) continue
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { ins ->
                            out.outputStream().use { outs -> ins.copyTo(outs) }
                        }
                        count++
                    }
                }
            }
            count
        }.getOrDefault(-1)
    }

    // --- Text preview (2 MB cap, binary refused) ---

    const val MAX_TEXT_BYTES = 2L * 1024L * 1024L

    /** File text, or null when missing/too big/binary. */
    fun readText(file: File): String? {
        return runCatching {
            if (!file.isFile || file.length() > MAX_TEXT_BYTES) return null
            val bytes = file.readBytes()
            if (bytes.take(8 * 1024).contains(0.toByte())) return null
            String(bytes, Charsets.UTF_8)
        }.getOrNull()
    }
}
