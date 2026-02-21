package work.slhaf.partner.api.agent.factory.component.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentRuntimeException;

public class ProxiedModuleRunningException extends AgentRuntimeException {
    public ProxiedModuleRunningException(String message) {
        super(message);
    }

    public ProxiedModuleRunningException(String message, Throwable cause) {
        super(message, cause);
    }
}
