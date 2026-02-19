package work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts;


import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.BeforeExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.CoreModule;

@Slf4j
public abstract class AgentRunningSubModule<I, O> extends Module {

    public abstract O execute(I data);


    @BeforeExecute
    private void beforeLog() {
        log.debug("[{}] 模块执行开始...", getModuleName());
    }

    @AfterExecute
    private void afterLog() {
        log.debug("[{}] 模块执行结束...", getModuleName());
    }

    private String getModuleName(){
        if (this.getClass().isAnnotationPresent(AgentModule.class)) {
            return this.getClass().getAnnotation(AgentModule.class).name();
        } else if (this.getClass().isAnnotationPresent(CoreModule.class)) {
            return CoreModule.class.getAnnotation(AgentModule.class).name();
        }else {
            return "Unknown Module";
        }
    }
}
