package work.slhaf.partner.module.modules.task;

import lombok.Data;
import lombok.EqualsAndHashCode;
import work.slhaf.partner.module.common.model.Model;
import work.slhaf.partner.module.common.model.ModelConstant;

@Data
public class TaskEvaluator  {
    private static TaskEvaluator taskEvaluator;

    private TaskEvaluator (){}

    public static TaskEvaluator getInstance() {
        if (taskEvaluator == null) {
            taskEvaluator = new TaskEvaluator();
        }
        return taskEvaluator;
    }

    protected String modelKey() {
        return "task_evaluator";
    }
}
