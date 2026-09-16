package com.homelab.household.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubConfigTest {

    @Test
    fun defaults_isDebug_to_false() {
        val config = HubConfig(baseUrl = "https://hub.test")
        assertEquals("https://hub.test", config.baseUrl)
        assertFalse(config.isDebug)
    }

    @Test
    fun retains_explicit_isDebug_flag() {
        val config = HubConfig(baseUrl = "https://hub.test", isDebug = true)
        assertEquals("https://hub.test", config.baseUrl)
        assertTrue(config.isDebug)
    }
}
