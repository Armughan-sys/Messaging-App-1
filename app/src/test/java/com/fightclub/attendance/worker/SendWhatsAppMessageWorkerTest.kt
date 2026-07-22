package com.fightclub.attendance.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.fightclub.attendance.automation.WhatsAppSendResult
import com.fightclub.attendance.automation.WhatsAppSender
import com.fightclub.attendance.data.local.entity.AttendanceStatus
import com.fightclub.attendance.data.local.entity.MessageTrigger
import com.fightclub.attendance.data.model.AppSettings
import com.fightclub.attendance.data.model.SavedContact
import com.fightclub.attendance.data.repository.AttendanceStatusRepository
import com.fightclub.attendance.data.repository.MessageLogRepository
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.domain.scheduler.AlarmScheduler
import com.fightclub.attendance.notification.NotificationHelper
import com.fightclub.attendance.util.Constants
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
class SendWhatsAppMessageWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var attendanceStatusRepository: AttendanceStatusRepository
    private lateinit var messageLogRepository: MessageLogRepository
    private lateinit var whatsAppSender: WhatsAppSender
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var notificationHelper: NotificationHelper
    private val clock: Clock = Clock.fixed(Instant.parse("2024-01-02T16:00:00Z"), ZoneId.of("UTC"))

    private val configuredContact = SavedContact("key", "Abdullah Malik KAK", "+15551234567")
    private val settingsWithContact = AppSettings.DEFAULT.copy(contact = configuredContact)

    @Before
    fun setUp() {
        settingsRepository = mockk()
        attendanceStatusRepository = mockk(relaxUnitFun = true)
        messageLogRepository = mockk()
        whatsAppSender = mockk()
        alarmScheduler = mockk(relaxUnitFun = true)
        notificationHelper = mockk(relaxUnitFun = true)

        coEvery { messageLogRepository.recordAttempt(any(), any(), any(), any(), any()) } returns 1L
        coEvery { messageLogRepository.updateStatus(any(), any(), any()) } returns Unit
    }

    /**
     * [SendWhatsAppMessageWorker] takes several extra constructor dependencies beyond the
     * (Context, WorkerParameters) pair WorkManager normally reflects on, so — as recommended for
     * testing Hilt workers — a small [WorkerFactory] wires our fakes in manually instead.
     */
    private fun buildWorker(inputData: Data): SendWhatsAppMessageWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = SendWhatsAppMessageWorker(
                appContext,
                workerParameters,
                settingsRepository,
                attendanceStatusRepository,
                messageLogRepository,
                whatsAppSender,
                alarmScheduler,
                notificationHelper,
                clock
            )
        }

        return TestListenableWorkerBuilder<SendWhatsAppMessageWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun `deadline send fires and reschedules next week when nobody responded`() = runTest {
        coEvery { settingsRepository.getSettings() } returns settingsWithContact
        coEvery { attendanceStatusRepository.isTodayResolved() } returns false
        coEvery { whatsAppSender.sendMessage(any(), any()) } returns WhatsAppSendResult.Sent

        val worker = buildWorker(
            Data.Builder()
                .putString(Constants.INPUT_MESSAGE_TRIGGER, MessageTrigger.NO_RESPONSE_DEADLINE.name)
                .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, DayOfWeek.TUESDAY.value)
                .build()
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { whatsAppSender.sendMessage(configuredContact.phoneNumber, settingsWithContact.messageText) }
        coVerify { attendanceStatusRepository.setTodayStatus(AttendanceStatus.AUTO_SENT_NO_RESPONSE) }
        coVerify { alarmScheduler.rescheduleDeadline(DayOfWeek.TUESDAY, settingsWithContact.autoSendTime) }
    }

    @Test
    fun `deadline send is skipped once the user already answered`() = runTest {
        coEvery { settingsRepository.getSettings() } returns settingsWithContact
        coEvery { attendanceStatusRepository.isTodayResolved() } returns true

        val worker = buildWorker(
            Data.Builder()
                .putString(Constants.INPUT_MESSAGE_TRIGGER, MessageTrigger.NO_RESPONSE_DEADLINE.name)
                .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, DayOfWeek.MONDAY.value)
                .build()
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { whatsAppSender.sendMessage(any(), any()) }
        coVerify { alarmScheduler.rescheduleDeadline(DayOfWeek.MONDAY, settingsWithContact.autoSendTime) }
    }

    @Test
    fun `manual NO response sends immediately without touching the alarm schedule`() = runTest {
        coEvery { settingsRepository.getSettings() } returns settingsWithContact
        coEvery { attendanceStatusRepository.isTodayResolved() } returns false
        coEvery { whatsAppSender.sendMessage(any(), any()) } returns WhatsAppSendResult.Sent

        val worker = buildWorker(
            Data.Builder()
                .putString(Constants.INPUT_MESSAGE_TRIGGER, MessageTrigger.MANUAL_NO_RESPONSE.name)
                .build()
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { whatsAppSender.sendMessage(configuredContact.phoneNumber, settingsWithContact.messageText) }
        coVerify(exactly = 0) { alarmScheduler.rescheduleDeadline(any(), any()) }
        // Manual NO taps aren't the deadline trigger, so attendance status isn't overwritten here
        // (AttendanceResponseHandler already set it to NOT_ATTENDING before enqueueing this work).
        coVerify(exactly = 0) { attendanceStatusRepository.setTodayStatus(any()) }
    }

    @Test
    fun `missing contact fails the work without crashing and still reschedules`() = runTest {
        coEvery { settingsRepository.getSettings() } returns AppSettings.DEFAULT // no contact configured
        coEvery { attendanceStatusRepository.isTodayResolved() } returns false

        val worker = buildWorker(
            Data.Builder()
                .putString(Constants.INPUT_MESSAGE_TRIGGER, MessageTrigger.NO_RESPONSE_DEADLINE.name)
                .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, DayOfWeek.THURSDAY.value)
                .build()
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { whatsAppSender.sendMessage(any(), any()) }
        coVerify { alarmScheduler.rescheduleDeadline(DayOfWeek.THURSDAY, AppSettings.DEFAULT.autoSendTime) }
    }

    @Test
    fun `accessibility service disabled is logged as failed and surfaces the enable-service notification`() = runTest {
        coEvery { settingsRepository.getSettings() } returns settingsWithContact
        coEvery { attendanceStatusRepository.isTodayResolved() } returns false
        coEvery { whatsAppSender.sendMessage(any(), any()) } returns WhatsAppSendResult.AccessibilityServiceDisabled

        val worker = buildWorker(
            Data.Builder()
                .putString(Constants.INPUT_MESSAGE_TRIGGER, MessageTrigger.NO_RESPONSE_DEADLINE.name)
                .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, DayOfWeek.TUESDAY.value)
                .build()
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { notificationHelper.showAccessibilityServiceDisabledNotification() }
    }
}
