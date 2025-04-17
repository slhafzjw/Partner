package work.slhaf.agent.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.core.interation.InteractionModulesLoader;
import work.slhaf.agent.core.interation.TaskCallback;
import work.slhaf.agent.core.interation.data.InteractionInputData;
import work.slhaf.agent.core.model.CoreModel;
import work.slhaf.agent.modules.memory.MemoryManager;
import work.slhaf.agent.modules.task.TaskScheduler;
import work.slhaf.module.InteractionModule;

import java.io.IOException;
import java.util.List;

@Data
@Slf4j
public class InteractionHub {

    private static InteractionHub interactionHub;

    private TaskCallback callback;

    private CoreModel coreModel;
    private MemoryManager memoryManager;
    private TaskScheduler taskScheduler;

    public static InteractionHub initialize() throws IOException {
        if (interactionHub == null) {
            interactionHub = new InteractionHub();
            log.info("InteractionHub注册完毕...");
        }
        return interactionHub;
    }

    public void call(InteractionInputData inputData) throws IOException {
        List<InteractionModule> interactionModules = InteractionModulesLoader.registerInteractionModules();

        callback.onTaskFinished(null, null);
    }
}
