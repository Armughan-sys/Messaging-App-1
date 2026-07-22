package com.fightclub.attendance.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fightclub.attendance.data.local.entity.MessageTrigger
import com.fightclub.attendance.util.Constants
import com.fightclub.attendance.worker.SendWhatsAppMessageWorker

/**
 * Fires at exactly 4:00 PM on the days [com.fightclub.attendance.domain.scheduler.AlarmScheduler]
 * scheduled it for: unconditionally on Tuesday/Thursday, and as the "no response yet" deadline on
 * Monday/Wednesday/Friday. All of the actual work (checking attendance status, sending the
 * WhatsApp message, logging, re-arming next week's alarm) happens in
 * [SendWhatsAppMessageWorker] so it survives process death and benefits from WorkManager's
 * Doze-aware execution guarantees.
 */
class AutoSendAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val dayValue = intent.getIntExtra(Constants.EXTRA_DAY_OF_WEEK_VALUE, -1)
        if (dayValue !in 1..7) return
        val isDeadline = intent.getBooleanExtra(Constants.EXTRA_IS_DEADLINE, false)
        val trigger = if (isDeadline) MessageTrigger.NO_RESPONSE_DEADLINE else MessageTrigger.AUTOMATIC_SCHEDULE

        val inputData = Data.Builder()
            .putString(Constants.INPUT_MESSAGE_TRIGGER, trigger.name)
            .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, dayValue)
            .build()

        val request = OneTimeWorkRequestBuilder<SendWhatsAppMessageWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "${Constants.WORK_SEND_MESSAGE}_${trigger.name}_$dayValue",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }
}
