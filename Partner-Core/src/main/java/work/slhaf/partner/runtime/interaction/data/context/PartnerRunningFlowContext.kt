package work.slhaf.partner.runtime.interaction.data.context

import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.api.agent.runtime.interaction.flow.RunningFlowContext
import work.slhaf.partner.module.common.entity.AppendPromptData
import work.slhaf.partner.runtime.interaction.data.context.subcontext.CoreContext
import work.slhaf.partner.runtime.interaction.data.context.subcontext.ModuleContext

class PartnerRunningFlowContext private constructor(
    override val source: String,
    override val input: String,
) : RunningFlowContext() {

    companion object {

        private const val SOURCE_SELF = "self"
        private const val SOURCE_SELF_PLATFORM = "AGENT_INTERNAL"
        private const val SOURCE_SELF_NICKNAME = "PARTNER"

        private object InfoKeys {
            const val PLATFORM = "platform"
            const val NICKNAME = "nickname"
        }

        private object SourceTag {
            private const val AGENT = "[AGENT]"
            private const val USER = "[USER]"

            fun buildUserSource(userId: String): String = "$USER: $userId"
            fun buildAgentSource(): String = "$AGENT: $SOURCE_SELF"
        }

        fun fromUser(
            userId: String,
            input: String,
            platform: String,
            nickName: String
        ) = PartnerRunningFlowContext(SourceTag.buildUserSource(userId), input).apply {
            putUserInfo(InfoKeys.PLATFORM, platform)
            putUserInfo(InfoKeys.NICKNAME, nickName)
        }

        fun fromSelf(input: String) =
            PartnerRunningFlowContext(SourceTag.buildAgentSource(), input).apply {
                putUserInfo(InfoKeys.PLATFORM, SOURCE_SELF_PLATFORM)
                putUserInfo(InfoKeys.NICKNAME, SOURCE_SELF_NICKNAME)
            }
    }

    val moduleContext = ModuleContext()
    val coreContext = CoreContext()
    val coreResponse = JSONObject()

    fun appendPrompt(appendPromptData: AppendPromptData) = moduleContext.appendPromptData(appendPromptData)

}
