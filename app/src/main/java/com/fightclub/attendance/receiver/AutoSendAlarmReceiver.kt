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
 * Fires once per active day at the configured deadline time
 * ([com.fightclub.attendance.data.model.AppSettings.autoSendTime]). All of the actual work
 * (checking whether the attendance prompt was already answered, sending the WhatsApp message,
 * logging, re-arming next week's alarm) happens in [SendWhatsAppMessageWorker] so it survives
 * process death and benefits from WorkManager's Doze-aware execution guarantees.
 */
class AutoSendAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val dayValue = intent.getIntExtra(Constants.EXTRA_DAY_OF_WEEK_VALUE, -1)
        if (dayValue !in 1..7) return

        val inputData = Data.Builder()
            .putString(Constants.INPUT_MESSAGE_TRIGGER, MessageTrigger.NO_RESPONSE_DEADLINE.name)
            .putInt(Constants.EXTRA_DAY_OF_WEEK_VALUE, dayValue)
            .build()

        val request = OneTimeWorkRequestBuilder<SendWhatsAppMessageWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "${Constants.WORK_SEND_MESSAGE}_${MessageTrigger.NO_RESPONSE_DEADLINE.name}_$dayValue",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }
}
