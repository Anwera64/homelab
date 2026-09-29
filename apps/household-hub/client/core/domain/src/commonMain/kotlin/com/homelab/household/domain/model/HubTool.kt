package com.homelab.household.domain.model

/**
 * The names the hub gives its tools. Parts and steps keep the hub's name (see [AnswerPart]); these
 * are the one place the phone spells them, so the screens and mappers that key on a tool agree.
 */
object HubTool {
    const val CALENDAR_READ = "calendar_read"
    const val CALENDAR_WRITE = "calendar_write"
    const val DOCUMENT_WRITER = "document_writer"
    const val WEB_SEARCH = "searxng_search"
    const val READ_PAGE = "read_page"
    const val LOOKUP_SOURCES = "lookup_sources"
    const val PDF_READER = "pdf_reader"
}
