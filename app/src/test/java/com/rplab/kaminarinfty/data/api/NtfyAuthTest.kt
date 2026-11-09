package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.ServerConfig
import okhttp3.Credentials
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyAuthTest {

    private fun config(
        method: AuthMethod,
        username: String = "",
        secret: String = "",
    ) = ServerConfig(
        serverUrl = "https://ntfy.example.com",
        authMethod = method,
        username = username,
        tokenOrPassword = secret,
    )

    @Test
    fun `guest emits no authorization header`() {
        val builder = newBuilder()
        NtfyAuth.apply(builder, config(AuthMethod.GUEST))

        assertNull(NtfyAuth.header(config(AuthMethod.GUEST)))
        assertNull(builder.build().header("Authorization"))
    }

    @Test
    fun `basic uses base64 credentials`() {
        val config = config(AuthMethod.USERNAME_PASSWORD, "alice", "s3cret")
        val builder = newBuilder()
        NtfyAuth.apply(builder, config)

        assertEquals(Credentials.basic("alice", "s3cret"), builder.build().header("Authorization"))
    }

    @Test
    fun `bearer uses access token`() {
        val config = config(AuthMethod.ACCESS_TOKEN, secret = "token-123")
        val builder = newBuilder()
        NtfyAuth.apply(builder, config)

        assertEquals("Bearer token-123", builder.build().header("Authorization"))
    }

    @Test
    fun `crlf in a pasted credential is stripped`() {
        val config = config(AuthMethod.ACCESS_TOKEN, secret = "tok\r\nen")
        val builder = newBuilder()
        NtfyAuth.apply(builder, config)

        assertEquals("Bearer token", builder.build().header("Authorization"))
    }

    @Test
    fun `basic without password emits nothing`() {
        assertNull(NtfyAuth.header(config(AuthMethod.USERNAME_PASSWORD, "alice", "")))
    }

    @Test
    fun `bearer without token emits nothing`() {
        assertNull(NtfyAuth.header(config(AuthMethod.ACCESS_TOKEN, secret = "")))
    }

    private fun newBuilder(): Request.Builder =
        Request.Builder().url("https://ntfy.example.com/alerts")
}
