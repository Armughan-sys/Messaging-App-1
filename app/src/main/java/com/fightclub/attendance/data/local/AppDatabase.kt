package com.fightclub.attendance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fightclub.attendance.data.local.dao.AttendanceStatusDao
import com.fightclub.attendance.data.local.dao.SettingsDao
import com.fightclub.attendance.data.local.dao.SmsLogDao
import com.fightclub.attendance.data.local.entity.AttendanceStatusEntity
import com.fightclub.attendance.data.local.entity.SettingsEntity
import com.fightclub.attendance.data.local.entity.SmsLogEntity

@Database(
    entities = [
        SettingsEntity::class,
        SmsLogEntity::class,
        AttendanceStatusEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun smsLogDao(): SmsLogDao
    abstract fun attendanceStatusDao(): AttendanceStatusDao

    companion object {
        const val DATABASE_NAME = "fight_club_attendance.db"
    }
}
