package work.slhaf.partner.module.modules.action.identifier;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.module.common.module.PreRunningModule;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.HashMap;

/**
 * 负责识别潜在的行动干预信息，作用于正在进行或已存在的行动池中内容
 */
@AgentModule(name = "action_identifier", order = 2)
public class ActionIdentifier extends PreRunningModule implements ActivateModel {

    @Override
    protected void doExecute(PartnerRunningFlowContext context) {

    }

    @Override
    public String modelKey() {
        return "";
    }

    @Override
    public boolean withBasicPrompt() {
        return false;
    }

    @Override
    protected HashMap<String, String> getPromptDataMap(PartnerRunningFlowContext context) {
        return null;
    }

    @Override
    protected String moduleName() {
        return "";
    }
}
