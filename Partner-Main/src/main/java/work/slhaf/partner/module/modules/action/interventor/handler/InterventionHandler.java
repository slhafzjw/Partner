package work.slhaf.partner.module.modules.action.interventor.handler;

import java.util.List;
import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore.ExecutorType;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput;
import work.slhaf.partner.module.modules.action.interventor.handler.entity.HandlerInput.HandlerInputData;

@Slf4j
@AgentSubModule
public class InterventionHandler extends AgentRunningSubModule<HandlerInput, Void> {

    @InjectCapability
    private ActionCapability actionCapability;

    @Override
    public Void execute(HandlerInput data) {
        ExecutorService executor = actionCapability.getExecutor(ExecutorType.VIRTUAL);
        executor.execute(() -> {
            log.debug("干预开始执行");
            List<HandlerInputData> dataList = data.getData();
            for (HandlerInputData inputData : dataList) {
                log.debug("干预操作: {}, 干预类型: {}",inputData.getTendency(),inputData.getType());
                
            }
        });
        return null;
    }

}
