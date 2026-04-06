package work.slhaf.partner.framework.agent.factory.component.exception;

import work.slhaf.partner.framework.agent.exception.AgentRuntimeException;

public class ProxiedModuleRunningException extends AgentRuntimeException {
    public ProxiedModuleRunningException(String message) {
        super(message);
    }

    public ProxiedModuleRunningException(String message, Throwable cause) {
        super(message, cause);
    }
}
