package com.rplab.kaminarinfty.data.api

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NtfyAdminClientTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listsUsers() {
        server.start()
        val users = JSONArray()
            .put(JSONObject().put("username", "alice").put("role", "user").put("grants", JSONArray()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(users.toString()))

        val client = NtfyAdminClient(baseUrl = server.url("/").toString().trimEnd('/'), requireHttps = false)
        val result = client.listUsers("admin", "secret")
        assertTrue(result.isSuccess)
        assertEquals("alice", result.getOrNull()?.first()?.username)
    }

    @Test
    fun createsUser() {
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"success\":true}"))

        val client = NtfyAdminClient(baseUrl = server.url("/").toString().trimEnd('/'), requireHttps = false)
        val result = client.createUser("bob", "password", "admin", "secret")
        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/users", request.path)
    }

    @Test
    fun grantsAccess() {
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"success\":true}"))

        val client = NtfyAdminClient(baseUrl = server.url("/").toString().trimEnd('/'), requireHttps = false)
        val result = client.grantAccess("bob", "alerts", "read-write", "admin", "secret")
        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/v1/access", request.path)
    }
}
