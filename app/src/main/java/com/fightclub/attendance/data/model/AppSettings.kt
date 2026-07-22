package com.fightclub.attendance.data.model

import com.fightclub.attendance.data.local.entity.SettingsEntity
import java.time.DayOfWeek
import java.time.LocalTime

/** How the user wants the app's theme rendered. */
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

/** The manager contact the app sends attendance SMS messages to. */
data class SavedContact(
    val lookupKey: String,
    val displayName: String,
    val phoneNumber: String
)

/** Domain-level, strongly-typed view of [SettingsEntity]. */
data class AppSettings(
    val contact: SavedContact?,
    val smsMessage: String,
    val promptTime1: LocalTime,
    val promptTime2: LocalTime,
    val promptTime3: LocalTime,
    val autoSendTime: LocalTime,
    val classDays: Set<DayOfWeek>,
    val autoSendDays: Set<DayOfWeek>,
    val themeMode: ThemeMode,
    val hasCompletedFirstLaunchSetup: Boolean
) {
    companion object {
        val DEFAULT = AppSettings(
            contact = null,
            smsMessage = SettingsEntity.DEFAULT_SMS_MESSAGE,
            promptTime1 = LocalTime.of(13, 0),
            promptTime2 = LocalTime.of(15, 0),
            promptTime3 = LocalTime.of(15, 45),
            autoSendTime = LocalTime.of(16, 0),
            classDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            autoSendDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
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

fun SettingsEntity.toDomain(): AppSettings = AppSettings(
    contact = if (contactPhoneNumber != null && contactDisplayName != null && contactLookupKey != null) {
        SavedContact(contactLookupKey, contactDisplayName, contactPhoneNumber)
    } else {
        null
    },
    smsMessage = smsMessage,
    promptTime1 = LocalTime.parse(promptTime1),
    promptTime2 = LocalTime.parse(promptTime2),
    promptTime3 = LocalTime.parse(promptTime3),
    autoSendTime = LocalTime.parse(autoSendTime),
    classDays = classDays.toDaySet(),
    autoSendDays = autoSendDays.toDaySet(),
    themeMode = ThemeMode.valueOf(themeMode),
    hasCompletedFirstLaunchSetup = hasCompletedFirstLaunchSetup
)

fun AppSettings.toEntity(): SettingsEntity = SettingsEntity(
    contactLookupKey = contact?.lookupKey,
    contactDisplayName = contact?.displayName,
    contactPhoneNumber = contact?.phoneNumber,
    smsMessage = smsMessage,
    promptTime1 = promptTime1.toString(),
    promptTime2 = promptTime2.toString(),
    promptTime3 = promptTime3.toString(),
    autoSendTime = autoSendTime.toString(),
    classDays = classDays.toStorageString(),
    autoSendDays = autoSendDays.toStorageString(),
    themeMode = themeMode.name,
    hasCompletedFirstLaunchSetup = hasCompletedFirstLaunchSetup
)
