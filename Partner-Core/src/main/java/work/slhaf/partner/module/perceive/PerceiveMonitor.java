package work.slhaf.partner.module.perceive;

import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.CommunicationBlockContent;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Setter
public class PerceiveMonitor extends AbstractAgentModule.Running<PartnerRunningFlowContext> {
    @InjectCapability
    private PerceiveCapability perceiveCapability;
    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Override
    public void execute(PartnerRunningFlowContext context) {
        String lastInteractTime = perceiveCapability.refreshInteract();
        ContextBlock block = new ContextBlock(
                new CommunicationBlockContent(
                        "environment_perceive_info",
                        "perceive_monitor",
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
