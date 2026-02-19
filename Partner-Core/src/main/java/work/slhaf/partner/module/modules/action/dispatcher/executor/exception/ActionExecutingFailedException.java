package work.slhaf.partner.module.modules.action.dispatcher.executor.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentRuntimeException;

public class ActionExecutingFailedException extends AgentRuntimeException {

    public ActionExecutingFailedException(String message) {
        super(message);
    }

    public ActionExecutingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
