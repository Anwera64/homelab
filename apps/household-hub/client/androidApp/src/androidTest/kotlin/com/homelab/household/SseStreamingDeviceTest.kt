package com.homelab.household

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homelab.household.data.remote.DefensiveSseStreamReader
import com.homelab.household.domain.model.ChatStreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Proves token-by-token SSE on the Android engine:
 *  - the server waits for each delta to be received before writing the next, so buffering deadlocks;
 *  - one 15-second gap, which OkHttp's default 10s read timeout would kill.
 */
@RunWith(AndroidJUnit4::class)
class SseStreamingDeviceTest {

    private lateinit var server: ServerSocket
    private val firstDeltaReceived = CountDownLatch(1)

    @Before
    fun setUp() {
        server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun deltas_arrive_one_by_one_and_survive_a_long_pause() {
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val out = socket.getOutputStream()
                out.write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/event-stream\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray()
                )
                out.flush()

                out.write("data: {\"type\":\"delta\",\"content\":\"first \"}\n\n".toByteArray())
                out.flush()

                // Nothing else is written until the client has really received the first delta.
                assertTrue(
                    "Client never received the first delta — the engine is buffering",
                    firstDeltaReceived.await(20, TimeUnit.SECONDS)
                )

                Thread.sleep(15_000)

                out.write("data: {\"type\":\"delta\",\"content\":\"second\"}\n\n".toByteArray())
                out.flush()
                out.write("data: [DONE]\n\n".toByteArray())
                out.flush()
            }
        }

        val client: HttpClient = GlobalContext.get().get()
        val sseReader = DefensiveSseStreamReader()
        val received = mutableListOf<String>()

        runBlocking {
            client.prepareGet("http://127.0.0.1:${server.localPort}/stream").execute { response ->
                sseReader.readEvents(response.bodyAsChannel()).collect { event ->
                    if (event is ChatStreamEvent.Delta) {
                        received += event.content
                        firstDeltaReceived.countDown()
                    }
                }
            }
        }

        assertEquals(listOf("first ", "second"), received)
    }
}
