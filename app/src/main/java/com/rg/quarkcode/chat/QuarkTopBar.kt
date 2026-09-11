package com.rg.quarkcode.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Expressive top bar: project pill opens the drawer (SpacesSheet deleted),
// token-aware status badge + context ring with full semantics.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarkTopBar(
    project: String,
    usedFraction: Float,
    usedLabel: String,
    statusOk: Boolean,
    modifier: Modifier = Modifier,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null,
    onMenu: () -> Unit,
    onSpaces: () -> Unit,
    onTokenClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Open navigation drawer")
            }
        },
        title = {
            AssistChip(
                onClick = onSpaces,
                label = {
                    Text(
                        project,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                }
            )
        },
        actions = {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = if (statusOk) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (statusOk) "Connected" else "Disconnected"
                        }
                    )
                },
                modifier = Modifier.size(24.dp)
            ) { }
            TokenRingButton(
                fraction = usedFraction,
                label = usedLabel,
                onClick = onTokenClick
            )
        }
    )
}

// Context ring: determinate arc + % label, opens the Context stats sheet.
@Composable
fun TokenRingButton(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = when {
        fraction >= 0.9f -> MaterialTheme.colorScheme.error
        fraction >= 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val textColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Context $label used, open details" },
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
