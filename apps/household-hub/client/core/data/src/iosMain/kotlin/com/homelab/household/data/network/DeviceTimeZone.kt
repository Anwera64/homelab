package com.homelab.household.data.network

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

actual fun deviceTimeZoneId(): String = NSTimeZone.localTimeZone.name
