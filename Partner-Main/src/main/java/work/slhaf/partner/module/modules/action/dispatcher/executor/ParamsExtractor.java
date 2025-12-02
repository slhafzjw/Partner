package work.slhaf.partner.module.modules.action.dispatcher.executor;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ExtractorInput;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ExtractorResult;

/**
 * 负责依据输入内容进行行动单元的参数信息提取
 */
@AgentSubModule
public class ParamsExtractor extends AgentRunningSubModule<ExtractorInput, ExtractorResult> implements ActivateModel {

    @Override
    public ExtractorResult execute(ExtractorInput data) {
        return null;
    }

    @Override
    public String modelKey() {
        return "params_extractor";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }

}
