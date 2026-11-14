package com.rplab.kaminarinfty.contract

/**
 * Canonical, approved `kf-` topic catalogue (P-01).
 *
 * The cross-language contract data lives in `contracts/kf_contract.v1.json`.
 * [KfTopicCatalog] is the typed Android view of that data; a unit test asserts
 * exact parity, so neither side can drift silently.
 */
enum class KfTopicCategory {
    INFRASTRUCTURE,
    MUSIC,
    STICKERS,
    NEWS,
    ADMINISTRATION,
    OTHER,
}

enum class KfPriority {
    NORMAL,
    HIGH,
}

data class KfTopicSpec(
    val id: String,
    val category: KfTopicCategory,
    val description: String,
    val publisher: String,
    val readers: Set<String>,
    val writers: Set<String>,
    val reserved: Boolean,
    val retentionDays: Int,
    val priority: KfPriority,
    val schema: String,
    /** Non-null when this topic cannot be finalised until an owner decision. */
    val openDecision: String? = null,
)

data class KfLegacyTopicSpec(
    val id: String,
    val state: String,
    val replacement: List<String>,
    val rule: String,
)

object KfTopicCatalog {
    val legacyTopics: List<KfLegacyTopicSpec> = listOf(
        KfLegacyTopicSpec(
            id = "KAMINARI-FUJI-Homelab",
            state = "active",
            replacement = listOf(
                "kf-pve-host", "kf-pve-ct", "kf-pve-vm", "kf-backups",
                "kf-web", "kf-services", "kf-network",
            ),
            rule = "Never delete without explicit owner approval; migrate producers first.",
        ),
        KfLegacyTopicSpec(
            id = "kaminari-diary",
            state = "active",
            replacement = listOf("kf-diary"),
            rule = "Owner approval required before retirement.",
        ),
        KfLegacyTopicSpec(
            id = "kaminari-onboarding-test",
            state = "active",
            replacement = emptyList(),
            rule = "Test-only; restrict after onboarding migration (P-09).",
        ),
    )

    private fun infra(id: String, description: String, priority: KfPriority = KfPriority.NORMAL) =
        KfTopicSpec(
            id = id,
            category = KfTopicCategory.INFRASTRUCTURE,
            description = description,
            publisher = "kf_monitor",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_monitor"),
            reserved = false,
            retentionDays = 90,
            priority = priority,
            schema = "kf.infrastructure.v1",
        )

