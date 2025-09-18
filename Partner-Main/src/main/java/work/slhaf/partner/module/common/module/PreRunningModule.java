package work.slhaf.partner.module.common.module;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.module.common.entity.AppendPromptData;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.io.IOException;
import java.util.HashMap;

/**
 * 前置模块抽象类
 */
@Slf4j
public abstract class PreRunningModule extends AgentRunningModule<PartnerRunningFlowContext> {
    private void setAppendedPrompt(PartnerRunningFlowContext context) {
        AppendPromptData data = new AppendPromptData();
        data.setModuleName(moduleName());
        HashMap<String, String> map = getPromptDataMap(context.getUserId());
        data.setAppendedPrompt(map);
        context.setAppendedPrompt(data);
    }

    private void setActiveModule(PartnerRunningFlowContext context) {
        context.getCoreContext().addActiveModule(moduleName());
    }

    protected abstract HashMap<String, String> getPromptDataMap(String userId);

    protected abstract String moduleName();

    @Override
    public final void execute(PartnerRunningFlowContext context) throws IOException, ClassNotFoundException {
        log.debug("[{}] 模块执行开始...", this.getClass().getAnnotation(AgentModule.class).name());
        doExecute(context); // 子类实现差异化逻辑
        setAppendedPrompt(context); // 通用逻辑
        setActiveModule(context);   // 通用逻辑
        log.debug("[{}] 模块执行结束...", this.getClass().getAnnotation(AgentModule.class).name());
    }

    protected abstract void doExecute(PartnerRunningFlowContext context) throws IOException, ClassNotFoundException;


}
