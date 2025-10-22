package work.slhaf.partner.module.modules.action.planner;

import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.thread.InteractionThreadPoolExecutor;
import work.slhaf.partner.common.vector.VectorClient;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.entity.ActionInfo;
import work.slhaf.partner.core.action.entity.ActionStatus;
import work.slhaf.partner.core.action.entity.ImmediateActionInfo;
import work.slhaf.partner.core.action.entity.ScheduledActionInfo;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustMetaData;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.module.common.module.PreRunningModule;
import work.slhaf.partner.module.modules.action.planner.confirmer.ActionConfirmer;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerInput;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerResult;
import work.slhaf.partner.module.modules.action.planner.evaluator.ActionEvaluator;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorResult;
import work.slhaf.partner.module.modules.action.planner.extractor.ActionExtractor;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorResult;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * 负责针对本次输入生成基础的行动计划，在主模型传达意愿后，执行行动或者放入计划池
 */
@AgentModule(name = "action_planner", order = 2)
public class ActionPlanner extends PreRunningModule {

    @InjectCapability
    private CognationCapability cognationCapability;
    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private PerceiveCapability perceiveCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;

    @InjectModule
    private ActionEvaluator actionEvaluator;
    @InjectModule
    private ActionExtractor actionExtractor;
    @InjectModule
    private ActionConfirmer actionConfirmer;

    private InteractionThreadPoolExecutor executor;
    private ActionAssemblyHelper assemblyHelper;

    @Init
    public void init() {
        executor = InteractionThreadPoolExecutor.getInstance();
        assemblyHelper = new ActionAssemblyHelper();
    }

    @Override
    protected void doExecute(PartnerRunningFlowContext context) {
        List<Callable<Void>> tasks = new ArrayList<>();
        addConfirmTask(tasks, context);
        addNewActionTask(tasks, context);
        executor.invokeAll(tasks);
    }

    /**
     * 新的提取与评估任务
     *
     * @param tasks   并发任务列表
     * @param context 流程上下文
     */
    private void addNewActionTask(List<Callable<Void>> tasks, PartnerRunningFlowContext context) {
        tasks.add(() -> {
            ExtractorInput extractorInput = assemblyHelper.buildExtractorInput(context);
            ExtractorResult extractorResult = actionExtractor.execute(extractorInput);
            if (extractorResult.getTendencies().isEmpty()) {
                return null;
            }
            EvaluatorInput evaluatorInput = assemblyHelper.buildEvaluatorInput(extractorResult, context.getUserId());
            List<EvaluatorResult> evaluatorResults = actionEvaluator.execute(evaluatorInput); //并发操作均为访问
            setupActionInfo(evaluatorResults, context);
            return null;
        });
    }

    @AfterExecute
    private void updateTendencyCache(List<EvaluatorResult> evaluatorResults, String input, ExtractorResult extractorResult) {
        if (!VectorClient.status) {
            return;
        }
        executor.execute(() -> {
            CacheAdjustData data = new CacheAdjustData();
            List<CacheAdjustMetaData> list = new ArrayList<>();
            List<String> hitTendencies = extractorResult.getTendencies();
            for (EvaluatorResult result : evaluatorResults) {
                CacheAdjustMetaData metaData = new CacheAdjustMetaData();
                metaData.setTendency(result.getTendency());
                metaData.setPassed(result.isOk());
                metaData.setHit(hitTendencies.contains(result.getTendency()));
                list.add(metaData);
            }
            data.setMetaDataList(list);
            data.setInput(input);
            actionCapability.updateTendencyCache(data);
        });

    }

    /**
     * 待确认行动的判断任务
     *
     * @param tasks   并发任务列表
     * @param context 流程上下文
     */
    private void addConfirmTask(List<Callable<Void>> tasks, PartnerRunningFlowContext context) {
        tasks.add(() -> {
            ConfirmerInput confirmerInput = assemblyHelper.buildConfirmerInput(context);
            ConfirmerResult result = actionConfirmer.execute(confirmerInput);
            setupPendingActionInfo(context, result);
            return null;
        });
    }

    private void setupPendingActionInfo(PartnerRunningFlowContext context, ConfirmerResult result) {
        //TODO 需考虑未确认任务的失效或者拒绝时机，在action core中实现
        List<String> uuids = result.getUuids();
        if (uuids == null) {
            return;
        }
        String contextUuid = context.getUuid();
        List<ActionInfo> pendingActions = actionCapability.popPendingAction(context.getUserId());
        for (ActionInfo actionInfo : pendingActions) {
            if (uuids.contains(actionInfo.getUuid())) {
                actionCapability.putPreparedAction(contextUuid, actionInfo);
            }
        }
    }