    val topics: List<KfTopicSpec> = listOf(
        infra("kf-web", "Website, TLS, tunnel, deploy and auth-outage events."),
        infra("kf-pve-host", "PVE node health, CPU/RAM pressure, node state and updates."),
        infra("kf-pve-ct", "LXC lifecycle, resource pressure and guest-agent state."),
        infra("kf-pve-vm", "QEMU lifecycle, resource pressure and guest-agent state."),
        infra("kf-storage", "ZFS/SMART, capacity, scrub and pool degradation.", KfPriority.HIGH),
        infra("kf-backups", "Backup start, result, verification, retention and restore drills. No full logs."),
        infra("kf-network", "WAN/LAN, DNS, tunnel, reverse proxy, firewall and VPN events."),
        infra("kf-services", "Docker and application health, restart loops and endpoint failures."),
        infra("kf-security", "CrowdSec, auth failures, SSH/PVE anomalies and firewall changes. No secrets.", KfPriority.HIGH),
        infra("kf-ntfy", "ntfy health, restarts, publish/auth failures and watchdog alerts."),
        KfTopicSpec(
            id = "kf-digest",
            category = KfTopicCategory.INFRASTRUCTURE,
            description = "Daily summary and quiet-hours rollup.",
            publisher = "kf_monitor",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_monitor"),
            reserved = false,
            retentionDays = 30,
            priority = KfPriority.NORMAL,
            schema = "kf.digest.v1",
        ),
        KfTopicSpec(
            id = "kf-music-pipeline",
            category = KfTopicCategory.MUSIC,
            description = "Memos request through download, metadata, library placement and Navidrome scan, stage-level only.",
            publisher = "kf_music",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_music"),
            reserved = false,
            retentionDays = 30,
            priority = KfPriority.NORMAL,
            schema = "kf.music.request.v1",
        ),
        KfTopicSpec(
            id = "kf-music-reports",
            category = KfTopicCategory.MUSIC,
            description = "One final report per music run or batch.",
            publisher = "kf_music",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_music"),
            reserved = false,
            retentionDays = 90,
            priority = KfPriority.NORMAL,
            schema = "kf.music.report.v1",
        ),
        KfTopicSpec(
            id = "kf-stickers-pipeline",
            category = KfTopicCategory.STICKERS,
            description = "Reserved: future Diary sticker generation stages.",
            publisher = "kf_stickers",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_stickers"),
            reserved = true,
            retentionDays = 30,
            priority = KfPriority.NORMAL,
            schema = "kf.stickers.request.v1",
        ),
        KfTopicSpec(
            id = "kf-stickers-reports",
            category = KfTopicCategory.STICKERS,
            description = "Reserved: future sticker run summary.",
            publisher = "kf_stickers",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_stickers"),
            reserved = true,
            retentionDays = 90,
            priority = KfPriority.NORMAL,
            schema = "kf.stickers.report.v1",
        ),
        KfTopicSpec(
            id = "kf-news",
            category = KfTopicCategory.NEWS,
            description = "News card only: headline, source, category, summary and original URL.",
            publisher = "kf_news",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_news"),
            reserved = false,
            retentionDays = 30,
            priority = KfPriority.NORMAL,
            schema = "kf.news.card.v1",
        ),
        KfTopicSpec(
            id = "kf-news-reports",
            category = KfTopicCategory.NEWS,
            description = "Scraper runs, counts, source failures and processing state.",
            publisher = "kf_news",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_news"),
            reserved = false,
            retentionDays = 90,
            priority = KfPriority.NORMAL,
            schema = "kf.news.report.v1",
        ),
        KfTopicSpec(
            id = "kf-command-request",
            category = KfTopicCategory.ADMINISTRATION,
            description = "Typed command envelope from the Android owner client. Never shell text.",
            publisher = "kaminari_app",
            readers = setOf("kf_gateway", "kfadmin"),
            writers = setOf("kaminari_app"),
            reserved = false,
            retentionDays = 30,
            priority = KfPriority.HIGH,
            schema = "kf.command.v1",
        ),
        KfTopicSpec(
            id = "kf-command-result",
            category = KfTopicCategory.ADMINISTRATION,
            description = "Correlated execution result, status and audit reference. No secrets.",
            publisher = "kf_gateway",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_gateway"),
            reserved = false,
            retentionDays = 90,
            priority = KfPriority.HIGH,
            schema = "kf.command.result.v1",
        ),
        KfTopicSpec(
            id = "kf-admin-audit",
            category = KfTopicCategory.ADMINISTRATION,
            description = "Administrative and command audit events. Never carries credentials.",
            publisher = "kf_gateway",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = setOf("kf_gateway"),
            reserved = false,
            retentionDays = 365,
            priority = KfPriority.HIGH,
            schema = "kf.admin.audit.v1",
        ),
        KfTopicSpec(
            id = "kf-diary",
            category = KfTopicCategory.OTHER,
            description = "Diary events and sticker synchronisation.",
            publisher = "diary app (identity unresolved, P-07)",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = emptySet(),
            openDecision = "P-07",
            reserved = false,
            retentionDays = 90,
            priority = KfPriority.NORMAL,
            schema = "kf.diary.v1",
        ),
        KfTopicSpec(
            id = "kf-iot",
            category = KfTopicCategory.OTHER,
            description = "Reserved: home automation and IoT state/alerts. No producer exists.",
            publisher = "none",
            readers = setOf("kaminari_app", "kfadmin"),
            writers = emptySet(),
            reserved = true,
            retentionDays = 90,
            priority = KfPriority.NORMAL,
            schema = "kf.infrastructure.v1",
        ),
    )

