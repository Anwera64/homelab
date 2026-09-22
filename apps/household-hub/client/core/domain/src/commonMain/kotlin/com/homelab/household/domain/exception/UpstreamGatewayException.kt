package com.homelab.household.domain.exception

class UpstreamGatewayException(
    val statusCode: Int,
    message: String = "Upstream server returned error $statusCode",
) : DomainException(message)
