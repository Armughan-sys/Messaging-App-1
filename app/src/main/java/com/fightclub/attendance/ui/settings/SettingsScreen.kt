package com.fightclub.attendance.ui.settings

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    var messageDraft by remember(settings.smsMessage) { mutableStateOf(settings.smsMessage) }

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
                        enabled = messageDraft != settings.smsMessage
                    ) {
                        Text(stringResource(R.string.settings_save_message))
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_section_class_days)) {
                    DayOfWeekChipRow(
                        selectedDays = settings.classDays,
                        onToggle = { day ->
                            val updated = settings.classDays.toMutableSet().apply {
                                if (contains(day)) remove(day) else add(day)
                            }
                            viewModel.updateClassDays(updated)
                        }
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_section_auto_send_days)) {
                    DayOfWeekChipRow(
                        selectedDays = settings.autoSendDays,
                        onToggle = { day ->
                            val updated = settings.autoSendDays.toMutableSet().apply {
                                if (contains(day)) remove(day) else add(day)
                            }
                            viewModel.updateAutoSendDays(updated)
                        }
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_section_times)) {
                    TimeRow(
                        label = stringResource(R.string.settings_prompt_time_1),
                        time = settings.promptTime1,
                        onClick = { editingTimeSlot = TimeSlot.PROMPT_1 }
                    )
                    TimeRow(
                        label = stringResource(R.string.settings_prompt_time_2),
                        time = settings.promptTime2,
                        onClick = { editingTimeSlot = TimeSlot.PROMPT_2 }
                    )
                    TimeRow(
                        label = stringResource(R.string.settings_prompt_time_3),
                        time = settings.promptTime3,
                        onClick = { editingTimeSlot = TimeSlot.PROMPT_3 }
                    )
                    TimeRow(
                        label = stringResource(R.string.settings_auto_send_time),
                        time = settings.autoSendTime,
                        onClick = { editingTimeSlot = TimeSlot.AUTO_SEND }
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
            TimeSlot.PROMPT_1 -> settings.promptTime1
            TimeSlot.PROMPT_2 -> settings.promptTime2
            TimeSlot.PROMPT_3 -> settings.promptTime3
            TimeSlot.AUTO_SEND -> settings.autoSendTime
        }
        AppTimePickerDialog(
            initialTime = initialTime,
            onDismiss = { editingTimeSlot = null },
            onConfirm = { time ->
                when (slot) {
                    TimeSlot.PROMPT_1 -> viewModel.updatePromptTime1(time)
                    TimeSlot.PROMPT_2 -> viewModel.updatePromptTime2(time)
                    TimeSlot.PROMPT_3 -> viewModel.updatePromptTime3(time)
                    TimeSlot.AUTO_SEND -> viewModel.updateAutoSendTime(time)
                }
                editingTimeSlot = null
            }
        )
    }
}

private enum class TimeSlot { PROMPT_1, PROMPT_2, PROMPT_3, AUTO_SEND }

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
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) {
            Text(time.toString())
        }
    }
}

private val dayLabels = mapOf(
    DayOfWeek.MONDAY to "Mon",
    DayOfWeek.TUESDAY to "Tue",
    DayOfWeek.WEDNESDAY to "Wed",
    DayOfWeek.THURSDAY to "Thu",
    DayOfWeek.FRIDAY to "Fri",
    DayOfWeek.SATURDAY to "Sat",
    DayOfWeek.SUNDAY to "Sun"
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
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                androidx.compose.foundation.layout.Row(
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
