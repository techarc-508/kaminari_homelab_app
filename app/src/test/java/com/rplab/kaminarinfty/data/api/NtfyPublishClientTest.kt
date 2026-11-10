package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NtfyPublishClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NtfyPublishClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NtfyPublishClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun config(
        method: AuthMethod = AuthMethod.GUEST,
        username: String = "",
        secret: String = "",
    ) = ServerConfig(
        serverUrl = baseUrl(),
        authMethod = method,
        username = username,
        tokenOrPassword = secret,
        useHttpsOnly = false,
    )

    private fun request(
        topic: String = "test-topic",
        message: String = "Hello",
        title: String? = null,
        priority: Int = PublishRequest.PRIORITY_DEFAULT,
        tags: List<String> = emptyList(),
        clickUrl: String? = null,
        attachUrl: String? = null,
        delay: String? = null,
    ) = PublishRequest(
        topic = topic,
        message = message,
        title = title,
        priority = priority,
        tags = tags,
        clickUrl = clickUrl,
        attachUrl = attachUrl,
        delay = delay,
    )

    private fun enqueueJson(code: Int = 200, body: String = "{\"id\":\"msg_1\"}") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `guest publish sends no authorization header and parses message id`() = runBlocking {
        enqueueJson()

        val result = client.publish(config(), request())

        assertTrue(result is PublishResult.Accepted)
        assertEquals("msg_1", (result as PublishResult.Accepted).serverMessageId)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/test-topic", recorded.path)
        assertNull(recorded.getHeader("Authorization"))
        assertEquals("text/plain; charset=utf-8", recorded.getHeader("Content-Type"))
        assertEquals("Hello", recorded.body.readUtf8())
    }

    @Test
    fun `basic auth uses base64 credentials`() = runBlocking {
        enqueueJson()

        val result = client.publish(
            config(AuthMethod.USERNAME_PASSWORD, username = "alice", secret = "s3cret"),
            request(),
        )

        assertTrue(result is PublishResult.Accepted)
        assertEquals(
            Credentials.basic("alice", "s3cret"),
            server.takeRequest().getHeader("Authorization"),
        )
    }

    @Test
    fun `bearer auth uses access token`() = runBlocking {
        enqueueJson()

        val result = client.publish(
            config(AuthMethod.ACCESS_TOKEN, secret = "token-123"),
            request(),
        )

        assertTrue(result is PublishResult.Accepted)
        assertEquals("Bearer token-123", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `invalid topic is rejected before any network call`() = runBlocking {
        val result = client.publish(config(), request(topic = "bad topic!"))

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `blank message is rejected before any network call`() = runBlocking {
        val result = client.publish(config(), request(message = "   "))

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `priority outside ntfy range is rejected`() = runBlocking {
        val result = client.publish(config(), request(priority = 9))

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalid click url is rejected`() = runBlocking {
        val result = client.publish(config(), request(clickUrl = "ftp://example.com"))

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalid delay is rejected`() = runBlocking {
        val result = client.publish(config(), request(delay = "tomorrow"))

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing access token is rejected before any network call`() = runBlocking {
        val result = client.publish(config(AuthMethod.ACCESS_TOKEN, secret = ""), request())

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `credentials in server url are rejected`() = runBlocking {
        val config = ServerConfig(
            serverUrl = "http://user:pass@127.0.0.1:${server.port}",
            authMethod = AuthMethod.GUEST,
            useHttpsOnly = false,
        )

        val result = client.publish(config, request())

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `metadata is encoded as json`() = runBlocking {
        enqueueJson()

        val result = client.publish(
            config(),
            request(
                message = "Disk almost full",
                title = "Storage warning",
                priority = 5,
                tags = listOf("warning", "disk"),
                clickUrl = "https://example.com/panel",
                attachUrl = "https://example.com/chart.png",
                delay = "30m",
            ),
        )

        assertTrue(result is PublishResult.Accepted)
        val recorded = server.takeRequest()
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"message\":\"Disk almost full\""))
        assertTrue(body.contains("\"title\":\"Storage warning\""))
        assertTrue(body.contains("\"priority\":5"))
        assertTrue(body.contains("\"tags\":[\"warning\",\"disk\"]"))
        assertTrue(body.contains("\"click\":\"https://example.com/panel\""))
        assertTrue(body.contains("\"attach\":\"https://example.com/chart.png\""))
        assertTrue(body.contains("\"delay\":\"30m\""))
    }

    @Test
    fun `plain message uses text body`() = runBlocking {
        enqueueJson(body = "")

        val result = client.publish(config(), request(message = "Plain text body"))

        assertTrue(result is PublishResult.Accepted)
        assertNull((result as PublishResult.Accepted).serverMessageId)
        val recorded = server.takeRequest()
        assertEquals("text/plain; charset=utf-8", recorded.getHeader("Content-Type"))
        assertEquals("Plain text body", recorded.body.readUtf8())
    }

    @Test
    fun `401 maps to authentication failure`() = runBlocking {
        enqueueJson(code = 401, body = "unauthorized")

        val result = client.publish(config(AuthMethod.ACCESS_TOKEN, secret = "bad"), request())

        assertTrue(result is PublishResult.AuthenticationFailed)
        assertEquals(401, (result as PublishResult.AuthenticationFailed).statusCode)
    }

    @Test
    fun `403 maps to permission failure`() = runBlocking {
        enqueueJson(code = 403, body = "forbidden")

        val result = client.publish(config(), request())

        assertTrue(result is PublishResult.PermissionFailed)
        assertEquals(403, (result as PublishResult.PermissionFailed).statusCode)
    }

    @Test
    fun `400 maps to validation failure`() = runBlocking {
        enqueueJson(code = 400, body = "bad request")

        val result = client.publish(config(), request())

        assertTrue(result is PublishResult.ValidationFailed)
    }

    @Test
    fun `500 maps to server error`() = runBlocking {
        enqueueJson(code = 500, body = "boom")

        val result = client.publish(config(), request())

        assertTrue(result is PublishResult.ServerError)
        assertEquals(500, (result as PublishResult.ServerError).statusCode)
    }

    @Test
    fun `redirects are not followed to avoid duplicate sends`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/elsewhere"))

        val result = client.publish(config(), request())

        assertTrue(result is PublishResult.ServerError)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `unreachable server maps to network error`() = runBlocking {
        val config = ServerConfig(
            serverUrl = "http://127.0.0.1:1",
            authMethod = AuthMethod.GUEST,
            useHttpsOnly = false,
        )

        val result = client.publish(config, request())

        assertTrue(result is PublishResult.NetworkError)
    }
}
