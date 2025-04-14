package work.slhaf.agent.core.models.task;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.core.config.Config;
import work.slhaf.agent.core.models.common.Model;
import work.slhaf.agent.core.models.common.ModelConstant;

@EqualsAndHashCode(callSuper = true)
@Data
public class TaskTrigger extends Model {
    public static final String MODEL_KEY = "task_trigger";
    private static TaskTrigger taskTrigger;
    public static TaskTrigger initialize(Config config) {

        if (taskTrigger == null) {
            taskTrigger = new TaskTrigger();
            taskTrigger.setPrompt(ModelConstant.SLICE_EVALUATOR_PROMPT);
            setModel(config,taskTrigger, MODEL_KEY, taskTrigger.getPrompt());
        }

        return taskTrigger;
    }

}
