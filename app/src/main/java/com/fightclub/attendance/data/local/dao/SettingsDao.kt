package com.fightclub.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fightclub.attendance.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SINGLETON_ID} LIMIT 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SINGLETON_ID} LIMIT 1")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(settings: SettingsEntity)

    @Query("INSERT OR IGNORE INTO settings (id) VALUES (${SettingsEntity.SINGLETON_ID})")
    suspend fun insertDefaultIfMissing()
}
