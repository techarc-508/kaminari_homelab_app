package com.rplab.kaminarinfty.delivery

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.rplab.kaminarinfty.KaminariApp
import com.rplab.kaminarinfty.model.DeliveryMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatterySaverWorkerTest {

    private lateinit var app: KaminariApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `worker does not poll when the user has switched to instant mode`() = runBlocking {
        app.container.preferences.setDeliveryMode(DeliveryMode.INSTANT)

        val worker = TestListenableWorkerBuilder<BatterySaverWorker>(app).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `worker succeeds without network work when no profile is active`() = runBlocking {
        app.container.preferences.setDeliveryMode(DeliveryMode.BATTERY_SAVER)
        app.container.preferences.clearActiveProfileId()

        val worker = TestListenableWorkerBuilder<BatterySaverWorker>(app).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }
}
