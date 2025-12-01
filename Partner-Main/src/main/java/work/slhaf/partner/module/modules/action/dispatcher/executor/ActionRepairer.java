package work.slhaf.partner.module.modules.action.dispatcher.executor;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.RepairerResult;

/**
 * 负责识别行动链的调整，可通过协调 {@link  DynamicActionGenerator} 生成新的行动单元（必要时持久化）、或者依据现有输出结果与已知信息和可选行动单元直接调整行动链、如果当前局部信息无法满足，将发起自对话借助干预模块进行操作或者借助自对话通道向用户发起沟通请求
 */
@AgentSubModule
public class ActionRepairer extends AgentRunningSubModule<RepairerInput, RepairerResult> implements ActivateModel {

    @Override
    public RepairerResult execute(RepairerInput data) {
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
