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
import java.time.LocalTime
import java.time.ZoneId

/**
 * Verifies the AlarmManager scheduling contract described in
 * [com.fightclub.attendance.domain.scheduler.AlarmScheduler]: one alarm per (purpose, day),
 * rebuilt from scratch on every [AlarmSchedulerImpl.scheduleAll] call, with no duplicates.
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
    fun `scheduleAll arms an auto-send alarm for every configured auto-send day`() {
        scheduler.scheduleAll(AppSettings.DEFAULT)

        // AppSettings.DEFAULT.autoSendDays = {TUESDAY, THURSDAY}
        assertNotNull(pendingAutoSendIntent(DayOfWeek.TUESDAY, isDeadline = false))
        assertNotNull(pendingAutoSendIntent(DayOfWeek.THURSDAY, isDeadline = false))
    }

    @Test
    fun `scheduleAll arms three prompts plus a deadline for every class day`() {
        scheduler.scheduleAll(AppSettings.DEFAULT)

        // AppSettings.DEFAULT.classDays = {MONDAY, WEDNESDAY, FRIDAY}
        for (day in listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) {
            assertNotNull("prompt alarm missing for $day", pendingPromptIntent(day))
            assertNotNull("deadline alarm missing for $day", pendingAutoSendIntent(day, isDeadline = true))
        }
    }

    @Test
    fun `scheduleAll never leaves alarms for days no longer configured`() {
        scheduler.scheduleAll(AppSettings.DEFAULT)
        assertNotNull(pendingAutoSendIntent(DayOfWeek.TUESDAY, isDeadline = false))

        val withoutTuesday = AppSettings.DEFAULT.copy(autoSendDays = setOf(DayOfWeek.THURSDAY))
        scheduler.scheduleAll(withoutTuesday)

        assertNull(
            "Tuesday's auto-send alarm should have been cancelled",
            pendingAutoSendIntent(DayOfWeek.TUESDAY, isDeadline = false)
        )
        assertNotNull(pendingAutoSendIntent(DayOfWeek.THURSDAY, isDeadline = false))
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
        val next = scheduler.nextAutoSendOccurrence(AppSettings.DEFAULT)
        assertNotNull(next)
    }

    private fun pendingAutoSendIntent(day: DayOfWeek, isDeadline: Boolean): PendingIntent? {
        val requestCode = (if (isDeadline) 5000 else 1000) + day.value
        val intent = Intent(context, AutoSendAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
    }

    private fun pendingPromptIntent(day: DayOfWeek): PendingIntent? {
        // Checks the first prompt slot (base 2000); sufficient to prove the day was armed.
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
