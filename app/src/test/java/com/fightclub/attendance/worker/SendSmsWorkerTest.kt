package com.fightclub.attendance.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.fightclub.attendance.data.local.entity.AttendanceStatus
import com.fightclub.attendance.data.local.entity.SmsTrigger
import com.fightclub.attendance.data.model.AppSettings
import com.fightclub.attendance.data.model.SavedContact
import com.fightclub.attendance.data.repository.AttendanceStatusRepository
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.data.repository.SmsLogRepository
import com.fightclub.attendance.domain.scheduler.AlarmScheduler
import com.fightclub.attendance.notification.NotificationHelper
import com.fightclub.attendance.util.Constants
import com.fightclub.attendance.util.SmsSendResult
import com.fightclub.attendance.util.SmsSender
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SendSmsWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var attendanceStatusRepository: AttendanceStatusRepository
    private lateinit var smsLogRepository: SmsLogRepository
    private lateinit var smsSender: SmsSender
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var notificationHelper: NotificationHelper
    private val clock: Clock = Clock.fixed(Instant.parse("2024-01-02T16:00:00Z"), ZoneId.of("UTC"))

    private val configuredContact = SavedContact("key", "Abdullah Malik KAK", "+15551234567")
    private val settingsWithContact = AppSettings.DEFAULT.copy(contact = configuredContact)

    @Before
    fun setUp() {
        settingsRepository = mockk()
        attendanceStatusRepository = mockk(relaxUnitFun = true)
        smsLogRepository = mockk()
        smsSender = mockk()
        alarmScheduler = mockk(relaxUnitFun = true)
        notificationHelper = mockk(relaxUnitFun = true)

        coEvery { smsLogRepository.recordAttempt(any(), any(), any(), any(), any()) } returns 1L
        coEvery { smsLogRepository.updateStatus(any(), any(), any()) } returns Unit
    }

    /**
     * [SendSmsWorker] takes several extra constructor dependencies beyond the (Context,
     * WorkerParameters) pair WorkManager normally reflects on, so — as recommended for testing
     * Hilt workers — a small [WorkerFactory] wires our fakes in manually instead.
     */
    private fun buildWorker(inputData: Data): SendSmsWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = SendSmsWorker(
                appContext,
                workerParameters,
                settingsRepository,
                attendanceStatusRepository,
                smsLogRepository,
                smsSender,
                alarmScheduler,
                notificationHelper,
                clock
            )
        }

        return TestListenableWorkerBuilder<SendSmsWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun `unconditional Tuesday send always sends regardless of attendance status`() = runTest {
        coEvery { settingsRepository.getSettings() } returns settingsWithContact
        coEvery { attendanceStatusRepository.isTodayResolved() } returns false
        coEvery { smsSender.sendSms(any(), any()) } returns SmsSendResult.Sent

        val worker = buildWorker(
            Data.Builder()
                .putString(Constants.INPUT_SMS_TRIGGER, SmsTrigger.AUTOMATIC_SCHEDULE.name)
                .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, DayOfWeek.TUESDAY.value)
                .build()
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { smsSender.sendSms(configuredContact.phoneNumber, settingsWithContact.smsMessage) }
        coVerify { alarmScheduler.rescheduleAutoSend(DayOfWeek.TUESDAY, settingsWithContact.autoSendTime, false) }
    }

    @Test
    fun `deadline send is skipped once the user already answered`() = runTest {
        coEvery { settingsRepository.getSettings() } returns settingsWithContact
        coEvery { attendanceStatusRepository.isTodayResolved() } returns true

        val worker = buildWorker(
            Data.Builder()
                .putString(Constants.INPUT_SMS_TRIGGER, SmsTrigger.NO_RESPONSE_DEADLINE.name)
                .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, DayOfWeek.MONDAY.value)
                .build()
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { smsSender.sendSms(any(), any()) }
        coVerify { alarmScheduler.rescheduleAutoSend(DayOfWeek.MONDAY, settingsWithContact.autoSendTime, true) }
    }

    @Test
    fun `deadline send fires and marks status when nobody responded`() = runTest {
        coEvery { settingsRepository.getSettings() } returns settingsWithContact
        coEvery { attendanceStatusRepository.isTodayResolved() } returns false
        coEvery { smsSender.sendSms(any(), any()) } returns SmsSendResult.Sent

        val worker = buildWorker(
            Data.Builder()
                .putString(Constants.INPUT_SMS_TRIGGER, SmsTrigger.NO_RESPONSE_DEADLINE.name)
                .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, DayOfWeek.FRIDAY.value)
                .build()
        )

        worker.doWork()

        coVerify { smsSender.sendSms(configuredContact.phoneNumber, settingsWithContact.smsMessage) }
        coVerify { attendanceStatusRepository.setTodayStatus(AttendanceStatus.AUTO_SENT_NO_RESPONSE) }
    }

    @Test
    fun `missing contact fails the work without crashing and still reschedules`() = runTest {
        coEvery { settingsRepository.getSettings() } returns AppSettings.DEFAULT // no contact configured
        coEvery { attendanceStatusRepository.isTodayResolved() } returns false

        val worker = buildWorker(
            Data.Builder()
                .putString(Constants.INPUT_SMS_TRIGGER, SmsTrigger.AUTOMATIC_SCHEDULE.name)
                .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, DayOfWeek.THURSDAY.value)
                .build()
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { smsSender.sendSms(any(), any()) }
        coVerify { alarmScheduler.rescheduleAutoSend(DayOfWeek.THURSDAY, settingsWithContact.autoSendTime, false) }
    }
}
