package work.slhaf.partner.api.agent.runtime.interaction.flow;

import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.runtime.exception.GlobalExceptionHandler;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

import java.util.List;

/**
 * Agent执行流程
 */
public class AgentRunningFlow<C extends RunningFlowContext> {

    public C launch(List<MetaModule> moduleList, C interactionContext){
        try {
            //流程执行启动
            for (MetaModule metaModule : moduleList) {
                metaModule.getInstance().execute(interactionContext);
            }
        }catch (Exception e){
            GlobalExceptionHandler.INSTANCE.handle(e);
        }
        return interactionContext;
    }
}
