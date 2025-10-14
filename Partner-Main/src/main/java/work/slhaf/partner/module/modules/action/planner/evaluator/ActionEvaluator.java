package work.slhaf.partner.module.modules.action.planner.evaluator;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorResult;

import java.util.List;

@AgentSubModule
public class ActionEvaluator extends AgentRunningSubModule<EvaluatorInput, List<EvaluatorResult>> implements ActivateModel {

    @Override
    public List<EvaluatorResult> execute(EvaluatorInput data) {

        return null;
    }

    @Override
    public String modelKey() {
        return "action_evaluator";
    }

    @Override
    public boolean withBasicPrompt() {
        return true;
    }
}
