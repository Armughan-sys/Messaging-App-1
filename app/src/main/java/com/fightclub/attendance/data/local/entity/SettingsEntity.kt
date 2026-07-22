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

    // What time class starts, per day of week — different days (e.g. Friday/Saturday) can run on
    // a different schedule than the rest. Stored as "DAYNAME=HH:mm" pairs joined by commas, e.g.
    // "MONDAY=18:00,TUESDAY=18:00,...,SATURDAY=10:00"; always holds all 7 days so a day newly
    // added to activeDays already has a sensible time.
    val classTimesByDay: String = DEFAULT_CLASS_TIMES,
    // How many hours before that day's class time the daily attendance prompt fires.
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
        const val DEFAULT_CLASS_TIMES =
            "MONDAY=18:00,TUESDAY=18:00,WEDNESDAY=18:00,THURSDAY=18:00,FRIDAY=18:00,SATURDAY=18:00,SUNDAY=18:00"
    }
}
