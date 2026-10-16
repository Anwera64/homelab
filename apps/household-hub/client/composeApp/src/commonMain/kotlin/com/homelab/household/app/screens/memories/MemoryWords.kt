package com.homelab.household.app.screens.memories

import androidx.compose.runtime.Composable
import com.homelab.household.app.resources.Res
import com.homelab.household.app.resources.memories_age_last_week
import com.homelab.household.app.resources.memories_age_month_year
import com.homelab.household.app.resources.memories_age_today
import com.homelab.household.app.resources.memories_age_yesterday
import com.homelab.household.app.resources.memories_month_apr
import com.homelab.household.app.resources.memories_month_aug
import com.homelab.household.app.resources.memories_month_dec
import com.homelab.household.app.resources.memories_month_feb
import com.homelab.household.app.resources.memories_month_jan
import com.homelab.household.app.resources.memories_month_jul
import com.homelab.household.app.resources.memories_month_jun
import com.homelab.household.app.resources.memories_month_mar
import com.homelab.household.app.resources.memories_month_may
import com.homelab.household.app.resources.memories_month_nov
import com.homelab.household.app.resources.memories_month_oct
import com.homelab.household.app.resources.memories_month_sep
import com.homelab.household.app.resources.tool_card_month_apr
import com.homelab.household.app.resources.tool_card_month_aug
import com.homelab.household.app.resources.tool_card_month_dec
import com.homelab.household.app.resources.tool_card_month_feb
import com.homelab.household.app.resources.tool_card_month_jan
import com.homelab.household.app.resources.tool_card_month_jul
import com.homelab.household.app.resources.tool_card_month_jun
import com.homelab.household.app.resources.tool_card_month_mar
import com.homelab.household.app.resources.tool_card_month_may
import com.homelab.household.app.resources.tool_card_month_nov
import com.homelab.household.app.resources.tool_card_month_oct
import com.homelab.household.app.resources.tool_card_month_sep
import com.homelab.household.app.resources.tool_card_weekday_fri
import com.homelab.household.app.resources.tool_card_weekday_mon
import com.homelab.household.app.resources.tool_card_weekday_sat
import com.homelab.household.app.resources.tool_card_weekday_sun
import com.homelab.household.app.resources.tool_card_weekday_thu
import com.homelab.household.app.resources.tool_card_weekday_tue
import com.homelab.household.app.resources.tool_card_weekday_wed
import com.homelab.household.presentation.memories.MemoryAge
import org.jetbrains.compose.resources.stringResource

/** A memory's age as the page says it: "today", "Tuesday", "March", "March 2025". */
@Composable
internal fun ageWords(age: MemoryAge): String =
    when (age) {
        MemoryAge.Today -> stringResource(Res.string.memories_age_today)

        MemoryAge.Yesterday -> stringResource(Res.string.memories_age_yesterday)

        is MemoryAge.Weekday -> stringResource(WEEKDAYS[age.isoDay - 1])

        MemoryAge.LastWeek -> stringResource(Res.string.memories_age_last_week)

        is MemoryAge.Month -> stringResource(MONTHS[age.month - 1])

        is MemoryAge.MonthYear -> stringResource(
            Res.string.memories_age_month_year,
            stringResource(MONTHS[age.month - 1]),
            age.year,
        )
    }

internal val WEEKDAYS =
    listOf(
        Res.string.tool_card_weekday_mon,
        Res.string.tool_card_weekday_tue,
        Res.string.tool_card_weekday_wed,
        Res.string.tool_card_weekday_thu,
        Res.string.tool_card_weekday_fri,
        Res.string.tool_card_weekday_sat,
        Res.string.tool_card_weekday_sun,
    )

internal val MONTHS =
    listOf(
        Res.string.memories_month_jan,
        Res.string.memories_month_feb,
        Res.string.memories_month_mar,
        Res.string.memories_month_apr,
        Res.string.memories_month_may,
        Res.string.memories_month_jun,
        Res.string.memories_month_jul,
        Res.string.memories_month_aug,
        Res.string.memories_month_sep,
        Res.string.memories_month_oct,
        Res.string.memories_month_nov,
        Res.string.memories_month_dec,
    )

/** "17 Oct" in an "until" chip. */
internal val SHORT_MONTHS =
    listOf(
        Res.string.tool_card_month_jan,
        Res.string.tool_card_month_feb,
        Res.string.tool_card_month_mar,
        Res.string.tool_card_month_apr,
        Res.string.tool_card_month_may,
        Res.string.tool_card_month_jun,
        Res.string.tool_card_month_jul,
        Res.string.tool_card_month_aug,
        Res.string.tool_card_month_sep,
        Res.string.tool_card_month_oct,
        Res.string.tool_card_month_nov,
        Res.string.tool_card_month_dec,
    )
