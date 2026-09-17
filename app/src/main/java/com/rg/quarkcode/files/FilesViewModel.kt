package com.rg.quarkcode.files

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rg.quarkcode.backend.RuntimeFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** UI state for the sandboxed workspace/system browser. */
data class FilesUiState(
    val root: FilesGate.Root = FilesGate.Root.WORKSPACE,
    /** Current dir, relative to the root ("" = root). */
    val relative: String = "",
    val entries: List<FilesRepo.FileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val showHidden: Boolean = false,
    val sort: FilesRepo.SortMode = FilesRepo.SortMode.NAME,
    val searching: Boolean = false,
    val query: String = "",
    val searchHits: List<FilesRepo.FileEntry> = emptyList(),
    /** Selected entry relative paths. */
    val selection: Set<String> = emptySet(),
    /** Clipboard entry relative paths (resolved under [clipRoot]). */
    val clip: List<String> = emptyList(),
    val clipRoot: FilesGate.Root = FilesGate.Root.WORKSPACE,
    val cutMode: Boolean = false,
    val notice: String? = null,
    val createOpen: Boolean = false,
    val createIsDir: Boolean = true,
    val renameTarget: String? = null,
    val deleteTargets: List<String> = emptyList(),
    val propsTarget: String? = null,
    val sortOpen: Boolean = false,
    val menuOpen: Boolean = false,
    val textTarget: String? = null,
    val textBody: String? = null,
    val textEditable: Boolean = false,
    val textSaving: Boolean = false,
    val zipTarget: String? = null,
    val zipEntries: List<FilesRepo.ZipNode> = emptyList(),
    val zipBusy: Boolean = false,
    val imageTarget: String? = null,
    val imageFile: File? = null,
    /** SAF "save to phone" flow: workspace source + suggested name. */
    val exportSource: String? = null,
    val exportName: String = ""
)

/**
 * Browser logic. Every path that touches disk is resolved through
 * [FilesGate]; writes are only attempted on WORKSPACE.
 */
class FilesViewModel(application: Application) : AndroidViewModel(application) {

    var uiState by mutableStateOf(FilesUiState())
        private set

    private val app = application
    private var store: FilesStore? = null
    private var opened = false
    private var searchJob: Job? = null

    fun open() {
        if (opened) {
            refresh()
            return
        }
        opened = true
        viewModelScope.launch {
            val s = FilesStore(app).also { store = it }
            val hidden = withContext(Dispatchers.IO) { s.showHidden.first() }
            val sort = withContext(Dispatchers.IO) { s.sort.first() }
            val last = withContext(Dispatchers.IO) { s.lastPath.first() }
            RuntimeFiles.ensureDirs(app)
            uiState = uiState.copy(showHidden = hidden, sort = sort, relative = last)
            refresh()
        }
    }

    private fun rootDir(root: FilesGate.Root): File =
        FilesGate.rootDir(app, root)

