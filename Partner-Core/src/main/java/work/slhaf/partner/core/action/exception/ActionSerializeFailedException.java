package work.slhaf.partner.core.action.exception;

import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;

public class ActionSerializeFailedException extends AgentRuntimeException {
    public ActionSerializeFailedException(String message) {
        super(message);
    }

    public ActionSerializeFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
