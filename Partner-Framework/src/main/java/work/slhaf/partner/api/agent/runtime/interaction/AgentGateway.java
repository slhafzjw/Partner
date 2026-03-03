package work.slhaf.partner.api.agent.runtime.interaction;

import work.slhaf.partner.api.agent.runtime.interaction.data.AgentInputData;
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentOutputData;
import work.slhaf.partner.api.agent.runtime.interaction.flow.RunningFlowContext;

public interface AgentGateway<I extends AgentInputData, O extends AgentOutputData, C extends RunningFlowContext> {

    void launch();

    default void receive(I inputData) {
        O outputData = adapter().submit(inputData);
        send(outputData);
    }

    void send(O outputData);

    /**
     * 通过adapter提供的receive、send方法进行与客户端的交互行为
     *
     * @return adapter实例
     */
    AgentInteractionAdapter<I, O, C> adapter();
}
