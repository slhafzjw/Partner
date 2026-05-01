package work.slhaf.partner.external.onebot.v11

sealed class OneBotV11Event {
    abstract val postType: OneBotV11PostType
}

enum class OneBotV11PostType {
    MESSAGE,
    META_EVENT
}

enum class OneBotV11MessageType(val displayName: String) {
    PRIVATE("私聊"),
    GROUP("群聊"),
    UNKNOWN("未知")
}

data class OneBotV11MessageEvent(
    val messageType: OneBotV11MessageType,
    val userId: Long,
    val groupId: Long?,
    val message: Any?,
    val rawMessage: String?
) : OneBotV11Event() {
    override val postType: OneBotV11PostType = OneBotV11PostType.MESSAGE

    fun isPrivateMessage(): Boolean = messageType == OneBotV11MessageType.PRIVATE

    fun isGroupMessage(): Boolean = messageType == OneBotV11MessageType.GROUP

    fun routeTarget(): String? = when {
        isPrivateMessage() -> "onebot:private:$userId"
        isGroupMessage() && groupId != null -> "onebot:group:$groupId:$userId"
        else -> null
    }
}

data class OneBotV11MetaEvent(
    val metaEventType: String
) : OneBotV11Event() {
    override val postType: OneBotV11PostType = OneBotV11PostType.META_EVENT

    fun isHeartbeat(): Boolean = metaEventType == "heartbeat"
}
