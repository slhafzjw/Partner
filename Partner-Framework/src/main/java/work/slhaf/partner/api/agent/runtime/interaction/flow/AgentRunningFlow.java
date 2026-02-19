package work.slhaf.partner.api.agent.runtime.interaction.flow;

import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.runtime.exception.AgentRuntimeException;
import work.slhaf.partner.api.agent.runtime.exception.GlobalExceptionHandler;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Agent执行流程
 */
public class AgentRunningFlow<C extends RunningFlowContext> {

    public C launch(Map<Integer, List<MetaModule>> modules, C interactionContext) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            //流程执行启动
            for (Map.Entry<Integer, List<MetaModule>> entry : modules.entrySet()) {
                List<Future<?>> futures = new ArrayList<>();
                List<MetaModule> moduleList = entry.getValue();
                for (MetaModule module : moduleList) {
                    Future<?> future = executor.submit(() -> {
                        module.getInstance().execute(interactionContext);
                    });
                    futures.add(future);
                }
                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        boolean exit = GlobalExceptionHandler.INSTANCE.handle(e);
                        if (exit) throw new AgentRuntimeException("Agent执行出错!", e);
                        interactionContext.getErrMsg().add(e.getLocalizedMessage());
                    }
                }
            }
            interactionContext.setOk(1);
        } catch (Exception e) {
            interactionContext.setOk(0);
            interactionContext.getErrMsg().add(e.getLocalizedMessage());
        }
        return interactionContext;
    }
}
