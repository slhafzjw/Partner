package work.slhaf.partner.api.agent.runtime.interaction;

import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.runtime.config.AgentConfigManager;
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentInputData;
import work.slhaf.partner.api.agent.runtime.interaction.data.AgentOutputData;
import work.slhaf.partner.api.agent.runtime.interaction.flow.AgentRunningFlow;
import work.slhaf.partner.api.agent.runtime.interaction.flow.entity.RunningFlowContext;

import java.util.List;

public abstract class AgentInteractionAdapter<I extends AgentInputData, O extends AgentOutputData, C extends RunningFlowContext> {

    protected AgentRunningFlow<C> agentRunningFlow = new AgentRunningFlow<>();
    protected List<MetaModule> moduleList = AgentConfigManager.INSTANCE.getModuleList();

    public C call(C finalInputData){
        return agentRunningFlow.launch(moduleList, finalInputData);
    }

    protected abstract O parseOutputData(C outputContext);

    protected abstract C parseInputData(I inputData);

}
