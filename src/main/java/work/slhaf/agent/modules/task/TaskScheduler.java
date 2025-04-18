package work.slhaf.agent.modules.task;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.core.interaction.InteractionModule;
import work.slhaf.agent.core.interaction.data.InteractionContext;

import java.io.IOException;

@Data
@Slf4j
public class TaskScheduler implements InteractionModule {
    private static TaskScheduler taskScheduler;

    private TaskScheduler(){}

    public static TaskScheduler getInstance() throws IOException, ClassNotFoundException {
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
