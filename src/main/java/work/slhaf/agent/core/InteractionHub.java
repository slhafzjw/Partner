package work.slhaf.agent.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.InteractionModulesLoader;
import work.slhaf.agent.core.interaction.TaskCallback;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.interaction.data.InteractionInputData;
import work.slhaf.agent.core.memory.MemoryManager;
import work.slhaf.agent.core.module.CoreModel;
import work.slhaf.agent.modules.preprocess.PreprocessExecutor;
import work.slhaf.agent.modules.task.TaskScheduler;

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

    public void call(InteractionInputData inputData) throws IOException, ClassNotFoundException, InterruptedException {
        //预处理
        InteractionContext interactionContext = PreprocessExecutor.getInstance().execute(inputData);
        //加载模块
        List<InteractionModule> interactionModules = InteractionModulesLoader.getInstance().registerInteractionModules();
        for (InteractionModule interactionModule : interactionModules) {
            interactionModule.execute(interactionContext);
        }
        callback.onTaskFinished(interactionContext.getUserInfo(), interactionContext.getCoreResponse().getString("message"));
    }
}
