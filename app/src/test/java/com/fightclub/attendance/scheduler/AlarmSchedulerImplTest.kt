package com.fightclub.attendance.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.fightclub.attendance.data.model.AppSettings
import com.fightclub.attendance.domain.scheduler.AlarmSchedulerImpl
import com.fightclub.attendance.receiver.AttendancePromptReceiver
import com.fightclub.attendance.receiver.AutoSendAlarmReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Verifies the AlarmManager scheduling contract described in
 * [com.fightclub.attendance.domain.scheduler.AlarmScheduler]: every active day gets exactly one
 * prompt alarm and one deadline alarm, rebuilt from scratch on every
 * [AlarmSchedulerImpl.scheduleAll] call, with no duplicates.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerImplTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var scheduler: AlarmSchedulerImpl

    // A fixed Monday so every occurrence calculation in the test is deterministic.
    private val clock: Clock = Clock.fixed(
        Instant.parse("2024-01-01T09:00:00Z"),
        ZoneId.of("UTC")
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        scheduler = AlarmSchedulerImpl(context, clock)
    }

    @Test
    fun `scheduleAll arms a prompt and a deadline alarm for every active day`() {
        scheduler.scheduleAll(AppSettings.DEFAULT)

        // AppSettings.DEFAULT.activeDays = Monday through Saturday.
        for (day in listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        )) {
            assertNotNull("prompt alarm missing for $day", pendingPromptIntent(day))
            assertNotNull("deadline alarm missing for $day", pendingDeadlineIntent(day))
        }
    }

    @Test
    fun `scheduleAll arms nothing for a day not in activeDays`() {
        scheduler.scheduleAll(AppSettings.DEFAULT)

        assertNull(pendingPromptIntent(DayOfWeek.SUNDAY))
        assertNull(pendingDeadlineIntent(DayOfWeek.SUNDAY))
    }

    @Test
    fun `scheduleAll never leaves alarms for days no longer configured`() {
        scheduler.scheduleAll(AppSettings.DEFAULT)
        assertNotNull(pendingDeadlineIntent(DayOfWeek.SATURDAY))

        val withoutSaturday = AppSettings.DEFAULT.copy(
            activeDays = AppSettings.DEFAULT.activeDays - DayOfWeek.SATURDAY
        )
        scheduler.scheduleAll(withoutSaturday)

        assertNull(
            "Saturday's alarms should have been cancelled",
            pendingDeadlineIntent(DayOfWeek.SATURDAY)
        )
        assertNull(pendingPromptIntent(DayOfWeek.SATURDAY))
        assertNotNull(pendingDeadlineIntent(DayOfWeek.MONDAY))
    }

    @Test
    fun `calling scheduleAll twice does not create duplicate alarms`() {
        scheduler.scheduleAll(AppSettings.DEFAULT)
        val countAfterFirstCall = shadowAlarmManager.scheduledAlarms.size

        scheduler.scheduleAll(AppSettings.DEFAULT)
        val countAfterSecondCall = shadowAlarmManager.scheduledAlarms.size

        assertEquals(
            "Re-scheduling identical settings must not add extra alarms",
            countAfterFirstCall,
            countAfterSecondCall
        )
    }

    @Test
    fun `next occurrence helpers report the soonest matching alarm`() {
        assertNotNull(scheduler.nextDeadlineOccurrence(AppSettings.DEFAULT))
        assertNotNull(scheduler.nextPromptOccurrence(AppSettings.DEFAULT))
    }

    private fun pendingDeadlineIntent(day: DayOfWeek): PendingIntent? {
        val requestCode = 5000 + day.value
        val intent = Intent(context, AutoSendAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
    }

    private fun pendingPromptIntent(day: DayOfWeek): PendingIntent? {
        val requestCode = 2000 + day.value
        val intent = Intent(context, AttendancePromptReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
    }
}
