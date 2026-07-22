package com.fightclub.attendance.data.repository

import com.fightclub.attendance.data.local.dao.SmsLogDao
import com.fightclub.attendance.data.local.entity.SmsDeliveryStatus
import com.fightclub.attendance.data.local.entity.SmsLogEntity
import com.fightclub.attendance.data.local.entity.SmsTrigger
import com.fightclub.attendance.data.model.SmsLogEntry
import com.fightclub.attendance.data.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface SmsLogRepository {
    fun observeLatest(): Flow<SmsLogEntry?>
    fun observeRecent(limit: Int = 20): Flow<List<SmsLogEntry>>

    suspend fun recordAttempt(
        recipientName: String,
        recipientNumber: String,
        message: String,
        trigger: SmsTrigger,
        timestampMillis: Long
    ): Long

    suspend fun updateStatus(logId: Long, status: SmsDeliveryStatus, failureReason: String? = null)
}

@Singleton
class SmsLogRepositoryImpl @Inject constructor(
    private val smsLogDao: SmsLogDao
) : SmsLogRepository {

    override fun observeLatest(): Flow<SmsLogEntry?> =
        smsLogDao.observeLatest().map { it?.toDomain() }

    override fun observeRecent(limit: Int): Flow<List<SmsLogEntry>> =
        smsLogDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun recordAttempt(
        recipientName: String,
        recipientNumber: String,
        message: String,
        trigger: SmsTrigger,
        timestampMillis: Long
    ): Long = smsLogDao.insert(
        SmsLogEntity(
            timestampMillis = timestampMillis,
            recipientName = recipientName,
            recipientNumber = recipientNumber,
            message = message,
            status = SmsDeliveryStatus.PENDING.name,
            trigger = trigger.name
        )
    )

    override suspend fun updateStatus(logId: Long, status: SmsDeliveryStatus, failureReason: String?) {
        val existing = smsLogDao.getById(logId) ?: return
        smsLogDao.update(existing.copy(status = status.name, failureReason = failureReason))
    }
}
