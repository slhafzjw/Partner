package work.slhaf.partner.module.modules.action.planner;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.vector.VectorClient;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.ImmediateActionData;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.core.action.entity.ScheduledActionData;
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

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * 负责针对本次输入生成基础的行动计划，在主模型传达意愿后，执行行动或者放入计划池
 */
@Slf4j
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

    private ExecutorService executor;

    private final ActionAssemblyHelper assemblyHelper = new ActionAssemblyHelper();

    @Init
    public void init() {
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
    }

    @Override
    protected void doExecute(PartnerRunningFlowContext context) {
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            addConfirmTask(tasks, context);
            addNewActionTask(tasks, context);
            executor.invokeAll(tasks);
        } catch (Exception e) {
            log.error("执行异常", e);
        }
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
        List<ActionData> pendingActions = actionCapability.popPendingAction(context.getUserId());
        for (ActionData actionData : pendingActions) {
            if (uuids.contains(actionData.getUuid())) {
                actionCapability.putPreparedAction(contextUuid, actionData);
            }
        }
    }


    private void setupActionInfo(List<EvaluatorResult> evaluatorResults, PartnerRunningFlowContext context) {
        for (EvaluatorResult evaluatorResult : evaluatorResults) {
            ActionData actionData = assemblyHelper.buildMetaActionInfo(evaluatorResult);
            if (evaluatorResult.isNeedConfirm()) {
                actionCapability.putPendingActions(context.getUserId(), actionData);
            } else {
                actionCapability.putPreparedAction(context.getUuid(), actionData);
            }
        }
    }


    @Override
    protected Map<String, String> getPromptDataMap(PartnerRunningFlowContext context) {
        HashMap<String, String> map = new HashMap<>();
        String userId = context.getUserId();
        setupPendingActions(map, userId);
        setupPreparedActions(map, userId);
        return map;
    }

    private void setupPendingActions(HashMap<String, String> map, String userId) {
        List<ActionData> actionData = actionCapability.listPendingAction(userId);
        if (actionData == null || actionData.isEmpty()) {
            map.put("[待确认行动] <等待用户确认的行动信息>", "无待确认行动");
            return;
        }
        for (int i = 0; i < actionData.size(); i++) {
            map.put("[待确认行动 " + (i + 1) + " ] <等待用户确认的行动信息>", generateActionStr(actionData.get(i)));
        }
    }

    private void setupPreparedActions(HashMap<String, String> map, String userId) {
        List<ActionData> actionData = actionCapability.listPreparedAction(userId);
        if (actionData == null || actionData.isEmpty()) {
            map.put("[预备行动] <预备执行或放入计划池的行动信息>", "无预备行动");
            return;
        }
        for (int i = 0; i < actionData.size(); i++) {
            map.put("[预备行动 " + (i + 1) + " ] <预备执行或放入计划池的行动信息>", generateActionStr(actionData.get(i)));
        }
    }

    private String generateActionStr(ActionData actionData) {
        return "<行动倾向>" + " : " + actionData.getTendency() +
                "<行动原因>" + " : " + actionData.getReason() +
                "<工具描述>" + " : " + actionData.getDescription();
    }

    @Override
    protected String moduleName() {
        return "[行动模块]";
    }

    private final class ActionAssemblyHelper {
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

        private ActionData buildMetaActionInfo(EvaluatorResult evaluatorResult) {
            LinkedHashMap<Integer, List<MetaAction>> actionChain = new LinkedHashMap<>();
            for (MetaAction metaAction : evaluatorResult.getActionChain()) {
                actionChain.computeIfAbsent(metaAction.getOrder(), k -> new ArrayList<>()).add(metaAction);
            }
            return switch (evaluatorResult.getType()) {
                case PLANNING -> {
                    ScheduledActionData actionInfo = new ScheduledActionData();
                    actionInfo.getActionChain().putAll(actionChain);
                    actionInfo.setScheduleContent(evaluatorResult.getScheduleContent());
                    actionInfo.setStatus(ActionData.ActionStatus.PREPARE);
                    actionInfo.setUuid(UUID.randomUUID().toString());
                    yield actionInfo;
                }
                case IMMEDIATE -> {
                    ImmediateActionData actionInfo = new ImmediateActionData();
                    actionInfo.getActionChain().putAll(actionChain);
                    actionInfo.setStatus(ActionData.ActionStatus.PREPARE);
                    actionInfo.setUuid(UUID.randomUUID().toString());
                    yield actionInfo;
                }
            };
        }

        private ConfirmerInput buildConfirmerInput(PartnerRunningFlowContext context) {
            ConfirmerInput confirmerInput = new ConfirmerInput();
            confirmerInput.setInput(context.getInput());
            List<ActionData> pendingActions = actionCapability.listPendingAction(context.getUserId());
            confirmerInput.setActionData(pendingActions);
            return confirmerInput;
        }
    }
}
