package work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts;


public abstract class AgentRunningSubModule<I, O> extends Module {

    public abstract O execute(I data);

}
