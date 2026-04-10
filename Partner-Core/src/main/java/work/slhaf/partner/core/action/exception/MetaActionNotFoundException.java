package work.slhaf.partner.core.action.exception;

import work.slhaf.partner.framework.agent.exception.deprecated.AgentRuntimeException;

public class MetaActionNotFoundException extends AgentRuntimeException {
    public MetaActionNotFoundException(String message) {
        super(message);
    }

    public MetaActionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
