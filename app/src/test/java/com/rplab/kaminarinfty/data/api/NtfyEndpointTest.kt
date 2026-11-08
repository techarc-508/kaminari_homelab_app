package com.rplab.kaminarinfty.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NtfyEndpointTest {

    @Test
    fun `normalizes trailing slash and whitespace`() {
        val result = NtfyEndpoint.normalizeBaseUrl("  https://ntfy.test/  ", requireHttps = true)
        assertEquals("https://ntfy.test", result.getOrThrow())
    }

    @Test
    fun `rejects urls without a scheme`() {
        val result = NtfyEndpoint.normalizeBaseUrl("ntfy.test", requireHttps = true)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("http://"))
    }

    @Test
    fun `rejects cleartext http when https is enforced`() {
        val result = NtfyEndpoint.normalizeBaseUrl("http://192.168.1.5:8080", requireHttps = true)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTPS is enforced"))
    }

    @Test
    fun `allows cleartext http only when explicitly permitted`() {
        val result = NtfyEndpoint.normalizeBaseUrl("http://192.168.1.5:8080/", requireHttps = false)
        assertEquals("http://192.168.1.5:8080", result.getOrThrow())
    }

    @Test
    fun `rejects credentials embedded in the url`() {
        val result = NtfyEndpoint.normalizeBaseUrl("https://user:pass@ntfy.test", requireHttps = true)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("must not be embedded"))
    }

    @Test
    fun `rejects query strings and fragments`() {
        assertTrue(NtfyEndpoint.normalizeBaseUrl("https://ntfy.test/?a=1", true).isFailure)
        assertTrue(NtfyEndpoint.normalizeBaseUrl("https://ntfy.test/#frag", true).isFailure)
    }

    @Test
    fun `builds a comma separated multi topic stream url with since`() {
        val url = NtfyEndpoint.streamUrl(
            rawBaseUrl = "https://ntfy.test/",
            topics = listOf("ops", "media"),
            since = "12h",
            requireHttps = true,
            poll = false,
        ).getOrThrow()

        assertEquals("https://ntfy.test/ops,media/json?since=12h", url)
    }

    @Test
    fun `adds poll flag for bounded catch-up`() {
        val url = NtfyEndpoint.streamUrl(
            rawBaseUrl = "https://ntfy.test",
            topics = listOf("ops"),
            since = "abcd1234",
            requireHttps = true,
            poll = true,
        ).getOrThrow()

        assertEquals("https://ntfy.test/ops/json?since=abcd1234&poll=1", url)
    }

    @Test
    fun `preserves a reverse proxy path prefix`() {
        val url = NtfyEndpoint.streamUrl(
            rawBaseUrl = "https://example.test/ntfy/",
            topics = listOf("ops"),
            since = "all",
            requireHttps = true,
            poll = false,
        ).getOrThrow()

        assertEquals("https://example.test/ntfy/ops/json?since=all", url)
    }

    @Test
    fun `rejects invalid topic names instead of encoding them`() {
        val url = NtfyEndpoint.streamUrl(
            rawBaseUrl = "https://ntfy.test",
            topics = listOf("ops/../../admin"),
            since = "12h",
            requireHttps = true,
            poll = false,
        )

        assertTrue(url.isFailure)
        assertTrue(url.exceptionOrNull()?.message.orEmpty().contains("Invalid topic"))
    }

    @Test
    fun `rejects invalid history cursors`() {
        val url = NtfyEndpoint.streamUrl(
            rawBaseUrl = "https://ntfy.test",
            topics = listOf("ops"),
            since = "12h&poll=1",
            requireHttps = true,
            poll = false,
        )

        assertTrue(url.isFailure)
        assertTrue(url.exceptionOrNull()?.message.orEmpty().contains("cursor"))
    }

    @Test
    fun `rejects an empty topic list`() {
        val url = NtfyEndpoint.streamUrl("https://ntfy.test", emptyList(), "12h", true, false)
        assertTrue(url.isFailure)
    }

    @Test
    fun `accepts documented since forms`() {
        assertTrue(NtfyEndpoint.isValidSince("all"))
        assertTrue(NtfyEndpoint.isValidSince("30m"))
        assertTrue(NtfyEndpoint.isValidSince("7d"))
        assertTrue(NtfyEndpoint.isValidSince("1700000000"))
        assertTrue(NtfyEndpoint.isValidSince("abcd1234EFGH"))
        // Alphanumeric strings are legal message IDs; reject real injection shapes.
        assertTrue(!NtfyEndpoint.isValidSince("12h&poll=1"))
        assertTrue(!NtfyEndpoint.isValidSince("12 h"))
        assertTrue(!NtfyEndpoint.isValidSince(""))
    }

    @Test
    fun `builds the health url under a path prefix`() {
        val url = NtfyEndpoint.healthUrl("https://example.test/ntfy", requireHttps = true).getOrThrow()
        assertEquals("https://example.test/ntfy/v1/health", url)
    }
}
