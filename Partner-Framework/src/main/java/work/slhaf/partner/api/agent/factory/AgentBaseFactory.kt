package work.slhaf.partner.api.agent.factory;

import work.slhaf.partner.api.agent.factory.context.AgentRegisterContext;

public abstract class AgentBaseFactory {
    abstract void execute(AgentRegisterContext context);
}
