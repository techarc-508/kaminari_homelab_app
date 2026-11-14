package com.rplab.kaminarinfty.data.api

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thin client for documented ntfy administration endpoints.
 *
 * It requires an admin-capable credential and never stores or logs it. The
 * caller is responsible for supplying and protecting that credential.
 */
class NtfyAdminClient(
    private val baseUrl: String,
    private val requireHttps: Boolean = true,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build(),
) {

    data class NtfyUser(
        val username: String,
        val role: String,
        val grants: List<Pair<String, String>> = emptyList(),
    )

    fun listUsers(username: String, secret: String): Result<List<NtfyUser>> =
        request("GET", "/v1/users", username, secret).map { body ->
            val array = JSONArray(body)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val grants = buildList<Pair<String, String>> {
                        val grantArray = item.optJSONArray("grants")
                        if (grantArray != null) {
                            for (j in 0 until grantArray.length()) {
                                val grant = grantArray.getJSONObject(j)
                                add(grant.optString("topic") to grant.optString("permission"))
                            }
                        }
                    }
                    add(NtfyUser(item.optString("username"), item.optString("role"), grants))
                }
            }
        }

    fun createUser(username: String, password: String, adminUsername: String, adminSecret: String): Result<Boolean> =
        request(
            "PUT",
            "/v1/users",
            adminUsername,
            adminSecret,
            body = JSONObject().put("username", username).put("password", password).toString(),
        ).map { true }

    fun deleteUser(username: String, adminUsername: String, adminSecret: String): Result<Boolean> =
        request(
            "DELETE",
            "/v1/users",
            adminUsername,
            adminSecret,
            body = JSONObject().put("username", username).toString(),
        ).map { true }

    fun grantAccess(
        username: String,
        topic: String,
        permission: String,
        adminUsername: String,
        adminSecret: String,
    ): Result<Boolean> =
        request(
            "PUT",
            "/v1/access",
            adminUsername,
            adminSecret,
            body = JSONObject()
                .put("username", username)
                .put("topic", topic)
                .put("permission", permission)
                .toString(),
        ).map { true }

    private fun request(
        method: String,
        path: String,
        username: String,
        secret: String,
        body: String? = null,
    ): Result<String> {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isEmpty()) return Result.failure(IllegalArgumentException("Server URL is required"))
        if (requireHttps && !normalized.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("HTTPS is required"))
        }
        if (username.isBlank() || secret.isBlank()) {
            return Result.failure(IllegalArgumentException("Admin credentials are required"))
        }
        val url = "$normalized$path"
        val builder = Request.Builder().url(url)
            .header("Authorization", Credentials.basic(username, secret))
        val media = "application/json".toMediaType()
        if (body != null) {
            when (method) {
                "PUT" -> builder.put(body.toRequestBody(media))
                "POST" -> builder.post(body.toRequestBody(media))
                "DELETE" -> builder.delete(body.toRequestBody(media))
                else -> builder.method(method, body.toRequestBody(media))
            }
        } else {
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                else -> builder.method(method, null)
            }
        }
        val request = builder.build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.failure(IOException("HTTP ${response.code}: ${raw.take(200)}"))
                } else {
                    Result.success(raw)
                }
            }
        } catch (ioe: IOException) {
            Result.failure(ioe)
        }
    }
}
