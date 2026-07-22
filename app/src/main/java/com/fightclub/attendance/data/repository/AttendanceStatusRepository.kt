package com.fightclub.attendance.data.repository

import com.fightclub.attendance.data.local.dao.AttendanceStatusDao
import com.fightclub.attendance.data.local.entity.AttendanceStatus
import com.fightclub.attendance.data.local.entity.AttendanceStatusEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

interface AttendanceStatusRepository {
    /** Returns today's status, defaulting to [AttendanceStatus.PENDING] if nothing is recorded yet. */
    suspend fun getTodayStatus(): AttendanceStatus

    fun observeTodayStatus(): Flow<AttendanceStatus>

    suspend fun setTodayStatus(status: AttendanceStatus)

    /** True once today has already been answered or auto-resolved, so further prompts should stay silent. */
    suspend fun isTodayResolved(): Boolean

    suspend fun pruneOldEntries()
}

@Singleton
class AttendanceStatusRepositoryImpl @Inject constructor(
    private val dao: AttendanceStatusDao,
    private val clock: Clock
) : AttendanceStatusRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun today(): String = LocalDate.now(clock).format(dateFormatter)

    override suspend fun getTodayStatus(): AttendanceStatus {
        val entity = dao.getByDate(today())
        return entity?.status?.let { AttendanceStatus.valueOf(it) } ?: AttendanceStatus.PENDING
    }

    override fun observeTodayStatus(): Flow<AttendanceStatus> =
        dao.observeByDate(today()).map { entity ->
            entity?.status?.let { AttendanceStatus.valueOf(it) } ?: AttendanceStatus.PENDING
        }

    override suspend fun setTodayStatus(status: AttendanceStatus) {
        dao.upsert(
            AttendanceStatusEntity(
                date = today(),
                status = status.name,
                updatedAtMillis = clock.millis()
            )
        )
    }

    override suspend fun isTodayResolved(): Boolean =
        getTodayStatus() != AttendanceStatus.PENDING

    override suspend fun pruneOldEntries() {
        val cutoff = LocalDate.now(clock).minusDays(30).format(dateFormatter)
        dao.deleteOlderThan(cutoff)
    }
}
