package com.rg.quarkcode.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rg.quarkcode.backend.BackendViewModel
import com.rg.quarkcode.backend.InstallStage
import com.rg.quarkcode.backend.LocalBackend
import com.rg.quarkcode.backend.StageState
import com.rg.quarkcode.backend.ToolRowState

/**
 * Settings → Device tab: three SEPARATE sections — (1) Debian system,
 * (2) official opencode, (3) optional tools — each with its own staged
 * progress so people always see how far each piece got.
 */
@Composable
fun DeviceBackendPanel(
    modifier: Modifier = Modifier,
    vm: BackendViewModel = viewModel()
) {
    val backendState by vm.backendState.collectAsState()
    val logs by vm.backendLogs.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!vm.supported) {
            Text(
                text = "On-device backend needs an arm64 phone — this device is not supported. Remote backends still work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            return
        }
        // Master switch + server status.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "On-device backend", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = serverStatusLine(backendState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = vm.enabled,
                onCheckedChange = vm::setEnabled,
                modifier = Modifier.semantics { contentDescription = "Enable on-device backend" }
            )
        }
        vm.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // ---- Section 1: Debian system ----
        DeviceSectionCard(title = "1 · Linux system (Debian)") {
            if (vm.debianVersion != null) {
                Text(
                    text = "Debian ${vm.debianVersion} · ${formatBytes(vm.debianBytes)} on disk",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "The Linux home the server and tools live in (~150–250 MB, Wi-Fi recommended).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StageList(stages = vm.debianStages)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.debianStages.any { it.state == StageState.ACTIVE }) {
                    OutlinedButton(
                        onClick = vm::cancelDebian,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Cancel")
                    }
                } else if (vm.debianVersion == null) {
                    Button(
                        onClick = vm::downloadDebian,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Download Debian")
                    }
                } else {
                    OutlinedButton(
                        onClick = vm::deleteDebian,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Delete system")
                    }
                }
            }
        }

        // ---- Section 2: official opencode ----
        DeviceSectionCard(title = "2 · opencode (official)") {
            if (vm.opencodeVersion != null) {
                Text(
                    text = "opencode v${vm.opencodeVersion} · ${formatBytes(vm.opencodeBytes)} · untouched upstream build",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "The official agent binary, installed straight from opencode-ai releases (~170 MB).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StageList(stages = vm.opencodeStages)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.opencodeStages.any { it.state == StageState.ACTIVE }) {
                    OutlinedButton(
                        onClick = vm::cancelOpencode,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Cancel")
                    }
                } else if (vm.opencodeVersion == null) {
                    Button(
                        onClick = vm::downloadOpencode,
                        enabled = vm.debianVersion != null,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Download opencode")
                    }
                } else {
                    val running = backendState is LocalBackend.State.Running ||
                        backendState is LocalBackend.State.Starting
                    if (running) {
                        OutlinedButton(
                            onClick = vm::stop,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Stop")
                        }
                        Button(
                            onClick = vm::restart,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Restart")
                        }
                    } else {
                        Button(
                            onClick = vm::start,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Start backend")
                        }
                        OutlinedButton(
                            onClick = vm::deleteOpencode,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
            if (vm.opencodeVersion != null && vm.opencodeStages.isEmpty()) {
                OutlinedButton(
                    onClick = vm::downloadOpencode,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Check for opencode update")
                }
            }
        }

        // ---- Section 3: tools ----
        DeviceSectionCard(title = "3 · Tools (optional)") {
            if (vm.debianVersion == null) {
                Text(
                    text = "Available after the Debian system is installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                vm.tools.forEach { row ->
                    ToolRow(
                        row = row,
                        onInstall = { vm.installTool(row.def.id) },
                        onRemove = { vm.removeTool(row.def.id) }
                    )
                }
            }
        }

        if (logs.isNotEmpty()) {
            Text(
                text = "Server log",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Text(
                    text = logs.takeLast(30).joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Server: official opencode (MIT) in Debian (proot, GPL-2.0). " +
                "Agent workspace lives in app-private storage. Sources: " +
                "github.com/opencode-ai/opencode · termux/proot-distro",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun StageList(
    stages: List<InstallStage>,
    modifier: Modifier = Modifier
) {
    if (stages.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        stages.forEach { stage ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (stage.state) {
                    StageState.DONE -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    StageState.ACTIVE -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    StageState.ERROR -> Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    StageState.PENDING -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = stageLabel(stage),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (stage.state) {
                        StageState.ERROR -> MaterialTheme.colorScheme.error
                        StageState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
            }
            if (stage.state == StageState.ACTIVE && stage.fraction >= 0f) {
                LinearProgressIndicator(
                    progress = { stage.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp)
                )
            }
        }
    }
}

private fun stageLabel(stage: InstallStage): String {
    val base = stage.label
    if (stage.detail.isBlank()) return base
    return "$base — ${stage.detail}"
}

@Composable
private fun ToolRow(
    row: ToolRowState,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${row.def.label} · ${row.def.approxMb}" +
                    (if (row.installed) " · installed" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = row.def.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (row.working) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else if (row.installed) {
            OutlinedButton(onClick = onRemove) {
                Text("Remove")
            }
        } else {
            Button(onClick = onInstall) {
                Text("Install")
            }
        }
    }
}

private fun serverStatusLine(backend: LocalBackend.State): String = when (backend) {
    is LocalBackend.State.Running -> "Running · 127.0.0.1:4096"
    is LocalBackend.State.Starting -> "Starting…"
    is LocalBackend.State.Stopped -> backend.error ?: "Stopped"
    is LocalBackend.State.Idle -> "Idle"
}

private fun formatBytes(value: Long): String = when {
    value >= 1_000_000_000L -> "%.1f GB".format(value / 1_000_000_000.0)
    value >= 1_000_000L -> "%.0f MB".format(value / 1_000_000.0)
    value >= 1_000L -> "%.0f KB".format(value / 1_000.0)
    else -> "$value B"
}
