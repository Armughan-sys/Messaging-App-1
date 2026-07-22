package com.fightclub.attendance.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.fightclub.attendance.data.local.AppDatabase
import com.fightclub.attendance.data.model.SavedContact
import com.fightclub.attendance.data.model.ThemeMode
import com.fightclub.attendance.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = SettingsRepositoryImpl(database.settingsDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getSettings returns defaults when nothing has been saved yet`() = runTest {
        val settings = repository.getSettings()

        assertNull(settings.contact)
        assertEquals("AoA Abdullah\nSaim and me won't be attending the class today", settings.messageText)
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
            ),
            settings.activeDays
        )
        for (day in DayOfWeek.values()) {
            assertEquals("class time mismatch for $day", LocalTime.of(18, 0), settings.classTimeFor(day))
        }
        assertEquals(3, settings.promptLeadHours)
        assertEquals(LocalTime.of(15, 0), settings.promptTimeFor(DayOfWeek.FRIDAY))
    }

    @Test
    fun `updateSettings with a different Friday and Saturday class time persists per-day`() = runTest {
        val first = repository.getSettings()
        val updated = first.copy(
            classTimes = first.classTimes +
                (DayOfWeek.FRIDAY to LocalTime.of(19, 30)) +
                (DayOfWeek.SATURDAY to LocalTime.of(10, 0))
        )

        repository.updateSettings(updated)

        val result = repository.getSettings()
        assertEquals(LocalTime.of(19, 30), result.classTimeFor(DayOfWeek.FRIDAY))
        assertEquals(LocalTime.of(10, 0), result.classTimeFor(DayOfWeek.SATURDAY))
        // Untouched days keep their previous time.
        assertEquals(LocalTime.of(18, 0), result.classTimeFor(DayOfWeek.MONDAY))
    }

    @Test
    fun `updateContact persists and is reflected by observeSettings`() = runTest {
        val contact = SavedContact(
            lookupKey = "abc123",
            displayName = "Abdullah Malik KAK",
            phoneNumber = "+15551234567"
        )

        repository.updateContact(contact)

        repository.observeSettings().test {
            val emitted = awaitItem()
            assertEquals(contact, emitted.contact)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateSettings overwrites the single settings row rather than inserting a new one`() = runTest {
        val first = repository.getSettings()
        repository.updateSettings(first.copy(themeMode = ThemeMode.DARK))
        repository.updateSettings(first.copy(themeMode = ThemeMode.LIGHT))

        val result = repository.getSettings()
        assertEquals(ThemeMode.LIGHT, result.themeMode)
    }

    @Test
    fun `markFirstLaunchSetupComplete flips the flag without touching other fields`() = runTest {
        val contact = SavedContact("key", "Abdullah Malik KAK", "+15550001111")
        repository.updateContact(contact)

        repository.markFirstLaunchSetupComplete()

        val settings = repository.getSettings()
        assertEquals(true, settings.hasCompletedFirstLaunchSetup)
        assertEquals(contact, settings.contact)
    }
}
