package com.fightclub.attendance.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DateTimeUtilsTest {

    private val zone = ZoneId.of("America/New_York")

    private fun clockAt(dateTime: String): Clock {
        val zdt = ZonedDateTime.parse(dateTime).withZoneSameInstant(zone)
        return Clock.fixed(zdt.toInstant(), zone)
    }

    @Test
    fun `same day, time still ahead, returns today`() {
        // Tuesday 2024-01-02 at 10:00, looking for Tuesday 16:00 -> should be today.
        val clock = clockAt("2024-01-02T10:00:00-05:00")

        val result = DateTimeUtils.nextOccurrenceMillis(DayOfWeek.TUESDAY, LocalTime.of(16, 0), clock)

        val expected = ZonedDateTime.of(2024, 1, 2, 16, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, result)
    }

    @Test
    fun `same day, time already passed, rolls to next week`() {
        // Tuesday 2024-01-02 at 17:00, looking for Tuesday 16:00 -> should roll to Jan 9.
        val clock = clockAt("2024-01-02T17:00:00-05:00")

        val result = DateTimeUtils.nextOccurrenceMillis(DayOfWeek.TUESDAY, LocalTime.of(16, 0), clock)

        val expected = ZonedDateTime.of(2024, 1, 9, 16, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, result)
    }

    @Test
    fun `different day of week, rolls forward to the correct day`() {
        // Monday 2024-01-01, looking for the next Thursday.
        val clock = clockAt("2024-01-01T09:00:00-05:00")

        val result = DateTimeUtils.nextOccurrenceMillis(DayOfWeek.THURSDAY, LocalTime.of(16, 0), clock)

        val expected = ZonedDateTime.of(2024, 1, 4, 16, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, result)
    }

    @Test
    fun `exact boundary time rolls to next week rather than repeating now`() {
        // Exactly Tuesday 16:00:00 -> next occurrence should be one week later, not "now".
        val clock = clockAt("2024-01-02T16:00:00-05:00")

        val result = DateTimeUtils.nextOccurrenceMillis(DayOfWeek.TUESDAY, LocalTime.of(16, 0), clock)

        val expected = ZonedDateTime.of(2024, 1, 9, 16, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, result)
    }
}
