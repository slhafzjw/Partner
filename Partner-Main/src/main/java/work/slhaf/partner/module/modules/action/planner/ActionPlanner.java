package work.slhaf.partner.module.modules.action.planner;

import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.module.common.module.PreRunningModule;
import work.slhaf.partner.module.modules.action.planner.evaluator.ActionEvaluator;
import work.slhaf.partner.module.modules.action.planner.extractor.ActionExtractor;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorInput;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.io.IOException;
import java.util.HashMap;

/**
 * 负责针对本次输入生成基础的行动建议，是否执行由主模型判断。
 */
@AgentModule(name = "task_planner",order = 2)
public class ActionPlanner extends PreRunningModule {

    @InjectModule
    private ActionEvaluator actionEvaluator;
    @InjectModule
    private ActionExtractor actionExtractor;

    @Override
    protected void doExecute(PartnerRunningFlowContext context) throws IOException, ClassNotFoundException {
        ExtractorInput extractorInput = getExtractorInput(context);
    }

    private ExtractorInput getExtractorInput(PartnerRunningFlowContext context) {
        ExtractorInput input = new ExtractorInput();
        input.setInput(context.getInput());
        input.setRecentMessages();
        return input;
    }

    @Override
    protected HashMap<String, String> getPromptDataMap(String userId) {
        return null;
    }

    @Override
    protected String moduleName() {
        return "task_planner";
    }
}
