package com.fightclub.attendance.data.model

import com.fightclub.attendance.data.local.entity.MessageDeliveryStatus
import com.fightclub.attendance.data.local.entity.MessageLogEntity
import com.fightclub.attendance.data.local.entity.MessageTrigger
import java.time.Instant

data class MessageLogEntry(
    val id: Long,
    val timestamp: Instant,
    val recipientName: String,
    val recipientNumber: String,
    val message: String,
    val status: MessageDeliveryStatus,
    val trigger: MessageTrigger,
    val failureReason: String?
)

fun MessageLogEntity.toDomain(): MessageLogEntry = MessageLogEntry(
    id = id,
    timestamp = Instant.ofEpochMilli(timestampMillis),
    recipientName = recipientName,
    recipientNumber = recipientNumber,
    message = message,
    status = MessageDeliveryStatus.valueOf(status),
    trigger = MessageTrigger.valueOf(trigger),
    failureReason = failureReason
)
