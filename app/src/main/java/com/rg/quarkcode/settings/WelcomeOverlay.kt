package com.rg.quarkcode.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

const val QUARK_REPO_URL = "https://github.com/rgprince/Quark-Code"

// Lean first-launch guide: server -> chat -> workshop -> help.
// Details (RAM, keys, cost, licenses) live in Settings -> About.
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
                .heightIn(max = 580.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Welcome to Quark Code",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personal alpha to run opencode on your phone, sandboxed. No laptop needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    OverlayStep(
                        n = "1",
                        title = "Get a server",
                        text = "Settings → Device → download on this phone (Wi-Fi recommended), or point at a LAN / Tailscale URL."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OverlayStep(
                        n = "2",
                        title = "Chat",
                        text = "Build does it, Plan thinks first. Tap Allow / Deny when the agent asks."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OverlayStep(
                        n = "3",
                        title = "Workshop files",
                        text = "The agent only sees ~/workspace. Bring files in with Import, take them out with Share / Save to phone."
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Need help?",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Settings → Help reopens this guide. Found a bug? Please report it here:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = QUARK_REPO_URL,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { uri.openUri(QUARK_REPO_URL) }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Star the repo so others can find it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        onClose()
                        onOpenSettings()
                    }) {
                        Text("Open Settings")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onClose,
                        modifier = Modifier.height(48.dp)
                    ) {
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
    title: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = n,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
    }
}
