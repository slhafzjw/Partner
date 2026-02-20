package work.slhaf.partner.module.modules.action.planner;

import lombok.val;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.Init;
import work.slhaf.partner.api.agent.factory.module.annotation.InjectModule;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.vector.VectorClient;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustMetaData;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.module.common.module.PreRunningAbstractAgentModuleAbstract;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 负责针对本次输入生成基础的行动计划，在主模型传达意愿后，执行行动或者放入计划池
 */
public class ActionPlanner extends PreRunningAbstractAgentModuleAbstract {
    private final ActionAssemblyHelper assemblyHelper = new ActionAssemblyHelper();
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
            List<EvaluatorResult> evaluatorResults = actionEvaluator.execute(evaluatorInput); // 并发操作均为访问
            putActionData(evaluatorResults, context);
            updateTendencyCache(evaluatorResults, context.getInput(), extractorResult);
            return null;
        });
    }

    private void updateTendencyCache(List<EvaluatorResult> evaluatorResults, String input,
                                     ExtractorResult extractorResult) {
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
            setupConfirmedActionInfo(context, result);
            return null;
        });
    }

    private void setupConfirmedActionInfo(PartnerRunningFlowContext context, ConfirmerResult result) {
        // TODO 需考虑未确认任务的失效或者拒绝时机，在action core中实现
        List<String> uuids = result.getUuids();
        if (uuids == null) {
            return;
        }
        List<ExecutableAction> pendingActions = actionCapability.popPendingAction(context.getUserId());
        for (ExecutableAction executableAction : pendingActions) {
            if (uuids.contains(executableAction.getUuid())) {
                actionCapability.putAction(executableAction);
            }
        }
    }

    private void putActionData(List<EvaluatorResult> evaluatorResults, PartnerRunningFlowContext context) {
        for (EvaluatorResult evaluatorResult : evaluatorResults) {
            ExecutableAction executableAction = assemblyHelper.buildActionData(evaluatorResult, context.getUserId());
            if (evaluatorResult.isNeedConfirm()) {
                actionCapability.putPendingActions(context.getUserId(), executableAction);
            } else {
                actionCapability.putAction(executableAction);
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
        List<ExecutableAction> executableActionData = actionCapability.listPendingAction(userId);
        if (executableActionData == null || executableActionData.isEmpty()) {
            map.put("[待确认行动] <等待用户确认的行动信息>", "无待确认行动");
            return;
        }
        for (int i = 0; i < executableActionData.size(); i++) {
            map.put("[待确认行动 " + (i + 1) + " ] <等待用户确认的行动信息>", generateActionStr(executableActionData.get(i)));
        }
    }

    private void setupPreparedActions(HashMap<String, String> map, String userId) {
        val preparedActions = actionCapability.listActions(ExecutableAction.Status.PREPARE, userId).stream().toList();
        if (preparedActions.isEmpty()) {
            map.put("[预备行动] <预备执行或放入计划池的行动信息>", "无预备行动");
            return;
        }
        for (int i = 0; i < preparedActions.size(); i++) {
            map.put("[预备行动 " + (i + 1) + " ] <预备执行或放入计划池的行动信息>", generateActionStr(preparedActions.get(i)));
        }
    }

    private String generateActionStr(ExecutableAction executableAction) {
        return "<行动倾向>" + " : " + executableAction.getTendency() +
                "<行动原因>" + " : " + executableAction.getReason() +
                "<工具描述>" + " : " + executableAction.getDescription();
    }

    @Override
    protected String moduleName() {
        return "[行动模块]";
    }

    @Override
    public int order() {
        return 2;
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

        private ExecutableAction buildActionData(EvaluatorResult evaluatorResult, String userId) {
            Map<Integer, List<MetaAction>> actionChain = getActionChain(evaluatorResult);
            return switch (evaluatorResult.getType()) {
                case PLANNING -> new SchedulableExecutableAction(
                        evaluatorResult.getTendency(),
                        actionChain,
                        evaluatorResult.getReason(),
                        evaluatorResult.getDescription(),
                        userId,
                        evaluatorResult.getScheduleType(),
                        evaluatorResult.getScheduleContent()
                );
                case IMMEDIATE -> new ImmediateExecutableAction(
                        evaluatorResult.getTendency(),
                        actionChain,
                        evaluatorResult.getReason(),
                        evaluatorResult.getDescription(),
                        userId
                );
            };
        }

        private @NotNull Map<Integer, List<MetaAction>> getActionChain(EvaluatorResult evaluatorResult) {
            Map<Integer, List<MetaAction>> actionChain = new HashMap<>();
            Map<Integer, List<String>> primaryActionChain = evaluatorResult.getPrimaryActionChain();
            fixDependencies(primaryActionChain);
            primaryActionChain.forEach((order, actionKeys) -> {
                List<MetaAction> metaActions = actionKeys.stream()
                        .map(actionKey -> actionCapability.loadMetaAction(actionKey))
                        .toList();
                actionChain.put(order, metaActions);
            });
            return actionChain;
        }

        private void fixDependencies(Map<Integer, List<String>> primaryActionChain) {
            // 先将 primaryActionChain 的节点序号修正为从1开始依次增大
            fixOrder(primaryActionChain);
            List<Integer> fixedOrders = new ArrayList<>(primaryActionChain.keySet().stream().toList());
            AtomicBoolean fixed = new AtomicBoolean(false);
            do {
                Set<Integer> tempOrders = new HashSet<>();
                fixedOrders.sort(Integer::compareTo);
                for (Integer fixedOrder : fixedOrders) {
                    int lastOrder = fixedOrder - 1;
                    List<String> actionKeys = primaryActionChain.get(fixedOrder);
                    for (String actionKey : actionKeys) {
                        // 根据 actionKey 加载行动信息,并检查是否存在必需前置依赖
                        MetaActionInfo metaActionInfo = actionCapability.loadMetaActionInfo(actionKey);
                        List<String> preActions = metaActionInfo.getPreActions();
                        boolean preActionsExist = preActions != null && !preActions.isEmpty();
                        if (!preActionsExist) {
                            continue;
                        }
                        if (!metaActionInfo.isStrictDependencies()) {
                            continue;
                        }
                        if (checkDependenciesExist(lastOrder, preActions, primaryActionChain)) {
                            continue;
                        }
                        // 如果存在前置依赖,则将其放置在当前order之前的位置,
                        // 放置位置优先选择已存在的上一节点,如果不存在(行动链的头节点时)则需要向行动链新增order
                        // 不需要检查行动链的当前节点的已存在 Action 是否为新 Action 的依赖项,因为这些 Action 实际来自 LLM
                        // 的评估结果,并非作为依赖项存在
                        fixed.set(true);
                        List<String> actionsInChain = primaryActionChain.computeIfAbsent(lastOrder,
                                list -> new ArrayList<>());
                        preActions = new ArrayList<>(preActions);
                        preActions.removeAll(actionsInChain);
                        actionsInChain.addAll(preActions);
                        tempOrders.add(lastOrder);
                    }
                }
                fixedOrders.clear();
                fixedOrders.addAll(tempOrders);
            } while (fixed.getAndSet(false));
        }

        private void fixOrder(Map<Integer, List<String>> primaryActionChain) {
            Map<Integer, List<String>> tempChain = new HashMap<>(primaryActionChain);
            primaryActionChain.clear();
            int chainSize = tempChain.size();
            for (int i = 0; i < chainSize; i++) {
                primaryActionChain.put(i, tempChain.get(i));
            }
        }

        private boolean checkDependenciesExist(int lastOrder, List<String> preActions,
                                               Map<Integer, List<String>> primaryActionChain) {
            if (!primaryActionChain.containsKey(lastOrder)) {
                return false;
            }
            List<String> existActions = primaryActionChain.get(lastOrder);
            //noinspection SlowListContainsAll
            return existActions.containsAll(preActions);
        }

        private ConfirmerInput buildConfirmerInput(PartnerRunningFlowContext context) {
            ConfirmerInput confirmerInput = new ConfirmerInput();
            confirmerInput.setInput(context.getInput());
            List<ExecutableAction> pendingActions = actionCapability.listPendingAction(context.getUserId());
            confirmerInput.setExecutableActionData(pendingActions);
            return confirmerInput;
        }
    }
}
