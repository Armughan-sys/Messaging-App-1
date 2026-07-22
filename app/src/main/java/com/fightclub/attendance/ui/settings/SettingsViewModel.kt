package com.fightclub.attendance.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fightclub.attendance.data.model.AppSettings
import com.fightclub.attendance.data.model.SavedContact
import com.fightclub.attendance.data.model.ThemeMode
import com.fightclub.attendance.data.repository.ContactRepository
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.util.Constants
import com.fightclub.attendance.worker.RescheduleWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT)

    fun updateMessage(message: String) = updateAndReschedule { it.copy(messageText = message) }

    fun updateClassTime(time: LocalTime) = updateAndReschedule { it.copy(classTime = time) }
    fun updatePromptLeadHours(hours: Int) = updateAndReschedule { it.copy(promptLeadHours = hours) }
    fun updateAutoSendTime(time: LocalTime) = updateAndReschedule { it.copy(autoSendTime = time) }

    fun updateActiveDays(days: Set<DayOfWeek>) = updateAndReschedule { it.copy(activeDays = days) }

    fun updateTheme(mode: ThemeMode) = updateAndReschedule { it.copy(themeMode = mode) }

    fun updateContact(contact: SavedContact) = updateAndReschedule { it.copy(contact = contact) }

    fun resolveContactFromUri(uri: Uri) {
        viewModelScope.launch {
            val contact = contactRepository.resolveContact(uri) ?: return@launch
            updateContact(contact)
        }
    }

    private fun updateAndReschedule(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val current = settingsRepository.getSettings()
            settingsRepository.updateSettings(transform(current))
            enqueueReschedule()
        }
    }

    private fun enqueueReschedule() {
        val request = OneTimeWorkRequestBuilder<RescheduleWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(Constants.WORK_RESCHEDULE_ALARMS, ExistingWorkPolicy.REPLACE, request)
    }
}
