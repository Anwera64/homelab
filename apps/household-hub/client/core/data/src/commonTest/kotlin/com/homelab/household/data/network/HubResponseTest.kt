package com.homelab.household.data.network

import com.homelab.household.domain.exception.ForbiddenException
import com.homelab.household.domain.exception.NotFoundException
import com.homelab.household.domain.exception.PinLockedException
import com.homelab.household.domain.exception.ServerOfflineException
import com.homelab.household.domain.exception.UnauthorizedException
import com.homelab.household.domain.exception.UnexpectedContentTypeException
import com.homelab.household.domain.exception.UpstreamGatewayException
import com.homelab.household.domain.exception.WrongPinException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The one place the hub's HTTP answers become domain exceptions. Every Ktor data source reaches the
 * hub through these, which is why the rules are pinned here once rather than in each of them.
 */
class HubResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun hubAnswers(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}",
        contentType: String? = "application/json",
        retryAfter: String? = null,
    ): HttpResponse {
        val headers =
            Headers.build {
                contentType?.let { append(HttpHeaders.ContentType, it) }
                retryAfter?.let { append(HttpHeaders.RetryAfter, it) }
            }
        val client =
            HttpClient(MockEngine { respond(body, status, headers) }) {
                install(ContentNegotiation) { json(json) }
            }
        return client.get("https://hub.test/api/v1/anything")
    }

    // ---- reachingHub -------------------------------------------------------

    @Test
    fun `GIVEN the hub cannot be reached WHEN a call runs inside reachingHub THEN it surfaces as server offline`() =
        runTest {
            // GIVEN
            val unreachable: suspend () -> String = { throw IOException("Connection refused") }

            // WHEN
            val thrown = assertFailsWith<ServerOfflineException> { reachingHub(unreachable) }

            // THEN
            assertEquals("Connection refused", thrown.message)
        }

    @Test
    fun `GIVEN a call that throws a domain exception WHEN it runs inside reachingHub THEN the exception passes through unchanged`() =
        runTest {
            // GIVEN
            val refused: suspend () -> String = { throw WrongPinException(attemptsLeft = 2) }

            // WHEN
            val thrown = assertFailsWith<WrongPinException> { reachingHub(refused) }

            // THEN
            assertEquals(2, thrown.attemptsLeft)
        }

    @Test
    fun `GIVEN a call that succeeds WHEN it runs inside reachingHub THEN its answer is returned`() =
        runTest {
            // GIVEN
            val answering: suspend () -> String = { "the answer" }

            // WHEN
            val result = reachingHub(answering)

            // THEN
            assertEquals("the answer", result)
        }

    // ---- ensureJsonSuccess -------------------------------------------------

    @Test
    fun `GIVEN a proxy answering 502 503 or 504 WHEN ensuring a JSON success THEN the hub is reported offline`() =
        runTest {
            // GIVEN
            val proxyStatuses =
                listOf(
                    HttpStatusCode.BadGateway,
                    HttpStatusCode.ServiceUnavailable,
                    HttpStatusCode.GatewayTimeout,
                )

            // WHEN / THEN
            proxyStatuses.forEach { status ->
                val response = hubAnswers(status = status)
                assertFailsWith<ServerOfflineException>(status.toString()) { response.ensureJsonSuccess() }
            }
        }

    @Test
    fun `GIVEN the hub answering 404 WHEN ensuring a JSON success THEN the address is reported as not found`() =
        runTest {
            // GIVEN
            val response = hubAnswers(status = HttpStatusCode.NotFound)

            // WHEN
            val thrown = assertFailsWith<NotFoundException> { response.ensureJsonSuccess() }

            // THEN
            assertEquals(true, thrown.message?.contains("404"))
        }

    @Test
    fun `GIVEN the hub answering 500 WHEN ensuring a JSON success THEN it is reported as a bad answer from upstream`() =
        runTest {
            // GIVEN
            val response = hubAnswers(status = HttpStatusCode.InternalServerError)

            // WHEN
            val thrown = assertFailsWith<UpstreamGatewayException> { response.ensureJsonSuccess() }

            // THEN
            assertEquals(500, thrown.statusCode)
        }

    @Test
    fun `GIVEN a captive portal answering 200 with HTML WHEN ensuring a JSON success THEN the content type is reported as unexpected`() =
        runTest {
            // GIVEN
            val response = hubAnswers(body = "<html>Sign in to the wifi</html>", contentType = "text/html")

            // WHEN
            val thrown = assertFailsWith<UnexpectedContentTypeException> { response.ensureJsonSuccess() }

            // THEN
            assertEquals("text/html", thrown.contentType)
        }

    @Test
    fun `GIVEN the hub answering 200 with JSON WHEN ensuring a JSON success THEN the same response is handed back`() =
        runTest {
            // GIVEN
            val response = hubAnswers(body = """{"ok": true}""", contentType = "application/json; charset=UTF-8")

            // WHEN
            val ensured = response.ensureJsonSuccess()

            // THEN
            assertSame(response, ensured)
        }

    @Test
    fun `GIVEN the hub answering 204 with no content type WHEN ensuring a JSON success THEN it is accepted`() =
        runTest {
            // GIVEN
            val response = hubAnswers(status = HttpStatusCode.NoContent, body = "", contentType = null)

            // WHEN
            val ensured = response.ensureJsonSuccess()

            // THEN
            assertSame(response, ensured)
        }

    // ---- pinRefusal --------------------------------------------------------

    @Test
    fun `GIVEN a refusal carrying attempts left WHEN reading the PIN refusal THEN it is parsed`() =
        runTest {
            // GIVEN
            val response =
                hubAnswers(
                    status = HttpStatusCode.Forbidden,
                    body = """{"detail": "Wrong PIN", "attempts_left": 3}""",
                )

            // WHEN
            val refusal = response.pinRefusal()

            // THEN
            assertEquals(3, refusal?.attempts_left)
        }

    @Test
    fun `GIVEN a refusal whose body is not JSON WHEN reading the PIN refusal THEN it reads as no refusal rather than throwing`() =
        runTest {
            // GIVEN
            val response = hubAnswers(status = HttpStatusCode.Forbidden, body = "nope", contentType = "text/plain")

            // WHEN
            val refusal = response.pinRefusal()

            // THEN
            assertNull(refusal)
        }

    // ---- throwIfSignInRefused: sign-in refuses a PIN with 401 ---------------

    @Test
    fun `GIVEN sign-in refused with two attempts left WHEN checking the refusal THEN it says two attempts remain`() =
        runTest {
            // GIVEN
            val response =
                hubAnswers(
                    status = HttpStatusCode.Unauthorized,
                    body = """{"detail": "Wrong PIN", "attempts_left": 2}""",
                )

            // WHEN
            val thrown = assertFailsWith<WrongPinException> { response.throwIfSignInRefused() }

            // THEN
            assertEquals(2, thrown.attemptsLeft)
        }

    @Test
    fun `GIVEN sign-in refused without attempts left WHEN checking the refusal THEN it is a plain unauthorized`() =
        runTest {
            // GIVEN
            val response =
                hubAnswers(
                    status = HttpStatusCode.Unauthorized,
                    body = """{"detail": "Invalid or expired token."}""",
                )

            // WHEN / THEN
            assertFailsWith<UnauthorizedException> { response.throwIfSignInRefused() }
        }

    @Test
    fun `GIVEN sign-in locked out with a retry after in the body WHEN checking the refusal THEN it says how long to wait`() =
        runTest {
            // GIVEN
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked", "retry_after_seconds": 45}""",
                )

            // WHEN
            val thrown = assertFailsWith<PinLockedException> { response.throwIfSignInRefused() }

            // THEN
            assertEquals(45, thrown.retryAfterSeconds)
        }

    @Test
    fun `GIVEN sign-in locked out with only a Retry-After header WHEN checking the refusal THEN the header says how long to wait`() =
        runTest {
            // GIVEN
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked"}""",
                    retryAfter = "30",
                )

            // WHEN
            val thrown = assertFailsWith<PinLockedException> { response.throwIfSignInRefused() }

            // THEN
            assertEquals(30, thrown.retryAfterSeconds)
        }

    @Test
    fun `GIVEN a 429 that says nothing about waiting WHEN checking a sign-in refusal THEN it is reported as a bad answer from upstream`() =
        runTest {
            // GIVEN
            val response = hubAnswers(status = HttpStatusCode.TooManyRequests, body = """{"detail": "Locked"}""")

            // WHEN
            val thrown = assertFailsWith<UpstreamGatewayException> { response.throwIfSignInRefused() }

            // THEN
            assertEquals(429, thrown.statusCode)
        }

    @Test
    fun `GIVEN the hub answering 200 WHEN checking a sign-in refusal THEN nothing is thrown`() =
        runTest {
            // GIVEN
            val response = hubAnswers(status = HttpStatusCode.OK)

            // WHEN
            response.throwIfSignInRefused()

            // THEN
            assertEquals(HttpStatusCode.OK, response.status)
        }

    // ---- throwIfPinRefused: a signed-in call refuses a PIN with 403 ---------

    @Test
    fun `GIVEN a signed-in PIN refused with one attempt left WHEN checking the refusal THEN it says one attempt remains`() =
        runTest {
            // GIVEN
            val response =
                hubAnswers(
                    status = HttpStatusCode.Forbidden,
                    body = """{"detail": "Wrong PIN", "attempts_left": 1}""",
                )

            // WHEN
            val thrown = assertFailsWith<WrongPinException> { response.throwIfPinRefused() }

            // THEN
            assertEquals(1, thrown.attemptsLeft)
        }

    @Test
    fun `GIVEN a signed-in PIN refused without attempts left WHEN checking the refusal THEN it is a plain forbidden`() =
        runTest {
            // GIVEN
            val response = hubAnswers(status = HttpStatusCode.Forbidden, body = """{"detail": "Not allowed"}""")

            // WHEN / THEN
            assertFailsWith<ForbiddenException> { response.throwIfPinRefused() }
        }

    @Test
    fun `GIVEN a signed-in PIN locked out WHEN checking the refusal THEN it says how long to wait`() =
        runTest {
            // GIVEN
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked", "retry_after_seconds": 60}""",
                )

            // WHEN
            val thrown = assertFailsWith<PinLockedException> { response.throwIfPinRefused() }

            // THEN
            assertEquals(60, thrown.retryAfterSeconds)
        }

    @Test
    fun `GIVEN a 401 to a signed-in call WHEN checking a PIN refusal THEN nothing is thrown because a signed-in PIN is refused with 403`() =
        runTest {
            // GIVEN
            val response = hubAnswers(status = HttpStatusCode.Unauthorized, body = """{"detail": "no"}""")

            // WHEN
            response.throwIfPinRefused()

            // THEN
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    // ---- codeGuessesLocked -------------------------------------------------

    @Test
    fun `GIVEN too many code guesses with a retry after in the body WHEN building the lockout THEN it says how long to wait`() =
        runTest {
            // GIVEN
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked", "retry_after_seconds": 90}""",
                )

            // WHEN
            val lockout = response.codeGuessesLocked()

            // THEN
            assertEquals(90, lockout.retryAfterSeconds)
        }

    @Test
    fun `GIVEN too many code guesses with only a Retry-After header WHEN building the lockout THEN the header says how long to wait`() =
        runTest {
            // GIVEN
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked"}""",
                    retryAfter = "15",
                )

            // WHEN
            val lockout = response.codeGuessesLocked()

            // THEN
            assertEquals(15, lockout.retryAfterSeconds)
        }

    @Test
    fun `GIVEN too many code guesses that say nothing about waiting WHEN building the lockout THEN it is reported as a bad answer from upstream`() =
        runTest {
            // GIVEN
            val response = hubAnswers(status = HttpStatusCode.TooManyRequests, body = """{"detail": "Locked"}""")

            // WHEN
            val thrown = assertFailsWith<UpstreamGatewayException> { response.codeGuessesLocked() }

            // THEN
            assertEquals(429, thrown.statusCode)
        }
}
