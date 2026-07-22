package com.fightclub.attendance.domain.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.fightclub.attendance.data.model.AppSettings
import com.fightclub.attendance.receiver.AttendancePromptReceiver
import com.fightclub.attendance.receiver.AutoSendAlarmReceiver
import com.fightclub.attendance.util.Constants
import com.fightclub.attendance.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every [AlarmManager] interaction in the app.
 *
 * Every active day gets exactly two alarms, both computed from the same [AppSettings]:
 *  - a **prompt** alarm at [AppSettings.promptTime] ("class time" minus the configured lead hours)
 *  - a **deadline** alarm at [AppSettings.autoSendTime], which auto-sends if the prompt was ignored
 *
 * Scheduling strategy: rather than relying on a single repeating alarm (AlarmManager has no
 * reliable "every Tuesday" primitive), each alarm re-schedules its own next occurrence, one week
 * ahead, the moment it fires (see the receivers). [scheduleAll] is the entry point that
 * (re)builds every alarm from scratch and is called on first launch, whenever Settings are saved,
 * and after every boot via [com.fightclub.attendance.receiver.BootReceiver].
 *
 * Duplicate prevention: every alarm's [PendingIntent] request code is deterministic
 * (`namespace + dayOfWeek.value`), so calling [AlarmManager.setExactAndAllowWhileIdle] with the
 * same request code simply replaces any previously scheduled alarm instead of stacking a second
 * one. [scheduleAll] additionally cancels every possible request code up front so stale alarms
 * left over from a settings change (e.g. removing an active day) can never linger.
 */
interface AlarmScheduler {
    fun scheduleAll(settings: AppSettings)
    fun canScheduleExactAlarms(): Boolean

    /** Called by a receiver right after it fires, to queue up the same alarm one week later. */
    fun rescheduleDeadline(dayOfWeek: DayOfWeek, time: LocalTime)
    fun reschedulePrompt(dayOfWeek: DayOfWeek, time: LocalTime)

    /** Soonest upcoming occurrence of each alarm type, for display on the home screen. */
    fun nextDeadlineOccurrence(settings: AppSettings): Long?
    fun nextPromptOccurrence(settings: AppSettings): Long?
}

@Singleton
class AlarmSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock
) : AlarmScheduler {

    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    override fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    override fun scheduleAll(settings: AppSettings) {
        cancelEveryPossibleAlarm()

        for (day in settings.activeDays) {
            schedulePrompt(day, settings.promptTime)
            scheduleDeadline(day, settings.autoSendTime)
        }
    }

    override fun rescheduleDeadline(dayOfWeek: DayOfWeek, time: LocalTime) {
        scheduleDeadline(dayOfWeek, time)
    }

    override fun reschedulePrompt(dayOfWeek: DayOfWeek, time: LocalTime) {
        schedulePrompt(dayOfWeek, time)
    }

    override fun nextDeadlineOccurrence(settings: AppSettings): Long? =
        settings.activeDays.minOfOrNull { day ->
            DateTimeUtils.nextOccurrenceMillis(day, settings.autoSendTime, clock)
        }

    override fun nextPromptOccurrence(settings: AppSettings): Long? =
        settings.activeDays.minOfOrNull { day ->
            DateTimeUtils.nextOccurrenceMillis(day, settings.promptTime, clock)
        }

    private fun scheduleDeadline(dayOfWeek: DayOfWeek, time: LocalTime) {
        val requestCode = deadlineRequestCode(dayOfWeek)
        val intent = Intent(context, AutoSendAlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_DAY_OF_WEEK_VALUE, dayOfWeek.value)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMillis = DateTimeUtils.nextOccurrenceMillis(dayOfWeek, time, clock)
        setExactOrFallback(triggerAtMillis, pendingIntent)
    }

    private fun schedulePrompt(dayOfWeek: DayOfWeek, time: LocalTime) {
        val requestCode = promptRequestCode(dayOfWeek)
        val intent = Intent(context, AttendancePromptReceiver::class.java).apply {
            putExtra(Constants.EXTRA_DAY_OF_WEEK_VALUE, dayOfWeek.value)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMillis = DateTimeUtils.nextOccurrenceMillis(dayOfWeek, time, clock)
        setExactOrFallback(triggerAtMillis, pendingIntent)
    }

    /**
     * Uses [AlarmManager.setExactAndAllowWhileIdle] so the alarm fires at the precise minute even
     * in Doze. Falls back to an inexact alarm only if the exact-alarm permission is ever revoked
     * by the user from system settings after being granted (the UI surfaces a warning in that case).
     */
    private fun setExactOrFallback(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission revoked; falling back to inexact alarm", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelEveryPossibleAlarm() {
        for (day in DayOfWeek.values()) {
            cancelDeadline(day)
            cancelPrompt(day)
        }
    }

    private fun cancelDeadline(dayOfWeek: DayOfWeek) {
        val requestCode = deadlineRequestCode(dayOfWeek)
        val intent = Intent(context, AutoSendAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun cancelPrompt(dayOfWeek: DayOfWeek) {
        val requestCode = promptRequestCode(dayOfWeek)
        val intent = Intent(context, AttendancePromptReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun deadlineRequestCode(dayOfWeek: DayOfWeek): Int =
        Constants.REQUEST_CODE_DEADLINE_BASE + dayOfWeek.value

    private fun promptRequestCode(dayOfWeek: DayOfWeek): Int =
        Constants.REQUEST_CODE_PROMPT_BASE + dayOfWeek.value

    companion object {
        private const val TAG = "AlarmScheduler"
    }
}
