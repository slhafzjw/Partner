package work.slhaf.agent.module.modules.task;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.core.interaction.data.context.InteractionContext;
import work.slhaf.agent.core.interaction.module.InteractionModule;

@Data
@Slf4j
public class TaskScheduler implements InteractionModule {
    private static TaskScheduler taskScheduler;

    private TaskScheduler(){}

    public static TaskScheduler getInstance() {
        if (taskScheduler == null) {
            taskScheduler = new TaskScheduler();
            log.info("TaskScheduler注册完毕...");
        }

        return taskScheduler;
    }

    @Override
    public void execute(InteractionContext interactionContext) {

    }
}
