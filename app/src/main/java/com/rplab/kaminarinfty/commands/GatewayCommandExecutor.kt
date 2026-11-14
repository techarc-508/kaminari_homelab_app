package com.rplab.kaminarinfty.commands

import com.rplab.kaminarinfty.model.CommandResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/** Approved gateway target for every command, matching the shared contract. */
const val GATEWAY_COMMAND_TARGET = "proxmox-gateway"

/**
 * Real executor for the deployed Kaminari Gateway.
 *
 * The endpoint and token are resolved from settings on every call; nothing is
 * hardcoded and no credential is ever written to a log. TLS always uses the
 * platform trust chain. Mutations additionally require a live RFC 6238 code:
 * the executor requests a server-side challenge bound to this exact command and
 * submits the code with it, so a self-asserted approval can never succeed.
 */
class GatewayCommandExecutor(
    private val connections: GatewayConnectionProvider,
    private val client: OkHttpClient = defaultGatewayClient(),
    private val maxResponseBytes: Long = 1_048_576,
) : CommandExecutor {

    override val target: String = GATEWAY_COMMAND_TARGET

    override suspend fun execute(
        envelope: CommandEnvelope,
        confirmedAt: Long?,
        approvalCode: String?,
    ): CommandExecutionResult = withContext(Dispatchers.IO) {
        val connection = connections.current()
            ?: return@withContext failure(
                "Gateway is not configured. Set the gateway URL and token in Command Centre."
            )
        val baseUrl = GatewayUrlPolicy.normalize(connection.baseUrl)
            ?: return@withContext failure("Gateway URL is invalid")
        if (connection.requireHttps && !baseUrl.startsWith("https://")) {
            return@withContext failure("Gateway requires an https:// URL")
        }
        if (connection.token.isBlank()) {
            return@withContext failure("Gateway token is not configured")
        }

        val approval = if (envelope.requiresConfirmation) {
            val code = approvalCode?.trim().orEmpty()
            if (!TOTP_CODE.matches(code)) {
                return@withContext failure(
                    "Enter the current 6-digit authenticator code to approve this operation"
                )
            }
            when (val challenge = requestChallenge(baseUrl, connection.token, envelope)) {
                is ChallengeOutcome.Failed -> return@withContext failure(challenge.reason)
                is ChallengeOutcome.Issued -> StepUpApproval(challenge.challengeId, code)
            }
        } else {
            null
        }

        val response = try {
            postCommand(baseUrl, connection.token, envelope, approval)
        } catch (ioe: IOException) {
            return@withContext failure("Gateway request failed: ${ioe.message ?: "I/O error"}")
        } catch (ex: Exception) {
            return@withContext failure("Gateway request failed: ${ex.message ?: "unexpected error"}")
        }
        parseExecution(response)
    }

    private fun requestChallenge(
        baseUrl: String,
        token: String,
        envelope: CommandEnvelope,
    ): ChallengeOutcome {
        val payload = JSONObject()
            .put("command_id", envelope.commandId)
            .put("action", envelope.action)
            .put("target", envelope.target)
            .put("args", JSONObject(envelope.args as Map<*, *>))
        val request = Request.Builder()
            .url("$baseUrl/v1/stepup/challenge")
            .header("Authorization", "Bearer ${sanitize(token)}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = readBounded(response) ?: return ChallengeOutcome.Failed(
                    "Gateway response was too large"
                )
                if (!response.isSuccessful) {
                    return ChallengeOutcome.Failed(
                        "Approval request refused (HTTP ${response.code}): ${safeMessage(raw)}"
                    )
                }
                val challengeId = JSONObject(raw).optString("challenge_id", "")
                if (challengeId.isBlank()) {
                    return ChallengeOutcome.Failed("Gateway did not return an approval challenge")
                }
                ChallengeOutcome.Issued(challengeId)
            }
        } catch (ioe: IOException) {
            ChallengeOutcome.Failed("Approval request failed: ${ioe.message ?: "I/O error"}")
        }
    }

    private fun postCommand(
        baseUrl: String,
        token: String,
        envelope: CommandEnvelope,
        approval: StepUpApproval?,
    ): HttpResponse {
        val payload = JSONObject().put("envelope", JSONObject(envelope.toJson()))
        approval?.let { payload.put("approval", JSONObject().put("challenge_id", it.challengeId).put("code", it.code)) }
        val request = Request.Builder()
            .url("$baseUrl/v1/commands/execute")
            .header("Authorization", "Bearer ${sanitize(token)}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = readBounded(response) ?: return HttpResponse(false, response.code, "")
            return HttpResponse(response.isSuccessful, response.code, raw)
        }
    }

    private fun parseExecution(response: HttpResponse): CommandExecutionResult {
        if (response.body.isBlank()) {
            return failure("Gateway returned HTTP ${response.code} with no body")
        }
        val json = runCatching { JSONObject(response.body) }.getOrNull()
            ?: return failure("Gateway returned a response that is not JSON")
        if (!response.ok) {
            val detail = json.optString("error").ifBlank { response.body.take(200) }
            val code = json.optString("code")
            val suffix = if (code.isBlank()) "" else " [$code]"
            return failure("Gateway refused the request (HTTP ${response.code})$suffix: $detail")
        }
        val result = json.optJSONObject("result")
            ?: return failure("Gateway response did not include a command result")
        val detail = result.optJSONObject("detail")?.toString()
            ?: result.optString("detail", "{}")
        return CommandExecutionResult(
            result = CommandResult(
                mock = result.optBoolean("mock", false),
                ok = result.optBoolean("ok", false),
                summary = result.optString("summary", "Gateway command finished"),
                detailJson = detail,
            ),
            expired = json.optBoolean("expired", false),
        )
    }

    private fun readBounded(response: Response): String? {
        val declared = response.body?.contentLength() ?: -1L
        if (declared > maxResponseBytes) return null
        val raw = response.body?.string().orEmpty()
        if (raw.length.toLong() > maxResponseBytes) return null
        return raw
    }

    private fun failure(summary: String): CommandExecutionResult =
        CommandExecutionResult(
            result = CommandResult(
                mock = false,
                ok = false,
                summary = summary,
                detailJson = "{}",
            ),
            expired = false,
        )

    private fun sanitize(value: String): String =
        value.trim().replace("\r", "").replace("\n", "")

    private fun safeMessage(raw: String): String =
        if (raw.isBlank()) "no response body" else raw.take(200)

    private data class StepUpApproval(val challengeId: String, val code: String)

    private sealed interface ChallengeOutcome {
        data class Issued(val challengeId: String) : ChallengeOutcome
        data class Failed(val reason: String) : ChallengeOutcome
    }

    private data class HttpResponse(val ok: Boolean, val code: Int, val body: String)

    private companion object {
        val JSON = "application/json".toMediaType()
        val TOTP_CODE = Regex("^[0-9]{6}$")
    }
}

