package com.rplab.kaminarinfty.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecretStoreTest {

    private lateinit var store: SecretStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = KeystoreSecretStore(context)
    }

    private fun keystoreAvailable(): Boolean = runCatching {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }.isSuccess

    @Test
    fun `roundtrip through keystore-backed encryption`() {
        // Robolectric may not provide the AndroidKeyStore provider; real HW is required then.
        assumeTrue("AndroidKeyStore unavailable in this JVM", keystoreAvailable())

        store.put("primary", "tk_secret_value")
        assertEquals("tk_secret_value", store.get("primary"))

        store.put("primary", "rotated")
        assertEquals("rotated", store.get("primary"))

        store.remove("primary")
        assertNull(store.get("primary"))
    }

    @Test
    fun `unknown keys return null`() {
        assumeTrue("AndroidKeyStore unavailable in this JVM", keystoreAvailable())
        assertNull(store.get("missing"))
    }
}
