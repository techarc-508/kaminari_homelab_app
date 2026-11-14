package com.rplab.kaminarinfty.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayUrlPolicyTest {

    @Test
    fun normalizesTrailingSlash() {
        assertEquals("https://gateway.lan:8443", GatewayUrlPolicy.normalize(" https://gateway.lan:8443/ "))
    }

    @Test
    fun rejectsPathsAndQueries() {
        assertNull(GatewayUrlPolicy.normalize("https://gateway.lan/api"))
        assertNull(GatewayUrlPolicy.normalize("https://gateway.lan?x=1"))
    }

    @Test
    fun rejectsUserInfoAndUnsupportedSchemes() {
        assertNull(GatewayUrlPolicy.normalize("https://user:pass@gateway.lan"))
        assertNull(GatewayUrlPolicy.normalize("ftp://gateway.lan"))
        assertNull(GatewayUrlPolicy.normalize("gateway.lan"))
    }

    @Test
    fun plainHttpIsRejectedForRemoteHosts() {
        val error = GatewayUrlPolicy.validationError("http://gateway.lan")
        assertTrue(error!!.contains("https"))
    }

    @Test
    fun plainHttpLoopbackIsAllowed() {
        assertNull(GatewayUrlPolicy.validationError("http://127.0.0.1:18080"))
        assertNull(GatewayUrlPolicy.validationError("http://localhost:18080"))
        assertTrue(GatewayUrlPolicy.isLoopback("http://127.0.0.1:18080"))
    }

    @Test
    fun httpsIsAccepted() {
        assertNull(GatewayUrlPolicy.validationError("https://gateway.lan"))
        assertFalse(GatewayUrlPolicy.isLoopback("https://gateway.lan"))
    }

    @Test
    fun emptyUrlIsRejected() {
        assertEquals("Enter the gateway URL", GatewayUrlPolicy.validationError("   "))
    }
}
