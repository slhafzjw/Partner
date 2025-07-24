package work.slhaf.partner.api;

import work.slhaf.partner.api.entity.AgentContext;
import work.slhaf.partner.api.factory.AgentRegisterFactory;
import work.slhaf.partner.api.flow.AgentInteraction;

/**
 * Agent启动类
 */
public class Agent {

    public static void run(Class<?> clazz) {
        AgentContext context = AgentRegisterFactory.launch(clazz.getPackage().getName());
        AgentInteraction.launch(context);
    }

}
