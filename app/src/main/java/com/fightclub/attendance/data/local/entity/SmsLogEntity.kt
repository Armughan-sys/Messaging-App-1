package com.fightclub.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per SMS the app has attempted to send. Used to render "Last SMS sent" on the home
 * screen and to keep a full audit trail of automatic vs. manual sends.
 */
@Entity(tableName = "sms_log")
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val timestampMillis: Long,
    val recipientName: String,
    val recipientNumber: String,
    val message: String,

    /** One of [SmsDeliveryStatus]. */
    val status: String,

    /** One of [SmsTrigger]. */
    val trigger: String,

    val failureReason: String? = null
)

/** Lifecycle of a single SMS send attempt. */
enum class SmsDeliveryStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}

/** What caused an SMS to be sent, shown in history for transparency. */
enum class SmsTrigger {
    /** Unconditional Tuesday/Thursday schedule. */
    AUTOMATIC_SCHEDULE,

    /** User tapped NO on an attendance prompt. */
    MANUAL_NO_RESPONSE,

    /** No response was received by the 4:00 PM deadline. */
    NO_RESPONSE_DEADLINE
}
