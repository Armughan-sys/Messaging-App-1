package com.fightclub.attendance.data.model

import com.fightclub.attendance.data.local.entity.SmsDeliveryStatus
import com.fightclub.attendance.data.local.entity.SmsLogEntity
import com.fightclub.attendance.data.local.entity.SmsTrigger
import java.time.Instant

data class SmsLogEntry(
    val id: Long,
    val timestamp: Instant,
    val recipientName: String,
    val recipientNumber: String,
    val message: String,
    val status: SmsDeliveryStatus,
    val trigger: SmsTrigger,
    val failureReason: String?
)

fun SmsLogEntity.toDomain(): SmsLogEntry = SmsLogEntry(
    id = id,
    timestamp = Instant.ofEpochMilli(timestampMillis),
    recipientName = recipientName,
    recipientNumber = recipientNumber,
    message = message,
    status = SmsDeliveryStatus.valueOf(status),
    trigger = SmsTrigger.valueOf(trigger),
    failureReason = failureReason
)
