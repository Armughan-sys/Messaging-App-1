package com.fightclub.attendance.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fightclub.attendance.R
import com.fightclub.attendance.receiver.NotificationActionReceiver
import com.fightclub.attendance.util.Constants
import com.fightclub.attendance.util.PromptSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central place for building and posting every notification the app shows: the attendance
 * prompt (with a full-screen intent so it can appear over the lock screen) and the low-priority
 * SMS delivery status notification.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val promptChannel = NotificationChannel(
            Constants.CHANNEL_ATTENDANCE_PROMPT,
            context.getString(R.string.channel_attendance_prompt_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_attendance_prompt_description)
            enableVibration(true)
        }

        val statusChannel = NotificationChannel(
            Constants.CHANNEL_SMS_STATUS,
            context.getString(R.string.channel_sms_status_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_sms_status_description)
        }

        manager.createNotificationChannels(listOf(promptChannel, statusChannel))
    }

    /** Shows (or re-shows) today's attendance question as a full-screen, high-priority notification. */
    fun showAttendancePrompt(slot: PromptSlot) {
        if (!hasPostNotificationPermission()) return

        val fullScreenIntent = Intent(context, FullScreenAttendanceActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            Constants.NOTIFICATION_ID_ATTENDANCE_PROMPT,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val yesPendingIntent = actionPendingIntent(Constants.ACTION_ATTENDANCE_YES)
        val noPendingIntent = actionPendingIntent(Constants.ACTION_ATTENDANCE_NO)

        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_ATTENDANCE_PROMPT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.attendance_prompt_title))
            .setContentText(context.getString(R.string.attendance_prompt_question))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, context.getString(R.string.action_yes), yesPendingIntent)
            .addAction(0, context.getString(R.string.action_no), noPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        NotificationManagerCompat.from(context)
            .notify(Constants.NOTIFICATION_ID_ATTENDANCE_PROMPT, notification)
    }

    fun cancelAttendancePrompt() {
        NotificationManagerCompat.from(context).cancel(Constants.NOTIFICATION_ID_ATTENDANCE_PROMPT)
    }

    fun showSmsStatusNotification(success: Boolean, reason: String? = null) {
        if (!hasPostNotificationPermission()) return

        val title = if (success) {
            context.getString(R.string.sms_status_sent_title)
        } else {
            context.getString(R.string.sms_status_failed_title)
        }
        val text = if (success) {
            context.getString(R.string.sms_status_sent_text)
        } else {
            reason ?: context.getString(R.string.sms_status_failed_text)
        }

        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_SMS_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(Constants.NOTIFICATION_ID_SMS_STATUS, notification)
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        // Distinct request codes per action so the two PendingIntents never collide.
        val requestCode = Constants.NOTIFICATION_ID_ATTENDANCE_PROMPT + action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasPostNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}
