package work.slhaf.partner.api.agent.factory.module.abstracts;


import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.module.annotation.AfterExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.api.agent.factory.module.annotation.BeforeExecute;
import work.slhaf.partner.api.agent.factory.module.annotation.CoreModule;

@Slf4j
public abstract class AgentRunningSubModule<I, O> extends Module {

    public abstract O execute(I data);

}
