package com.fightclub.attendance.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fightclub.attendance.data.local.AppDatabase
import com.fightclub.attendance.data.local.entity.AttendanceStatus
import com.fightclub.attendance.data.repository.AttendanceStatusRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AttendanceStatusRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AttendanceStatusRepositoryImpl
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2024-01-02T12:00:00Z"), ZoneId.of("UTC"))

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = AttendanceStatusRepositoryImpl(database.attendanceStatusDao(), fixedClock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `defaults to PENDING when nothing has been recorded for today`() = runTest {
        assertEquals(AttendanceStatus.PENDING, repository.getTodayStatus())
        assertFalse(repository.isTodayResolved())
    }

    @Test
    fun `setTodayStatus persists and is readable back`() = runTest {
        repository.setTodayStatus(AttendanceStatus.NOT_ATTENDING)

        assertEquals(AttendanceStatus.NOT_ATTENDING, repository.getTodayStatus())
        assertTrue(repository.isTodayResolved())
    }

    @Test
    fun `ATTENDING counts as resolved, so later prompts would stay silent`() = runTest {
        repository.setTodayStatus(AttendanceStatus.ATTENDING)

        assertTrue(repository.isTodayResolved())
    }

    @Test
    fun `setTodayStatus twice overwrites rather than duplicating the row`() = runTest {
        repository.setTodayStatus(AttendanceStatus.PENDING)
        repository.setTodayStatus(AttendanceStatus.AUTO_SENT_NO_RESPONSE)

        assertEquals(AttendanceStatus.AUTO_SENT_NO_RESPONSE, repository.getTodayStatus())
    }
}
