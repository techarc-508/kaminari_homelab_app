package com.rplab.kaminarinfty.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryModeTest {

    @Test
    fun `only the two owner-locked modes are selectable`() {
        assertEquals(
            listOf(DeliveryMode.INSTANT, DeliveryMode.BATTERY_SAVER),
            DeliveryMode.entries.toList(),
        )
    }

    @Test
    fun `legacy saved preference migrates to battery saver`() {
        assertEquals(DeliveryMode.BATTERY_SAVER, DeliveryMode.fromPersisted("SAVED"))
    }

    @Test
    fun `fresh install defaults to battery saver`() {
        assertEquals(DeliveryMode.BATTERY_SAVER, DeliveryMode.DEFAULT)
        assertEquals(DeliveryMode.BATTERY_SAVER, DeliveryMode.fromPersisted(null))
        assertEquals(DeliveryMode.BATTERY_SAVER, DeliveryMode.fromPersisted(""))
        assertEquals(DeliveryMode.BATTERY_SAVER, DeliveryMode.fromPersisted("BROKEN"))
    }

    @Test
    fun `current persisted values round trip`() {
        assertEquals(DeliveryMode.INSTANT, DeliveryMode.fromPersisted("INSTANT"))
        assertEquals(DeliveryMode.BATTERY_SAVER, DeliveryMode.fromPersisted("BATTERY_SAVER"))
    }
}
