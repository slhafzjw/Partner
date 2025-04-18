package work.slhaf.agent.modules.task;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.common.config.Config;
import work.slhaf.agent.common.config.ModelConfig;
import work.slhaf.agent.common.model.Model;
import work.slhaf.agent.common.model.ModelConstant;

import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Data
public class TaskEvaluator extends Model {
    public static final String MODEL_KEY = "task_evaluator";
    private static TaskEvaluator taskEvaluator;

    private TaskEvaluator (){}

    public static TaskEvaluator getInstance() throws IOException, ClassNotFoundException {
        if (taskEvaluator == null) {
            taskEvaluator = new TaskEvaluator();
            setModel(Config.getConfig(),taskEvaluator,MODEL_KEY,ModelConstant.TASK_EVALUATOR_PROMPT);
        }
        return taskEvaluator;
    }
}
