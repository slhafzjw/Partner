package work.slhaf.partner.module.modules.action.planner.extractor;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorResult;

@AgentSubModule
public class ActionExtractor extends AgentRunningSubModule<ExtractorInput, ExtractorResult> implements ActivateModel {

    @Override
    public ExtractorResult execute(ExtractorInput data) {

        return null;
    }

    @Override
    public String modelKey() {
        return "action_extractor";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }
}
