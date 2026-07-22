package com.fightclub.attendance.util

/** App-wide constants shared across schedulers, receivers, workers, and UI. */
object Constants {

    /** The manager this app searches for and sends attendance WhatsApp messages to. */
    const val TARGET_CONTACT_DISPLAY_NAME = "Abdullah Malik KAK"

    /** Package name of the (non-Business) WhatsApp app the automation targets. */
    const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"

    // --- Notification channels ---
    const val CHANNEL_ATTENDANCE_PROMPT = "attendance_prompt_channel"
    const val CHANNEL_MESSAGE_STATUS = "message_status_channel"

    const val NOTIFICATION_ID_ATTENDANCE_PROMPT = 1001
    const val NOTIFICATION_ID_MESSAGE_STATUS = 1002
    const val NOTIFICATION_ID_ACCESSIBILITY_DISABLED = 1003

    // --- WorkManager unique work names ---
    const val WORK_SEND_MESSAGE = "work_send_message"
    const val WORK_RESCHEDULE_ALARMS = "work_reschedule_alarms"

    // --- Intent extra keys ---
    const val EXTRA_DAY_OF_WEEK_VALUE = "extra_day_of_week_value"
    const val EXTRA_ATTENDANCE_ACTION = "extra_attendance_action"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    const val ACTION_ATTENDANCE_YES = "com.fightclub.attendance.action.YES"
    const val ACTION_ATTENDANCE_NO = "com.fightclub.attendance.action.NO"

    // --- Worker input data keys ---
    const val INPUT_MESSAGE_TRIGGER = "input_message_trigger"

    // --- AlarmManager request-code namespaces ---
    // Each namespace is offset far enough apart that (namespace + DayOfWeek.value [1-7]) can
    // never collide with another namespace, which is what guarantees at most one pending alarm
    // per (purpose, day) pair and therefore prevents duplicate scheduled jobs. Every active day
    // now gets exactly two alarms: one prompt, one deadline.
    const val REQUEST_CODE_PROMPT_BASE = 2000
    const val REQUEST_CODE_DEADLINE_BASE = 5000
}
