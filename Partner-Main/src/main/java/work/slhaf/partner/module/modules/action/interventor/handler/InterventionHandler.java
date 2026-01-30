package work.slhaf.partner.module.modules.action.interventor.handler;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore.ExecutorType;
import work.slhaf.partner.module.modules.action.interventor.entity.MetaIntervention;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.ExecutingInterventionData;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.InterventionData;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.PreparedInterventionData;

import java.util.List;
import java.util.concurrent.ExecutorService;

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
                    actionCapability.handleInterventions(interventions, data.getRecord());
                } else if (interventionData instanceof PreparedInterventionData data) {
                    actionCapability.handleInterventions(interventions, data.getActionData());
                }

            });
        });
    }
}
