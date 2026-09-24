package com.homelab.household.data.network

actual fun deviceTimeZoneId(): String = java.util.TimeZone.getDefault().id
