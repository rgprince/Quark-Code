package com.rg.quarkcode.schedules

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Context.scheduleDataStore by preferencesDataStore(name = "quark_schedules")

@Serializable
data class LocalSchedule(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val prompt: String = "",
    val hour: Int = 9,
    val minute: Int = 0,
    val enabled: Boolean = true
)

data class SchedulesUiState(
    val schedules: List<LocalSchedule> = emptyList(),
    val editing: LocalSchedule? = null,
    val error: String? = null
)

class SchedulesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val json = Json { ignoreUnknownKeys = true }

    var uiState by mutableStateOf(SchedulesUiState())
        private set

    private object Keys {
        val ALL = stringPreferencesKey("schedules")
    }

    init {
        viewModelScope.launch {
            uiState = uiState.copy(schedules = load())
            uiState.schedules.filter { it.enabled }.forEach { schedule(it) }
        }
    }

    private suspend fun load(): List<LocalSchedule> =
        withContext(Dispatchers.IO) {
            val raw = app.scheduleDataStore.data.first()[Keys.ALL].orEmpty()
            if (raw.isBlank()) emptyList() else json.decodeFromString(raw)
        }

    private suspend fun persist(schedules: List<LocalSchedule>) {
        withContext(Dispatchers.IO) {
            app.scheduleDataStore.edit { prefs ->
                prefs[Keys.ALL] = json.encodeToString(schedules)
            }
        }
    }

    fun startEditing(schedule: LocalSchedule? = null) {
        uiState = uiState.copy(editing = schedule ?: LocalSchedule(), error = null)
    }

    fun cancelEditing() {
        uiState = uiState.copy(editing = null, error = null)
    }

    fun onEditChange(updated: LocalSchedule) {
        uiState = uiState.copy(editing = updated)
    }

    fun saveEditing() {
        val editing = uiState.editing ?: return
        if (editing.title.isBlank() || editing.prompt.isBlank()) {
            uiState = uiState.copy(error = "Title and prompt are required.")
            return
        }
        viewModelScope.launch {
            val current = load().toMutableList()
            val index = current.indexOfFirst { it.id == editing.id }
            if (index < 0) current.add(editing) else current[index] = editing
            persist(current)
            if (editing.enabled) schedule(editing) else unschedule(editing.id)
            uiState = uiState.copy(schedules = current, editing = null, error = null)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val current = load().filterNot { it.id == id }
            persist(current)
            unschedule(id)
            uiState = uiState.copy(schedules = current)
        }
    }

    fun toggle(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = load().map { if (it.id == id) it.copy(enabled = enabled) else it }
            persist(current)
            current.firstOrNull { it.id == id }?.let {
                if (enabled) schedule(it) else unschedule(it.id)
            }
            uiState = uiState.copy(schedules = current)
        }
    }

    private fun schedule(item: LocalSchedule) {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, item.hour)
            set(Calendar.MINUTE, item.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = next.timeInMillis - now.timeInMillis
        val request = OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(ScheduleWorker.KEY_TITLE, item.title)
                    .putString(ScheduleWorker.KEY_PROMPT, item.prompt)
                    .build()
            )
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            "schedule-${item.id}",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun unschedule(id: String) {
        WorkManager.getInstance(app).cancelUniqueWork("schedule-$id")
    }
}
