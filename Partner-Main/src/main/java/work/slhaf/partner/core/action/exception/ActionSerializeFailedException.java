package work.slhaf.partner.core.action.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentRuntimeException;

public class ActionSerializeFailedException extends AgentRuntimeException {
    public ActionSerializeFailedException(String message) {
        super(message);
    }

    public ActionSerializeFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
