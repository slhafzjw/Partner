package work.slhaf.partner.framework.agent.interaction

import com.alibaba.fastjson2.JSONObject
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.interaction.data.InteractionEvent

interface ResponseChannel {

    val channelName: String

    fun response(event: InteractionEvent)

    fun register() {
        AgentRuntime.registerResponseChannel(channelName, this)
    }
}

object LogChannel : ResponseChannel {

    private val log = LoggerFactory.getLogger(LogChannel::class.java)

    override val channelName: String
        get() = "log_channel"

    init {
        register()
    }

    override fun response(event: InteractionEvent) {
        log.info(JSONObject.toJSONString(event))
    }

}