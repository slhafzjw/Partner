package work.slhaf.partner.common.vector.exception;

import org.jetbrains.annotations.Nullable;
import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ExceptionReport;

public class VectorClientExecutionException extends AgentRuntimeException {

    private final String clientType;
    private final String phase;
    private final String target;

    public VectorClientExecutionException(String message, String clientType, String phase, @Nullable String target) {
        super(message);
        this.clientType = clientType;
        this.phase = phase;
        this.target = target;
    }

    public VectorClientExecutionException(String message, String clientType, String phase, @Nullable String target, Throwable cause) {
        super(message, cause);
        this.clientType = clientType;
        this.phase = phase;
        this.target = target;
    }

    public String getClientType() {
        return clientType;
    }

    public String getPhase() {
        return phase;
    }

    public String getTarget() {
        return target;
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
