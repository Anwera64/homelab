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
            val unreachable: suspend () -> String = { throw IOException("Connection refused") }

            val thrown = assertFailsWith<ServerOfflineException> { reachingHub(unreachable) }

            assertEquals("Connection refused", thrown.message)
        }

    @Test
    fun `GIVEN a call that throws a domain exception WHEN it runs inside reachingHub THEN the exception passes through unchanged`() =
        runTest {
            val refused: suspend () -> String = { throw WrongPinException(attemptsLeft = 2) }

            val thrown = assertFailsWith<WrongPinException> { reachingHub(refused) }

            assertEquals(2, thrown.attemptsLeft)
        }

    @Test
    fun `GIVEN a call that succeeds WHEN it runs inside reachingHub THEN its answer is returned`() =
        runTest {
            val answering: suspend () -> String = { "the answer" }

            val result = reachingHub(answering)

            assertEquals("the answer", result)
        }

    // ---- ensureJsonSuccess -------------------------------------------------

    @Test
    fun `GIVEN a proxy answering 502 503 or 504 WHEN ensuring a JSON success THEN the hub is reported offline`() =
        runTest {
            val proxyStatuses =
                listOf(
                    HttpStatusCode.BadGateway,
                    HttpStatusCode.ServiceUnavailable,
                    HttpStatusCode.GatewayTimeout,
                )

            proxyStatuses.forEach { status ->
                val response = hubAnswers(status = status)
                assertFailsWith<ServerOfflineException>(status.toString()) { response.ensureJsonSuccess() }
            }
        }

    @Test
    fun `GIVEN the hub answering 404 WHEN ensuring a JSON success THEN the address is reported as not found`() =
        runTest {
            val response = hubAnswers(status = HttpStatusCode.NotFound)

            val thrown = assertFailsWith<NotFoundException> { response.ensureJsonSuccess() }

            assertEquals(true, thrown.message?.contains("404"))
        }

    @Test
    fun `GIVEN the hub answering 500 WHEN ensuring a JSON success THEN it is reported as a bad answer from upstream`() =
        runTest {
            val response = hubAnswers(status = HttpStatusCode.InternalServerError)

            val thrown = assertFailsWith<UpstreamGatewayException> { response.ensureJsonSuccess() }

            assertEquals(500, thrown.statusCode)
        }

    @Test
    fun `GIVEN a captive portal answering 200 with HTML WHEN ensuring a JSON success THEN the content type is reported as unexpected`() =
        runTest {
            val response = hubAnswers(body = "<html>Sign in to the wifi</html>", contentType = "text/html")

            val thrown = assertFailsWith<UnexpectedContentTypeException> { response.ensureJsonSuccess() }

            assertEquals("text/html", thrown.contentType)
        }

    @Test
    fun `GIVEN the hub answering 200 with JSON WHEN ensuring a JSON success THEN the same response is handed back`() =
        runTest {
            val response = hubAnswers(body = """{"ok": true}""", contentType = "application/json; charset=UTF-8")

            val ensured = response.ensureJsonSuccess()

            assertSame(response, ensured)
        }

    @Test
    fun `GIVEN the hub answering 204 with no content type WHEN ensuring a JSON success THEN it is accepted`() =
        runTest {
            val response = hubAnswers(status = HttpStatusCode.NoContent, body = "", contentType = null)

            val ensured = response.ensureJsonSuccess()

            assertSame(response, ensured)
        }

    // ---- pinRefusal --------------------------------------------------------

    @Test
    fun `GIVEN a refusal carrying attempts left WHEN reading the PIN refusal THEN it is parsed`() =
        runTest {
            val response =
                hubAnswers(
                    status = HttpStatusCode.Forbidden,
                    body = """{"detail": "Wrong PIN", "attempts_left": 3}""",
                )

            val refusal = response.pinRefusal()

            assertEquals(3, refusal?.attempts_left)
        }

    @Test
    fun `GIVEN a refusal whose body is not JSON WHEN reading the PIN refusal THEN it reads as no refusal rather than throwing`() =
        runTest {
            val response = hubAnswers(status = HttpStatusCode.Forbidden, body = "nope", contentType = "text/plain")

            val refusal = response.pinRefusal()

            assertNull(refusal)
        }

    // ---- throwIfSignInRefused: sign-in refuses a PIN with 401 ---------------

    @Test
    fun `GIVEN sign-in refused with two attempts left WHEN checking the refusal THEN it says two attempts remain`() =
        runTest {
            val response =
                hubAnswers(
                    status = HttpStatusCode.Unauthorized,
                    body = """{"detail": "Wrong PIN", "attempts_left": 2}""",
                )

            val thrown = assertFailsWith<WrongPinException> { response.throwIfSignInRefused() }

            assertEquals(2, thrown.attemptsLeft)
        }

    @Test
    fun `GIVEN sign-in refused without attempts left WHEN checking the refusal THEN it is a plain unauthorized`() =
        runTest {
            val response =
                hubAnswers(
                    status = HttpStatusCode.Unauthorized,
                    body = """{"detail": "Invalid or expired token."}""",
                )

            assertFailsWith<UnauthorizedException> { response.throwIfSignInRefused() }
        }

    @Test
    fun `GIVEN sign-in locked out with a retry after in the body WHEN checking the refusal THEN it says how long to wait`() =
        runTest {
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked", "retry_after_seconds": 45}""",
                )

            val thrown = assertFailsWith<PinLockedException> { response.throwIfSignInRefused() }

            assertEquals(45, thrown.retryAfterSeconds)
        }

    @Test
    fun `GIVEN sign-in locked out with only a Retry-After header WHEN checking the refusal THEN the header says how long to wait`() =
        runTest {
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked"}""",
                    retryAfter = "30",
                )

            val thrown = assertFailsWith<PinLockedException> { response.throwIfSignInRefused() }

            assertEquals(30, thrown.retryAfterSeconds)
        }

    @Test
    fun `GIVEN a 429 that says nothing about waiting WHEN checking a sign-in refusal THEN it is reported as a bad answer from upstream`() =
        runTest {
            val response = hubAnswers(status = HttpStatusCode.TooManyRequests, body = """{"detail": "Locked"}""")

            val thrown = assertFailsWith<UpstreamGatewayException> { response.throwIfSignInRefused() }

            assertEquals(429, thrown.statusCode)
        }

    @Test
    fun `GIVEN the hub answering 200 WHEN checking a sign-in refusal THEN nothing is thrown`() =
        runTest {
            val response = hubAnswers(status = HttpStatusCode.OK)

            response.throwIfSignInRefused()

            assertEquals(HttpStatusCode.OK, response.status)
        }

    // ---- throwIfPinRefused: a signed-in call refuses a PIN with 403 ---------

    @Test
    fun `GIVEN a signed-in PIN refused with one attempt left WHEN checking the refusal THEN it says one attempt remains`() =
        runTest {
            val response =
                hubAnswers(
                    status = HttpStatusCode.Forbidden,
                    body = """{"detail": "Wrong PIN", "attempts_left": 1}""",
                )

            val thrown = assertFailsWith<WrongPinException> { response.throwIfPinRefused() }

            assertEquals(1, thrown.attemptsLeft)
        }

    @Test
    fun `GIVEN a signed-in PIN refused without attempts left WHEN checking the refusal THEN it is a plain forbidden`() =
        runTest {
            val response = hubAnswers(status = HttpStatusCode.Forbidden, body = """{"detail": "Not allowed"}""")

            assertFailsWith<ForbiddenException> { response.throwIfPinRefused() }
        }

    @Test
    fun `GIVEN a signed-in PIN locked out WHEN checking the refusal THEN it says how long to wait`() =
        runTest {
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked", "retry_after_seconds": 60}""",
                )

            val thrown = assertFailsWith<PinLockedException> { response.throwIfPinRefused() }

            assertEquals(60, thrown.retryAfterSeconds)
        }

    @Test
    fun `GIVEN a 401 to a signed-in call WHEN checking a PIN refusal THEN nothing is thrown because a signed-in PIN is refused with 403`() =
        runTest {
            val response = hubAnswers(status = HttpStatusCode.Unauthorized, body = """{"detail": "no"}""")

            response.throwIfPinRefused()

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    // ---- codeGuessesLocked -------------------------------------------------

    @Test
    fun `GIVEN too many code guesses with a retry after in the body WHEN building the lockout THEN it says how long to wait`() =
        runTest {
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked", "retry_after_seconds": 90}""",
                )

            val lockout = response.codeGuessesLocked()

            assertEquals(90, lockout.retryAfterSeconds)
        }

    @Test
    fun `GIVEN too many code guesses with only a Retry-After header WHEN building the lockout THEN the header says how long to wait`() =
        runTest {
            val response =
                hubAnswers(
                    status = HttpStatusCode.TooManyRequests,
                    body = """{"detail": "Locked"}""",
                    retryAfter = "15",
                )

            val lockout = response.codeGuessesLocked()

            assertEquals(15, lockout.retryAfterSeconds)
        }

    @Test
    fun `GIVEN too many code guesses that say nothing about waiting WHEN building the lockout THEN it is reported as a bad answer from upstream`() =
        runTest {
            val response = hubAnswers(status = HttpStatusCode.TooManyRequests, body = """{"detail": "Locked"}""")

            val thrown = assertFailsWith<UpstreamGatewayException> { response.codeGuessesLocked() }

            assertEquals(429, thrown.statusCode)
        }
}
