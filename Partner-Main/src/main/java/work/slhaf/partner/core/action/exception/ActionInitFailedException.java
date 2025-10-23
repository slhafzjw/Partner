package work.slhaf.partner.core.action.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentLaunchFailedException;

public class ActionInitFailedException extends AgentLaunchFailedException {
    public ActionInitFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ActionInitFailedException(String message) {
        super(message);
    }
}
