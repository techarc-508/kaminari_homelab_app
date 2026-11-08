package com.rplab.kaminarinfty.data.local

import com.rplab.kaminarinfty.model.NftyAction
import com.rplab.kaminarinfty.model.NftyAttachment
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessageMapperTest {

    @Test
    fun `full roundtrip preserves structured fields`() {
        val original = NftyMessage(
            id = "m1",
            time = 1_700_000_000_000,
            topic = "homelab",
            title = "Backup done",
            message = "pve backup finished",
            priority = NftyPriority.HIGH,
            tags = listOf("floppy_disk", "white_check_mark"),
            click = "https://pve.local",
            attachment = NftyAttachment(
                name = "log.txt",
                url = "https://ntfy.test/file/abc",
                type = "text/plain",
                size = 512,
            ),
            actions = listOf(
                NftyAction(id = "view:1", action = "view", label = "Open", url = "https://pve.local"),
                NftyAction(id = "confirm:2", action = "broadcast", label = "Ack", url = null),
            ),
            isRead = true,
            isSaved = true,
        )

        val restored = MessageMapper.toDomain(MessageMapper.toEntity("primary", original))

        assertEquals(original.id, restored.id)
        assertEquals(original.time, restored.time)
        assertEquals(original.topic, restored.topic)
        assertEquals(original.title, restored.title)
        assertEquals(original.message, restored.message)
        assertEquals(original.priority, restored.priority)
        assertEquals(original.tags, restored.tags)
        assertEquals(original.click, restored.click)
        assertEquals(original.attachment, restored.attachment)
        assertEquals(original.actions, restored.actions)
        assertEquals(true, restored.isRead)
        assertEquals(true, restored.isSaved)
    }

    @Test
    fun `null optional columns survive the roundtrip`() {
        val original = NftyMessage(
            id = "m2",
            time = 1,
            topic = "t",
            message = "plain",
        )

        val restored = MessageMapper.toDomain(MessageMapper.toEntity("primary", original))

        assertNull(restored.title)
        assertNull(restored.click)
        assertNull(restored.attachment)
        assertEquals(emptyList<NftyAction>(), restored.actions)
        assertEquals(emptyList<String>(), restored.tags)
    }

    @Test
    fun `malformed json columns degrade to empty values`() {
        val broken = MessageEntity(
            profileId = "primary",
            messageId = "m3",
            time = 1,
            topic = "t",
            title = null,
            body = "x",
            priority = 3,
            tagsJson = "{not-json",
            clickUrl = null,
            attachmentJson = "nope",
            actionsJson = "[",
            expiry = null,
        )

        val restored = MessageMapper.toDomain(broken)

        assertEquals(emptyList<String>(), restored.tags)
        assertNull(restored.attachment)
        assertEquals(emptyList<NftyAction>(), restored.actions)
    }
}
