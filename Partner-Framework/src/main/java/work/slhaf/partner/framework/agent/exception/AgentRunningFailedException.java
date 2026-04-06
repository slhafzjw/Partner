package work.slhaf.partner.framework.agent.exception;

public class AgentRunningFailedException extends AgentRuntimeException {
    public AgentRunningFailedException(String message) {
        super(message);
    }

    public AgentRunningFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
