package com.rg.quarkcode.schedules

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    state: SchedulesUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (LocalSchedule) -> Unit,
    onEditingChange: (LocalSchedule) -> Unit,
    onCancelEditing: () -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Schedules") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add schedule")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.schedules.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No schedules. Add one to get a daily reminder with your prompt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            items(state.schedules, key = { it.id }) { schedule ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = schedule.title,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "%02d:%02d · %s".format(
                                    schedule.hour,
                                    schedule.minute,
                                    schedule.prompt.take(80)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = schedule.enabled,
                            onCheckedChange = { onToggle(schedule.id, it) }
                        )
                        IconButton(onClick = { onEdit(schedule) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onDelete(schedule.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    state.editing?.let { editing ->
        ModalBottomSheet(onDismissRequest = onCancelEditing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(text = "Schedule", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editing.title,
                    onValueChange = { onEditingChange(editing.copy(title = it)) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editing.prompt,
                    onValueChange = { onEditingChange(editing.copy(prompt = it)) },
                    label = { Text("Prompt reminder") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = editing.hour.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.takeIf { it in 0..23 }?.let {
                                onEditingChange(editing.copy(hour = it))
                            }
                        },
                        label = { Text("Hour") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = editing.minute.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.takeIf { it in 0..59 }?.let {
                                onEditingChange(editing.copy(minute = it))
                            }
                        },
                        label = { Text("Minute") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                state.error?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text("Save")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
