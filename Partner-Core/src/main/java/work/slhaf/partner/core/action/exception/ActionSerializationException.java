package work.slhaf.partner.core.action.exception;

import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ExceptionReport;

public class ActionSerializationException extends AgentRuntimeException {

    private final String actionName;
    private final String baseDir;
    private final String fileExt;
    private final String stage;

    public ActionSerializationException(
            String message,
            @Nullable String actionName,
            @Nullable String baseDir,
            @Nullable String fileExt,
            String stage
    ) {
        super(message);
        this.actionName = actionName;
        this.baseDir = baseDir;
        this.fileExt = fileExt;
        this.stage = stage;
    }

    public ActionSerializationException(
            String message,
            @Nullable String actionName,
            @Nullable String baseDir,
            @Nullable String fileExt,
            String stage,
            Throwable cause
    ) {
        super(message, cause);
        this.actionName = actionName;
        this.baseDir = baseDir;
        this.fileExt = fileExt;
        this.stage = stage;
    }

    @Override
    public ExceptionReport toReport() {
        ExceptionReport report = super.toReport();
        report.getExtra().put("stage", stage);
        if (actionName != null) {
            report.getExtra().put("actionName", actionName);
        }
        if (baseDir != null) {
            report.getExtra().put("baseDir", baseDir);
        }
        if (fileExt != null) {
            report.getExtra().put("fileExt", fileExt);
        }
        return report;
    }
}