    fun refresh() {
        val snap = uiState
        viewModelScope.launch {
            uiState = snap.copy(loading = true, error = null)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (snap.root == FilesGate.Root.GUEST && !FilesGate.guestInstalled(app)) {
                        return@runCatching Pair(emptyList(), "missing")
                    }
                    val dir = FilesGate.resolve(app, snap.root, snap.relative)
                        ?: return@runCatching Pair(emptyList(), "blocked")
                    Pair(
                        FilesRepo.list(rootDir(snap.root), snap.relative, snap.showHidden, snap.sort),
                        if (dir.exists()) null else "gone"
                    )
                }.getOrElse { Pair(emptyList(), "error") }
            }
            val (items, problem) = result
            val message = when (problem) {
                "missing" -> "Debian system isn't installed yet — enable it in Settings → Device."
                "blocked" -> "Blocked: path escapes the sandbox."
                "gone" -> "Folder no longer exists."
                "error" -> "Couldn't list this folder."
                else -> null
            }
            uiState = uiState.copy(loading = false, entries = items, error = message)
        }
    }

    // --- navigation ---

    fun switchRoot(root: FilesGate.Root) {
        if (root == uiState.root) return
        searchJob?.cancel()
        val last = if (root == FilesGate.Root.WORKSPACE) {
            runCatching {
                kotlinx.coroutines.runBlocking {
                    withContext(Dispatchers.IO) { store?.lastPath?.first() ?: "" }
                }
            }.getOrDefault("")
        } else ""
        uiState = uiState.copy(
            root = root, relative = last, selection = emptySet(),
            searching = false, query = "", searchHits = emptyList(), error = null
        )
        refresh()
    }

    fun navigate(relative: String) {
        searchJob?.cancel()
        uiState = uiState.copy(
            relative = relative.trim('/'), selection = emptySet(),
            searching = false, query = "", searchHits = emptyList()
        )
        if (uiState.root == FilesGate.Root.WORKSPACE) {
            val rel = relative.trim('/')
            viewModelScope.launch(Dispatchers.IO) { store?.setLastPath(rel) }
        }
        refresh()
    }

    fun goCrumb(index: Int) {
        // index -1 = root; else segment index.
        val segs = FilesRepo.segments(uiState.relative)
        val target = if (index < 0) "" else segs.take(index + 1).joinToString("/")
        navigate(target)
    }

    // --- prefs ---

    fun setShowHidden(value: Boolean) {
        uiState = uiState.copy(showHidden = value, menuOpen = false)
        viewModelScope.launch(Dispatchers.IO) { store?.setShowHidden(value) }
        refresh()
    }

    fun setSort(mode: FilesRepo.SortMode) {
        uiState = uiState.copy(sort = mode, sortOpen = false)
        viewModelScope.launch(Dispatchers.IO) { store?.setSort(mode) }
        refresh()
    }

    fun setMenu(open: Boolean) { uiState = uiState.copy(menuOpen = open) }
    fun setSortOpen(open: Boolean) { uiState = uiState.copy(sortOpen = open) }

    // --- search ---

    fun setSearching(active: Boolean) {
        searchJob?.cancel()
        uiState = uiState.copy(
            searching = active,
            query = if (active) uiState.query else "",
            searchHits = if (active) uiState.searchHits else emptyList()
        )
        if (active && uiState.query.isNotBlank()) runSearch(uiState.query)
    }

    fun setQuery(query: String) {
        uiState = uiState.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            uiState = uiState.copy(searchHits = emptyList())
            return
        }
        runSearch(query)
    }

    private fun runSearch(query: String) {
        val snap = uiState
        searchJob = viewModelScope.launch {
            delay(300L)
            val hits = withContext(Dispatchers.IO) {
                if (snap.root == FilesGate.Root.GUEST && !FilesGate.guestInstalled(app)) {
                    emptyList()
                } else {
                    FilesRepo.search(rootDir(snap.root), query)
                }
            }
            if (uiState.query == query) uiState = uiState.copy(searchHits = hits)
        }
    }

    // --- selection + clipboard ---

    fun toggleSelect(relative: String) {
        val sel = uiState.selection.toMutableSet()
        if (!sel.add(relative)) sel.remove(relative)
        uiState = uiState.copy(selection = sel)
    }

    fun selectAll() {
        val all = (if (uiState.searching) uiState.searchHits else uiState.entries)
            .map { it.relative }.toSet()
        uiState = uiState.copy(selection = all)
    }

    fun clearSelection() { uiState = uiState.copy(selection = emptySet()) }

    fun copySelection() {
        if (uiState.selection.isEmpty()) return
        uiState = uiState.copy(
            clip = uiState.selection.toList(), clipRoot = uiState.root,
            cutMode = false, selection = emptySet(),
            notice = "${uiState.selection.size} item(s) copied — open a Workspace folder to paste"
        )
    }

    fun cutSelection() {
        if (uiState.selection.isEmpty()) return
        if (uiState.root != FilesGate.Root.WORKSPACE) {
            uiState = uiState.copy(
                selection = emptySet(),
                notice = "System is read-only — copy instead of move"
            )
            return
        }
        uiState = uiState.copy(
            clip = uiState.selection.toList(), clipRoot = uiState.root,
            cutMode = true, selection = emptySet(),
            notice = "${uiState.selection.size} item(s) cut — open a Workspace folder to paste"
        )
    }

    fun clearClip() { uiState = uiState.copy(clip = emptyList(), cutMode = false) }

    fun paste() {
        val snap = uiState
        if (snap.clip.isEmpty()) return
        if (snap.root != FilesGate.Root.WORKSPACE) {
            uiState = snap.copy(notice = "Paste only works inside Workspace (System is read-only)")
            return
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val dstDir = FilesGate.resolve(app, FilesGate.Root.WORKSPACE, snap.relative)
                    ?: return@withContext "Blocked: destination escapes the sandbox."
                if (!dstDir.isDirectory) return@withContext "Destination isn't a folder."
                val clipBase = rootDir(snap.clipRoot)
                var done = 0
                var failed = 0
                for (rel in snap.clip) {
                    val src = FilesGate.resolve(app, snap.clipRoot, rel)
                    if (src == null || !src.exists()) {
                        failed++
                        continue
                    }
                    val dst = freeName(dstDir, src.name)
                    val ok = if (snap.cutMode) {
                        FilesRepo.moveRec(clipBase, src, dst)
                    } else {
                        FilesRepo.copyRec(clipBase, src, dst)
                    }
                    if (ok) done++ else failed++
                }
                if (snap.cutMode) withContext(Dispatchers.Main) { clearClip() }
                when {
                    done > 0 && failed == 0 -> "Pasted $done item(s)"
                    done > 0 -> "Pasted $done, $failed failed"
                    else -> "Paste failed"
                }
            }
            uiState = uiState.copy(notice = outcome)
            refresh()
        }
    }

    private fun freeName(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (candidate.exists()) {
            n++
            candidate = File(dir, "$stem (copy $n)$ext")
            if (n > 999) break
        }
        return candidate
    }

    // --- create / rename / delete ---

    fun setCreate(open: Boolean, isDir: Boolean = true) {
        uiState = uiState.copy(createOpen = open, createIsDir = isDir)
    }

    fun confirmCreate(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            uiState = uiState.copy(createOpen = false, notice = "Name is empty")
            return
        }
        val snap = uiState
        uiState = snap.copy(createOpen = false)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val ws = rootDir(FilesGate.Root.WORKSPACE)
                val parent = FilesGate.resolve(app, FilesGate.Root.WORKSPACE, snap.relative)
                    ?: return@withContext false
                if (snap.createIsDir) FilesRepo.mkdir(ws, parent, clean)
                else FilesRepo.createFile(ws, parent, clean)
            }
            uiState = uiState.copy(notice = if (ok) "Created $clean" else "Couldn't create $clean")
            refresh()
        }
    }

    fun setRename(target: String?) { uiState = uiState.copy(renameTarget = target) }

    fun confirmRename(newName: String) {
        val target = uiState.renameTarget ?: return
        val snap = uiState
        uiState = snap.copy(renameTarget = null)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val ws = rootDir(FilesGate.Root.WORKSPACE)
                val src = FilesGate.resolve(app, FilesGate.Root.WORKSPACE, target)
                    ?: return@withContext false
                FilesRepo.rename(ws, src, newName)
            }
            uiState = uiState.copy(notice = if (ok) "Renamed" else "Couldn't rename")
            refresh()
        }
    }

    fun setDelete(targets: List<String>) { uiState = uiState.copy(deleteTargets = targets) }

    fun confirmDelete() {
        val targets = uiState.deleteTargets
        if (targets.isEmpty()) return
        uiState = uiState.copy(deleteTargets = emptyList(), selection = emptySet())
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val ws = rootDir(FilesGate.Root.WORKSPACE)
                var done = 0
                var failed = 0
                for (rel in targets) {
                    val f = FilesGate.resolve(app, FilesGate.Root.WORKSPACE, rel)
                    if (f != null && FilesRepo.deleteRec(ws, f)) done++ else failed++
                }
                when {
                    done > 0 && failed == 0 -> "Deleted $done item(s)"
                    done > 0 -> "Deleted $done, $failed failed"
                    else -> "Delete failed"
                }
            }
            uiState = uiState.copy(notice = outcome)
            refresh()
        }
    }

    fun setProps(target: String?) { uiState = uiState.copy(propsTarget = target) }

    // --- text viewer ---

    fun openText(relative: String) {
        val snap = uiState
        viewModelScope.launch {
            uiState = snap.copy(textTarget = relative, textBody = null, textEditable = false)
            val loaded = withContext(Dispatchers.IO) {
                val file = FilesGate.resolve(app, snap.root, relative) ?: return@withContext null
                FilesRepo.readText(file)
            }
            if (loaded == null) {
                uiState = uiState.copy(
                    textTarget = null,
                    notice = "Can't preview this file (too big or binary)"
                )
            } else {
                uiState = uiState.copy(
                    textBody = loaded,
                    textEditable = snap.root == FilesGate.Root.WORKSPACE
                )
            }
        }
    }

    fun editText(body: String) { uiState = uiState.copy(textBody = body) }

    fun saveText() {
        val target = uiState.textTarget ?: return
        val body = uiState.textBody ?: return
        uiState = uiState.copy(textSaving = true)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val ws = rootDir(FilesGate.Root.WORKSPACE)
                val file = FilesGate.resolve(app, FilesGate.Root.WORKSPACE, target)
                    ?: return@withContext false
                if (!FilesGate.contains(ws, file) || file.isDirectory) return@withContext false
                runCatching {
                    if (body.toByteArray(Charsets.UTF_8).size > FilesRepo.MAX_TEXT_BYTES) return@withContext false
                    file.writeText(body)
                    true
                }.getOrDefault(false)
            }
            uiState = uiState.copy(
                textSaving = false,
                notice = if (ok) "Saved" else "Couldn't save"
            )
            refresh()
        }
    }

    fun closeText() {
        uiState = uiState.copy(textTarget = null, textBody = null, textSaving = false)
    }

    // --- zip viewer ---

    fun openZip(relative: String) {
        val snap = uiState
        viewModelScope.launch {
            uiState = snap.copy(zipTarget = relative, zipEntries = emptyList(), zipBusy = true)
            val items = withContext(Dispatchers.IO) {
                val file = FilesGate.resolve(app, snap.root, relative) ?: return@withContext emptyList()
                FilesRepo.zipEntries(file)
            }
            uiState = uiState.copy(zipEntries = items, zipBusy = false)
        }
    }

    fun closeZip() { uiState = uiState.copy(zipTarget = null, zipEntries = emptyList()) }

    fun extractZip(onlyEntry: String? = null) {
        val zipRel = uiState.zipTarget ?: return
        val snap = uiState
        viewModelScope.launch {
            uiState = snap.copy(zipBusy = true)
            val outcome = withContext(Dispatchers.IO) {
                val ws = rootDir(FilesGate.Root.WORKSPACE)
                val zipFile = FilesGate.resolve(app, snap.root, zipRel)
                    ?: return@withContext "Blocked: archive escapes the sandbox."
                val destBase = FilesGate.resolve(app, FilesGate.Root.WORKSPACE, snap.relative)
                    ?.takeIf { snap.root == FilesGate.Root.WORKSPACE }
                    ?: ws
                val folder = zipFile.nameWithoutExtension.ifBlank { "extracted" }
                val dest = freeName(destBase, folder).apply { mkdirs() }
                val count = FilesRepo.extractZip(ws, zipFile, dest, onlyEntry)
                if (count < 0) "Extract failed" else "Extracted $count file(s) to ${dest.name}"
            }
            uiState = uiState.copy(zipBusy = false, notice = outcome)
        }
    }

    // --- image viewer ---

    fun openImage(relative: String) {
        val snap = uiState
        val file = FilesGate.resolve(app, snap.root, relative)
        if (file == null || !file.isFile) {
            uiState = snap.copy(notice = "Blocked: path escapes the sandbox.")
            return
        }
        uiState = snap.copy(imageTarget = relative, imageFile = file)
    }

    fun closeImage() { uiState = uiState.copy(imageTarget = null, imageFile = null) }

    fun clearNotice() { uiState = uiState.copy(notice = null) }

    // --- SAF import (phone → workspace; the ONLY way in) ---

    /** Copies SAF-picked documents into the current workspace folder. */
    fun importUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val snap = uiState
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val ws = rootDir(FilesGate.Root.WORKSPACE)
                ws.mkdirs()
                val dstDir = if (snap.root == FilesGate.Root.WORKSPACE) {
                    FilesGate.resolve(app, FilesGate.Root.WORKSPACE, snap.relative)
                        ?.takeIf { it.isDirectory }
                } else {
                    null
                } ?: ws
                var done = 0
                var failed = 0
                val resolver = app.contentResolver
                for (uri in uris) {
                    val ok = runCatching {
                        val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                            val idx = cursor.getColumnIndex(
                                android.provider.OpenableColumns.DISPLAY_NAME
                            )
                            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                        } ?: "import-${System.currentTimeMillis()}"
                        val safe = displayName.substringAfterLast('/').trim().takeIf { it.isNotEmpty() }
                            ?: "import-${System.currentTimeMillis()}"
                        val dst = freeName(dstDir, safe)
                        resolver.openInputStream(uri)?.use { ins ->
                            dst.outputStream().use { outs -> ins.copyTo(outs) }
                        } ?: return@runCatching false
                        // Refuse to land outside the sandbox even if freeName glitched.
                        FilesGate.contains(ws.canonicalFile, dst.canonicalFile)
                    }.getOrDefault(false)
                    if (ok) done++ else failed++
                }
                when {
                    done > 0 && failed == 0 -> "Imported $done file(s) into Workspace"
                    done > 0 -> "Imported $done, $failed failed"
                    else -> "Import failed"
                }
            }
            uiState = uiState.copy(notice = outcome)
            refresh()
        }
    }

    // --- SAF export (workspace → phone; user-driven only) ---

    fun setExport(source: String?) {
        if (source == null) {
            uiState = uiState.copy(exportSource = null, exportName = "")
            return
        }
        uiState = uiState.copy(exportSource = source, exportName = source.substringAfterLast('/'))
    }

    fun exportTo(uri: android.net.Uri?) {
        val source = uiState.exportSource
        uiState = uiState.copy(exportSource = null, exportName = "")
        if (source == null || uri == null) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val ws = rootDir(FilesGate.Root.WORKSPACE)
                val src = FilesGate.resolve(app, uiState.root, source)
                    ?.takeIf { it.isFile }
                    ?: return@withContext false
                // Workspace-only: guest system files can never leave the sandbox.
                if (!FilesGate.contains(ws.canonicalFile, src.canonicalFile)) return@withContext false
                runCatching {
                    app.contentResolver.openOutputStream(uri)?.use { outs ->
                        src.inputStream().use { ins -> ins.copyTo(outs) }
                    } ?: return@withContext false
                    true
                }.getOrDefault(false)
            }
            uiState = uiState.copy(notice = if (ok) "Saved to phone" else "Couldn't save file")
        }
    }

    // --- share / open-with (workspace files only) ---

    fun shareSelection() {
        val snap = uiState
        val ws = rootDir(FilesGate.Root.WORKSPACE)
        val files = snap.selection.mapNotNull { rel ->
            FilesGate.resolve(app, snap.root, rel)?.takeIf { it.isFile }
        }
        if (!ShareKit.share(app, ws, files)) {
            uiState = snap.copy(notice = "Nothing shareable — only Workspace files can leave the sandbox")
        } else {
            uiState = snap.copy(selection = emptySet())
        }
    }

    fun shareFile(relative: String) {
        val snap = uiState
        val ws = rootDir(FilesGate.Root.WORKSPACE)
        val file = FilesGate.resolve(app, snap.root, relative)?.takeIf { it.isFile }
        if (file == null || !ShareKit.share(app, ws, listOf(file))) {
            uiState = snap.copy(notice = "Only Workspace files can leave the sandbox")
        }
    }

    fun openWith(relative: String) {
        val snap = uiState
        val ws = rootDir(FilesGate.Root.WORKSPACE)
        val file = FilesGate.resolve(app, snap.root, relative)?.takeIf { it.isFile }
        if (file == null || !ShareKit.openWith(app, ws, file)) {
            uiState = snap.copy(notice = "Only Workspace files can be opened outside the sandbox")
        }
    }
}
