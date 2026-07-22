package com.fightclub.attendance.data.repository

import com.fightclub.attendance.data.local.dao.AttendanceStatusDao
import com.fightclub.attendance.data.local.entity.AttendanceStatus
import com.fightclub.attendance.data.local.entity.AttendanceStatusEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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

    /** Classes attended (status [AttendanceStatus.ATTENDING]) from this week's Monday through today. */
    suspend fun getAttendedCountThisWeek(): Int

    /** Classes attended (status [AttendanceStatus.ATTENDING]) from the 1st of this month through today. */
    suspend fun getAttendedCountThisMonth(): Int
}

@Singleton
class AttendanceStatusRepositoryImpl @Inject constructor(
    private val dao: AttendanceStatusDao,
    private val clock: Clock
) : AttendanceStatusRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun today(): LocalDate = LocalDate.now(clock)
    private fun LocalDate.iso(): String = format(dateFormatter)

    override suspend fun getTodayStatus(): AttendanceStatus {
        val entity = dao.getByDate(today().iso())
        return entity?.status?.let { AttendanceStatus.valueOf(it) } ?: AttendanceStatus.PENDING
    }

    override fun observeTodayStatus(): Flow<AttendanceStatus> =
        dao.observeByDate(today().iso()).map { entity ->
            entity?.status?.let { AttendanceStatus.valueOf(it) } ?: AttendanceStatus.PENDING
        }

    override suspend fun setTodayStatus(status: AttendanceStatus) {
        dao.upsert(
            AttendanceStatusEntity(
                date = today().iso(),
                status = status.name,
                updatedAtMillis = clock.millis()
            )
        )
    }

    override suspend fun isTodayResolved(): Boolean =
        getTodayStatus() != AttendanceStatus.PENDING

    override suspend fun pruneOldEntries() {
        val cutoff = today().minusDays(30).iso()
        dao.deleteOlderThan(cutoff)
    }

    override suspend fun getAttendedCountThisWeek(): Int {
        val today = today()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return dao.countByStatusInRange(AttendanceStatus.ATTENDING.name, weekStart.iso(), today.iso())
    }

    override suspend fun getAttendedCountThisMonth(): Int {
        val today = today()
        val monthStart = today.withDayOfMonth(1)
        return dao.countByStatusInRange(AttendanceStatus.ATTENDING.name, monthStart.iso(), today.iso())
    }
}
