package work.slhaf.partner.module.modules.action.dispatcher.executor;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.module.modules.action.dispatcher.executor.entity.ParamsExtractorInput;

/**
 * 负责依据输入内容进行行动单元的参数信息提取
 */
@AgentSubModule
public class ParamsExtractor extends AgentRunningSubModule<ParamsExtractorInput, String[]> implements ActivateModel {

    @Override
    public String[] execute(ParamsExtractorInput data) {
        return new String[0];
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
