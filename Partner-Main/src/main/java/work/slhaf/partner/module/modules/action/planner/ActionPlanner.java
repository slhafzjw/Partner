package work.slhaf.partner.module.modules.action.planner;

import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.ActionStatus;
import work.slhaf.partner.core.action.entity.ImmediateActionInfo;
import work.slhaf.partner.core.action.entity.MetaActionInfo;
import work.slhaf.partner.core.action.entity.ScheduledActionInfo;
import work.slhaf.partner.core.cache.CacheCapability;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.module.common.module.PreRunningModule;
import work.slhaf.partner.module.modules.action.planner.evaluator.ActionEvaluator;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorResult;
import work.slhaf.partner.module.modules.action.planner.extractor.ActionExtractor;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 负责针对本次输入生成基础的行动计划，在主模型传达意愿后，执行行动或者放入计划池
 */
@AgentModule(name = "task_planner", order = 2)
public class ActionPlanner extends PreRunningModule {

    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;
    @InjectCapability
    private CacheCapability cacheCapability;

    @InjectModule
    private ActionEvaluator actionEvaluator;
    @InjectModule
    private ActionExtractor actionExtractor;

    @Override
    protected void doExecute(PartnerRunningFlowContext context) {
        ExtractorInput extractorInput = getExtractorInput(context);
        ExtractorResult extractorResult = actionExtractor.execute(extractorInput);
        EvaluatorInput evaluatorInput = getEvaluatorInput(extractorResult, context.getUserId());
        EvaluatorResult evaluatorResult = actionEvaluator.execute(evaluatorInput);
        setupPreparedActionInfo(evaluatorResult, context.getUuid());

    }

    private void setupPreparedActionInfo(EvaluatorResult evaluatorResult, String uuid) {
        MetaActionInfo metaActionInfo = switch (evaluatorResult.getType()) {
            case PLANNING -> {
                ScheduledActionInfo actionInfo = new ScheduledActionInfo();
                actionInfo.setActionData(evaluatorResult.getActionData());
                actionInfo.setScheduleContent(evaluatorResult.getScheduleContent());
                actionInfo.setStatus(ActionStatus.PREPARE);
                yield actionInfo;
            }
            case IMMEDIATE -> {
                ImmediateActionInfo actionInfo = new ImmediateActionInfo();
                actionInfo.setActionData(evaluatorResult.getActionData());
                actionInfo.setStatus(ActionStatus.PREPARE);
                yield actionInfo;
            }
        };
        actionCapability.putPreparedAction(uuid, metaActionInfo);
    }

    private EvaluatorInput getEvaluatorInput(ExtractorResult extractorResult, String userId) {
        EvaluatorInput input = new EvaluatorInput();
        input.setTendency(extractorResult.getTendency());
        input.setUser(perceiveCapability.getUser(userId));
        input.setRecentMessages(cognationCapability.getChatMessages());
        input.setActivatedSlices(cacheCapability.getActivatedSlices(userId));
        return input;
    }

    private ExtractorInput getExtractorInput(PartnerRunningFlowContext context) {
        ExtractorInput input = new ExtractorInput();
        input.setInput(context.getInput());
        List<Message> chatMessages = cognationCapability.getChatMessages();
        List<Message> recentMessages = new ArrayList<>();
        if (chatMessages.size() > 5) {
            recentMessages.addAll(chatMessages.subList(chatMessages.size() - 5, chatMessages.size() - 1));
        } else if (chatMessages.size() > 1) {
            recentMessages.addAll(chatMessages.subList(0, chatMessages.size() - 1));
        }
        input.setRecentMessages(recentMessages);
        return input;
    }

    @Override
    protected HashMap<String, String> getPromptDataMap(String userId) {
        MetaActionInfo actionInfo = actionCapability.getPreparedAction(userId);
        HashMap<String, String> map = new HashMap<>();
        map.put("[行动确认原因] <生成行动的原因>", actionInfo.getActionData().getReason());

        if (actionInfo instanceof ImmediateActionInfo) {
            map.put("[行动类型] <将执行的行动类型，分为即时行动与计划行动>", "即时");
            map.put("[行动倾向] <你将要执行的动作>", actionInfo.getTendency());
            map.put("[行动工具] <本次行动将要调用的工具>", actionInfo.getActionData().getKey() + ": " + actionInfo.getActionData().getDescription());
        } else {
            ScheduledActionInfo info = (ScheduledActionInfo) actionInfo;
            map.put("[行动类型] <将执行的行动类型，分为即时行动与计划行动>", "计划");
            map.put("[计划内容] <生成的计划行动的内容，主要是计划时间的DateTime值或者CRON表达式>", info.getScheduleContent());
        }
        return map;
    }

    @Override
    protected String moduleName() {
        return "[行动模块]";
    }
}
