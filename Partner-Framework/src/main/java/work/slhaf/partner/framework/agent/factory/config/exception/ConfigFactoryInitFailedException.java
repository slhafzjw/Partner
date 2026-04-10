package work.slhaf.partner.framework.agent.factory.config.exception;

import work.slhaf.partner.framework.agent.exception.deprecated.AgentLaunchFailedException;

public class ConfigFactoryInitFailedException extends AgentLaunchFailedException {
    public ConfigFactoryInitFailedException(String message, Throwable cause) {
        super("AgentConfigLoader 执行失败: " + message, cause);
    }

    public ConfigFactoryInitFailedException(String message) {
        super("AgentConfigLoader 执行失败: " + message);
    }
}
