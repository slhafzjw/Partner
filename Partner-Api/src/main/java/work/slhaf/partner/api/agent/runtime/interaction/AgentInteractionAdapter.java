package work.slhaf.partner.api.agent.runtime.interaction;

import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentInputData;
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentOutputData;
import work.slhaf.partner.api.agent.runtime.interaction.flow.AgentRunningFlow;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

import java.util.List;

public abstract class AgentInteractionAdapter<I extends AgentInputData, O extends AgentOutputData, C extends RunningFlowContext> {

    private static AgentInteractionAdapter<?,?,?> INSTANCE;

    protected AgentRunningFlow<C> agentRunningFlow = new AgentRunningFlow<>();
    protected List<MetaModule> moduleList = AgentConfigManager.INSTANCE.getModuleList();

    public void receive(I inputData) {
        C finalInputData = parseInputData(inputData);
        C outputContext = agentRunningFlow.launch(moduleList, finalInputData);
        O outputData = parseOutputData(outputContext);
        send(outputData);
    }

    protected abstract O parseOutputData(C outputContext);

    protected abstract C parseInputData(I inputData);

    public abstract void send(O outputData);

    public static <I extends AgentInputData, O extends AgentOutputData, C extends RunningFlowContext> AgentInteractionAdapter<I, O, C> getInstance() {
        @SuppressWarnings("unchecked")
        AgentInteractionAdapter<I, O, C> instance = (AgentInteractionAdapter<I, O, C>) INSTANCE;
        return instance;
    }

    public static <I extends AgentInputData, O extends AgentOutputData, C extends RunningFlowContext> void setInstance(AgentInteractionAdapter<I, O, C> instance) {
        INSTANCE = instance;
    }
}
