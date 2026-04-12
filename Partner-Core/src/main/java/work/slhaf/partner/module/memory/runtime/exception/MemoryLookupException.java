package work.slhaf.partner.module.memory.runtime.exception;

import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ExceptionReport;

public class MemoryLookupException extends AgentRuntimeException {

    private final String lookupKey;
    private final String lookupTarget;

    public MemoryLookupException(String message, String lookupKey, String lookupTarget) {
        super(message);
        this.lookupKey = lookupKey;
        this.lookupTarget = lookupTarget;
    }

    public MemoryLookupException(String message, String lookupKey, String lookupTarget, Throwable cause) {
        super(message, cause);
        this.lookupKey = lookupKey;
        this.lookupTarget = lookupTarget;
    }

    @Override
    public ExceptionReport toReport() {
        ExceptionReport report = super.toReport();
        report.getExtra().put("lookupKey", lookupKey);
        report.getExtra().put("lookupTarget", lookupTarget);
        return report;
    }
}
