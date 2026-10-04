package com.homelab.household.data.telemetry

import android.os.Build

actual fun deviceAttributes(): Map<String, String> =
    mapOf(
        "os.name" to "Android",
        "os.version" to (Build.VERSION.RELEASE ?: "unknown"),
        "device.model.identifier" to listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" "),
    )
