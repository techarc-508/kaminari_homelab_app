package com.rplab.kaminarinfty

import com.rplab.kaminarinfty.model.ActivityEvent
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.ActivityType
import com.rplab.kaminarinfty.testing.FakeActivityRepository
import com.rplab.kaminarinfty.testing.FakeProfileRepository
import com.rplab.kaminarinfty.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var activity: FakeActivityRepository
    private lateinit var profiles: FakeProfileRepository

    @Before
    fun setUp() {
        activity = FakeActivityRepository()
        profiles = FakeProfileRepository().apply { activeFlow.value = "primary" }
        activity.events.value = listOf(
            event("1", ActivityType.PUBLISH_ACCEPTED, ActivitySeverity.SUCCESS, "Publish accepted"),
            event("2", ActivityType.MESSAGE_RECEIVED, ActivitySeverity.INFO, "Message received"),
            event("3", ActivityType.PUBLISH_AMBIGUOUS, ActivitySeverity.WARNING, "Outcome unknown"),
            event("4", ActivityType.PUBLISH_FAILED, ActivitySeverity.ERROR, "Publish failed"),
        )
    }

    private fun event(
        id: String,
        type: ActivityType,
        severity: ActivitySeverity,
        summary: String,
    ) = ActivityEvent(
        id = id,
        profileId = "primary",
        type = type,
        severity = severity,
        summary = summary,
        createdAt = 0L,
    )

    private fun buildViewModel() = ActivityViewModel(activity, profiles)

    @Test
    fun `events load newest-first from the repository`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(4, vm.uiState.value.events.size)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `publish filter keeps only publish transitions`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setFilter(ActivityFilter.PUBLISH)
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.visibleEvents.size)
        assertTrue(vm.uiState.value.visibleEvents.all { it.type.name.startsWith("PUBLISH_") })
    }

    @Test
    fun `attention filter keeps warnings and errors`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setFilter(ActivityFilter.ATTENTION)

        assertEquals(
            listOf("3", "4"),
            vm.uiState.value.visibleEvents.map { it.id },
        )
    }

    @Test
    fun `receive filter keeps only inbox events`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setFilter(ActivityFilter.RECEIVE)

        assertEquals(listOf("2"), vm.uiState.value.visibleEvents.map { it.id })
    }

    @Test
    fun `active profile change rebinds the activity query`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        assertTrue(activity.observedProfiles.contains("primary"))

        profiles.activeFlow.value = "secondary"
        advanceUntilIdle()

        assertEquals("secondary", vm.uiState.value.activeProfileId)
        assertTrue(activity.observedProfiles.contains("secondary"))
    }

    @Test
    fun `no profile produces an empty idle timeline`() = runTest(mainRule.dispatcher) {
        profiles.activeFlow.value = null
        val vm = buildViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.events.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }
}
