package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.NftyStreamClient
import com.example.data.api.NftyStreamEvent
import com.example.model.AuthMethod
import com.example.model.AuthState
import com.example.model.NftyPriority
import com.example.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
  fun `viewmodel loads sample messages and topics`() {
    val viewModel = MainViewModel()
    val state = viewModel.uiState.value

    assertTrue("Sample messages should be populated", state.messages.isNotEmpty())
    assertTrue("Topics should be populated", state.topics.isNotEmpty())
    assertTrue("Initial state is CONNECTING or established", state.authState in listOf(AuthState.CONNECTING, AuthState.GUEST, AuthState.DISCONNECTED))
  }

  @Test
  fun `toggle save message works`() {
    val viewModel = MainViewModel()
    val firstMessageId = viewModel.uiState.value.messages.first().id
    val initialSaved = viewModel.uiState.value.messages.first().isSaved

    viewModel.toggleSaveMessage(firstMessageId)
    val afterToggle = viewModel.uiState.value.messages.first { it.id == firstMessageId }.isSaved
    assertEquals(!initialSaved, afterToggle)
  }

  @Test
  fun `mark all as read updates all messages`() {
    val viewModel = MainViewModel()
    viewModel.markAllAsRead()
    val allRead = viewModel.uiState.value.messages.all { it.isRead }
    assertTrue("All messages should be read", allRead)
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
}
