package com.fightclub.attendance.ui.settings

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fightclub.attendance.R
import com.fightclub.attendance.data.model.ThemeMode
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val pickContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let(viewModel::resolveContactFromUri)
        }
    }

    var editingTimeSlot by remember { mutableStateOf<TimeSlot?>(null) }
    var messageDraft by remember(settings.messageText) { mutableStateOf(settings.messageText) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                SectionCard(title = stringResource(R.string.settings_section_contact)) {
                    Text(
                        text = settings.contact?.let { "${it.displayName}\n${it.phoneNumber}" }
                            ?: stringResource(R.string.settings_no_contact),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                            )
                            pickContactLauncher.launch(intent)
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(stringResource(R.string.settings_change_contact))
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_section_message)) {
                    OutlinedTextField(
                        value = messageDraft,
                        onValueChange = { messageDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Button(
                        onClick = { viewModel.updateMessage(messageDraft) },
                        modifier = Modifier.padding(top = 12.dp),
                        enabled = messageDraft != settings.messageText
                    ) {
                        Text(stringResource(R.string.settings_save_message))
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_section_active_days)) {
                    Text(
                        text = stringResource(R.string.settings_section_active_days_description),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    DayOfWeekChipRow(
                        selectedDays = settings.activeDays,
                        onToggle = { day ->
                            val updated = settings.activeDays.toMutableSet().apply {
                                if (contains(day)) remove(day) else add(day)
                            }
                            viewModel.updateActiveDays(updated)
                        }
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_section_class_time)) {
                    Text(
                        text = stringResource(R.string.settings_section_class_time_description),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (settings.activeDays.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_no_active_days),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        DayOfWeek.values()
                            .filter { it in settings.activeDays }
                            .sortedBy { it.value }
                            .forEach { day ->
                                TimeRow(
                                    label = dayFullLabels.getValue(day),
                                    time = settings.classTimeFor(day),
                                    onClick = { editingTimeSlot = TimeSlot.ClassTime(day) }
                                )
                            }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_section_prompt_and_deadline)) {
                    PromptLeadHoursRow(
                        hours = settings.promptLeadHours,
                        onChange = viewModel::updatePromptLeadHours
                    )
                    TimeRow(
                        label = stringResource(R.string.settings_auto_send_time),
                        time = settings.autoSendTime,
                        onClick = { editingTimeSlot = TimeSlot.AutoSend }
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_section_theme)) {
                    ThemeSelector(
                        selected = settings.themeMode,
                        onSelect = viewModel::updateTheme
                    )
                }
            }
        }
    }

    editingTimeSlot?.let { slot ->
        val initialTime = when (slot) {
            is TimeSlot.ClassTime -> settings.classTimeFor(slot.day)
            TimeSlot.AutoSend -> settings.autoSendTime
        }
        AppTimePickerDialog(
            initialTime = initialTime,
            onDismiss = { editingTimeSlot = null },
            onConfirm = { time ->
                when (slot) {
                    is TimeSlot.ClassTime -> viewModel.updateClassTime(slot.day, time)
                    TimeSlot.AutoSend -> viewModel.updateAutoSendTime(time)
                }
                editingTimeSlot = null
            }
        )
    }
}

private sealed class TimeSlot {
    data class ClassTime(val day: DayOfWeek) : TimeSlot()
    data object AutoSend : TimeSlot()
}

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
private fun TimeRow(label: String, time: LocalTime, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) {
            Text(time.toString())
        }
    }
}

@Composable
private fun PromptLeadHoursRow(hours: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_prompt_lead_hours),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (hours > MIN_LEAD_HOURS) onChange(hours - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
            }
            Text(
                text = hours.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { if (hours < MAX_LEAD_HOURS) onChange(hours + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
    }
}

private const val MIN_LEAD_HOURS = 0
private const val MAX_LEAD_HOURS = 12

private val dayLabels = mapOf(
    DayOfWeek.MONDAY to "Mon",
    DayOfWeek.TUESDAY to "Tue",
    DayOfWeek.WEDNESDAY to "Wed",
    DayOfWeek.THURSDAY to "Thu",
    DayOfWeek.FRIDAY to "Fri",
    DayOfWeek.SATURDAY to "Sat",
    DayOfWeek.SUNDAY to "Sun"
)

private val dayFullLabels = mapOf(
    DayOfWeek.MONDAY to "Monday",
    DayOfWeek.TUESDAY to "Tuesday",
    DayOfWeek.WEDNESDAY to "Wednesday",
    DayOfWeek.THURSDAY to "Thursday",
    DayOfWeek.FRIDAY to "Friday",
    DayOfWeek.SATURDAY to "Saturday",
    DayOfWeek.SUNDAY to "Sunday"
)

@Composable
private fun DayOfWeekChipRow(selectedDays: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DayOfWeek.values().toList()) { day ->
            FilterChip(
                selected = day in selectedDays,
                onClick = { onToggle(day) },
                label = { Text(dayLabels.getValue(day)) }
            )
        }
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.values().forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false
    )
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(24.dp)) {
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            }
        }
    }
}
