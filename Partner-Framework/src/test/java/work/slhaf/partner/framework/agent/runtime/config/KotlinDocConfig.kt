package work.slhaf.partner.framework.agent.runtime.config

import work.slhaf.partner.framework.agent.config.Config
import work.slhaf.partner.framework.agent.config.ConfigDoc

class KotlinDocConfig : Config() {
    @ConfigDoc(description = "WebSocket 监听端口", example = "29600")
    var port: Int = 29600

    @ConfigDoc(description = "心跳间隔", unit = "ms", constraint = "> 0", example = "10000")
    var heartbeatInterval: Int? = 10000

    @ConfigDoc(description = "标签")
    var tag: String? = null
}
