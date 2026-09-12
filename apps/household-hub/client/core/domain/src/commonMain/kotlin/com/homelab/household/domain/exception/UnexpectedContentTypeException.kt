package com.homelab.household.domain.exception

/**
 * The hub answered with something other than JSON — usually a captive portal, a proxy error page
 * or the wrong address entirely. The type is kept so the UI can say which.
 */
class UnexpectedContentTypeException(
    val contentType: String,
    message: String = "Expected JSON but the hub sent $contentType"
) : DomainException(message)
