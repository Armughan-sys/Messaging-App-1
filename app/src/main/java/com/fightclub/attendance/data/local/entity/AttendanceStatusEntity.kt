package com.fightclub.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks whether today's attendance question has been answered yet. Keyed by an ISO-8601 date
 * string (yyyy-MM-dd) so at most one row exists per calendar day. This is what lets the 3:00 PM
 * and 3:45 PM reminders (and the 4:00 PM deadline) know to stay silent once the user has already
 * responded.
 */
@Entity(tableName = "attendance_status")
data class AttendanceStatusEntity(
    @PrimaryKey
    val date: String,

    /** One of [AttendanceStatus]. */
    val status: String,

    val updatedAtMillis: Long
)

enum class AttendanceStatus {
    /** Question has been asked but not yet answered. */
    PENDING,

    /** User confirmed they are attending; no SMS is sent. */
    ATTENDING,

    /** User said they are not attending; the SMS was sent immediately. */
    NOT_ATTENDING,

    /** No response arrived before the deadline; the SMS was sent automatically. */
    AUTO_SENT_NO_RESPONSE
}
