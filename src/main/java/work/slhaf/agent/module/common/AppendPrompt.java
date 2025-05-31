package work.slhaf.agent.module.common;

import work.slhaf.agent.core.interaction.data.InteractionContext;

/**
 * 用于在前置模块设置追加提示词
 */
public interface AppendPrompt {
    void setAppendedPrompt(InteractionContext context);
}
