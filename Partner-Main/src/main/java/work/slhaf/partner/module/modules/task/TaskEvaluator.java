package work.slhaf.partner.module.modules.task;

import lombok.Data;

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
