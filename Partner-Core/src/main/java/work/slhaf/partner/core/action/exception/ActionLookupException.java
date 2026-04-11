package work.slhaf.partner.core.action.exception;

import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;
import work.slhaf.partner.framework.agent.exception.ExceptionReport;

public class ActionLookupException extends AgentRuntimeException {

    private final String actionKey;
    private final String lookupTarget;

    public ActionLookupException(String message, String actionKey, String lookupTarget) {
        super(message);
        this.actionKey = actionKey;
        this.lookupTarget = lookupTarget;
    }

    public ActionLookupException(String message, String actionKey, String lookupTarget, Throwable cause) {
        super(message, cause);
        this.actionKey = actionKey;
        this.lookupTarget = lookupTarget;
    }

    @Override
    public ExceptionReport toReport() {
        ExceptionReport report = super.toReport();
        report.getExtra().put("actionKey", actionKey);
        report.getExtra().put("lookupTarget", lookupTarget);
        return report;
    }
}
