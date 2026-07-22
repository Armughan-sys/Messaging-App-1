package com.fightclub.attendance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fightclub.attendance.data.local.dao.AttendanceStatusDao
import com.fightclub.attendance.data.local.dao.MessageLogDao
import com.fightclub.attendance.data.local.dao.SettingsDao
import com.fightclub.attendance.data.local.entity.AttendanceStatusEntity
import com.fightclub.attendance.data.local.entity.MessageLogEntity
import com.fightclub.attendance.data.local.entity.SettingsEntity

@Database(
    entities = [
        SettingsEntity::class,
        MessageLogEntity::class,
        AttendanceStatusEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun messageLogDao(): MessageLogDao
    abstract fun attendanceStatusDao(): AttendanceStatusDao

    companion object {
        const val DATABASE_NAME = "fight_club_attendance.db"
    }
}
