package work.slhaf.partner.module.modules.action.planner.confirmer;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerInput;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerResult;

@AgentSubModule
public class ActionConfirmer extends AgentRunningSubModule<ConfirmerInput, ConfirmerResult> implements ActivateModel {
    @Override
    public ConfirmerResult execute(ConfirmerInput data) {
        //TODO 完善确认逻辑
        return null;
    }

    @Override
    public String modelKey() {
        return "action-confirmer";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
