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

    val messageText: String = DEFAULT_MESSAGE_TEXT,

    // Stored as "HH:mm" (24-hour) strings so they sort and parse trivially.
    // What time the class itself starts, each active day.
    val classTime: String = "18:00",
    // How many hours before classTime the single daily attendance prompt fires.
    val promptLeadHours: Int = 3,
    // The deadline: if there's still no response by this clock time, the message auto-sends.
    val autoSendTime: String = "17:00",

    // Stored as comma-separated java.time.DayOfWeek names, e.g. "MONDAY,TUESDAY,...,SATURDAY".
    // Every active day gets the same flow: prompt -> (YES/NO/ignored) -> deadline auto-send.
    val activeDays: String = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",

    // "LIGHT", "DARK", or "SYSTEM".
    val themeMode: String = "SYSTEM",

    val hasCompletedFirstLaunchSetup: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_MESSAGE_TEXT = "AoA Abdullah\nSaim and me won't be attending the class today"
    }
}
