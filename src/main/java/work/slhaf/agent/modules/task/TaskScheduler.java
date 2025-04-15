package work.slhaf.agent.modules.task;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class TaskScheduler extends Model {
    public static final String MODEL_KEY = "task_trigger";
    private static TaskScheduler taskScheduler;
    public static TaskScheduler initialize(Config config) {

        if (taskScheduler == null) {
            taskScheduler = new TaskScheduler();
            taskScheduler.setPrompt(ModelConstant.SLICE_EVALUATOR_PROMPT);
            setModel(config, taskScheduler, MODEL_KEY, taskScheduler.getPrompt());
            log.info("TaskScheduler注册完毕...");
        }

        return taskScheduler;
    }

}
