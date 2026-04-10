package work.slhaf.partner.common.vector.exception;

import work.slhaf.partner.framework.agent.exception.deprecated.AgentRuntimeException;

public class VectorClientExecuteException extends AgentRuntimeException {

    public VectorClientExecuteException(String message) {
        super(message);
    }

    public VectorClientExecuteException(String message, Throwable cause) {
        super(message, cause);
    }

}
