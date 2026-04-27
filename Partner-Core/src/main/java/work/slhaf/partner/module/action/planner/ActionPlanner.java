package work.slhaf.partner.module.action.planner;

import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.core.action.entity.*;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;
import work.slhaf.partner.framework.agent.factory.component.annotation.InjectModule;
import work.slhaf.partner.framework.agent.support.Result;
import work.slhaf.partner.module.StateHintContent;
import work.slhaf.partner.module.action.executor.ActionExecutor;
import work.slhaf.partner.module.action.planner.evaluator.ActionEvaluator;
import work.slhaf.partner.module.action.planner.evaluator.entity.EvaluatorInput;
import work.slhaf.partner.module.action.planner.evaluator.entity.EvaluatorResult;
import work.slhaf.partner.module.action.planner.extractor.ActionExtractor;
import work.slhaf.partner.module.action.planner.extractor.entity.ExtractorResult;
import work.slhaf.partner.module.action.scheduler.ActionScheduler;
import work.slhaf.partner.runtime.PartnerRunningFlowContext;
import work.slhaf.partner.runtime.exception.ContextExceptionReporter;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 负责针对本次输入生成基础的行动计划
 */
@Slf4j
public class ActionPlanner extends AbstractAgentModule.Running<PartnerRunningFlowContext> {

    private static final String IMMEDIATE_WATCHER_CRON = "0/5 * * * * ?";
    private static final String TENDENCIES_EVALUATING_BLOCK_NAME = "pending_action_intentions";

    private final ActionAssemblyHelper assemblyHelper = new ActionAssemblyHelper();

    @InjectCapability
    private CognitionCapability cognitionCapability;
    @InjectCapability
    private ActionCapability actionCapability;

    @InjectModule
    private ActionEvaluator actionEvaluator;
    @InjectModule
    private ActionExtractor actionExtractor;
    @InjectModule
    private ActionScheduler actionScheduler;
    @InjectModule
    private ActionExecutor actionExecutor;

    private ExecutorService executor;

    @Init
    public void init() {
        this.setModuleName("action_planner");
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
    }

    @Override
    protected void doExecute(@NotNull PartnerRunningFlowContext context) {
        String input = context.encodeInputsBlock().encodeToXmlString();
        Result<ExtractorResult> result = actionExtractor.execute(input)
                .onFailure(exp -> ExceptionReporterHandler.INSTANCE.report(exp, ContextExceptionReporter.REPORTER_NAME));
        if (result.exceptionOrNull() != null) {
            return;
        }
        ExtractorResult extractorResult = result.getOrThrow();
        List<String> tendencies = extractorResult.getTendencies();
        if (tendencies.isEmpty()) {
            return;
        }
        appendTendencyBlock(tendencies);
        evaluateTendency(context.getSource(), extractorResult);
    }

    private void appendTendencyBlock(List<String> tendencies) {
        String datetime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        cognitionCapability.contextWorkspace().register(new ContextBlock(
                buildTendenciesEvaluatingCompactBlock(tendencies, datetime),
                buildTendenciesEvaluatingCompactBlock(tendencies, datetime),
                buildTendenciesEvaluatingAbstractBlock(tendencies, datetime),
                Set.of(ContextBlock.FocusedDomain.ACTION),
                60, 18, 4
        ));

        cognitionCapability.contextWorkspace().register(StateHintContent.createBlock(new StateHintContent(
                TENDENCIES_EVALUATING_BLOCK_NAME,
                "Partner intends to handle the latest input through actions. When replying to the user, present this as Partner preparing to take action, not as an internal evaluation process."
        ) {
            @Override
            public void fillStateContent(@NotNull Document document, @NotNull Element stateElement) {
                appendListElement(document, stateElement, "action_intentions", "action_intention", tendencies);
            }
        }));
    }

