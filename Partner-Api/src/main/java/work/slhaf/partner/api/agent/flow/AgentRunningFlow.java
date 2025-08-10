package work.slhaf.partner.api.agent.flow;

import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.flow.entity.RunningFlowContext;
import work.slhaf.partner.api.agent.runtime.exception.GlobalExceptionHandler;

import java.util.List;

/**
 * Agent执行流程
 */
public class AgentRunningFlow {

    private AgentRunningFlow(){}

    public static void launch(List<MetaModule> moduleList, RunningFlowContext interactionContext){
        try {
            //流程执行启动，需考虑模块热插拔，可结合http调整模块启用情况，并序列化至本地或数据库中
        }catch (Exception e){
            GlobalExceptionHandler.INSTANCE.handle(e);
        }
    }
}
