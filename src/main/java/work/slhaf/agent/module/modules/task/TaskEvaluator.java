package work.slhaf.agent.module.modules.task;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.agent.module.common.Model;
import work.slhaf.agent.module.common.ModelConstant;

@EqualsAndHashCode(callSuper = true)
@Data
public class TaskEvaluator extends Model {
    private static TaskEvaluator taskEvaluator;

    private TaskEvaluator (){}

    public static TaskEvaluator getInstance() {
        if (taskEvaluator == null) {
            taskEvaluator = new TaskEvaluator();
            setModel(taskEvaluator, ModelConstant.Prompt.SCHEDULE,true);
        }
        return taskEvaluator;
    }

    @Override
    protected String modelKey() {
        return "task_evaluator";
    }
}
