package com.homelab.household.data.network

import java.util.TimeZone

actual fun deviceTimeZoneId(): String = TimeZone.getDefault().id

actual fun deviceUtcOffsetSeconds(epochMillis: Long): Int = TimeZone.getDefault().getOffset(epochMillis) / 1000
