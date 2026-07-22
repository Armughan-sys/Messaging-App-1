package com.fightclub.attendance.data.model

import com.fightclub.attendance.data.local.entity.SettingsEntity
import java.time.DayOfWeek
import java.time.LocalTime

/** How the user wants the app's theme rendered. */
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

/** The manager contact the app sends attendance WhatsApp messages to. */
data class SavedContact(
    val lookupKey: String,
    val displayName: String,
    val phoneNumber: String
)

private val DEFAULT_CLASS_TIME: LocalTime = LocalTime.of(18, 0)

/**
 * Domain-level, strongly-typed view of [SettingsEntity].
 *
 * Every day in [activeDays] follows the same flow: at that day's prompt time (see
 * [promptTimeFor] — that day's [classTimes] entry minus [promptLeadHours]) the attendance
 * question is shown; if ignored, the message auto-sends at [autoSendTime]. [classTimes] lets
 * different days (e.g. Friday/Saturday) run on a different class schedule than the rest.
 */
data class AppSettings(
    val contact: SavedContact?,
    val messageText: String,
    val classTimes: Map<DayOfWeek, LocalTime>,
    val promptLeadHours: Int,
    val autoSendTime: LocalTime,
    val activeDays: Set<DayOfWeek>,
    val themeMode: ThemeMode,
    val hasCompletedFirstLaunchSetup: Boolean
) {
    /** [classTimes] always holds all 7 days, but this falls back safely if a day is ever missing. */
    fun classTimeFor(day: DayOfWeek): LocalTime = classTimes[day] ?: DEFAULT_CLASS_TIME

    /**
     * When that day's attendance prompt fires. Assumes [promptLeadHours] doesn't wrap the class
     * time past midnight into the previous calendar day — true for any realistic evening class
     * time, which is all this app is designed for.
     */
    fun promptTimeFor(day: DayOfWeek): LocalTime = classTimeFor(day).minusHours(promptLeadHours.toLong())

    companion object {
        val DEFAULT = AppSettings(
            contact = null,
            messageText = SettingsEntity.DEFAULT_MESSAGE_TEXT,
            classTimes = DayOfWeek.values().associateWith { DEFAULT_CLASS_TIME },
            promptLeadHours = 3,
            autoSendTime = LocalTime.of(17, 0),
            activeDays = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
            ),
            themeMode = ThemeMode.SYSTEM,
            hasCompletedFirstLaunchSetup = false
        )
    }
}

private fun String.toDaySet(): Set<DayOfWeek> =
    split(",")
        .filter { it.isNotBlank() }
        .map { DayOfWeek.valueOf(it.trim()) }
        .toSet()

private fun Set<DayOfWeek>.toStorageString(): String =
    joinToString(",") { it.name }

private fun String.toClassTimesMap(): Map<DayOfWeek, LocalTime> {
    val parsed = split(",")
        .filter { it.isNotBlank() }
        .associate { entry ->
            val (dayName, time) = entry.split("=")
            DayOfWeek.valueOf(dayName.trim()) to LocalTime.parse(time.trim())
        }
    // Guarantee every day has an entry even if the stored string is missing one (e.g. an older
    // save from before a day existed), so classTimeFor() never needs to guess silently.
    return DayOfWeek.values().associateWith { parsed[it] ?: DEFAULT_CLASS_TIME }
}

private fun Map<DayOfWeek, LocalTime>.toStorageString(): String =
    DayOfWeek.values().joinToString(",") { day -> "${day.name}=${(this[day] ?: DEFAULT_CLASS_TIME)}" }

fun SettingsEntity.toDomain(): AppSettings = AppSettings(
    contact = if (contactPhoneNumber != null && contactDisplayName != null && contactLookupKey != null) {
        SavedContact(contactLookupKey, contactDisplayName, contactPhoneNumber)
    } else {
        null
    },
    messageText = messageText,
    classTimes = classTimesByDay.toClassTimesMap(),
    promptLeadHours = promptLeadHours,
    autoSendTime = LocalTime.parse(autoSendTime),
    activeDays = activeDays.toDaySet(),
    themeMode = ThemeMode.valueOf(themeMode),
    hasCompletedFirstLaunchSetup = hasCompletedFirstLaunchSetup
)

fun AppSettings.toEntity(): SettingsEntity = SettingsEntity(
    contactLookupKey = contact?.lookupKey,
    contactDisplayName = contact?.displayName,
    contactPhoneNumber = contact?.phoneNumber,
    messageText = messageText,
    classTimesByDay = classTimes.toStorageString(),
    promptLeadHours = promptLeadHours,
    autoSendTime = autoSendTime.toString(),
    activeDays = activeDays.toStorageString(),
    themeMode = themeMode.name,
    hasCompletedFirstLaunchSetup = hasCompletedFirstLaunchSetup
)
