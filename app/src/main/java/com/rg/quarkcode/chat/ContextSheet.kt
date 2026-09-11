package com.rg.quarkcode.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Port of the webview SessionContextTab stats behind the ring button.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(
    stats: ContextStats,
    model: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Context",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = {
                    if (stats.limit <= 0L) 0f
                    else (stats.used.toFloat() / stats.limit.toFloat()).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(
                    label = "Used",
                    value = formatCount(stats.used),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = "Limit",
                    value = formatCount(stats.limit),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = "Usage",
                    value = usagePercent(stats),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(
                    label = "Input",
                    value = formatCount(stats.input),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = "Output",
                    value = formatCount(stats.output),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = "Cost",
                    value = formatCost(stats.cost),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            val clipboard = LocalClipboardManager.current
            FilledTonalButton(
                onClick = onExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Export session")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    clipboard.setText(
                        AnnotatedString(
                            "Context ${formatCount(stats.used)}/${formatCount(stats.limit)} " +
                                "(${usagePercent(stats)}) in ${formatCount(stats.input)} " +
                                "out ${formatCount(stats.output)} cost ${formatCost(stats.cost)} · $model"
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Copy stats")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatCount(value: Long): String = when {
    value >= 1_000_000L -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000L -> "%.1fk".format(value / 1_000.0)
    else -> value.toString()
}

// Free models show no price; paid show 2 decimals max (detail sheet only).
private fun formatCost(cost: Double): String =
    if (cost <= 0.0) "Free" else "$" + "%.2f".format(cost)

private fun usagePercent(stats: ContextStats): String {
    if (stats.limit <= 0L) return "—"
    val pct = (stats.used * 100) / stats.limit
    return "$pct%"
}
