package work.slhaf.partner.module.modules.action.dispatcher.executor;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.CorrectorInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.CorrectorResult;

/**
 * 负责在单组行动执行后，根据行动意图与结果检查后续行动是否符合目的，必要时直接调整行动链，或发起自对话请求进行干预
 */
@AgentSubModule
public class ActionCorrector extends AgentRunningSubModule<CorrectorInput, CorrectorResult> implements ActivateModel {

    @Override
    public CorrectorResult execute(CorrectorInput data) {
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
