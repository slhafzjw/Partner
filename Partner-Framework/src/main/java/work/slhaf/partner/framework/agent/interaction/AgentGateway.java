package work.slhaf.partner.framework.agent.interaction;

import work.slhaf.partner.framework.agent.interaction.data.InputData;
import work.slhaf.partner.framework.agent.interaction.flow.RunningFlowContext;

public interface AgentGateway<I extends InputData, C extends RunningFlowContext> extends ResponseChannel, AutoCloseable {

    void launch();

    AgentGatewayRegistration registration();

    @Override
    default void register() {
        registration().register();
    }

    default void receive(I inputData) {
        C parsedContext = parseRunningFlowContext(inputData);
        AgentRuntime.INSTANCE.submit(parsedContext);
    }

    C parseRunningFlowContext(I inputData);

}
