package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.NftyPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NtfyEventParserTest {

    private fun parsed(line: String): NtfyEvent =
        (NtfyEventParser.parse(line) as? NtfyParseResult.Parsed)?.event
            ?: error("expected a parsed event for: $line")

    private fun invalid(line: String): NtfyParseResult.Invalid =
        NtfyEventParser.parse(line) as? NtfyParseResult.Invalid
            ?: error("expected an invalid result for: $line")

    @Test
    fun `parses a full message with every documented field`() {
        val event = parsed(
            """
            {"id":"abc123","time":1700000000,"event":"message","topic":"ops",
             "title":"Disk full","message":"pve is at 97%","priority":5,
             "tags":["warning","disk"],"click":"https://pve.local",
             "attachment":{"name":"disk.png","url":"https://ntfy.test/file/1","type":"image/png","size":2048},
             "actions":[{"id":"view","action":"view","label":"Open","url":"https://pve.local"}],
             "expires":1700003600}
            """.trimIndent()
        ) as NtfyEvent.Message

        val message = event.message
        assertEquals("abc123", message.id)
        assertEquals(1_700_000_000_000L, message.time)
        assertEquals("ops", message.topic)
        assertEquals("Disk full", message.title)
        assertEquals("pve is at 97%", message.message)
        assertEquals(NftyPriority.URGENT, message.priority)
        assertEquals(listOf("warning", "disk"), message.tags)
        assertEquals("https://pve.local", message.click)
        assertEquals("disk.png", message.attachment?.name)
        assertEquals(2048L, message.attachment?.size)
        assertEquals("view", message.actions.single().id)
        assertEquals(1_700_003_600L, message.expiry)
        assertEquals(false, message.isSample)
    }

    @Test
    fun `normalizes millisecond timestamps without double scaling`() {
        val event = parsed(
            """{"id":"m1","time":1700000000123,"event":"message","topic":"ops","message":"x"}"""
        ) as NtfyEvent.Message
        assertEquals(1_700_000_000_123L, event.message.time)
    }

    @Test
    fun `missing optional fields fall back safely`() {
        val event = parsed(
            """{"id":"m1","time":1700000000,"event":"message","topic":"ops"}"""
        ) as NtfyEvent.Message

        assertNull(event.message.title)
        assertEquals("", event.message.message)
        assertEquals(NftyPriority.DEFAULT, event.message.priority)
        assertTrue(event.message.tags.isEmpty())
        assertNull(event.message.attachment)
        assertTrue(event.message.actions.isEmpty())
        assertNull(event.message.expiry)
    }

    @Test
    fun `priority is clamped into the documented 1-5 range`() {
        val low = parsed(
            """{"id":"m1","time":1700000000,"event":"message","topic":"ops","priority":0}"""
        ) as NtfyEvent.Message
        val high = parsed(
            """{"id":"m2","time":1700000000,"event":"message","topic":"ops","priority":99}"""
        ) as NtfyEvent.Message

        assertEquals(NftyPriority.MIN, low.message.priority)
        assertEquals(NftyPriority.URGENT, high.message.priority)
    }

    @Test
    fun `blank title becomes null instead of an empty string`() {
        val event = parsed(
            """{"id":"m1","time":1700000000,"event":"message","topic":"ops","title":"  "}"""
        ) as NtfyEvent.Message
        assertNull(event.message.title)
    }

    @Test
    fun `message without id is rejected`() {
        assertTrue(invalid("""{"time":1700000000,"event":"message","topic":"ops"}""").reason.contains("id"))
    }

    @Test
    fun `message without topic is rejected`() {
        assertTrue(invalid("""{"id":"m1","time":1700000000,"event":"message"}""").reason.contains("topic"))
    }

    @Test
    fun `message without a timestamp is rejected`() {
        assertTrue(invalid("""{"id":"m1","event":"message","topic":"ops"}""").reason.contains("timestamp"))
    }

    @Test
    fun `unparseable json is reported, not dropped silently`() {
        assertTrue(invalid("{not json").reason.contains("unparseable"))
    }

    @Test
    fun `missing event type is rejected`() {
        assertTrue(invalid("""{"id":"m1","time":1,"topic":"ops"}""").reason.contains("event"))
    }

    @Test
    fun `unknown event types are surfaced as unknown`() {
        val event = parsed("""{"id":"e1","time":1700000000,"event":"poll_request_unknown"}""")
        assertEquals("poll_request_unknown", (event as NtfyEvent.Unknown).eventType)
    }

    @Test
    fun `message_delete carries the deleted message id`() {
        val event = parsed(
            """{"id":"del1","time":1700000000,"event":"message_delete","topic":"ops"}"""
        ) as NtfyEvent.MessageDeleted

        assertEquals("del1", event.messageId)
        assertEquals("ops", event.topic)
    }

    @Test
    fun `message_delete without an id is rejected`() {
        assertTrue(
            invalid("""{"time":1700000000,"event":"message_delete","topic":"ops"}""").reason.contains("id")
        )
    }

    @Test
    fun `message_clear carries the topic and tolerates a global clear`() {
        val scoped = parsed(
            """{"id":"c1","time":1700000000,"event":"message_clear","topic":"ops"}"""
        ) as NtfyEvent.TopicCleared
        val global = parsed(
            """{"id":"c2","time":1700000000,"event":"message_clear"}"""
        ) as NtfyEvent.TopicCleared

        assertEquals("ops", scoped.topic)
        assertNull(global.topic)
    }

    @Test
    fun `keepalive and open events are recognised`() {
        assertEquals(
            1_700_000_000_000L,
            (parsed("""{"id":"k1","time":1700000000,"event":"keepalive"}""") as NtfyEvent.KeepAlive).timestamp,
        )
        assertTrue(parsed("""{"id":"o1","time":1700000000,"event":"open"}""") is NtfyEvent.Open)
    }

    @Test
    fun `poll_request is recognised`() {
        val event = parsed(
            """{"id":"p1","time":1700000000,"event":"poll_request","topic":"ops"}"""
        ) as NtfyEvent.PollRequest
        assertEquals("ops", event.topic)
    }

    @Test
    fun `oversized bodies are rejected to bound memory`() {
        val body = "x".repeat(NtfyEventParser.MAX_LINE_BYTES)
        val result = NtfyEventParser.parse(
            """{"id":"m1","time":1700000000,"event":"message","topic":"ops","message":"$body"}"""
        )
        assertTrue(result is NtfyParseResult.Invalid)
    }

    @Test
    fun `attachment without a url is ignored`() {
        val event = parsed(
            """{"id":"m1","time":1700000000,"event":"message","topic":"ops","attachment":{"name":"a"}}"""
        ) as NtfyEvent.Message
        assertNull(event.message.attachment)
    }

    @Test
    fun `invalid tag entries are skipped rather than failing the message`() {
        val event = parsed(
            """{"id":"m1","time":1700000000,"event":"message","topic":"ops","tags":["ok",1,null]}"""
        ) as NtfyEvent.Message
        assertEquals(listOf("ok"), event.message.tags)
    }
}
