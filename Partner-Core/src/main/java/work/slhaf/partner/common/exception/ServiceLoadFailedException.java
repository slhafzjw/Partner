package work.slhaf.partner.common.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentLaunchFailedException;

public class ServiceLoadFailedException extends AgentLaunchFailedException {
    public ServiceLoadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceLoadFailedException(String message) {
        super(message);
    }
}
