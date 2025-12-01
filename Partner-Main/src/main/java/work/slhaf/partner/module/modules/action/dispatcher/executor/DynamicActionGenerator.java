package work.slhaf.partner.module.modules.action.dispatcher.executor;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.GeneratorInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.GeneratorResult;

/**
 * 负责依据输入内容生成可执行的动态行动单元，并选择是否持久化至 SandboxRunner 容器内
 */
@AgentSubModule
public class DynamicActionGenerator extends AgentRunningSubModule<GeneratorInput, GeneratorResult> implements ActivateModel {

    @Override
    public GeneratorResult execute(GeneratorInput data) {
        return null;
    }

    @Override
    public String modelKey() {
        return "";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
