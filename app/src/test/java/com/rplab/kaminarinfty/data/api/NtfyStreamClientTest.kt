package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Contract tests against a local MockWebServer. The public ntfy endpoint is
 * never contacted from unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NtfyStreamClientTest {

    private lateinit var server: MockWebServer

    private val messageLine =
        """{"id":"m1","time":1700000001,"event":"message","topic":"ops","message":"hello","priority":4}"""
    private val secondMessageLine =
        """{"id":"m2","time":1700000002,"event":"message","topic":"media","message":"second","title":"T"}"""
    private val openLine = """{"id":"o1","time":1700000000,"event":"open"}"""
    private val keepAliveLine = """{"id":"k1","time":1700000000,"event":"keepalive"}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config(
        url: String = server.url("/").toString(),
        authMethod: AuthMethod = AuthMethod.GUEST,
        token: String = "",
        username: String = "",
        useHttpsOnly: Boolean = false,
    ) = ServerConfig(
        serverUrl = url,
        authMethod = authMethod,
        tokenOrPassword = token,
        username = username,
        useHttpsOnly = useHttpsOnly,
    )

    private fun streamBody(vararg lines: String): String = lines.joinToString("\n") + "\n"

    @Test
    fun `subscribes to multiple topics with a since cursor`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(streamBody(openLine, messageLine)))

        val events = NftyStreamClient().openJsonStream(
            config = config(),
            topics = listOf("ops", "media"),
            since = "abcd1234",
        ).toList()

        val request = server.takeRequest()
        assertEquals("/ops,media/json?since=abcd1234", request.path)
        assertEquals("application/x-ndjson", request.getHeader("Accept"))
        assertTrue(events.first() is NftyStreamEvent.Connected)
        assertEquals("m1", (events[2] as NftyStreamEvent.MessageReceived).message.id)
    }

    @Test
    fun `bearer token is sent as an authorization header, never in the url`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(streamBody(openLine)))

        NftyStreamClient().openJsonStream(
            config = config(authMethod = AuthMethod.ACCESS_TOKEN, token = "tk_secret"),
            topics = listOf("ops"),
        ).toList()

        val request = server.takeRequest()
        assertEquals("Bearer tk_secret", request.getHeader("Authorization"))
        assertTrue(!request.path.orEmpty().contains("tk_secret"))
    }

    @Test
    fun `username and password use basic auth`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(streamBody(openLine)))

        NftyStreamClient().openJsonStream(
            config = config(
                authMethod = AuthMethod.USERNAME_PASSWORD,
                username = "kaminari",
                token = "pw",
            ),
            topics = listOf("ops"),
        ).toList()

        val header = server.takeRequest().getHeader("Authorization").orEmpty()
        assertTrue(header.startsWith("Basic "))
    }

    @Test
    fun `authenticated stream reports AUTHENTICATED only after the server opens it`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(streamBody(openLine)))

        val events = NftyStreamClient().openJsonStream(
            config = config(authMethod = AuthMethod.ACCESS_TOKEN, token = "tk_secret"),
            topics = listOf("ops"),
        ).toList()

        assertEquals(AuthState.AUTHENTICATED, (events.first() as NftyStreamEvent.Connected).authState)
    }

    @Test
    fun `guest stream reports GUEST`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(streamBody(openLine)))

        val events = NftyStreamClient().openJsonStream(config(), listOf("ops")).toList()

        assertEquals(AuthState.GUEST, (events.first() as NftyStreamEvent.Connected).authState)
    }

    @Test
    fun `401 is a terminal authentication error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val events = NftyStreamClient().openJsonStream(config(), listOf("ops")).toList()

        val error = events.filterIsInstance<NftyStreamEvent.Error>().single()
        assertEquals(NftyErrorCategory.AUTH, error.category)
        assertEquals(false, error.canRetry)
    }

    @Test
    fun `403 and 404 are terminal permission errors`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(404))

        val forbidden = NftyStreamClient().openJsonStream(config(), listOf("ops")).toList()
            .filterIsInstance<NftyStreamEvent.Error>().single()
        val notFound = NftyStreamClient().openJsonStream(config(), listOf("missing")).toList()
            .filterIsInstance<NftyStreamEvent.Error>().single()

        assertEquals(NftyErrorCategory.FORBIDDEN, forbidden.category)
        assertEquals(false, forbidden.canRetry)
        assertEquals(NftyErrorCategory.FORBIDDEN, notFound.category)
        assertEquals(false, notFound.canRetry)
    }

    @Test
    fun `5xx and 429 are retriable`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(429))

        val unavailable = NftyStreamClient().openJsonStream(config(), listOf("ops")).toList()
            .filterIsInstance<NftyStreamEvent.Error>().single()
        val rateLimited = NftyStreamClient().openJsonStream(config(), listOf("ops")).toList()
            .filterIsInstance<NftyStreamEvent.Error>().single()

        assertTrue(unavailable.canRetry)
        assertTrue(rateLimited.canRetry)
    }

    @Test
    fun `malformed lines are reported without aborting the stream`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(streamBody("{not json", messageLine, """{"id":"x","time":1,"event":"mystery"}"""))
        )

        val events = NftyStreamClient().openJsonStream(config(), listOf("ops")).toList()

        assertEquals(2, events.filterIsInstance<NftyStreamEvent.Malformed>().size)
        assertEquals(1, events.filterIsInstance<NftyStreamEvent.MessageReceived>().size)
    }

    @Test
    fun `message_delete and message_clear are surfaced as lifecycle events`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                streamBody(
                    """{"id":"gone1","time":1700000003,"event":"message_delete","topic":"ops"}""",
                    """{"id":"clear1","time":1700000004,"event":"message_clear","topic":"ops"}""",
                )
            )
        )

        val events = NftyStreamClient().openJsonStream(config(), listOf("ops")).toList()

        assertEquals("gone1", (events.filterIsInstance<NftyStreamEvent.MessageDeleted>().single()).messageId)
        assertEquals("ops", events.filterIsInstance<NftyStreamEvent.TopicCleared>().single().topic)
    }

    @Test
    fun `multiple messages and keepalives are parsed in order`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(streamBody(openLine, keepAliveLine, messageLine, secondMessageLine))
        )

        val events = NftyStreamClient().openJsonStream(config(), listOf("ops", "media")).toList()
        val messages = events.filterIsInstance<NftyStreamEvent.MessageReceived>().map { it.message.id }

        assertEquals(listOf("m1", "m2"), messages)
        // `open` and `keepalive` are both liveness signals at this layer.
        assertEquals(2, events.filterIsInstance<NftyStreamEvent.KeepAlive>().size)
    }

    @Test
    fun `poll catch-up requests poll=1`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(streamBody(messageLine)))

        NftyStreamClient().openJsonStream(config(), listOf("ops"), since = "12h", poll = true).toList()

        assertEquals("/ops/json?since=12h&poll=1", server.takeRequest().path)
    }

    @Test
    fun `cleartext http is rejected before any request when https is enforced`() = runBlocking {
        val events = NftyStreamClient().openJsonStream(
            config(url = "http://127.0.0.1:${server.port}", useHttpsOnly = true),
            topics = listOf("ops"),
        ).toList()

        val error = events.filterIsInstance<NftyStreamEvent.Error>().single()
        assertEquals(false, error.canRetry)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalid topic names are rejected without a request`() = runBlocking {
        val events = NftyStreamClient().openJsonStream(config(), listOf("bad/topic")).toList()

        assertTrue(events.filterIsInstance<NftyStreamEvent.Error>().single().message.contains("Invalid topic"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `health probe succeeds and reports an unverified credential as REACHABLE`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"healthy":true}"""))

        val result = NftyStreamClient().testConnection(
            config(authMethod = AuthMethod.ACCESS_TOKEN, token = "tk")
        )

        assertEquals(AuthState.REACHABLE, result.getOrThrow())
        assertEquals("/v1/health", server.takeRequest().path)
    }

    @Test
    fun `health probe fails on an unhealthy server response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"healthy":false}"""))

        assertTrue(NftyStreamClient().testConnection(config()).isFailure)
    }

    @Test
    fun `health probe classifies 401 as a failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = NftyStreamClient().testConnection(config())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("401"))
    }

    @Test
    fun `offline server fails the probe instead of hanging`() = runBlocking {
        server.shutdown()

        val result = NftyStreamClient().testConnection(config(url = "http://127.0.0.1:${server.port}"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `socket timeout is classified as TIMEOUT and retriable`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val impatient = OkHttpClient.Builder()
            .readTimeout(300, TimeUnit.MILLISECONDS)
            .connectTimeout(300, TimeUnit.MILLISECONDS)
            .build()

        val events = NftyStreamClient(impatient).openJsonStream(config(), listOf("ops")).toList()

        val error = events.filterIsInstance<NftyStreamEvent.Error>().single()
        assertEquals(NftyErrorCategory.TIMEOUT, error.category)
        assertTrue(error.canRetry)
    }

    @Test
    fun `cancelling collection cancels the underlying call and does not leak`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = NftyStreamClient()

        val job = launch(Dispatchers.Default) {
            client.openJsonStream(config(), listOf("ops")).collect { }
        }

        withTimeout(5_000) {
            while (server.requestCount == 0) delay(10)
        }
        withTimeout(5_000) { job.cancelAndJoinBlocking() }
    }

    private suspend fun kotlinx.coroutines.Job.cancelAndJoinBlocking() {
        cancel()
        join()
    }
}
