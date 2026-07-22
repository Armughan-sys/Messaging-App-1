package com.fightclub.attendance.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fightclub.attendance.data.model.AppSettings
import com.fightclub.attendance.data.model.SavedContact
import com.fightclub.attendance.data.repository.ContactRepository
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.util.Constants
import com.fightclub.attendance.worker.RescheduleWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the app is in the "find Abdullah Malik KAK automatically" first-launch flow. */
sealed interface ContactSearchState {
    data object Idle : ContactSearchState
    data object Searching : ContactSearchState
    data class NeedsManualSelection(val candidates: List<SavedContact>) : ContactSearchState
    data object Resolved : ContactSearchState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT)

    private val _contactSearchState = MutableStateFlow<ContactSearchState>(ContactSearchState.Idle)
    val contactSearchState: StateFlow<ContactSearchState> = _contactSearchState

    private var hasStartedSearch = false

    /** Kicks off the one-time automatic phonebook search; safe to call repeatedly. */
    fun runAutoContactSearchIfNeeded(currentSettings: AppSettings) {
        if (currentSettings.hasCompletedFirstLaunchSetup || currentSettings.contact != null) return
        if (hasStartedSearch) return
        hasStartedSearch = true

        _contactSearchState.value = ContactSearchState.Searching
        viewModelScope.launch {
            val matches = contactRepository.searchByDisplayName(Constants.TARGET_CONTACT_DISPLAY_NAME)
            if (matches.size == 1) {
                saveContactAndFinishSetup(matches.first())
                _contactSearchState.value = ContactSearchState.Resolved
            } else {
                // Zero or multiple matches: let the user resolve the ambiguity manually.
                _contactSearchState.value = ContactSearchState.NeedsManualSelection(matches)
            }
        }
    }

    fun onCandidateSelected(contact: SavedContact) {
        viewModelScope.launch {
            saveContactAndFinishSetup(contact)
            _contactSearchState.value = ContactSearchState.Resolved
        }
    }

    fun onContactUriResolved(uri: Uri) {
        viewModelScope.launch {
            val contact = contactRepository.resolveContact(uri) ?: return@launch
            saveContactAndFinishSetup(contact)
            _contactSearchState.value = ContactSearchState.Resolved
        }
    }

    private suspend fun saveContactAndFinishSetup(contact: SavedContact) {
        settingsRepository.updateContact(contact)
        settingsRepository.markFirstLaunchSetupComplete()
        enqueueReschedule()
    }

    private fun enqueueReschedule() {
        val request = OneTimeWorkRequestBuilder<RescheduleWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(Constants.WORK_RESCHEDULE_ALARMS, ExistingWorkPolicy.REPLACE, request)
    }
}
