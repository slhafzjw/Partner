package work.slhaf.partner.api.agent.factory.module.abstracts;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractAgentSubModule<I, O> extends AbstractAgentModule {

    public abstract O execute(I data);

}
