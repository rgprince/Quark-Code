package com.rg.quarkcode.files

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

private fun fmtDate(ts: Long): String =
    runCatching { dateFmt.format(Date(ts)) }.getOrDefault("")

// Sandboxed browser: Workspace (read/write, = /workspace for the agent)
// and System (read-only Debian rootfs view). State + callbacks, Quark style.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    state: FilesUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onSwitchRoot: (FilesGate.Root) -> Unit,
    onNavigate: (String) -> Unit,
    onCrumb: (Int) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onSortOpen: () -> Unit,
    onSort: (FilesRepo.SortMode) -> Unit,
    onSortDismiss: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onSearching: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onClearClip: () -> Unit,
    onCreate: (Boolean, Boolean) -> Unit,
    onCreateConfirm: (String) -> Unit,
    onRename: (String?) -> Unit,
    onRenameConfirm: (String) -> Unit,
    onDelete: (List<String>) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onProps: (String?) -> Unit,
    onTextOpen: (String) -> Unit,
    onTextEdit: (String) -> Unit,
    onTextSave: () -> Unit,
    onTextClose: () -> Unit,
    onZipOpen: (String) -> Unit,
    onZipClose: () -> Unit,
    onZipExtract: (String?) -> Unit,
    onImageOpen: (String) -> Unit,
    onImageClose: () -> Unit,
    onNoticeShown: () -> Unit,
    onImport: (List<Uri>) -> Unit,
    onExportRequest: (String) -> Unit,
    onExportResult: (Uri?) -> Unit,
    onShareSelection: () -> Unit,
    onShareFile: (String) -> Unit,
    onOpenWith: (String) -> Unit
) {
    LaunchedEffect(Unit) { onOpen() }
    when {
        state.textTarget != null -> TextViewer(
            name = state.textTarget.substringAfterLast('/'),
            body = state.textBody,
            editable = state.textEditable,
            saving = state.textSaving,
            onEdit = onTextEdit,
            onSave = onTextSave,
            onClose = onTextClose
        )
        state.zipTarget != null -> ZipViewer(
            name = state.zipTarget.substringAfterLast('/'),
            entries = state.zipEntries,
            busy = state.zipBusy,
            onClose = onZipClose,
            onExtractAll = { onZipExtract(null) },
            onExtractEntry = { onZipExtract(it) }
        )
        state.imageTarget != null -> ImageViewer(
            name = state.imageTarget.substringAfterLast('/'),
            file = state.imageFile,
            onClose = onImageClose
        )
        else -> BrowserBody(
            state = state,
            modifier = modifier,
            onBack = onBack,
            onSwitchRoot = onSwitchRoot,
            onNavigate = onNavigate,
            onCrumb = onCrumb,
            onShowHidden = onShowHidden,
            onSortOpen = onSortOpen,
            onSort = onSort,
            onSortDismiss = onSortDismiss,
            onMenu = onMenu,
            onSearching = onSearching,
            onQuery = onQuery,
            onToggleSelect = onToggleSelect,
            onSelectAll = onSelectAll,
            onClearSelection = onClearSelection,
            onCopy = onCopy,
            onCut = onCut,
            onPaste = onPaste,
            onClearClip = onClearClip,
            onCreate = onCreate,
            onCreateConfirm = onCreateConfirm,
            onRename = onRename,
            onRenameConfirm = onRenameConfirm,
            onDelete = onDelete,
            onDeleteConfirm = onDeleteConfirm,
            onDeleteDismiss = onDeleteDismiss,
            onProps = onProps,
            onTextOpen = onTextOpen,
            onZipOpen = onZipOpen,
            onImageOpen = onImageOpen,
            onNoticeShown = onNoticeShown,
            onImport = onImport,
            onExportRequest = onExportRequest,
            onExportResult = onExportResult,
            onShareSelection = onShareSelection,
            onShareFile = onShareFile,
            onOpenWith = onOpenWith
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BrowserBody(
    state: FilesUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSwitchRoot: (FilesGate.Root) -> Unit,
    onNavigate: (String) -> Unit,
    onCrumb: (Int) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onSortOpen: () -> Unit,
    onSort: (FilesRepo.SortMode) -> Unit,
    onSortDismiss: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onSearching: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onClearClip: () -> Unit,
    onCreate: (Boolean, Boolean) -> Unit,
    onCreateConfirm: (String) -> Unit,
    onRename: (String?) -> Unit,
    onRenameConfirm: (String) -> Unit,
    onDelete: (List<String>) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onProps: (String?) -> Unit,
    onTextOpen: (String) -> Unit,
    onZipOpen: (String) -> Unit,
    onImageOpen: (String) -> Unit,
    onNoticeShown: () -> Unit,
    onImport: (List<Uri>) -> Unit,
    onExportRequest: (String) -> Unit,
    onExportResult: (Uri?) -> Unit,
    onShareSelection: () -> Unit,
    onShareFile: (String) -> Unit,
    onOpenWith: (String) -> Unit
) {
    val snacks = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snacks.showSnackbar(it)
            onNoticeShown()
        }
    }
    var fabMenu by remember { mutableStateOf(false) }
    val selecting = state.selection.isNotEmpty()
    val writable = state.root == FilesGate.Root.WORKSPACE
    // SAF launchers: no storage permission needed; the user picks exactly
    // what enters/leaves the sandbox, one transfer at a time.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImport(uris)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        onExportResult(uri)
    }
    LaunchedEffect(state.exportSource) {
        state.exportSource?.let { exportLauncher.launch(state.exportName.ifEmpty { "file" }) }
    }
    val shown = if (state.searching) state.searchHits else state.entries
    val folderName = state.relative.substringAfterLast('/').ifEmpty { FilesGate.label(state.root) }

    fun openEntry(entry: FilesRepo.FileEntry) {
        if (entry.isDir) {
            onNavigate(entry.relative)
            return
        }
        when {
            entry.extension == "zip" -> onZipOpen(entry.relative)
            FilesRepo.categoryOf(entry.extension) == FilesRepo.Category.IMAGE -> onImageOpen(entry.relative)
            FilesRepo.categoryOf(entry.extension) == FilesRepo.Category.DOC -> onTextOpen(entry.relative)
            else -> onProps(entry.relative)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snacks) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (state.searching) onSearching(false) else onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (state.searching) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQuery,
                            placeholder = { Text("Search ${FilesGate.label(state.root)}") },
                            singleLine = true,
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { onQuery("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                        )
                    } else {
                        Column {
                            Text(
                                text = if (selecting) "${state.selection.size} selected" else folderName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            if (!selecting) {
                                Text(
                                    text = FilesGate.guestPath(state.root, state.relative),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        TextButton(onClick = onSelectAll) { Text("All") }
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    } else if (!state.searching) {
                        IconButton(onClick = { onSearching(true) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onSortOpen) {
                            Icon(Icons.Filled.Sort, contentDescription = "Sort")
                        }
                        IconButton(onClick = { onMenu(true) }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = state.menuOpen,
                            onDismissRequest = { onMenu(false) }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Show hidden files") },
                                trailingIcon = {
                                    if (state.showHidden) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = { onShowHidden(!state.showHidden) }
                            )
                            if (writable) {
                                DropdownMenuItem(
                                    text = { Text("Import from phone…") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Upload, contentDescription = null)
                                    },
                                    onClick = {
                                        onMenu(false)
                                        importLauncher.launch(arrayOf("*/*"))
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (state.clip.isNotEmpty() && !state.searching && !selecting) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${state.clip.size} item(s) ${if (state.cutMode) "cut" else "copied"}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onClearClip) { Text("Cancel") }
                            if (writable) {
                                Button(onClick = onPaste) { Text("Paste") }
                            }
                        }
                    }
                }
                if (selecting) {
                    BottomAppBar {
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = onCopy) { Text("Copy") }
                        TextButton(onClick = onShareSelection) { Text("Share") }
                        if (writable) {
                            TextButton(onClick = onCut) { Text("Move") }
                            TextButton(
                                onClick = { onDelete(state.selection.toList()) }
                            ) {
                                Text(
                                    text = "Delete",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            TextButton(onClick = { onProps(state.selection.firstOrNull()) }) {
                                Text("Details")
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (writable && !state.searching && !selecting) {
                Box {
                    FloatingActionButton(onClick = { fabMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Create")
                    }
                    DropdownMenu(
                        expanded = fabMenu,
                        onDismissRequest = { fabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New folder") },
                            leadingIcon = {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                            },
                            onClick = {
                                fabMenu = false
                                onCreate(true, true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New file") },
                            leadingIcon = {
                                Icon(Icons.Filled.NoteAdd, contentDescription = null)
                            },
                            onClick = {
                                fabMenu = false
                                onCreate(true, false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import from phone…") },
                            leadingIcon = {
                                Icon(Icons.Filled.Upload, contentDescription = null)
                            },
                            onClick = {
                                fabMenu = false
                                importLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                FilesGate.Root.entries.forEachIndexed { index, root ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        selected = state.root == root,
                        onClick = { onSwitchRoot(root) },
                        label = { Text(FilesGate.label(root)) }
                    )
                }
            }
            if (!state.searching) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "root") {
                        AssistChip(
                            onClick = { onCrumb(-1) },
                            label = { Text(FilesGate.label(state.root)) }
                        )
                    }
                    val segs = FilesRepo.segments(state.relative)
                    segs.forEachIndexed { index, seg ->
                        item(key = "crumb-$index-$seg") {
                            AssistChip(
                                onClick = { onCrumb(index) },
                                label = { Text(seg) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (state.loading && shown.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (shown.isEmpty()) {
                EmptyHint(
                    text = when {
                        state.error != null -> state.error
                        state.searching -> "No matches for “${state.query}”."
                        state.relative.isEmpty() && writable -> "Workspace is empty — create a folder or import files."
                        else -> "Empty folder."
                    }
                )
            } else {
                if (state.error != null && !state.searching) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.relative }) { entry ->
                        val selected = state.selection.contains(entry.relative)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selecting) onToggleSelect(entry.relative)
                                        else openEntry(entry)
                                    },
                                    onLongClick = { onToggleSelect(entry.relative) }
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selecting) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { onToggleSelect(entry.relative) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            EntryIcon(entry = entry)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (entry.isDir) {
                                        "Folder · ${fmtDate(entry.lastModified)}"
                                    } else {
                                        "${FilesRepo.formatSize(entry.size)} · ${fmtDate(entry.lastModified)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (!selecting) {
                                IconButton(
                                    onClick = {
                                        if (entry.isDir) openEntry(entry)
                                        else onProps(entry.relative)
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Actions",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.sortOpen) {
        AlertDialog(
            onDismissRequest = onSortDismiss,
            title = { Text("Sort by") },
            text = {
                Column {
                    FilesRepo.SortMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { onSort(mode) }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.sort == mode,
                                onClick = { onSort(mode) }
                            )
                            Text(
                                text = mode.name.lowercase(Locale.getDefault())
                                    .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onSortDismiss) { Text("Close") }
            }
        )
    }

    if (state.createOpen) {
        var name by remember(state.createIsDir) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { onCreate(false, state.createIsDir) },
            title = { Text(if (state.createIsDir) "New folder" else "New file") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { onCreateConfirm(name) }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { onCreate(false, state.createIsDir) }) { Text("Cancel") }
            }
        )
    }

    state.renameTarget?.let { target ->
        var name by remember(target) {
            mutableStateOf(target.substringAfterLast('/'))
        }
        AlertDialog(
            onDismissRequest = { onRename(null) },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { onRenameConfirm(name) }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { onRename(null) }) { Text("Cancel") }
            }
        )
    }

    if (state.deleteTargets.isNotEmpty()) {
        val preview = state.deleteTargets.take(5)
            .joinToString("\n") { "· " + it.substringAfterLast('/') }
        val extra = state.deleteTargets.size - preview.lines().size
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text("Delete ${state.deleteTargets.size} item(s)?") },
            text = {
                Text(
                    text = preview + if (extra > 0) "\n· …and $extra more" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = onDeleteConfirm) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) { Text("Cancel") }
            }
        )
    }

    state.propsTarget?.let { target ->
        val entry = shown.firstOrNull { it.relative == target }
            ?: state.entries.firstOrNull { it.relative == target }
        AlertDialog(
            onDismissRequest = { onProps(null) },
            title = { Text(target.substringAfterLast('/')) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PropRow("Location", FilesGate.guestPath(state.root, target))
                    if (entry != null) {
                        PropRow(
                            "Type",
                            if (entry.isDir) "Folder" else FilesRepo.mimeOf(
                                java.io.File(entry.name)
                            )
                        )
                        if (!entry.isDir) {
                            PropRow("Size", FilesRepo.formatSize(entry.size))
                        }
                        PropRow("Modified", fmtDate(entry.lastModified))
                    }
                    if (writable) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                onProps(null)
                                onRename(target)
                            }) { Text("Rename") }
                            OutlinedButton(onClick = {
                                onProps(null)
                                onDelete(listOf(target))
                            }) { Text("Delete") }
                        }
                    }
                    val isFile = entry?.isDir == false
                    if (isFile) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                onProps(null)
                                onShareFile(target)
                            }) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share")
                            }
                            OutlinedButton(onClick = {
                                onProps(null)
                                onExportRequest(target)
                            }) {
                                Icon(
                                    Icons.Filled.SaveAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                        if (writable) {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(onClick = {
                                onProps(null)
                                onOpenWith(target)
                            }) { Text("Open with…") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onProps(null) }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun PropRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EntryIcon(entry: FilesRepo.FileEntry, modifier: Modifier = Modifier) {
    val (icon, tint) = entryIcon(entry)
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(28.dp)
    )
}

@Composable
private fun entryIcon(entry: FilesRepo.FileEntry): Pair<ImageVector, Color> {
    val scheme = MaterialTheme.colorScheme
    if (entry.isDir) return Icons.Filled.Folder to scheme.primary
    return when (FilesRepo.categoryOf(entry.extension)) {
        FilesRepo.Category.IMAGE -> Icons.Filled.Image to scheme.tertiary
        FilesRepo.Category.VIDEO -> Icons.Filled.Movie to scheme.tertiary
        FilesRepo.Category.AUDIO -> Icons.Filled.AudioFile to scheme.tertiary
        FilesRepo.Category.ARCHIVE -> Icons.Filled.Archive to scheme.secondary
        FilesRepo.Category.APP -> Icons.Filled.Android to scheme.secondary
        FilesRepo.Category.DOC -> when (entry.extension) {
            "pdf" -> Icons.Filled.PictureAsPdf to scheme.secondary
            "kt", "java", "py", "js", "ts", "json", "xml", "html", "css",
            "c", "cpp", "h", "sh", "yml", "yaml", "toml" -> Icons.Filled.Code to scheme.secondary
            else -> Icons.Filled.Description to scheme.onSurfaceVariant
        }
        FilesRepo.Category.OTHER -> Icons.Filled.Description to scheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextViewer(
    name: String,
    body: String?,
    editable: Boolean,
    saving: Boolean,
    onEdit: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            text = if (editable) "Editable" else "Read-only (System)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (editable) {
                        IconButton(onClick = onSave, enabled = !saving) {
                            Icon(Icons.Filled.Save, contentDescription = "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (body == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            BasicTextField(
                value = body,
                onValueChange = { if (editable) onEdit(it) },
                readOnly = !editable,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZipViewer(
    name: String,
    entries: List<FilesRepo.ZipNode>,
    busy: Boolean,
    onClose: () -> Unit,
    onExtractAll: () -> Unit,
    onExtractEntry: (String) -> Unit
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                },
                actions = {
                    TextButton(onClick = onExtractAll, enabled = !busy) { Text("Extract") }
                }
            )
        }
    ) { padding ->
        if (busy && entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (entries.isEmpty()) {
            EmptyHint(
                text = "Archive is empty or unreadable.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(entries, key = { it.name }) { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { if (!node.isDir) onExtractEntry(node.name) }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (node.isDir) Icons.Filled.Folder else Icons.Filled.Description,
                            contentDescription = null,
                            tint = if (node.isDir) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                            if (!node.isDir) {
                                Text(
                                    text = FilesRepo.formatSize(node.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!node.isDir) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Extract",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewer(
    name: String,
    file: java.io.File?,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            title = {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1
                )
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }
}
