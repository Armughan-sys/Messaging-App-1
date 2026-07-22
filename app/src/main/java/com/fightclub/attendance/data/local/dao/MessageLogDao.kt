package com.fightclub.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.fightclub.attendance.data.local.entity.MessageLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageLogDao {

    @Insert
    suspend fun insert(entry: MessageLogEntity): Long

    @Update
    suspend fun update(entry: MessageLogEntity)

    @Query("SELECT * FROM message_log ORDER BY timestampMillis DESC LIMIT 1")
    fun observeLatest(): Flow<MessageLogEntity?>

    @Query("SELECT * FROM message_log ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<MessageLogEntity>>

    @Query("SELECT * FROM message_log WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessageLogEntity?
}
