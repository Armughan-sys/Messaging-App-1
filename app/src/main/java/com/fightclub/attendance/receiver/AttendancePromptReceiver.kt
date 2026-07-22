package com.fightclub.attendance.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fightclub.attendance.data.repository.AttendanceStatusRepository
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.domain.scheduler.AlarmScheduler
import com.fightclub.attendance.notification.NotificationHelper
import com.fightclub.attendance.util.Constants
import com.fightclub.attendance.util.PromptSlot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * Fires at 1:00 PM, 3:00 PM, and 3:45 PM on class days (Mon/Wed/Fri). Shows the "are you
 * attending?" prompt only if today's question hasn't already been answered — which is what makes
 * the 3:00 PM and 3:45 PM reminders stay silent once the user has responded to an earlier one.
 *
 * Uses [android.content.BroadcastReceiver.goAsync] rather than WorkManager because the work here
 * (one status read, showing a notification, one alarm reschedule) is quick and must not be
 * delayed by WorkManager's scheduling overhead — an attendance prompt firing a few seconds late
 * is not a concern the same way a missed SMS would be.
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
        val slot = intent.getStringExtra(Constants.EXTRA_PROMPT_SLOT)?.let {
            runCatching { PromptSlot.valueOf(it) }.getOrNull()
        }
        if (dayValue !in 1..7 || slot == null) return
        val dayOfWeek = DayOfWeek.of(dayValue)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!attendanceStatusRepository.isTodayResolved()) {
                    notificationHelper.showAttendancePrompt(slot)
                }

                val settings = settingsRepository.getSettings()
                val time = when (slot) {
                    PromptSlot.FIRST -> settings.promptTime1
                    PromptSlot.SECOND -> settings.promptTime2
                    PromptSlot.THIRD -> settings.promptTime3
                }
                alarmScheduler.reschedulePrompt(dayOfWeek, time, slot)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
