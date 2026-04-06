package work.slhaf.partner.core.action.exception;

import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;

public class ActionLoadFailedException extends AgentRuntimeException {
    public ActionLoadFailedException(String message) {
        super(message);
    }

    public ActionLoadFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
