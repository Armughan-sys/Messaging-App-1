package com.fightclub.attendance.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fightclub.attendance.domain.scheduler.AttendanceResponseHandler
import com.fightclub.attendance.notification.NotificationHelper
import com.fightclub.attendance.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles taps on the YES / NO actions attached directly to the attendance-prompt notification,
 * so the user can respond without ever opening the app or unlocking the phone.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var attendanceResponseHandler: AttendanceResponseHandler

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val isAttending = when (intent.action) {
            Constants.ACTION_ATTENDANCE_YES -> true
            Constants.ACTION_ATTENDANCE_NO -> false
            else -> return
        }

        notificationHelper.cancelAttendancePrompt()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                attendanceResponseHandler.handleResponse(isAttending)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
