package work.slhaf.partner.core.action.exception;

import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;

public class ActionDataNotFoundException extends AgentRuntimeException {
    public ActionDataNotFoundException(String message) {
        super(message);
    }

    public ActionDataNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
