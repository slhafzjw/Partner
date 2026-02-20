package work.slhaf.partner.common.vector.exception;

import work.slhaf.partner.api.agent.runtime.exception.AgentRuntimeException;

public class VectorClientLoadFailedException extends AgentRuntimeException {

    public VectorClientLoadFailedException(String message) {
        super(message);
    }

    public VectorClientLoadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
