package com.homelab.household.data.telemetry

import platform.UIKit.UIDevice

actual fun deviceAttributes(): Map<String, String> =
    UIDevice.currentDevice.let { device ->
        mapOf(
            "os.name" to device.systemName,
            "os.version" to device.systemVersion,
            "device.model.identifier" to device.model,
        )
    }
