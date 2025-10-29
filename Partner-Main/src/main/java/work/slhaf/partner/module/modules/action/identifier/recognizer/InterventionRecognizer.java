package work.slhaf.partner.module.modules.action.identifier.recognizer;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.identifier.recognizer.entity.InterventionResult;

@AgentSubModule
public class InterventionRecognizer extends AgentRunningSubModule<String, InterventionResult> implements ActivateModel {

    @Override
    public InterventionResult execute(String data) {
        //使用LLM进行快速意图识别

        return null;
    }

    @Override
    public String modelKey() {
        return "intervention_recognizer";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
