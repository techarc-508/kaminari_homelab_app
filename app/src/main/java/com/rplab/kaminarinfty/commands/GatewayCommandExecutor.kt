package com.rplab.kaminarinfty.commands

import com.rplab.kaminarinfty.model.CommandResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Real executor boundary for the separate Proxmox gateway.
 *
 * It never shells out, never proxies arbitrary URLs and never retries an
 * ambiguous mutation. TLS validation uses the platform trust chain.
 */
class GatewayCommandExecutor(
    private val baseUrl: String,
    private val gatewayToken: String,
    private val requireHttps: Boolean = true,
    private val connectTimeoutSeconds: Long = 10,
    private val readTimeoutSeconds: Long = 20,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build(),
) : CommandExecutor {

    override val target: String = "proxmox-gateway"

    override suspend fun execute(
        envelope: CommandEnvelope,
        confirmedAt: Long?,
    ): CommandExecutionResult {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) {
            return failure("Gateway URL is not configured")
        }
        if (requireHttps && !normalizedBase.startsWith("https://")) {
            return failure("Gateway requires an https:// URL")
        }
        if (gatewayToken.isBlank()) {
            return failure("Gateway token is not configured")
        }

        val approvalToken = if (envelope.requiresConfirmation && confirmedAt != null) {
            envelope.nonce
        } else {
            null
        }

        val bodyJson = JSONObject()
            .put("envelope", JSONObject(envelope.toJson()))
        if (approvalToken != null) {
            bodyJson.put("approval_token", approvalToken)
        }

        val request = Request.Builder()
            .url("$normalizedBase/v1/commands/execute")
            .header("Authorization", "Bearer ${sanitize(gatewayToken)}")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return failure("Gateway returned HTTP ${response.code}: ${safeMessage(raw)}")
                }
                val json = JSONObject(raw)
                val result = json.getJSONObject("result")
                val detail = result.optJSONObject("detail")?.toString()
                    ?: result.optString("detail", "{}")
                CommandExecutionResult(
                    result = CommandResult(
                        mock = false,
                        ok = result.optBoolean("ok", false),
                        summary = result.optString("summary", "Gateway command finished"),
                        detailJson = detail,
                    ),
                    expired = json.optBoolean("expired", false),
                )
            }
        } catch (ioe: IOException) {
            failure("Gateway request failed: ${ioe.message ?: "I/O error"}")
        } catch (ex: Exception) {
            failure("Gateway request failed: ${ex.message ?: "unexpected error"}")
        }
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

    private fun safeMessage(raw: String): String {
        if (raw.isBlank()) return "no response body"
        return raw.take(200)
    }
}
