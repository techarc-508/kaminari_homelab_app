package com.rplab.kaminarinfty

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.data.api.NftyStreamClient
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.NftyPriority
import com.rplab.kaminarinfty.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kaminari Nfty", appName)
  }

  @Test
  fun `server config defaults to correct endpoint`() {
    val config = ServerConfig()
    assertEquals("https://nfty.rplab.qzz.io", config.serverUrl)
    assertEquals(AuthMethod.GUEST, config.authMethod)
    assertTrue(config.useHttpsOnly)
  }

  @Test
  fun `priority mapping matches ntfy spec`() {
    assertEquals(NftyPriority.MIN, NftyPriority.fromLevel(1))
    assertEquals(NftyPriority.LOW, NftyPriority.fromLevel(2))
    assertEquals(NftyPriority.DEFAULT, NftyPriority.fromLevel(3))
    assertEquals(NftyPriority.HIGH, NftyPriority.fromLevel(4))
    assertEquals(NftyPriority.URGENT, NftyPriority.fromLevel(5))
  }

  @Test
  fun `client rejects URLs without a scheme before any network call`() {
    val result = NftyStreamClient().testConnection(ServerConfig(serverUrl = "nfty.test"))
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("http"))
  }
}
