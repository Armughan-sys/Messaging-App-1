package com.fightclub.attendance.domain.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fightclub.attendance.data.local.entity.AttendanceStatus
import com.fightclub.attendance.data.local.entity.SmsTrigger
import com.fightclub.attendance.data.repository.AttendanceStatusRepository
import com.fightclub.attendance.util.Constants
import com.fightclub.attendance.worker.SendSmsWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that decides what happens when the user answers the attendance question,
 * whether the tap came from the notification's YES/NO actions or from the full-screen activity.
 * Keeping this logic in one class guarantees both entry points behave identically.
 */
@Singleton
class AttendanceResponseHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val attendanceStatusRepository: AttendanceStatusRepository
) {

    suspend fun handleResponse(isAttending: Boolean) {
        // Idempotent: if today was already resolved (e.g. the user tapped both the notification
        // and the full-screen activity), the second call is a harmless no-op.
        if (attendanceStatusRepository.isTodayResolved()) return

        if (isAttending) {
            attendanceStatusRepository.setTodayStatus(AttendanceStatus.ATTENDING)
            return
        }

        attendanceStatusRepository.setTodayStatus(AttendanceStatus.NOT_ATTENDING)
        val request = OneTimeWorkRequestBuilder<SendSmsWorker>()
            .setInputData(
                Data.Builder()
                    .putString(Constants.INPUT_SMS_TRIGGER, SmsTrigger.MANUAL_NO_RESPONSE.name)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(Constants.WORK_SEND_SMS, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
