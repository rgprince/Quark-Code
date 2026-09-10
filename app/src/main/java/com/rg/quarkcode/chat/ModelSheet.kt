package com.rg.quarkcode.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// AndCode picker structure (Favorites / Recents / all, star toggles)
// in Quark styling. Keys are "providerId/modelId" throughout.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    runtime: Runtime,
    models: List<CatalogModel>,
    selectedId: String,
    favorites: Set<String>,
    recents: List<String>,
    catalogLoading: Boolean,
    catalogError: String?,
    modifier: Modifier = Modifier,
    onRuntimeChange: (Runtime) -> Unit,
    onModelChange: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onRetryCatalog: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val byId = remember(models) { models.associateBy { it.id } }
    val favoriteModels = remember(models, favorites) {
        models.filter { favorites.contains(it.id) }
    }
    val recentModels = remember(models, recents) {
        recents.mapNotNull { byId[it] }.take(3)
    }
    val visible = remember(models, query) {
        if (query.isBlank()) models else models.filter {
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
            Spacer(modifier = Modifier.height(12.dp))
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
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
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
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
                    SectionHeader(title = "All models")
                }
                if (visible.isEmpty()) {
                    item(key = "all-empty") {
                        Text(
                            text = "No models match. Check the server connection.",
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
                            onSelect = { onModelChange(model.id) },
                            onFavoriteToggle = { onFavoriteToggle(model.id) }
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

@Composable
private fun ModelRow(
    model: CatalogModel,
    selected: Boolean,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Favorite",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = model.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
    }
}
