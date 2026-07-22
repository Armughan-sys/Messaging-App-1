package com.fightclub.attendance

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fightclub.attendance.data.local.AppDatabase
import com.fightclub.attendance.data.local.entity.AttendanceStatus
import com.fightclub.attendance.data.local.entity.AttendanceStatusEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs Room against the real, on-device SQLite implementation (rather than Robolectric's shadow)
 * as a sanity check that the schema is valid and every DAO query actually executes on a device.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseInstrumentedTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun settingsDaoInsertDefaultThenReadRoundTrips() = runBlocking {
        database.settingsDao().insertDefaultIfMissing()
        val settings = database.settingsDao().get()
        assertEquals(1, settings?.id)
    }

    @Test
    fun attendanceStatusDaoUpsertThenReadRoundTrips() = runBlocking {
        val today = "2024-01-02"
        database.attendanceStatusDao().upsert(
            AttendanceStatusEntity(date = today, status = AttendanceStatus.NOT_ATTENDING.name, updatedAtMillis = 0L)
        )

        val result = database.attendanceStatusDao().getByDate(today)
        assertEquals(AttendanceStatus.NOT_ATTENDING.name, result?.status)
    }
}
