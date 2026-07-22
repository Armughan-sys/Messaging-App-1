package com.fightclub.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table holding every user-configurable setting. A fixed [id] of 1 guarantees the
 * table never grows past one row; reads/writes always target that row.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,

    val contactLookupKey: String? = null,
    val contactDisplayName: String? = null,
    val contactPhoneNumber: String? = null,

    val smsMessage: String = DEFAULT_SMS_MESSAGE,

    // Stored as "HH:mm" (24-hour) strings so they sort and parse trivially.
    val promptTime1: String = "13:00",
    val promptTime2: String = "15:00",
    val promptTime3: String = "15:45",
    val autoSendTime: String = "16:00",

    // Stored as comma-separated java.time.DayOfWeek names, e.g. "MONDAY,WEDNESDAY,FRIDAY".
    val classDays: String = "MONDAY,WEDNESDAY,FRIDAY",
    val autoSendDays: String = "TUESDAY,THURSDAY",

    // "LIGHT", "DARK", or "SYSTEM".
    val themeMode: String = "SYSTEM",

    val hasCompletedFirstLaunchSetup: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_SMS_MESSAGE = "AoA Abdullah\nSaim and me won't be attending the class today"
    }
}
