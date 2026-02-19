package work.slhaf.partner.module.modules.perceive.selector;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.core.perceive.pojo.User;
import work.slhaf.partner.module.common.module.PreRunningAbstractAgentModuleAbstract;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Setter
@AgentModule(name = "perceive_selector",order = 2)
public class PerceiveSelector extends PreRunningAbstractAgentModuleAbstract {

    @InjectCapability
    private PerceiveCapability perceiveCapability;

    @Override
    public void doExecute(PartnerRunningFlowContext context) {
    }

    @Override
    protected Map<String, String> getPromptDataMap(PartnerRunningFlowContext context) {
        HashMap<String, String> map = new HashMap<>();
        User user = perceiveCapability.getUser(context.getUserId());
        map.put("[关系] <你与最新聊天用户的关系>", user.getRelation());
        map.put("[态度] <你对于最新聊天用户的态度>", user.getAttitude().toString());
        map.put("[印象] <你对于最新聊天用户的印象>", user.getImpressions().toString());
        map.put("[静态记忆] <你关于最新聊天用户的静态记忆>", user.getStaticMemory().toString());
        return map;
    }

    @Override
    public String moduleName() {
        return "[感知模块]";
    }
}