    private val byId: Map<String, KfTopicSpec> = topics.associateBy { it.id }

    fun topic(id: String): KfTopicSpec? = byId[id]

    fun isApproved(id: String): Boolean = byId.containsKey(id)

    fun byCategory(category: KfTopicCategory): List<KfTopicSpec> =
        topics.filter { it.category == category }

    val reserved: List<KfTopicSpec> = topics.filter { it.reserved }

    /** The Android identity is read-only except for command requests. */
    val androidReadable: List<KfTopicSpec> = topics.filter { "kaminari_app" in it.readers }
    val androidWritable: List<KfTopicSpec> = topics.filter { "kaminari_app" in it.writers }
}

enum class KfRole {
    ADMIN,
    USER,
}

data class KfIdentitySpec(
    val name: String,
    val role: KfRole,
    val ntfyAdmin: Boolean,
    val purpose: String,
)

enum class KfPermission {
    READ,
    WRITE,
    ADMIN,
    NONE,
}

/**
 * Approved identities after P-02. `kf_gateway` is deliberately separate from
 * `kaminari_app` because ntfy permissions are per user, not per token.
 */
object KfIdentityCatalog {
    val identities: List<KfIdentitySpec> = listOf(
        KfIdentitySpec(
            "kfadmin", KfRole.ADMIN, true,
            "Sole ntfy administrator. Server-side only; never embedded in Android, web or gateway.",
        ),
        KfIdentitySpec(
            "kaminari_app", KfRole.USER, false,
            "Restricted Android identity. Reads the notification ecosystem and writes command requests only.",
        ),
        KfIdentitySpec(
            "kf_gateway", KfRole.USER, false,
            "Separate non-admin gateway identity. Reads command requests; writes results and audit events.",
        ),
        KfIdentitySpec(
            "kf_monitor", KfRole.USER, false,
            "Infrastructure publisher. Writes only its approved domain topics.",
        ),
        KfIdentitySpec("kf_music", KfRole.USER, false, "Music pipeline publisher."),
        KfIdentitySpec(
            "kf_stickers", KfRole.USER, false,
            "Sticker pipeline publisher (reserved until a producer exists).",
        ),
        KfIdentitySpec("kf_news", KfRole.USER, false, "News pipeline publisher."),
    )

    private val byName = identities.associateBy { it.name }

    fun identity(name: String): KfIdentitySpec? = byName[name]

    val administrators: List<KfIdentitySpec> = identities.filter { it.ntfyAdmin }

    /**
     * Effective ntfy permission for one identity on one topic. Admin bypasses
     * explicit grants; everything else is deny-by-default.
     */
    fun permission(identity: String, topicId: String): KfPermission {
        val identitySpec = byName[identity] ?: return KfPermission.NONE
        if (identitySpec.ntfyAdmin) return KfPermission.ADMIN
        val topic = KfTopicCatalog.topic(topicId) ?: return KfPermission.NONE
        return when {
            identity in topic.writers && identity in topic.readers -> KfPermission.WRITE
            identity in topic.writers -> KfPermission.WRITE
            identity in topic.readers -> KfPermission.READ
            else -> KfPermission.NONE
        }
    }

    fun canWrite(identity: String, topicId: String): Boolean =
        permission(identity, topicId).let { it == KfPermission.WRITE || it == KfPermission.ADMIN }

    fun canRead(identity: String, topicId: String): Boolean =
        permission(identity, topicId).let { it == KfPermission.READ || it == KfPermission.ADMIN }
}

enum class KfRisk {
    L0,
    L1,
    L2,
    L3,
}

/**
 * Authoritative typed operation catalogue. Every entry is an allowlisted
 * operation with a stable ID, required identity, risk level and contract.
 * There is no generic execution variant.
 */
