package work.slhaf.partner.api.agent.factory.context;

import lombok.Data;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaModule;
import work.slhaf.partner.api.agent.factory.module.pojo.MetaSubModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.abstracts.AgentRunningSubModule;

import java.util.ArrayList;
import java.util.List;

@Data
public class ModuleFactoryContext {
    private List<MetaModule> agentModuleList = new ArrayList<>();
    private List<MetaSubModule> agentSubModuleList = new ArrayList<>();
}