    private void setupActionInfo(List<EvaluatorResult> evaluatorResults, PartnerRunningFlowContext context) {
        for (EvaluatorResult evaluatorResult : evaluatorResults) {
            ActionInfo actionInfo = assemblyHelper.buildMetaActionInfo(evaluatorResult);
            if (evaluatorResult.isNeedConfirm()) {
                actionCapability.putPendingActions(context.getUserId(), actionInfo);
            } else {
                actionCapability.putPreparedAction(context.getUuid(), actionInfo);
            }
        }
    }


    @Override
    protected HashMap<String, String> getPromptDataMap(PartnerRunningFlowContext context) {
        HashMap<String, String> map = new HashMap<>();
        setupPendingActions(map, context.getUserId());
        setupPreparedActions(map, context.getUuid());
        return map;
    }

    private void setupPendingActions(HashMap<String, String> map, String userId) {
        List<ActionInfo> actionInfos = actionCapability.listPendingAction(userId);
        if (actionInfos == null || actionInfos.isEmpty()) {
            map.put("[待确认行动] <待确认行动信息>", "无待确认行动");
            return;
        }
        for (int i = 0; i < actionInfos.size(); i++) {
            map.put("[待确认行动 " + (i + 1) + " ]", generateActionStr(actionInfos.get(i)));
        }
    }

    private void setupPreparedActions(HashMap<String, String> map, String uuid) {
        List<ActionInfo> actionInfos = actionCapability.listPreparedAction(uuid);
        if (actionInfos == null || actionInfos.isEmpty()) {
            map.put("[预备行动] <预备行动信息>", "无预备行动");
            return;
        }
        for (int i = 0; i < actionInfos.size(); i++) {
            map.put("[预备行动 " + (i + 1) + " ]", generateActionStr(actionInfos.get(i)));
        }
    }

    private String generateActionStr(ActionInfo actionInfo) {
        return "<行动倾向>" + " : " + actionInfo.getTendency() +
                "<行动原因>" + " : " + actionInfo.getReason() +
                "<工具描述>" + " : " + actionInfo.getDescription();
    }

    @Override
    protected String moduleName() {
        return "[行动模块]";
    }

    private class ActionAssemblyHelper {
        private ActionAssemblyHelper() {
        }

        private ExtractorInput buildExtractorInput(PartnerRunningFlowContext context) {
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

        private EvaluatorInput buildEvaluatorInput(ExtractorResult extractorResult, String userId) {
            EvaluatorInput input = new EvaluatorInput();
            input.setTendencies(extractorResult.getTendencies());
            input.setUser(perceiveCapability.getUser(userId));
            input.setRecentMessages(cognationCapability.getChatMessages());
            input.setActivatedSlices(memoryCapability.getActivatedSlices(userId));
            return input;
        }

        private ActionInfo buildMetaActionInfo(EvaluatorResult evaluatorResult) {
            return switch (evaluatorResult.getType()) {
                case PLANNING -> {
                    ScheduledActionInfo actionInfo = new ScheduledActionInfo();
                    actionInfo.setActionChain(evaluatorResult.getActionChain());
                    actionInfo.setScheduleContent(evaluatorResult.getScheduleContent());
                    actionInfo.setStatus(ActionStatus.PREPARE);
                    actionInfo.setUuid(UUID.randomUUID().toString());
                    yield actionInfo;
                }
                case IMMEDIATE -> {
                    ImmediateActionInfo actionInfo = new ImmediateActionInfo();
                    actionInfo.setActionChain(evaluatorResult.getActionChain());
                    actionInfo.setStatus(ActionStatus.PREPARE);
                    actionInfo.setUuid(UUID.randomUUID().toString());
                    yield actionInfo;
                }
            };
        }

        private ConfirmerInput buildConfirmerInput(PartnerRunningFlowContext context) {
            ConfirmerInput confirmerInput = new ConfirmerInput();
            confirmerInput.setInput(context.getInput());
            List<ActionInfo> pendingActions = actionCapability.listPendingAction(context.getUserId());
            confirmerInput.setActionInfos(pendingActions);
            return confirmerInput;
        }
    }
}
