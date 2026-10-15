package com.homelab.household.presentation.memories

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** When a memory was noted, as its row says it: "today", "Sunday", "last week", "March", "March 2025". */
sealed interface MemoryAge {
    data object Today : MemoryAge

    data object Yesterday : MemoryAge

    /** Two to six days ago. [isoDay] is 1 for Monday through 7 for Sunday. */
    data class Weekday(
        val isoDay: Int,
    ) : MemoryAge

    /** Seven to thirteen days ago. */
    data object LastWeek : MemoryAge

    /** Earlier this year. [month] is 1 for January. */
    data class Month(
        val month: Int,
    ) : MemoryAge

    data class MonthYear(
        val month: Int,
        val year: Int,
    ) : MemoryAge
}

/** A day of the month as the member's phone sees it, for "until 17 Oct". */
data class LocalDay(
    val day: Int,
    val month: Int,
)

/** [at] measured against [now] in local days, [offsetSeconds] from UTC. */
@OptIn(ExperimentalTime::class)
fun memoryAge(
    at: Instant,
    now: Instant,
    offsetSeconds: Int,
): MemoryAge {
    val then = civil(at, offsetSeconds)
    val today = civil(now, offsetSeconds)
    val days = today.epochDay - then.epochDay
    return when {
        days <= 0 -> MemoryAge.Today
        days == 1L -> MemoryAge.Yesterday
        days < WEEK -> MemoryAge.Weekday(then.isoDay)
        days < 2 * WEEK -> MemoryAge.LastWeek
        then.year == today.year -> MemoryAge.Month(then.month)
        else -> MemoryAge.MonthYear(then.month, then.year)
    }
}

@OptIn(ExperimentalTime::class)
fun localDay(
    at: Instant,
    offsetSeconds: Int,
): LocalDay = civil(at, offsetSeconds).let { LocalDay(day = it.day, month = it.month) }

private const val WEEK = 7L
private const val SECONDS_PER_DAY = 86_400L

private class Civil(
    val epochDay: Long,
    val year: Int,
    val month: Int,
    val day: Int,
    val isoDay: Int,
)

/** The local calendar day of an instant: Howard Hinnant's days-to-civil, so no date library is needed. */
@OptIn(ExperimentalTime::class)
private fun civil(
    at: Instant,
    offsetSeconds: Int,
): Civil {
    val epochDay = (at.epochSeconds + offsetSeconds).floorDiv(SECONDS_PER_DAY)
    val z = epochDay + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    // 1 January 1970 was a Thursday (ISO day 4).
    val isoDay = ((epochDay + 3).mod(7L) + 1).toInt()
    return Civil(epochDay, year, month, day, isoDay)
}
