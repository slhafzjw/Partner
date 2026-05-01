package work.slhaf.partner.external.onebot.v11

import com.alibaba.fastjson2.JSONObject
import org.java_websocket.WebSocket
import java.util.*
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
object OneBotV11ActionExecutor {

    private lateinit var activeConnection: AtomicReference<WebSocket>

    @JvmStatic
    fun bindConnectionReference(reference: AtomicReference<WebSocket>) {
        activeConnection = reference
    }

    @JvmStatic
    fun sendMessage(target: String, message: String): Boolean {
        val action = buildSendMessageAction(target, message) ?: return false
        activeConnection.load().send(action.toJSONString())
        return true
    }

    private fun buildSendMessageAction(target: String, message: String): JSONObject? {
        val segments = target.split(":")
        if (segments.size < 3 || segments[0] != "onebot") {
            return null
        }

        val params = JSONObject()
        val actionName = when (segments[1]) {
            "private" -> {
                params["user_id"] = segments[2].toLongOrNull() ?: return null
                "send_private_msg"
            }

            "group" -> {
                params["group_id"] = segments[2].toLongOrNull() ?: return null
                "send_group_msg"
            }

            else -> return null
        }
        params["message"] = message

        val action = JSONObject()
        action["action"] = actionName
        action["params"] = params
        action["echo"] = UUID.randomUUID().toString()
        return action
    }
}
