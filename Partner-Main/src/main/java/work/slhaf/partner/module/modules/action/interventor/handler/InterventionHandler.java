package work.slhaf.partner.module.modules.action.interventor.handler;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore.ExecutorType;
import work.slhaf.partner.core.action.ActionCore.PhaserRecord;
import work.slhaf.partner.core.action.entity.ActionData;
import work.slhaf.partner.core.action.entity.ActionData.ActionStatus;
import work.slhaf.partner.core.action.entity.MetaAction;
import work.slhaf.partner.module.modules.action.interventor.entity.InterventionType;
import work.slhaf.partner.module.modules.action.interventor.entity.MetaIntervention;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.ExecutingInterventionData;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.InterventionData;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.PreparedInterventionData;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

@Slf4j
@AgentSubModule
public class InterventionHandler extends AgentRunningSubModule<HandlerInput, Void> {

    @InjectCapability
    private ActionCapability actionCapability;

    /**
     * 针对‘行动干预’做出处理
     *
     * @param data 行动干预输入
     * @return 无返回值
     */
    @Override
    public Void execute(HandlerInput data) {
        ExecutorService executor = actionCapability.getExecutor(ExecutorType.VIRTUAL);
        handle(data.getExecuting(), executor);
        handle(data.getPrepared(), executor);
        return null;
    }

    private void handle(List<? extends InterventionData> executing, ExecutorService executor) {
        executor.execute(() -> {
            executing.forEach(interventionData -> {
                // 干预逻辑一致
                // 同步操作不同
                // HandlerAction 抽取同步逻辑
                // 此处进行遍历 intervention
                // 根据Intervention类型进行分发

                List<MetaIntervention> interventions = interventionData.getInterventions();
                if (interventionData instanceof ExecutingInterventionData data) {
                    handleInterventions(interventions, data.getRecord());
                } else if (interventionData instanceof PreparedInterventionData data) {
                    handleInterventions(interventions, data.getActionData());
                }

            });
        });
    }

    private <T> void handleInterventions(List<MetaIntervention> interventions, T data) {
        // 加载数据
        Phaser phaser = null;
        ActionData actionData = switch (data) {
            case PhaserRecord record -> {
                phaser = record.phaser();
                yield record.actionData();
            }
            case ActionData tempData -> tempData;
            default -> null;
        };
        if (actionData == null) {
            return;
        }

        // 加锁确保同步
        synchronized (actionData) {
            applyInterventions(interventions, actionData, phaser);
        }
    }

    private void applyInterventions(List<MetaIntervention> interventions, ActionData actionData, Phaser phaser) {
        boolean rebuildCleanTag = false;

        interventions.sort(Comparator.comparingInt(MetaIntervention::getOrder));

        for (MetaIntervention intervention : interventions) {
            List<MetaAction> actions = intervention.getActions()
                    .stream()
                    .map(actionKey -> actionCapability.loadMetaAction(actionKey))
                    .toList();

            switch (intervention.getType()) {
                case InterventionType.APPEND -> handleAppend(actionData, intervention.getOrder(), actions);
                case InterventionType.INSERT -> handleInsert(actionData, intervention.getOrder(), actions, phaser);
                case InterventionType.DELETE -> handleDelete(actionData, intervention.getOrder(), actions);
                case InterventionType.CANCEL -> handleCancel(actionData);
                case InterventionType.REBUILD -> {
                    if (!rebuildCleanTag) {
                        cleanActionData(actionData);
                        rebuildCleanTag = true;
                    }
                    handleRebuild(actionData, intervention.getOrder(), actions);
                }
            }
        }

    }

    /**
     * 在未进入执行阶段的行动单元组新增新的行动
     */
    private void handleAppend(ActionData actionData, int order, List<MetaAction> actions) {
        if (order <= actionData.getExecutingStage()) return;

        actionData.getActionChain().put(order, actions);
    }

    /**
     * 在未进入执行阶段和正处于行动阶段的行动单元组插入新的行动, 如果插入位置正处于执行阶段, 则启动执行线程, 通过 Phaser 确保同步
     */
    private void handleInsert(ActionData actionData, int order, List<MetaAction> actions, Phaser phaser) {
        if (order < actionData.getExecutingStage()) return;

        phaser.register();
        try {
            Map<Integer, List<MetaAction>> actionChain = actionData.getActionChain();
            actionChain.put(order, actions);

            if (order == actionData.getExecutingStage()) {
                ExecutorService virtualExecutor = actionCapability.getExecutor(ExecutorType.VIRTUAL);
                ExecutorService platformExecutor = actionCapability.getExecutor(ExecutorType.PLATFORM);
                ExecutorService executor;
                phaser.bulkRegister(actions.size());

                for (MetaAction action : actions) {
                    executor = action.isIo() ? virtualExecutor : platformExecutor;
                    executor.execute(() -> {
                        try {
                            actionCapability.execute(action);
                        } finally {
                            phaser.arriveAndDeregister();
                        }
                    });
                }

            }
        } finally {
            phaser.arriveAndDeregister();
        }
    }

    private void handleDelete(ActionData actionData, int order, List<MetaAction> actions) {
        if (order <= actionData.getExecutingStage()) return;

        Map<Integer, List<MetaAction>> actionChain = actionData.getActionChain();
        if (actionChain.containsKey(order)) {
            actionChain.get(order).removeAll(actions);
            if (actionChain.get(order).isEmpty()) {
                actionChain.remove(order);
            }
        }
    }

    private void handleCancel(ActionData actionData) {
        actionData.setStatus(ActionStatus.FAILED);
        actionData.setResult("行动取消");
    }

    private void handleRebuild(ActionData actionData, int order, List<MetaAction> actions) {
        Map<Integer, List<MetaAction>> actionChain = actionData.getActionChain();
        actionChain.put(order, actions);
    }

    private void cleanActionData(ActionData actionData) {
        actionData.getActionChain().clear();
        actionData.setExecutingStage(0);
        actionData.setStatus(ActionStatus.PREPARE);
        actionData.getHistory().clear();
    }

}
