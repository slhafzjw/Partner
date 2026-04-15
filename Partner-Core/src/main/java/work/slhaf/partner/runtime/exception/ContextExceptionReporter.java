package work.slhaf.partner.runtime.exception;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import work.slhaf.partner.core.cognition.BlockContent;
import work.slhaf.partner.core.cognition.CognitionCapability;
import work.slhaf.partner.core.cognition.ContextBlock;
import work.slhaf.partner.framework.agent.exception.AgentException;
import work.slhaf.partner.framework.agent.exception.ExceptionReport;
import work.slhaf.partner.framework.agent.exception.ExceptionReporter;
import work.slhaf.partner.framework.agent.factory.capability.annotation.InjectCapability;
import work.slhaf.partner.framework.agent.factory.component.annotation.AgentComponent;
import work.slhaf.partner.framework.agent.factory.component.annotation.Init;

import java.util.Set;

@AgentComponent
public class ContextExceptionReporter implements ExceptionReporter {

    public static final String REPORTER_NAME = "context-reporter";

    @InjectCapability
    private CognitionCapability cognitionCapability;

    @Init
    public void init() {
        register();
    }

    @Override
    @NotNull
    public String reporterName() {
        return REPORTER_NAME;
    }

    @Override
    public void report(@NotNull AgentException exception) {
        ExceptionReport report = exception.toReport();
        cognitionCapability.contextWorkspace().register(new ContextBlock(
                buildExceptionReportBlock(report),
                Set.of(ContextBlock.FocusedDomain.COGNITION),
                10,
                10,
                0
        ));
    }

    private @NotNull BlockContent buildExceptionReportBlock(ExceptionReport report) {
        return new BlockContent("agent-runtime-exception", "context-exception-reporter") {
            @Override
            protected void fillXml(@NotNull Document document, @NotNull Element root) {
                appendTextElement(document, root, "exception_info", report.toString());
            }
        };
    }
}
