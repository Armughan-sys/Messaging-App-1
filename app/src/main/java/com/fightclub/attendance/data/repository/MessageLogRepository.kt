package com.fightclub.attendance.data.repository

import com.fightclub.attendance.data.local.dao.MessageLogDao
import com.fightclub.attendance.data.local.entity.MessageDeliveryStatus
import com.fightclub.attendance.data.local.entity.MessageLogEntity
import com.fightclub.attendance.data.local.entity.MessageTrigger
import com.fightclub.attendance.data.model.MessageLogEntry
import com.fightclub.attendance.data.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface MessageLogRepository {
    fun observeLatest(): Flow<MessageLogEntry?>
    fun observeRecent(limit: Int = 20): Flow<List<MessageLogEntry>>

    suspend fun recordAttempt(
        recipientName: String,
        recipientNumber: String,
        message: String,
        trigger: MessageTrigger,
        timestampMillis: Long
    ): Long

    suspend fun updateStatus(logId: Long, status: MessageDeliveryStatus, failureReason: String? = null)
}

@Singleton
class MessageLogRepositoryImpl @Inject constructor(
    private val messageLogDao: MessageLogDao
) : MessageLogRepository {

    override fun observeLatest(): Flow<MessageLogEntry?> =
        messageLogDao.observeLatest().map { it?.toDomain() }

    override fun observeRecent(limit: Int): Flow<List<MessageLogEntry>> =
        messageLogDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun recordAttempt(
        recipientName: String,
        recipientNumber: String,
        message: String,
        trigger: MessageTrigger,
        timestampMillis: Long
    ): Long = messageLogDao.insert(
        MessageLogEntity(
            timestampMillis = timestampMillis,
            recipientName = recipientName,
            recipientNumber = recipientNumber,
            message = message,
            status = MessageDeliveryStatus.PENDING.name,
            trigger = trigger.name
        )
    )

    override suspend fun updateStatus(logId: Long, status: MessageDeliveryStatus, failureReason: String?) {
        val existing = messageLogDao.getById(logId) ?: return
        messageLogDao.update(existing.copy(status = status.name, failureReason = failureReason))
    }
}