data class KfActionSpec(
    val id: String,
    val title: String,
    val description: String,
    val target: String,
    val requiredIdentity: String,
    val risk: KfRisk,
    val mutation: Boolean,
    val stepUp: Boolean,
    val ownerConfirmation: Boolean,
    val requestSchema: String,
    val responseSchema: String,
    val timeoutMs: Long,
    val audit: Boolean,
    val args: List<String>,
)

object KfActionCatalog {
    const val COMMAND_TARGET = "proxmox-gateway"
    const val COMMAND_SCHEMA = "kf.command.v1"
    /** Accepted only so an existing producer is not broken by the rename. */
    const val LEGACY_COMMAND_SCHEMA = "rplab.command.v1"
    const val COMMAND_RESULT_SCHEMA = "kf.command.result.v1"
    const val COMMAND_REPLY_TOPIC = "kf-command-result"
    const val LEGACY_COMMAND_REPLY_TOPIC = "rplab-command-results"

    private fun read(
        id: String,
        title: String,
        description: String,
        args: List<String> = emptyList(),
    ) = KfActionSpec(
        id = id,
        title = title,
        description = description,
        target = COMMAND_TARGET,
        requiredIdentity = "kf_gateway",
        risk = KfRisk.L0,
        mutation = false,
        stepUp = false,
        ownerConfirmation = false,
        requestSchema = COMMAND_SCHEMA,
        responseSchema = COMMAND_RESULT_SCHEMA,
        timeoutMs = 30_000,
        audit = true,
        args = args,
    )

    private fun lifecycle(
        id: String,
        title: String,
        description: String,
        risk: KfRisk,
    ) = KfActionSpec(
        id = id,
        title = title,
        description = description,
        target = COMMAND_TARGET,
        requiredIdentity = "kf_gateway",
        risk = risk,
        mutation = true,
        stepUp = true,
        ownerConfirmation = risk == KfRisk.L2,
        requestSchema = COMMAND_SCHEMA,
        responseSchema = COMMAND_RESULT_SCHEMA,
        timeoutMs = 30_000,
        audit = true,
        args = listOf("node", "vmid"),
    )

    val actions: List<KfActionSpec> = listOf(
        read("host.health", "Host health", "Read-only Proxmox version and gateway health."),
        read("node.list", "Node list", "Read-only Proxmox node inventory."),
        read("vm.list", "VM list", "Read-only VM inventory for one node.", listOf("node")),
        read("container.list", "Container list", "Read-only LXC inventory for one node.", listOf("node")),
        read("vm.status", "VM status", "Read-only current status for one VM.", listOf("node", "vmid")),
        read("container.status", "Container status", "Read-only current status for one LXC container.", listOf("node", "vmid")),
        read("storage.summary", "Storage summary", "Read-only storage inventory for one node.", listOf("node")),
        read("task.history", "Task history", "Read-only recent Proxmox tasks for one node.", listOf("node")),
        lifecycle("vm.start", "Start VM", "Start one VM. Reversible lifecycle operation.", KfRisk.L1),
        lifecycle("vm.stop", "Stop VM", "Forcibly stop one VM. Requires exact confirmation.", KfRisk.L2),
        lifecycle("vm.restart", "Restart VM", "Reboot one VM. Requires exact confirmation.", KfRisk.L2),
        lifecycle("container.start", "Start container", "Start one LXC container. Reversible lifecycle operation.", KfRisk.L1),
        lifecycle("container.stop", "Stop container", "Forcibly stop one LXC container. Requires exact confirmation.", KfRisk.L2),
        lifecycle("container.restart", "Restart container", "Reboot one LXC container. Requires exact confirmation.", KfRisk.L2),
    )

    private val byId = actions.associateBy { it.id }

    fun action(id: String): KfActionSpec? = byId[id]

    fun actionsFor(identity: String): List<KfActionSpec> =
        actions.filter { it.requiredIdentity == identity || KfIdentityCatalog.identity(identity)?.ntfyAdmin == true }

    val mutations: List<KfActionSpec> = actions.filter { it.mutation }
}
