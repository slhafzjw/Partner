package work.slhaf.agent.modules.task.data;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class TaskData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    /**
     * 执行类别: 即时任务/定时任务
     */
    private String executeType;

    /**
     * cron表达式，仅定时任务需要填写
     */
    private String cronStr;
    private String comment;

    public static class Constant {
        public static final String CURRENT = "current";
        public static final String SCHEDULE = "schedule";
    }
}
