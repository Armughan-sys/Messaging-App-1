package com.fightclub.attendance.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fightclub.attendance.automation.WhatsAppSendResult
import com.fightclub.attendance.automation.WhatsAppSender
import com.fightclub.attendance.data.local.entity.AttendanceStatus
import com.fightclub.attendance.data.local.entity.MessageDeliveryStatus
import com.fightclub.attendance.data.local.entity.MessageTrigger
import com.fightclub.attendance.data.repository.AttendanceStatusRepository
import com.fightclub.attendance.data.repository.MessageLogRepository
import com.fightclub.attendance.data.repository.SettingsRepository
import com.fightclub.attendance.domain.scheduler.AlarmScheduler
import com.fightclub.attendance.notification.NotificationHelper
import com.fightclub.attendance.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Does the actual work of sending the attendance WhatsApp message, for both triggers in
 * [MessageTrigger]:
 *  - [MessageTrigger.NO_RESPONSE_DEADLINE]: the daily deadline, sent only if the attendance
 *    question is still [AttendanceStatus.PENDING] (i.e. the prompt was ignored).
 *  - [MessageTrigger.MANUAL_NO_RESPONSE]: the user tapped NO, so send immediately.
 *
 * Runs as a [CoroutineWorker] (rather than directly in a BroadcastReceiver) so the send survives
 * process death and gets WorkManager's Doze-aware scheduling guarantees.
 */
@HiltWorker
class SendWhatsAppMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val attendanceStatusRepository: AttendanceStatusRepository,
    private val messageLogRepository: MessageLogRepository,
    private val whatsAppSender: WhatsAppSender,
    private val alarmScheduler: AlarmScheduler,
    private val notificationHelper: NotificationHelper,
    private val clock: Clock
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val triggerName = inputData.getString(Constants.INPUT_MESSAGE_TRIGGER)
            ?: return Result.failure()
        val trigger = runCatching { MessageTrigger.valueOf(triggerName) }.getOrNull()
            ?: return Result.failure()
        val dayValue = inputData.getInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, -1).takeIf { it in 1..7 }

        val settings = settingsRepository.getSettings()

        if (trigger == MessageTrigger.NO_RESPONSE_DEADLINE && attendanceStatusRepository.isTodayResolved()) {
            // The user already answered (or a previous run already auto-sent); stay quiet.
            rescheduleDeadlineIfNeeded(dayValue, settings.autoSendTime)
            return Result.success()
        }

        val contact = settings.contact
        if (contact == null) {
            rescheduleDeadlineIfNeeded(dayValue, settings.autoSendTime)
            notificationHelper.showMessageStatusNotification(
                success = false,
                reason = "No manager contact configured yet — open the app to select one."
            )
            return Result.failure()
        }

        val logId = messageLogRepository.recordAttempt(
            recipientName = contact.displayName,
            recipientNumber = contact.phoneNumber,
            message = settings.messageText,
            trigger = trigger,
            timestampMillis = clock.millis()
        )

        when (val result = whatsAppSender.sendMessage(contact.phoneNumber, settings.messageText)) {
            is WhatsAppSendResult.Sent -> {
                messageLogRepository.updateStatus(logId, MessageDeliveryStatus.SENT)
                if (trigger == MessageTrigger.NO_RESPONSE_DEADLINE) {
                    attendanceStatusRepository.setTodayStatus(AttendanceStatus.AUTO_SENT_NO_RESPONSE)
                }
                notificationHelper.showMessageStatusNotification(success = true)
            }
            is WhatsAppSendResult.Failed -> {
                messageLogRepository.updateStatus(logId, MessageDeliveryStatus.FAILED, result.reason)
                notificationHelper.showMessageStatusNotification(success = false, reason = result.reason)
            }
            is WhatsAppSendResult.AccessibilityServiceDisabled -> {
                val reason = "WhatsApp Auto-Send accessibility service is off"
                messageLogRepository.updateStatus(logId, MessageDeliveryStatus.FAILED, reason)
                notificationHelper.showAccessibilityServiceDisabledNotification()
            }
        }

        rescheduleDeadlineIfNeeded(dayValue, settings.autoSendTime)
        return Result.success()
    }

    /**
     * The daily deadline alarm must queue up its own next occurrence one week ahead; a manual NO
     * tap has no recurring schedule to renew (it didn't come from an alarm in the first place).
     */
    private fun rescheduleDeadlineIfNeeded(dayValue: Int?, autoSendTime: LocalTime) {
        if (dayValue == null) return
        alarmScheduler.rescheduleDeadline(DayOfWeek.of(dayValue), autoSendTime)
    }
}
