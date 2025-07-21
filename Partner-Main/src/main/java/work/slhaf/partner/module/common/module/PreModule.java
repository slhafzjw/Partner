package work.slhaf.partner.module.common.module;

import work.slhaf.partner.core.interaction.data.context.InteractionContext;
import work.slhaf.partner.core.interaction.module.InteractionFlow;
import work.slhaf.partner.module.common.entity.AppendPromptData;

import java.util.HashMap;

/**
 * 前置模块抽象类
 */
public abstract class PreModule extends InteractionModule {
    protected void setAppendedPrompt(InteractionContext context) {
        AppendPromptData data = new AppendPromptData();
        data.setModuleName(moduleName());
        HashMap<String, String> map = getPromptDataMap(context.getUserId());
        data.setAppendedPrompt(map);
        context.getModuleContext().getAppendedPrompt().add(data);
    }

    protected void setActiveModule(InteractionContext context) {
        context.getCoreContext().addActiveModule(moduleName());
    }

    protected abstract HashMap<String, String> getPromptDataMap(String userId);

    protected abstract String moduleName();
}
