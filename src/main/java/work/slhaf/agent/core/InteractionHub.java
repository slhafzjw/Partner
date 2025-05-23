package work.slhaf.agent.core;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.exception_handler.GlobalExceptionHandler;
import work.slhaf.agent.common.exception_handler.pojo.GlobalException;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.InteractionModulesLoader;
import work.slhaf.agent.core.interaction.TaskCallback;
import work.slhaf.agent.core.interaction.data.InteractionContext;
import work.slhaf.agent.core.interaction.data.InteractionInputData;
import work.slhaf.agent.core.module.CoreModel;
import work.slhaf.agent.modules.preprocess.PreprocessExecutor;
import work.slhaf.agent.modules.task.TaskScheduler;

import java.io.IOException;
import java.util.List;

@Data
@Slf4j
public class InteractionHub {

    private static InteractionHub interactionHub;

    @ToString.Exclude
    private TaskCallback callback;
    private CoreModel coreModel;
    private TaskScheduler taskScheduler;
    private List<InteractionModule> interactionModules;

    public static InteractionHub initialize() throws IOException {
        if (interactionHub == null) {
            interactionHub = new InteractionHub();
            //加载模块
            interactionHub.setInteractionModules(InteractionModulesLoader.getInstance().registerInteractionModules());
            log.info("InteractionHub注册完毕...");
        }
        return interactionHub;
    }

    public void call(InteractionInputData inputData) throws IOException, ClassNotFoundException, InterruptedException {
        InteractionContext interactionContext = PreprocessExecutor.getInstance().execute(inputData);
        try {
            //预处理
            for (InteractionModule interactionModule : interactionModules) {
                interactionModule.execute(interactionContext);
            }
        } catch (GlobalException e) {
            GlobalExceptionHandler.writeExceptionState(e.getData());
            interactionContext.getCoreResponse().put("text", "[ERROR] " + e.getMessage());
        } finally {
            callback.onTaskFinished(interactionContext.getUserInfo(), interactionContext.getCoreResponse().getString("text"));
            InteractionContext.clearUp();
        }
    }
}
