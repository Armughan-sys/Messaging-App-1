package com.fightclub.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fightclub.attendance.data.local.entity.AttendanceStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceStatusDao {

    @Upsert
    suspend fun upsert(entity: AttendanceStatusEntity)

    @Query("SELECT * FROM attendance_status WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): AttendanceStatusEntity?

    @Query("SELECT * FROM attendance_status WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<AttendanceStatusEntity?>

    /**
     * Counts days with [status] whose date falls within [startDate, endDate] (both inclusive).
     * Dates are stored as ISO "yyyy-MM-dd" strings, which sort identically whether compared
     * lexicographically or chronologically, so a plain string range comparison is correct here.
     */
    @Query(
        "SELECT COUNT(*) FROM attendance_status " +
            "WHERE status = :status AND date >= :startDate AND date <= :endDate"
    )
    suspend fun countByStatusInRange(status: String, startDate: String, endDate: String): Int

    @Query("DELETE FROM attendance_status WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
