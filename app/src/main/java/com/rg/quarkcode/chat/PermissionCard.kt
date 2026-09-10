package com.rg.quarkcode.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Webview permission prompt as big-thumb Allow/Deny card (V1 scope).
@Composable
fun PermissionCard(
    request: PermissionRequest,
    modifier: Modifier = Modifier,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onRememberChange: (Boolean) -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Permission: ${request.tool}",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = request.summary,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = request.remember,
                    onCheckedChange = onRememberChange
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Remember",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAllow,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Allow")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Deny")
                }
            }
        }
    }
}
