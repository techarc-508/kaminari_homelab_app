package com.rplab.kaminarinfty.contract

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KfCatalogTest {

    private fun contractFile(): File {
        val candidates = listOf(
            File("contracts/kf_contract.v1.json"),
            File("../contracts/kf_contract.v1.json"),
            File("../../contracts/kf_contract.v1.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("contract JSON not found from ${File(".").absolutePath}")
    }

    private fun contract(): JSONObject = JSONObject(contractFile().readText())

    @Test
    fun `catalogue has exactly the 22 approved topics`() {
        assertEquals(22, KfTopicCatalog.topics.size)
        assertEquals(22, KfTopicCatalog.topics.map { it.id }.toSet().size)
    }

    @Test
    fun `excluded and unapproved topic names are absent`() {
        assertFalse(KfTopicCatalog.isApproved("kf-builds"))
        assertFalse(KfTopicCatalog.isApproved("rplab-web"))
        assertTrue(KfTopicCatalog.isApproved("kf-web"))
        assertNull(KfTopicCatalog.topic("kf-builds"))
    }

    @Test
    fun `all required categories exist`() {
        assertEquals(
            KfTopicCategory.entries.toSet(),
            KfTopicCatalog.topics.map { it.category }.toSet(),
        )
    }

    @Test
    fun `reserved topics are explicit`() {
        assertEquals(
            setOf("kf-stickers-pipeline", "kf-stickers-reports", "kf-iot"),
            KfTopicCatalog.reserved.map { it.id }.toSet(),
        )
        assertTrue(KfTopicCatalog.topic("kf-music-pipeline")!!.reserved.not())
    }

    @Test
    fun `legacy topics remain documented and never deleted implicitly`() {
        assertEquals(3, KfTopicCatalog.legacyTopics.size)
        assertEquals(
            setOf("KAMINARI-FUJI-Homelab", "kaminari-diary", "kaminari-onboarding-test"),
            KfTopicCatalog.legacyTopics.map { it.id }.toSet(),
        )
        assertTrue(
            KfTopicCatalog.legacyTopics.single { it.id == "KAMINARI-FUJI-Homelab" }
                .replacement.contains("kf-pve-host")
        )
        assertTrue(KfTopicCatalog.legacyTopics.all { it.rule.isNotBlank() })
        assertTrue(
            KfTopicCatalog.legacyTopics.single { it.id == "KAMINARI-FUJI-Homelab" }
                .rule.contains("owner approval", ignoreCase = true)
        )
    }

    @Test
    fun `unresolved diary ownership is recorded rather than invented`() {
        val diary = KfTopicCatalog.topic("kf-diary")!!
        assertEquals("P-07", diary.openDecision)
        assertTrue(diary.writers.isEmpty())
    }

    @Test
    fun `exactly one approved administrator exists`() {
        assertEquals(7, KfIdentityCatalog.identities.size)
        assertEquals(listOf("kfadmin"), KfIdentityCatalog.administrators.map { it.name })
        assertTrue(KfIdentityCatalog.identities.single { it.name == "kfadmin" }.ntfyAdmin)
        assertFalse(KfIdentityCatalog.identities.single { it.name == "kaminari_app" }.ntfyAdmin)
        assertFalse(KfIdentityCatalog.identities.single { it.name == "kf_gateway" }.ntfyAdmin)
    }

    @Test
    fun `android identity is read-mostly and writes only command requests`() {
        assertEquals(
            listOf("kf-command-request"),
            KfTopicCatalog.androidWritable.map { it.id },
        )
        assertEquals(21, KfTopicCatalog.androidReadable.size)
        assertFalse(KfIdentityCatalog.canWrite("kaminari_app", "kf-command-result"))
        assertFalse(KfIdentityCatalog.canWrite("kaminari_app", "kf-admin-audit"))
        assertFalse(KfIdentityCatalog.canWrite("kaminari_app", "kf-security"))
        assertTrue(KfIdentityCatalog.canWrite("kaminari_app", "kf-command-request"))
        assertTrue(KfIdentityCatalog.canRead("kaminari_app", "kf-command-result"))
        assertTrue(KfIdentityCatalog.canRead("kaminari_app", "kf-admin-audit"))
    }

    @Test
    fun `gateway identity is separate and minimal`() {
        assertTrue(KfIdentityCatalog.canRead("kf_gateway", "kf-command-request"))
        assertTrue(KfIdentityCatalog.canWrite("kf_gateway", "kf-command-result"))
        assertTrue(KfIdentityCatalog.canWrite("kf_gateway", "kf-admin-audit"))
        assertFalse(KfIdentityCatalog.canWrite("kf_gateway", "kf-command-request"))
        assertFalse(KfIdentityCatalog.canRead("kf_gateway", "kf-services"))
        assertFalse(KfIdentityCatalog.canWrite("kf_gateway", "kf-web"))
    }

    @Test
    fun `producer identities cannot read or cross domains`() {
        assertTrue(KfIdentityCatalog.canWrite("kf_monitor", "kf-pve-host"))
        assertFalse(KfIdentityCatalog.canRead("kf_monitor", "kf-pve-host"))
        assertFalse(KfIdentityCatalog.canWrite("kf_monitor", "kf-music-pipeline"))
        assertFalse(KfIdentityCatalog.canWrite("kf_music", "kf-news"))
        assertFalse(KfIdentityCatalog.canWrite("kf_news", "kf-admin-audit"))
        assertFalse(KfIdentityCatalog.canWrite("kf_stickers", "kf-diary"))
    }

    @Test
    fun `unknown identity and topic are denied by default`() {
        assertEquals(
            KfPermission.NONE,
            KfIdentityCatalog.permission("anonymous", "kf-web"),
        )
        assertEquals(
            KfPermission.NONE,
            KfIdentityCatalog.permission("kaminari_app", "not-a-topic"),
        )
        assertFalse(KfIdentityCatalog.canWrite("anonymous", "kf-news"))
    }

    @Test
    fun `action catalogue is allowlisted and complete`() {
        assertEquals(14, KfActionCatalog.actions.size)
        assertEquals(14, KfActionCatalog.actions.map { it.id }.toSet().size)
        assertEquals(4, KfActionCatalog.actions.count { it.risk == KfRisk.L2 })
        assertEquals(6, KfActionCatalog.mutations.size)
        assertTrue(KfActionCatalog.actions.all { KfIdentityCatalog.identity(it.requiredIdentity) != null })
        assertTrue(KfActionCatalog.actions.all { it.requiredIdentity == "kf_gateway" })
        assertTrue(KfActionCatalog.mutations.all { it.stepUp && it.audit })
        assertTrue(KfActionCatalog.mutations.filter { it.risk == KfRisk.L2 }.all { it.ownerConfirmation })
        assertTrue(KfActionCatalog.actions.filter { !it.mutation }.all { it.risk == KfRisk.L0 && !it.stepUp })
    }

    @Test
    fun `required arguments match the gateway contract`() {
        assertEquals(emptyList<String>(), KfActionCatalog.action("host.health")!!.args)
        assertEquals(listOf("node"), KfActionCatalog.action("vm.list")!!.args)
        assertEquals(listOf("node", "vmid"), KfActionCatalog.action("vm.status")!!.args)
        assertEquals(listOf("node"), KfActionCatalog.action("storage.summary")!!.args)
        assertEquals(listOf("node", "vmid"), KfActionCatalog.action("container.restart")!!.args)
    }

    @Test
    fun `native catalogue matches the shared contract json`() {
        val json = contract()
        val topics = json.getJSONArray("topics")
        assertEquals(KfTopicCatalog.topics.size, topics.length())
        for (index in 0 until topics.length()) {
            val item = topics.getJSONObject(index)
            val spec = KfTopicCatalog.topic(item.getString("id"))
            assertNotNull("missing topic ${item.getString("id")}", spec)
            assertEquals(item.getString("category"), spec!!.category.name)
            assertEquals(item.getString("schema"), spec.schema)
            assertEquals(item.getBoolean("reserved"), spec.reserved)
            assertEquals(item.getInt("retentionDays"), spec.retentionDays)
            assertEquals(item.getString("priority").uppercase(), spec.priority.name)
            assertEquals(item.getJSONArray("readers").toStringSet(), spec.readers)
            assertEquals(item.getJSONArray("writers").toStringSet(), spec.writers)
        }

        val identities = json.getJSONArray("identities")
        assertEquals(KfIdentityCatalog.identities.size, identities.length())
        for (index in 0 until identities.length()) {
            val item = identities.getJSONObject(index)
            val spec = KfIdentityCatalog.identity(item.getString("name"))
            assertNotNull(spec)
            assertEquals(item.getBoolean("ntfyAdmin"), spec!!.ntfyAdmin)
        }

        val actions = json.getJSONArray("actions")
        assertEquals(KfActionCatalog.actions.size, actions.length())
        for (index in 0 until actions.length()) {
            val item = actions.getJSONObject(index)
            val spec = KfActionCatalog.action(item.getString("id"))
            assertNotNull("missing action ${item.getString("id")}", spec)
            assertEquals(item.getString("risk"), spec!!.risk.name)
            assertEquals(item.getBoolean("mutation"), spec.mutation)
            assertEquals(item.getBoolean("stepUp"), spec.stepUp)
            assertEquals(item.getBoolean("ownerConfirmation"), spec.ownerConfirmation)
            assertEquals(item.getJSONArray("args").toStringList(), spec.args)
        }

        assertEquals(
            json.getString("commandReplyTopic"),
            KfActionCatalog.COMMAND_REPLY_TOPIC,
        )
        assertEquals(
            json.getJSONArray("legacyCommandReplyTopics").toStringList(),
            listOf(KfActionCatalog.LEGACY_COMMAND_REPLY_TOPIC),
        )
    }

    private fun org.json.JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private fun org.json.JSONArray.toStringSet(): Set<String> = toStringList().toSet()
}
