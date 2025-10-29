package work.slhaf.partner.module.modules.action.identifier;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.ActivateModel;
import work.slhaf.partner.module.common.module.PreRunningModule;
import work.slhaf.partner.module.modules.action.identifier.evaluator.InterventionEvaluator;
import work.slhaf.partner.module.modules.action.identifier.recognizer.InterventionRecognizer;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.HashMap;

/**
 * 负责识别潜在的行动干预信息，作用于正在进行或已存在的行动池中内容
 */
@AgentModule(name = "action_identifier", order = 2)
public class ActionInterventor extends PreRunningModule implements ActivateModel {

    @InjectModule
    private InterventionRecognizer interventionRecognizer;
    @InjectModule
    private InterventionEvaluator interventionEvaluator;

    @Override
    protected void doExecute(PartnerRunningFlowContext context) {
        //综合当前正在进行的行动链信息、用户交互历史、激活的记忆切片，尝试识别出是否存在行动干预意图
        //首先通过recognizer进行快速意图识别，识别成功则步入评估阶段，评估成功则直接作用于目标行动链
    }

    @Override
    public String modelKey() {
        return "action_identifier";
    }

    @Override
    public boolean withBasicPrompt() {
        return true;
    }

    @Override
    protected HashMap<String, String> getPromptDataMap(PartnerRunningFlowContext context) {
        return null;
    }

    @Override
    protected String moduleName() {
        return "[行动干预识别模块]";
    }
}
