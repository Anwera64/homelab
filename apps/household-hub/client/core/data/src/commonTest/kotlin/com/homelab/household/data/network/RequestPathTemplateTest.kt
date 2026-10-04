package com.homelab.household.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [templateRequestPath] is what stands between a hub URL and a log line that leaves the phone, so
 * what it must never let through is spelled out here one case at a time.
 */
class RequestPathTemplateTest {
    @Test
    fun `GIVEN a path naming a conversation by its UUID WHEN it is templated THEN the id is replaced`() {
        // GIVEN
        val path = "/api/v1/sessions/3f2a9c1e-7b64-4d0a-9e51-0c8d2b6f4a17/chat/stream"

        // WHEN
        val templated = templateRequestPath(path)

        // THEN
        assertEquals("/api/v1/sessions/{id}/chat/stream", templated)
    }

    @Test
    fun `GIVEN a path naming a tool call WHEN it is templated THEN both the conversation and the call are replaced`() {
        // GIVEN
        val path = "/api/v1/sessions/3F2A9C1E-7B64-4D0A-9E51-0C8D2B6F4A17/tools/call_x9Tq2LmA/decision"

        // WHEN
        val templated = templateRequestPath(path)

        // THEN
        assertEquals("/api/v1/sessions/{id}/tools/{id}/decision", templated)
    }

    @Test
    fun `GIVEN a path carrying an invite code WHEN it is templated THEN the code is replaced`() {
        // GIVEN
        val path = "/api/v1/invites/maple-otter-lamp/redeem"

        // WHEN
        val templated = templateRequestPath(path)

        // THEN
        assertEquals("/api/v1/invites/{id}/redeem", templated)
    }

    @Test
    fun `GIVEN a path naming a member by a plain word WHEN it is templated THEN the word is replaced`() {
        // GIVEN
        val path = "/api/v1/users/emma/pin-resets"

        // WHEN
        val templated = templateRequestPath(path)

        // THEN
        assertEquals("/api/v1/users/{id}/pin-resets", templated)
    }

    @Test
    fun `GIVEN a path with nothing but fixed words WHEN it is templated THEN it is left as it is`() {
        // GIVEN
        val path = "/api/v1/users/me/tool-approvals"

        // WHEN
        val templated = templateRequestPath(path)

        // THEN
        assertEquals("/api/v1/users/me/tool-approvals", templated)
    }

    @Test
    fun `GIVEN a whole address with a query WHEN it is templated THEN only the path is left`() {
        // GIVEN
        val url = "https://hub.test/api/v1/sessions?search=dentist&limit=20#top"

        // WHEN
        val templated = templateRequestPath(url)

        // THEN
        assertEquals("/api/v1/sessions", templated)
    }
}
