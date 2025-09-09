package work.slhaf.partner.module.common.module;

import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;
import work.slhaf.partner.module.common.entity.AppendPromptData;

import java.util.HashMap;

/**
 * 前置模块抽象类
 */
public abstract class PreRunningModule extends AgentRunningModule<PartnerRunningFlowContext> {
    protected void setAppendedPrompt(PartnerRunningFlowContext context) {
        AppendPromptData data = new AppendPromptData();
        data.setModuleName(moduleName());
        HashMap<String, String> map = getPromptDataMap(context.getUserId());
        data.setAppendedPrompt(map);
        context.getModuleContext().getAppendedPrompt().add(data);
    }

    protected void setActiveModule(PartnerRunningFlowContext context) {
        context.getCoreContext().addActiveModule(moduleName());
    }

    protected abstract HashMap<String, String> getPromptDataMap(String userId);

    protected abstract String moduleName();
}
