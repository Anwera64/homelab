package com.homelab.household.data.telemetry

actual fun deviceAttributes(): Map<String, String> =
    mapOf(
        "os.name" to (System.getProperty("os.name") ?: "JVM"),
        "os.version" to (System.getProperty("os.version") ?: "unknown"),
        "device.model.identifier" to "desktop",
    )
