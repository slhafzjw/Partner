package work.slhaf.partner.module.modules.action.planner;

import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.factory.component.annotation.Init;
import work.slhaf.partner.api.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.api.chat.pojo.Message;
import work.slhaf.partner.common.vector.VectorClient;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustData;
import work.slhaf.partner.core.action.entity.cache.CacheAdjustMetaData;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.memory.MemoryCapability;
import work.slhaf.partner.module.modules.action.executor.ActionExecutor;
import work.slhaf.partner.module.modules.action.planner.confirmer.ActionConfirmer;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerInput;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.ConfirmerResult;
import work.slhaf.partner.module.modules.action.planner.confirmer.entity.PendingDecisionItem;
import work.slhaf.partner.module.modules.action.planner.evaluator.ActionEvaluator;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.modules.action.planner.evaluator.entity.EvaluatorResult;
import work.slhaf.partner.module.modules.action.planner.extractor.ActionExtractor;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorInput;
import work.slhaf.partner.module.modules.action.planner.extractor.entity.ExtractorResult;
import work.slhaf.partner.module.modules.action.scheduler.ActionScheduler;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 负责针对本次输入生成基础的行动计划
 */
public class ActionPlanner extends AbstractAgentModule.Running<PartnerRunningFlowContext> {

    private static final long PENDING_TTL_MILLIS = 30 * 60 * 1000L;
    private static final long PENDING_REMINDER_ADVANCE_MILLIS = 5 * 60 * 1000L;
    private static final String IMMEDIATE_WATCHER_CRON = "0/5 * * * * ?";

    private final ActionAssemblyHelper assemblyHelper = new ActionAssemblyHelper();

    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectCapability
    private ActionCapability actionCapability;
    @InjectCapability
    private MemoryCapability memoryCapability;

    @InjectModule
    private ActionEvaluator actionEvaluator;
    @InjectModule
    private ActionExtractor actionExtractor;
    @InjectModule
    private ActionConfirmer actionConfirmer;
    @InjectModule
    private ActionScheduler actionScheduler;
    @InjectModule
    private ActionExecutor actionExecutor;

    private ExecutorService executor;

    @Init
    public void init() {
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
    }

    @Override
    public void execute(PartnerRunningFlowContext context) {
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
            EvaluatorInput evaluatorInput = assemblyHelper.buildEvaluatorInput(extractorResult, context.getSource());
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
        List<PendingDecisionItem> decisions = result.getDecisions();
        if (decisions == null || decisions.isEmpty()) {
            return;
        }
        for (PendingDecisionItem decisionItem : decisions) {
            PendingActionRecord pendingAction = actionCapability.resolvePendingDecision(
                    context.getSource(),
                    decisionItem.getPendingId(),
                    decisionItem.getDecision(),
                    decisionItem.getReason()
            );
            if (pendingAction == null) {
                continue;
            }
            if (decisionItem.getDecision() == PendingActionRecord.Decision.CONFIRM
                    && pendingAction.getStatus() == PendingActionRecord.Status.CONFIRMED) {
                executeOrSchedule(pendingAction.getExecutableAction());
            }
        }
    }

    private void putActionData(List<EvaluatorResult> evaluatorResults, PartnerRunningFlowContext context) {
        for (EvaluatorResult evaluatorResult : evaluatorResults) {
            ExecutableAction executableAction = assemblyHelper.buildActionData(evaluatorResult, context.getSource());
            if (evaluatorResult.isNeedConfirm()) {
                PendingActionRecord pendingAction = actionCapability.createPendingAction(
                        context.getSource(),
                        executableAction,
                        PENDING_TTL_MILLIS,
                        PENDING_REMINDER_ADVANCE_MILLIS
                );
                schedulePendingLifecycleActions(pendingAction);
            } else {
                executeOrSchedule(executableAction);
            }
        }
    }

    private void schedulePendingLifecycleActions(PendingActionRecord pendingAction) {
        StateAction reminderAction = buildPendingReminderAction(pendingAction);
        StateAction expireAction = buildPendingExpireAction(pendingAction);
        actionCapability.bindPendingLifecycleActions(pendingAction.getPendingId(), reminderAction, expireAction);
        actionScheduler.schedule(reminderAction);
        actionScheduler.schedule(expireAction);
    }

    private StateAction buildPendingReminderAction(PendingActionRecord pendingAction) {
        return new StateAction(
                pendingAction.getUserId(),
                "pending-action-reminder:" + pendingAction.getPendingId(),
                "待确认行动提醒",
                Schedulable.ScheduleType.ONCE,
                asScheduleContent(pendingAction.getRemindAt()),
                new StateAction.Trigger.Call(() -> {
                    handlePendingReminder(pendingAction.getPendingId(), pendingAction.getUserId());
                    return Unit.INSTANCE;
                })
        );
    }

