package com.rplab.kaminarinfty.admin

/**
 * Typed Android administration boundary (owner decision P-03, Option B).
 *
 * The Android app keeps the full administration experience, but privileged
 * operations never execute locally. They are submitted to the authenticated
 * Kaminari Gateway, which owns the server-side credentials and authorization.
 *
 * Phase 1 ships only this contract and honest UI state. No implementation of
 * [GatewayAdminService] exists in the production app yet, and no ntfy admin
 * credential is available to any Android code path.
 */
enum class AdminSection(val label: String) {
    SERVER("ntfy server"),
    USERS("Users and access"),
    TOPICS("Topics"),
    ACL("ACL and permissions"),
    DIAGNOSTICS("Server diagnostics"),
    PVE("PVE host"),
    GUESTS("CT and VM"),
    STORAGE("Storage and backup"),
    DOCKER("Docker and services"),
    NETWORK("Network and security"),
    PIPELINES("Pipelines"),
    AUDIT("Command and audit history"),
}

enum class AdminCapabilityState(val label: String) {
    /** Contract defined; no backend operation is authorized yet. */
    PLANNED("Planned"),

    /** Requires the deployed, authenticated gateway. */
    GATEWAY_REQUIRED("Gateway required"),

    /** Not implemented and not simulated. */
    UNAVAILABLE("Unavailable"),
}

data class AdminCapability(
    val id: String,
    val title: String,
    val description: String,
    val section: AdminSection,
    val state: AdminCapabilityState,
    /** Stable gateway operation ID; null when the operation is not yet defined. */
    val operationId: String? = null,
    /** True when the operation changes server or guest state. */
    val mutation: Boolean = false,
)

data class AdminOperationRequest(
    val operationId: String,
    val target: String,
    val args: Map<String, String> = emptyMap(),
    val correlationId: String,
    val approvalToken: String? = null,
)

sealed interface AdminOperationResult {
    data class Completed(
        val summary: String,
        val auditId: String? = null,
    ) : AdminOperationResult

    data class Rejected(val reason: String) : AdminOperationResult

    data class Unavailable(val reason: String) : AdminOperationResult

    data class Failed(val reason: String) : AdminOperationResult
}

/**
 * Gateway-backed administration boundary. Implementations must never hold an
 * ntfy superadmin credential, and the interface deliberately has no method for
 * arbitrary HTTP, shell, SSH or PVE proxying.
 */
interface GatewayAdminService {
    suspend fun execute(request: AdminOperationRequest): AdminOperationResult
}

/** Production default until a gateway is deployed and configured. */
object UnavailableGatewayAdminService : GatewayAdminService {
    override suspend fun execute(request: AdminOperationRequest): AdminOperationResult =
        AdminOperationResult.Unavailable(
            "Administration requires the deployed Kaminari Gateway; no privileged operation ran"
        )
}

object AdminCatalog {
    val capabilities: List<AdminCapability> = listOf(
        AdminCapability(
            id = "ntfy.server.status",
            title = "Server status and diagnostics",
            description = "Health, version, auth mode and publish diagnostics through the gateway.",
            section = AdminSection.SERVER,
            state = AdminCapabilityState.GATEWAY_REQUIRED,
            operationId = "admin.ntfy.status",
        ),
        AdminCapability(
            id = "ntfy.users.list",
            title = "List users and roles",
            description = "Read users, roles and token labels without any secret value.",
            section = AdminSection.USERS,
            state = AdminCapabilityState.GATEWAY_REQUIRED,
            operationId = "admin.ntfy.users.list",
        ),
        AdminCapability(
            id = "ntfy.users.manage",
            title = "Create, disable or revoke a user",
            description = "Mutation. Requires server-side identity authority, step-up and audit.",
            section = AdminSection.USERS,
            state = AdminCapabilityState.PLANNED,
            operationId = "admin.ntfy.users.manage",
            mutation = true,
        ),
        AdminCapability(
            id = "ntfy.diagnostics",
            title = "Connection and publish diagnostics",
            description = "Bounded, sanitized diagnostics from the gateway; no secrets or raw logs.",
            section = AdminSection.DIAGNOSTICS,
            state = AdminCapabilityState.GATEWAY_REQUIRED,
            operationId = "admin.ntfy.diagnostics",
        ),
        AdminCapability(
            id = "ntfy.topics.list",
            title = "List topics",
            description = "Approved catalogue and observed activity.",
            section = AdminSection.TOPICS,
            state = AdminCapabilityState.PLANNED,
            operationId = "admin.ntfy.topics.list",
        ),
        AdminCapability(
            id = "ntfy.acl.manage",
            title = "Manage ACL grants",
            description = "Mutation. Never available offline and never executed by the APK.",
            section = AdminSection.ACL,
            state = AdminCapabilityState.PLANNED,
            operationId = "admin.ntfy.acl.manage",
            mutation = true,
        ),
        AdminCapability(
            id = "pve.node.list",
            title = "Node inventory",
            description = "Read-only node list and status through the gateway.",
            section = AdminSection.PVE,
            state = AdminCapabilityState.GATEWAY_REQUIRED,
            operationId = "node.list",
        ),
        AdminCapability(
            id = "pve.guest.list",
            title = "CT and VM inventory",
            description = "Read-only guest inventory through the gateway.",
            section = AdminSection.GUESTS,
            state = AdminCapabilityState.GATEWAY_REQUIRED,
            operationId = "container.list",
        ),
        AdminCapability(
            id = "pve.storage.list",
            title = "Storage inventory",
            description = "Read-only storage and capacity summary.",
            section = AdminSection.STORAGE,
            state = AdminCapabilityState.GATEWAY_REQUIRED,
            operationId = "storage.summary",
        ),
        AdminCapability(
            id = "docker.list",
            title = "Docker services",
            description = "Container inventory and health read through a future gateway operation.",
            section = AdminSection.DOCKER,
            state = AdminCapabilityState.PLANNED,
        ),
        AdminCapability(
            id = "network.read",
            title = "Network and security posture",
            description = "Firewall, tunnel, DNS and CrowdSec read models.",
            section = AdminSection.NETWORK,
            state = AdminCapabilityState.PLANNED,
        ),
        AdminCapability(
            id = "pipelines.read",
            title = "Music, news and sticker pipeline state",
            description = "Read-only run and report views from the pipeline topics.",
            section = AdminSection.PIPELINES,
            state = AdminCapabilityState.PLANNED,
        ),
        AdminCapability(
            id = "audit.list",
            title = "Command and audit history",
            description = "Correlated results and durable audit references.",
            section = AdminSection.AUDIT,
            state = AdminCapabilityState.GATEWAY_REQUIRED,
        ),
    )

    fun bySection(section: AdminSection): List<AdminCapability> =
        capabilities.filter { it.section == section }

    fun capability(id: String): AdminCapability? = capabilities.firstOrNull { it.id == id }

    val mutations: List<AdminCapability> = capabilities.filter { it.mutation }
}