    private @NotNull BlockContent buildTendenciesEvaluatingAbstractBlock(List<String> tendencies, String datetime) {
        return new BlockContent(TENDENCIES_EVALUATING_BLOCK_NAME, getModuleName()) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "datetime", datetime);
                appendTextElement(document, root, "abstract", "There are " + tendencies.size() + " action intentions that Partner is preparing to handle.");
            }
        };
    }

    private @NotNull BlockContent buildTendenciesEvaluatingCompactBlock(List<String> tendencies, String datetime) {
        return new BlockContent(TENDENCIES_EVALUATING_BLOCK_NAME, getModuleName()) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                int size = tendencies.size();
                boolean num = size > 3;
                appendTextElement(document, root, "datetime", datetime);
                appendTextElement(document, root, "tendencies_count", size);
                appendListElement(document, root, num ? "tendencies_truncated" : "tendencies", "tendency", num ? tendencies.subList(0, 3) : tendencies);
            }
        };
    }

    private void evaluateTendency(String source, ExtractorResult extractorResult) {
        executor.execute(() -> {
            EvaluatorInput evaluatorInput = assemblyHelper.buildEvaluatorInput(extractorResult);
            List<EvaluatorResult> evaluatorResults = actionEvaluator.execute(evaluatorInput); // 并发操作均为访问
            handleEvaluatorResults(evaluatorResults, source);

            cognitionCapability.contextWorkspace().expire(TENDENCIES_EVALUATING_BLOCK_NAME, getModuleName());
        });
    }

    private void handleEvaluatorResults(List<EvaluatorResult> evaluatorResults, String source) {
        boolean hasPendingConfirmation = false;
        List<String> refusedTendencies = new ArrayList<>();
        for (EvaluatorResult evaluatorResult : evaluatorResults) {
            expireResolvedPending(evaluatorResult);
            if (!evaluatorResult.isOk()) {
                refusedTendencies.add(evaluatorResult.getTendency());
                continue;
            }
            ExecutableAction executableAction = assemblyHelper.buildActionData(evaluatorResult, source);
            if (executableAction == null) {
                continue;
            }
            if (evaluatorResult.isNeedConfirm()) {
                hasPendingConfirmation = true;
                registerPendingContextBlock(executableAction, evaluatorResult);
                continue;
            }
            executeOrSchedule(executableAction);
        }
        if (!refusedTendencies.isEmpty()) {
            cognitionCapability.initiateTurn("Some candidate action tendencies were refused during evaluation. Only reply if the user has not already received a suitable response and one of the refused tendencies was explicitly promised or implied as executable. If a response has already been given, or no promise was made, respond with NO_REPLY.", source, getModuleName());
        }
        if (hasPendingConfirmation) {
            cognitionCapability.initiateTurn("Some actions are pending user confirmation. Ask the user to confirm before executing them.", source, getModuleName());
        }
    }

    private void expireResolvedPending(EvaluatorResult evaluatorResult) {
        EvaluatorResult.ResolvedPending resolvedPending = evaluatorResult.getResolvedPending();
        if (resolvedPending == null) {
            return;
        }
        if (resolvedPending.getBlockName() == null || resolvedPending.getSource() == null) {
            return;
        }
        cognitionCapability.contextWorkspace().expire(
                resolvedPending.getBlockName(),
                resolvedPending.getSource()
        );
    }

    private void registerPendingContextBlock(ExecutableAction executableAction, EvaluatorResult evaluatorResult) {
        cognitionCapability.contextWorkspace().register(StateHintContent.createBlock(new StateHintContent(
                "actions_need_confirmation",
                "Partner is waiting for user confirmation before executing a pending action."
        ) {
            @Override
            public void fillStateContent(@NotNull Document document, @NotNull Element stateElement) {
                appendTextElement(document, stateElement, "state", "need_user_confirm");
                appendTextElement(document, stateElement, "action_type", evaluatorResult.getType());
                appendTextElement(document, stateElement, "tendency", executableAction.getTendency());
                appendTextElement(document, stateElement, "reason", executableAction.getReason());
                appendTextElement(document, stateElement, "description", executableAction.getDescription());
                appendTextElement(document, stateElement, "source_user", executableAction.getSource());
                EvaluatorResult.ScheduleData scheduleData = evaluatorResult.getScheduleData();
                if (scheduleData != null) {
                    appendTextElement(document, stateElement, "schedule_type", scheduleData.getType());
                    appendTextElement(document, stateElement, "schedule_content", scheduleData.getContent());
                }
                Map<Integer, List<String>> primaryActionChain = evaluatorResult.getPrimaryActionChainAsMap();
                if (primaryActionChain == null || primaryActionChain.isEmpty()) {
                    return;
                }
                Element chainRoot = document.createElement("primary_action_chain");
                stateElement.appendChild(chainRoot);
                List<Integer> orders = new ArrayList<>(primaryActionChain.keySet());
                orders.sort(Integer::compareTo);
                for (Integer order : orders) {
                    Element orderElement = document.createElement("step");
                    orderElement.setAttribute("order", String.valueOf(order));
                    chainRoot.appendChild(orderElement);
                    appendRepeatedElements(
                            document,
                            orderElement,
                            "action_key",
                            primaryActionChain.getOrDefault(order, List.of())
                    );
                }
            }
        }));
    }

    private void executeOrSchedule(ExecutableAction executableAction) {
        switch (executableAction) {
            case ImmediateExecutableAction action -> executeImmediateWithWatcher(action);
            case SchedulableExecutableAction action -> actionScheduler.schedule(action);
            default -> log.error("unknown executable action type: {}", executableAction.getClass().getSimpleName());
        }
    }

    private void executeImmediateWithWatcher(ImmediateExecutableAction action) {
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
                    return Unit.INSTANCE;
                })
        );
        watcherRef[0] = watcher;
        actionScheduler.schedule(watcher);
    }

    @Override
    public int order() {
        return 2;
    }

    private final class ActionAssemblyHelper {
        private ActionAssemblyHelper() {
        }

        private EvaluatorInput buildEvaluatorInput(ExtractorResult extractorResult) {
            EvaluatorInput input = new EvaluatorInput();
            input.setTendencies(extractorResult.getTendencies());
            return input;
        }

        private ExecutableAction buildActionData(EvaluatorResult evaluatorResult, String userId) {
            Map<Integer, List<MetaAction>> actionChain = getActionChain(evaluatorResult);
            if (actionChain == null) {
                return null;
            }
            return switch (evaluatorResult.getType()) {
                case PLANNING -> new SchedulableExecutableAction(
                        evaluatorResult.getTendency(),
                        actionChain,
                        evaluatorResult.getReason(),
                        evaluatorResult.getDescription(),
                        userId,
                        evaluatorResult.getScheduleData().getType(),
                        evaluatorResult.getScheduleData().getContent()
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

        private Map<Integer, List<MetaAction>> getActionChain(EvaluatorResult evaluatorResult) {
            Map<Integer, List<MetaAction>> actionChain = new HashMap<>();
            Map<Integer, List<String>> primaryActionChain = evaluatorResult.getPrimaryActionChainAsMap();
            if (!fixDependencies(primaryActionChain)) {
                return null;
            }
            for (Map.Entry<Integer, List<String>> entry : primaryActionChain.entrySet()) {
                List<MetaAction> metaActions = new ArrayList<>();
                for (String actionKey : entry.getValue()) {
                    Result<MetaAction> metaActionResult = actionCapability.loadMetaAction(actionKey);
                    AgentRuntimeException failure = metaActionResult.onSuccess(metaActions::add)
                            .exceptionOrNull();
                    if (failure != null) {
                        return null;
                    }
                }
                actionChain.put(entry.getKey(), metaActions);
            }
            return actionChain;
        }

        private boolean fixDependencies(Map<Integer, List<String>> primaryActionChain) {
            if (primaryActionChain == null || primaryActionChain.isEmpty()) {
                return false;
            }
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
                    if (actionKeys == null || actionKeys.isEmpty()) {
                        continue;
                    }
                    for (String actionKey : actionKeys) {
                        // 根据 actionKey 加载行动信息,并检查是否存在必需前置依赖

                        Result<MetaActionInfo> infoResult = actionCapability.loadMetaActionInfo(actionKey);
                        if (infoResult.exceptionOrNull() != null) {
                            return false;
                        }

                        MetaActionInfo metaActionInfo = infoResult.getOrThrow();
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
            return true;
        }

        private void fixOrder(Map<Integer, List<String>> primaryActionChain) {
            Map<Integer, List<String>> tempChain = new HashMap<>(primaryActionChain);
            primaryActionChain.clear();
            List<Integer> orders = new ArrayList<>(tempChain.keySet());
            orders.sort(Integer::compareTo);
            int fixedOrder = 1;
            for (Integer order : orders) {
                List<String> actionKeys = tempChain.get(order);
                primaryActionChain.put(fixedOrder++, actionKeys == null ? new ArrayList<>() : new ArrayList<>(actionKeys));
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

    }
}
