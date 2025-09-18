package work.slhaf.partner.module.modules.task;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

@Data
@Slf4j
public class TaskScheduler   {
  private static TaskScheduler taskScheduler;

  private TaskScheduler() {
  }

  public static TaskScheduler getInstance() {
    if (taskScheduler == null) {
      taskScheduler = new TaskScheduler();
      log.info("TaskScheduler注册完毕...");
    }

    return taskScheduler;
  }

  public void execute(PartnerRunningFlowContext runningFlowContext) {

  }
}
