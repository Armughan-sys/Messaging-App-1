package com.fightclub.attendance.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fightclub.attendance.data.repository.AttendanceStatusRepository
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.domain.scheduler.AlarmScheduler
import com.fightclub.attendance.notification.NotificationHelper
import com.fightclub.attendance.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * Fires once per active day, at "class time minus the configured lead hours" (see
 * [AppSettings.promptTime][com.fightclub.attendance.data.model.AppSettings.promptTime]). Shows
 * the "are you attending?" prompt only if today's question hasn't already been answered, so a
 * stray duplicate firing can never double-prompt.
 *
 * Uses [android.content.BroadcastReceiver.goAsync] rather than WorkManager because the work here
 * (one status read, showing a notification, one alarm reschedule) is quick and must not be
 * delayed by WorkManager's scheduling overhead — an attendance prompt firing a few seconds late
 * is not a concern the same way a missed message would be.
 */
@AndroidEntryPoint
class AttendancePromptReceiver : BroadcastReceiver() {

    @Inject
    lateinit var attendanceStatusRepository: AttendanceStatusRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val dayValue = intent.getIntExtra(Constants.EXTRA_DAY_OF_WEEK_VALUE, -1)
        if (dayValue !in 1..7) return
        val dayOfWeek = DayOfWeek.of(dayValue)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!attendanceStatusRepository.isTodayResolved()) {
                    notificationHelper.showAttendancePrompt()
                }

                val settings = settingsRepository.getSettings()
                alarmScheduler.reschedulePrompt(dayOfWeek, settings.promptTime)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
