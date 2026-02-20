package work.slhaf.partner.module.common.module;

import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentModule;
import work.slhaf.partner.module.common.entity.AppendPromptData;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.Map;

/**
 * 前置模块抽象类
 */
public abstract class PreRunningAbstractAgentModuleAbstract extends AbstractAgentModule.Running<PartnerRunningFlowContext> {
    private synchronized void setAppendedPrompt(PartnerRunningFlowContext context) {
        AppendPromptData data = new AppendPromptData();
        data.setModuleName(moduleName());
        Map<String, String> map = getPromptDataMap(context);
        data.setAppendedPrompt(map);
        context.setAppendedPrompt(data);
    }

    private synchronized void setActiveModule(PartnerRunningFlowContext context) {
        context.getCoreContext().addActiveModule(moduleName());
    }

    protected abstract Map<String, String> getPromptDataMap(PartnerRunningFlowContext context);

    /**
     * 用于在CoreModule接收到的模块Prompt中标识模块名称
     */
    protected abstract String moduleName();

    @Override
    public final void execute(PartnerRunningFlowContext context) {
        doExecute(context); // 子类实现差异化逻辑
        setAppendedPrompt(context); // 通用逻辑
        setActiveModule(context);   // 通用逻辑
    }

    protected abstract void doExecute(PartnerRunningFlowContext context);
}
