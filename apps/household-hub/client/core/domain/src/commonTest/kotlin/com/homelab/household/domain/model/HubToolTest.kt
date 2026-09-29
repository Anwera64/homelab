package com.homelab.household.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The hub's tool names, pinned in one place: a renamed tool on the hub fails here, not on a screen. */
class HubToolTest {
    @Test
    fun `GIVEN the hub's tools WHEN named THEN the names match what the hub sends`() {
        assertEquals("calendar_read", HubTool.CALENDAR_READ)
        assertEquals("calendar_write", HubTool.CALENDAR_WRITE)
        assertEquals("document_writer", HubTool.DOCUMENT_WRITER)
        assertEquals("searxng_search", HubTool.WEB_SEARCH)
        assertEquals("read_page", HubTool.READ_PAGE)
        assertEquals("lookup_sources", HubTool.LOOKUP_SOURCES)
        assertEquals("pdf_reader", HubTool.PDF_READER)
    }
}
