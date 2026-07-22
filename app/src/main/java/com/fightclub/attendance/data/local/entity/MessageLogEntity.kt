package com.fightclub.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per WhatsApp attendance message the app has attempted to send. Used to render "Last
 * message sent" on the home screen and to keep a full audit trail of automatic vs. manual sends.
 */
@Entity(tableName = "message_log")
data class MessageLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val timestampMillis: Long,
    val recipientName: String,
    val recipientNumber: String,
    val message: String,

    /** One of [MessageDeliveryStatus]. */
    val status: String,

    /** One of [MessageTrigger]. */
    val trigger: String,

    val failureReason: String? = null
)

/** Lifecycle of a single WhatsApp message send attempt. */
enum class MessageDeliveryStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}

/** What caused a message to be sent, shown in history for transparency. */
enum class MessageTrigger {
    /** User tapped NO on the attendance prompt. */
    MANUAL_NO_RESPONSE,

    /** No response was received by the daily deadline. */
    NO_RESPONSE_DEADLINE
}
