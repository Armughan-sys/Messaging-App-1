package com.fightclub.attendance.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fightclub.attendance.R
import com.fightclub.attendance.data.local.entity.MessageDeliveryStatus
import com.fightclub.attendance.data.model.MessageLogEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")

private fun formatMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)

private fun formatInstant(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(dateTimeFormatter)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (!state.contactConfigured) {
                item {
                    WarningCard(
                        icon = Icons.Filled.Warning,
                        text = stringResource(R.string.home_warning_no_contact)
                    )
                }
            }

            if (!state.whatsAppAccessibilityServiceEnabled) {
                item {
                    WarningCard(
                        icon = Icons.Filled.Warning,
                        text = stringResource(R.string.home_warning_accessibility_disabled),
                        actionLabel = stringResource(R.string.home_warning_accessibility_action),
                        onAction = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
            }

            if (!state.canScheduleExactAlarms) {
                item {
                    WarningCard(
                        icon = Icons.Filled.Warning,
                        text = stringResource(R.string.home_warning_exact_alarm),
                        actionLabel = stringResource(R.string.home_warning_exact_alarm_action),
                        onAction = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        icon = Icons.Filled.DateRange,
                        title = stringResource(R.string.home_classes_this_week),
                        value = state.classesAttendedThisWeek.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Filled.CalendarMonth,
                        title = stringResource(R.string.home_classes_this_month),
                        value = state.classesAttendedThisMonth.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                InfoCard(
                    icon = Icons.Filled.CalendarMonth,
                    title = stringResource(R.string.home_next_scheduled_action),
                    value = state.nextScheduledActionMillis?.let(::formatMillis)
                        ?: stringResource(R.string.home_no_schedule)
                )
            }

            item {
                InfoCard(
                    icon = Icons.Filled.Send,
                    title = stringResource(R.string.home_next_auto_message),
                    value = state.nextDeadlineMillis?.let(::formatMillis)
                        ?: stringResource(R.string.home_no_schedule)
                )
            }

            item {
                InfoCard(
                    icon = Icons.Filled.NotificationsActive,
                    title = stringResource(R.string.home_next_prompt),
                    value = state.nextPromptMillis?.let(::formatMillis)
                        ?: stringResource(R.string.home_no_schedule)
                )
            }

            item {
                LastMessageCard(state.lastMessage)
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun LastMessageCard(lastMessage: MessageLogEntry?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.home_last_message_sent), style = MaterialTheme.typography.labelLarge)

            if (lastMessage == null) {
                Text(
                    text = stringResource(R.string.home_no_message_yet),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                return@Column
            }

            Text(
                text = formatInstant(lastMessage.timestamp),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            val (statusIcon, statusText) = when (lastMessage.status) {
                MessageDeliveryStatus.SENT, MessageDeliveryStatus.DELIVERED ->
                    Icons.Filled.CheckCircle to stringResource(R.string.home_message_status_sent)
                MessageDeliveryStatus.FAILED ->
                    Icons.Filled.Error to (lastMessage.failureReason ?: stringResource(R.string.home_message_status_failed))
                MessageDeliveryStatus.PENDING ->
                    Icons.Filled.NotificationsActive to stringResource(R.string.home_message_status_pending)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(statusIcon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WarningCard(
    icon: ImageVector,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