/** Result of a connection test; every field is real data from the gateway. */
data class GatewayHealth(
    val reachable: Boolean,
    val message: String,
    val durableState: Boolean = false,
    val mutationsEnabled: Boolean = false,
    val stepUpEnrolled: Boolean = false,
)

/**
 * Calls `GET /readyz` and reports exactly what the gateway says. A rejected
 * token is reported as an authentication failure, never as "connected".
 */
class GatewayProbe(
    private val client: OkHttpClient = defaultGatewayClient(),
    private val maxResponseBytes: Long = 65_536,
) {
    suspend fun probe(connection: GatewayConnection?): GatewayHealth = withContext(Dispatchers.IO) {
        if (connection == null) {
            return@withContext GatewayHealth(false, "Gateway is not configured")
        }
        val baseUrl = GatewayUrlPolicy.normalize(connection.baseUrl)
            ?: return@withContext GatewayHealth(false, "Gateway URL is invalid")
        if (connection.requireHttps && !baseUrl.startsWith("https://")) {
            return@withContext GatewayHealth(false, "Gateway requires an https:// URL")
        }
        if (connection.token.isBlank()) {
            return@withContext GatewayHealth(false, "Gateway token is not configured")
        }
        val request = Request.Builder()
            .url("$baseUrl/readyz")
            .header("Authorization", "Bearer ${connection.token.trim()}")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (raw.length > maxResponseBytes) {
                    return@withContext GatewayHealth(false, "Gateway response was too large")
                }
                if (response.code == 401) {
                    return@withContext GatewayHealth(false, "Gateway rejected the token (401)")
                }
                if (!response.isSuccessful) {
                    return@withContext GatewayHealth(
                        false,
                        "Gateway not ready (HTTP ${response.code})",
                    )
                }
                val json = runCatching { JSONObject(raw) }.getOrNull()
                    ?: return@withContext GatewayHealth(false, "Gateway returned a non-JSON response")
                val durable = json.optBoolean("durable_state", false)
                val mutations = json.optBoolean("mutations_enabled", false)
                val enrolled = json.optBoolean("step_up_enrolled", false)
                GatewayHealth(
                    reachable = true,
                    message = if (json.optString("status") == "ready") {
                        "Connected — durable state, ${if (enrolled) "step-up enrolled" else "step-up not enrolled"}"
                    } else {
                        "Gateway answered: ${json.optString("status", "unknown")}"
                    },
                    durableState = durable,
                    mutationsEnabled = mutations,
                    stepUpEnrolled = enrolled,
                )
            }
        } catch (ioe: IOException) {
            GatewayHealth(false, "Gateway unreachable: ${ioe.message ?: "I/O error"}")
        } catch (ex: Exception) {
            GatewayHealth(false, "Gateway probe failed: ${ex.message ?: "unexpected error"}")
        }
    }
}

private fun defaultGatewayClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .build()
