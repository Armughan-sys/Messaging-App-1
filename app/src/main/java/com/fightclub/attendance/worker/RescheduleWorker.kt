package com.fightclub.attendance.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.domain.scheduler.AlarmScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Rebuilds every AlarmManager alarm from the currently saved settings. Triggered by
 * [com.fightclub.attendance.receiver.BootReceiver] after a reboot or app update, and by the
 * Settings screen whenever the user saves a change, so alarms always reflect the latest
 * configuration and are never left stale or duplicated.
 */
@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.getSettings()
        alarmScheduler.scheduleAll(settings)
        return Result.success()
    }
}
