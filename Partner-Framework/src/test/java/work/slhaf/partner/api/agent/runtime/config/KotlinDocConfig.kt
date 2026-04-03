package work.slhaf.partner.api.agent.runtime.config

class KotlinDocConfig : Config() {
    @ConfigDoc(description = "WebSocket 监听端口", example = "29600")
    var port: Int = 29600

    @ConfigDoc(description = "心跳间隔", unit = "ms", constraint = "> 0", example = "10000")
    var heartbeatInterval: Int? = 10000

    @ConfigDoc(description = "标签")
    var tag: String? = null
}
