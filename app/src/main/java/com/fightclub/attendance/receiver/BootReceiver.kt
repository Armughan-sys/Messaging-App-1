package com.fightclub.attendance.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fightclub.attendance.util.Constants
import com.fightclub.attendance.worker.RescheduleWorker

/**
 * Restores every alarm after the device reboots (or the app is updated, which also clears
 * pending alarms on some OEM skins). Deliberately does not touch Room or AlarmManager directly:
 * it just hands off to [RescheduleWorker] via WorkManager, which is durable across process death
 * and is the pattern Android recommends for boot-time work.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val request = OneTimeWorkRequestBuilder<RescheduleWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(Constants.WORK_RESCHEDULE_ALARMS, ExistingWorkPolicy.REPLACE, request)
    }
}
