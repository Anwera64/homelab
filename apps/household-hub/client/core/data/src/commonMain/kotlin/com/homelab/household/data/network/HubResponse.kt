package com.homelab.household.data.network

import com.homelab.household.data.dto.PinRefusalDto
import com.homelab.household.domain.exception.CodeGuessesLockedException
import com.homelab.household.domain.exception.ForbiddenException
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.exception.UnexpectedContentTypeException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.exception.WrongPinException
import com.homelab.household.domain.util.runCatchingSafe
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Where the hub's HTTP answers become domain exceptions, named once for every remote data source.
 *
 * Nothing above the data sources sees an [HttpStatusCode]: a data source reaches the hub inside
 * [reachingHub], checks the refusals its endpoint can return, and then calls [ensureJsonSuccess].
 */

/** Network failures become [ServerOfflineException]; everything else passes through as thrown. */
suspend fun <T> reachingHub(block: suspend () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        NetworkExceptionHelper.rethrowAsDomain(e)
    }

/**
 * 502–504 is a proxy saying the hub is down or starting; 404 means the address is wrong; any
 * other failure is the hub answering badly. A success that isn't JSON is a captive portal or
 * the wrong server.
 */
fun HttpResponse.ensureJsonSuccess(): HttpResponse {
    if (status.value in 502..504) {
        throw ServerOfflineException(message = "Hub is offline or starting up (HTTP ${status.value})")
    }
    if (!status.isSuccess()) {
        if (status == HttpStatusCode.NotFound) {
            throw NotFoundException("Hub endpoint returned HTTP 404. Check your hub address.")
        }
        throw UpstreamGatewayException(statusCode = status.value)
    }
    val type = contentType()?.withoutParameters()
    if (type != null && type != ContentType.Application.Json) {
        throw UnexpectedContentTypeException(contentType = type.toString())
    }
    return this
}

/** Why the hub refused, when it said. A body that isn't a refusal reads as none rather than throwing. */
suspend fun HttpResponse.pinRefusal(): PinRefusalDto? =
    runCatchingSafe { body<PinRefusalDto>() }.getOrNull()

/**
 * How **sign-in** refuses a PIN: 401 with `attempts_left`, and 429 with `retry_after_seconds` once
 * the misses run out. Nobody is signed in yet, so a 401 here is a wrong PIN, not a dead token.
 */
suspend fun HttpResponse.throwIfSignInRefused() {
    when (status) {
        HttpStatusCode.Unauthorized -> {
            val attemptsLeft = pinRefusal()?.attempts_left
            throw if (attemptsLeft != null) WrongPinException(attemptsLeft) else UnauthorizedException("Wrong PIN")
        }
        HttpStatusCode.TooManyRequests -> throw pinLockout()
        else -> Unit
    }
}

/**
 * How a **signed-in** call refuses a PIN: 403 with `attempts_left`, and 429 with
 * `retry_after_seconds`. It is 403 rather than 401 precisely so that re-checking a PIN cannot be
 * mistaken for the hub dropping the token — see `signOutOnUnauthorized`.
 */
suspend fun HttpResponse.throwIfPinRefused() {
    when (status) {
        HttpStatusCode.Forbidden -> {
            val attemptsLeft = pinRefusal()?.attempts_left
            throw if (attemptsLeft != null) WrongPinException(attemptsLeft) else ForbiddenException("Wrong PIN")
        }
        HttpStatusCode.TooManyRequests -> throw pinLockout()
        else -> Unit
    }
}

/**
 * Too many wrong invite or reset codes. Returned rather than thrown, so the caller reads as
 * `throw response.codeGuessesLocked()` beside the other refusals its endpoint can return.
 */
suspend fun HttpResponse.codeGuessesLocked(): CodeGuessesLockedException =
    CodeGuessesLockedException(retryAfterSeconds())

private suspend fun HttpResponse.pinLockout(): PinLockedException =
    PinLockedException(retryAfterSeconds())

/**
 * How long the hub says to wait, from the refusal body or the `Retry-After` header. A 429 that says
 * neither is the hub answering badly — guessing a wait would be worse than saying so.
 */
private suspend fun HttpResponse.retryAfterSeconds(): Int =
    pinRefusal()?.retry_after_seconds
        ?: headers[HttpHeaders.RetryAfter]?.toIntOrNull()
        ?: throw UpstreamGatewayException(statusCode = status.value)
