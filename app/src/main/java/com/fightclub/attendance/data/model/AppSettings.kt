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

/**
 * Domain-level, strongly-typed view of [SettingsEntity].
 *
 * Every day in [activeDays] follows the same flow: at [promptTime] (computed as [classTime] minus
 * [promptLeadHours]) the attendance question is shown; if ignored, the message auto-sends at
 * [autoSendTime].
 */
data class AppSettings(
    val contact: SavedContact?,
    val messageText: String,
    val classTime: LocalTime,
    val promptLeadHours: Int,
    val autoSendTime: LocalTime,
    val activeDays: Set<DayOfWeek>,
    val themeMode: ThemeMode,
    val hasCompletedFirstLaunchSetup: Boolean
) {
    /**
     * When the single daily attendance prompt fires. Assumes [promptLeadHours] doesn't wrap
     * [classTime] past midnight into the previous calendar day — true for any realistic evening
     * class time, which is all this app is designed for.
     */
    val promptTime: LocalTime
        get() = classTime.minusHours(promptLeadHours.toLong())

    companion object {
        val DEFAULT = AppSettings(
            contact = null,
            messageText = SettingsEntity.DEFAULT_MESSAGE_TEXT,
            classTime = LocalTime.of(18, 0),
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

fun SettingsEntity.toDomain(): AppSettings = AppSettings(
    contact = if (contactPhoneNumber != null && contactDisplayName != null && contactLookupKey != null) {
        SavedContact(contactLookupKey, contactDisplayName, contactPhoneNumber)
    } else {
        null
    },
    messageText = messageText,
    classTime = LocalTime.parse(classTime),
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
    classTime = classTime.toString(),
    promptLeadHours = promptLeadHours,
    autoSendTime = autoSendTime.toString(),
    activeDays = activeDays.toStorageString(),
    themeMode = themeMode.name,
    hasCompletedFirstLaunchSetup = hasCompletedFirstLaunchSetup
)
