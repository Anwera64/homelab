package com.homelab.household.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubConfigTest {
    @Test
    fun `GIVEN a hub address and nothing else WHEN the config is built THEN it is not a debug build`() {
        val address = "https://hub.test"

        val config = HubConfig(baseUrl = address)

        assertEquals(address, config.baseUrl)
        assertFalse(config.isDebug)
    }

    @Test
    fun `GIVEN a hub address marked as debug WHEN the config is built THEN the debug flag is kept`() {
        val address = "https://hub.test"

        val config = HubConfig(baseUrl = address, isDebug = true)

        assertEquals(address, config.baseUrl)
        assertTrue(config.isDebug)
    }
}
