package com.rplab.kaminarinfty.admin

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminContractTest {

    @Test
    fun `every administration section has a capability`() {
        AdminSection.entries.forEach { section ->
            assertTrue(
                "section ${section.name} has no capability",
                AdminCatalog.bySection(section).isNotEmpty(),
            )
        }
    }

    @Test
    fun `no capability claims to be available in phase one`() {
        assertTrue(
            AdminCatalog.capabilities.none {
                it.state == AdminCapabilityState.UNAVAILABLE && it.operationId != null
            }
        )
        assertTrue(
            AdminCatalog.capabilities.all {
                it.state == AdminCapabilityState.PLANNED ||
                    it.state == AdminCapabilityState.GATEWAY_REQUIRED
            }
        )
        assertTrue(AdminCatalog.capabilities.any { it.state == AdminCapabilityState.GATEWAY_REQUIRED })
    }

    @Test
    fun `mutations are explicitly marked and never local`() {
        assertTrue(AdminCatalog.mutations.isNotEmpty())
        assertTrue(AdminCatalog.mutations.all { it.mutation })
        assertTrue(AdminCatalog.mutations.all { it.operationId?.startsWith("admin.ntfy") == true })
    }

    @Test
    fun `operation ids are unique`() {
        val ids = AdminCatalog.capabilities.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `unavailable service is honest and performs nothing`() = runTest {
        val result = UnavailableGatewayAdminService.execute(
            AdminOperationRequest(
                operationId = "admin.ntfy.users.list",
                target = "ntfy",
                correlationId = "corr-1",
            )
        )
        assertTrue(result is AdminOperationResult.Unavailable)
        assertTrue(
            (result as AdminOperationResult.Unavailable).reason.contains("gateway", ignoreCase = true)
        )
    }

    @Test
    fun `no direct privileged ntfy client remains in the production source`() {
        val mainSource = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
        ).firstOrNull { it.exists() }
            ?: error("main source tree not found from ${File(".").absolutePath}")

        val offenders = mainSource.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val text = file.readText()
                text.contains("NtfyAdminClient") ||
                    text.contains("/v1/access") ||
                    text.contains("/v1/users")
            }
            .map { it.relativeTo(mainSource).path }
            .toList()

        assertTrue("privileged ntfy client code found in APK sources: $offenders", offenders.isEmpty())
        assertFalse(File("src/main/java/com/rplab/kaminarinfty/data/api/NtfyAdminClient.kt").exists())
    }
}
