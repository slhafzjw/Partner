package work.slhaf.partner.api.agent.runtime.exception;

public class AgentRuntimeException extends RuntimeException {
    public AgentRuntimeException(String message) {
        super("Agent 执行出错 " + message);
    }

    public AgentRuntimeException(String message, Throwable cause) {
        super("Agent 执行出错 " + message, cause);
    }
}
