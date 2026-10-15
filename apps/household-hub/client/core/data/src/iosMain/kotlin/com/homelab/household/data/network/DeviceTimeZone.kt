package com.homelab.household.data.network

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

actual fun deviceTimeZoneId(): String = NSTimeZone.localTimeZone.name

actual fun deviceUtcOffsetSeconds(epochMillis: Long): Int =
    NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)).toInt()
