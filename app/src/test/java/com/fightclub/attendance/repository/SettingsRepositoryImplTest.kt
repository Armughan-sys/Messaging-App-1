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
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), settings.classDays)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), settings.autoSendDays)
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
