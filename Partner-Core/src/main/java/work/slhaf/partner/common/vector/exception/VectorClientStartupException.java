package work.slhaf.partner.common.vector.exception;

import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.framework.agent.exception.AgentStartupException;
import work.slhaf.partner.framework.agent.exception.ExceptionReport;

public class VectorClientStartupException extends AgentStartupException {

    private final String clientType;
    private final String phase;
    private final String target;

    public VectorClientStartupException(String message, String clientType, String phase, @Nullable String target) {
        super(message, "vector-client-registry");
        this.clientType = clientType;
        this.phase = phase;
        this.target = target;
    }

    public VectorClientStartupException(String message, String clientType, String phase, @Nullable String target, Throwable cause) {
        super(message, "vector-client-registry", cause);
        this.clientType = clientType;
        this.phase = phase;
        this.target = target;
    }

    @Override
    public ExceptionReport toReport() {
        ExceptionReport report = super.toReport();
        report.getExtra().put("clientType", clientType);
        report.getExtra().put("phase", phase);
        if (target != null) {
            report.getExtra().put("target", target);
        }
        return report;
    }
}
