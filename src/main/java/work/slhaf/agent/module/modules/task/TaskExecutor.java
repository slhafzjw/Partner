package work.slhaf.agent.module.modules.task;

import lombok.Data;
import work.slhaf.agent.core.interaction.InteractionThreadPoolExecutor;

@Data
public class TaskExecutor {

    private static TaskExecutor taskExecutor;
    private InteractionThreadPoolExecutor executor;
    private TaskExecutor(){}

    public static TaskExecutor getInstance(){
        if (taskExecutor == null){
            taskExecutor = new TaskExecutor();
            taskExecutor.setExecutor(InteractionThreadPoolExecutor.getInstance());
        }
        return taskExecutor;
    }
}
