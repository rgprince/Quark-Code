package com.rg.quarkcode.files

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.rg.quarkcode.backend.LocalBackend
import com.rg.quarkcode.backend.RuntimeFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class SandboxFacts(
    val workspacePath: String,
    val guestPath: String,
    val workspaceBytes: Long,
    val guestBytes: Long
)

/**
 * Sandbox status: proves the confinement story at a glance. Everything
 * stated here mirrors LocalBackend (binds, workdir, loopback server) and
 * FilesGate/ShareKit (workspace-only writes and shares).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var facts by remember { mutableStateOf<SandboxFacts?>(null) }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val ws = RuntimeFiles.workspace(context)
            val guest = RuntimeFiles.guest(context)
            SandboxFacts(
                workspacePath = ws.absolutePath,
                guestPath = guest.absolutePath,
                workspaceBytes = dirBytes(ws),
                guestBytes = dirBytes(guest)
            )
        }
        facts = loaded
    }
    val infoText = buildString {
        appendLine("Quark Code sandbox")
        appendLine("Agent sees: /workspace (read/write), / (Debian, read), /dev, /proc")
        appendLine("Agent workdir: /workspace")
        appendLine("Server: 127.0.0.1:${LocalBackend.PORT} (loopback only, password-gated)")
        appendLine("Files enter: user SAF import into Workspace only")
        appendLine("Files leave: user Share / Save-to-phone from Workspace only")
        facts?.let {
            appendLine("Workspace: ${it.workspacePath} (${FilesRepo.formatSize(it.workspaceBytes)})")
            appendLine("Guest: ${it.guestPath} (${FilesRepo.formatSize(it.guestBytes)})")
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Sandbox") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "top-space") { Spacer(modifier = Modifier.height(4.dp)) }
            item(key = "status") {
                SandboxCard(title = "Confined", icon = Icons.Filled.Lock) {
                    Text(
                        text = "The agent runs inside the Debian guest and sees only " +
                            "/workspace plus the guest system. Your phone storage is " +
                            "unreachable to it — there is no bind, no permission, no path.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "sees") {
                SandboxCard(title = "What the agent sees", icon = Icons.Filled.Folder) {
                    SandboxFact("/workspace", "Agent files · read + write")
                    SandboxFact("/", "Debian system · read-only for the agent")
                    SandboxFact("/dev, /proc", "Device + process views (proot)")
                    SandboxFact("Workdir", "/workspace (server starts here)")
                    SandboxFact("Server", "127.0.0.1:${LocalBackend.PORT} · password-gated")
                }
            }
            item(key = "enter") {
                SandboxCard(title = "How files enter", icon = Icons.Filled.Download) {
                    Text(
                        text = "Only you can add files: Files → Import from phone. " +
                            "The system picker hands over exactly the documents you tap — " +
                            "nothing is scanned or synced in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "leave") {
                SandboxCard(title = "How files leave", icon = Icons.Filled.Upload) {
                    Text(
                        text = "Only you can take files out: Share, Open with…, or Save " +
                            "to phone — and only from Workspace. Guest system files are " +
                            "blocked from leaving, always.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "disk") {
                SandboxCard(title = "On this phone", icon = Icons.Filled.Info) {
                    val f = facts
                    if (f == null) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        SandboxFact("Workspace", "${FilesRepo.formatSize(f.workspaceBytes)}\n${f.workspacePath}")
                        Spacer(modifier = Modifier.height(4.dp))
                        SandboxFact("Guest", "${FilesRepo.formatSize(f.guestBytes)}\n${f.guestPath}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(infoText)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Copy sandbox info")
                    }
                }
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

private fun dirBytes(dir: File): Long {
    if (!dir.exists()) return 0L
    return runCatching {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)
}

@Composable
private fun SandboxCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SandboxFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
