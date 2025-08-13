package work.slhaf.partner.api.agent.runtime.interaction;

import work.slhaf.partner.api.agent.runtime.interaction.data.AgentInputData;
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentOutputData;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

public interface AgentGateway {

    void launch();

    <I extends AgentInputData, O extends AgentOutputData, C extends RunningFlowContext> AgentInteractionAdapter<I, O, C> adapter();
}
