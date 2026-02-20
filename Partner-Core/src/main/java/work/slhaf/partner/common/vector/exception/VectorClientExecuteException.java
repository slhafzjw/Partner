package work.slhaf.partner.common.vector.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentRuntimeException;

public class VectorClientExecuteException extends AgentRuntimeException {

    public VectorClientExecuteException(String message) {
        super(message);
    }

    public VectorClientExecuteException(String message, Throwable cause) {
        super(message, cause);
    }

}
