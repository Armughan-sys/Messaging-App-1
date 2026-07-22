package com.fightclub.attendance.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fightclub.attendance.data.model.SmsLogEntry
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.data.repository.SmsLogRepository
import com.fightclub.attendance.domain.scheduler.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val contactConfigured: Boolean = false,
    val contactName: String? = null,
    val nextAutoSendMillis: Long? = null,
    val nextPromptMillis: Long? = null,
    val lastSms: SmsLogEntry? = null,
    val canScheduleExactAlarms: Boolean = true
) {
    /** The single soonest upcoming action across both alarm types, for the headline card. */
    val nextScheduledActionMillis: Long?
        get() = listOfNotNull(nextAutoSendMillis, nextPromptMillis).minOrNull()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    smsLogRepository: SmsLogRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    // Recomputing "next occurrence" only when settings change would leave a stale countdown
    // once that occurrence passes, so also re-tick once a minute.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepository.observeSettings(),
        smsLogRepository.observeLatest(),
        ticker
    ) { settings, lastSms, _ ->
        HomeUiState(
            isLoading = false,
            contactConfigured = settings.contact != null,
            contactName = settings.contact?.displayName,
            nextAutoSendMillis = alarmScheduler.nextAutoSendOccurrence(settings),
            nextPromptMillis = alarmScheduler.nextPromptOccurrence(settings),
            lastSms = lastSms,
            canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
