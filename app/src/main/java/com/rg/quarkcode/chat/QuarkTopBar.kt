package com.rg.quarkcode.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
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
                TwoLineMenuIcon(description = "Open navigation drawer")
            }
        },
        title = {
            // Chat name, not a folder: "New chat" until the server generates
            // a title, then the generated name. Tap opens the drawer.
            Text(
                text = project.ifBlank { "New chat" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSpaces, role = Role.Button)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Chat $project. Open navigation drawer." }
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

// Two-line menu mark: Quark's drawer glyph (not the stock 3-line burger).
@Composable
fun TwoLineMenuIcon(
    description: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = description ?: "Open navigation drawer"
                role = Role.Button
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface)
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface)
                .align(Alignment.CenterHorizontally)
        )
    }
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
