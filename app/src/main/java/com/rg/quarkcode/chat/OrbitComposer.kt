package com.rg.quarkcode.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Expressive orbit composer: 32dp container, Assist/Filter chips,
// 56dp XL send morphing Send<->Stop, determinate meter, 48dp targets,
// menu-style slash/@ popups, IME-safe.
@Composable
fun OrbitComposer(
    input: String,
    model: String,
    meterLabel: String,
    meterFraction: Float,
    sending: Boolean,
    queuedCount: Int,
    modes: List<String>,
    mode: String?,
    variants: List<String>,
    selectedVariant: String?,
    slashSuggestions: List<SlashSuggestion>,
    atSuggestions: List<AtFile>,
    modifier: Modifier = Modifier,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    onMicClick: () -> Unit,
    onModelClick: () -> Unit,
    onModeChange: (String) -> Unit,
    onVariantChange: (String?) -> Unit,
    onSlashSelect: (SlashSuggestion) -> Unit,
    onAtSelect: (AtFile) -> Unit
) {
    // Provider name stays out of the box: short model label only.
    val shortModel = model.substringAfter(" / ", model)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(vertical = 4.dp)
    ) {
        if (input.startsWith("/") && slashSuggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(slashSuggestions, key = { it.name }) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onSlashSelect(suggestion) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = suggestion.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = suggestion.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        if (atSuggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(atSuggestions, key = { it.path }) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onAtSelect(file) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = file.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
            ),
            tonalElevation = if (focused) 2.dp else 1.dp
        ) {
            val modeColor = if ((mode ?: modes.firstOrNull() ?: "build") == "plan") {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Mode color line: primary = build, tertiary = plan.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(modeColor)
                        .semantics {
                            contentDescription = "Mode ${mode ?: modes.firstOrNull() ?: "build"}"
                        }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .semantics {
                            contentDescription = "Message input"
                        }
                ) {
                    if (input.isEmpty()) {
                        Text(
                            text = "/ for commands, @ for files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            .focusable(),
                        minLines = 1,
                        maxLines = 5,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = onModelClick,
                        label = {
                            Text(
                                shortModel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    ModeMini(
                        modes = modes,
                        selected = mode,
                        onSelect = onModeChange
                    )
                    if (variants.isNotEmpty()) {
                        VariantMini(
                            options = variants,
                            selected = selectedVariant,
                            onSelect = onVariantChange
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (queuedCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.semantics {
                                contentDescription = "$queuedCount messages queued"
                            }
                        ) {
                            Text(
                                text = "Queued $queuedCount",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (!sending) {
                        OutlinedIconButton(
                            onClick = onMicClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Voice input",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = { if (sending) onAbort() else onSend() },
                        enabled = sending || input.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = if (sending) "Stop generating" else "Send message"
                            }
                    ) {
                        AnimatedContent(targetState = sending, label = "send-stop") { isSending ->
                            Icon(
                                imageVector = if (isSending) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
        // One slot at the card bottom: sending progress while busy,
        // context meter otherwise.
        if (sending) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp, end = 4.dp)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            MeterBar(
                fraction = meterFraction,
                label = meterLabel,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

// Compact mode pill: color dot + short label, tap for menu.
// The card-top color line carries the mode identity (build/plan).
@Composable
private fun ModeMini(
    modes: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (modes.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.takeIf { modes.contains(it) } ?: modes.firstOrNull() ?: "build"
    val dot = if (label == "plan") MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clickable(
                    onClick = { expanded = true },
                    role = Role.Button
                )
                .semantics { contentDescription = "Mode $label, change mode" }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dot)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry) },
                    onClick = { onSelect(entry); expanded = false },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun VariantMini(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = if (selected != null) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clickable(
                    onClick = { expanded = true },
                    role = Role.Button
                )
                .semantics {
                    contentDescription = "Thinking ${selected ?: "auto"}, change thinking"
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = selected ?: "auto",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Default") },
                onClick = { onSelect(null); expanded = false },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.replaceFirstChar { it.uppercase() }) },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun MeterBar(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier
) {
    val color = when {
        fraction >= 0.9f -> MaterialTheme.colorScheme.error
        fraction >= 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .semantics { contentDescription = "Context $label used" },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