    private StateAction buildPendingExpireAction(PendingActionRecord pendingAction) {
        return new StateAction(
                pendingAction.getUserId(),
                "pending-action-expire:" + pendingAction.getPendingId(),
                "待确认行动失效",
                Schedulable.ScheduleType.ONCE,
                asScheduleContent(pendingAction.getExpireAt()),
                new StateAction.Trigger.Call(() -> {
                    handlePendingExpire(pendingAction.getPendingId());
                    return Unit.INSTANCE;
                })
        );
    }

    private void handlePendingReminder(String pendingId, String userId) {
        boolean marked = actionCapability.markPendingReminded(pendingId);
        if (!marked) {
            return;
        }
        try {
            // TODO target 指定行为待补充; 主动回复链路待补充
            cognitionCapability.initiateTurn("系统提醒：存在待确认行动即将过期，请确认是否继续执行。pendingId=" + pendingId, userId);
        } catch (Exception e) {
            log.warn("触发待确认行动提醒失败, pendingId: {}", pendingId, e);
        }
    }

    private void handlePendingExpire(String pendingId) {
        actionCapability.expirePendingIfWaiting(pendingId);
    }

    private String asScheduleContent(long targetTimeMillis) {
        long now = System.currentTimeMillis();
        long safeTarget = Math.max(targetTimeMillis, now + 1000L);
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(safeTarget), ZoneId.systemDefault()).toString();
    }

    private void executeOrSchedule(ExecutableAction executableAction) {
        switch (executableAction) {
            case ImmediateExecutableAction action -> executeImmediateWithWatcher(action);
            case SchedulableExecutableAction action -> actionScheduler.schedule(action);
            default -> log.error("unknown executable action type: {}", executableAction.getClass().getSimpleName());
        }
    }

    private void executeImmediateWithWatcher(ImmediateExecutableAction action) {
        actionCapability.putAction(action);
        actionExecutor.execute(action);

        AtomicBoolean notified = new AtomicBoolean(false);
        final StateAction[] watcherRef = new StateAction[1];
        StateAction watcher = new StateAction(
                action.getSource(),
                "immediate-action-watcher:" + action.getUuid(),
                "轮询即时行动执行结果",
                Schedulable.ScheduleType.CYCLE,
                IMMEDIATE_WATCHER_CRON,
                new StateAction.Trigger.Call(() -> {
                    Action.Status status = action.getStatus();
                    if (status != Action.Status.SUCCESS && status != Action.Status.FAILED) {
                        return Unit.INSTANCE;
                    }
                    if (watcherRef[0] != null) {
                        watcherRef[0].setEnabled(false);
                    }
                    if (!notified.compareAndSet(false, true)) {
                        return Unit.INSTANCE;
                    }
                    watcherSelfTalk(action);
                    return Unit.INSTANCE;
                })
        );
        watcherRef[0] = watcher;
        actionScheduler.schedule(watcher);
    }

    private void watcherSelfTalk(ImmediateExecutableAction action) {
        String result = action.getResult();
        String structuredSignal = String.format(
                "{event=immediate_action_finished,actionUuid=%s,tendency=%s,status=%s,source=%s,result=%s}",
                action.getUuid(),
                action.getTendency(),
                action.getStatus(),
                action.getSource(),
                result == null ? "" : result //将会在 ActionExecutor
        );
        try {
            cognitionCapability.initiateTurn(structuredSignal, action.getSource());
        } catch (Exception e) {
            log.warn("触发 immediate 行动完成自对话失败, actionUuid: {}", action.getUuid(), e);
        }
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
            List<Message> chatMessages = cognitionCapability.snapshotChatMessages();
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
            input.setRecentMessages(cognitionCapability.snapshotChatMessages());
            input.setActivatedSlices(memoryCapability.getActivatedSlices());
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
                        Set<String> preActions = metaActionInfo.getPreActions();
                        boolean preActionsExist = preActions.isEmpty();
                        if (!preActionsExist) {
                            continue;
                        }
                        if (!metaActionInfo.getStrictDependencies()) {
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
                        preActions = new HashSet<>(preActions);
                        actionsInChain.forEach(preActions::remove);
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

        private boolean checkDependenciesExist(int lastOrder, Set<String> preActions,
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
            List<PendingActionRecord> pendingActions = actionCapability.listActivePendingActions(context.getSource());
            confirmerInput.setRecentMessages(cognitionCapability.snapshotChatMessages());
            confirmerInput.setPendingActions(pendingActions);
            return confirmerInput;
        }
    }
}
