package com.rg.quarkcode.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Variation on AndCode: centered project pill, status pulse dot,
// and the webview-style token ring (kept AND the bottom meter).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarkTopBar(
    project: String,
    usedFraction: Float,
    usedLabel: String,
    statusOk: Boolean,
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    onSpaces: () -> Unit,
    onTokenClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
            }
        },
        title = {
            AssistChip(
                onClick = onSpaces,
                label = { Text(project) },
                leadingIcon = {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                }
            )
        },
        actions = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(
                        color = if (statusOk) {
                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        } else {
                            androidx.compose.ui.graphics.Color(0xFFF44336)
                        }
                    )
                }
            }
            TokenRingButton(
                fraction = usedFraction,
                label = usedLabel,
                onClick = onTokenClick
            )
        }
    )
}

// Port of the webview SessionContextUsage button: simple ring + %,
// opens the Context stats sheet. Canvas-drawn: no version-sensitive API.
@Composable
fun TokenRingButton(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx())
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 3.dp.toPx())
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
