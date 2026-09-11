package com.rg.quarkcode.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// AndCode IA (warning tint + 3 full-width buttons), Quark Void/Paper tokens.
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = "Permission required",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "The agent wants to run this action. Review before allowing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = request.tool,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary
            )
            if (request.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = request.summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = request.remember, onCheckedChange = onRememberChange)
                Text(text = "Remember choice", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
                Text("Allow once")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth(),
                enabled = request.remember
            ) {
                Text("Always allow")
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(onClick = onDeny, modifier = Modifier.fillMaxWidth()) {
                Text("Deny")
            }
        }
    }
}
