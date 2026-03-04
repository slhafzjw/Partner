package work.slhaf.partner.runtime.interaction.data.context

import com.alibaba.fastjson2.JSONObject
import work.slhaf.partner.api.agent.runtime.interaction.flow.RunningFlowContext
import work.slhaf.partner.module.common.entity.AppendPromptData
import work.slhaf.partner.runtime.interaction.data.context.subcontext.CoreContext
import work.slhaf.partner.runtime.interaction.data.context.subcontext.ModuleContext

class PartnerRunningFlowContext(
    override val source: String,
    override val input: String,
    platform: String,
    nickName: String
) : RunningFlowContext() {

    init {
        putUserInfo("platform", platform)
        putUserInfo("nickname", nickName)
    }

    val moduleContext = ModuleContext()
    val coreContext = CoreContext()
    val coreResponse = JSONObject()

    var finished: Boolean
        get() = moduleContext.isFinished
        set(value) {
            moduleContext.isFinished = value
        }

    fun appendPrompt(appendPromptData: AppendPromptData) = moduleContext.appendPromptData(appendPromptData)

}
