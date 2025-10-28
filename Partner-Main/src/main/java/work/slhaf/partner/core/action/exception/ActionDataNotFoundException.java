package work.slhaf.partner.core.action.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentRuntimeException;

public class ActionDataNotFoundException extends AgentRuntimeException {
    public ActionDataNotFoundException(String message) {
        super(message);
    }

    public ActionDataNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
