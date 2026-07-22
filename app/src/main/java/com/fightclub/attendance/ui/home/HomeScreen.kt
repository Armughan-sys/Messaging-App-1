package com.fightclub.attendance.ui.home

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fightclub.attendance.R
import com.fightclub.attendance.data.local.entity.SmsDeliveryStatus
import com.fightclub.attendance.data.model.SmsLogEntry
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
                    title = stringResource(R.string.home_next_auto_sms),
                    value = state.nextAutoSendMillis?.let(::formatMillis)
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
                LastSmsCard(state.lastSms)
            }
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
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
private fun LastSmsCard(lastSms: SmsLogEntry?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.home_last_sms_sent), style = MaterialTheme.typography.labelLarge)

            if (lastSms == null) {
                Text(
                    text = stringResource(R.string.home_no_sms_yet),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                return@Column
            }

            Text(
                text = formatInstant(lastSms.timestamp),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            val (statusIcon, statusText) = when (lastSms.status) {
                SmsDeliveryStatus.SENT, SmsDeliveryStatus.DELIVERED ->
                    Icons.Filled.CheckCircle to stringResource(R.string.home_sms_status_sent)
                SmsDeliveryStatus.FAILED ->
                    Icons.Filled.Error to (lastSms.failureReason ?: stringResource(R.string.home_sms_status_failed))
                SmsDeliveryStatus.PENDING ->
                    Icons.Filled.NotificationsActive to stringResource(R.string.home_sms_status_pending)
            }

            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row {
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
