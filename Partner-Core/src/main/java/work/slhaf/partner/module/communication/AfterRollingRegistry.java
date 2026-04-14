package work.slhaf.partner.module.communication;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.core.action.ActionCapability;
import work.slhaf.partner.core.action.ActionCore;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ExceptionReporterHandler;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

@EqualsAndHashCode(callSuper = true)
@Slf4j
public class AfterRollingRegistry extends AbstractAgentModule.Standalone {

    private final CopyOnWriteArrayList<AfterRolling> consumers = new CopyOnWriteArrayList<>();
    @InjectCapability
    private ActionCapability actionCapability;
    private ExecutorService executor;

    @Init
    public void init() {
        executor = actionCapability.getExecutor(ActionCore.ExecutorType.VIRTUAL);
    }

    public void register(AfterRolling consumer) {
        if (consumers.contains(consumer)) {
            return;
        }
        consumers.add(consumer);
    }

    public void trigger(RollingResult result) {
        if (consumers.isEmpty()) {
            return;
        }
        executor.execute(() -> {
            for (AfterRolling consumer : List.copyOf(consumers)) {
                try {
                    consumer.consume(result);
                } catch (Exception e) {
                    ExceptionReporterHandler.INSTANCE.report(new AgentRuntimeException("after-rolling consumer occurred exception: " + consumer.getClass().getSimpleName(), e));
                }
            }
        });
    }
}
