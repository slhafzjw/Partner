package work.slhaf.partner.module.perceive;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.CommunicationBlockContent;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class PerceiveMonitor extends AbstractAgentModule.Running<PartnerRunningFlowContext> {
    @InjectCapability
    private PerceiveCapability perceiveCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public void execute(@NotNull PartnerRunningFlowContext context) {
        String lastInteractTime = perceiveCapability.refreshInteract();
        ContextBlock block = new ContextBlock(
                new CommunicationBlockContent(
                        "environment_perceive_info",
                        "perceive_monitor",
                        BlockContent.Urgency.NORMAL,
                        CommunicationBlockContent.Projection.SUPPLY
                ) {
                    @Override
                    protected void fillXml(@NotNull Document document, @NotNull Element root) {
                        appendTextElement(document, root, "last_interact_time", lastInteractTime);
                        appendTextElement(document, root, "current_time", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    }
                },
                Set.of(ContextBlock.VisibleDomain.COMMUNICATION),
                100,
                30,
                0
        );
        cognitionCapability.contextWorkspace().register(block);
    }

    @Override
    public int order() {
        return 1;
    }

}
