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

    @Query("DELETE FROM attendance_status WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
