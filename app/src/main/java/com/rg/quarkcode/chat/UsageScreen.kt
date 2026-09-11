package com.rg.quarkcode.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Usage stats: opt-in scan of up to 30 recent sessions, then totals by
// period (week/month/all) with per-model breakdown and server costs.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    state: UsageState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRequestScan: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirmScan: () -> Unit,
    onCancelScan: () -> Unit,
    onPeriodChange: (UsagePeriod) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Usage") }
            )
        }
    ) { paddingValues ->
        val totals = when (state.period) {
            UsagePeriod.WEEK -> state.week
            UsagePeriod.MONTH -> state.month
            UsagePeriod.ALL -> state.all
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "top-space") { Spacer(modifier = Modifier.height(4.dp)) }
            if (!state.scanning && state.scannedAt == 0L && state.error == null) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PieChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "See where your tokens go",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Totals for week, month and all time — broken down by model, with server-reported cost. Free models show $0.00.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRequestScan,
                            modifier = Modifier.heightIn(min = 52.dp)
                        ) {
                            Text("Scan now")
                        }
                    }
                }
            }
            if (state.scanning) {
                item(key = "scanning") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Scanning ${state.scanned} of ${state.total} sessions…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (state.total <= 0) 0f
                                    else (state.scanned.toFloat() / state.total).coerceIn(0f, 1f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onCancelScan,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
            state.error?.let { message ->
                item(key = "error") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onRequestScan) {
                                Text("Try again")
                            }
                        }
                    }
                }
            }
            if (state.scannedAt > 0L && !state.scanning) {
                item(key = "period") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        UsagePeriod.entries.forEachIndexed { index, period ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, UsagePeriod.entries.size),
                                selected = state.period == period,
                                onClick = { onPeriodChange(period) },
                                label = { Text(period.label) }
                            )
                        }
                    }
                }
                item(key = "hero") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Total tokens",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = shortTokens(totals.total),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "in ${shortTokens(totals.input)} · " +
                                    "cache ${shortTokens(totals.cache)} · " +
                                    "out ${shortTokens(totals.output)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                HeroStat(label = "Cost", value = formatUsageCost(totals.cost))
                                HeroStat(label = "Sessions", value = totals.sessions.toString())
                                HeroStat(label = "Models", value = totals.byModel.size.toString())
                            }
                        }
                    }
                }
                if (totals.byModel.isEmpty()) {
                    item(key = "no-models") {
                        Text(
                            text = "No model activity in this period.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val maxTokens = totals.byModel.maxOf { it.context }.coerceAtLeast(1L)
                    item(key = "models-header") {
                        Text(
                            text = "By model",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    item(key = "donut") {
                        ModelDonut(
                            list = totals.byModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(totals.byModel, key = { it.provider + "/" + it.label }) { usage ->
                        ModelUsageRow(usage = usage, maxTokens = maxTokens)
                    }
                }
                item(key = "footnote") {
                    Text(
                        text = "Cost is server-reported per session (free models count $0.00). " +
                            "Per-model cost is split pro-rata by token share. Up to 30 recent sessions scanned.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item(key = "rescan") {
                    OutlinedButton(
                        onClick = onRequestScan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan again")
                    }
                }
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
    if (state.confirmScan) {
        AlertDialog(
            onDismissRequest = onDismissConfirm,
            title = { Text("Scan usage?") },
            text = {
                Text("Reads up to 30 recent sessions from this server to add up tokens, models and cost. Nothing is modified.")
            },
            confirmButton = {
                Button(onClick = onConfirmScan) {
                    Text("Scan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissConfirm) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Donut of context share by model, drawn on Canvas (no new dependency).
@Composable
private fun ModelDonut(
    list: List<ModelUsage>,
    modifier: Modifier = Modifier
) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.outline
    )
    val total = list.sumOf { it.context }.coerceAtLeast(1L)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier
                    .size(110.dp)
                    .semantics { contentDescription = "Token share by model" }
            ) {
                var start = -90f
                list.forEachIndexed { index, usage ->
                    val sweep = 360f * (usage.context.toFloat() / total)
                    if (sweep > 0.5f) {
                        drawArc(
                            color = palette[index % palette.size],
                            startAngle = start,
                            sweepAngle = sweep - 2f,
                            useCenter = false,
                            style = Stroke(width = 36f, cap = StrokeCap.Round)
                        )
                    }
                    start += sweep
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                list.take(5).forEachIndexed { index, usage ->
                    val pct = (usage.context * 100 / total).coerceIn(0L, 100L)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(palette[index % palette.size])
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = usage.label.ifBlank { "unknown" },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ModelUsageRow(usage: ModelUsage, maxTokens: Long, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = usage.label.ifBlank { "unknown" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (usage.provider.isNotBlank()) {
                        Text(
                            text = usage.provider,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = shortTokens(usage.context),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = formatUsageCost(usage.cost),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "${shortTokens(usage.tokens)} new + ${shortTokens(usage.cache)} cache",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (usage.context.toFloat() / maxTokens).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

private fun shortTokens(value: Long): String = when {
    value >= 1_000_000L -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000L -> "%.1fk".format(value / 1_000.0)
    else -> value.toString()
}

private fun formatUsageCost(cost: Double): String =
    if (cost <= 0.0) "Free" else "$" + "%.2f".format(cost)
