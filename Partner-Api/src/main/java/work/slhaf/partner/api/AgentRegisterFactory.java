package work.slhaf.partner.api;

import work.slhaf.partner.api.common.entity.AgentRegisterContext;

public class AgentRegisterFactory {

    private AgentRegisterContext context = new AgentRegisterContext();

    private AgentRegisterFactory(){}

    public static void launch(){
        //TODO 通过调用module与capability的注册逻辑，完成完整的注册过程，需要考虑hook机制
        AgentRegisterFactory factory = new AgentRegisterFactory();
    }

}
