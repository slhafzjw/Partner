package work.slhaf.partner.core.action.exception;

import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.framework.agent.exception.AgentStartupException;
import work.slhaf.partner.framework.agent.exception.ExceptionReport;

public class ActionInfrastructureStartupException extends AgentStartupException {

    private final String component;
    private final String path;
    private final String command;

    public ActionInfrastructureStartupException(String message, String component, @Nullable String path, @Nullable String command) {
        super(message, "action-core");
        this.component = component;
        this.path = path;
        this.command = command;
    }

    public ActionInfrastructureStartupException(String message, String component, @Nullable String path, @Nullable String command, Throwable cause) {
        super(message, "action-core", cause);
        this.component = component;
        this.path = path;
        this.command = command;
    }

    @Override
    public ExceptionReport toReport() {
        ExceptionReport report = super.toReport();
        report.getExtra().put("component", component);
        if (path != null) {
            report.getExtra().put("path", path);
        }
        if (command != null) {
            report.getExtra().put("command", command);
        }
        return report;
    }
}
