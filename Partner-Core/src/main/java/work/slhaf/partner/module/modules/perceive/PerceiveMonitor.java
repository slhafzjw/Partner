package work.slhaf.partner.module.modules.perceive;

import lombok.AllArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.api.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.api.agent.factory.component.abstracts.AbstractAgentModule;
import work.slhaf.partner.api.agent.runtime.interaction.flow.ContextBlock;
import work.slhaf.partner.core.perceive.PerceiveCapability;
import work.slhaf.partner.runtime.interaction.data.context.PartnerRunningFlowContext;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Setter
public class PerceiveMonitor extends AbstractAgentModule.Running<PartnerRunningFlowContext> {
    @InjectCapability
    private PerceiveCapability perceiveCapability;

    @Override
    public void execute(PartnerRunningFlowContext context) {
        context.getContextBlocks().add(new PerceiveBlock(perceiveCapability.refreshInteract()));
    }

    @Override
    public int order() {
        return 1;
    }

    @AllArgsConstructor
    private static class PerceiveBlock extends ContextBlock {

        private String lastInteractTime;

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        @NotNull
        public Type getType() {
            return Type.CONTEXT;
        }

        @Override
        @NotNull
        public String getBlockName() {
            return "perceive_info";
        }

        @Override
        @NotNull
        public String getSource() {
            return "perceive_monitor";
        }

        @Override
        protected void fillXml(@NotNull Document document, @NotNull Element root) {
            Element lastInteractTime = document.createElement("last_interact_time");
            lastInteractTime.setTextContent(this.lastInteractTime);
            root.appendChild(lastInteractTime);

            Element currentTime = document.createElement("current_time");
            currentTime.setTextContent(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            root.appendChild(currentTime);
        }
    }
}
