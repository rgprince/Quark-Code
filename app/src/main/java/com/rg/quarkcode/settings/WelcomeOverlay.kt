package com.rg.quarkcode.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

const val QUARK_REPO_URL = "https://github.com/rgprince/Quark-Code"

// Big first-launch guide: getting started + the two things users hit most
// (Muse silent crash, RAM) + where to report bugs. Re-openable from
// Settings → Help → "Show welcome guide".
@Composable
fun WelcomeOverlay(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uri = LocalUriHandler.current
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Welcome to Quark Code",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Native Android client for opencode — on your phone, no laptop needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    OverlayStep(
                        n = "1",
                        text = "Get a server: Settings → Device → download Debian + opencode, or point at a LAN/Tailscale URL."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OverlayStep(
                        n = "2",
                        text = "Build does, Plan thinks first. Tap the edge strip in the chat box to switch modes."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OverlayStep(
                        n = "3",
                        text = "Thinking effort: tap the model pill → Thinking effort in the model sheet."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Known issue — Muse models",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Muse-family models can silently crash across ALL opencode versions " +
                            "when you swap Build ↔ Plan, swap models mid-chat, or the network drops. " +
                            "That crash is upstream, not this app — just retry the turn or start a new chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Before you install",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This build uses ~700 MB–1 GB RAM on average. Phones with less than " +
                            "4 GB RAM will struggle — please skip it for now. RAM use gets " +
                            "further optimized in future builds, and yes, it still has some bugs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Found a bug?",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please report it and leave a star so others can find the project:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = QUARK_REPO_URL,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { uri.openUri(QUARK_REPO_URL) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onClose()
                            onOpenSettings()
                        }
                    ) {
                        Text("Open Settings")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) {
                        Text("Get started")
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayStep(
    n: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Text(
            text = n,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
