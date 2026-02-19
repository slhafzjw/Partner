package work.slhaf.partner.api.agent.factory.module.abstracts;

import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentRunningModule;
import work.slhaf.partner.api.agent.factory.module.annotation.BeforeExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.CoreModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

/**
 * 流程执行模块基类
 */
@Slf4j
public abstract class AbstractAgentRunningModule<C extends RunningFlowContext> extends AbstractAgentModule {
    public abstract void execute(C context);

    @BeforeExecute
    private void beforeLog() {
        log.debug("[{}] 模块执行开始...", getModuleName());
    }

    @AfterExecute
    private void afterLog() {
        log.debug("[{}] 模块执行结束...", getModuleName());
    }

    private String getModuleName(){
        if (this.getClass().isAnnotationPresent(AgentRunningModule.class)) {
            return this.getClass().getAnnotation(AgentRunningModule.class).name();
        } else if (this.getClass().isAnnotationPresent(CoreModule.class)) {
            return CoreModule.class.getAnnotation(AgentRunningModule.class).name();
        }else {
            return "Unknown AbstractAgentModule";
        }
    }
}
