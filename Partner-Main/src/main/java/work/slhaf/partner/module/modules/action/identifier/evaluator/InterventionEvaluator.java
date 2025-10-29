package work.slhaf.partner.module.modules.action.identifier.evaluator;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.identifier.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.identifier.evaluator.entity.EvaluatorResult;

@AgentSubModule
public class InterventionEvaluator extends AgentRunningSubModule<EvaluatorInput, EvaluatorResult> implements ActivateModel {

    @Override
    public EvaluatorResult execute(EvaluatorInput data) {
        //基于干预意图、记忆切片、交互上下文、已有行动程序综合评估，尝试选取出合适的行动程序，对目标行动链做出调整

        return null;
    }

    @Override
    public String modelKey() {
        return "intervention_evaluator";
    }

    @Override
    public boolean withBasicPrompt() {
        return true;
    }
}
