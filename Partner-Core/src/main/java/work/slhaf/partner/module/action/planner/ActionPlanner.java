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
import work.slhaf.partner.core.cognition.CommunicationBlockContent;
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
    private static final String BLOCK_SOURCE = "action_planner_pending";
    private static final String TENDENCIES_EVALUATING_BLOCK_NAME = "action_tendencies_under_evaluation";

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
        evaluateTendency(context.getSource(), input, extractorResult);
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
                BLOCK_SOURCE,
                "Partner is evaluating whether any action tendency should be taken for the latest input."
        ) {
            @Override
            public void fillStateContent(@NotNull Document document, @NotNull Element stateElement) {
                appendListElement(document, stateElement, "action_tendencies", "tendency", tendencies);
            }
        }));
    }

    private @NotNull BlockContent buildTendenciesEvaluatingAbstractBlock(List<String> tendencies, String datetime) {
        return new BlockContent(TENDENCIES_EVALUATING_BLOCK_NAME, getModuleName()) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "datetime", datetime);
                appendTextElement(document, root, "abstract", "There are " + tendencies.size() + " candidate action tendencies under evaluation.");
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

    private void evaluateTendency(String source, String input, ExtractorResult extractorResult) {
        executor.execute(() -> {
            EvaluatorInput evaluatorInput = assemblyHelper.buildEvaluatorInput(extractorResult);
            List<EvaluatorResult> evaluatorResults = actionEvaluator.execute(evaluatorInput); // 并发操作均为访问
            handleEvaluatorResults(evaluatorResults, source, input);

            cognitionCapability.contextWorkspace().expire(TENDENCIES_EVALUATING_BLOCK_NAME, getModuleName());
        });
    }

    private void handleEvaluatorResults(List<EvaluatorResult> evaluatorResults, String source, String input) {
        List<ExecutableAction> passedActions = new ArrayList<>();
        int approvedExecutableCount = 0;
        int pendingConfirmCount = 0;
        for (EvaluatorResult evaluatorResult : evaluatorResults) {
            expireResolvedPending(evaluatorResult);
            if (!evaluatorResult.isOk()) {
                continue;
            }
            ExecutableAction executableAction = assemblyHelper.buildActionData(evaluatorResult, source);
            if (executableAction == null) {
                continue;
            }
            passedActions.add(executableAction);
            if (evaluatorResult.isNeedConfirm()) {
                registerPendingContextBlock(executableAction, evaluatorResult, input);
                pendingConfirmCount++;
                continue;
            }
            executeOrSchedule(executableAction);
            approvedExecutableCount++;
        }
        if (passedActions.isEmpty()) {
            return;
        }
        createTurn(approvedExecutableCount, pendingConfirmCount, source, input);
    }

    private void createTurn(int approvedExecutableCount, int pendingConfirmCount, String source, String input) {
        String turnInput = approvedExecutableCount + " actions are approved for execution, " +
                pendingConfirmCount + " actions are waiting for confirmation, " +
                "according to input: " + trimInput(input) + ". For more information, please refer to the context content or other tags in this input block.";
        cognitionCapability.initiateTurn(turnInput, source, getModuleName());
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

    private void registerPendingContextBlock(ExecutableAction executableAction, EvaluatorResult evaluatorResult, String input) {
        String blockName = buildPendingBlockName(executableAction);
        input = trimInput(input);
        ContextBlock block = new ContextBlock(
                buildPendingBlock(blockName, executableAction, evaluatorResult),
                buildPendingCompactBlock(blockName, executableAction, evaluatorResult, input),
                buildPendingAbstractBlock(blockName, executableAction, evaluatorResult, input),
                Set.of(ContextBlock.FocusedDomain.ACTION),
                30,
                10,
                5
        );
        cognitionCapability.contextWorkspace().register(block);
    }

    private String buildPendingBlockName(ExecutableAction executableAction) {
        return "pending_action-" + executableAction.getUuid();
    }

    private BlockContent buildPendingBlock(String blockName, ExecutableAction executableAction, EvaluatorResult evaluatorResult) {
        return new CommunicationBlockContent(blockName, BLOCK_SOURCE, BlockContent.Urgency.HIGH, CommunicationBlockContent.Projection.SUPPLY) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "state", "need_user_confirm");
                appendTextElement(document, root, "action_uuid", executableAction.getUuid());
                appendTextElement(document, root, "action_type", evaluatorResult.getType());
                appendTextElement(document, root, "tendency", executableAction.getTendency());
                appendTextElement(document, root, "reason", executableAction.getReason());
                appendTextElement(document, root, "description", executableAction.getDescription());
                appendTextElement(document, root, "source_user", executableAction.getSource());
                EvaluatorResult.ScheduleData scheduleData = evaluatorResult.getScheduleData();
                if (scheduleData != null) {
                    appendTextElement(document, root, "schedule_type", scheduleData.getType());
                    appendTextElement(document, root, "schedule_content", scheduleData.getContent());
                }
                Map<Integer, List<String>> primaryActionChain = evaluatorResult.getPrimaryActionChainAsMap();
                if (primaryActionChain == null || primaryActionChain.isEmpty()) {
                    return;
                }
                Element chainRoot = document.createElement("primary_action_chain");
                root.appendChild(chainRoot);
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
        };
    }

    private BlockContent buildPendingCompactBlock(String blockName, ExecutableAction executableAction, EvaluatorResult evaluatorResult, String input) {
        return new CommunicationBlockContent(blockName, BLOCK_SOURCE, BlockContent.Urgency.HIGH, CommunicationBlockContent.Projection.SUPPLY) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "state", "need_user_confirm");
                appendTextElement(document, root, "related_input", input);
                appendTextElement(document, root, "tendency", executableAction.getTendency());
                appendTextElement(document, root, "description", executableAction.getDescription());
                appendTextElement(document, root, "action_type", evaluatorResult.getType());
            }
        };
    }

    private BlockContent buildPendingAbstractBlock(String blockName, ExecutableAction executableAction, EvaluatorResult evaluatorResult, String input) {
        return new BlockContent(blockName, BLOCK_SOURCE, BlockContent.Urgency.HIGH) {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "state", "need_user_confirm");
                appendTextElement(document, root, "related_input", input);
                appendTextElement(document, root, "pending_tendency", executableAction.getTendency());
                appendTextElement(document, root, "summary", "exists pending action waiting for confirmation");
                appendTextElement(document, root, "action_type", evaluatorResult.getType());
            }
        };
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
                    watcherSelfTalk(action);
                    return Unit.INSTANCE;
                })
        );
        watcherRef[0] = watcher;
        actionScheduler.schedule(watcher);
    }

    private void watcherSelfTalk(ImmediateExecutableAction action) {
        try {
            cognitionCapability.initiateTurn("An action was finished, which uuid is " + action.getUuid(), action.getSource());
        } catch (Exception e) {
            log.warn("触发 immediate 行动完成自对话失败, actionUuid: {}", action.getUuid(), e);
        }
    }

    @Override
    public int order() {
        return 2;
    }

    private String trimInput(@NotNull String input) {
        input = input.trim();
        input = input.length() <= 100 ? input : input.substring(0, 100);
        return input;
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
