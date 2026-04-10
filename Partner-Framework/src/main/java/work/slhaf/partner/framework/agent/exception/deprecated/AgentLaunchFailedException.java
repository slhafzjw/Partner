package work.slhaf.partner.framework.agent.exception.deprecated;

@Deprecated
public class AgentLaunchFailedException extends RuntimeException {
    public AgentLaunchFailedException(String message, Throwable cause) {
        super("Agent 启动失败 " + message, cause);
    }

    public AgentLaunchFailedException(String message) {
        super("Agent 启动失败 " + message);
    }
}
