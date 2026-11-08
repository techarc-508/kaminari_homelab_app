package com.rplab.kaminarinfty.data.local

import com.rplab.kaminarinfty.model.NftyAction
import com.rplab.kaminarinfty.model.NftyAttachment
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority
import org.json.JSONArray
import org.json.JSONObject

/** Row <-> domain mapping. JSON columns keep the schema stable as ntfy adds fields. */
object MessageMapper {

    fun toEntity(profileId: String, message: NftyMessage): MessageEntity = MessageEntity(
        profileId = profileId,
        messageId = message.id,
        time = message.time,
        topic = message.topic,
        title = message.title,
        body = message.message,
        priority = message.priority.level,
        tagsJson = JSONArray(message.tags).toString(),
        clickUrl = message.click,
        attachmentJson = message.attachment?.let {
            JSONObject()
                .put("name", it.name)
                .put("url", it.url)
                .put("type", it.type)
                .put("size", it.size)
                .toString()
        },
        actionsJson = if (message.actions.isEmpty()) null else JSONArray().apply {
            message.actions.forEach { a ->
                put(
                    JSONObject()
                        .put("id", a.id)
                        .put("action", a.action)
                        .put("label", a.label)
                        .put("url", a.url)
                )
            }
        }.toString(),
        expiry = message.expiry,
        isRead = message.isRead,
        isSaved = message.isSaved,
        isSample = message.isSample,
    )

    fun toDomain(entity: MessageEntity): NftyMessage = NftyMessage(
        id = entity.messageId,
        time = entity.time,
        topic = entity.topic,
        title = entity.title,
        message = entity.body,
        priority = NftyPriority.fromLevel(entity.priority),
        tags = parseTags(entity.tagsJson),
        click = entity.clickUrl,
        attachment = parseAttachment(entity.attachmentJson),
        actions = parseActions(entity.actionsJson),
        expiry = entity.expiry,
        isRead = entity.isRead,
        isSaved = entity.isSaved,
        isSample = entity.isSample,
    )

    private fun parseTags(json: String?): List<String> = try {
        val array = JSONArray(json ?: "[]")
        buildList { for (i in 0 until array.length()) add(array.getString(i)) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseAttachment(json: String?): NftyAttachment? = try {
        json?.let {
            val obj = JSONObject(it)
            NftyAttachment(
                name = obj.optString("name", "attachment"),
                url = obj.optString("url", ""),
                type = if (obj.isNull("type")) null else obj.optString("type"),
                size = if (obj.isNull("size")) null else obj.optLong("size"),
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun parseActions(json: String?): List<NftyAction> = try {
        val array = JSONArray(json ?: "[]")
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    NftyAction(
                        id = obj.optString("id", "act_$i"),
                        action = obj.optString("action", "view"),
                        label = obj.optString("label", "Action"),
                        url = if (obj.isNull("url")) null else obj.optString("url"),
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
