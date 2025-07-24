package work.slhaf.partner.api.flow;

import work.slhaf.partner.api.entity.AgentContext;

/**
 * Agent执行流程
 */
public class AgentInteraction {

    private AgentInteraction(){}

    public static void launch(AgentContext context){
        //流程执行启动，需考虑模块热插拔，可结合http调整模块启用情况，并序列化至本地或数据库中
    }
}
