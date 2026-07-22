package com.fightclub.attendance.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fightclub.attendance.automation.AccessibilityUtils
import com.fightclub.attendance.automation.WhatsAppAccessibilityService
import com.fightclub.attendance.data.model.MessageLogEntry
import com.fightclub.attendance.data.repository.MessageLogRepository
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.domain.scheduler.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val contactConfigured: Boolean = false,
    val contactName: String? = null,
    val nextAutoSendMillis: Long? = null,
    val nextPromptMillis: Long? = null,
    val lastMessage: MessageLogEntry? = null,
    val canScheduleExactAlarms: Boolean = true,
    val whatsAppAccessibilityServiceEnabled: Boolean = true
) {
    /** The single soonest upcoming action across both alarm types, for the headline card. */
    val nextScheduledActionMillis: Long?
        get() = listOfNotNull(nextAutoSendMillis, nextPromptMillis).minOrNull()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
    messageLogRepository: MessageLogRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    // Recomputing "next occurrence" (and the accessibility-service check) only when settings
    // change would leave a stale display once that occurrence passes or the user flips the
    // service in system Settings, so also re-tick once a minute.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepository.observeSettings(),
        messageLogRepository.observeLatest(),
        ticker
    ) { settings, lastMessage, _ ->
        HomeUiState(
            isLoading = false,
            contactConfigured = settings.contact != null,
            contactName = settings.contact?.displayName,
            nextAutoSendMillis = alarmScheduler.nextAutoSendOccurrence(settings),
            nextPromptMillis = alarmScheduler.nextPromptOccurrence(settings),
            lastMessage = lastMessage,
            canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms(),
            whatsAppAccessibilityServiceEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(
                context,
                WhatsAppAccessibilityService::class.java
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
