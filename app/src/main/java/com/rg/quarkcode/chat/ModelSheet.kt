package com.rg.quarkcode.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// QoL: shows only the chosen provider's models by default (AndCode showed all).
// Provider itself is chosen in Settings; picker offers an escape-hatch "Show all".
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    runtime: Runtime,
    models: List<CatalogModel>,
    selectedId: String,
    selectedProviderId: String?,
    providerName: String,
    favorites: Set<String>,
    recents: List<String>,
    hiddenModels: List<CatalogModel>,
    catalogLoading: Boolean,
    catalogError: String?,
    variants: List<String> = emptyList(),
    selectedVariant: String? = null,
    modifier: Modifier = Modifier,
    onRuntimeChange: (Runtime) -> Unit,
    onModelChange: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onHiddenToggle: (String) -> Unit,
    onVariantChange: (String?) -> Unit = {},
    onRetryCatalog: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember(selectedProviderId) { mutableStateOf(false) }
    val byId = remember(models) { models.associateBy { it.id } }
    val scoped = remember(models, selectedProviderId, showAll) {
        if (showAll || selectedProviderId.isNullOrBlank()) models
        else models.filter { it.providerId == selectedProviderId }
    }
    val favoriteModels = remember(scoped, favorites) {
        scoped.filter { favorites.contains(it.id) }
    }
    val recentModels = remember(scoped, recents) {
        recents.mapNotNull { byId[it] }.filter { showAll || it.providerId == selectedProviderId }.take(3)
    }
    val visible = remember(scoped, query) {
        if (query.isBlank()) scoped else scoped.filter {
            it.label.contains(query, ignoreCase = true)
        }
    }
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
                text = "Model & runtime",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selectedProviderId.isNullOrBlank()) "No provider selected"
                    else "Provider: $providerName (${scoped.size})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenProviderSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Runtime.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Runtime.entries.size
                        ),
                        selected = runtime == entry,
                        onClick = { onRuntimeChange(entry) },
                        label = {
                            Text(if (entry == Runtime.NATIVE) "Native" else "Server")
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search models") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (variants.isNotEmpty()) {
                SectionHeader(title = "Thinking effort")
                FlowChips(
                    options = listOf(null) + variants,
                    selected = selectedVariant,
                    onSelect = onVariantChange
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (catalogLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Loading providers…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            catalogError?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onRetryCatalog) {
                        Text("Retry")
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (favoriteModels.isNotEmpty()) {
                    item(key = "fav-header") {
                        SectionHeader(title = "Favorites")
                    }
                    items(favoriteModels, key = { "fav-${it.id}" }) { model ->
                        ModelRow(
                            model = model,
                            selected = selectedId == model.id,
                            favorite = true,
                            onSelect = { onModelChange(model.id) },
                            onFavoriteToggle = { onFavoriteToggle(model.id) }
                        )
                    }
                }
                if (recentModels.isNotEmpty()) {
                    item(key = "recent-header") {
                        SectionHeader(title = "Recent")
                    }
                    items(recentModels, key = { "recent-${it.id}" }) { model ->
                        ModelRow(
                            model = model,
                            selected = selectedId == model.id,
                            favorite = favorites.contains(model.id),
                            onSelect = { onModelChange(model.id) },
                            onFavoriteToggle = { onFavoriteToggle(model.id) }
                        )
                    }
                }
                item(key = "all-header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(title = if (showAll) "All models" else "Models", modifier = Modifier.weight(1f))
                        if (!selectedProviderId.isNullOrBlank()) {
                            TextButton(onClick = { showAll = !showAll }) {
                                Text(if (showAll) "Only mine" else "Show all")
                            }
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item(key = "all-empty") {
                        Text(
                            text = "No models here. Change provider in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(visible, key = { "all-${it.id}" }) { model ->
                        ModelRow(
                            model = model,
                            selected = selectedId == model.id,
                            favorite = favorites.contains(model.id),
                            showHide = showAll,
                            onSelect = { onModelChange(model.id) },
                            onFavoriteToggle = { onFavoriteToggle(model.id) },
                            onHiddenToggle = { onHiddenToggle(model.id) }
                        )
                    }
                }
                if (showAll && hiddenModels.isNotEmpty()) {
                    item(key = "hidden-header") {
                        SectionHeader(title = "Hidden")
                    }
                    items(hiddenModels, key = { "hidden-${it.id}" }) { model ->
                        ModelRow(
                            model = model,
                            selected = false,
                            favorite = false,
                            hidden = true,
                            showHide = true,
                            onSelect = { },
                            onFavoriteToggle = { },
                            onHiddenToggle = { onHiddenToggle(model.id) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

// Custom (non-M3-list) model row: full name wraps to 2 lines so you can
// actually tell models apart; provider/id line underneath in mono.
@Composable
private fun ModelRow(
    model: CatalogModel,
    selected: Boolean,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    showHide: Boolean = false,
    onSelect: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onHiddenToggle: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(
                enabled = !hidden,
                onClick = onSelect,
                role = Role.Button
            )
            .semantics {
                contentDescription = "${model.label}, " +
                    if (selected) "selected" else "not selected"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (favorite) "Unfavorite" else "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (model.providerId.isBlank() && model.modelId.isBlank()) {
                        "server default"
                    } else {
                        "${model.providerId} · ${model.modelId}".trim(' ', '·')
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showHide) {
                IconButton(onClick = onHiddenToggle, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (hidden) "Unhide model" else "Hide model",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (!hidden) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle
                    else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(
    options: List<String?>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option ?: "Default") }
            )
        }
    }
}
