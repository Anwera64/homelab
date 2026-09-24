package com.homelab.household.data.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin

/** The header the hub reads the phone's IANA zone from — see [sendDeviceTimeZone]. */
const val TIME_ZONE_HEADER = "X-Timezone"

/**
 * Puts this phone's current IANA timezone id on every outgoing call, so the hub can tell an AI
 * agent today's date in the user's own day rather than the server's (issue #39). [currentZoneId]
 * is called fresh for each request instead of once at startup, because a member can fly somewhere
 * else mid-session; without this header the hub falls back to UTC.
 */
fun HttpClientConfig<*>.sendDeviceTimeZone(currentZoneId: () -> String) {
    val plugin =
        createClientPlugin("DeviceTimeZone") {
            onRequest { request, _ ->
                request.headers[TIME_ZONE_HEADER] = currentZoneId()
            }
        }
    install(plugin)
}

/** This phone's current IANA timezone id, e.g. `"America/Mexico_City"`. */
expect fun deviceTimeZoneId(): String
