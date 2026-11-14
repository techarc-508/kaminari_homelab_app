package com.rplab.kaminarinfty.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * The private gateway CA must stay scoped to the gateway host. A regression here
 * would turn the bundled anchor into process-wide trust for every TLS host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkSecurityConfigTest {

    private data class Config(
        val kind: String,
        val cleartext: String?,
        val domains: MutableList<String> = mutableListOf(),
        val anchors: MutableList<String> = mutableListOf(),
    )

    private fun XmlPullParser.attr(name: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i) == name) return getAttributeValue(i)
        }
        return null
    }

    private fun parse(): List<Config> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(R.xml.network_security_config)
        val configs = mutableListOf<Config>()
        var current: Config? = null
        var inAnchors = false
        var lastTag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "base-config", "domain-config" -> {
                        current = Config(kind = parser.name, cleartext = parser.attr("cleartextTrafficPermitted"))
                        configs.add(current)
                    }
                    "trust-anchors" -> inAnchors = true
                    "certificates" -> if (inAnchors) {
                        current?.anchors?.add(parser.attr("src").orEmpty())
                    }
                }
                XmlPullParser.TEXT -> if (lastTag == "domain") {
                    parser.text?.trim()?.takeIf { it.isNotEmpty() }?.let { current?.domains?.add(it) }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "trust-anchors") inAnchors = false
                    lastTag = parser.name
                }
            }
            if (event == XmlPullParser.START_TAG) lastTag = parser.name
            event = parser.next()
        }
        return configs
    }

    @Test
    fun baseConfigTrustsOnlyThePlatformStore() {
        val base = parse().single { it.kind == "base-config" }
        assertEquals("false", base.cleartext)
        assertEquals(1, base.anchors.size)
    }

    @Test
    fun gatewayHostGetsTheBundledCa() {
        val gateway = parse().single { it.kind == "domain-config" && "100.82.207.40" in it.domains }
        assertEquals("false", gateway.cleartext)
        assertEquals(2, gateway.anchors.size)
        val platformAnchor = parse().single { it.kind == "base-config" }.anchors.single()
        assertTrue("gateway config must keep the platform store", platformAnchor in gateway.anchors)
        assertTrue(
            "gateway config must add exactly one private anchor",
            gateway.anchors.count { it != platformAnchor } == 1,
        )
    }

    @Test
    fun onlyTheGatewayHostHasAPrivateAnchor() {
        val base = parse().single { it.kind == "base-config" }.anchors.single()
        val withPrivate = parse()
            .filter { it.kind == "domain-config" && it.anchors.any { a -> a != base } }
        assertEquals(1, withPrivate.size)
        assertEquals(listOf("100.82.207.40"), withPrivate.first().domains)
    }

    @Test
    fun cleartextIsLimitedToLoopbackHarnesses() {
        val cleartext = parse().filter { it.cleartext == "true" }.flatMap { it.domains }.toSet()
        assertEquals(setOf("127.0.0.1", "localhost", "10.0.2.2"), cleartext)
    }
}
