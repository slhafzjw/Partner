package work.slhaf.agent.module.common;

import work.slhaf.agent.core.interaction.data.context.InteractionContext;

/**
 * 用于在前置模块设置追加提示词
 */
public interface PreModuleActions {
    void setAppendedPrompt(InteractionContext context);
    void setActiveModule(InteractionContext context);
    String getModuleName();
}
