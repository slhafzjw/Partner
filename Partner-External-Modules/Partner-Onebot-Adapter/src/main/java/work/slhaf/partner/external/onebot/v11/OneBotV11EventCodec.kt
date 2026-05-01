package work.slhaf.partner.external.onebot.v11

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.framework.agent.interaction.data.InputData

object OneBotV11EventCodec {

    @JvmStatic
    fun parse(json: JSONObject): OneBotV11Event? {
        return when (json.getString("post_type")) {
            "message" -> parseMessage(json)
            "meta_event" -> parseMetaEvent(json)
            else -> null
        }
    }

    @JvmStatic
    fun toInputData(event: OneBotV11MessageEvent): InputData? {
        val target = event.routeTarget() ?: return null
        val content = extractText(event).takeIf { it.isNotBlank() } ?: return null
        val inputData = InputData(target, content)
        inputData.addMeta("message_type", event.messageType.displayName)
        event.groupId?.let { inputData.addMeta("group_id", it.toString()) }
        return inputData
    }

    private fun extractText(event: OneBotV11MessageEvent): String {
        event.rawMessage?.takeIf { it.isNotBlank() }?.let { return it }
        val message = event.message ?: return ""
        return when (message) {
            is String -> message
            is JSONArray -> message.asSequence()
                .filterIsInstance<JSONObject>()
                .filter { it.getString("type") == "text" }
                .mapNotNull { it.getJSONObject("data")?.getString("text") }
                .joinToString("")

            else -> message.toString()
        }
    }

    private fun parseMessage(json: JSONObject): OneBotV11MessageEvent {
        return OneBotV11MessageEvent(
            messageType = parseMessageType(json.getString("message_type")),
            userId = json.getLongValue("user_id"),
            groupId = json.getLong("group_id"),
            message = json["message"],
            rawMessage = json.getString("raw_message")
        )
    }

    private fun parseMetaEvent(json: JSONObject): OneBotV11MetaEvent {
        return OneBotV11MetaEvent(
            metaEventType = json.getString("meta_event_type") ?: ""
        )
    }

    private fun parseMessageType(value: String?): OneBotV11MessageType {
        return when (value) {
            "private" -> OneBotV11MessageType.PRIVATE
            "group" -> OneBotV11MessageType.GROUP
            else -> OneBotV11MessageType.UNKNOWN
        }
    }
}
