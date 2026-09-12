package com.rg.quarkcode.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.rg.quarkcode.backend.LocalBackend

/**
 * Settings → Device tab: the on-device backend manager. The APK stays lean —
 * the native opencode server downloads here on first opt-in (~180 MB),
 * then runs on loopback exactly like a remote backend.
 */
@Composable
fun DeviceBackendPanel(
    modifier: Modifier = Modifier,
    vm: BackendViewModel = viewModel()
) {
    val state = vm.uiState
    val backendState by vm.backendState.collectAsState()
    val logs by vm.backendLogs.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!state.supported) {
            Text(
                text = "On-device backend needs an arm64 phone — this device is not supported. Remote backends still work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            return
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "On-device backend", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = statusLine(state, backendState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = vm::setEnabled,
                modifier = Modifier.semantics { contentDescription = "Enable on-device backend" }
            )
        }
        state.downloadedVersion?.let { version ->
            Text(
                text = "Runtime v$version · ${formatBytes(state.sizeBytes)} on disk",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (state.downloading) {
            val fraction = if (state.downloadTotal > 0) {
                (state.downloadBytes.toFloat() / state.downloadTotal).coerceIn(0f, 1f)
            } else {
                0f
            }
            Text(
                text = "Downloading backend v${state.downloadVersion ?: "…"} · " +
                    "${formatBytes(state.downloadBytes)}" +
                    (if (state.downloadTotal > 0) " / ${formatBytes(state.downloadTotal)}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = vm::cancelDownload,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Cancel download")
            }
        } else if (state.downloadedVersion == null) {
            Text(
                text = "First download is ~180 MB — Wi-Fi recommended. The app itself stays small.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = vm::download,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Download backend")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        onClick = vm::deleteRuntime,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Delete")
                    }
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
            text = "Server: official opencode, native Android build (Hope2333, MIT). " +
                "Agent workspace lives in app-private storage. Sources: " +
                "github.com/sst/opencode · github.com/Hope2333/opencode-termux",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun statusLine(
    state: com.rg.quarkcode.backend.BackendPanelState,
    backend: LocalBackend.State
): String = when (backend) {
    is LocalBackend.State.Running -> "Running · 127.0.0.1:4096"
    is LocalBackend.State.Starting -> "Starting…"
    is LocalBackend.State.Stopped -> backend.error ?: "Stopped"
    is LocalBackend.State.Idle -> if (state.downloadedVersion != null) {
        "Downloaded — start it to chat on-device"
    } else {
        "Not downloaded"
    }
}

private fun formatBytes(value: Long): String = when {
    value >= 1_000_000_000L -> "%.1f GB".format(value / 1_000_000_000.0)
    value >= 1_000_000L -> "%.0f MB".format(value / 1_000_000.0)
    value >= 1_000L -> "%.0f KB".format(value / 1_000.0)
    else -> "$value B"
}
