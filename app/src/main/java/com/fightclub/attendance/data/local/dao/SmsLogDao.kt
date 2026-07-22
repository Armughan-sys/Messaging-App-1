package com.fightclub.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.fightclub.attendance.data.local.entity.SmsLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {

    @Insert
    suspend fun insert(entry: SmsLogEntity): Long

    @Update
    suspend fun update(entry: SmsLogEntity)

    @Query("SELECT * FROM sms_log ORDER BY timestampMillis DESC LIMIT 1")
    fun observeLatest(): Flow<SmsLogEntity?>

    @Query("SELECT * FROM sms_log ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_log WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsLogEntity?
}
