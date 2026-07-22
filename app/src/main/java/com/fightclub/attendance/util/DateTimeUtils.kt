package com.fightclub.attendance.util

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure date/time math shared by [com.fightclub.attendance.domain.scheduler.AlarmScheduler]. */
object DateTimeUtils {

    /**
     * Returns the epoch-millis of the next time [dayOfWeek]/[time] occurs at or after `now`
     * (as given by [clock]). If today already matches both the day and a time in the future,
     * today is used; otherwise the calculation rolls forward up to 7 days.
     */
    fun nextOccurrenceMillis(
        dayOfWeek: DayOfWeek,
        time: LocalTime,
        clock: Clock,
        zoneId: ZoneId = clock.zone
    ): Long {
        val now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId)
        var candidate = ZonedDateTime.of(LocalDateTime.of(now.toLocalDate(), time), zoneId)

        while (candidate.dayOfWeek != dayOfWeek || !candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }
}
