package work.slhaf.partner.module.modules.process;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.module.abstracts.AbstractAgentRunningModule;
import work.slhaf.partner.api.agent.factory.module.annotation.AgentModule;
import work.slhaf.partner.core.cognation.CognationCapability;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
@AgentModule(name = "postprocess_executor", order = 6)
public class PostprocessExecutor extends AbstractAgentRunningModule<PartnerRunningFlowContext> {

    private static final int POST_PROCESS_TRIGGER_ROLL_LIMIT = 36;

    @InjectCapability
    private CognationCapability cognationCapability;

    @Override
    public void execute(PartnerRunningFlowContext context) {
        boolean trigger = cognationCapability.getChatMessages().size() >= POST_PROCESS_TRIGGER_ROLL_LIMIT;
        context.getModuleContext().getExtraContext().put("post_process_trigger", trigger);
        log.debug("[PostprocessExecutor] 是否执行后处理: {}", trigger);
    }
}
