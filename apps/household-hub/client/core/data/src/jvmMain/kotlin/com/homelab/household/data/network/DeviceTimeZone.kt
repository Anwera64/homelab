package com.homelab.household.data.network

import java.util.TimeZone

actual fun deviceTimeZoneId(): String = TimeZone.getDefault().id
